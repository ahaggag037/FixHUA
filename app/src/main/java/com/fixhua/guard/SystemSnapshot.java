package com.fixhua.guard;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
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
import android.provider.Settings;
import android.webkit.WebView;

import java.util.Locale;

final class SystemSnapshot {
    static final String GBOX = "com.gbox.android";
    static final String MICROG = "com.google.android.gms";
    static final String PLAY_STORE = "com.android.vending";
    static final String HUAWEI_SYSTEM_MANAGER = "com.huawei.systemmanager";
    static final String HMS_CORE = "com.huawei.hwid";
    static final String APP_GALLERY = "com.huawei.appmarket";

    final boolean gboxInstalled;
    final boolean gboxEnabled;
    final boolean gboxLaunchable;
    final boolean gboxSuspended;
    final String gboxVersion;
    final String gboxInstaller;
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
    final boolean deviceInteractive;
    final int thermalStatus;
    final int batteryPercent;
    final float batteryTempC;
    final boolean batteryCharging;

    final long totalRamMb;
    final long availableRamMb;
    final boolean lowMemory;
    final boolean lowRamDevice;
    final long totalStorageMb;
    final long freeStorageMb;

    final boolean networkObserved;
    final boolean networkPresent;
    final boolean networkInternetCapable;
    final boolean networkValidated;
    final boolean networkWifi;
    final boolean networkVpn;
    final boolean networkMetered;
    final boolean networkCaptivePortal;

    final String webViewPackage;
    final String webViewVersion;

    final int alwaysFinishActivities;
    final int autoTime;
    final int autoTimeZone;

    private SystemSnapshot(Builder b) {
        gboxInstalled = b.gboxInstalled;
        gboxEnabled = b.gboxEnabled;
        gboxLaunchable = b.gboxLaunchable;
        gboxSuspended = b.gboxSuspended;
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
        deviceInteractive = b.deviceInteractive;
        thermalStatus = b.thermalStatus;
        batteryPercent = b.batteryPercent;
        batteryTempC = b.batteryTempC;
        batteryCharging = b.batteryCharging;
        totalRamMb = b.totalRamMb;
        availableRamMb = b.availableRamMb;
        lowMemory = b.lowMemory;
        lowRamDevice = b.lowRamDevice;
        totalStorageMb = b.totalStorageMb;
        freeStorageMb = b.freeStorageMb;
        networkObserved = b.networkObserved;
        networkPresent = b.networkPresent;
        networkInternetCapable = b.networkInternetCapable;
        networkValidated = b.networkValidated;
        networkWifi = b.networkWifi;
        networkVpn = b.networkVpn;
        networkMetered = b.networkMetered;
        networkCaptivePortal = b.networkCaptivePortal;
        webViewPackage = b.webViewPackage;
        webViewVersion = b.webViewVersion;
        alwaysFinishActivities = b.alwaysFinishActivities;
        autoTime = b.autoTime;
        autoTimeZone = b.autoTimeZone;
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

        PackageInfo gbox = packageInfo(pm, GBOX);
        b.gboxInstalled = gbox != null;
        b.gboxVersion = versionName(gbox);
        b.gboxInstaller = gbox == null ? "not-installed" : installer(pm, GBOX);
        if (gbox != null) {
            ApplicationInfo appInfo = gbox.applicationInfo;
            b.gboxEnabled = appInfo == null || appInfo.enabled;
            b.gboxLaunchable = safeLaunchIntent(pm, GBOX) != null;
            b.gboxSuspended = isPackageSuspended(pm, GBOX);
        } else {
            b.gboxEnabled = false;
            b.gboxLaunchable = false;
            b.gboxSuspended = false;
        }

        PackageInfo microg = packageInfo(pm, MICROG);
        b.microgInstalled = microg != null;
        b.microgVersion = versionName(microg);

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
            try {
                b.powerSaveMode = power.isPowerSaveMode();
                b.deviceIdleMode = power.isDeviceIdleMode();
                b.deviceInteractive = power.isInteractive();
                b.thermalStatus = power.getCurrentThermalStatus();
                b.gboxBatteryExempt = b.gboxInstalled && batteryExempt(power, GBOX);
                b.microgBatteryExempt = b.microgInstalled && batteryExempt(power, MICROG);
            } catch (RuntimeException ignored) {
                b.thermalStatus = -1;
            }
        }

