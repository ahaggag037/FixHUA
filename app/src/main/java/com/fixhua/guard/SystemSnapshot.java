package com.fixhua.guard;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.InstallSourceInfo;
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
import android.provider.Settings;
import android.webkit.WebView;

import java.util.Locale;

final class SystemSnapshot {
    static final String GBOX = "com.gbox.android";
    // com.google.android.gms is the Google Play services package name. A compatible
    // microG implementation can use the same package id, so package presence alone
    // must never be labelled as cryptographic proof of the implementation vendor.
    static final String GOOGLE_PLAY_SERVICES = "com.google.android.gms";
    static final String PLAY_STORE = "com.android.vending";
    static final String HUAWEI_SYSTEM_MANAGER = "com.huawei.systemmanager";
    static final String HMS_CORE = "com.huawei.hwid";
    static final String APP_GALLERY = "com.huawei.appmarket";

    enum EnvironmentProfile {
        GBOX_HOST,
        HUAWEI_NATIVE_GMS,
        HUAWEI_HMS_ONLY,
        GENERIC_NATIVE_GMS,
        GENERIC_ANDROID
    }

    final boolean gboxInstalled;
    final String gboxVersion;
    final String gboxInstaller;
    // Kept as field names for source compatibility with the existing controller code.
    // They now mean the com.google.android.gms package, not a claim that microG is used.
    final boolean microgInstalled;
    final String microgVersion;
    final boolean playStoreInstalled;
    final String playStoreVersion;
    final boolean hmsInstalled;
    final boolean appGalleryInstalled;
    final boolean huaweiSystemManagerInstalled;
    final boolean huaweiEnvironment;
    final boolean identityConflict;

    final String manufacturer;
    final String brand;
    final String model;
    final String buildDisplay;
    final String androidRelease;
    final int sdk;

    final boolean gboxBatteryExempt;
    final boolean microgBatteryExempt;
    final boolean powerSaveMode;
    final boolean deviceIdleMode;
    final boolean thermalStatusSupported;
    final int thermalStatus;
    final int batteryPercent;
    final float batteryTempC;
    final int batteryStatus;
    final int batteryPlugged;
    final int batteryVoltageMv;

    final long totalRamMb;
    final long availableRamMb;
    final boolean lowMemory;
    final boolean lowRamDevice;
    final int memoryClassMb;
    final int largeMemoryClassMb;
    final long totalStorageMb;
    final long freeStorageMb;
    final int cpuCoreCount;
    final long uptimeMinutes;

    final boolean networkPresent;
    final boolean networkInternetCapable;
    final boolean networkValidated;
    final boolean networkWifi;
    final boolean networkVpn;
    final boolean networkMetered;

    final String webViewPackage;
    final String webViewVersion;

    final int alwaysFinishActivities;
    final int autoTime;
    final int autoTimeZone;
    final float windowAnimationScale;
    final float transitionAnimationScale;
    final float animatorDurationScale;

    final boolean dumpPermissionGranted;
    final boolean privilegedProcessProbeSuccess;
    final int currentMaxCachedProcesses;
    final int referenceMaxCachedProcesses;
    final int overrideMaxCachedProcesses;
    final String privilegedProcessProbeFailure;

