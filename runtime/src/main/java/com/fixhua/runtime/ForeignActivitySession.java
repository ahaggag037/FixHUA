package com.fixhua.runtime;

import android.app.Activity;
import android.os.Bundle;

import com.fixhua.core.CoreModels;
import com.fixhua.core.ExecutionEvidence;
import com.fixhua.core.SyntheticSlicePlans;

/** Hosts one ordinary Activity class that was rewritten to SyntheticActivityFacade. */
final class ForeignActivitySession {
    private static final String ACTIVITY_CLASS = "com.fixhua.sampletarget.SampleActivity";

    private final SyntheticActivityFacade target;
    private final String syntheticPackageName;
    private final SyntheticExecutionScope.PackageQueryBackend packageQuery;
    private boolean created;
    private boolean destroyed;

    private ForeignActivitySession(
            SyntheticActivityFacade target,
            String syntheticPackageName,
            SyntheticExecutionScope.PackageQueryBackend packageQuery) {
        this.target = target;
        this.syntheticPackageName = syntheticPackageName;
        this.packageQuery = packageQuery;
    }

    static ForeignActivitySession load(
            ClassLoader classLoader,
            Activity host,
            String syntheticPackageName,
            SyntheticExecutionScope.PackageQueryBackend packageQuery) throws Exception {
        Class<?> activityClass = classLoader.loadClass(ACTIVITY_CLASS);
        Object instance = activityClass.getConstructor().newInstance();
        if (!(instance instanceof SyntheticActivityFacade facade)) {
            throw new SecurityException("Ordinary Activity was not rewritten to FixHUA facade");
        }
        ForeignActivitySession session = new ForeignActivitySession(
                facade, syntheticPackageName, packageQuery);
        try (SyntheticExecutionScope.Scope ignored = SyntheticExecutionScope.install(
                syntheticPackageName, packageQuery)) {
            facade.fixhuaAttachHost(host);
        }
        return session;
    }

    void onCreate(Bundle state) throws Exception {
        if (created) throw new IllegalStateException("Foreign Activity already created");
        if (destroyed) throw new IllegalStateException("Foreign Activity already destroyed");
        invoke(() -> target.fixhuaDispatchCreate(state));
        created = true;
        emit("onCreate", "DELIVERED");
    }

    void onResume() throws Exception {
        requireAlive();
        invoke(target::fixhuaDispatchResume);
        emit("onResume", "DELIVERED");
    }

    void onDestroy() throws Exception {
        if (!created || destroyed) return;
        invoke(target::fixhuaDispatchDestroy);
        destroyed = true;
        emit("onDestroy", "DELIVERED");
    }

    private void invoke(ThrowingRunnable action) throws Exception {
        try (SyntheticExecutionScope.Scope ignored = SyntheticExecutionScope.install(
                syntheticPackageName, packageQuery)) {
            action.run();
        }
    }

    private static void emit(String phase, String outcome) {
        ExecutionEvidenceEmitter.emit(
                SyntheticSlicePlans.CAPABILITY_SYNTHETIC_SLICE_V1,
                "runtime.ordinary-activity-session-v1",
                ExecutionEvidence.Decision.SHIM,
                CoreModels.TruthScope.SYNTHETIC_VIEW,
                "ordinary-activity:" + phase,
                outcome);
    }

    private void requireAlive() {
        if (!created) throw new IllegalStateException("Foreign Activity not created");
        if (destroyed) throw new IllegalStateException("Foreign Activity destroyed");
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
