package com.fixhua.runtime;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.loader.ResourcesLoader;
import android.content.res.loader.ResourcesProvider;
import android.os.Build;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;

import dalvik.system.DexClassLoader;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

final class RuntimeArtifactProbe {
    private static final String SAMPLE_ENTRY = "com.fixhua.sampletarget.ProbeEntry";
    private static final String SAMPLE_R_STRING = "com.fixhua.sampletarget.R$string";

    record Result(
            String sourceSha256,
            String transformedSha256,
            int packageManagerCallsites,
            int contextIdentityCallsites,
            int safeStubCallsites,
            String codeMarker,
            String appViewMarker,
            String resourceMarker) {
        int rewrittenCallsites() {
            return packageManagerCallsites + contextIdentityCallsites + safeStubCallsites;
        }
    }

    static Result run(
            Context context,
            File sourceApk,
            IBinder broker,
            String sessionId,
            String artifactSha256,
            String planDigest,
            String syntheticPackageName) throws Exception {
        if (Build.VERSION.SDK_INT < 30) {
            throw new UnsupportedOperationException("ResourcesLoader requires API 30+");
        }
        if (sourceApk == null || !sourceApk.isFile()) {
            throw new IllegalStateException("Runtime artifact is missing");
        }
        if (sourceApk.canWrite()) {
            throw new SecurityException("Runtime artifact must be read-only before transformation");
        }
        if (syntheticPackageName == null || syntheticPackageName.isBlank()) {
            throw new SecurityException("Synthetic package identity is missing");
        }

        DexCallsiteTransformer.Result transformed = DexCallsiteTransformer.transform(context, sourceApk);
        if (!artifactSha256.equalsIgnoreCase(transformed.sourceSha256())) {
            throw new SecurityException("Transform source differs from Controller-approved artifact");
        }
        if (transformed.packageManagerCallsites() != 1) {
            throw new SecurityException("Slice V3 requires exactly one PackageManager callsite");
        }
        if (transformed.contextIdentityCallsites() != 1) {
            throw new SecurityException("Slice V3 requires exactly one Context identity callsite");
        }
        if (transformed.safeStubCallsites() != 1) {
            throw new SecurityException("Slice V3 requires exactly one permission SAFE_STUB callsite");
        }

        File apk = transformed.image();
        File optimized = new File(
                context.getCodeCacheDir(), "sample-target-loader-" + transformed.imageSha256().substring(0, 12));
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

        Method run = entry.getMethod("run", Context.class);
        String appViewMarker;
        try (SyntheticExecutionScope.Scope ignored = SyntheticExecutionScope.install(
                syntheticPackageName,
                packageName -> SyntheticPackageManagerView.query(
                        broker, sessionId, artifactSha256, planDigest, packageName))) {
            appViewMarker = (String) run.invoke(null, context);
        }

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

        return new Result(
                transformed.sourceSha256(),
                transformed.imageSha256(),
                transformed.packageManagerCallsites(),
                transformed.contextIdentityCallsites(),
                transformed.safeStubCallsites(),
                codeMarker,
                appViewMarker,
                resourceMarker);
    }

    private RuntimeArtifactProbe() {}
}
