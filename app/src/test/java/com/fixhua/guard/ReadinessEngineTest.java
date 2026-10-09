package com.fixhua.guard;

import android.os.PowerManager;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ReadinessEngineTest {

    @Test
    public void healthySnapshotIsReady() {
        SystemSnapshot snapshot = new SystemSnapshot.Builder().build();
        ReadinessEngine.Result result = ReadinessEngine.evaluate(snapshot);
        assertEquals(ReadinessEngine.Status.READY, result.status);
        assertEquals(0, result.count(ReadinessEngine.Severity.WARNING));
        assertEquals(0, result.count(ReadinessEngine.Severity.BLOCKER));
    }

    @Test
    public void missingGboxBlocksLaunchReadiness() {
        SystemSnapshot snapshot = new SystemSnapshot.Builder()
                .gboxInstalled(false)
                .build();
        ReadinessEngine.Result result = ReadinessEngine.evaluate(snapshot);
        assertEquals(ReadinessEngine.Status.BLOCKED, result.status);
        assertEquals(1, result.count(ReadinessEngine.Severity.BLOCKER));
        assertNotNull(result.firstActionable());
        assertEquals("GBOX_MISSING", result.firstActionable().code);
    }

    @Test
    public void powerAndBatteryRestrictionsNeedAttention() {
        SystemSnapshot snapshot = new SystemSnapshot.Builder()
                .powerSaveMode(true)
                .gboxBatteryExempt(false)
                .build();
        ReadinessEngine.Result result = ReadinessEngine.evaluate(snapshot);
        assertEquals(ReadinessEngine.Status.ATTENTION, result.status);
        assertTrue(result.count(ReadinessEngine.Severity.WARNING) >= 2);
    }

    @Test
    public void alwaysFinishActivitiesNeedsAttention() {
        SystemSnapshot snapshot = new SystemSnapshot.Builder()
                .alwaysFinishActivities(1)
                .build();
        ReadinessEngine.Result result = ReadinessEngine.evaluate(snapshot);
        assertEquals(ReadinessEngine.Status.ATTENTION, result.status);
        assertEquals("ALWAYS_FINISH_ACTIVITIES", result.firstActionable().code);
    }

    @Test
    public void lowStorageNeedsAttentionAtHuaweiThreshold() {
        SystemSnapshot snapshot = new SystemSnapshot.Builder()
                .storage(100_000, 10_000)
                .build();
        ReadinessEngine.Result result = ReadinessEngine.evaluate(snapshot);
        assertEquals(ReadinessEngine.Status.ATTENTION, result.status);
        assertTrue(hasCode(result, "LOW_STORAGE"));
    }

    @Test
    public void thermalPressureNeedsAttention() {
        SystemSnapshot snapshot = new SystemSnapshot.Builder()
                .thermalStatus(PowerManager.THERMAL_STATUS_SEVERE)
                .build();
        ReadinessEngine.Result result = ReadinessEngine.evaluate(snapshot);
        assertEquals(ReadinessEngine.Status.ATTENTION, result.status);
        assertTrue(hasCode(result, "THERMAL_PRESSURE"));
    }

    @Test
    public void identityConflictIsInformationalOnly() {
        SystemSnapshot snapshot = new SystemSnapshot.Builder()
                .identityConflict(true)
                .build();
        ReadinessEngine.Result result = ReadinessEngine.evaluate(snapshot);
        assertEquals(ReadinessEngine.Status.READY, result.status);
        assertEquals(1, result.count(ReadinessEngine.Severity.INFO));
        assertEquals(0, result.count(ReadinessEngine.Severity.WARNING));
    }

    @Test
    public void missingMicrogDoesNotBlockGboxContainerMode() {
        SystemSnapshot snapshot = new SystemSnapshot.Builder()
                .microgInstalled(false)
                .build();
        ReadinessEngine.Result result = ReadinessEngine.evaluate(snapshot);
        assertEquals(ReadinessEngine.Status.READY, result.status);
        assertTrue(hasCode(result, "MICROG_MISSING"));
    }

    private static boolean hasCode(ReadinessEngine.Result result, String code) {
        for (ReadinessEngine.Finding finding : result.findings) {
            if (code.equals(finding.code)) return true;
        }
        return false;
    }
}
