package com.fixhua.runtime;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;

import com.fixhua.core.CoreModels;
import com.fixhua.core.ExecutionEvidence;
import com.fixhua.core.SyntheticSlicePlans;

/**
 * Runtime-owned facade superclass for an imported ordinary Application. The object is not installed
 * into ActivityThread; FixHUA supplies a restricted base Context and explicitly dispatches the
 * bounded lifecycle needed by the managed target.
 */
public class SyntheticApplicationFacade extends Application {
    private boolean attached;
    private boolean created;

    public final void fixhuaAttachHost(Context host) {
        if (host == null) throw new IllegalArgumentException("host");
        if (attached) throw new IllegalStateException("Application facade already attached");
        super.attachBaseContext(new SyntheticRestrictedContext(host, this));
        attached = true;
        emit("attach", "READY");
    }

    public final void fixhuaDispatchCreate() {
        if (!attached) throw new IllegalStateException("Application facade is not attached");
        if (created) throw new IllegalStateException("Application facade already created");
        onCreate();
        created = true;
        emit("onCreate", "DELIVERED");
    }

    @Override
    public Context getApplicationContext() {
        return this;
    }

    /**
     * Super target for transformed `invoke-super Application.onCreate` calls. The real Runtime
     * Application owns framework process lifecycle, so this imported facade must not enter it.
     */
    @Override
    @SuppressLint("MissingSuperCall")
    public void onCreate() {
        // Intentionally empty. Imported subclass code runs around this no-op super target.
    }

    public final boolean fixhuaIsCreated() {
        return created;
    }

    private static void emit(String phase, String outcome) {
        ExecutionEvidenceEmitter.emit(
                SyntheticSlicePlans.CAPABILITY_SYNTHETIC_SLICE_V1,
                "runtime.application-facade-v1",
                ExecutionEvidence.Decision.SHIM,
                CoreModels.TruthScope.SYNTHETIC_VIEW,
                "application-facade:" + phase,
                outcome);
    }
}
