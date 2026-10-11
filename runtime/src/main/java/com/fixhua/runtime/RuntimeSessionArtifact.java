package com.fixhua.runtime;

import android.content.Context;
import android.os.IBinder;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.Locale;

/** Receives a Controller-approved APK descriptor and creates a verified Runtime-private image. */
final class RuntimeSessionArtifact {
    private static final String BROKER_DESCRIPTOR = "com.fixhua.guard.IHostCapabilityBroker";
    private static final int TRANSACTION_OPEN_SESSION_ARTIFACT = IBinder.FIRST_CALL_TRANSACTION + 1;
    private static final long MAX_ARTIFACT_BYTES = 512L * 1024L * 1024L;
    private static final int BUFFER_BYTES = 128 * 1024;

    static File obtain(
            Context context,
            IBinder broker,
            String sessionId,
            String expectedSha256,
            String planDigest) throws Exception {
        validateToken(sessionId, 128, false, "sessionId");
        validateToken(expectedSha256, 64, true, "artifactSha256");
        validateToken(planDigest, 64, true, "planDigest");

        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        ParcelFileDescriptor descriptor = null;
        try {
            data.writeInterfaceToken(BROKER_DESCRIPTOR);
            data.writeString(sessionId);
            data.writeString(expectedSha256);
            data.writeString(planDigest);
            boolean handled = broker.transact(TRANSACTION_OPEN_SESSION_ARTIFACT, data, reply, 0);
            if (!handled) throw new SecurityException("Controller did not handle artifact request");
            reply.readException();
            if (reply.readInt() != 1) throw new SecurityException("Controller returned no artifact descriptor");
            descriptor = ParcelFileDescriptor.CREATOR.createFromParcel(reply);
            if (descriptor == null) throw new SecurityException("Artifact descriptor missing");
            return copyAndVerify(context, descriptor, expectedSha256.toLowerCase(Locale.ROOT));
        } finally {
            if (descriptor != null) descriptor.close();
            data.recycle();
            reply.recycle();
        }
    }

    private static File copyAndVerify(
            Context context,
            ParcelFileDescriptor descriptor,
            String expectedSha256) throws Exception {
        File root = new File(context.getFilesDir(), "execution-images-v1");
        if (!root.exists() && !root.mkdirs()) throw new IOException("Cannot create execution image store");

        File finalFile = new File(root, expectedSha256 + ".apk");
        if (finalFile.exists()) {
            if (!expectedSha256.equals(sha256(finalFile))) {
                throw new SecurityException("Existing Runtime image hash mismatch");
            }
            ensureReadOnly(finalFile);
            return finalFile;
        }

        File temp = File.createTempFile("incoming-", ".apk", root);
        boolean committed = false;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[BUFFER_BYTES];
            long total = 0;
            try (BufferedInputStream in = new BufferedInputStream(
                    new ParcelFileDescriptor.AutoCloseInputStream(descriptor), BUFFER_BYTES);
                 BufferedOutputStream out = new BufferedOutputStream(
                         new FileOutputStream(temp), BUFFER_BYTES)) {
                int read;
                while ((read = in.read(buffer)) != -1) {
                    total += read;
                    if (total > MAX_ARTIFACT_BYTES) {
                        throw new SecurityException("Runtime artifact exceeds size limit");
                    }
                    digest.update(buffer, 0, read);
                    out.write(buffer, 0, read);
                }
                out.flush();
            }
            String actual = hex(digest.digest());
            if (!MessageDigest.isEqual(
                    expectedSha256.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                    actual.getBytes(java.nio.charset.StandardCharsets.US_ASCII))) {
                throw new SecurityException("Runtime artifact hash mismatch");
            }
            if (!temp.renameTo(finalFile)) throw new IOException("Cannot commit Runtime image");
            committed = true;
            ensureReadOnly(finalFile);
            return finalFile;
        } finally {
            if (!committed && temp.exists() && !temp.delete()) temp.deleteOnExit();
        }
    }

    private static void ensureReadOnly(File file) throws IOException {
        if (!file.setReadable(true, true)) throw new IOException("Cannot mark Runtime image readable");
        if (!file.setWritable(false, false)) throw new IOException("Cannot make Runtime image read-only");
        if (file.canWrite()) throw new IOException("Runtime image remained writable");
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

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) out.append(String.format(Locale.ROOT, "%02x", value));
        return out.toString();
    }

    private static void validateToken(String value, int limit, boolean exact, String name) {
        if (value == null || value.isBlank() || value.length() > limit || (exact && value.length() != limit)) {
            throw new SecurityException("Malformed " + name);
        }
    }

    private RuntimeSessionArtifact() {}
}
