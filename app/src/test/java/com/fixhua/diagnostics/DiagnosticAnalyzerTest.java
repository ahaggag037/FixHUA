package com.fixhua.diagnostics;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class DiagnosticAnalyzerTest {
    @Test
    public void flagsThermalPressure() {
        String r = DiagnosticAnalyzer.classify(3, 3000, 8000, 0.0, 0.0, 0.0, true, true);
        assertTrue(r.contains("thermal pressure"));
    }

    @Test
    public void flagsMemoryPressure() {
        String r = DiagnosticAnalyzer.classify(0, 400, 8000, 12.0, 1.2, 0.0, true, true);
        assertTrue(r.contains("memory pressure"));
    }

    @Test
    public void correlatesCpuDropWithThermal() {
        String r = DiagnosticAnalyzer.classify(2, 3000, 8000, 0.0, 0.0, 40.0, true, true);
        assertTrue(r.contains("CPU frequency fell sharply"));
    }

    @Test
    public void flagsConnectivityAndBatteryRestriction() {
        String r = DiagnosticAnalyzer.classify(0, 3000, 8000, 0.0, 0.0, 0.0, false, false);
        assertTrue(r.contains("network is not validated"));
        assertTrue(r.contains("battery-restricted"));
    }

    @Test
    public void quietSampleDoesNotInventCause() {
        String r = DiagnosticAnalyzer.classify(0, 5000, 8000, 0.0, 0.0, 5.0, true, true);
        assertTrue(r.startsWith("NONE:"));
    }
}
