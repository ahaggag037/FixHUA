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
}
