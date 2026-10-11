package com.fixhua.runtime;

import android.content.Context;
import android.content.pm.PackageManager;

/** Entry points used when transformed foreign code asks Context for guest-facing facts. */
public final class ContextHooks {
    /**
     * Synthetic replacement for Context#getPackageName(). The host Context argument is ignored so
     * transformed guest code cannot accidentally inherit the Runtime package identity.
     */
    public static String getPackageName(Context ignoredHostContext) {
        return SyntheticExecutionScope.require().packageName();
    }

    /**
     * Neutral SAFE_STUB for permission perception. Synthetic Slice V1 never fabricates a grant:
     * permission checks are denied unless a future recipe/provider explicitly supplies semantics.
     */
    public static int checkSelfPermission(Context ignoredHostContext, String ignoredPermission) {
        SyntheticExecutionScope.require();
        return PackageManager.PERMISSION_DENIED;
    }

    /**
     * Typed host-service redirect. Slice V1 delegates only connectivity and fails closed for every
     * other service name so imported code cannot inherit the Runtime's entire service surface.
     */
    public static Object getSystemService(Context hostContext, String serviceName) {
        SyntheticExecutionScope.require();
        if (hostContext == null || serviceName == null) return null;
        if (!Context.CONNECTIVITY_SERVICE.equals(serviceName)) return null;
        return hostContext.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    private ContextHooks() {}
}
