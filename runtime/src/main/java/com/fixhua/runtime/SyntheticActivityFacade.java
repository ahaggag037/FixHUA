package com.fixhua.runtime;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.View;

import com.fixhua.core.CoreModels;
import com.fixhua.core.ExecutionEvidence;
import com.fixhua.core.SyntheticSlicePlans;

/**
 * Public-API facade used as the rewritten superclass of imported ordinary Activities. The object is
 * never registered with ActivityThread. A real Runtime-owned Activity supplies window/UI/lifecycle,
 * while this facade exposes only the bounded behavior FixHUA has modeled.
 */
public class SyntheticActivityFacade extends Activity {
    private Activity hostActivity;
    private Application syntheticApplication;

    public final void fixhuaAttachHost(Activity host, Application application) {
        if (host == null) throw new IllegalArgumentException("host");
        if (application == null) throw new IllegalArgumentException("application");
        if (hostActivity != null) throw new IllegalStateException("Facade already attached");
        hostActivity = host;
        syntheticApplication = application;
        super.attachBaseContext(new SyntheticRestrictedContext(host, application));
        emit("attach", "READY");
    }

    public final void fixhuaDispatchCreate(Bundle state) {
        requireHost();
        onCreate(state);
        emit("onCreate", "DELIVERED");
    }

    public final void fixhuaDispatchResume() {
        requireHost();
        onResume();
        emit("onResume", "DELIVERED");
    }

    public final void fixhuaDispatchDestroy() {
        if (hostActivity == null) return;
        onDestroy();
        emit("onDestroy", "DELIVERED");
    }

    @Override
    public Context getApplicationContext() {
        if (syntheticApplication != null) return syntheticApplication;
        return super.getApplicationContext();
    }

    /**
     * Super target for transformed `invoke-super Activity.onCreate` calls. This facade instance is
     * deliberately not registered with ActivityThread, so calling Activity.onCreate() would enter a
     * framework lifecycle for an unattached Activity and violate the proxy ownership model.
     */
    @Override
    @SuppressLint("MissingSuperCall")
    protected void onCreate(Bundle state) {
        // Intentionally no framework super-call; RuntimeActivity owns the real Android lifecycle.
    }

    /** Super target for transformed `invoke-super Activity.onResume` calls. */
    @Override
    @SuppressLint("MissingSuperCall")
    protected void onResume() {
        // Intentionally no framework super-call; RuntimeActivity owns the real Android lifecycle.
    }

    /** Super target for transformed `invoke-super Activity.onDestroy` calls. */
    @Override
    @SuppressLint("MissingSuperCall")
    protected void onDestroy() {
        // Intentionally no framework super-call; RuntimeActivity owns the real Android lifecycle.
    }

    @Override
    public void setContentView(View view) {
        requireHost().setContentView(view);
        emit("setContentView", view == null ? "NULL" : "DELIVERED");
    }

    @Override
    public <T extends View> T findViewById(int id) {
        return requireHost().findViewById(id);
    }

    @Override
    public Resources.Theme getTheme() {
        return requireHost().getTheme();
    }

    @Override
    public void finish() {
        requireHost().finish();
    }

    private Activity requireHost() {
        if (hostActivity == null) throw new IllegalStateException("Facade is not attached");
        return hostActivity;
    }

    private static void emit(String phase, String outcome) {
        ExecutionEvidenceEmitter.emit(
                SyntheticSlicePlans.CAPABILITY_SYNTHETIC_SLICE_V1,
                "runtime.activity-facade-v2",
                ExecutionEvidence.Decision.SHIM,
                CoreModels.TruthScope.SYNTHETIC_VIEW,
                "activity-facade:" + phase,
                outcome);
    }
}
