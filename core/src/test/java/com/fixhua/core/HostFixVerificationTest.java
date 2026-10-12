package com.fixhua.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public final class HostFixVerificationTest {
    private final HostFixVerification verifier = new HostFixVerification();

    @Test
    public void jlnLikeFix_standardLimitAndMsaaOff_areVerifiedWithoutCrashClaims() {
        HostVerificationSnapshot before = new HostVerificationSnapshot(
                4,
                32,
                9,
                true,
                false,
                true,
                false,
                true,
                List.of(
                        "DEV_PROCESS_LIMIT_OVERRIDE",
                        "FORCE_4X_MSAA",
                        "RUNTIME_PROCESS_BUDGET_CONFLICT"));
        HostVerificationSnapshot after = new HostVerificationSnapshot(
                32,
                32,
                9,
                false,
                false,
                true,
                false,
                true,
                List.of());

        HostFixVerification.Result result = verifier.compare(before, after);

        assertTrue(result.hasVerifiedImprovement());
        assertTrue(result.processLimitChanged());
        assertTrue(result.processLimitRestoredToOem());
        assertTrue(result.force4xMsaaDisabled());
        assertFalse(result.alwaysFinishActivitiesDisabled());
        assertEquals(
                List.of(
                        "DEV_PROCESS_LIMIT_OVERRIDE",
                        "FORCE_4X_MSAA",
                        "RUNTIME_PROCESS_BUDGET_CONFLICT"),
                result.resolvedFindingCodes());
        assertTrue(result.remainingFindingCodes().isEmpty());
        assertTrue(result.introducedFindingCodes().isEmpty());
    }

    @Test
    public void newFinding_isReportedAsNewProblem_notSilentlyFoldedIntoImprovement() {
        HostVerificationSnapshot before = snapshot(32, false, List.of());
        HostVerificationSnapshot after = snapshot(32, false, List.of("WEBVIEW_DIRTY"));

        HostFixVerification.Result result = verifier.compare(before, after);

        assertFalse(result.hasVerifiedImprovement());
        assertTrue(result.hasNewProblem());
        assertEquals(List.of("WEBVIEW_DIRTY"), result.introducedFindingCodes());
    }

    @Test
    public void snapshotCodec_roundTripsOnlyBoundedCompatibilityFacts() {
        HostVerificationSnapshot original = new HostVerificationSnapshot(
                4,
                32,
                9,
                true,
                false,
                true,
                false,
                true,
                List.of("RUNTIME_PROCESS_BUDGET_CONFLICT", "DEV_PROCESS_LIMIT_OVERRIDE"));

        String encoded = original.encode();
        HostVerificationSnapshot decoded = HostVerificationSnapshot.decode(encoded);

        assertEquals(original, decoded);
        assertFalse(encoded.contains("JLN-L21"));
        assertFalse(encoded.contains("serial"));
        assertFalse(encoded.contains("com.gbox.android"));
        assertFalse(encoded.contains("crash"));
        assertFalse(encoded.contains("anr"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void malformedSnapshot_isRejected() {
        HostVerificationSnapshot.decode("HVS1;cur=4;garbage=true");
    }

    private static HostVerificationSnapshot snapshot(
            int processLimit,
            boolean msaa,
            List<String> findingCodes) {
        return new HostVerificationSnapshot(
                processLimit,
                32,
                9,
                msaa,
                false,
                true,
                false,
                true,
                findingCodes);
    }
}
