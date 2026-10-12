package com.fixhua.core;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public final class HostVerificationReportTest {
    @Test
    public void reportContainsOnlyBoundedCompatibilityFactsAndComparison() {
        HostVerificationSnapshot before = new HostVerificationSnapshot(
                4, 32, 9, true, false, true, false, true,
                List.of("DEV_PROCESS_LIMIT_OVERRIDE", "FORCE_4X_MSAA"));
        HostVerificationSnapshot after = new HostVerificationSnapshot(
                32, 32, 9, false, false, true, false, true,
                List.of());
        HostFixVerification.Result comparison = new HostFixVerification().compare(before, after);

        String report = HostVerificationReport.render(after, comparison);

        assertTrue(report.contains("process_limit_restored_to_oem=true"));
        assertTrue(report.contains("force_4x_msaa_disabled=true"));
        assertTrue(report.contains("verified_improvement=true"));
        assertFalse(report.contains("JLN-L21"));
        assertFalse(report.toLowerCase().contains("serial"));
        assertFalse(report.contains("com.gbox.android"));
        assertFalse(report.contains("Input dispatching timed out"));
        assertFalse(report.contains("Application.getResources"));
    }

    @Test
    public void firstSnapshotIsExplicitlyBaselineOnly() {
        HostVerificationSnapshot current = new HostVerificationSnapshot(
                4, 32, 9, true, false, true, false, true,
                List.of("DEV_PROCESS_LIMIT_OVERRIDE"));

        String report = HostVerificationReport.render(current, null);

        assertTrue(report.contains("baseline=none"));
        assertTrue(report.contains("verified_improvement=false"));
    }
}
