package com.fixhua.core;

import static com.fixhua.core.CoreModels.AuthorityPlane.P0_PUBLIC;
import static com.fixhua.core.CoreModels.AuthorityPlane.P5_SYNTHETIC;
import static com.fixhua.core.CoreModels.Persistence.SESSION;
import static com.fixhua.core.CoreModels.ProviderSemantics.EXACT;
import static com.fixhua.core.CoreModels.TruthScope.HOST_DELEGATED;
import static com.fixhua.core.CoreModels.TruthScope.SYNTHETIC_VIEW;

import java.util.List;
import java.util.Set;

/** Frozen minimal plans used by Synthetic Slice V1. */
public final class SyntheticSlicePlans {
    public static final int CAPABILITY_READ_IMPORTED_ARTIFACT = 1001;
    public static final int CAPABILITY_SYNTHETIC_PACKAGE_IDENTITY = 1101;
    public static final String RECIPE_ID = "sample.synthetic.v1";
    public static final int RECIPE_VERSION = 2;

    private static CoreModels.ProviderDescriptor artifactProvider() {
        return new CoreModels.ProviderDescriptor(
                "controller.readonly-artifact-fd",
                "1",
                P0_PUBLIC,
                Set.of(CAPABILITY_READ_IMPORTED_ARTIFACT),
                Set.of(),
                Set.of(HOST_DELEGATED),
                EXACT,
                SESSION,
                false,
                0,
                0);
    }

    private static CoreModels.ProviderDescriptor packageIdentityProvider() {
        return new CoreModels.ProviderDescriptor(
                "runtime.synthetic-package-identity",
                "1",
                P5_SYNTHETIC,
                Set.of(CAPABILITY_SYNTHETIC_PACKAGE_IDENTITY),
                Set.of(CAPABILITY_READ_IMPORTED_ARTIFACT),
                Set.of(SYNTHETIC_VIEW),
                EXACT,
                SESSION,
                false,
                0,
                0);
    }

    public static CoreModels.ResolutionPlan artifactReadPlan()
            throws CapabilityResolver.ResolutionException {
        CoreModels.CapabilityRequest request = new CoreModels.CapabilityRequest(
                CAPABILITY_READ_IMPORTED_ARTIFACT,
                Set.of(HOST_DELEGATED),
                EXACT,
                false);
        return new CapabilityResolver().resolve(request, List.of(artifactProvider()));
    }

    public static CoreModels.ResolutionPlan runtimeSessionPlan()
            throws CapabilityResolver.ResolutionException {
        CoreModels.CapabilityRequest request = new CoreModels.CapabilityRequest(
                CAPABILITY_SYNTHETIC_PACKAGE_IDENTITY,
                Set.of(SYNTHETIC_VIEW),
                EXACT,
                false);
        return new CapabilityResolver().resolve(
                request,
                List.of(artifactProvider(), packageIdentityProvider()));
    }

    private SyntheticSlicePlans() {}
}
