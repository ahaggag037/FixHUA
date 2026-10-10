package com.fixhua.guard;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class DiagnosticLogic {
    enum BootloaderState { LOCKED, UNLOCKED, UNKNOWN }
    enum RootSignal { NONE_OBVIOUS, POSSIBLE, STRONG }

    static final class BootAssessment {
        final BootloaderState state;
        final List<String> evidence;
        BootAssessment(BootloaderState state, List<String> evidence) {
            this.state = state;
            this.evidence = evidence;
        }
    }

    static final class RootAssessment {
        final RootSignal signal;
        final List<String> evidence;
        RootAssessment(RootSignal signal, List<String> evidence) {
            this.signal = signal;
            this.evidence = evidence;
        }
    }

    static BootAssessment assessBootloader(Map<String, String> props) {
        List<String> evidence = new ArrayList<>();
        String flashLocked = norm(props.get("ro.boot.flash.locked"));
        String deviceState = firstNonEmpty(
                norm(props.get("ro.boot.vbmeta.device_state")),
                norm(props.get("ro.boot.device_state"))
        );
        String verified = norm(props.get("ro.boot.verifiedbootstate"));

        if ("0".equals(flashLocked)) evidence.add("ro.boot.flash.locked=0");
        if ("unlocked".equals(deviceState)) evidence.add("device_state=unlocked");
        if ("orange".equals(verified)) evidence.add("verifiedbootstate=orange");
        if (!evidence.isEmpty()) return new BootAssessment(BootloaderState.UNLOCKED, evidence);

        if ("1".equals(flashLocked)) evidence.add("ro.boot.flash.locked=1");
        if ("locked".equals(deviceState)) evidence.add("device_state=locked");
        if ("green".equals(verified)) evidence.add("verifiedbootstate=green");
        if (("1".equals(flashLocked) || "locked".equals(deviceState)) && "green".equals(verified)) {
            return new BootAssessment(BootloaderState.LOCKED, evidence);
        }

        if (evidence.isEmpty()) evidence.add("No decisive public boot property was readable");
        return new BootAssessment(BootloaderState.UNKNOWN, evidence);
    }

    static RootAssessment assessRoot(
            Map<String, String> props,
            boolean suOnPath,
            boolean rootManagerVisible,
            boolean suspiciousMount,
            boolean writableSystemSignal) {
        List<String> evidence = new ArrayList<>();
        int strong = 0;
        int weak = 0;

        if (suOnPath) { strong++; evidence.add("su executable visible on PATH"); }
        if (rootManagerVisible) { strong++; evidence.add("known root manager package visible"); }
        if (suspiciousMount) { strong++; evidence.add("mount table contains Magisk/KernelSU/APatch/overlay root signal"); }
        if (writableSystemSignal) { strong++; evidence.add("system/vendor mount appears writable"); }

        String tags = norm(props.get("ro.build.tags"));
        String type = norm(props.get("ro.build.type"));
        String debuggable = norm(props.get("ro.debuggable"));
        String secure = norm(props.get("ro.secure"));
        if (tags.contains("test-keys")) { weak++; evidence.add("ro.build.tags contains test-keys"); }
        if ("eng".equals(type) || "userdebug".equals(type)) { weak++; evidence.add("build type=" + type); }
        if ("1".equals(debuggable)) { weak++; evidence.add("ro.debuggable=1"); }
        if ("0".equals(secure)) { weak++; evidence.add("ro.secure=0"); }

        if (strong >= 1 || weak >= 3) return new RootAssessment(RootSignal.STRONG, evidence);
        if (weak >= 1) return new RootAssessment(RootSignal.POSSIBLE, evidence);
        evidence.add("No obvious root signal visible to an ordinary app");
        return new RootAssessment(RootSignal.NONE_OBVIOUS, evidence);
    }

    private static String norm(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String firstNonEmpty(String a, String b) {
        return a.isEmpty() ? b : a;
    }

    private DiagnosticLogic() {}
}
