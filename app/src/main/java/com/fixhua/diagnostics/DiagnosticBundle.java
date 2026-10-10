package com.fixhua.diagnostics;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.PowerManager;

import java.io.File;
import java.util.Locale;

/** Builds the final copyable report after a diagnostic session completes. */
final class DiagnosticBundle {
    private static final String GBOX = "com.gbox.android";

    private DiagnosticBundle() {}

    static String build(Context context) {
        StringBuilder out = new StringBuilder(160_000);
        out.append("FIXHUA_FULL_DIAGNOSTIC_BUNDLE\n");
        out.append("schema=4\n");
        out.append("purpose=automatic_device_wide_lag_diagnostics\n");
        out.append("privacy=NO account/message/photo/contact/location/android_id/serial/ip/password content; foreground package names may be recorded when Usage Access is granted\n\n");

        appendExperimentIdentity(context, out);
        out.append("\n=== RAW_FACTS_STATIC ===\n");
        out.append(DiagnosticCollector.collect(context));
        appendCapabilityAndMissingEvidence(context, out);
        appendDerivedMetrics(context, out);
        appendArchitectureSignals(context, out);

        out.append("\n=== RAW_FACTS_TIMELINE ===\n");
        out.append(DiagnosticTimeline.report(context, 4000));

        out.append("\n=== INTERPRETATION_CONTRACT ===\n");
        out.append("FACT=direct API/file/package observation.\n");
        out.append("DERIVED=calculation from facts.\n");
        out.append("HYPOTHESIS=correlation-based explanation, never proof by itself.\n");
        out.append("UNKNOWN=important evidence the current privilege/API surface cannot obtain.\n");
        out.append("Rule: CPU frequency drops alone are never sufficient evidence of a freeze.\n");
        out.append("Rule: unavailable PSI is represented as unavailable/NA, not zero.\n");
        out.append("Rule: automatic incidents require multiple signals or a strong service-stall signal, then a stable recovery window before closing.\n");
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
        line(out, "usage_access_self_test", String.valueOf(UsageAccessProbe.hasAccess(context)));
        line(out, "gbox_installed", String.valueOf(isInstalled(context, GBOX)));
        line(out, "deep_diagnostic_active_now", String.valueOf(DeepDiagnosticService.active));
        line(out, "deep_diagnostic_samples", String.valueOf(DeepDiagnosticService.sampleCount(context)));
        line(out, "deep_diagnostic_started_at_epoch_ms", String.valueOf(DeepDiagnosticService.startedAt(context)));
        line(out, "automatic_incidents_completed", String.valueOf(DeepDiagnosticService.incidentCount(context)));
        line(out, "detector_last_state", DeepDiagnosticService.detectorState(context));
        line(out, "report_generated_during_session_finalization", "true");
    }

    private static void appendCapabilityAndMissingEvidence(Context context, StringBuilder out) {
        out.append("\n=== CAPABILITY_SELF_TESTS ===\n");
        boolean usage = UsageAccessProbe.hasAccess(context);
        boolean memoryPsi = new File("/proc/pressure/memory").canRead();
        boolean ioPsi = new File("/proc/pressure/io").canRead();
        boolean procStat = new File("/proc/stat").canRead();
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
        line(out, "TEST_memory_psi_readable", passFail(memoryPsi));
        line(out, "TEST_io_psi_readable", passFail(ioPsi));
        line(out, "TEST_proc_stat_readable", passFail(procStat));
        line(out, "TEST_cpu_freq_readable_cores", String.valueOf(cpuReadable));
        line(out, "TEST_thermal_api", passFail(thermalApi));
        line(out, "TEST_gbox_package_visible", passFail(gbox));
        line(out, "TEST_root_last_known_available", passFail(rootKnownAvailable));

        out.append("\n=== MISSING_EVIDENCE ===\n");
        if (!usage) {
            unknown(out, "foreground_history", "Usage Access self-test failed",
                    "grant Usage Access; FixHUA will continue system-level metrics without it");
        }
        if (!memoryPsi) {
            unknown(out, "memory_psi", "kernel PSI path is unreadable/unsupported",
                    "RAM available ratio + ActivityManager.lowMemory remain active fallbacks");
        }
        if (!ioPsi) {
            unknown(out, "io_psi", "kernel I/O PSI path is unreadable/unsupported",
                    "storage free space remains visible but kernel I/O pressure cannot be inferred directly");
        }
        if (!procStat) {
            unknown(out, "cpu_load", "/proc/stat is unreadable",
                    "CPU frequency will be recorded but frequency-only drops will not trigger high-confidence attribution");
        }
        if (cpuReadable == 0) {
            unknown(out, "per_core_cpu_frequency", "cpufreq sysfs paths are unreadable/absent",
                    "CPU-load and other system-pressure evidence remain usable");
        }
        if (!rootKnownAvailable) {
            unknown(out, "kernel_driver_binder_service_details",
                    "normal Android sandbox blocks full system/service/driver visibility",
                    "root/ADB/Shizuku can expand future diagnostics but are not baseline dependencies");
        }
        if (!gbox) {
            unknown(out, "gbox_runtime", "GBox package is not visible/installed",
                    "install or expose GBox before a GBox-specific reproduction");
        }
        unknown(out, "other_apps_private_logs", "Android sandbox blocks private files/logcat for other apps",
                "FixHUA intentionally does not bypass app sandboxes");
        unknown(out, "frame_jank_inside_other_apps", "normal apps cannot directly instrument another app's render pipeline",
                "automatic incidents therefore use system pressure/stall correlations, not fabricated frame data");
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
        line(out, "DERIVED_normal_sampling_interval_seconds", "1.0");
        line(out, "DERIVED_incident_sampling_interval_seconds", "0.5");
        line(out, "DERIVED_incident_recovery_stability_seconds", "12");
    }

    private static void appendArchitectureSignals(Context context, StringBuilder out) {
        out.append("\n=== ARCHITECTURE_SIGNALS ===\n");
        boolean usage = UsageAccessProbe.hasAccess(context);
        boolean root = context.getSharedPreferences(GuardService.PREFS, Context.MODE_PRIVATE)
                .getBoolean("root_available", false);
        boolean psi = new File("/proc/pressure/memory").canRead();
        int cores = readableCpuFreqCores();

        signal(out, "sampler_thread", "KEEP", "sampling is isolated from the Activity main looper");
        signal(out, "bounded_wakelock", "KEEP", "partial wakelock is held only for the explicit diagnostic session timeout");
        signal(out, "foreground_detection", usage ? "AVAILABLE" : "DEGRADED", usage
                ? "foreground package transitions can be correlated"
                : "system-level incident detection continues without foreground attribution");
        signal(out, "memory_pressure_backend", psi ? "AVAILABLE" : "FALLBACK_ACTIVE", psi
                ? "PSI evidence available"
                : "RAM ratio + lowMemory fallback active; no synthetic PSI zero");
        signal(out, "cpu_frequency_backend", cores > 0 ? "AVAILABLE" : "DEGRADED", cores > 0
                ? "readable cpufreq cores=" + cores
                : "do not attribute freezes to frequency without another readable backend");
        signal(out, "root_layer", root ? "AVAILABLE" : "OPTIONAL_UNAVAILABLE", root
                ? "root-only evidence may be added in a later explicit privileged mode"
                : "baseline diagnostics stay non-root and honest about visibility limits");
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
