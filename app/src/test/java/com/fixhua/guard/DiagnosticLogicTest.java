package com.fixhua.guard;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DiagnosticLogicTest {
    @Test public void unlockedWhenFlashLockedZero() {
        Map<String,String> p = new HashMap<>();
        p.put("ro.boot.flash.locked", "0");
        assertEquals(DiagnosticLogic.BootloaderState.UNLOCKED, DiagnosticLogic.assessBootloader(p).state);
    }

    @Test public void lockedRequiresLockedSignalAndGreenVerifiedBoot() {
        Map<String,String> p = new HashMap<>();
        p.put("ro.boot.flash.locked", "1");
        p.put("ro.boot.verifiedbootstate", "green");
        assertEquals(DiagnosticLogic.BootloaderState.LOCKED, DiagnosticLogic.assessBootloader(p).state);
    }

    @Test public void unknownDoesNotPretendLocked() {
        Map<String,String> p = new HashMap<>();
        p.put("ro.boot.verifiedbootstate", "green");
        assertEquals(DiagnosticLogic.BootloaderState.UNKNOWN, DiagnosticLogic.assessBootloader(p).state);
    }

    @Test public void suIsStrongRootSignal() {
        DiagnosticLogic.RootAssessment r = DiagnosticLogic.assessRoot(new HashMap<>(), true, false, false, false);
        assertEquals(DiagnosticLogic.RootSignal.STRONG, r.signal);
        assertTrue(r.evidence.get(0).contains("su"));
    }

    @Test public void testKeysAloneAreOnlyPossible() {
        Map<String,String> p = new HashMap<>();
        p.put("ro.build.tags", "release-keys,test-keys");
        DiagnosticLogic.RootAssessment r = DiagnosticLogic.assessRoot(p, false, false, false, false);
        assertEquals(DiagnosticLogic.RootSignal.POSSIBLE, r.signal);
    }

    @Test public void mountOverlayAloneIsNotStrongRootEvidence() {
        Map<String,String> p = new HashMap<>();
        p.put("ro.build.tags", "release-keys");
        p.put("ro.build.type", "user");
        p.put("ro.debuggable", "0");
        p.put("ro.secure", "1");
        DiagnosticLogic.RootAssessment r = DiagnosticLogic.assessRoot(p, false, false, true, false);
        assertEquals(DiagnosticLogic.RootSignal.POSSIBLE, r.signal);
        assertTrue(r.evidence.get(0).contains("mount"));
    }

    @Test public void cleanSignalsRemainNoneObvious() {
        Map<String,String> p = new HashMap<>();
        p.put("ro.build.tags", "release-keys");
        p.put("ro.build.type", "user");
        p.put("ro.debuggable", "0");
        p.put("ro.secure", "1");
        assertEquals(DiagnosticLogic.RootSignal.NONE_OBVIOUS,
                DiagnosticLogic.assessRoot(p, false, false, false, false).signal);
    }
}
