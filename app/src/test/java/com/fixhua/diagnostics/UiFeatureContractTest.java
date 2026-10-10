package com.fixhua.diagnostics;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UiFeatureContractTest {
    @Test
    public void diagnosticBundleDeclaresPrivacyBoundary() {
        String privacy = DiagnosticFeatureContract.PRIVACY_BOUNDARY;
        assertTrue(privacy.contains("messages"));
        assertTrue(privacy.contains("location"));
        assertTrue(privacy.contains("passwords are never collected"));
    }

    @Test
    public void manualLagMarkerIsNotPartOfRequiredActions() {
        assertFalse(DiagnosticFeatureContract.REQUIRED_ACTIONS.contains("lag_marker"));
        assertTrue(DiagnosticFeatureContract.REQUIRED_ACTIONS.contains("diagnostic_toggle"));
        assertTrue(DiagnosticFeatureContract.REQUIRED_ACTIONS.contains("report_copy"));
    }

    @Test
    public void automaticDiagnosticReliabilityContractIsExplicit() {
        assertTrue(DiagnosticFeatureContract.AUTOMATIC_DIAGNOSTIC_REQUIREMENTS.contains("off_main_thread_sampling"));
        assertTrue(DiagnosticFeatureContract.AUTOMATIC_DIAGNOSTIC_REQUIREMENTS.contains("bounded_partial_wakelock"));
        assertTrue(DiagnosticFeatureContract.AUTOMATIC_DIAGNOSTIC_REQUIREMENTS.contains("automatic_incident_detection"));
        assertTrue(DiagnosticFeatureContract.AUTOMATIC_DIAGNOSTIC_REQUIREMENTS.contains("completion_notification"));
    }
}