    private SystemSnapshot(Builder b) {
        gboxInstalled = b.gboxInstalled;
        gboxVersion = b.gboxVersion;
        gboxInstaller = b.gboxInstaller;
        microgInstalled = b.microgInstalled;
        microgVersion = b.microgVersion;
        playStoreInstalled = b.playStoreInstalled;
        playStoreVersion = b.playStoreVersion;
        hmsInstalled = b.hmsInstalled;
        appGalleryInstalled = b.appGalleryInstalled;
        huaweiSystemManagerInstalled = b.huaweiSystemManagerInstalled;
        huaweiEnvironment = b.huaweiEnvironment;
        identityConflict = b.identityConflict;
        manufacturer = b.manufacturer;
        brand = b.brand;
        model = b.model;
        buildDisplay = b.buildDisplay;
        androidRelease = b.androidRelease;
        sdk = b.sdk;
        gboxBatteryExempt = b.gboxBatteryExempt;
        microgBatteryExempt = b.microgBatteryExempt;
        powerSaveMode = b.powerSaveMode;
        deviceIdleMode = b.deviceIdleMode;
        thermalStatusSupported = b.thermalStatusSupported;
        thermalStatus = b.thermalStatus;
        batteryPercent = b.batteryPercent;
        batteryTempC = b.batteryTempC;
        batteryStatus = b.batteryStatus;
        batteryPlugged = b.batteryPlugged;
        batteryVoltageMv = b.batteryVoltageMv;
        totalRamMb = b.totalRamMb;
        availableRamMb = b.availableRamMb;
        lowMemory = b.lowMemory;
        lowRamDevice = b.lowRamDevice;
        memoryClassMb = b.memoryClassMb;
        largeMemoryClassMb = b.largeMemoryClassMb;
        totalStorageMb = b.totalStorageMb;
        freeStorageMb = b.freeStorageMb;
        cpuCoreCount = b.cpuCoreCount;
        uptimeMinutes = b.uptimeMinutes;
        networkPresent = b.networkPresent;
        networkInternetCapable = b.networkInternetCapable;
        networkValidated = b.networkValidated;
        networkWifi = b.networkWifi;
        networkVpn = b.networkVpn;
        networkMetered = b.networkMetered;
        webViewPackage = b.webViewPackage;
        webViewVersion = b.webViewVersion;
        alwaysFinishActivities = b.alwaysFinishActivities;
        autoTime = b.autoTime;
        autoTimeZone = b.autoTimeZone;
        windowAnimationScale = b.windowAnimationScale;
        transitionAnimationScale = b.transitionAnimationScale;
        animatorDurationScale = b.animatorDurationScale;
        dumpPermissionGranted = b.dumpPermissionGranted;
        privilegedProcessProbeSuccess = b.privilegedProcessProbeSuccess;
        currentMaxCachedProcesses = b.currentMaxCachedProcesses;
        referenceMaxCachedProcesses = b.referenceMaxCachedProcesses;
        overrideMaxCachedProcesses = b.overrideMaxCachedProcesses;
        privilegedProcessProbeFailure = b.privilegedProcessProbeFailure;
    }

