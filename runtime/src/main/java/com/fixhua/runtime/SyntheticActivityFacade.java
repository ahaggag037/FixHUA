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

/** Public-API facade used as the rewritten superclass of imported ordinary Activities. */
public class SyntheticActivityFacade extends Activity {
    private Activity hostActivity;
    private Application syntheticApplication;

    public final void fixhuaAttachHost(Activity host, Application application) {
        if (host == null) throw new IllegalArgumentException("host");
        if (!(application instanceof SyntheticApplicationFacade syntheticApp)) {
            throw new IllegalArgumentException("application must be a FixHUA synthetic Application");
        }
        if (hostActivity != null) throw new IllegalStateException("Facade already attached");
        hostActivity = host;
        syntheticApplication = application;
        super.attachBaseContext(new SyntheticRestrictedContext(
                host, application, syntheticApp.fixhuaDataPlane()));
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

    @Override
    @SuppressLint("MissingSuperCall")
    protected void onCreate(Bundle state) {
        // RuntimeActivity owns the real Android lifecycle.
    }

    @Override
    @SuppressLint("MissingSuperCall")
    protected void onResume() {
        // RuntimeActivity owns the real Android lifecycle.
    }

    @Override
    @SuppressLint("MissingSuperCall")
    protected void onDestroy() {
        // RuntimeActivity owns the real Android lifecycle.
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
