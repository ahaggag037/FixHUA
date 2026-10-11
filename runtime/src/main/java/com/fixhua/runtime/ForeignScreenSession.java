package com.fixhua.runtime;

import android.content.Context;

import com.fixhua.core.CoreModels;
import com.fixhua.core.ExecutionEvidence;
import com.fixhua.core.SyntheticSlicePlans;

import java.lang.reflect.Method;

/**
 * Public-API lifecycle delegation for one imported screen object. The real Android Activity stays
 * owned by FixHUA Runtime; imported code receives only the lifecycle callbacks we explicitly
 * expose, always inside the active synthetic execution environment.
 */
final class ForeignScreenSession {
    private static final String SCREEN_CLASS = "com.fixhua.sampletarget.ProbeScreenDelegate";

    private final Object target;
    private final Method onCreate;
    private final Method onResume;
    private final Method onConfigurationChanged;
    private final Method onDestroy;
    private final Method snapshot;
    private final String syntheticPackageName;
    private final SyntheticExecutionScope.PackageQueryBackend packageQuery;
    private boolean created;
    private boolean destroyed;

    private ForeignScreenSession(
            Object target,
            Method onCreate,
            Method onResume,
            Method onConfigurationChanged,
            Method onDestroy,
            Method snapshot,
            String syntheticPackageName,
            SyntheticExecutionScope.PackageQueryBackend packageQuery) {
        this.target = target;
        this.onCreate = onCreate;
        this.onResume = onResume;
        this.onConfigurationChanged = onConfigurationChanged;
        this.onDestroy = onDestroy;
        this.snapshot = snapshot;
        this.syntheticPackageName = syntheticPackageName;
        this.packageQuery = packageQuery;
    }

    static ForeignScreenSession load(
            ClassLoader classLoader,
            String syntheticPackageName,
            SyntheticExecutionScope.PackageQueryBackend packageQuery) throws Exception {
        Class<?> screen = classLoader.loadClass(SCREEN_CLASS);
        Object target = screen.getConstructor().newInstance();
        return new ForeignScreenSession(
                target,
                screen.getMethod("onCreate", Context.class),
                screen.getMethod("onResume"),
                screen.getMethod("onConfigurationChanged", int.class),
                screen.getMethod("onDestroy"),
                screen.getMethod("snapshot"),
                syntheticPackageName,
                packageQuery);
    }

    String onCreate(Context context) throws Exception {
        if (created) throw new IllegalStateException("Foreign screen already created");
        if (destroyed) throw new IllegalStateException("Foreign screen already destroyed");
        String marker = invoke(onCreate, context);
        created = true;
        emitLifecycle("onCreate", marker);
        return marker;
    }

    String onResume() throws Exception {
        requireAlive();
        String marker = invoke(onResume);
        emitLifecycle("onResume", marker);
        return marker;
    }

    String onConfigurationChanged(int orientation) throws Exception {
        requireAlive();
        String marker = invoke(onConfigurationChanged, orientation);
        emitLifecycle("onConfigurationChanged", marker);
        return marker;
    }

    String onDestroy() throws Exception {
        if (!created || destroyed) return snapshot();
        String marker = invoke(onDestroy);
        destroyed = true;
        emitLifecycle("onDestroy", marker);
        return marker;
    }

    String snapshot() throws Exception {
        try (SyntheticExecutionScope.Scope ignored = SyntheticExecutionScope.install(
                syntheticPackageName, packageQuery)) {
            return (String) snapshot.invoke(target);
        }
    }

    private String invoke(Method method, Object... args) throws Exception {
        try (SyntheticExecutionScope.Scope ignored = SyntheticExecutionScope.install(
                syntheticPackageName, packageQuery)) {
            return (String) method.invoke(target, args);
        }
    }

    private void emitLifecycle(String phase, String marker) {
        ExecutionEvidenceEmitter.emit(
                SyntheticSlicePlans.CAPABILITY_SYNTHETIC_SLICE_V1,
                "runtime.proxy-lifecycle-v1",
                ExecutionEvidence.Decision.SHIM,
                CoreModels.TruthScope.SYNTHETIC_VIEW,
                "proxy-lifecycle:" + phase,
                marker);
    }

    private void requireAlive() {
        if (!created) throw new IllegalStateException("Foreign screen not created");
        if (destroyed) throw new IllegalStateException("Foreign screen destroyed");
    }
}
