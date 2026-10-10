package com.fixhua.guard;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class ReportBuilderTest {
    @Test
    public void reportMakesPointInTimeLimitExplicit() {
        SystemSnapshot snapshot = new SystemSnapshot.Builder().build();
        String report = ReportBuilder.build(snapshot, ReadinessEngine.evaluate(snapshot));
        assertTrue(report.contains("point_in_time_snapshot=true"));
        assertTrue(report.contains("causal_diagnosis=false"));
        assertTrue(report.contains("gbox_launchable=true"));
        assertTrue(report.contains("network_observed=true"));
    }

    @Test
    public void incidentReportHasDistinctCaptureType() {
        SystemSnapshot snapshot = new SystemSnapshot.Builder().build();
        String report = ReportBuilder.buildIncident(snapshot, ReadinessEngine.evaluate(snapshot));
        assertTrue(report.contains("capture_type=manual_incident_snapshot"));
        assertTrue(report.contains("This report is a point-in-time compatibility snapshot"));
    }
}
