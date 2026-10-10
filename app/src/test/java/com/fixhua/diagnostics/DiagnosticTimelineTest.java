package com.fixhua.diagnostics;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class DiagnosticTimelineTest {
    @Test
    public void analyzerDoesNotOverclaimCpuDropWithoutThermal() {
        String r = DiagnosticAnalyzer.classify(0, 4000, 8000, 0.0, 0.0, 50.0, true, true);
        assertTrue(r.contains("cause not proven"));
    }

    @Test
    public void combinedPressureCanProduceMultipleEvidenceItems() {
        String r = DiagnosticAnalyzer.classify(3, 300, 8000, 15.0, 2.0, 50.0, false, false);
        assertTrue(r.contains("thermal pressure"));
        assertTrue(r.contains("memory pressure"));
        assertTrue(r.contains("CPU frequency fell sharply"));
        assertTrue(r.contains("network is not validated"));
        assertTrue(r.contains("battery-restricted"));
    }
}
