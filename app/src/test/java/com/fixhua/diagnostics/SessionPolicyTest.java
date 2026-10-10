package com.fixhua.diagnostics;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SessionPolicyTest {
    @Test
    public void severeThermalDisablesLocks() {
        SessionPolicy.Decision d = SessionPolicy.decide(
                GuardService.MODE_STABILITY, true, true, true,
                true, true, false, SessionPolicy.THERMAL_SEVERE);
        assertFalse(d.cpuWakeLock);
        assertEquals(SessionPolicy.WifiMode.NONE, d.wifiMode);
    }

    @Test
    public void balancedForegroundUsesCpuAndHighPerfWifi() {
        SessionPolicy.Decision d = SessionPolicy.decide(
                GuardService.MODE_BALANCED, true, true, true,
                true, true, false, 0);
        assertTrue(d.cpuWakeLock);
        assertEquals(SessionPolicy.WifiMode.HIGH_PERF, d.wifiMode);
    }

    @Test
    public void stabilityCoolForegroundUsesLowLatencyWifi() {
        SessionPolicy.Decision d = SessionPolicy.decide(
                GuardService.MODE_STABILITY, true, true, true,
                true, true, false, 0);
        assertTrue(d.cpuWakeLock);
        assertEquals(SessionPolicy.WifiMode.LOW_LATENCY, d.wifiMode);
    }

    @Test
    public void moderateThermalDropsBalancedWifiBoost() {
        SessionPolicy.Decision d = SessionPolicy.decide(
                GuardService.MODE_BALANCED, true, true, true,
                true, true, false, SessionPolicy.THERMAL_MODERATE);
        assertTrue(d.cpuWakeLock);
        assertEquals(SessionPolicy.WifiMode.NONE, d.wifiMode);
    }

    @Test
    public void noUsageAccessKeepsExplicitSessionProtection() {
        SessionPolicy.Decision d = SessionPolicy.decide(
                GuardService.MODE_BALANCED, false, false, false,
                true, true, false, 0);
        assertTrue(d.cpuWakeLock);
        assertEquals(SessionPolicy.WifiMode.HIGH_PERF, d.wifiMode);
    }

    @Test
    public void ecoBackgroundIsIdle() {
        SessionPolicy.Decision d = SessionPolicy.decide(
                GuardService.MODE_ECO, true, false, true,
                true, true, false, 0);
        assertFalse(d.cpuWakeLock);
        assertEquals(SessionPolicy.WifiMode.NONE, d.wifiMode);
    }
}
