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

/** Rewrites ordinary Application inheritance in the private execution image to FixHUA's facade. */
final class ApplicationFacadeTransformer {
    private static final String TRANSFORM_ID = "application-facade-v1";
    private static final String ANDROID_APPLICATION = "Landroid/app/Application;";
    private static final String FIXHUA_APPLICATION_FACADE =
            "Lcom/fixhua/runtime/SyntheticApplicationFacade;";

    record Result(File image, int rewrittenApplicationClasses, String inputSha256, String imageSha256) {}

    static Result transform(Context context, File inputApk) throws Exception {
        if (inputApk == null || !inputApk.isFile()) {
            throw new IllegalStateException("Activity-stage execution image missing");
        }
        if (inputApk.canWrite()) {
            throw new SecurityException("Activity-stage execution image must be read-only");
        }

        String inputHash = sha256(inputApk);
        File directory = new File(context.getCodeCacheDir(), "application-facade-images-v1");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException("Cannot create Application facade transform directory");
        }
        File output = new File(directory, inputHash + "-" + TRANSFORM_ID + ".apk");
        File temp = new File(directory, output.getName() + ".tmp");
        if (temp.exists() && !temp.delete()) {
            throw new IllegalStateException("Cannot reset Application facade temp image");
        }

        AtomicInteger applicationClasses = new AtomicInteger();
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
                    rewriteDex(input.getInputStream(entry), zipOut, applicationClasses);
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

        if (output.exists() && !output.delete()) {
            temp.delete();
            throw new IllegalStateException("Cannot replace Application facade image");
        }
        if (!temp.renameTo(output)) {
            temp.delete();
            throw new IllegalStateException("Cannot commit Application facade image");
        }
        if (!output.setReadOnly()) {
            output.delete();
            throw new SecurityException("Cannot make Application facade image read-only");
        }
        return new Result(output, applicationClasses.get(), inputHash, sha256(output));
    }

    private static void rewriteDex(
            InputStream raw,
            OutputStream destination,
            AtomicInteger applicationClasses) throws Exception {
        File tempDex = File.createTempFile("fixhua-application-facade-", ".dex");
        try {
            DexFile dex;
            try (BufferedInputStream in = new BufferedInputStream(raw)) {
                dex = com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile.fromInputStream(
                        Opcodes.getDefault(), in);
            }
            for (ClassDef classDef : dex.getClasses()) {
                if (ANDROID_APPLICATION.equals(classDef.getSuperclass())) {
                    applicationClasses.incrementAndGet();
                }
            }

            RewriterModule module = new RewriterModule() {
                @Override
                public Rewriter<String> getTypeRewriter(Rewriters rewriters) {
                    return type -> ANDROID_APPLICATION.equals(type)
                            ? FIXHUA_APPLICATION_FACADE : type;
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

    private ApplicationFacadeTransformer() {}
}
