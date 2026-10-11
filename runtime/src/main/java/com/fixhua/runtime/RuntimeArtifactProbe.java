package com.fixhua.runtime;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.content.res.loader.ResourcesLoader;
import android.content.res.loader.ResourcesProvider;
import android.os.Build;
import android.os.ParcelFileDescriptor;

import dalvik.system.DexClassLoader;

import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.MessageDigest;

final class RuntimeArtifactProbe {
    private static final String SAMPLE_PACKAGE = "com.fixhua.sampletarget";
    private static final String SAMPLE_ENTRY = "com.fixhua.sampletarget.ProbeEntry";
    private static final String SAMPLE_R_STRING = "com.fixhua.sampletarget.R$string";

    record Result(String artifactSha256, String codeMarker, String resourceMarker) {}

    static Result run(Context context) throws Exception {
        PackageManager pm = context.getPackageManager();
        ApplicationInfo info = pm.getApplicationInfo(SAMPLE_PACKAGE, 0);
        File apk = new File(info.sourceDir);
        if (!apk.isFile()) throw new IllegalStateException("Sample APK source is missing");
        return run(context, apk);
    }

    static Result run(Context context, File apk) throws Exception {
        if (Build.VERSION.SDK_INT < 30) {
            throw new UnsupportedOperationException("ResourcesLoader requires API 30+");
        }
        if (apk == null || !apk.isFile()) {
            throw new IllegalStateException("Runtime artifact is missing");
        }
        if (apk.canWrite()) {
            throw new SecurityException("Runtime artifact must be read-only before code loading");
        }

        String hash = sha256(apk);
        File optimized = new File(context.getCodeCacheDir(), "sample-target-loader-" + hash.substring(0, 12));
        if (!optimized.exists() && !optimized.mkdirs()) {
            throw new IllegalStateException("Cannot create runtime code cache");
        }

        DexClassLoader classLoader = new DexClassLoader(
                apk.getAbsolutePath(),
                optimized.getAbsolutePath(),
                null,
                context.getClassLoader());

        Class<?> entry = classLoader.loadClass(SAMPLE_ENTRY);
        Method pureMarker = entry.getMethod("pureMarker");
        String codeMarker = (String) pureMarker.invoke(null);

        Class<?> rString = classLoader.loadClass(SAMPLE_R_STRING);
        Field markerField = rString.getField("synthetic_marker");
        int markerResId = markerField.getInt(null);

        ResourcesProvider provider;
        try (ParcelFileDescriptor pfd = ParcelFileDescriptor.open(apk, ParcelFileDescriptor.MODE_READ_ONLY)) {
            provider = ResourcesProvider.loadFromApk(pfd);
        }

        ResourcesLoader loader = new ResourcesLoader();
        loader.addProvider(provider);
        Resources resources = context.createConfigurationContext(
                context.getResources().getConfiguration()).getResources();
        resources.addLoaders(loader);
        String resourceMarker;
        try {
            resourceMarker = resources.getString(markerResId);
        } finally {
            resources.removeLoaders(loader);
            loader.clearProviders();
            provider.close();
        }

        return new Result(hash, codeMarker, resourceMarker);
    }

    static String sha256(File file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[64 * 1024];
        try (FileInputStream input = new FileInputStream(file)) {
            int read;
            while ((read = input.read(buffer)) != -1) md.update(buffer, 0, read);
        }
        StringBuilder hex = new StringBuilder(64);
        for (byte b : md.digest()) hex.append(String.format("%02x", b));
        return hex.toString();
    }

    private RuntimeArtifactProbe() {}
}
