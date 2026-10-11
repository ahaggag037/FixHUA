package com.fixhua.runtime;

import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.UserHandle;

/**
 * Context boundary presented to transformed ordinary Activities. It exposes only explicitly modeled
 * compatibility surfaces and prevents imported code from inheriting Runtime-family IPC authority.
 */
final class SyntheticRestrictedContext extends ContextWrapper {
    SyntheticRestrictedContext(Context host) {
        super(host);
    }

    @Override
    public String getPackageName() {
        return ContextHooks.getPackageName(this);
    }

    @Override
    public Context getApplicationContext() {
        return this;
    }

    @Override
    public int checkSelfPermission(String permission) {
        return ContextHooks.checkSelfPermission(this, permission);
    }

    @Override
    public Object getSystemService(String name) {
        return ContextHooks.getSystemService(getBaseContext(), name);
    }

    @Override
    public boolean bindService(Intent service, ServiceConnection conn, int flags) {
        emitBlockedBinding(service);
        return false;
    }

    @Override
    public boolean bindService(
            Intent service,
            int flags,
            java.util.concurrent.Executor executor,
            ServiceConnection conn) {
        emitBlockedBinding(service);
        return false;
    }

    @Override
    public boolean bindIsolatedService(
            Intent service,
            int flags,
            String instanceName,
            java.util.concurrent.Executor executor,
            ServiceConnection conn) {
        emitBlockedBinding(service);
        return false;
    }

    @Override
    public ComponentName startService(Intent service) {
        throw new SecurityException("Imported Activity cannot start arbitrary host services");
    }

    @Override
    public ComponentName startForegroundService(Intent service) {
        throw new SecurityException("Imported Activity cannot start arbitrary host foreground services");
    }

    private static void emitBlockedBinding(Intent intent) {
        String target = "implicit";
        if (intent != null && intent.getComponent() != null) {
            target = intent.getComponent().flattenToShortString();
        }
        ExecutionEvidenceEmitter.emit(
                com.fixhua.core.SyntheticSlicePlans.CAPABILITY_SYNTHETIC_SLICE_V1,
                "runtime.restricted-context-v1",
                com.fixhua.core.ExecutionEvidence.Decision.UNSUPPORTED,
                com.fixhua.core.CoreModels.TruthScope.SYNTHETIC_VIEW,
                "bind-service",
                "DENIED:" + sanitize(target));
    }

    private static String sanitize(String value) {
        String out = value.replace('|', '_').replace('\n', '_').replace('\r', '_');
        return out.length() <= 120 ? out : out.substring(0, 120);
    }
}
