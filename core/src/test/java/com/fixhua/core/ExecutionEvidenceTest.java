package com.fixhua.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public final class ExecutionEvidenceTest {
    @Test
    public void canonicalLineIsStableAndTyped() {
        ExecutionEvidence.Event event = new ExecutionEvidence.Event(
                7,
                SyntheticSlicePlans.CAPABILITY_SAFE_STUB_PERMISSION,
                "runtime.safe-stub-permission-v1",
                ExecutionEvidence.Decision.SAFE_STUB,
                CoreModels.TruthScope.SYNTHETIC_VIEW,
                "permission:runtime-bridge",
                "DENIED");

        assertEquals(
                "v=1|seq=7|capability=1104|provider=runtime.safe-stub-permission-v1"
                        + "|decision=SAFE_STUB|truth=SYNTHETIC_VIEW"
                        + "|subject=permission:runtime-bridge|outcome=DENIED",
                event.canonicalLine());
    }

    @Test
    public void reservedDelimiterIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ExecutionEvidence.Event(
                1,
                1104,
                "bad|provider",
                ExecutionEvidence.Decision.SAFE_STUB,
                CoreModels.TruthScope.SYNTHETIC_VIEW,
                "permission",
                "DENIED"));
    }
}
