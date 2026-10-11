package com.fixhua.core;

import static com.fixhua.core.CoreModels.AuthorityPlane.P0_PUBLIC;
import static com.fixhua.core.CoreModels.Persistence.SESSION;
import static com.fixhua.core.CoreModels.ProviderSemantics.EXACT;
import static com.fixhua.core.CoreModels.TruthScope.HOST_DELEGATED;

import java.util.List;
import java.util.Set;

/** Frozen minimal plans used by Synthetic Slice V1. */
public final class SyntheticSlicePlans {
    public static final int CAPABILITY_READ_IMPORTED_ARTIFACT = 1001;
    public static final String RECIPE_ID = "sample.synthetic.v1";
    public static final int RECIPE_VERSION = 1;

    public static CoreModels.ResolutionPlan artifactReadPlan()
            throws CapabilityResolver.ResolutionException {
        CoreModels.ProviderDescriptor provider = new CoreModels.ProviderDescriptor(
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
        CoreModels.CapabilityRequest request = new CoreModels.CapabilityRequest(
                CAPABILITY_READ_IMPORTED_ARTIFACT,
                Set.of(HOST_DELEGATED),
                EXACT,
                false);
        return new CapabilityResolver().resolve(request, List.of(provider));
    }

    private SyntheticSlicePlans() {}
}
