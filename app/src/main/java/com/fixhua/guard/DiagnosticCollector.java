package com.fixhua.guard;

import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.InstallSourceInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.PowerManager;
import android.os.StatFs;
import android.os.SystemClock;
import android.provider.Settings;
import android.webkit.WebView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class DiagnosticCollector {
    static final String VERSION = "3.0.0";

    private static final String[] PROPS = {
            "ro.boot.verifiedbootstate", "ro.boot.flash.locked", "ro.boot.vbmeta.device_state",
            "ro.boot.device_state", "ro.boot.veritymode", "ro.boot.avb_version", "ro.boot.vbmeta.avb_version",
            "ro.boot.slot_suffix", "ro.boot.slot", "ro.build.ab_update", "ro.virtual_ab.enabled",
            "ro.virtual_ab.compression.enabled", "ro.boot.dynamic_partitions", "ro.treble.enabled",
            "ro.product.first_api_level", "ro.board.first_api_level", "ro.vendor.api_level",
            "ro.vendor.build.security_patch", "ro.build.version.security_patch", "ro.secure", "ro.debuggable",
            "ro.adb.secure", "ro.build.type", "ro.build.tags", "ro.build.flavor", "ro.crypto.state",
            "ro.crypto.type", "ro.product.cpu.abi", "ro.product.cpu.abilist", "ro.vendor.product.cpu.abilist",
            "ro.hardware", "ro.boot.hardware", "ro.boot.hardware.sku", "ro.boot.boot_devices",
            "ro.boot.bootdevice", "ro.build.system_root_image", "ro.boot.system_root_image", "ro.config.low_ram",
            "ro.boot.warranty_bit", "ro.build.version.emui", "ro.config.hw_emui_lite.enable",
            "ro.hw.country", "ro.boot.huawei_swtype"
    };

    private static final String[] RELEVANT = {
            "com.gbox.android", "com.gspace.android", "com.google.android.gms", "com.google.android.gsf",
            "com.android.vending", "com.huawei.hwid", "com.huawei.appmarket", "com.huawei.systemmanager",
            "com.huawei.webview", "com.google.android.webview", "com.android.webview",
            "com.topjohnwu.magisk", "me.weishu.kernelsu", "me.bmax.apatch",
            "moe.shizuku.privileged.api", "com.draco.ladb"
    };

    private static final Set<String> ROOT_MANAGERS = new HashSet<>(Arrays.asList(
            "com.topjohnwu.magisk", "me.weishu.kernelsu", "me.bmax.apatch"));

    static final class Result {
        final File zipFile;
        final String summary;
        final boolean usageAccessGranted;
        Result(File zipFile, String summary, boolean usageAccessGranted) {
            this.zipFile = zipFile;
            this.summary = summary;
            this.usageAccessGranted = usageAccessGranted;
        }
    }

    static Result collect(Context context) throws Exception {
        Context app = context.getApplicationContext();
        PackageManager pm = app.getPackageManager();
        LinkedHashMap<String,String> props = collectProperties();
        String mounts = readFile("/proc/mounts", 300_000);
        boolean suVisible = !shell(2, "/system/bin/sh", "-c", "command -v su 2>/dev/null || which su 2>/dev/null").trim().isEmpty();
        boolean managerVisible = false;
        for (String pkg : ROOT_MANAGERS) if (packageInfo(pm, pkg, 0) != null) managerVisible = true;
        String ml = mounts.toLowerCase(Locale.ROOT);
        boolean suspiciousMount = ml.contains("magisk") || ml.contains("kernelsu") || ml.contains("apatch")
                || ml.contains("/data/adb/modules") || ml.contains("overlayfs") || ml.contains(" overlay ");
        boolean writableSystem = writableSystemMount(ml);

        DiagnosticLogic.BootAssessment boot = DiagnosticLogic.assessBootloader(props);
        DiagnosticLogic.RootAssessment root = DiagnosticLogic.assessRoot(props, suVisible, managerVisible, suspiciousMount, writableSystem);
        boolean usageAccess = hasUsageAccess(app);

        JSONObject report = new JSONObject();
        report.put("schema", "fixhua-diagnostic-v3");
        report.put("collectorVersion", VERSION);
        report.put("generatedAt", isoNow());
        report.put("privacy", privacy());
        report.put("device", device());
        report.put("bootRoot", bootRoot(props, boot, root, suVisible, managerVisible, suspiciousMount, writableSystem));
        report.put("powerMemoryStorage", powerMemoryStorage(app));
        report.put("network", network(app));
        report.put("settings", settings(app));
        report.put("relevantPackages", relevantPackages(pm));
        report.put("installedPackages", installedPackages(pm));
        report.put("usageAccessGranted", usageAccess);
        report.put("usageEvents48h", usageEvents(app, usageAccess));
        report.put("capabilities", capabilities(usageAccess));

        String summary = summary(props, boot, root, usageAccess);
        File dir = new File(app.getCacheDir(), "diagnostics");
        if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Cannot create diagnostic directory");
        File out = new File(dir, "FixHUA-Diagnostic-" + fileStamp() + ".zip");
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(out))) {
            put(zip, "report.json", report.toString(2));
            put(zip, "summary.txt", summary);
            put(zip, "raw/selected_properties.txt", propsText(props));
            put(zip, "raw/proc_version.txt", readFile("/proc/version", 64_000));
            put(zip, "raw/proc_cpuinfo.txt", readFile("/proc/cpuinfo", 250_000));
            put(zip, "raw/proc_meminfo.txt", readFile("/proc/meminfo", 128_000));
            put(zip, "raw/proc_mounts.txt", mounts);
            put(zip, "raw/proc_partitions.txt", readFile("/proc/partitions", 128_000));
            put(zip, "raw/proc_cmdline.txt", readFile("/proc/cmdline", 64_000));
            put(zip, "raw/battery_uevent.txt", readFile("/sys/class/power_supply/battery/uevent", 64_000));
            put(zip, "raw/selinux_enforce.txt", readFile("/sys/fs/selinux/enforce", 4096));
            put(zip, "raw/uname.txt", shell(2, "/system/bin/uname", "-a"));
            put(zip, "raw/getenforce.txt", shell(2, "/system/bin/sh", "-c", "getenforce 2>&1"));
            put(zip, "raw/id.txt", shell(2, "/system/bin/id"));
            put(zip, "raw/block_by_name.txt", shell(2, "/system/bin/sh", "-c", "ls -la /dev/block/by-name 2>&1 || ls -la /dev/block/bootdevice/by-name 2>&1 || true"));
            put(zip, "README-FIRST.txt", readme(usageAccess));
        }
        return new Result(out, summary, usageAccess);
    }

    private static JSONObject privacy() throws Exception {
        JSONObject j = new JSONObject();
        j.put("accountsContactsMessagesPhotosLocation", false);
        j.put("imeiSerialAndroidId", false);
        j.put("packageNamesVersions", true);
        j.put("filteredRelevantUsageEventsWhenGranted", true);
        j.put("automaticUpload", false);
        return j;
    }

    private static JSONObject device() throws Exception {
        JSONObject j = new JSONObject();
        j.put("manufacturer", safe(Build.MANUFACTURER));
        j.put("brand", safe(Build.BRAND));
        j.put("model", safe(Build.MODEL));
        j.put("device", safe(Build.DEVICE));
        j.put("product", safe(Build.PRODUCT));
        j.put("board", safe(Build.BOARD));
        j.put("hardware", safe(Build.HARDWARE));
        j.put("bootloader", safe(Build.BOOTLOADER));
        j.put("fingerprint", safe(Build.FINGERPRINT));
        j.put("display", safe(Build.DISPLAY));
        j.put("buildId", safe(Build.ID));
        j.put("buildType", safe(Build.TYPE));
        j.put("buildTags", safe(Build.TAGS));
        j.put("androidRelease", safe(Build.VERSION.RELEASE));
        j.put("sdk", Build.VERSION.SDK_INT);
        j.put("securityPatch", safe(Build.VERSION.SECURITY_PATCH));
        j.put("incremental", safe(Build.VERSION.INCREMENTAL));
        j.put("baseOs", safe(Build.VERSION.BASE_OS));
        j.put("supportedAbis", new JSONArray(Arrays.asList(Build.SUPPORTED_ABIS)));
        j.put("supported64BitAbis", new JSONArray(Arrays.asList(Build.SUPPORTED_64_BIT_ABIS)));
        j.put("supported32BitAbis", new JSONArray(Arrays.asList(Build.SUPPORTED_32_BIT_ABIS)));
        j.put("kernelJavaProperty", safe(System.getProperty("os.version")));
        j.put("radioVersion", safe(Build.getRadioVersion()));
        if (Build.VERSION.SDK_INT >= 31) {
            j.put("socManufacturer", safe(Build.SOC_MANUFACTURER));
            j.put("socModel", safe(Build.SOC_MODEL));
            j.put("sku", safe(Build.SKU));
            j.put("odmSku", safe(Build.ODM_SKU));
        }
        j.put("elapsedRealtimeMs", SystemClock.elapsedRealtime());
        return j;
    }

    private static JSONObject bootRoot(Map<String,String> props,
                                       DiagnosticLogic.BootAssessment boot,
                                       DiagnosticLogic.RootAssessment root,
                                       boolean suVisible, boolean managerVisible,
                                       boolean suspiciousMount, boolean writableSystem) throws Exception {
        JSONObject j = new JSONObject();
        j.put("bootloaderAssessment", boot.state.name());
        j.put("bootloaderEvidence", new JSONArray(boot.evidence));
        j.put("rootAssessment", root.signal.name());
        j.put("rootEvidence", new JSONArray(root.evidence));
        j.put("suVisibleOnPath", suVisible);
        j.put("knownRootManagerVisible", managerVisible);
        j.put("suspiciousRootOrOverlayMount", suspiciousMount);
        j.put("writableSystemOrVendorSignal", writableSystem);
        JSONObject p = new JSONObject();
        for (Map.Entry<String,String> e : props.entrySet()) p.put(e.getKey(), e.getValue());
        j.put("selectedProperties", p);
        j.put("interpretationWarning", "NONE_OBVIOUS is not proof root is absent. UNKNOWN is preserved when boot state cannot be established by an ordinary app.");
        return j;
    }

    private static JSONObject powerMemoryStorage(Context app) throws Exception {
        JSONObject j = new JSONObject();
        PowerManager p = (PowerManager) app.getSystemService(Context.POWER_SERVICE);
        if (p != null) {
            j.put("powerSaveMode", p.isPowerSaveMode());
            j.put("deviceIdleMode", p.isDeviceIdleMode());
            j.put("interactive", p.isInteractive());
            j.put("selfBatteryOptimizationExempt", p.isIgnoringBatteryOptimizations(app.getPackageName()));
            if (Build.VERSION.SDK_INT >= 29) j.put("thermalStatus", p.getCurrentThermalStatus());
        }
        ActivityManager am = (ActivityManager) app.getSystemService(Context.ACTIVITY_SERVICE);
        if (am != null) {
            ActivityManager.MemoryInfo m = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(m);
            j.put("ramTotalBytes", m.totalMem);
            j.put("ramAvailableBytes", m.availMem);
            j.put("ramThresholdBytes", m.threshold);
            j.put("lowMemory", m.lowMemory);
            j.put("memoryClassMb", am.getMemoryClass());
            j.put("largeMemoryClassMb", am.getLargeMemoryClass());
            j.put("selfBackgroundRestricted", am.isBackgroundRestricted());
        }
        StatFs stat = new StatFs(Environment.getDataDirectory().getAbsolutePath());
        j.put("dataTotalBytes", stat.getTotalBytes());
        j.put("dataAvailableBytes", stat.getAvailableBytes());
        BatteryManager bm = (BatteryManager) app.getSystemService(Context.BATTERY_SERVICE);
        if (bm != null) {
            j.put("batteryCapacityPercent", bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY));
            j.put("batteryChargeCounterUah", bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER));
            j.put("batteryCurrentNowUa", bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW));
        }
        return j;
    }

    private static JSONObject network(Context app) throws Exception {
        JSONObject j = new JSONObject();
        ConnectivityManager cm = (ConnectivityManager) app.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return j;
        Network n = cm.getActiveNetwork();
        NetworkCapabilities c = n == null ? null : cm.getNetworkCapabilities(n);
        LinkProperties lp = n == null ? null : cm.getLinkProperties(n);
        j.put("activeNetworkPresent", n != null);
        j.put("metered", cm.isActiveNetworkMetered());
        if (c != null) {
            j.put("validated", c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED));
            j.put("internet", c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET));
            j.put("wifi", c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI));
            j.put("cellular", c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR));
            j.put("vpn", c.hasTransport(NetworkCapabilities.TRANSPORT_VPN));
        }
        if (lp != null) {
            JSONArray dns = new JSONArray();
            for (java.net.InetAddress a : lp.getDnsServers()) dns.put(a.getHostAddress());
            j.put("dnsServers", dns);
            j.put("privateDnsActive", lp.isPrivateDnsActive());
            j.put("privateDnsServerName", safe(lp.getPrivateDnsServerName()));
        }
        return j;
    }

    private static JSONObject settings(Context app) throws Exception {
        JSONObject j = new JSONObject();
        j.put("adbEnabled", globalInt(app, Settings.Global.ADB_ENABLED));
        j.put("developmentSettingsEnabled", globalInt(app, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED));
        j.put("alwaysFinishActivities", globalInt(app, Settings.Global.ALWAYS_FINISH_ACTIVITIES));
        j.put("airplaneMode", globalInt(app, Settings.Global.AIRPLANE_MODE_ON));
        j.put("autoTime", globalInt(app, Settings.Global.AUTO_TIME));
        j.put("autoTimeZone", globalInt(app, Settings.Global.AUTO_TIME_ZONE));
        j.put("stayOnWhilePluggedIn", globalInt(app, Settings.Global.STAY_ON_WHILE_PLUGGED_IN));
        j.put("privateDnsMode", globalString(app, "private_dns_mode"));
        j.put("privateDnsSpecifier", globalString(app, "private_dns_specifier"));
        return j;
    }

    private static JSONArray relevantPackages(PackageManager pm) throws Exception {
        JSONArray arr = new JSONArray();
        for (String pkg : RELEVANT) {
            PackageInfo info = packageInfo(pm, pkg, PackageManager.GET_PERMISSIONS | PackageManager.GET_SIGNING_CERTIFICATES);
            JSONObject j = new JSONObject();
            j.put("package", pkg);
            j.put("visibleInstalled", info != null);
            if (info != null) fillPackage(pm, info, j, true);
            arr.put(j);
        }
        try {
            PackageInfo web = WebView.getCurrentWebViewPackage();
            if (web != null) {
                JSONObject j = new JSONObject();
                j.put("role", "current_webview");
                fillPackage(pm, web, j, false);
                arr.put(j);
            }
        } catch (RuntimeException ignored) {}
        return arr;
    }

    private static JSONArray installedPackages(PackageManager pm) throws Exception {
        JSONArray arr = new JSONArray();
        List<PackageInfo> list;
        try {
            if (Build.VERSION.SDK_INT >= 33) list = pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES));
            else list = pm.getInstalledPackages(PackageManager.GET_SIGNING_CERTIFICATES);
        } catch (RuntimeException e) {
            arr.put(new JSONObject().put("error", e.getClass().getSimpleName() + ":" + safe(e.getMessage())));
            return arr;
        }
        Collections.sort(list, Comparator.comparing(a -> a.packageName));
        for (PackageInfo info : list) {
            JSONObject j = new JSONObject();
            fillPackage(pm, info, j, false);
            arr.put(j);
        }
        return arr;
    }

    private static void fillPackage(PackageManager pm, PackageInfo info, JSONObject j, boolean includePermissions) throws Exception {
        j.put("package", info.packageName);
        j.put("versionName", safe(info.versionName));
        j.put("versionCode", info.getLongVersionCode());
        j.put("firstInstallTime", info.firstInstallTime);
        j.put("lastUpdateTime", info.lastUpdateTime);
        j.put("installer", installer(pm, info.packageName));
        ApplicationInfo ai = info.applicationInfo;
        if (ai != null) {
            j.put("targetSdk", ai.targetSdkVersion);
            j.put("enabled", ai.enabled);
            j.put("systemApp", (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0);
            j.put("stoppedFlag", (ai.flags & ApplicationInfo.FLAG_STOPPED) != 0);
            j.put("debuggableFlag", (ai.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0);
            j.put("nativeLibraryDir", safe(ai.nativeLibraryDir));
            j.put("sourceDir", safe(ai.sourceDir));
        }
        JSONArray certs = certSha256(info);
        if (certs.length() > 0) j.put("signingCertSha256", certs);
        if (includePermissions && info.requestedPermissions != null) {
            JSONArray perms = new JSONArray();
            for (int i = 0; i < info.requestedPermissions.length; i++) {
                JSONObject p = new JSONObject().put("name", info.requestedPermissions[i]);
                boolean granted = info.requestedPermissionsFlags != null && i < info.requestedPermissionsFlags.length
                        && (info.requestedPermissionsFlags[i] & PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0;
                p.put("granted", granted);
                perms.put(p);
            }
            j.put("requestedPermissions", perms);
        }
    }

    private static JSONArray certSha256(PackageInfo info) throws Exception {
        JSONArray out = new JSONArray();
        if (info.signingInfo == null) return out;
        Signature[] sigs = info.signingInfo.hasMultipleSigners() ? info.signingInfo.getApkContentsSigners()
                : info.signingInfo.getSigningCertificateHistory();
        if (sigs != null) for (Signature s : sigs) out.put(sha256(s.toByteArray()));
        return out;
    }

    private static JSONArray usageEvents(Context app, boolean granted) throws Exception {
        JSONArray arr = new JSONArray();
        if (!granted) return arr;
        UsageStatsManager usm = (UsageStatsManager) app.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usm == null) return arr;
        long end = System.currentTimeMillis();
        long start = end - TimeUnit.HOURS.toMillis(48);
        Set<String> interesting = new HashSet<>(Arrays.asList(RELEVANT));
        try {
            UsageEvents stream = usm.queryEvents(start, end);
            UsageEvents.Event e = new UsageEvents.Event();
            while (stream != null && stream.hasNextEvent() && arr.length() < 1200) {
                stream.getNextEvent(e);
                String pkg = e.getPackageName();
                if (pkg == null || !interesting.contains(pkg)) continue;
                int type = e.getEventType();
                if (!lifecycle(type) && type != UsageEvents.Event.STANDBY_BUCKET_CHANGED) continue;
                JSONObject j = new JSONObject();
                j.put("package", pkg);
                j.put("timestamp", e.getTimeStamp());
                j.put("type", type);
                j.put("typeName", eventName(type));
                if (type == UsageEvents.Event.STANDBY_BUCKET_CHANGED) j.put("standbyBucket", e.getAppStandbyBucket());
                arr.put(j);
            }
        } catch (RuntimeException ex) {
            arr.put(new JSONObject().put("error", ex.getClass().getSimpleName() + ":" + safe(ex.getMessage())));
        }
        return arr;
    }

    private static boolean lifecycle(int type) {
        return type == UsageEvents.Event.MOVE_TO_FOREGROUND || type == UsageEvents.Event.MOVE_TO_BACKGROUND
                || type == UsageEvents.Event.ACTIVITY_RESUMED || type == UsageEvents.Event.ACTIVITY_PAUSED
                || type == UsageEvents.Event.ACTIVITY_STOPPED;
    }

    private static String eventName(int type) {
        if (type == UsageEvents.Event.MOVE_TO_FOREGROUND) return "MOVE_TO_FOREGROUND";
        if (type == UsageEvents.Event.MOVE_TO_BACKGROUND) return "MOVE_TO_BACKGROUND";
        if (type == UsageEvents.Event.ACTIVITY_RESUMED) return "ACTIVITY_RESUMED";
        if (type == UsageEvents.Event.ACTIVITY_PAUSED) return "ACTIVITY_PAUSED";
        if (type == UsageEvents.Event.ACTIVITY_STOPPED) return "ACTIVITY_STOPPED";
        if (type == UsageEvents.Event.STANDBY_BUCKET_CHANGED) return "STANDBY_BUCKET_CHANGED";
        return "EVENT_" + type;
    }

    private static JSONArray capabilities(boolean usageAccess) throws Exception {
        JSONArray a = new JSONArray();
        cap(a, "build_kernel_boot_properties", "AVAILABLE", "Collected where Android exposes them");
        cap(a, "installed_package_metadata", "AVAILABLE_OR_OEM_FILTERED", "Private diagnostic build requests package visibility");
        cap(a, "gbox_usage_timeline", usageAccess ? "AVAILABLE" : "USER_GRANT_REQUIRED", "Usage Access settings control this API");
        cap(a, "other_app_exit_reasons", "ADB_OR_PRIVILEGED_REQUIRED", "Android requires privileged DUMP access for another UID");
        cap(a, "full_system_logcat", "ADB_OR_PRIVILEGED_REQUIRED", "Unrestricted system logs are protected");
        cap(a, "other_app_tombstones_anr_traces", "ADB_OR_PRIVILEGED_REQUIRED", "Protected system data");
        cap(a, "gbox_internal_virtual_files", "NOT_VISIBLE_FROM_HOST_APP", "Virtual environment internals are not exposed to a normal host app");
        cap(a, "system_modification", "NOT_ATTEMPTED", "Probe is read-only and does not execute su");
        return a;
    }

    private static void cap(JSONArray a, String name, String status, String reason) throws Exception {
        a.put(new JSONObject().put("name", name).put("status", status).put("reason", reason));
    }

    private static LinkedHashMap<String,String> collectProperties() {
        LinkedHashMap<String,String> map = new LinkedHashMap<>();
        for (String k : PROPS) {
            String v = shell(2, "/system/bin/getprop", k).trim();
            map.put(k, v.isEmpty() ? "<unavailable-or-empty>" : v);
        }
        return map;
    }

    private static boolean hasUsageAccess(Context app) {
        try {
            AppOpsManager ops = (AppOpsManager) app.getSystemService(Context.APP_OPS_SERVICE);
            if (ops == null) return false;
            int mode = ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), app.getPackageName());
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (RuntimeException e) { return false; }
    }

    private static PackageInfo packageInfo(PackageManager pm, String pkg, long flags) {
        try {
            if (Build.VERSION.SDK_INT >= 33) return pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(flags));
            return pm.getPackageInfo(pkg, (int) flags);
        } catch (Exception e) { return null; }
    }

    private static String installer(PackageManager pm, String pkg) {
        try {
            InstallSourceInfo i = pm.getInstallSourceInfo(pkg);
            String s = i.getInstallingPackageName();
            return s == null ? "unknown" : s;
        } catch (Exception e) { return "unavailable:" + e.getClass().getSimpleName(); }
    }

    private static int globalInt(Context app, String key) {
        try { return Settings.Global.getInt(app.getContentResolver(), key); }
        catch (Exception e) { return -1; }
    }

    private static String globalString(Context app, String key) {
        try {
            String v = Settings.Global.getString(app.getContentResolver(), key);
            return v == null ? "<null>" : v;
        } catch (Exception e) { return "<unavailable:" + e.getClass().getSimpleName() + ">"; }
    }

    private static boolean writableSystemMount(String text) {
        for (String l : text.split("\\n")) {
            boolean target = l.contains(" /system ") || l.contains(" /vendor ") || l.contains(" /product ");
            boolean rw = l.contains("(rw,") || l.contains(" rw,") || l.endsWith(" rw");
            if (target && rw) return true;
        }
        return false;
    }

    private static String readFile(String path, int max) {
        File f = new File(path);
        if (!f.exists()) return "<not-present>\n";
        try (InputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int total = 0;
            while (total < max) {
                int n = in.read(buf, 0, Math.min(buf.length, max - total));
                if (n < 0) break;
                out.write(buf, 0, n);
                total += n;
            }
            String s = out.toString(StandardCharsets.UTF_8.name());
            if (f.length() > max) s += "\n<truncated>\n";
            return s;
        } catch (Exception e) { return "<unavailable:" + e.getClass().getSimpleName() + ":" + safe(e.getMessage()) + ">\n"; }
    }

    private static String shell(int timeout, String... cmd) {
        java.lang.Process p = null;
        try {
            p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            if (!p.waitFor(timeout, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return "<timeout>\n";
            }
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder b = new StringBuilder();
                String line;
                int chars = 0;
                while ((line = r.readLine()) != null && chars < 200_000) {
                    b.append(line).append('\n');
                    chars += line.length() + 1;
                }
                return b.toString();
            }
        } catch (Exception e) { return "<unavailable:" + e.getClass().getSimpleName() + ":" + safe(e.getMessage()) + ">\n"; }
        finally { if (p != null) p.destroy(); }
    }

    private static String propsText(Map<String,String> props) {
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String,String> e : props.entrySet()) b.append(e.getKey()).append('=').append(e.getValue()).append('\n');
        return b.toString();
    }

    private static String summary(Map<String,String> props, DiagnosticLogic.BootAssessment boot,
                                  DiagnosticLogic.RootAssessment root, boolean usageAccess) {
        StringBuilder s = new StringBuilder();
        s.append("FixHUA Diagnostic Probe v").append(VERSION).append('\n');
        s.append("Generated: ").append(isoNow()).append("\n\n");
        s.append("DEVICE\n  ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                .append(" / Android ").append(Build.VERSION.RELEASE).append(" API ").append(Build.VERSION.SDK_INT).append('\n');
        s.append("  Hardware: ").append(Build.HARDWARE).append(" / Board: ").append(Build.BOARD).append('\n');
        if (Build.VERSION.SDK_INT >= 31) s.append("  SoC: ").append(Build.SOC_MANUFACTURER).append(' ').append(Build.SOC_MODEL).append('\n');
        s.append("  Security patch: ").append(Build.VERSION.SECURITY_PATCH).append("\n\n");
        s.append("BOOT / ROOT DIAGNOSTICS\n  Bootloader: ").append(boot.state).append('\n');
        for (String e : boot.evidence) s.append("    - ").append(e).append('\n');
        s.append("  Root signal: ").append(root.signal).append('\n');
        for (String e : root.evidence) s.append("    - ").append(e).append('\n');
        s.append("  VerifiedBoot: ").append(props.get("ro.boot.verifiedbootstate")).append('\n');
        s.append("  FlashLocked: ").append(props.get("ro.boot.flash.locked")).append('\n');
        s.append("  Slot: ").append(props.get("ro.boot.slot_suffix")).append('\n');
        s.append("  Virtual A/B: ").append(props.get("ro.virtual_ab.enabled")).append('\n');
        s.append("  Treble: ").append(props.get("ro.treble.enabled")).append("\n\n");
        s.append("GBOX VISIBILITY\n  Usage Access: ").append(usageAccess ? "GRANTED" : "NOT GRANTED").append('\n');
        s.append("  Deep external-app crash/log/tombstone evidence is marked in report.json when privileged access is required.\n\n");
        s.append("Send the entire ZIP back for analysis.\n");
        return s.toString();
    }

    private static String readme(boolean usageAccess) {
        return "FixHUA Diagnostic Probe v" + VERSION + "\n\n"
                + "Local diagnostic bundle. No automatic upload.\nPrimary: report.json\nHuman summary: summary.txt\nRaw evidence: raw/*\n\n"
                + "Usage Access at capture: " + usageAccess + "\n"
                + "If false, enable Usage Access and capture again after reproducing the GBox problem.\n"
                + "Absence of a root signal is not proof that root is absent.\n";
    }

    private static void put(ZipOutputStream zip, String name, String text) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(text.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String sha256(byte[] bytes) throws Exception {
        MessageDigest d = MessageDigest.getInstance("SHA-256");
        byte[] h = d.digest(bytes);
        StringBuilder b = new StringBuilder();
        for (byte x : h) b.append(String.format(Locale.US, "%02x", x & 0xff));
        return b.toString();
    }

    private static String safe(String s) { return s == null ? "unknown" : s; }
    private static String isoNow() { return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(new Date()); }
    private static String fileStamp() { return new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()); }

    private DiagnosticCollector() {}
}
