package com.fixhua.diagnostics;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.PowerManager;

import java.io.File;
import java.util.Locale;

/**
 * Produces the user-copyable diagnostic bundle intended for engineering analysis.
 *
 * The report deliberately separates observed facts from derived signals and
 * hypotheses so later architecture decisions are not based on an accidental
 * inference. It does not collect account data, messages, photos, contacts,
 * precise location, Android ID, serial number, IP addresses, or passwords.
 */
final class DiagnosticBundle {
    private static final String GBOX = "com.gbox.android";

    private DiagnosticBundle() {}

    static String build(Context context) {
        StringBuilder out = new StringBuilder(96_000);
        out.append("FIXHUA_FULL_DIAGNOSTIC_BUNDLE\n");
        out.append("schema=3\n");
        out.append("purpose=machine_assisted_engineering_analysis\n");
        out.append("privacy=NO accounts/messages/photos/contacts/location/android_id/serial/ip/passwords\n\n");

        appendExperimentIdentity(context, out);
        out.append("\n=== RAW_FACTS_STATIC ===\n");
        out.append(DiagnosticCollector.collect(context));

        appendCapabilityAndMissingEvidence(context, out);
        appendDerivedMetrics(context, out);
        appendArchitectureSignals(context, out);

        out.append("\n=== RAW_FACTS_TIMELINE ===\n");
        out.append(DiagnosticTimeline.report(context, 1400));

        out.append("\n=== INTERPRETATION_CONTRACT ===\n");
        out.append("FACT=direct API/file/package observation.\n");
        out.append("DERIVED=calculation from facts.\n");
        out.append("HYPOTHESIS=correlation-based explanation, never proof by itself.\n");
        out.append("UNKNOWN=important evidence the current privilege/API surface cannot obtain.\n");
        out.append("Rule: architecture changes should cite supporting facts/timestamps and preserve competing hypotheses.\n");
        return out.toString();
    }

    private static void appendExperimentIdentity(Context context, StringBuilder out) {
        out.append("=== EXPERIMENT_IDENTITY ===\n");
        line(out, "fixhua_package", context.getPackageName());
        try {
            PackageInfo pi = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            line(out, "fixhua_version", String.valueOf(pi.versionName));
            line(out, "fixhua_version_code", String.valueOf(pi.getLongVersionCode()));
        } catch (Throwable t) {
            line(out, "fixhua_version", "UNKNOWN:" + t.getClass().getSimpleName());
        }
        line(out, "device_model", Build.MODEL);
        line(out, "android_release", Build.VERSION.RELEASE);
        line(out, "sdk", String.valueOf(Build.VERSION.SDK_INT));
        line(out, "guard_mode", GuardService.getMode(context));
        line(out, "guard_active_now", String.valueOf(GuardService.active));
        line(out, "guard_last_state", GuardService.lastState(context));
        line(out, "root_last_status", RootSessionController.lastStatus(context));
        line(out, "usage_access", String.valueOf(GuardService.hasUsageAccess(context)));
        line(out, "gbox_installed", String.valueOf(isInstalled(context, GBOX)));
        line(out, "session_count", String.valueOf(GuardService.stat(context, "sessions")));
        line(out, "last_session_ms", String.valueOf(GuardService.stat(context, "last_session_ms")));
        line(out, "last_peak_thermal", String.valueOf(GuardService.stat(context, "last_peak_thermal")));
        line(out, "deep_diagnostic_active_now", String.valueOf(DeepDiagnosticService.active));
        line(out, "deep_diagnostic_samples", String.valueOf(DeepDiagnosticService.sampleCount(context)));
        line(out, "deep_diagnostic_started_at_epoch_ms", String.valueOf(DeepDiagnosticService.startedAt(context)));
    }

    private static void appendCapabilityAndMissingEvidence(Context context, StringBuilder out) {
        out.append("\n=== CAPABILITY_SELF_TESTS ===\n");
        boolean usage = GuardService.hasUsageAccess(context);
        boolean psi = new File("/proc/pressure/memory").canRead();
        int cpuReadable = readableCpuFreqCores();
        boolean gbox = isInstalled(context, GBOX);
        boolean rootKnownAvailable = context.getSharedPreferences(GuardService.PREFS, Context.MODE_PRIVATE)
                .getBoolean("root_available", false);

        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        boolean thermalApi = false;
        try {
            pm.getCurrentThermalStatus();
            thermalApi = true;
        } catch (Throwable ignored) {
        }

        line(out, "TEST_usage_access", passFail(usage));
        line(out, "TEST_memory_psi_readable", passFail(psi));
        line(out, "TEST_cpu_freq_readable_cores", String.valueOf(cpuReadable));
        line(out, "TEST_thermal_api", passFail(thermalApi));
        line(out, "TEST_gbox_package_visible", passFail(gbox));
        line(out, "TEST_root_last_known_available", passFail(rootKnownAvailable));

        out.append("\n=== MISSING_EVIDENCE ===\n");
        if (!usage) {
            unknown(out, "foreground_history", "Usage Access is not granted", "grant Usage Access; no root required");
        }
        if (!psi) {
            unknown(out, "memory_psi", "kernel path /proc/pressure/memory is unreadable or unsupported", "device/kernel support or privileged collector may be required");
        }
        if (cpuReadable == 0) {
            unknown(out, "per_core_cpu_frequency", "cpufreq sysfs paths are unreadable/absent", "root or a device-specific metrics backend may be required");
        }
        if (!rootKnownAvailable) {
            unknown(out, "root_only_kernel_and_process_evidence", "no previously confirmed su/root capability", "root-only diagnostics intentionally unavailable");
        }
        if (!gbox) {
            unknown(out, "gbox_runtime", "GBox package is not visible/installed", "install or expose GBox package before reproduction");
        }
        unknown(out, "gbox_private_logs", "Android app sandbox blocks another app's private files/logs", "requires explicit privileged/root diagnostic path; baseline app will not bypass sandbox");
        unknown(out, "gbox_guest_process_internal_state", "virtualized guest internals are not exposed by normal Android APIs", "requires GBox-supported diagnostics or carefully scoped privileged observation");
        unknown(out, "gbox_historical_exit_reason", "ApplicationExitInfo is reliably available only for FixHUA's own process in this app context", "privileged/system evidence would be needed for stronger attribution");
        unknown(out, "frame_jank_inside_gbox_guest", "FixHUA cannot directly instrument another app's private rendering pipeline", "future external perfetto/root instrumentation may be evaluated if evidence warrants it");
    }

