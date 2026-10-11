package com.fixhua.runtime;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;

import com.fixhua.core.CoreModels;
import com.fixhua.core.ExecutionEvidence;
import com.fixhua.core.SyntheticSlicePlans;

/** Runtime-owned facade superclass for an imported ordinary Application. */
public class SyntheticApplicationFacade extends Application {
    private boolean attached;
    private boolean created;
    private GuestDataPlane dataPlane;

    public final void fixhuaAttachHost(Context host, GuestDataPlane guestDataPlane) {
        if (host == null) throw new IllegalArgumentException("host");
        if (guestDataPlane == null) throw new IllegalArgumentException("guestDataPlane");
        if (attached) throw new IllegalStateException("Application facade already attached");
        dataPlane = guestDataPlane;
        super.attachBaseContext(new SyntheticRestrictedContext(host, this, guestDataPlane));
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

    @Override
    @SuppressLint("MissingSuperCall")
    public void onCreate() {
        // RuntimeApp owns the real process lifecycle. Imported subclass code runs around this no-op.
    }

    public final boolean fixhuaIsCreated() { return created; }

    final GuestDataPlane fixhuaDataPlane() {
        if (dataPlane == null) throw new IllegalStateException("Application data plane not attached");
        return dataPlane;
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
