package com.fixhua.guard;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.net.Uri;
import android.os.Build;

import com.fixhua.core.SyntheticPackageRecord;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Imports a user-selected APK into Controller-private, content-addressed storage.
 *
 * <p>This class parses metadata only. It never loads target classes or executes imported code in
 * the Controller UID.</p>
 */
final class ArtifactImporter {
    private static final long MAX_APK_BYTES = 512L * 1024L * 1024L;
    private static final int BUFFER_BYTES = 128 * 1024;
    private static final String STORE_DIR = "artifact-store-v1";

    record ImportedArtifact(
            File artifactFile,
            File metadataFile,
            SyntheticPackageRecord record,
            boolean reusedExistingArtifact,
            RuntimeSessionStore.Session runtimeSession) {}

    private final Context context;
    private final PackageManager packageManager;

    ArtifactImporter(Context context) {
        this.context = context.getApplicationContext();
        this.packageManager = this.context.getPackageManager();
    }

    ImportedArtifact importUri(Uri uri) throws Exception {
        if (uri == null) throw new IllegalArgumentException("uri");
        try (InputStream raw = context.getContentResolver().openInputStream(uri)) {
            if (raw == null) throw new IOException("Selected document cannot be opened");
            return importStream(raw);
        }
    }

    ImportedArtifact importInstalledPackageForAcceptance(String packageName) throws Exception {
        ApplicationInfo info = packageManager.getApplicationInfo(packageName, 0);
        File source = new File(info.sourceDir);
        if (!source.isFile()) throw new IOException("Installed acceptance package source is missing");
        try (InputStream raw = new FileInputStream(source)) {
            return importStream(raw);
        }
    }

    private ImportedArtifact importStream(InputStream raw) throws Exception {
        File store = new File(context.getFilesDir(), STORE_DIR);
        if (!store.exists() && !store.mkdirs()) {
            throw new IOException("Cannot create artifact store");
        }

        File incoming = File.createTempFile("incoming-", ".apk", store);
        try {
            CopyResult copied = copyAndHash(raw, incoming);
            ParsedArchive parsed = parseArchive(incoming, copied.sha256(), copied.bytes());
            SyntheticPackageRecord record = parsed.record();

            File finalApk = new File(store, copied.sha256() + ".apk");
            boolean reused = false;
            if (finalApk.exists()) {
                if (finalApk.length() != copied.bytes()
                        || !copied.sha256().equals(sha256(finalApk))) {
                    throw new IOException("Content-addressed artifact store is inconsistent");
                }
                if (finalApk.canWrite()) {
                    throw new SecurityException("Existing content-addressed artifact is writable");
                }
                reused = true;
            } else {
                if (!incoming.renameTo(finalApk)) {
                    throw new IOException("Cannot commit imported artifact");
                }
                try {
                    makeReadOnly(finalApk);
                } catch (Exception failure) {
                    if (finalApk.exists() && !finalApk.delete()) finalApk.deleteOnExit();
                    throw failure;
                }
            }

            File metadata = new File(store, copied.sha256() + ".json");
            writeMetadataAtomically(metadata, record, parsed.nativeAbis());
            RuntimeSessionStore.Session session = new RuntimeSessionStore(context).create(record);
            return new ImportedArtifact(finalApk, metadata, record, reused, session);
        } finally {
            if (incoming.exists() && !incoming.delete()) incoming.deleteOnExit();
        }
    }

