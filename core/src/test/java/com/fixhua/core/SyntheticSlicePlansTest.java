package com.fixhua.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.fixhua.core.CoreModels.ResolutionPlan;
import com.fixhua.core.CoreModels.ResolutionStep;
import org.junit.Test;

public final class SyntheticSlicePlansTest {
    @Test
    public void artifactReadPlanIsDeterministicAndHostDelegated() throws Exception {
        ResolutionPlan first = SyntheticSlicePlans.artifactReadPlan();
        ResolutionPlan second = SyntheticSlicePlans.artifactReadPlan();

        assertEquals(first.digestSha256(), second.digestSha256());
        assertEquals(64, first.digestSha256().length());
        assertEquals(1, first.steps().size());

        ResolutionStep step = first.steps().get(0);
        assertEquals(SyntheticSlicePlans.CAPABILITY_READ_IMPORTED_ARTIFACT, step.capabilityId());
        assertEquals("controller.readonly-artifact-fd", step.providerId());
        assertEquals(CoreModels.TruthScope.HOST_DELEGATED, step.truthScope());
        assertEquals(CoreModels.ProviderSemantics.EXACT, step.semantics());
        assertFalse(first.digestSha256().isBlank());
    }

    @Test
    public void runtimeSessionPlanIncludesHostFactsSyntheticRealitySafeStubAndHostRedirect()
            throws Exception {
        ResolutionPlan first = SyntheticSlicePlans.runtimeSessionPlan();
        ResolutionPlan second = SyntheticSlicePlans.runtimeSessionPlan();
        assertEquals(first.digestSha256(), second.digestSha256());
        assertEquals(8, first.steps().size());

        ResolutionStep hostFact = first.steps().get(0);
        ResolutionStep connectivity = first.steps().get(1);
        ResolutionStep artifact = first.steps().get(2);
        ResolutionStep identity = first.steps().get(3);
        ResolutionStep query = first.steps().get(4);
        ResolutionStep contextIdentity = first.steps().get(5);
        ResolutionStep safeStub = first.steps().get(6);
        ResolutionStep slice = first.steps().get(7);

        assertEquals(SyntheticSlicePlans.CAPABILITY_HOST_GBOX_PACKAGE_FACT, hostFact.capabilityId());
        assertEquals("controller.host-gbox-package-fact", hostFact.providerId());
        assertEquals(CoreModels.AuthorityPlane.P0_PUBLIC, hostFact.authorityPlane());
        assertEquals(CoreModels.TruthScope.HOST_REAL, hostFact.truthScope());

        assertEquals(SyntheticSlicePlans.CAPABILITY_HOST_CONNECTIVITY_SERVICE, connectivity.capabilityId());
        assertEquals("runtime.host-connectivity-service-v1", connectivity.providerId());
        assertEquals(CoreModels.AuthorityPlane.P0_PUBLIC, connectivity.authorityPlane());
        assertEquals(CoreModels.TruthScope.HOST_DELEGATED, connectivity.truthScope());

        assertEquals(SyntheticSlicePlans.CAPABILITY_READ_IMPORTED_ARTIFACT, artifact.capabilityId());
        assertEquals("controller.readonly-artifact-fd", artifact.providerId());
        assertEquals(CoreModels.TruthScope.HOST_DELEGATED, artifact.truthScope());

        assertEquals(SyntheticSlicePlans.CAPABILITY_SYNTHETIC_PACKAGE_IDENTITY, identity.capabilityId());
        assertEquals("runtime.synthetic-package-identity", identity.providerId());
        assertEquals(CoreModels.AuthorityPlane.P5_SYNTHETIC, identity.authorityPlane());
        assertEquals(CoreModels.TruthScope.SYNTHETIC_VIEW, identity.truthScope());

        assertEquals(SyntheticSlicePlans.CAPABILITY_SYNTHETIC_PACKAGE_QUERY, query.capabilityId());
        assertEquals("runtime.synthetic-package-query", query.providerId());
        assertEquals(CoreModels.AuthorityPlane.P5_SYNTHETIC, query.authorityPlane());
        assertEquals(CoreModels.TruthScope.SYNTHETIC_VIEW, query.truthScope());

        assertEquals(SyntheticSlicePlans.CAPABILITY_SYNTHETIC_CONTEXT_IDENTITY, contextIdentity.capabilityId());
        assertEquals("runtime.synthetic-context-identity", contextIdentity.providerId());
        assertEquals(CoreModels.AuthorityPlane.P5_SYNTHETIC, contextIdentity.authorityPlane());
        assertEquals(CoreModels.TruthScope.SYNTHETIC_VIEW, contextIdentity.truthScope());

        assertEquals(SyntheticSlicePlans.CAPABILITY_SAFE_STUB_PERMISSION, safeStub.capabilityId());
        assertEquals("runtime.safe-stub-permission-v1", safeStub.providerId());
        assertEquals(CoreModels.AuthorityPlane.P5_SYNTHETIC, safeStub.authorityPlane());
        assertEquals(CoreModels.TruthScope.SYNTHETIC_VIEW, safeStub.truthScope());

        assertEquals(SyntheticSlicePlans.CAPABILITY_SYNTHETIC_SLICE_V1, slice.capabilityId());
        assertEquals("runtime.synthetic-slice-v1", slice.providerId());
        assertEquals(CoreModels.TruthScope.SYNTHETIC_VIEW, slice.truthScope());
    }
}
