package com.fixhua.guard;

import android.os.PowerManager;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ReadinessEngineTest {

    @Test
    public void healthySnapshotIsReady() {
        ReadinessEngine.Result result = ReadinessEngine.evaluate(new SystemSnapshot.Builder().build());
        assertEquals(ReadinessEngine.Status.READY, result.status);
        assertEquals(0, result.count(ReadinessEngine.Severity.WARNING));
        assertEquals(0, result.count(ReadinessEngine.Severity.BLOCKER));
    }

    @Test
    public void missingGboxBlocksAndPointsToAppGallery() {
        ReadinessEngine.Result result = ReadinessEngine.evaluate(new SystemSnapshot.Builder()
                .gboxInstalled(false)
                .build());
        assertEquals(ReadinessEngine.Status.BLOCKED, result.status);
        assertEquals("GBOX_MISSING", result.firstActionable().code);
        assertEquals(ReadinessEngine.Action.OPEN_APP_GALLERY, result.firstActionable().action);
    }

    @Test
    public void disabledGboxBlocksLaunchReadiness() {
        ReadinessEngine.Result result = ReadinessEngine.evaluate(new SystemSnapshot.Builder()
                .gboxEnabled(false)
                .build());
        assertEquals(ReadinessEngine.Status.BLOCKED, result.status);
        assertTrue(hasCode(result, "GBOX_DISABLED"));
    }

    @Test
    public void unlaunchableGboxBlocksLaunchReadiness() {
        ReadinessEngine.Result result = ReadinessEngine.evaluate(new SystemSnapshot.Builder()
                .gboxLaunchable(false)
                .build());
        assertEquals(ReadinessEngine.Status.BLOCKED, result.status);
        assertTrue(hasCode(result, "GBOX_NOT_LAUNCHABLE"));
    }

    @Test
    public void suspendedGboxNeedsAttention() {
        ReadinessEngine.Result result = ReadinessEngine.evaluate(new SystemSnapshot.Builder()
                .gboxSuspended(true)
                .build());
        assertEquals(ReadinessEngine.Status.ATTENTION, result.status);
        assertTrue(hasCode(result, "GBOX_SUSPENDED"));
    }

    @Test
    public void powerAndBatteryRestrictionsNeedAttention() {
        ReadinessEngine.Result result = ReadinessEngine.evaluate(new SystemSnapshot.Builder()
                .powerSaveMode(true)
                .gboxBatteryExempt(false)
                .build());
        assertEquals(ReadinessEngine.Status.ATTENTION, result.status);
        assertTrue(result.count(ReadinessEngine.Severity.WARNING) >= 2);
    }

    @Test
    public void huaweiBatteryRestrictionRoutesToHuaweiPolicy() {
        ReadinessEngine.Result result = ReadinessEngine.evaluate(new SystemSnapshot.Builder()
                .gboxBatteryExempt(false)
                .huaweiEnvironment(true)
                .build());
        ReadinessEngine.Finding finding = findingByCode(result, "GBOX_BATTERY_OPTIMIZED");
        assertNotNull(finding);
        assertEquals(ReadinessEngine.Action.OPEN_HUAWEI_APP_LAUNCH, finding.action);
    }

    @Test
    public void alwaysFinishActivitiesNeedsAttention() {
        ReadinessEngine.Result result = ReadinessEngine.evaluate(new SystemSnapshot.Builder()
                .alwaysFinishActivities(1)
                .build());
        assertEquals(ReadinessEngine.Status.ATTENTION, result.status);
        assertEquals("ALWAYS_FINISH_ACTIVITIES", result.firstActionable().code);
    }

    @Test
    public void criticalLowStorageNeedsAttention() {
        ReadinessEngine.Result result = ReadinessEngine.evaluate(new SystemSnapshot.Builder()
                .storage(100_000, 10_000)
                .build());
        assertEquals(ReadinessEngine.Status.ATTENTION, result.status);
        assertTrue(hasCode(result, "LOW_STORAGE"));
    }

    @Test
    public void constrainedStorageIsInformationalOnly() {
        ReadinessEngine.Result result = ReadinessEngine.evaluate(new SystemSnapshot.Builder()
                .storage(100_000, 15_000)
                .build());
        assertEquals(ReadinessEngine.Status.READY, result.status);
        assertTrue(hasCode(result, "STORAGE_TIGHT"));
    }

    @Test
    public void thermalPressureNeedsAttention() {
        ReadinessEngine.Result result = ReadinessEngine.evaluate(new SystemSnapshot.Builder()
                .thermalStatus(PowerManager.THERMAL_STATUS_SEVERE)
                .build());
        assertEquals(ReadinessEngine.Status.ATTENTION, result.status);
        assertTrue(hasCode(result, "THERMAL_PRESSURE"));
    }

    @Test
    public void moderateThermalStateIsInformational() {
        ReadinessEngine.Result result = ReadinessEngine.evaluate(new SystemSnapshot.Builder()
                .thermalStatus(PowerManager.THERMAL_STATUS_MODERATE)
                .build());
        assertEquals(ReadinessEngine.Status.READY, result.status);
        assertTrue(hasCode(result, "THERMAL_WARM"));
    }

    @Test
    public void identityConflictIsInformationalOnly() {
        ReadinessEngine.Result result = ReadinessEngine.evaluate(new SystemSnapshot.Builder()
                .identityConflict(true)
                .build());
        assertEquals(ReadinessEngine.Status.READY, result.status);
        assertTrue(hasCode(result, "DEVICE_IDENTITY_MIXED"));
    }

    @Test
    public void missingMicrogDoesNotBlockGboxContainerMode() {
        ReadinessEngine.Result result = ReadinessEngine.evaluate(new SystemSnapshot.Builder()
                .microgInstalled(false)
                .build());
        assertEquals(ReadinessEngine.Status.READY, result.status);
        assertTrue(hasCode(result, "MICROG_MISSING"));
    }

    @Test
    public void androidLowMemorySignalNeedsAttentionButHasNoFakeFix() {
        ReadinessEngine.Result result = ReadinessEngine.evaluate(new SystemSnapshot.Builder()
                .lowMemory(true)
                .build());
        assertEquals(ReadinessEngine.Status.ATTENTION, result.status);
        assertTrue(hasCode(result, "MEMORY_PRESSURE"));
        assertNull(result.firstActionable());
    }

    @Test
    public void lowFreeRamAloneDoesNotBecomeWarning() {
        ReadinessEngine.Result result = ReadinessEngine.evaluate(new SystemSnapshot.Builder()
                .ram(8_000, 200)
                .lowMemory(false)
                .build());
        assertEquals(ReadinessEngine.Status.READY, result.status);
        assertTrue(hasCode(result, "RAM_BUSY"));
        assertEquals(0, result.count(ReadinessEngine.Severity.WARNING));
    }

    @Test
    public void unknownNetworkDoesNotCreateFalseWarning() {
        ReadinessEngine.Result result = ReadinessEngine.evaluate(new SystemSnapshot.Builder()
                .networkObserved(false)
                .build());
        assertEquals(ReadinessEngine.Status.READY, result.status);
        assertTrue(hasCode(result, "NETWORK_UNKNOWN"));
    }

    @Test
    public void offlineNetworkNeedsAttention() {
        ReadinessEngine.Result result = ReadinessEngine.evaluate(new SystemSnapshot.Builder()
                .networkPresent(false)
                .networkValidated(false)
                .build());
        assertEquals(ReadinessEngine.Status.ATTENTION, result.status);
        assertTrue(hasCode(result, "NETWORK_OFFLINE"));
    }

    @Test
    public void unvalidatedNetworkNeedsAttention() {
        ReadinessEngine.Result result = ReadinessEngine.evaluate(new SystemSnapshot.Builder()
                .networkValidated(false)
                .build());
        assertEquals(ReadinessEngine.Status.ATTENTION, result.status);
        assertTrue(hasCode(result, "NETWORK_UNVALIDATED"));
    }

    @Test
    public void captivePortalIsRecordedSeparately() {
        ReadinessEngine.Result result = ReadinessEngine.evaluate(new SystemSnapshot.Builder()
                .networkValidated(false)
                .networkCaptivePortal(true)
                .build());
        assertTrue(hasCode(result, "CAPTIVE_PORTAL"));
        assertTrue(hasCode(result, "NETWORK_UNVALIDATED"));
    }

    @Test
    public void hotBatteryWithoutSevereThermalSignalIsInfoOnly() {
        ReadinessEngine.Result result = ReadinessEngine.evaluate(new SystemSnapshot.Builder()
                .batteryTempC(46f)
                .thermalStatus(PowerManager.THERMAL_STATUS_LIGHT)
                .build());
        assertEquals(ReadinessEngine.Status.READY, result.status);
        assertTrue(hasCode(result, "BATTERY_HOT"));
        assertFalse(hasCode(result, "THERMAL_PRESSURE"));
    }

    private static boolean hasCode(ReadinessEngine.Result result, String code) {
        return findingByCode(result, code) != null;
    }

    private static ReadinessEngine.Finding findingByCode(ReadinessEngine.Result result, String code) {
        for (ReadinessEngine.Finding finding : result.findings) {
            if (code.equals(finding.code)) return finding;
        }
        return null;
    }
}
