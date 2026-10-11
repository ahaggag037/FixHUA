package com.fixhua.runtime;

import android.app.Application;
import android.content.res.Configuration;
import android.util.Log;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Process-wide lifecycle invariant for the low-privilege synthetic runtime.
 *
 * <p>The target-device bugreport captured repeated virtual-app failures where framework
 * configuration handling observed a null Application. The FixHUA runtime therefore treats an
 * initialized Application + Resources as an explicit invariant rather than an implementation
 * accident.</p>
 */
public final class RuntimeApp extends Application {
    private static final String TAG = "FixHUARuntimeLifecycle";
    private static final AtomicInteger CONFIG_GENERATION = new AtomicInteger();
    private static volatile RuntimeApp instance;
    private static volatile boolean initialized;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        initialized = getResources() != null;
        if (!initialized) {
            throw new IllegalStateException("Runtime Application resources unavailable during onCreate");
        }
        Log.i(TAG, "applicationReady=true process=" + Application.getProcessName()
                + " generation=" + CONFIG_GENERATION.get());
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        assertReady("applicationConfigurationChanged");
        int generation = CONFIG_GENERATION.incrementAndGet();
        super.onConfigurationChanged(newConfig);
        Log.i(TAG, "applicationConfigChanged=true generation=" + generation
                + " orientation=" + newConfig.orientation);
    }

    static RuntimeApp requireInstance(String phase) {
        RuntimeApp app = instance;
        if (!initialized || app == null || app.getResources() == null) {
            throw new IllegalStateException("Runtime Application invariant failed at " + phase);
        }
        return app;
    }

    static void assertReady(String phase) {
        requireInstance(phase);
        Log.i(TAG, "applicationInvariant=true phase=" + phase
                + " generation=" + CONFIG_GENERATION.get());
    }

    static int configurationGeneration() {
        return CONFIG_GENERATION.get();
    }
}
