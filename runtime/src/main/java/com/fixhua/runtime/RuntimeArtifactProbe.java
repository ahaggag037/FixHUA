package com.fixhua.runtime;

import android.app.Activity;
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
    private static final String SAMPLE_APPLICATION = "com.fixhua.sampletarget.SampleApplication";
    private static final String SAMPLE_R_STRING = "com.fixhua.sampletarget.R$string";

    record Result(
            String sourceSha256,
            String transformedSha256,
            int packageManagerCallsites,
            int contextIdentityCallsites,
            int safeStubCallsites,
            int hostRedirectCallsites,
            int activityFacadeClasses,
            int applicationFacadeClasses,
            String applicationClassName,
            String applicationMarker,
            String codeMarker,
            String appViewMarker,
            String resourceMarker,
            String screenCreateMarker,
            ForeignApplicationSession applicationSession,
            ForeignScreenSession screenSession,
            ForeignActivitySession ordinaryActivitySession) {
        int rewrittenCallsites() {
            return packageManagerCallsites
                    + contextIdentityCallsites
                    + safeStubCallsites
                    + hostRedirectCallsites;
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
        if (!(context instanceof Activity hostActivity)) {
            throw new IllegalArgumentException("Runtime probe requires a host Activity");
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

        DexCallsiteTransformer.Result callsiteTransform = DexCallsiteTransformer.transform(context, sourceApk);
        if (!artifactSha256.equalsIgnoreCase(callsiteTransform.sourceSha256())) {
            throw new SecurityException("Transform source differs from Controller-approved artifact");
        }
        if (callsiteTransform.packageManagerCallsites() != 1) {
            throw new SecurityException("Slice V4 requires exactly one PackageManager callsite");
        }
        if (callsiteTransform.contextIdentityCallsites() != 1) {
            throw new SecurityException("Slice V4 requires exactly one Context identity callsite");
        }
        if (callsiteTransform.safeStubCallsites() != 1) {
            throw new SecurityException("Slice V4 requires exactly one permission SAFE_STUB callsite");
        }
        if (callsiteTransform.hostRedirectCallsites() != 1) {
            throw new SecurityException("Slice V4 requires exactly one typed host-service redirect callsite");
        }

        ActivityFacadeTransformer.Result activityTransform = ActivityFacadeTransformer.transform(
                context, callsiteTransform.image());
        if (!callsiteTransform.imageSha256().equalsIgnoreCase(activityTransform.inputSha256())) {
            throw new SecurityException("Activity facade input differs from first transform stage");
        }
        if (activityTransform.rewrittenActivityClasses() != 1) {
            throw new SecurityException("R9 requires exactly one ordinary Activity facade target");
        }

        ApplicationFacadeTransformer.Result applicationTransform = ApplicationFacadeTransformer.transform(
                context, activityTransform.image());
        if (!activityTransform.imageSha256().equalsIgnoreCase(applicationTransform.inputSha256())) {
            throw new SecurityException("Application facade input differs from Activity transform stage");
        }
        if (applicationTransform.rewrittenApplicationClasses() != 1) {
            throw new SecurityException("R10 requires exactly one ordinary Application facade target");
        }

        File apk = applicationTransform.image();
        File optimized = new File(
                context.getCodeCacheDir(), "sample-target-loader-" + applicationTransform.imageSha256().substring(0, 12));
        if (!optimized.exists() && !optimized.mkdirs()) {
            throw new IllegalStateException("Cannot create runtime code cache");
        }

        DexClassLoader classLoader = new DexClassLoader(
                apk.getAbsolutePath(),
                optimized.getAbsolutePath(),
                null,
                context.getClassLoader());

        SyntheticExecutionScope.PackageQueryBackend packageQuery = packageName ->
                SyntheticPackageManagerView.query(
                        broker, sessionId, artifactSha256, planDigest, packageName);

        SyntheticPackageManagerView.QueryResult selfPackage = packageQuery.query(syntheticPackageName);
        if (!selfPackage.found() || selfPackage.projection() == null) {
            throw new SecurityException("Synthetic package metadata disappeared before Application creation");
        }
        String applicationClassName = selfPackage.projection().applicationClassName();
        if (applicationClassName == null || applicationClassName.isBlank()) {
            throw new SecurityException("R10 sample requires a declared Application class");
        }

        // Application is always established before any imported Activity or probe execution.
        ForeignApplicationSession applicationSession = ForeignApplicationSession.create(
                classLoader,
                hostActivity,
                applicationClassName,
                syntheticPackageName,
                packageQuery);

        Class<?> applicationClass = classLoader.loadClass(SAMPLE_APPLICATION);
        Method applicationSnapshot = applicationClass.getMethod("snapshot");
        String applicationMarker = (String) applicationSnapshot.invoke(null);
        String expectedApplicationPrefix = "created=true;package=" + syntheticPackageName
                + ";bridgePerm=denied;connectivity=host;bridgeBind=denied;";
        if (applicationMarker == null || !applicationMarker.startsWith(expectedApplicationPrefix)) {
            throw new SecurityException("Imported Application did not consume the restricted synthetic environment");
        }

        Class<?> entry = classLoader.loadClass(SAMPLE_ENTRY);
        Method pureMarker = entry.getMethod("pureMarker");
        String codeMarker = (String) pureMarker.invoke(null);

        Method run = entry.getMethod("run", Context.class);
        String appViewMarker;
        try (SyntheticExecutionScope.Scope ignored = SyntheticExecutionScope.install(
                syntheticPackageName, packageQuery)) {
            appViewMarker = (String) run.invoke(null, applicationSession.application());
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

        ForeignScreenSession screenSession = ForeignScreenSession.load(
                classLoader, syntheticPackageName, packageQuery);
        String screenCreateMarker = screenSession.onCreate(applicationSession.application());

        ForeignActivitySession ordinaryActivitySession = ForeignActivitySession.load(
                classLoader,
                hostActivity,
                applicationSession.application(),
                syntheticPackageName,
                packageQuery);
        ordinaryActivitySession.onCreate(null);

        return new Result(
                callsiteTransform.sourceSha256(),
                applicationTransform.imageSha256(),
                callsiteTransform.packageManagerCallsites(),
                callsiteTransform.contextIdentityCallsites(),
                callsiteTransform.safeStubCallsites(),
                callsiteTransform.hostRedirectCallsites(),
                activityTransform.rewrittenActivityClasses(),
                applicationTransform.rewrittenApplicationClasses(),
                applicationClassName,
                applicationMarker,
                codeMarker,
                appViewMarker,
                resourceMarker,
                screenCreateMarker,
                applicationSession,
                screenSession,
                ordinaryActivitySession);
    }

    private RuntimeArtifactProbe() {}
}
