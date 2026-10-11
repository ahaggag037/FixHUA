package com.fixhua.runtime;

import android.content.Context;
import android.content.pm.PackageManager;

import com.fixhua.core.CoreModels;
import com.fixhua.core.ExecutionEvidence;
import com.fixhua.core.SyntheticSlicePlans;

/** Entry points used when transformed foreign code asks Context for guest-facing facts. */
public final class ContextHooks {
    /**
     * Synthetic replacement for Context#getPackageName(). The host Context argument is ignored so
     * transformed guest code cannot accidentally inherit the Runtime package identity.
     */
    public static String getPackageName(Context ignoredHostContext) {
        String packageName = SyntheticExecutionScope.require().packageName();
        ExecutionEvidenceEmitter.emit(
                SyntheticSlicePlans.CAPABILITY_SYNTHETIC_CONTEXT_IDENTITY,
                "runtime.synthetic-context-identity",
                ExecutionEvidence.Decision.SYNTHETIC,
                CoreModels.TruthScope.SYNTHETIC_VIEW,
                "context-package-name",
                packageName);
        return packageName;
    }

    /**
     * Neutral SAFE_STUB for permission perception. Synthetic Slice V1 never fabricates a grant:
     * permission checks are denied unless a future recipe/provider explicitly supplies semantics.
     */
    public static int checkSelfPermission(Context ignoredHostContext, String permission) {
        SyntheticExecutionScope.require();
        String subject = permission == null || permission.isBlank()
                ? "permission:unknown"
                : "permission:" + safeSubject(permission);
        ExecutionEvidenceEmitter.emit(
                SyntheticSlicePlans.CAPABILITY_SAFE_STUB_PERMISSION,
                "runtime.safe-stub-permission-v1",
                ExecutionEvidence.Decision.SAFE_STUB,
                CoreModels.TruthScope.SYNTHETIC_VIEW,
                subject,
                "DENIED");
        return PackageManager.PERMISSION_DENIED;
    }

    /**
     * Typed host-service redirect. Slice V1 delegates only connectivity and fails closed for every
     * other service name so imported code cannot inherit the Runtime's entire service surface.
     */
    public static Object getSystemService(Context hostContext, String serviceName) {
        SyntheticExecutionScope.require();
        if (hostContext == null || serviceName == null) {
            ExecutionEvidenceEmitter.emit(
                    SyntheticSlicePlans.CAPABILITY_HOST_CONNECTIVITY_SERVICE,
                    "runtime.host-connectivity-service-v1",
                    ExecutionEvidence.Decision.UNSUPPORTED,
                    CoreModels.TruthScope.HOST_DELEGATED,
                    "system-service:null",
                    "NULL");
            return null;
        }
        if (!Context.CONNECTIVITY_SERVICE.equals(serviceName)) {
            ExecutionEvidenceEmitter.emit(
                    SyntheticSlicePlans.CAPABILITY_HOST_CONNECTIVITY_SERVICE,
                    "runtime.host-connectivity-service-v1",
                    ExecutionEvidence.Decision.UNSUPPORTED,
                    CoreModels.TruthScope.HOST_DELEGATED,
                    "system-service:" + safeSubject(serviceName),
                    "NOT_ALLOWLISTED");
            return null;
        }
        Object service = hostContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        ExecutionEvidenceEmitter.emit(
                SyntheticSlicePlans.CAPABILITY_HOST_CONNECTIVITY_SERVICE,
                "runtime.host-connectivity-service-v1",
                ExecutionEvidence.Decision.DELEGATED,
                CoreModels.TruthScope.HOST_DELEGATED,
                "system-service:connectivity",
                service == null ? "MISSING" : "PRESENT");
        return service;
    }

    private static String safeSubject(String value) {
        String sanitized = value.replace('|', '_').replace('\n', '_').replace('\r', '_');
        return sanitized.length() <= 120 ? sanitized : sanitized.substring(0, 120);
    }

    private ContextHooks() {}
}
