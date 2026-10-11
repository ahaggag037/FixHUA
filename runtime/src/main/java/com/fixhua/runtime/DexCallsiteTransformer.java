package com.fixhua.runtime;

import android.content.Context;

import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.DexFile;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.MethodImplementation;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction3rc;
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction35c;
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction3rc;
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference;
import com.android.tools.smali.dexlib2.rewriter.DexRewriter;
import com.android.tools.smali.dexlib2.rewriter.InstructionRewriter;
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
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Creates a private derived execution APK while preserving the imported source artifact unchanged.
 * Slice V3 rewrites only explicitly declared compatibility callsites.
 */
final class DexCallsiteTransformer {
    private static final String TRANSFORM_ID = "pm-context-permission-v3";

    private static final String PM_SOURCE_CLASS = "Landroid/content/pm/PackageManager;";
    private static final String PM_SOURCE_METHOD = "getPackageInfo";
    private static final String PM_SOURCE_RETURN = "Landroid/content/pm/PackageInfo;";
    private static final List<String> PM_SOURCE_PARAMS = List.of("Ljava/lang/String;", "I");
    private static final ImmutableMethodReference PM_HOOK_REFERENCE = new ImmutableMethodReference(
            "Lcom/fixhua/runtime/PackageManagerHooks;",
            PM_SOURCE_METHOD,
            List.of("Landroid/content/pm/PackageManager;", "Ljava/lang/String;", "I"),
            PM_SOURCE_RETURN);

    private static final String CONTEXT_SOURCE_CLASS = "Landroid/content/Context;";
    private static final String CONTEXT_IDENTITY_METHOD = "getPackageName";
    private static final String CONTEXT_IDENTITY_RETURN = "Ljava/lang/String;";
    private static final ImmutableMethodReference CONTEXT_IDENTITY_HOOK_REFERENCE =
            new ImmutableMethodReference(
                    "Lcom/fixhua/runtime/ContextHooks;",
                    CONTEXT_IDENTITY_METHOD,
                    List.of("Landroid/content/Context;"),
                    CONTEXT_IDENTITY_RETURN);

    private static final String PERMISSION_METHOD = "checkSelfPermission";
    private static final String PERMISSION_RETURN = "I";
    private static final List<String> PERMISSION_PARAMS = List.of("Ljava/lang/String;");
    private static final ImmutableMethodReference PERMISSION_HOOK_REFERENCE =
            new ImmutableMethodReference(
                    "Lcom/fixhua/runtime/ContextHooks;",
                    PERMISSION_METHOD,
                    List.of("Landroid/content/Context;", "Ljava/lang/String;"),
                    PERMISSION_RETURN);

    record Result(
            File image,
            int packageManagerCallsites,
            int contextIdentityCallsites,
            int safeStubCallsites,
            String sourceSha256,
            String imageSha256) {
        int rewrittenCallsites() {
            return packageManagerCallsites + contextIdentityCallsites + safeStubCallsites;
        }
    }

    private record TargetCounts(int packageManager, int contextIdentity, int safeStub) {}