    private static void appendDerivedMetrics(Context context, StringBuilder out) {
        out.append("\n=== DERIVED_METRICS ===\n");
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        double availableRatio = mi.totalMem > 0 ? (double) mi.availMem / (double) mi.totalMem : -1.0;
        line(out, "DERIVED_ram_available_ratio", availableRatio < 0 ? "UNKNOWN" : String.format(Locale.US, "%.4f", availableRatio));
        line(out, "DERIVED_guard_total_minutes", String.valueOf(GuardService.stat(context, "total_guard_ms") / 60_000L));
        line(out, "DERIVED_gbox_focus_entries", String.valueOf(GuardService.stat(context, "gbox_focus_entries")));
        line(out, "DERIVED_guard_sampling_interval_seconds", "~5");
        line(out, "DERIVED_deep_diagnostic_sampling_interval_seconds", "1");
    }

    private static void appendArchitectureSignals(Context context, StringBuilder out) {
        out.append("\n=== ARCHITECTURE_SIGNALS ===\n");
        boolean usage = GuardService.hasUsageAccess(context);
        boolean root = context.getSharedPreferences(GuardService.PREFS, Context.MODE_PRIVATE)
                .getBoolean("root_available", false);
        boolean psi = new File("/proc/pressure/memory").canRead();
        int cores = readableCpuFreqCores();

        signal(out, "foreground_detection", usage ? "KEEP" : "BLOCKED", usage
                ? "Usage-based adaptive protection can be evaluated"
                : "Current architecture cannot attribute foreground transitions reliably until Usage Access is granted");
        signal(out, "root_layer", root ? "AVAILABLE" : "OPTIONAL_UNAVAILABLE", root
                ? "Root-specific reversible probes may be evaluated"
                : "Do not make root-only behavior a baseline dependency");
        signal(out, "memory_pressure_backend", psi ? "KEEP" : "NEEDS_FALLBACK", psi
                ? "PSI evidence available"
                : "Add alternative pressure metrics only if reports confirm PSI is unavailable on target firmware");
        signal(out, "cpu_frequency_backend", cores > 0 ? "KEEP" : "NEEDS_FALLBACK", cores > 0
                ? "Readable cpufreq cores=" + cores
                : "Do not infer CPU throttling from frequency until a readable backend exists");
        signal(out, "gbox_private_observability", "BOUNDARY", "Normal app sandbox is an architectural visibility limit; escalate privileges only when a concrete unresolved hypothesis requires it");
    }

    private static boolean isInstalled(Context context, String pkg) {
        try {
            context.getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private static int readableCpuFreqCores() {
        File root = new File("/sys/devices/system/cpu");
        File[] cpus = root.listFiles((dir, name) -> name.matches("cpu[0-9]+"));
        if (cpus == null) return 0;
        int count = 0;
        for (File cpu : cpus) {
            File scaling = new File(cpu, "cpufreq/scaling_cur_freq");
            File info = new File(cpu, "cpufreq/cpuinfo_cur_freq");
            if (scaling.canRead() || info.canRead()) count++;
        }
        return count;
    }

    private static String passFail(boolean value) {
        return value ? "PASS" : "NOT_AVAILABLE";
    }

    private static void unknown(StringBuilder out, String evidence, String why, String path) {
        out.append("UNKNOWN evidence=").append(safe(evidence))
                .append(" reason=").append(safe(why))
                .append(" next_path=").append(safe(path)).append('\n');
    }

    private static void signal(StringBuilder out, String area, String status, String detail) {
        out.append("SIGNAL area=").append(safe(area))
                .append(" status=").append(safe(status))
                .append(" detail=").append(safe(detail)).append('\n');
    }

    private static void line(StringBuilder out, String key, String value) {
        out.append(key).append('=').append(value == null ? "null" : value).append('\n');
    }

    private static String safe(String value) {
        if (value == null) return "null";
        return value.replace('\n', ' ').replace('\r', ' ').trim();
    }
}