    private ParsedArchive parseArchive(File apk, String artifactSha256, long bytes) throws Exception {
        int flags = PackageManager.GET_PERMISSIONS | PackageManager.GET_SIGNING_CERTIFICATES;
        PackageInfo info;
        if (Build.VERSION.SDK_INT >= 33) {
            info = packageManager.getPackageArchiveInfo(
                    apk.getAbsolutePath(), PackageManager.PackageInfoFlags.of(flags));
        } else {
            //noinspection deprecation
            info = packageManager.getPackageArchiveInfo(apk.getAbsolutePath(), flags);
        }
        if (info == null || info.applicationInfo == null || info.packageName == null) {
            throw new IOException("Selected file is not a parseable Android APK");
        }

        ApplicationInfo app = info.applicationInfo;
        List<String> permissions = new ArrayList<>();
        if (info.requestedPermissions != null) {
            for (String permission : info.requestedPermissions) permissions.add(permission);
        }

        List<String> signerDigests = signingDigests(info.signingInfo);
        if (signerDigests.isEmpty()) throw new IOException("APK has no signing certificate");

        NativeScan nativeScan = scanNativeCode(apk);
        SyntheticPackageRecord record = new SyntheticPackageRecord(
                artifactSha256,
                bytes,
                info.packageName,
                info.versionName,
                info.getLongVersionCode(),
                app.minSdkVersion,
                app.targetSdkVersion,
                app.className,
                (app.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0,
                nativeScan.hasNativeCode(),
                permissions,
                signerDigests);
        return new ParsedArchive(record, nativeScan.abis());
    }

    private static CopyResult copyAndHash(InputStream input, File destination) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        long total = 0;
        byte[] buffer = new byte[BUFFER_BYTES];
        try (BufferedInputStream in = new BufferedInputStream(input, BUFFER_BYTES);
             BufferedOutputStream out = new BufferedOutputStream(
                     new FileOutputStream(destination), BUFFER_BYTES)) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > MAX_APK_BYTES) throw new IOException("APK exceeds 512 MB safety limit");
                digest.update(buffer, 0, read);
                out.write(buffer, 0, read);
            }
            out.flush();
        }
        if (total <= 0) throw new IOException("APK is empty");
        return new CopyResult(total, hex(digest.digest()));
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[BUFFER_BYTES];
        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(file), BUFFER_BYTES)) {
            int read;
            while ((read = in.read(buffer)) != -1) digest.update(buffer, 0, read);
        }
        return hex(digest.digest());
    }

    private static List<String> signingDigests(SigningInfo signingInfo) throws Exception {
        List<String> result = new ArrayList<>();
        if (signingInfo == null) return result;
        Signature[] signatures = signingInfo.hasMultipleSigners()
                ? signingInfo.getApkContentsSigners()
                : signingInfo.getSigningCertificateHistory();
        if (signatures == null) return result;
        for (Signature signature : signatures) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            result.add(hex(digest.digest(signature.toByteArray())));
        }
        return result;
    }

    private static NativeScan scanNativeCode(File apk) throws IOException {
        List<String> abis = new ArrayList<>();
        boolean hasNative = false;
        try (ZipFile zip = new ZipFile(apk)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!entry.isDirectory() && name.startsWith("lib/") && name.endsWith(".so")) {
                    hasNative = true;
                    String[] parts = name.split("/", 3);
                    if (parts.length >= 3 && !parts[1].isBlank() && !abis.contains(parts[1])) {
                        abis.add(parts[1]);
                    }
                }
            }
        }
        abis.sort(String::compareTo);
        return new NativeScan(hasNative, List.copyOf(abis));
    }

    private static void makeReadOnly(File artifact) throws IOException {
        if (!artifact.setReadable(true, true)) throw new IOException("Cannot mark artifact readable");
        if (!artifact.setWritable(false, false)) throw new IOException("Cannot make artifact read-only");
        if (artifact.canWrite()) throw new IOException("Artifact remained writable");
    }

    private static void writeMetadataAtomically(
            File metadata,
            SyntheticPackageRecord record,
            List<String> nativeAbis) throws Exception {
        JSONObject json = new JSONObject();
        json.put("schemaVersion", 1);
        json.put("truthScope", "HOST_REAL");
        json.put("artifactSha256", record.artifactSha256());
        json.put("artifactSizeBytes", record.artifactSizeBytes());
        json.put("packageName", record.packageName());
        json.put("versionName", record.versionName());
        json.put("versionCode", record.versionCode());
        json.put("minSdk", record.minSdk());
        json.put("targetSdk", record.targetSdk());
        json.put("applicationClassName", record.applicationClassName());
        json.put("debuggable", record.debuggable());
        json.put("hasNativeCode", record.hasNativeCode());
        json.put("requestedPermissions", new JSONArray(record.requestedPermissions()));
        json.put("signingCertificateSha256", new JSONArray(record.signingCertificateSha256()));
        json.put("nativeAbis", new JSONArray(nativeAbis));

        File temp = new File(metadata.getParentFile(), metadata.getName() + ".tmp");
        try (FileOutputStream output = new FileOutputStream(temp, false)) {
            byte[] encoded = json.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            output.write(encoded);
            output.getFD().sync();
        }
        if (metadata.exists() && !metadata.delete()) {
            throw new IOException("Cannot replace artifact metadata");
        }
        if (!temp.renameTo(metadata)) throw new IOException("Cannot commit artifact metadata");
    }

    private static String hex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) hex.append(String.format(Locale.ROOT, "%02x", value));
        return hex.toString();
    }

    private record CopyResult(long bytes, String sha256) {}
    private record NativeScan(boolean hasNativeCode, List<String> abis) {}
    private record ParsedArchive(SyntheticPackageRecord record, List<String> nativeAbis) {}
}
