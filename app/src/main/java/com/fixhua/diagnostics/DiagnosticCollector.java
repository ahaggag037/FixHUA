package com.fixhua.diagnostics;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.PowerManager;
import android.os.StatFs;
import android.os.SystemClock;
import android.webkit.WebView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

final class DiagnosticCollector {
    private DiagnosticCollector() {}

    static String collect(Context context) {
        StringBuilder out = new StringBuilder(8192);
        out.append("FixHUA Diagnostic Report v0.1\n");
        out.append("Generated: ").append(now()).append("\n");
        out.append("Privacy: no accounts, contacts, messages, photos, location, Android ID, serial number, or IP address are collected.\n\n");

        section(out, "DEVICE / OS");
        line(out, "manufacturer", Build.MANUFACTURER);
        line(out, "brand", Build.BRAND);
        line(out, "model", Build.MODEL);
        line(out, "device", Build.DEVICE);
        line(out, "product", Build.PRODUCT);
        line(out, "hardware", Build.HARDWARE);
        line(out, "android_release", Build.VERSION.RELEASE);
        line(out, "sdk", String.valueOf(Build.VERSION.SDK_INT));
        line(out, "security_patch", Build.VERSION.SECURITY_PATCH);
        line(out, "build_display", Build.DISPLAY);
        line(out, "build_fingerprint", Build.FINGERPRINT);
        line(out, "supported_abis", String.join(",", Build.SUPPORTED_ABIS));
        line(out, "uptime_minutes", String.valueOf(SystemClock.elapsedRealtime() / 60000L));
        line(out, "emui_property", getProp("ro.build.version.emui"));
        line(out, "harmony_magic_property", getProp("ro.build.version.magic"));
        line(out, "huawei_display_property", getProp("ro.huawei.build.display.id"));
        line(out, "huawei_rom_version", getProp("ro.huawei.build.version.incremental"));

        section(out, "MEMORY / STORAGE");
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        line(out, "ram_total_mb", mb(mi.totalMem));
        line(out, "ram_available_mb", mb(mi.availMem));
        line(out, "ram_low_memory", String.valueOf(mi.lowMemory));
        line(out, "ram_low_threshold_mb", mb(mi.threshold));
        Runtime rt = Runtime.getRuntime();
        line(out, "fixhua_heap_used_mb", mb(rt.totalMemory() - rt.freeMemory()));
        line(out, "fixhua_heap_max_mb", mb(rt.maxMemory()));
        StatFs stat = new StatFs(Environment.getDataDirectory().getAbsolutePath());
        line(out, "data_storage_total_mb", mb(stat.getTotalBytes()));
        line(out, "data_storage_available_mb", mb(stat.getAvailableBytes()));
        line(out, "proc_meminfo_excerpt", squash(readTextFile("/proc/meminfo", 2400)));
        line(out, "memory_psi", squash(readTextFile("/proc/pressure/memory", 1200)));
        line(out, "swap_info", squash(readTextFile("/proc/swaps", 1000)));

        section(out, "POWER / THERMAL / BATTERY");
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        line(out, "power_save_mode", String.valueOf(pm.isPowerSaveMode()));
        line(out, "device_idle_mode", String.valueOf(pm.isDeviceIdleMode()));
        line(out, "thermal_status", thermalName(pm.getCurrentThermalStatus()));
        line(out, "fixhua_battery_optimization_ignored", String.valueOf(pm.isIgnoringBatteryOptimizations(context.getPackageName())));
        line(out, "gbox_battery_optimization_ignored", safeBatteryWhitelist(pm, "com.gbox.android"));

        BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        line(out, "battery_capacity_percent", String.valueOf(bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)));
        line(out, "battery_current_now_uA", String.valueOf(bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)));
        Intent battery = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (battery != null) {
            int temp = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
            int voltage = battery.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
            int status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            int health = battery.getIntExtra(BatteryManager.EXTRA_HEALTH, -1);
            int plugged = battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
            line(out, "battery_temp_c", temp == Integer.MIN_VALUE ? "unknown" : String.format(Locale.US, "%.1f", temp / 10.0));
            line(out, "battery_voltage_mv", String.valueOf(voltage));
            line(out, "battery_status", String.valueOf(status));
            line(out, "battery_health", String.valueOf(health));
            line(out, "battery_plugged", String.valueOf(plugged));
        }

        section(out, "NETWORK");
        appendNetwork(context, out);

        section(out, "WEBVIEW");
        try {
            PackageInfo webView = WebView.getCurrentWebViewPackage();
            if (webView == null) {
                line(out, "webview", "not resolved");
            } else {
                line(out, "webview_package", webView.packageName);
                line(out, "webview_version", webView.versionName + " (" + webView.getLongVersionCode() + ")");
            }
        } catch (Throwable t) {
            line(out, "webview", "error: " + t.getClass().getSimpleName());
        }

        section(out, "GBOX / GOOGLE COMPATIBILITY PACKAGES");
        appendPackage(context, out, "GBox", "com.gbox.android");
        appendPackage(context, out, "Google Play services / microG", "com.google.android.gms");
        appendPackage(context, out, "Play Store / microG Companion", "com.android.vending");
        appendPackage(context, out, "Google Services Framework", "com.google.android.gsf");
        appendPackage(context, out, "Huawei HMS Core", "com.huawei.hwid");
        appendPackage(context, out, "Huawei AppGallery", "com.huawei.appmarket");
        appendPackage(context, out, "GSpace", "com.gspace.android");
        appendPackage(context, out, "Aurora Store", "com.aurora.store");
        appendPackage(context, out, "Shizuku", "moe.shizuku.privileged.api");

        section(out, "FIXHUA HISTORICAL EXITS");
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                List<ApplicationExitInfo> exits = am.getHistoricalProcessExitReasons(context.getPackageName(), 0, 8);
                if (exits.isEmpty()) {
                    out.append("none\n");
                } else {
                    for (ApplicationExitInfo exit : exits) {
                        out.append("time=").append(new Date(exit.getTimestamp()))
                                .append(" reason=").append(exit.getReason())
                                .append(" importance=").append(exit.getImportance())
                                .append(" pss_kb=").append(exit.getPss())
                                .append(" rss_kb=").append(exit.getRss())
                                .append('\n');
                    }
                }
            } catch (Throwable t) {
                line(out, "exit_history", "unavailable: " + t.getClass().getSimpleName());
            }
        } else {
            line(out, "exit_history", "requires Android 11+");
        }

        section(out, "KNOWN VISIBILITY LIMITS");
        out.append("A normal Android app cannot read another app's private logs, internal GBox guest processes, ANR traces, or historical exit reasons.\n");
        out.append("A later optional Shizuku/ADB mode can add privileged diagnostics if baseline evidence shows it is necessary.\n");

        return out.toString();
    }

    static String sampleLine(Context context) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        Intent battery = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int temp = battery == null ? Integer.MIN_VALUE : battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
        return now()
                + " availRamMB=" + mb(mi.availMem)
                + " totalRamMB=" + mb(mi.totalMem)
                + " lowMemory=" + mi.lowMemory
                + " thermal=" + thermalName(pm.getCurrentThermalStatus())
                + " powerSave=" + pm.isPowerSaveMode()
                + " battery=" + bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                + " batteryTempC=" + (temp == Integer.MIN_VALUE ? "unknown" : String.format(Locale.US, "%.1f", temp / 10.0))
                + " network=" + networkSummary(context)
                + " gboxPowerWhitelist=" + safeBatteryWhitelist(pm, "com.gbox.android");
    }

    private static void appendNetwork(Context context, StringBuilder out) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        Network active = cm.getActiveNetwork();
        NetworkCapabilities nc = active == null ? null : cm.getNetworkCapabilities(active);
        line(out, "active_network", nc == null ? "none" : transports(nc));
        line(out, "metered", String.valueOf(cm.isActiveNetworkMetered()));
        if (nc != null) {
            line(out, "validated", String.valueOf(nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)));
            line(out, "internet_capability", String.valueOf(nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)));
            line(out, "not_restricted", String.valueOf(nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)));
        }
    }

    private static String networkSummary(Context context) {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            Network n = cm.getActiveNetwork();
            NetworkCapabilities nc = n == null ? null : cm.getNetworkCapabilities(n);
            if (nc == null) return "none";
            return transports(nc) + "/validated=" + nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) + "/metered=" + cm.isActiveNetworkMetered();
        } catch (Throwable t) {
            return "error";
        }
    }

    private static String transports(NetworkCapabilities nc) {
        StringBuilder s = new StringBuilder();
        if (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) s.append("WIFI+");
        if (nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) s.append("CELLULAR+");
        if (nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) s.append("ETHERNET+");
        if (nc.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) s.append("VPN+");
        if (s.length() == 0) return "OTHER";
        return s.substring(0, s.length() - 1);
    }

    private static void appendPackage(Context context, StringBuilder out, String label, String pkg) {
        PackageManager pm = context.getPackageManager();
        try {
            PackageInfo pi = pm.getPackageInfo(pkg, 0);
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            String appLabel = String.valueOf(pm.getApplicationLabel(ai));
            String installer;
            if (Build.VERSION.SDK_INT >= 30) {
                installer = pm.getInstallSourceInfo(pkg).getInstallingPackageName();
            } else {
                installer = pm.getInstallerPackageName(pkg);
            }
            out.append(label).append(": INSTALLED")
                    .append(" package=").append(pkg)
                    .append(" appLabel=").append(appLabel)
                    .append(" version=").append(pi.versionName)
                    .append(" code=").append(pi.getLongVersionCode())
                    .append(" installer=").append(installer == null ? "unknown" : installer)
                    .append('\n');
        } catch (PackageManager.NameNotFoundException e) {
            out.append(label).append(": NOT_INSTALLED package=").append(pkg).append('\n');
        } catch (Throwable t) {
            out.append(label).append(": ERROR package=").append(pkg).append(" error=").append(t.getClass().getSimpleName()).append('\n');
        }
    }

    private static String safeBatteryWhitelist(PowerManager pm, String pkg) {
        try {
            return String.valueOf(pm.isIgnoringBatteryOptimizations(pkg));
        } catch (Throwable t) {
            return "unavailable:" + t.getClass().getSimpleName();
        }
    }

    private static String getProp(String key) {
        try {
            Process p = new ProcessBuilder("getprop", key).redirectErrorStream(true).start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String v = r.readLine();
                return v == null || v.trim().isEmpty() ? "not_exposed" : v.trim();
            } finally {
                p.destroy();
            }
        } catch (Throwable t) {
            return "unavailable:" + t.getClass().getSimpleName();
        }
    }

    private static String readTextFile(String path, int maxChars) {
        File f = new File(path);
        if (!f.canRead()) return "unavailable";
        StringBuilder b = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f)))) {
            String line;
            while ((line = r.readLine()) != null && b.length() < maxChars) {
                b.append(line).append(';');
            }
            return b.toString();
        } catch (Throwable t) {
            return "unavailable:" + t.getClass().getSimpleName();
        }
    }

    private static String squash(String s) {
        return s == null ? "unknown" : s.replace('\n', ' ').replace('\r', ' ').trim();
    }

    private static String thermalName(int value) {
        switch (value) {
            case PowerManager.THERMAL_STATUS_NONE: return "NONE";
            case PowerManager.THERMAL_STATUS_LIGHT: return "LIGHT";
            case PowerManager.THERMAL_STATUS_MODERATE: return "MODERATE";
            case PowerManager.THERMAL_STATUS_SEVERE: return "SEVERE";
            case PowerManager.THERMAL_STATUS_CRITICAL: return "CRITICAL";
            case PowerManager.THERMAL_STATUS_EMERGENCY: return "EMERGENCY";
            case PowerManager.THERMAL_STATUS_SHUTDOWN: return "SHUTDOWN";
            default: return "UNKNOWN(" + value + ")";
        }
    }

    private static void section(StringBuilder out, String name) {
        out.append("\n=== ").append(name).append(" ===\n");
    }

    private static void line(StringBuilder out, String key, String value) {
        out.append(key).append('=').append(value == null ? "null" : value).append('\n');
    }

    private static String mb(long bytes) {
        return String.valueOf(bytes / 1024L / 1024L);
    }

    private static String now() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.US).format(new Date());
    }
}
