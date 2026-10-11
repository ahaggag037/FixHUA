package com.fixhua.runtime;

import android.content.Context;

import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.DexFile;
import com.android.tools.smali.dexlib2.rewriter.DexRewriter;
import com.android.tools.smali.dexlib2.rewriter.Rewriter;
import com.android.tools.smali.dexlib2.rewriter.RewriterModule;
import com.android.tools.smali.dexlib2.rewriter.Rewriters;
import com.android.tools.smali.dexlib2.writer.pool.DexPool;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.Enumeration;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * R9 transform stage: rewrites ordinary `android.app.Activity` inheritance in the private execution
 * image to FixHUA's public-API facade. The imported source and first-stage image remain immutable.
 */
final class ActivityFacadeTransformer {
    private static final String TRANSFORM_ID = "activity-facade-v1";
    private static final String ANDROID_ACTIVITY = "Landroid/app/Activity;";
    private static final String FIXHUA_ACTIVITY_FACADE =
            "Lcom/fixhua/runtime/SyntheticActivityFacade;";

    record Result(File image, int rewrittenActivityClasses, String inputSha256, String imageSha256) {}

    static Result transform(Context context, File inputApk) throws Exception {
        if (inputApk == null || !inputApk.isFile()) {
            throw new IllegalStateException("First-stage execution image missing");
        }
        if (inputApk.canWrite()) {
            throw new SecurityException("First-stage execution image must be read-only");
        }

        String inputHash = sha256(inputApk);
        File directory = new File(context.getCodeCacheDir(), "activity-facade-images-v1");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException("Cannot create Activity facade transform directory");
        }
        File output = new File(directory, inputHash + "-" + TRANSFORM_ID + ".apk");
        File temp = new File(directory, output.getName() + ".tmp");
        if (temp.exists() && !temp.delete()) {
            throw new IllegalStateException("Cannot reset Activity facade temp image");
        }

        AtomicInteger activityClasses = new AtomicInteger();
        try (ZipFile input = new ZipFile(inputApk);
             ZipOutputStream zipOut = new ZipOutputStream(
                     new BufferedOutputStream(new FileOutputStream(temp)))) {
            Enumeration<? extends ZipEntry> entries = input.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    ZipEntry directoryEntry = new ZipEntry(entry.getName());
                    directoryEntry.setTime(0L);
                    zipOut.putNextEntry(directoryEntry);
                    zipOut.closeEntry();
                    continue;
                }

                ZipEntry outEntry = new ZipEntry(entry.getName());
                outEntry.setTime(0L);
                zipOut.putNextEntry(outEntry);
                if (isDexEntry(entry.getName())) {
                    rewriteDex(input.getInputStream(entry), zipOut, activityClasses);
                } else {
                    try (InputStream in = new BufferedInputStream(input.getInputStream(entry))) {
                        copy(in, zipOut);
                    }
                }
                zipOut.closeEntry();
            }
        } catch (Exception failure) {
            if (temp.exists()) temp.delete();
            throw failure;
        }

        if (activityClasses.get() <= 0) {
            temp.delete();
            throw new SecurityException("No ordinary Activity subclass found for facade transform");
        }
        if (output.exists() && !output.delete()) {
            temp.delete();
            throw new IllegalStateException("Cannot replace Activity facade image");
        }
        if (!temp.renameTo(output)) {
            temp.delete();
            throw new IllegalStateException("Cannot commit Activity facade image");
        }
        if (!output.setReadOnly()) {
            output.delete();
            throw new SecurityException("Cannot make Activity facade image read-only");
        }
        return new Result(output, activityClasses.get(), inputHash, sha256(output));
    }

    private static void rewriteDex(
            InputStream raw,
            OutputStream destination,
            AtomicInteger activityClasses) throws Exception {
        File tempDex = File.createTempFile("fixhua-activity-facade-", ".dex");
        try {
            DexFile dex;
            try (BufferedInputStream in = new BufferedInputStream(raw)) {
                dex = com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile.fromInputStream(
                        Opcodes.getDefault(), in);
            }
            for (ClassDef classDef : dex.getClasses()) {
                if (ANDROID_ACTIVITY.equals(classDef.getSuperclass())) {
                    activityClasses.incrementAndGet();
                }
            }

            RewriterModule module = new RewriterModule() {
                @Override
                public Rewriter<String> getTypeRewriter(Rewriters rewriters) {
                    return type -> ANDROID_ACTIVITY.equals(type) ? FIXHUA_ACTIVITY_FACADE : type;
                }
            };
            DexFile rewrittenDex = new DexRewriter(module).getDexFileRewriter().rewrite(dex);
            DexPool.writeTo(tempDex.getAbsolutePath(), rewrittenDex);
            try (InputStream rewrittenInput = new BufferedInputStream(new FileInputStream(tempDex))) {
                copy(rewrittenInput, destination);
            }
        } finally {
            tempDex.delete();
        }
    }

    private static boolean isDexEntry(String name) {
        if ("classes.dex".equals(name)) return true;
        if (!name.startsWith("classes") || !name.endsWith(".dex")) return false;
        String number = name.substring("classes".length(), name.length() - ".dex".length());
        if (number.isEmpty()) return false;
        for (int i = 0; i < number.length(); i++) {
            if (!Character.isDigit(number.charAt(i))) return false;
        }
        return true;
    }

    private static void copy(InputStream in, OutputStream out) throws Exception {
        byte[] buffer = new byte[64 * 1024];
        int read;
        while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
    }

    private static String sha256(File file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[64 * 1024];
        try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
            int read;
            while ((read = in.read(buffer)) != -1) md.update(buffer, 0, read);
        }
        StringBuilder hex = new StringBuilder(64);
        for (byte b : md.digest()) hex.append(String.format(Locale.ROOT, "%02x", b));
        return hex.toString();
    }

    private ActivityFacadeTransformer() {}
}