    static SystemSnapshot capture(Context context) {
        Context app = context.getApplicationContext();
        PackageManager pm = app.getPackageManager();
        Builder b = new Builder();

        b.manufacturer = safe(Build.MANUFACTURER);
        b.brand = safe(Build.BRAND);
        b.model = safe(Build.MODEL);
        b.buildDisplay = safe(Build.DISPLAY);
        b.androidRelease = safe(Build.VERSION.RELEASE);
        b.sdk = Build.VERSION.SDK_INT;
        b.cpuCoreCount = Math.max(1, Runtime.getRuntime().availableProcessors());
        b.uptimeMinutes = Math.max(0L, SystemClock.elapsedRealtime() / 60_000L);

        PackageInfo gbox = packageInfo(pm, GBOX);
        b.gboxInstalled = gbox != null;
        b.gboxVersion = versionName(gbox);
        b.gboxInstaller = gbox == null ? "not-installed" : installer(pm, GBOX);

        PackageInfo googlePlayServices = packageInfo(pm, GOOGLE_PLAY_SERVICES);
        b.microgInstalled = googlePlayServices != null;
        b.microgVersion = versionName(googlePlayServices);

        PackageInfo play = packageInfo(pm, PLAY_STORE);
        b.playStoreInstalled = play != null;
        b.playStoreVersion = versionName(play);

        b.huaweiSystemManagerInstalled = packageInfo(pm, HUAWEI_SYSTEM_MANAGER) != null;
        b.hmsInstalled = packageInfo(pm, HMS_CORE) != null;
        b.appGalleryInstalled = packageInfo(pm, APP_GALLERY) != null;

        boolean buildLooksHuawei = looksHuawei(b.manufacturer) || looksHuawei(b.brand);
        b.huaweiEnvironment = buildLooksHuawei
                || b.huaweiSystemManagerInstalled
                || b.hmsInstalled
                || b.appGalleryInstalled;
        b.identityConflict = b.huaweiEnvironment && !buildLooksHuawei;

        PowerManager power = (PowerManager) app.getSystemService(Context.POWER_SERVICE);
        if (power != null) {
            b.powerSaveMode = power.isPowerSaveMode();
            b.deviceIdleMode = power.isDeviceIdleMode();
            b.thermalStatusSupported = Build.VERSION.SDK_INT >= 29;
            if (b.thermalStatusSupported) {
                try {
                    b.thermalStatus = power.getCurrentThermalStatus();
                } catch (RuntimeException ignored) {
                    b.thermalStatusSupported = false;
                    b.thermalStatus = PowerManager.THERMAL_STATUS_NONE;
                }
            }
            b.gboxBatteryExempt = b.gboxInstalled && batteryExempt(power, GBOX);
            b.microgBatteryExempt = b.microgInstalled && batteryExempt(power, GOOGLE_PLAY_SERVICES);
        }

        ActivityManager am = (ActivityManager) app.getSystemService(Context.ACTIVITY_SERVICE);
        if (am != null) {
            ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(info);
            b.totalRamMb = bytesToMb(info.totalMem);
            b.availableRamMb = bytesToMb(info.availMem);
            b.lowMemory = info.lowMemory;
            b.lowRamDevice = am.isLowRamDevice();
            b.memoryClassMb = am.getMemoryClass();
            b.largeMemoryClassMb = am.getLargeMemoryClass();
        }

        try {
            StatFs stat = new StatFs(Environment.getDataDirectory().getAbsolutePath());
            b.totalStorageMb = bytesToMb(stat.getTotalBytes());
            b.freeStorageMb = bytesToMb(stat.getAvailableBytes());
        } catch (RuntimeException ignored) {
            b.totalStorageMb = 0L;
            b.freeStorageMb = 0L;
        }

        ConnectivityManager cm = (ConnectivityManager) app.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            try {
                Network network = cm.getActiveNetwork();
                NetworkCapabilities caps = network == null ? null : cm.getNetworkCapabilities(network);
                b.networkPresent = network != null && caps != null;
                if (caps != null) {
                    b.networkInternetCapable = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
                    b.networkValidated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
                    b.networkWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
                    b.networkVpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
                }
            } catch (RuntimeException ignored) {
                b.networkPresent = false;
                b.networkInternetCapable = false;
                b.networkValidated = false;
                b.networkWifi = false;
                b.networkVpn = false;
            }
            try {
                b.networkMetered = cm.isActiveNetworkMetered();
            } catch (RuntimeException ignored) {
                b.networkMetered = false;
            }
        }

        try {
            PackageInfo webView = WebView.getCurrentWebViewPackage();
            if (webView != null) {
                b.webViewPackage = safe(webView.packageName);
                b.webViewVersion = versionName(webView);
            }
        } catch (RuntimeException ignored) {
            b.webViewPackage = "unavailable";
            b.webViewVersion = "unknown";
        }

        b.alwaysFinishActivities = readGlobalInt(app, Settings.Global.ALWAYS_FINISH_ACTIVITIES);
        b.autoTime = readGlobalInt(app, Settings.Global.AUTO_TIME);
        b.autoTimeZone = readGlobalInt(app, Settings.Global.AUTO_TIME_ZONE);
        b.windowAnimationScale = readGlobalFloat(app, Settings.Global.WINDOW_ANIMATION_SCALE);
        b.transitionAnimationScale = readGlobalFloat(app, Settings.Global.TRANSITION_ANIMATION_SCALE);
        b.animatorDurationScale = readGlobalFloat(app, Settings.Global.ANIMATOR_DURATION_SCALE);

        PrivilegedHostProbe.Result privilegedProbe = PrivilegedHostProbe.capture(app);
        b.dumpPermissionGranted = privilegedProbe.dumpPermissionGranted();
        b.privilegedProcessProbeSuccess = privilegedProbe.success();
        b.privilegedProcessProbeFailure = privilegedProbe.failureReason();
        b.currentMaxCachedProcesses = privilegedProbe.processBudget().currentMaxCachedProcesses();
        b.referenceMaxCachedProcesses = privilegedProbe.processBudget().referenceMaxCachedProcesses();
        b.overrideMaxCachedProcesses = privilegedProbe.processBudget().overrideMaxCachedProcesses();

