package com.fixhua.diagnostics;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * Keeps the diagnostic feature contract explicit at unit-test level.
 * Runtime Android wiring is additionally validated by compilation/lint.
 */
public class UiFeatureContractTest {
    @Test
    public void diagnosticBundleDeclaresPrivacyBoundary() {
        String privacy = DiagnosticFeatureContract.PRIVACY_BOUNDARY;
        assertTrue(privacy.contains("messages"));
        assertTrue(privacy.contains("location"));
        assertTrue(privacy.contains("passwords are never collected"));
    }

    @Test
    public void requiredUserActionsAreAllDeclared() {
        assertTrue(DiagnosticFeatureContract.REQUIRED_ACTIONS.contains("root_probe"));
        assertTrue(DiagnosticFeatureContract.REQUIRED_ACTIONS.contains("guard_start"));
        assertTrue(DiagnosticFeatureContract.REQUIRED_ACTIONS.contains("diagnostic_toggle"));
        assertTrue(DiagnosticFeatureContract.REQUIRED_ACTIONS.contains("lag_marker"));
        assertTrue(DiagnosticFeatureContract.REQUIRED_ACTIONS.contains("report_copy"));
        assertTrue(DiagnosticFeatureContract.REQUIRED_ACTIONS.contains("guard_stop"));
    }
}