        ActivityManager am = (ActivityManager) app.getSystemService(Context.ACTIVITY_SERVICE);
        if (am != null) {
            try {
                ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
                am.getMemoryInfo(info);
                b.totalRamMb = bytesToMb(info.totalMem);
                b.availableRamMb = bytesToMb(info.availMem);
                b.lowMemory = info.lowMemory;
                b.lowRamDevice = am.isLowRamDevice();
            } catch (RuntimeException ignored) {
                b.totalRamMb = 0L;
                b.availableRamMb = 0L;
            }
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
                b.networkObserved = true;
                b.networkPresent = network != null && caps != null;
                if (caps != null) {
                    b.networkInternetCapable = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
                    b.networkValidated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
                    b.networkWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
                    b.networkVpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
                    b.networkCaptivePortal = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL);
                }
                b.networkMetered = cm.isActiveNetworkMetered();
            } catch (RuntimeException ignored) {
                b.networkObserved = false;
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

        try {
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
                int status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN);
                b.batteryCharging = status == BatteryManager.BATTERY_STATUS_CHARGING
                        || status == BatteryManager.BATTERY_STATUS_FULL;
            }
        } catch (RuntimeException ignored) {
            // Keep unknown defaults.
        }

        return b.build();
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

    private static Intent safeLaunchIntent(PackageManager pm, String pkg) {
        try {
            return pm.getLaunchIntentForPackage(pkg);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static boolean isPackageSuspended(PackageManager pm, String pkg) {
        try {
            return pm.isPackageSuspended(pkg);
        } catch (PackageManager.NameNotFoundException | RuntimeException ignored) {
            return false;
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
        boolean gboxEnabled = true;
        boolean gboxLaunchable = true;
        boolean gboxSuspended = false;
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
        boolean deviceInteractive = true;
        int thermalStatus = PowerManager.THERMAL_STATUS_NONE;
        int batteryPercent = 50;
        float batteryTempC = 30f;
        boolean batteryCharging = false;
        long totalRamMb = 8192;
        long availableRamMb = 4096;
        boolean lowMemory = false;
        boolean lowRamDevice = false;
        long totalStorageMb = 128000;
        long freeStorageMb = 64000;
        boolean networkObserved = true;
        boolean networkPresent = true;
        boolean networkInternetCapable = true;
        boolean networkValidated = true;
        boolean networkWifi = true;
        boolean networkVpn = false;
        boolean networkMetered = false;
        boolean networkCaptivePortal = false;
        String webViewPackage = "com.huawei.webview";
        String webViewVersion = "test";
        int alwaysFinishActivities = 0;
        int autoTime = 1;
        int autoTimeZone = 1;

        Builder gboxInstalled(boolean value) { gboxInstalled = value; if (!value) { gboxEnabled = false; gboxLaunchable = false; } return this; }
        Builder gboxEnabled(boolean value) { gboxEnabled = value; return this; }
        Builder gboxLaunchable(boolean value) { gboxLaunchable = value; return this; }
        Builder gboxSuspended(boolean value) { gboxSuspended = value; return this; }
        Builder networkObserved(boolean value) { networkObserved = value; return this; }
        Builder networkPresent(boolean value) { networkPresent = value; return this; }
        Builder networkInternetCapable(boolean value) { networkInternetCapable = value; return this; }
        Builder networkValidated(boolean value) { networkValidated = value; return this; }
        Builder networkCaptivePortal(boolean value) { networkCaptivePortal = value; return this; }
        Builder powerSaveMode(boolean value) { powerSaveMode = value; return this; }
        Builder gboxBatteryExempt(boolean value) { gboxBatteryExempt = value; return this; }
        Builder lowMemory(boolean value) { lowMemory = value; return this; }
        Builder lowRamDevice(boolean value) { lowRamDevice = value; return this; }
        Builder ram(long total, long available) { totalRamMb = total; availableRamMb = available; return this; }
        Builder storage(long total, long free) { totalStorageMb = total; freeStorageMb = free; return this; }
        Builder thermalStatus(int value) { thermalStatus = value; return this; }
        Builder batteryTempC(float value) { batteryTempC = value; return this; }
        Builder deviceIdleMode(boolean value) { deviceIdleMode = value; return this; }
        Builder alwaysFinishActivities(int value) { alwaysFinishActivities = value; return this; }
        Builder autoTime(int value) { autoTime = value; return this; }
        Builder autoTimeZone(int value) { autoTimeZone = value; return this; }
        Builder webViewPackage(String value) { webViewPackage = value; return this; }
        Builder microgInstalled(boolean value) { microgInstalled = value; return this; }
        Builder identityConflict(boolean value) { identityConflict = value; return this; }
        Builder huaweiEnvironment(boolean value) { huaweiEnvironment = value; return this; }

        SystemSnapshot build() { return new SystemSnapshot(this); }
    }
}
