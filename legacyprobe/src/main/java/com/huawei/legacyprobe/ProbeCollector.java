package com.huawei.legacyprobe;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.PowerManager;
import android.os.StatFs;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import android.webkit.WebView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

final class ProbeCollector {
    private ProbeCollector() {}

    static String collect(Context context) {
        StringBuilder out = new StringBuilder(16384);
        line(out, "Huawei Legacy Probe Report v1.0");
        line(out, "Generated: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(new Date()));
        line(out, "Privacy: no accounts, contacts, messages, photos, location, IMEI, serial, Android ID, SSID, MAC, or IP address are collected.");

        section(out, "DEVICE / SYSTEM");
        kv(out, "manufacturer", Build.MANUFACTURER);
        kv(out, "brand", Build.BRAND);
        kv(out, "model", Build.MODEL);
        kv(out, "device", Build.DEVICE);
        kv(out, "product", Build.PRODUCT);
        kv(out, "board", Build.BOARD);
        kv(out, "hardware", Build.HARDWARE);
        kv(out, "android_release", Build.VERSION.RELEASE);
        kv(out, "sdk", Build.VERSION.SDK_INT);
        kv(out, "build_display", Build.DISPLAY);
        kv(out, "build_type", Build.TYPE);
        kv(out, "build_tags", Build.TAGS);
        kv(out, "security_patch", Build.VERSION.SDK_INT >= 23 ? Build.VERSION.SECURITY_PATCH : "unavailable");
        kv(out, "kernel_version", System.getProperty("os.version", "unknown"));
        kv(out, "emui", getprop("ro.build.version.emui"));
        kv(out, "emui_api", getprop("ro.build.hw_emui_api_level"));
        kv(out, "incremental", Build.VERSION.INCREMENTAL);
        kv(out, "bootloader", Build.BOOTLOADER);

        section(out, "CPU / ABI");
        kv(out, "available_processors", Runtime.getRuntime().availableProcessors());
        kv(out, "supported_abis", join(Build.SUPPORTED_ABIS));
        kv(out, "cpu_abi", getprop("ro.product.cpu.abi"));
        kv(out, "cpu_abilist", getprop("ro.product.cpu.abilist"));
        kv(out, "soc_model", firstNonEmpty(getprop("ro.soc.model"), getprop("ro.board.platform"), readFirstMatching("/proc/cpuinfo", "Hardware")));
        appendCpuFreq(out);

        section(out, "MEMORY");
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (am != null) {
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            kv(out, "ram_total_mb", mb(mi.totalMem));
            kv(out, "ram_available_mb", mb(mi.availMem));
            kv(out, "low_memory", mi.lowMemory);
            kv(out, "low_memory_threshold_mb", mb(mi.threshold));
            kv(out, "memory_class_mb", am.getMemoryClass());
            kv(out, "large_memory_class_mb", am.getLargeMemoryClass());
        }
        appendMemInfo(out);

        section(out, "STORAGE");
        appendStorage(out, "data", Environment.getDataDirectory());
        appendStorage(out, "external", Environment.getExternalStorageDirectory());

        section(out, "BATTERY / POWER / THERMAL");
        appendBattery(context, out);
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            kv(out, "power_save_mode", Build.VERSION.SDK_INT >= 21 && pm.isPowerSaveMode());
            kv(out, "device_idle_mode", Build.VERSION.SDK_INT >= 23 && pm.isDeviceIdleMode());
            if (Build.VERSION.SDK_INT >= 23) kv(out, "ignoring_battery_optimizations_self", pm.isIgnoringBatteryOptimizations(context.getPackageName()));
        }
        kv(out, "thermal_zone_summary", summarizeThermalZones());

        section(out, "DISPLAY");
        appendDisplay(context, out);

        section(out, "NETWORK (NO IDENTIFIERS)");
        appendNetwork(context, out);

        section(out, "SYSTEM SETTINGS (READ ONLY)");
        appendSettings(context, out);

        section(out, "GOOGLE / HUAWEI COMPONENTS");
        appendPackage(context, out, "google_play_services", "com.google.android.gms");
        appendPackage(context, out, "play_store", "com.android.vending");
        appendPackage(context, out, "google_services_framework", "com.google.android.gsf");
        appendPackage(context, out, "hms_core", "com.huawei.hwid");
        appendPackage(context, out, "appgallery", "com.huawei.appmarket");
        appendPackage(context, out, "huawei_system_manager", "com.huawei.systemmanager");
        appendPackage(context, out, "huawei_launcher", "com.huawei.android.launcher");
        appendPackage(context, out, "android_system_webview", "com.google.android.webview");
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                PackageInfo web = WebView.getCurrentWebViewPackage();
                kv(out, "active_webview", web == null ? "unknown" : web.packageName + " " + web.versionName);
            } catch (Throwable t) { kv(out, "active_webview", "unavailable:" + t.getClass().getSimpleName()); }
        }

        section(out, "SENSORS");
        appendSensors(context, out);

        section(out, "CAMERAS");
        appendCameras(context, out);

        section(out, "MEDIA CODECS");
        appendCodecs(out);

        section(out, "RUNTIME / PROC SNAPSHOT");
        kv(out, "uptime", readFirstLine("/proc/uptime"));
        kv(out, "loadavg", readFirstLine("/proc/loadavg"));
        kv(out, "vm_swappiness", readFirstLine("/proc/sys/vm/swappiness"));
        kv(out, "dirty_ratio", readFirstLine("/proc/sys/vm/dirty_ratio"));
        kv(out, "dirty_background_ratio", readFirstLine("/proc/sys/vm/dirty_background_ratio"));

        section(out, "NOTES");
        line(out, "This report is diagnostic only. Values marked unavailable are restricted by Android/Huawei and were not bypassed.");
        line(out, "Send this TXT report back in the chat; optimization recommendations should be based on this device-specific evidence.");
        return out.toString();
    }

    private static void appendCpuFreq(StringBuilder out) {
        int n = Math.max(1, Runtime.getRuntime().availableProcessors());
        for (int i = 0; i < n; i++) {
            String base = "/sys/devices/system/cpu/cpu" + i + "/cpufreq/";
            String max = readFirstLine(base + "cpuinfo_max_freq");
            String min = readFirstLine(base + "cpuinfo_min_freq");
            String cur = readFirstLine(base + "scaling_cur_freq");
            if (!"unavailable".equals(max) || !"unavailable".equals(cur)) {
                kv(out, "cpu" + i + "_freq_khz", "min=" + min + ", cur=" + cur + ", max=" + max);
            }
        }
    }

    private static void appendMemInfo(StringBuilder out) {
        String[] keys = {"MemTotal", "MemAvailable", "MemFree", "Buffers", "Cached", "SwapTotal", "SwapFree", "Zswap", "Shmem", "Slab"};
        for (String key : keys) {
            String v = readFirstMatching("/proc/meminfo", key + ":");
            if (!"unavailable".equals(v)) kv(out, "proc_" + key.toLowerCase(Locale.US), v);
        }
        kv(out, "zram_disksize", readFirstLine("/sys/block/zram0/disksize"));
        kv(out, "zram_compr_algorithm", readFirstLine("/sys/block/zram0/comp_algorithm"));
    }

    private static void appendStorage(StringBuilder out, String name, File file) {
        try {
            StatFs fs = new StatFs(file.getAbsolutePath());
            long total = fs.getTotalBytes();
            long free = fs.getAvailableBytes();
            kv(out, name + "_total_mb", mb(total));
            kv(out, name + "_free_mb", mb(free));
            kv(out, name + "_free_percent", total > 0 ? String.format(Locale.US, "%.1f", free * 100.0 / total) : "unknown");
        } catch (Throwable t) { kv(out, name + "_storage", "unavailable:" + t.getClass().getSimpleName()); }
    }

    private static void appendBattery(Context c, StringBuilder out) {
        Intent b = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (b == null) { kv(out, "battery", "unavailable"); return; }
        int level = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        kv(out, "battery_percent", level >= 0 && scale > 0 ? Math.round(level * 100f / scale) : -1);
        int temp = b.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
        kv(out, "battery_temp_c", temp == Integer.MIN_VALUE ? "unknown" : String.format(Locale.US, "%.1f", temp / 10f));
        kv(out, "battery_voltage_mv", b.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1));
        kv(out, "battery_health", b.getIntExtra(BatteryManager.EXTRA_HEALTH, -1));
        kv(out, "battery_status", b.getIntExtra(BatteryManager.EXTRA_STATUS, -1));
        kv(out, "battery_plugged", b.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0));
        BatteryManager bm = (BatteryManager) c.getSystemService(Context.BATTERY_SERVICE);
        if (bm != null && Build.VERSION.SDK_INT >= 21) {
            kv(out, "charge_counter_uah", bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER));
            kv(out, "current_now_ua", bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW));
            kv(out, "capacity_property", bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY));
        }
    }

    private static void appendDisplay(Context c, StringBuilder out) {
        DisplayMetrics dm = new DisplayMetrics();
        WindowManager wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
        if (wm != null) wm.getDefaultDisplay().getRealMetrics(dm);
        else dm = c.getResources().getDisplayMetrics();
        kv(out, "resolution_px", dm.widthPixels + "x" + dm.heightPixels);
        kv(out, "density_dpi", dm.densityDpi);
        kv(out, "density", dm.density);
        kv(out, "scaled_density", dm.scaledDensity);
        kv(out, "refresh_rate_hz", wm == null ? "unknown" : wm.getDefaultDisplay().getRefreshRate());
        kv(out, "brightness_mode", Settings.System.getInt(c.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, -1));
        kv(out, "brightness", Settings.System.getInt(c.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, -1));
        kv(out, "screen_off_timeout_ms", Settings.System.getInt(c.getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, -1));
    }

    @SuppressWarnings("deprecation")
    private static void appendNetwork(Context c, StringBuilder out) {
        ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) { kv(out, "network", "unavailable"); return; }
        NetworkInfo ni = cm.getActiveNetworkInfo();
        kv(out, "network_present", ni != null);
        kv(out, "network_connected", ni != null && ni.isConnected());
        kv(out, "network_type", ni == null ? "none" : ni.getTypeName());
        kv(out, "network_metered", cm.isActiveNetworkMetered());
        kv(out, "wifi_enabled", isWifiEnabled(c));
    }

    private static boolean isWifiEnabled(Context c) {
        try {
            android.net.wifi.WifiManager wm = (android.net.wifi.WifiManager) c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            return wm != null && wm.isWifiEnabled();
        } catch (Throwable t) { return false; }
    }

    private static void appendSettings(Context c, StringBuilder out) {
        String[] globals = {"development_settings_enabled", "adb_enabled", "always_finish_activities", "window_animation_scale", "transition_animation_scale", "animator_duration_scale", "stay_on_while_plugged_in", "auto_time", "auto_time_zone", "low_power", "device_idle_constants", "activity_manager_constants"};
        for (String k : globals) kv(out, "global_" + k, Settings.Global.getString(c.getContentResolver(), k));
        String[] systems = {"screen_brightness_mode", "screen_brightness", "screen_off_timeout", "accelerometer_rotation", "user_rotation", "font_scale"};
        for (String k : systems) kv(out, "system_" + k, Settings.System.getString(c.getContentResolver(), k));
        String[] secures = {"location_mode", "accessibility_enabled", "enabled_accessibility_services"};
        for (String k : secures) kv(out, "secure_" + k, Settings.Secure.getString(c.getContentResolver(), k));
    }

    private static void appendPackage(Context c, StringBuilder out, String label, String pkg) {
        try {
            PackageInfo p = c.getPackageManager().getPackageInfo(pkg, 0);
            kv(out, label, "installed version=" + p.versionName + " code=" + longVersion(p));
        } catch (PackageManager.NameNotFoundException e) { kv(out, label, "not_installed"); }
        catch (Throwable t) { kv(out, label, "unavailable:" + t.getClass().getSimpleName()); }
    }

    private static long longVersion(PackageInfo p) {
        return Build.VERSION.SDK_INT >= 28 ? p.getLongVersionCode() : p.versionCode;
    }

    private static void appendSensors(Context c, StringBuilder out) {
        SensorManager sm = (SensorManager) c.getSystemService(Context.SENSOR_SERVICE);
        if (sm == null) { kv(out, "sensors", "unavailable"); return; }
        List<Sensor> sensors = sm.getSensorList(Sensor.TYPE_ALL);
        kv(out, "sensor_count", sensors.size());
        int i = 0;
        for (Sensor s : sensors) {
            if (i++ >= 64) { line(out, "sensor_list_truncated=true"); break; }
            line(out, "sensor=" + clean(s.getName()) + " | vendor=" + clean(s.getVendor()) + " | type=" + s.getType() + " | maxRange=" + s.getMaximumRange() + " | resolution=" + s.getResolution() + " | power_mA=" + s.getPower());
        }
    }

    private static void appendCameras(Context c, StringBuilder out) {
        CameraManager cm = (CameraManager) c.getSystemService(Context.CAMERA_SERVICE);
        if (cm == null) { kv(out, "cameras", "unavailable"); return; }
        try {
            String[] ids = cm.getCameraIdList();
            kv(out, "camera_count", ids.length);
            for (int i = 0; i < ids.length; i++) {
                CameraCharacteristics cc = cm.getCameraCharacteristics(ids[i]);
                Integer facing = cc.get(CameraCharacteristics.LENS_FACING);
                Integer level = cc.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
                int[] caps = cc.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
                line(out, "camera" + i + "=facing:" + facing + ", hwLevel:" + level + ", capabilities:" + (caps == null ? "unknown" : ints(caps)));
            }
        } catch (Throwable t) { kv(out, "cameras", "unavailable:" + t.getClass().getSimpleName()); }
    }

    private static void appendCodecs(StringBuilder out) {
        try {
            MediaCodecInfo[] infos = new MediaCodecList(MediaCodecList.ALL_CODECS).getCodecInfos();
            kv(out, "codec_count", infos.length);
            int count = 0;
            for (MediaCodecInfo info : infos) {
                if (count++ >= 80) { line(out, "codec_list_truncated=true"); break; }
                line(out, "codec=" + clean(info.getName()) + " | encoder=" + info.isEncoder() + " | types=" + join(info.getSupportedTypes()));
            }
        } catch (Throwable t) { kv(out, "codecs", "unavailable:" + t.getClass().getSimpleName()); }
    }

    private static String summarizeThermalZones() {
        File root = new File("/sys/class/thermal");
        File[] files = root.listFiles();
        if (files == null) return "unavailable";
        List<String> vals = new ArrayList<>();
        for (File f : files) {
            if (!f.getName().startsWith("thermal_zone")) continue;
            String type = readFirstLine(new File(f, "type").getAbsolutePath());
            String temp = readFirstLine(new File(f, "temp").getAbsolutePath());
            if (!"unavailable".equals(temp)) vals.add(type + ":" + temp);
            if (vals.size() >= 24) break;
        }
        return vals.isEmpty() ? "unavailable" : join(vals.toArray(new String[0]));
    }

    private static String getprop(String key) {
        try {
            Process p = new ProcessBuilder("getprop", key).redirectErrorStream(true).start();
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String v = r.readLine();
            try { p.waitFor(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return empty(v) ? "unknown" : clean(v);
        } catch (Throwable t) { return "unavailable"; }
    }

    private static String readFirstLine(String path) {
        try (BufferedReader r = new BufferedReader(new FileReader(path))) {
            String s = r.readLine();
            return empty(s) ? "unknown" : clean(s);
        } catch (Throwable t) { return "unavailable"; }
    }

    private static String readFirstMatching(String path, String prefix) {
        try (BufferedReader r = new BufferedReader(new FileReader(path))) {
            String s;
            while ((s = r.readLine()) != null) if (s.startsWith(prefix)) return clean(s);
        } catch (Throwable ignored) {}
        return "unavailable";
    }

    private static String firstNonEmpty(String... vals) {
        for (String v : vals) if (!empty(v) && !"unknown".equals(v) && !"unavailable".equals(v)) return v;
        return "unknown";
    }

    private static long mb(long bytes) { return bytes / 1024L / 1024L; }
    private static void section(StringBuilder b, String s) { b.append("\n=== ").append(s).append(" ===\n"); }
    private static void kv(StringBuilder b, String k, Object v) { b.append(k).append('=').append(v == null ? "unknown" : clean(String.valueOf(v))).append('\n'); }
    private static void line(StringBuilder b, String s) { b.append(clean(s)).append('\n'); }
    private static boolean empty(String s) { return s == null || s.trim().isEmpty(); }
    private static String clean(String s) { return s == null ? "unknown" : s.replace('\n', ' ').replace('\r', ' ').trim(); }
    private static String join(String[] a) { if (a == null || a.length == 0) return "none"; StringBuilder b = new StringBuilder(); for (String s : a) { if (b.length() > 0) b.append(','); b.append(clean(s)); } return b.toString(); }
    private static String ints(int[] a) { StringBuilder b = new StringBuilder(); for (int v : a) { if (b.length() > 0) b.append(','); b.append(v); } return b.toString(); }
}
