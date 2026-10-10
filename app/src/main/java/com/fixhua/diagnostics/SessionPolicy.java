package com.fixhua.diagnostics;

/** Pure policy logic for the temporary active guard. */
final class SessionPolicy {
    enum WifiMode { NONE, HIGH_PERF, LOW_LATENCY }

    static final class Decision {
        final boolean cpuWakeLock;
        final WifiMode wifiMode;
        final String reason;

        Decision(boolean cpuWakeLock, WifiMode wifiMode, String reason) {
            this.cpuWakeLock = cpuWakeLock;
            this.wifiMode = wifiMode;
            this.reason = reason;
        }
    }

    static final int THERMAL_MODERATE = 2;
    static final int THERMAL_SEVERE = 3;

    private SessionPolicy() {}

    static Decision decide(
            String mode,
            boolean usageAccess,
            boolean gboxForeground,
            boolean recentGbox,
            boolean interactive,
            boolean validatedWifi,
            boolean powerSave,
            int thermalStatus
    ) {
        if (thermalStatus >= THERMAL_SEVERE) {
            return new Decision(false, WifiMode.NONE, "thermal safeguard");
        }

        boolean activeWindow = usageAccess ? (gboxForeground || recentGbox) : true;
        if (!activeWindow) {
            return new Decision(false, WifiMode.NONE, "waiting for GBox");
        }

        if (GuardService.MODE_ECO.equals(mode)) {
            boolean cpu = gboxForeground && interactive;
            return new Decision(cpu, WifiMode.NONE, cpu ? "eco foreground protection" : "eco idle");
        }

        if (GuardService.MODE_STABILITY.equals(mode)) {
            boolean cpu = true;
            WifiMode wifi = WifiMode.NONE;
            if (validatedWifi && interactive && !powerSave) {
                wifi = thermalStatus >= THERMAL_MODERATE
                        ? WifiMode.HIGH_PERF
                        : WifiMode.LOW_LATENCY;
            }
            return new Decision(cpu, wifi, gboxForeground ? "stability active" : "stability grace window");
        }

        boolean cpu = true;
        WifiMode wifi = WifiMode.NONE;
        if (validatedWifi && interactive && !powerSave && thermalStatus < THERMAL_MODERATE) {
            wifi = WifiMode.HIGH_PERF;
        }
        return new Decision(cpu, wifi, gboxForeground ? "balanced active" : "balanced grace window");
    }
}
