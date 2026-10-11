package com.fixhua.runtime;

import android.content.Context;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * Runtime-private persistent data root for one synthetic package identity.
 *
 * <p>The root is keyed by package name + signing certificate digest, not package name alone, so a
 * different signer cannot inherit an earlier guest's state merely by reusing its package name.
 * Nothing in this class escapes the Runtime application's private sandbox.</p>
 */
final class GuestDataPlane {
    private static final String STORE_VERSION = "guest-data-v1";

    private final String packageName;
    private final String signerSha256;
    private final String identityDigest;
    private final File root;
    private final File files;
    private final File cache;
    private final File codeCache;
    private final File noBackup;
    private final File databases;

    private GuestDataPlane(
            String packageName,
            String signerSha256,
            String identityDigest,
            File root,
            File files,
            File cache,
            File codeCache,
            File noBackup,
            File databases) {
        this.packageName = packageName;
        this.signerSha256 = signerSha256;
        this.identityDigest = identityDigest;
        this.root = root;
        this.files = files;
        this.cache = cache;
        this.codeCache = codeCache;
        this.noBackup = noBackup;
        this.databases = databases;
    }

    static GuestDataPlane create(Context host, String packageName, String signerSha256) throws Exception {
        if (host == null) throw new IllegalArgumentException("host");
        validatePackageName(packageName);
        String signer = normalizeSha256(signerSha256);
        String identity = sha256(packageName + "\n" + signer);

        File store = new File(host.getNoBackupFilesDir(), STORE_VERSION);
        ensureDirectory(store);
        File root = confinedChild(store, identity);
        ensureDirectory(root);

        File files = directory(root, "files");
        File cache = directory(root, "cache");
        File codeCache = directory(root, "code_cache");
        File noBackup = directory(root, "no_backup");
        File databases = directory(root, "databases");

        return new GuestDataPlane(
                packageName,
                signer,
                identity,
                root,
                files,
                cache,
                codeCache,
                noBackup,
                databases);
    }

    String packageName() {
        return packageName;
    }

    String signerSha256() {
        return signerSha256;
    }

    String identityDigest() {
        return identityDigest;
    }

    File root() {
        return root;
    }

    File filesDir() {
        return files;
    }

    File cacheDir() {
        return cache;
    }

    File codeCacheDir() {
        return codeCache;
    }

    File noBackupFilesDir() {
        return noBackup;
    }

    File databasePath(String name) {
        return confinedLeaf(databases, name);
    }

    File filePath(String name) {
        return confinedLeaf(files, name);
    }

    String[] fileList() {
        String[] names = files.list();
        return names == null ? new String[0] : names;
    }

    private static File directory(File root, String name) throws Exception {
        File directory = confinedChild(root, name);
        ensureDirectory(directory);
        return directory;
    }

    private static File confinedChild(File parent, String name) throws Exception {
        File child = new File(parent, name);
        String parentPath = parent.getCanonicalPath();
        String childPath = child.getCanonicalPath();
        if (!childPath.startsWith(parentPath + File.separator)) {
            throw new SecurityException("Guest data path escaped its root");
        }
        return child;
    }

    private static File confinedLeaf(File parent, String name) {
        if (name == null || name.isBlank() || name.length() > 180
                || name.contains("/") || name.contains("\\")
                || ".".equals(name) || "..".equals(name)) {
            throw new SecurityException("Malformed guest data name");
        }
        return new File(parent, name);
    }

    private static void ensureDirectory(File directory) {
        if (directory.isDirectory()) return;
        if (directory.exists() || !directory.mkdirs() || !directory.isDirectory()) {
            throw new IllegalStateException("Cannot establish guest data directory");
        }
    }

    private static void validatePackageName(String value) {
        if (value == null || value.isBlank() || value.length() > 255
                || value.startsWith(".") || value.endsWith(".") || value.contains("..")) {
            throw new SecurityException("Malformed guest package identity");
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean allowed = (c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '_' || c == '.';
            if (!allowed) throw new SecurityException("Malformed guest package identity");
        }
    }

    private static String normalizeSha256(String value) {
        if (value == null) throw new SecurityException("Guest signer identity missing");
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() != 64) throw new SecurityException("Malformed guest signer identity");
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) {
                throw new SecurityException("Malformed guest signer identity");
            }
        }
        return normalized;
    }

    private static String sha256(String value) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(64);
        for (byte b : digest) hex.append(String.format(Locale.ROOT, "%02x", b));
        return hex.toString();
    }
}
