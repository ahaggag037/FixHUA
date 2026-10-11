package com.fixhua.runtime;

import android.content.Context;

import com.fixhua.core.CoreModels;
import com.fixhua.core.ExecutionEvidence;
import com.fixhua.core.SyntheticSlicePlans;

/** Owns the imported Application object and its signer-bound data plane for one Runtime session. */
final class ForeignApplicationSession {
    private final SyntheticApplicationFacade application;
    private final GuestDataPlane dataPlane;
    private final String declaredClassName;
    private final String syntheticPackageName;
    private final SyntheticExecutionScope.PackageQueryBackend packageQuery;

    private ForeignApplicationSession(
            SyntheticApplicationFacade application,
            GuestDataPlane dataPlane,
            String declaredClassName,
            String syntheticPackageName,
            SyntheticExecutionScope.PackageQueryBackend packageQuery) {
        this.application = application;
        this.dataPlane = dataPlane;
        this.declaredClassName = declaredClassName == null ? "" : declaredClassName;
        this.syntheticPackageName = syntheticPackageName;
        this.packageQuery = packageQuery;
    }

    static ForeignApplicationSession create(
            ClassLoader classLoader,
            Context hostContext,
            String declaredClassName,
            String syntheticPackageName,
            SyntheticExecutionScope.PackageQueryBackend packageQuery) throws Exception {
        SyntheticPackageManagerView.QueryResult self = packageQuery.query(syntheticPackageName);
        if (!self.found() || self.projection() == null) {
            throw new SecurityException("Synthetic package disappeared before guest data creation");
        }
        String signerSha256 = self.projection().signingSha256();
        GuestDataPlane dataPlane = GuestDataPlane.create(hostContext, syntheticPackageName, signerSha256);

        SyntheticApplicationFacade facade;
        if (declaredClassName == null || declaredClassName.isBlank()) {
            facade = new SyntheticApplicationFacade();
        } else {
            Class<?> appClass = classLoader.loadClass(declaredClassName);
            Object instance = appClass.getConstructor().newInstance();
            if (!(instance instanceof SyntheticApplicationFacade rewritten)) {
                throw new SecurityException("Declared Application was not rewritten to FixHUA facade");
            }
            facade = rewritten;
        }

        ForeignApplicationSession session = new ForeignApplicationSession(
                facade, dataPlane, declaredClassName, syntheticPackageName, packageQuery);
        try (SyntheticExecutionScope.Scope ignored = SyntheticExecutionScope.install(
                syntheticPackageName, packageQuery)) {
            facade.fixhuaAttachHost(hostContext, dataPlane);
            facade.fixhuaDispatchCreate();
        }
        if (!facade.fixhuaIsCreated()) {
            throw new SecurityException("Imported Application did not reach created state");
        }
        emit("create", declaredClassName == null || declaredClassName.isBlank()
                ? "BASE_SYNTHETIC" : declaredClassName);
        ExecutionEvidenceEmitter.emit(
                SyntheticSlicePlans.CAPABILITY_SYNTHETIC_SLICE_V1,
                "runtime.guest-data-v1",
                ExecutionEvidence.Decision.SHIM,
                CoreModels.TruthScope.SYNTHETIC_VIEW,
                "guest-data:identity",
                "READY:" + dataPlane.identityDigest().substring(0, 12));
        return session;
    }

    SyntheticApplicationFacade application() { return application; }
    GuestDataPlane dataPlane() { return dataPlane; }
    String declaredClassName() { return declaredClassName; }
    String syntheticPackageName() { return syntheticPackageName; }

    private static void emit(String phase, String outcome) {
        ExecutionEvidenceEmitter.emit(
                SyntheticSlicePlans.CAPABILITY_SYNTHETIC_SLICE_V1,
                "runtime.application-session-v1",
                ExecutionEvidence.Decision.SHIM,
                CoreModels.TruthScope.SYNTHETIC_VIEW,
                "application-session:" + phase,
                sanitize(outcome));
    }

    private static String sanitize(String value) {
        String out = value == null ? "" : value.replace('|', '_').replace('\n', '_').replace('\r', '_');
        return out.length() <= 160 ? out : out.substring(0, 160);
    }
}