    static Result transform(Context context, File sourceApk) throws Exception {
        if (sourceApk == null || !sourceApk.isFile()) throw new IllegalStateException("Source APK missing");
        if (sourceApk.canWrite()) throw new SecurityException("Source APK must be immutable before transform");
        String sourceHash = sha256(sourceApk);
        File directory = new File(context.getCodeCacheDir(), "transformed-images-v3");
        if (!directory.exists() && !directory.mkdirs()) throw new IllegalStateException("Cannot create transform directory");
        File output = new File(directory, sourceHash + "-" + TRANSFORM_ID + ".apk");
        File temp = new File(directory, output.getName() + ".tmp");
        if (temp.exists() && !temp.delete()) throw new IllegalStateException("Cannot reset transform temp");

        AtomicInteger packageManagerCallsites = new AtomicInteger();
        AtomicInteger contextIdentityCallsites = new AtomicInteger();
        AtomicInteger safeStubCallsites = new AtomicInteger();
        try (ZipFile input = new ZipFile(sourceApk);
             ZipOutputStream zipOut = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(temp)))) {
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
                String upper = entry.getName().toUpperCase(Locale.ROOT);
                if (upper.startsWith("META-INF/")
                        && (upper.endsWith(".RSA") || upper.endsWith(".DSA") || upper.endsWith(".EC")
                        || upper.endsWith(".SF") || upper.equals("META-INF/MANIFEST.MF"))) continue;

                ZipEntry outEntry = new ZipEntry(entry.getName());
                outEntry.setTime(0L);
                zipOut.putNextEntry(outEntry);
                if (isDexEntry(entry.getName())) {
                    rewriteDex(
                            input.getInputStream(entry),
                            zipOut,
                            packageManagerCallsites,
                            contextIdentityCallsites,
                            safeStubCallsites);
                } else {
                    try (InputStream in = new BufferedInputStream(input.getInputStream(entry))) { copy(in, zipOut); }
                }
                zipOut.closeEntry();
            }
        } catch (Exception failure) {
            if (temp.exists()) temp.delete();
            throw failure;
        }

        if (packageManagerCallsites.get() + contextIdentityCallsites.get() + safeStubCallsites.get() <= 0) {
            temp.delete();
            throw new SecurityException("No declared compatibility callsite was found");
        }
        if (output.exists() && !output.delete()) {
            temp.delete();
            throw new IllegalStateException("Cannot replace transformed image");
        }
        if (!temp.renameTo(output)) {
            temp.delete();
            throw new IllegalStateException("Cannot commit transformed image");
        }
        if (!output.setReadOnly()) {
            output.delete();
            throw new SecurityException("Cannot make transformed image read-only");
        }
        return new Result(
                output,
                packageManagerCallsites.get(),
                contextIdentityCallsites.get(),
                safeStubCallsites.get(),
                sourceHash,
                sha256(output));
    }

    private static void rewriteDex(
            InputStream raw,
            OutputStream destination,
            AtomicInteger packageManagerCallsites,
            AtomicInteger contextIdentityCallsites,
            AtomicInteger safeStubCallsites) throws Exception {
        File tempDex = File.createTempFile("fixhua-rewritten-", ".dex");
        try {
            DexFile dex;
            try (BufferedInputStream in = new BufferedInputStream(raw)) {
                dex = com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile.fromInputStream(Opcodes.getDefault(), in);
            }
            TargetCounts counts = countTargetCallsites(dex);
            packageManagerCallsites.addAndGet(counts.packageManager());
            contextIdentityCallsites.addAndGet(counts.contextIdentity());
            safeStubCallsites.addAndGet(counts.safeStub());

            RewriterModule module = new RewriterModule() {
                @Override public Rewriter<Instruction> getInstructionRewriter(Rewriters rewriters) {
                    InstructionRewriter delegate = new InstructionRewriter(rewriters);
                    return instruction -> {
                        Instruction transformed = rewriteInstruction(instruction);
                        return transformed != instruction ? transformed : delegate.rewrite(instruction);
                    };
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

    private static TargetCounts countTargetCallsites(DexFile dex) {
        int packageManager = 0;
        int contextIdentity = 0;
        int safeStub = 0;
        for (ClassDef classDef : dex.getClasses()) {
            for (Method method : classDef.getMethods()) {
                MethodImplementation implementation = method.getImplementation();
                if (implementation == null) continue;
                for (Instruction instruction : implementation.getInstructions()) {
                    MethodReference reference = virtualReference(instruction);
                    if (reference == null) continue;
                    if (isPackageManagerTarget(reference)) packageManager++;
                    else if (isContextIdentityTarget(reference)) contextIdentity++;
                    else if (isPermissionSafeStubTarget(reference)) safeStub++;
                }
            }
        }
        return new TargetCounts(packageManager, contextIdentity, safeStub);
    }

    private static Instruction rewriteInstruction(Instruction instruction) {
        if (instruction instanceof Instruction35c call && call.getOpcode() == Opcode.INVOKE_VIRTUAL) {
            MethodReference reference = asMethodReference(call.getReference());
            ImmutableMethodReference hook = hookFor(reference);
            if (hook != null) {
                return new ImmutableInstruction35c(
                        Opcode.INVOKE_STATIC,
                        call.getRegisterCount(),
                        call.getRegisterC(),
                        call.getRegisterD(),
                        call.getRegisterE(),
                        call.getRegisterF(),
                        call.getRegisterG(),
                        hook);
            }
        }
        if (instruction instanceof Instruction3rc call && call.getOpcode() == Opcode.INVOKE_VIRTUAL_RANGE) {
            MethodReference reference = asMethodReference(call.getReference());
            ImmutableMethodReference hook = hookFor(reference);
            if (hook != null) {
                return new ImmutableInstruction3rc(
                        Opcode.INVOKE_STATIC_RANGE,
                        call.getStartRegister(),
                        call.getRegisterCount(),
                        hook);
            }
        }
        return instruction;
    }

    private static MethodReference virtualReference(Instruction instruction) {
        if (instruction instanceof Instruction35c call && call.getOpcode() == Opcode.INVOKE_VIRTUAL) {
            return asMethodReference(call.getReference());
        }
        if (instruction instanceof Instruction3rc call && call.getOpcode() == Opcode.INVOKE_VIRTUAL_RANGE) {
            return asMethodReference(call.getReference());
        }
        return null;
    }

    private static MethodReference asMethodReference(Object reference) {
        return reference instanceof MethodReference method ? method : null;
    }

    private static ImmutableMethodReference hookFor(MethodReference method) {
        if (method == null) return null;
        if (isPackageManagerTarget(method)) return PM_HOOK_REFERENCE;
        if (isContextIdentityTarget(method)) return CONTEXT_IDENTITY_HOOK_REFERENCE;
        if (isPermissionSafeStubTarget(method)) return PERMISSION_HOOK_REFERENCE;
        return null;
    }

    private static boolean isPackageManagerTarget(MethodReference method) {
        if (!PM_SOURCE_CLASS.equals(method.getDefiningClass())
                || !PM_SOURCE_METHOD.equals(method.getName())
                || !PM_SOURCE_RETURN.equals(method.getReturnType())) return false;
        return parametersEqual(method.getParameterTypes(), PM_SOURCE_PARAMS);
    }

    private static boolean isContextIdentityTarget(MethodReference method) {
        return CONTEXT_SOURCE_CLASS.equals(method.getDefiningClass())
                && CONTEXT_IDENTITY_METHOD.equals(method.getName())
                && CONTEXT_IDENTITY_RETURN.equals(method.getReturnType())
                && method.getParameterTypes().isEmpty();
    }

    private static boolean isPermissionSafeStubTarget(MethodReference method) {
        return CONTEXT_SOURCE_CLASS.equals(method.getDefiningClass())
                && PERMISSION_METHOD.equals(method.getName())
                && PERMISSION_RETURN.equals(method.getReturnType())
                && parametersEqual(method.getParameterTypes(), PERMISSION_PARAMS);
    }

    private static boolean parametersEqual(
            List<? extends CharSequence> actual,
            List<String> expected) {
        if (actual.size() != expected.size()) return false;
        for (int i = 0; i < actual.size(); i++) {
            if (!expected.get(i).contentEquals(actual.get(i))) return false;
        }
        return true;
    }

    private static boolean isDexEntry(String name) {
        if ("classes.dex".equals(name)) return true;
        if (!name.startsWith("classes") || !name.endsWith(".dex")) return false;
        String number = name.substring("classes".length(), name.length() - ".dex".length());
        if (number.isEmpty()) return false;
        for (int i = 0; i < number.length(); i++) if (!Character.isDigit(number.charAt(i))) return false;
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

    private DexCallsiteTransformer() {}
}
