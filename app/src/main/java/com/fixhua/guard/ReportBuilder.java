package com.fixhua.guard;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class ReportBuilder {
    static String build(SystemSnapshot s, ReadinessEngine.Result result) {
        StringBuilder out = new StringBuilder(6144);
        out.append("FixHUA System Health & Optimization Report v2.2.0\n");
        out.append("Generated: ")
                .append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(new Date()))
                .append('\n');
        out.append("Privacy: no accounts, contacts, messages, photos, location, Android ID, serial number, or IP address are collected.\n\n");

        out.append("=== HEALTH / READINESS ===\n");
        out.append("status=").append(result.status.name()).append('\n');
        out.append("blockers=").append(result.count(ReadinessEngine.Severity.BLOCKER)).append('\n');
        out.append("warnings=").append(result.count(ReadinessEngine.Severity.WARNING)).append('\n');
        out.append("info=").append(result.count(ReadinessEngine.Severity.INFO)).append("\n\n");

        out.append("=== DEVICE / ENVIRONMENT ===\n");
        out.append("environment_profile=").append(s.environmentProfile().name()).append('\n');
        out.append("manufacturer=").append(s.manufacturer).append('\n');
        out.append("brand=").append(s.brand).append('\n');
        out.append("model=").append(s.model).append('\n');
        out.append("android_release=").append(s.androidRelease).append('\n');
        out.append("sdk=").append(s.sdk).append('\n');
        out.append("build_display=").append(s.buildDisplay).append('\n');
        out.append("huawei_environment=").append(s.huaweiEnvironment).append('\n');
        out.append("identity_conflict_signal=").append(s.identityConflict).append('\n');
        out.append("cpu_logical_cores=").append(s.cpuCoreCount).append('\n');
        out.append("uptime_minutes=").append(s.uptimeMinutes).append("\n\n");

        out.append("=== GOOGLE / HUAWEI PLATFORM ===\n");
        out.append("native_google_stack_detected=").append(s.nativeGoogleStackDetected()).append('\n');
        out.append("google_play_services_package_present=").append(s.microgInstalled).append('\n');
        out.append("google_play_services_version=").append(s.microgVersion).append('\n');
        out.append("play_store_installed=").append(s.playStoreInstalled).append('\n');
        out.append("play_store_version=").append(s.playStoreVersion).append('\n');
        out.append("gbox_installed=").append(s.gboxInstalled).append('\n');
        out.append("gbox_version=").append(s.gboxVersion).append('\n');
        out.append("gbox_installer=").append(s.gboxInstaller).append('\n');
        out.append("hms_core_installed=").append(s.hmsInstalled).append('\n');
        out.append("appgallery_installed=").append(s.appGalleryInstalled).append('\n');
        out.append("huawei_system_manager_installed=").append(s.huaweiSystemManagerInstalled).append('\n');
        out.append("note_google_package_identity=Presence of com.google.android.gms is reported as Google Play services package presence; package id alone is not used to claim a specific implementation vendor.\n");
        out.append("note_gbox=GBox is optional and is evaluated only when installed. Its absence is not a failure on native-GMS devices.\n\n");

        out.append("=== POWER / BATTERY / THERMAL ===\n");
        if (s.gboxInstalled) {
            out.append("gbox_battery_exempt=").append(s.gboxBatteryExempt).append('\n');
        } else {
            out.append("gbox_battery_exempt=not-applicable\n");
        }
        out.append("google_play_services_battery_exempt=").append(s.microgBatteryExempt).append('\n');
        out.append("power_save_mode=").append(s.powerSaveMode).append('\n');
        out.append("device_idle_mode=").append(s.deviceIdleMode).append('\n');
        out.append("thermal_status_supported=").append(s.thermalStatusSupported).append('\n');
        out.append("thermal_status=")
                .append(s.thermalStatusSupported ? Integer.toString(s.thermalStatus) : "unsupported-on-this-api")
                .append('\n');
        out.append("battery_percent=").append(s.batteryPercent).append('\n');
        out.append("battery_temp_c=").append(String.format(Locale.US, "%.1f", s.batteryTempC)).append('\n');
        out.append("battery_charging=").append(s.batteryCharging()).append('\n');
        out.append("battery_status_code=").append(s.batteryStatus).append('\n');
        out.append("battery_plugged_code=").append(s.batteryPlugged).append('\n');
        out.append("battery_voltage_mv=").append(s.batteryVoltageMv).append("\n\n");

        out.append("=== MEMORY / STORAGE ===\n");
        out.append("ram_total_mb=").append(s.totalRamMb).append('\n');
        out.append("ram_available_mb=").append(s.availableRamMb).append('\n');
        out.append("ram_available_percent=")
                .append(String.format(Locale.US, "%.1f", s.availableRamFraction() * 100.0)).append('\n');
        out.append("low_memory=").append(s.lowMemory).append('\n');
        out.append("low_ram_device=").append(s.lowRamDevice).append('\n');
        out.append("app_memory_class_mb=").append(s.memoryClassMb).append('\n');
        out.append("app_large_memory_class_mb=").append(s.largeMemoryClassMb).append('\n');
        out.append("storage_total_mb=").append(s.totalStorageMb).append('\n');
        out.append("storage_free_mb=").append(s.freeStorageMb).append('\n');
        out.append("storage_free_percent=")
                .append(String.format(Locale.US, "%.1f", s.freeStorageFraction() * 100.0))
                .append("\n\n");

        out.append("=== NETWORK ===\n");
        out.append("network_present=").append(s.networkPresent).append('\n');
        out.append("internet_capability=").append(s.networkInternetCapable).append('\n');
        out.append("validated=").append(s.networkValidated).append('\n');
        out.append("wifi=").append(s.networkWifi).append('\n');
        out.append("vpn=").append(s.networkVpn).append('\n');
        out.append("metered=").append(s.networkMetered).append("\n\n");

        out.append("=== WEBVIEW / SYSTEM SETTINGS ===\n");
        out.append("webview_package=").append(s.webViewPackage).append('\n');
        out.append("webview_version=").append(s.webViewVersion).append('\n');
        out.append("always_finish_activities=").append(s.alwaysFinishActivities).append('\n');
        out.append("auto_time=").append(s.autoTime).append('\n');
        out.append("auto_time_zone=").append(s.autoTimeZone).append('\n');
        out.append("window_animation_scale=").append(formatFloat(s.windowAnimationScale)).append('\n');
        out.append("transition_animation_scale=").append(formatFloat(s.transitionAnimationScale)).append('\n');
        out.append("animator_duration_scale=").append(formatFloat(s.animatorDurationScale)).append("\n\n");

        out.append("=== ADVANCED DIAGNOSTICS ===\n");
        out.append("dump_permission_granted=").append(s.dumpPermissionGranted).append('\n');
        out.append("process_budget_probe_success=").append(s.privilegedProcessProbeSuccess).append('\n');
        out.append("process_budget_probe_failure=").append(s.privilegedProcessProbeFailure).append('\n');
        out.append("current_max_cached_processes=").append(s.currentMaxCachedProcesses).append('\n');
        out.append("reference_max_cached_processes=").append(s.referenceMaxCachedProcesses).append('\n');
        out.append("override_max_cached_processes=").append(s.overrideMaxCachedProcesses).append('\n');
        out.append("note_privilege=Official Google support does not bypass Android app sandboxing. Deeper dumpsys/system-service data still requires an explicit ADB/HDB/Shizuku/root-level bridge.\n\n");

        out.append("=== FINDINGS ===\n");
        if (result.findings.isEmpty()) {
            out.append("none\n");
        } else {
            for (ReadinessEngine.Finding finding : result.findings) {
                out.append('[').append(finding.severity.name()).append("] ")
                        .append(finding.code).append(": ")
                        .append(finding.message).append('\n');
            }
        }

        return out.toString();
    }

    private static String formatFloat(float value) {
        return value < 0f ? "unavailable" : String.format(Locale.US, "%.2f", value);
    }

    private ReportBuilder() {}
}