        Intent battery = app.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (battery != null) {
            int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            if (level >= 0 && scale > 0) {
                b.batteryPercent = Math.round((level * 100f) / scale);
            }
            int tenths = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
            if (tenths != Integer.MIN_VALUE) {
                b.batteryTempC = tenths / 10f;
            }
            b.batteryStatus = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            b.batteryPlugged = battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
            b.batteryVoltageMv = battery.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
        }

        return b.build();
    }

    EnvironmentProfile environmentProfile() {
        if (gboxInstalled) return EnvironmentProfile.GBOX_HOST;
        if (microgInstalled && playStoreInstalled) {
            return huaweiEnvironment
                    ? EnvironmentProfile.HUAWEI_NATIVE_GMS
                    : EnvironmentProfile.GENERIC_NATIVE_GMS;
        }
        if (huaweiEnvironment) return EnvironmentProfile.HUAWEI_HMS_ONLY;
        return EnvironmentProfile.GENERIC_ANDROID;
    }

    boolean nativeGoogleStackDetected() {
        EnvironmentProfile profile = environmentProfile();
        return profile == EnvironmentProfile.HUAWEI_NATIVE_GMS
                || profile == EnvironmentProfile.GENERIC_NATIVE_GMS;
    }

    boolean batteryCharging() {
        return batteryStatus == BatteryManager.BATTERY_STATUS_CHARGING
                || batteryStatus == BatteryManager.BATTERY_STATUS_FULL;
    }

    double freeStorageFraction() {
        return totalStorageMb > 0 ? (double) freeStorageMb / (double) totalStorageMb : 1.0;
    }

    double availableRamFraction() {
        return totalRamMb > 0 ? (double) availableRamMb / (double) totalRamMb : 1.0;
    }

    private static boolean batteryExempt(PowerManager pm, String pkg) {
        try {
            return pm.isIgnoringBatteryOptimizations(pkg);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static int readGlobalInt(Context context, String key) {
        try {
            return Settings.Global.getInt(context.getContentResolver(), key);
        } catch (Settings.SettingNotFoundException | SecurityException ignored) {
            return -1;
        }
    }

    private static float readGlobalFloat(Context context, String key) {
        try {
            return Settings.Global.getFloat(context.getContentResolver(), key);
        } catch (Settings.SettingNotFoundException | SecurityException ignored) {
            return -1f;
        }
    }

    private static PackageInfo packageInfo(PackageManager pm, String pkg) {
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                return pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0));
            }
            return pm.getPackageInfo(pkg, 0);
        } catch (PackageManager.NameNotFoundException | RuntimeException ignored) {
            return null;
        }
    }

    private static String installer(PackageManager pm, String pkg) {
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                InstallSourceInfo info = pm.getInstallSourceInfo(pkg);
                String name = info.getInstallingPackageName();
                return name == null ? "unknown" : name;
            }
            String name = pm.getInstallerPackageName(pkg);
            return name == null ? "unknown" : name;
        } catch (PackageManager.NameNotFoundException | RuntimeException ignored) {
            return "unknown";
        }
    }

    private static String versionName(PackageInfo info) {
        if (info == null || info.versionName == null || info.versionName.trim().isEmpty()) return "unknown";
        return info.versionName;
    }

    private static boolean looksHuawei(String value) {
        String lower = safe(value).toLowerCase(Locale.ROOT);
        return lower.contains("huawei") || lower.contains("honor") || lower.contains("hihonor");
    }

    private static String safe(String value) {
        return value == null ? "unknown" : value;
    }

    private static long bytesToMb(long value) {
        return value <= 0 ? 0L : value / (1024L * 1024L);
    }

    static final class Builder {
        boolean gboxInstalled = true;
        String gboxVersion = "test";
        String gboxInstaller = "unknown";
        boolean microgInstalled = true;
        String microgVersion = "test";
        boolean playStoreInstalled = true;
        String playStoreVersion = "test";
        boolean hmsInstalled = true;
        boolean appGalleryInstalled = true;
        boolean huaweiSystemManagerInstalled = true;
        boolean huaweiEnvironment = true;
        boolean identityConflict = false;
        String manufacturer = "HUAWEI";
        String brand = "HUAWEI";
        String model = "test";
        String buildDisplay = "test";
        String androidRelease = "12";
        int sdk = 31;
        boolean gboxBatteryExempt = true;
        boolean microgBatteryExempt = true;
        boolean powerSaveMode = false;
        boolean deviceIdleMode = false;
        boolean thermalStatusSupported = true;
        int thermalStatus = PowerManager.THERMAL_STATUS_NONE;
        int batteryPercent = 50;
        float batteryTempC = 30f;
        int batteryStatus = BatteryManager.BATTERY_STATUS_DISCHARGING;
        int batteryPlugged = 0;
        int batteryVoltageMv = 4000;
        long totalRamMb = 8192;
        long availableRamMb = 4096;
        boolean lowMemory = false;
        boolean lowRamDevice = false;
        int memoryClassMb = 256;
        int largeMemoryClassMb = 512;
        long totalStorageMb = 128000;
        long freeStorageMb = 64000;
        int cpuCoreCount = 8;
        long uptimeMinutes = 120;
        boolean networkPresent = true;
        boolean networkInternetCapable = true;
        boolean networkValidated = true;
        boolean networkWifi = true;
        boolean networkVpn = false;
        boolean networkMetered = false;
        String webViewPackage = "com.android.chrome";
        String webViewVersion = "test";
        int alwaysFinishActivities = 0;
        int autoTime = 1;
        int autoTimeZone = 1;
        float windowAnimationScale = 1f;
        float transitionAnimationScale = 1f;
        float animatorDurationScale = 1f;
        boolean dumpPermissionGranted = false;
        boolean privilegedProcessProbeSuccess = false;
        int currentMaxCachedProcesses = -1;
        int referenceMaxCachedProcesses = -1;
        int overrideMaxCachedProcesses = -1;
        String privilegedProcessProbeFailure = "DUMP_NOT_GRANTED";

        Builder gboxInstalled(boolean value) { gboxInstalled = value; return this; }
        Builder playStoreInstalled(boolean value) { playStoreInstalled = value; return this; }
        Builder networkValidated(boolean value) { networkValidated = value; return this; }
        Builder network(boolean present, boolean internetCapable, boolean validated, boolean wifi) {
            networkPresent = present;
            networkInternetCapable = internetCapable;
            networkValidated = validated;
            networkWifi = wifi;
            return this;
        }
        Builder powerSaveMode(boolean value) { powerSaveMode = value; return this; }
        Builder gboxBatteryExempt(boolean value) { gboxBatteryExempt = value; return this; }
        Builder lowMemory(boolean value) { lowMemory = value; return this; }
        Builder ram(long total, long available) { totalRamMb = total; availableRamMb = available; return this; }
        Builder storage(long total, long free) { totalStorageMb = total; freeStorageMb = free; return this; }
        Builder thermalStatus(int value) { thermalStatus = value; return this; }
        Builder thermalStatusSupported(boolean value) { thermalStatusSupported = value; return this; }
        Builder batteryTempC(float value) { batteryTempC = value; return this; }
        Builder alwaysFinishActivities(int value) { alwaysFinishActivities = value; return this; }
        Builder autoTime(int value) { autoTime = value; return this; }
        Builder autoTimeZone(int value) { autoTimeZone = value; return this; }
        Builder webViewPackage(String value) { webViewPackage = value; return this; }
        Builder microgInstalled(boolean value) { microgInstalled = value; return this; }
        Builder identityConflict(boolean value) { identityConflict = value; return this; }
        Builder huaweiEnvironment(boolean value) { huaweiEnvironment = value; return this; }
        Builder processBudget(boolean granted, boolean success, int current, int reference, int override, String failure) {
            dumpPermissionGranted = granted;
            privilegedProcessProbeSuccess = success;
            currentMaxCachedProcesses = current;
            referenceMaxCachedProcesses = reference;
            overrideMaxCachedProcesses = override;
            privilegedProcessProbeFailure = failure;
            return this;
        }

        SystemSnapshot build() { return new SystemSnapshot(this); }
    }
}
