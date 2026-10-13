package com.haggag.inesystemprobe;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.MemoryInfo;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.os.StatFs;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private TextView status;
    private TextView reportView;
    private Button scanButton;
    private Button copyButton;
    private Button shareButton;
    private volatile String currentReport = "";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void buildUi() {
        int pad = dp(16);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("INE System Probe");
        title.setTextSize(24f);
        title.setGravity(Gravity.START);
        root.addView(title, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText("تشخيص خام للهاتف الحالي — بدون منطق GBox أو أحكام جاهزية مسبقة");
        subtitle.setTextSize(14f);
        subtitle.setPadding(0, dp(6), 0, dp(12));
        root.addView(subtitle, matchWrap());

        scanButton = new Button(this);
        scanButton.setText("تشغيل الفحص");
        scanButton.setOnClickListener(v -> runProbe());
        root.addView(scanButton, matchWrap());

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        copyButton = new Button(this);
        copyButton.setText("نسخ التقرير");
        copyButton.setEnabled(false);
        copyButton.setOnClickListener(v -> copyReport());
        actions.addView(copyButton, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        shareButton = new Button(this);
        shareButton.setText("مشاركة");
        shareButton.setEnabled(false);
        shareButton.setOnClickListener(v -> shareReport());
        actions.addView(shareButton, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(actions, matchWrap());

        status = new TextView(this);
        status.setText("جاهز للفحص.");
        status.setPadding(0, dp(10), 0, dp(10));
        root.addView(status, matchWrap());

        ScrollView scroll = new ScrollView(this);
        reportView = new TextView(this);
        reportView.setTextSize(12f);
        reportView.setTextIsSelectable(true);
        reportView.setTypeface(android.graphics.Typeface.MONOSPACE);
        scroll.addView(reportView, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void runProbe() {
        scanButton.setEnabled(false);
        copyButton.setEnabled(false);
        shareButton.setEnabled(false);
        status.setText("جاري جمع القياسات...");
        reportView.setText("");

        executor.execute(() -> {
            final String report = buildReport();
            currentReport = report;
            runOnUiThread(() -> {
                reportView.setText(report);
                status.setText("اكتمل الفحص. القيم UNAVAILABLE تعني أن Android منع القراءة أو أن المصدر غير موجود.");
                scanButton.setEnabled(true);
                copyButton.setEnabled(true);
                shareButton.setEnabled(true);
            });
        });
    }

    private String buildReport() {
        StringBuilder r = new StringBuilder(16384);
        Map<String, String> props = loadSystemProperties();
        section(r, "INE SYSTEM PROBE v0.1.0");
        add(r, "generated", timestamp());
        add(r, "scope", "raw-system-profile");
        add(r, "privacy", "no accounts, contacts, messages, photos, location, Android ID, serial number, or IP address collected");

        section(r, "DEVICE / OS");
        add(r, "manufacturer", Build.MANUFACTURER);
        add(r, "brand", Build.BRAND);
        add(r, "model", Build.MODEL);
        add(r, "device", Build.DEVICE);
        add(r, "product", Build.PRODUCT);
        add(r, "android_release", Build.VERSION.RELEASE);
        add(r, "sdk", Build.VERSION.SDK_INT);
        add(r, "security_patch", Build.VERSION.SECURITY_PATCH);
        add(r, "build_display", Build.DISPLAY);
        add(r, "build_type", Build.TYPE);
        add(r, "build_tags", Build.TAGS);
        add(r, "emui", prop(props, "ro.build.version.emui"));
        add(r, "cust", prop(props, "ro.build.version.incremental"));
        add(r, "kernel", System.getProperty("os.version", "UNAVAILABLE"));
        add(r, "uptime_seconds", SystemClock.elapsedRealtime() / 1000L);
        add(r, "boot_count", globalInt(Settings.Global.BOOT_COUNT));

        section(r, "CPU");
        add(r, "logical_cores", Runtime.getRuntime().availableProcessors());
        add(r, "supported_abis", join(Build.SUPPORTED_ABIS));
        add(r, "cpu_hardware", cpuHardware());
        add(r, "cpu_loadavg", safeReadFirstLine("/proc/loadavg"));
        add(r, "cpu_freqs_mhz", cpuFrequencies());

        section(r, "MEMORY / LMK / ZRAM");
        appendMemory(r);
        add(r, "proc_meminfo", compactLines(safeReadText("/proc/meminfo", 24), 14));
        add(r, "proc_swaps", compactLines(safeReadText("/proc/swaps", 12), 8));
        add(r, "zram_disksize_bytes", safeReadFirstLine("/sys/block/zram0/disksize"));
        add(r, "zram_mm_stat", safeReadFirstLine("/sys/block/zram0/mm_stat"));
        add(r, "lmk_minfree", safeReadFirstLine("/sys/module/lowmemorykiller/parameters/minfree"));

        section(r, "STORAGE");
        appendStorage(r);

        section(r, "GPU / DISPLAY");
        appendGpuDisplay(r);

        section(r, "BATTERY / POWER / THERMAL");
        appendBatteryPower(r);
        add(r, "thermal_zones", thermalZones());

        section(r, "NETWORK");
        appendNetwork(r);

        section(r, "WEBVIEW / RUNTIME");
        appendWebView(r);
        add(r, "visible_processes_to_app", visibleProcessCount());
        add(r, "default_launcher", defaultLauncher());

        section(r, "GOOGLE / HUAWEI STACK PRESENCE");
        appendPackage(r, "google_play_services", "com.google.android.gms");
        appendPackage(r, "play_store", "com.android.vending");
        appendPackage(r, "google_services_framework", "com.google.android.gsf");
        appendPackage(r, "hms_core", "com.huawei.hwid");
        appendPackage(r, "appgallery", "com.huawei.appmarket");
        appendPackage(r, "huawei_system_manager", "com.huawei.systemmanager");

        section(r, "SYSTEM SETTINGS RELEVANT TO PERFORMANCE");
        add(r, "power_save_mode", isPowerSaveMode());
        add(r, "device_idle_mode", isDeviceIdleMode());
        add(r, "always_finish_activities", globalInt(Settings.Global.ALWAYS_FINISH_ACTIVITIES));
        add(r, "development_settings_enabled", globalInt(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED));
        add(r, "window_animation_scale", globalFloat(Settings.Global.WINDOW_ANIMATION_SCALE));
        add(r, "transition_animation_scale", globalFloat(Settings.Global.TRANSITION_ANIMATION_SCALE));
        add(r, "animator_duration_scale", globalFloat(Settings.Global.ANIMATOR_DURATION_SCALE));
        add(r, "background_process_limit", globalInt("background_process_limit"));

        section(r, "BOOT / SECURITY STATE");
        add(r, "verified_boot_state", prop(props, "ro.boot.verifiedbootstate"));
        add(r, "vbmeta_device_state", prop(props, "ro.boot.vbmeta.device_state"));
        add(r, "flash_locked", prop(props, "ro.boot.flash.locked"));
        add(r, "verity_mode", prop(props, "ro.boot.veritymode"));
        add(r, "selinux", commandOneLine("/system/bin/getenforce"));
        add(r, "root_binary_indicators", rootIndicators());

        section(r, "READABILITY NOTE");
        r.append("UNAVAILABLE is not treated as a fault. It only means the data source was absent or not readable by a normal Android app.\n");
        r.append("This report intentionally makes no optimization recommendation; recommendations should be derived after reviewing the measurements from this device.\n");
        return r.toString();
    }

    private void appendMemory(StringBuilder r) {
        try {
            ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            MemoryInfo mi = new MemoryInfo();
            am.getMemoryInfo(mi);
            add(r, "ram_total_mb", bytesToMb(mi.totalMem));
            add(r, "ram_available_mb", bytesToMb(mi.availMem));
            add(r, "ram_threshold_mb", bytesToMb(mi.threshold));
            add(r, "low_memory", mi.lowMemory);
            add(r, "memory_class_mb", am.getMemoryClass());
            add(r, "large_memory_class_mb", am.getLargeMemoryClass());
            add(r, "low_ram_device", am.isLowRamDevice());
        } catch (Throwable t) {
            add(r, "memory_api", "UNAVAILABLE");
        }
    }

    private void appendStorage(StringBuilder r) {
        try {
            StatFs s = new StatFs(Environment.getDataDirectory().getAbsolutePath());
            long total = s.getTotalBytes();
            long free = s.getAvailableBytes();
            add(r, "data_total_mb", bytesToMb(total));
            add(r, "data_available_mb", bytesToMb(free));
            add(r, "data_available_percent", total > 0 ? String.format(Locale.US, "%.1f", free * 100.0 / total) : "UNAVAILABLE");
            add(r, "block_size_bytes", s.getBlockSizeLong());
        } catch (Throwable t) {
            add(r, "storage_api", "UNAVAILABLE");
        }
    }

    private void appendGpuDisplay(StringBuilder r) {
        try {
            ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            add(r, "gles_declared", am.getDeviceConfigurationInfo().getGlEsVersion());
        } catch (Throwable t) {
            add(r, "gles_declared", "UNAVAILABLE");
        }
        Map<String, String> gl = captureGl();
        add(r, "gl_vendor", gl.get("vendor"));
        add(r, "gl_renderer", gl.get("renderer"));
        add(r, "gl_version", gl.get("version"));
        try {
            Display d = getWindowManager().getDefaultDisplay();
            add(r, "display_width_px", getResources().getDisplayMetrics().widthPixels);
            add(r, "display_height_px", getResources().getDisplayMetrics().heightPixels);
            add(r, "display_density_dpi", getResources().getDisplayMetrics().densityDpi);
            add(r, "display_refresh_hz", String.format(Locale.US, "%.2f", d.getRefreshRate()));
        } catch (Throwable t) {
            add(r, "display_api", "UNAVAILABLE");
        }
    }

    private void appendBatteryPower(StringBuilder r) {
        try {
            Intent b = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (b != null) {
                int level = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                add(r, "battery_percent", level >= 0 ? Math.round(level * 100f / Math.max(1, scale)) : "UNAVAILABLE");
                add(r, "battery_temp_c", tempTenths(b.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE)));
                add(r, "battery_voltage_mv", b.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1));
                add(r, "battery_status", b.getIntExtra(BatteryManager.EXTRA_STATUS, -1));
                add(r, "battery_plugged", b.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1));
            }
        } catch (Throwable t) {
            add(r, "battery_broadcast", "UNAVAILABLE");
        }
        try {
            BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
            add(r, "battery_capacity_property", bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY));
            add(r, "battery_current_now_ua", bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW));
            add(r, "battery_charge_counter_uah", bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER));
            add(r, "battery_energy_counter_nwh", bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER));
        } catch (Throwable t) {
            add(r, "battery_manager", "UNAVAILABLE");
        }
    }

    private void appendNetwork(StringBuilder r) {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            NetworkInfo ni = cm.getActiveNetworkInfo();
            if (ni == null) {
                add(r, "active_network", "none");
            } else {
                add(r, "active_network", ni.getTypeName());
                add(r, "network_subtype", ni.getSubtypeName());
                add(r, "network_connected", ni.isConnected());
                add(r, "network_metered", cm.isActiveNetworkMetered());
            }
        } catch (Throwable t) {
            add(r, "network_api", "UNAVAILABLE");
        }
    }

    private void appendWebView(StringBuilder r) {
        try {
            PackageInfo p = WebView.getCurrentWebViewPackage();
            if (p == null) {
                add(r, "webview_package", "UNAVAILABLE");
            } else {
                add(r, "webview_package", p.packageName);
                add(r, "webview_version", p.versionName);
            }
        } catch (Throwable t) {
            add(r, "webview_package", "UNAVAILABLE");
        }
    }

    private void appendPackage(StringBuilder r, String label, String packageName) {
        try {
            PackageManager pm = getPackageManager();
            PackageInfo p = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES);
            add(r, label + "_installed", true);
            add(r, label + "_version", p.versionName);
            add(r, label + "_version_code", p.getLongVersionCode());
            String installer = pm.getInstallerPackageName(packageName);
            add(r, label + "_installer", installer == null ? "unknown" : installer);
            if (p.signatures != null && p.signatures.length > 0) {
                add(r, label + "_signer_sha256", sha256(p.signatures[0].toByteArray()));
            }
        } catch (PackageManager.NameNotFoundException e) {
            add(r, label + "_installed", false);
        } catch (Throwable t) {
            add(r, label + "_installed", "UNAVAILABLE");
        }
    }

    private boolean isPowerSaveMode() {
        try {
            return ((PowerManager) getSystemService(POWER_SERVICE)).isPowerSaveMode();
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean isDeviceIdleMode() {
        try {
            return ((PowerManager) getSystemService(POWER_SERVICE)).isDeviceIdleMode();
        } catch (Throwable t) {
            return false;
        }
    }

    private String visibleProcessCount() {
        try {
            ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            List<RunningAppProcessInfo> list = am.getRunningAppProcesses();
            return list == null ? "UNAVAILABLE" : Integer.toString(list.size());
        } catch (Throwable t) {
            return "UNAVAILABLE";
        }
    }

    private String defaultLauncher() {
        try {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_HOME);
            ResolveInfo ri = getPackageManager().resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY);
            return ri == null || ri.activityInfo == null ? "UNAVAILABLE" : ri.activityInfo.packageName;
        } catch (Throwable t) {
            return "UNAVAILABLE";
        }
    }

    private String cpuHardware() {
        String text = safeReadText("/proc/cpuinfo", 180);
        if (text.startsWith("UNAVAILABLE")) return text;
        String[] keys = {"Hardware", "model name", "Processor"};
        for (String key : keys) {
            for (String line : text.split("\\n")) {
                if (line.toLowerCase(Locale.ROOT).startsWith(key.toLowerCase(Locale.ROOT))) {
                    int p = line.indexOf(':');
                    return p >= 0 ? line.substring(p + 1).trim() : line.trim();
                }
            }
        }
        return "UNAVAILABLE";
    }

    private String cpuFrequencies() {
        List<String> parts = new ArrayList<>();
        int cores = Runtime.getRuntime().availableProcessors();
        for (int i = 0; i < cores; i++) {
            String base = "/sys/devices/system/cpu/cpu" + i + "/cpufreq/";
            String cur = safeReadFirstLine(base + "scaling_cur_freq");
            String max = safeReadFirstLine(base + "cpuinfo_max_freq");
            if (!cur.startsWith("UNAVAILABLE") || !max.startsWith("UNAVAILABLE")) {
                parts.add("cpu" + i + ":cur=" + khzToMhz(cur) + ",max=" + khzToMhz(max));
            }
        }
        return parts.isEmpty() ? "UNAVAILABLE" : join(parts.toArray(new String[0]));
    }

    private String thermalZones() {
        File root = new File("/sys/class/thermal");
        File[] children = root.listFiles();
        if (children == null) return "UNAVAILABLE";
        List<String> out = new ArrayList<>();
        int count = 0;
        for (File f : children) {
            if (!f.getName().startsWith("thermal_zone")) continue;
            String type = safeReadFirstLine(new File(f, "type").getAbsolutePath());
            String temp = safeReadFirstLine(new File(f, "temp").getAbsolutePath());
            if (!type.startsWith("UNAVAILABLE") || !temp.startsWith("UNAVAILABLE")) {
                out.add(f.getName() + ":" + type + "=" + normalizeThermal(temp));
                count++;
                if (count >= 24) break;
            }
        }
        return out.isEmpty() ? "UNAVAILABLE" : join(out.toArray(new String[0]));
    }

    private Map<String, String> loadSystemProperties() {
        Map<String, String> out = new LinkedHashMap<>();
        try {
            Process p = new ProcessBuilder("/system/bin/getprop").redirectErrorStream(true).start();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (!line.startsWith("[") || !line.contains("]: [")) continue;
                    int split = line.indexOf("]: [");
                    if (split <= 1 || !line.endsWith("]")) continue;
                    String key = line.substring(1, split);
                    String value = line.substring(split + 4, line.length() - 1);
                    out.put(key, value);
                }
            }
            p.waitFor();
        } catch (Throwable ignored) {
        }
        return out;
    }

    private String prop(Map<String, String> props, String key) {
        String v = props.get(key);
        return v == null || v.trim().isEmpty() ? "UNAVAILABLE" : v.trim();
    }

    private Map<String, String> captureGl() {
        Map<String, String> out = new LinkedHashMap<>();
        out.put("vendor", "UNAVAILABLE");
        out.put("renderer", "UNAVAILABLE");
        out.put("version", "UNAVAILABLE");
        EGLDisplay display = EGL14.EGL_NO_DISPLAY;
        EGLSurface surface = EGL14.EGL_NO_SURFACE;
        EGLContext context = EGL14.EGL_NO_CONTEXT;
        try {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
            int[] version = new int[2];
            if (!EGL14.eglInitialize(display, version, 0, version, 1)) return out;
            int[] configAttribs = {
                    EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                    EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                    EGL14.EGL_RED_SIZE, 8,
                    EGL14.EGL_GREEN_SIZE, 8,
                    EGL14.EGL_BLUE_SIZE, 8,
                    EGL14.EGL_NONE
            };
            EGLConfig[] configs = new EGLConfig[1];
            int[] num = new int[1];
            if (!EGL14.eglChooseConfig(display, configAttribs, 0, configs, 0, 1, num, 0) || num[0] == 0) return out;
            int[] ctxAttribs = {EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE};
            context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT, ctxAttribs, 0);
            int[] surfaceAttribs = {EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE};
            surface = EGL14.eglCreatePbufferSurface(display, configs[0], surfaceAttribs, 0);
            if (!EGL14.eglMakeCurrent(display, surface, surface, context)) return out;
            out.put("vendor", nullSafe(GLES20.glGetString(GLES20.GL_VENDOR)));
            out.put("renderer", nullSafe(GLES20.glGetString(GLES20.GL_RENDERER)));
            out.put("version", nullSafe(GLES20.glGetString(GLES20.GL_VERSION)));
        } catch (Throwable ignored) {
        } finally {
            try {
                if (display != EGL14.EGL_NO_DISPLAY) {
                    EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
                    if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface);
                    if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context);
                    EGL14.eglTerminate(display);
                }
            } catch (Throwable ignored) {
            }
        }
        return out;
    }

    private String rootIndicators() {
        String[] paths = {
                "/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su",
                "/data/adb/magisk", "/system/app/Superuser.apk"
        };
        List<String> found = new ArrayList<>();
        for (String path : paths) {
            try {
                if (new File(path).exists()) found.add(path);
            } catch (Throwable ignored) {
            }
        }
        return found.isEmpty() ? "none-visible" : join(found.toArray(new String[0]));
    }

    private String commandOneLine(String command) {
        try {
            Process p = new ProcessBuilder(command).redirectErrorStream(true).start();
            String line;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                line = br.readLine();
            }
            p.waitFor();
            return line == null || line.trim().isEmpty() ? "UNAVAILABLE" : line.trim();
        } catch (Throwable t) {
            return "UNAVAILABLE";
        }
    }

    private int globalInt(String key) {
        try {
            return Settings.Global.getInt(getContentResolver(), key);
        } catch (Throwable t) {
            return Integer.MIN_VALUE;
        }
    }

    private String globalFloat(String key) {
        try {
            return Float.toString(Settings.Global.getFloat(getContentResolver(), key));
        } catch (Throwable t) {
            return "UNAVAILABLE";
        }
    }

    private String safeReadFirstLine(String path) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(path), StandardCharsets.UTF_8))) {
            String line = br.readLine();
            return line == null || line.trim().isEmpty() ? "UNAVAILABLE" : line.trim();
        } catch (Throwable t) {
            return "UNAVAILABLE";
        }
    }

    private String safeReadText(String path, int maxLines) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(path), StandardCharsets.UTF_8))) {
            StringBuilder out = new StringBuilder();
            String line;
            int count = 0;
            while ((line = br.readLine()) != null && count < maxLines) {
                out.append(line).append('\n');
                count++;
            }
            return out.length() == 0 ? "UNAVAILABLE" : out.toString().trim();
        } catch (Throwable t) {
            return "UNAVAILABLE";
        }
    }

    private String compactLines(String text, int maxLines) {
        if (text == null || text.startsWith("UNAVAILABLE")) return "UNAVAILABLE";
        String[] lines = text.split("\\n");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.length && i < maxLines; i++) {
            if (i > 0) out.append(" | ");
            out.append(lines[i].trim().replaceAll("\\s+", " "));
        }
        return out.toString();
    }

    private String khzToMhz(String value) {
        try {
            return String.format(Locale.US, "%.0f", Long.parseLong(value.trim()) / 1000.0);
        } catch (Throwable t) {
            return "UNAVAILABLE";
        }
    }

    private String normalizeThermal(String value) {
        try {
            double v = Double.parseDouble(value.trim());
            if (Math.abs(v) > 1000) v /= 1000.0;
            else if (Math.abs(v) > 200) v /= 10.0;
            return String.format(Locale.US, "%.1fC", v);
        } catch (Throwable t) {
            return value;
        }
    }

    private String tempTenths(int value) {
        if (value == Integer.MIN_VALUE) return "UNAVAILABLE";
        return String.format(Locale.US, "%.1f", value / 10.0);
    }

    private long bytesToMb(long bytes) {
        return bytes / (1024L * 1024L);
    }

    private String timestamp() {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US);
        f.setTimeZone(TimeZone.getDefault());
        return f.format(new Date());
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(bytes);
            StringBuilder s = new StringBuilder();
            for (byte b : digest) s.append(String.format(Locale.US, "%02x", b));
            return s.toString();
        } catch (Throwable t) {
            return "UNAVAILABLE";
        }
    }

    private String join(String[] values) {
        if (values == null || values.length == 0) return "UNAVAILABLE";
        StringBuilder s = new StringBuilder();
        for (String value : values) {
            if (s.length() > 0) s.append(", ");
            s.append(value);
        }
        return s.toString();
    }

    private String nullSafe(String value) {
        return value == null || value.trim().isEmpty() ? "UNAVAILABLE" : value.trim();
    }

    private void section(StringBuilder r, String title) {
        r.append("\n=== ").append(title).append(" ===\n");
    }

    private void add(StringBuilder r, String key, Object value) {
        if (value instanceof Integer && ((Integer) value) == Integer.MIN_VALUE) {
            r.append(key).append("=UNAVAILABLE\n");
        } else {
            r.append(key).append('=').append(value == null ? "UNAVAILABLE" : value).append('\n');
        }
    }

    private void copyReport() {
        if (currentReport.isEmpty()) return;
        android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cm.setPrimaryClip(android.content.ClipData.newPlainText("INE System Probe report", currentReport));
        Toast.makeText(this, "تم نسخ التقرير", Toast.LENGTH_SHORT).show();
    }

    private void shareReport() {
        if (currentReport.isEmpty()) return;
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_SUBJECT, "INE System Probe report");
        i.putExtra(Intent.EXTRA_TEXT, currentReport);
        startActivity(Intent.createChooser(i, "مشاركة التقرير"));
    }
}
