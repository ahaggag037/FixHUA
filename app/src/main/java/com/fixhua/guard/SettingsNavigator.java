package com.fixhua.guard;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;

final class SettingsNavigator {

    static boolean launchGBox(Context context) {
        Intent launch = context.getPackageManager().getLaunchIntentForPackage(SystemSnapshot.GBOX);
        if (launch == null) return false;
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        try {
            context.startActivity(launch);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    static void openAppDetails(Context context, String packageName) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + packageName));
        startSafely(context, intent, new Intent(Settings.ACTION_SETTINGS));
    }

    static void openAppGallery(Context context) {
        PackageManager pm = context.getPackageManager();
        Intent launch = pm.getLaunchIntentForPackage(SystemSnapshot.APP_GALLERY);
        if (launch != null) {
            if (!(context instanceof Activity)) launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                context.startActivity(launch);
                return;
            } catch (RuntimeException ignored) {
                // Fall through to general settings if AppGallery cannot launch.
            }
        }
        startSafely(context, new Intent(Settings.ACTION_SETTINGS), new Intent(Settings.ACTION_SETTINGS));
    }

    static void openHuaweiAppLaunch(Context context) {
        String[][] targets = new String[][]{
                {"com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"},
                {"com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"},
                {"com.huawei.systemmanager", "com.huawei.systemmanager.power.ui.HwPowerManagerActivity"}
        };

        PackageManager pm = context.getPackageManager();
        for (String[] target : targets) {
            Intent intent = new Intent().setComponent(new ComponentName(target[0], target[1]));
            if (!(context instanceof Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                if (intent.resolveActivity(pm) != null) {
                    context.startActivity(intent);
                    return;
                }
            } catch (ActivityNotFoundException | SecurityException ignored) {
                // Try the next Huawei System Manager entry point.
            }
        }

        Intent systemManager = pm.getLaunchIntentForPackage(SystemSnapshot.HUAWEI_SYSTEM_MANAGER);
        if (systemManager != null) {
            if (!(context instanceof Activity)) systemManager.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                context.startActivity(systemManager);
                return;
            } catch (RuntimeException ignored) {
                // Fall through to standard Android battery settings.
            }
        }
        openBatterySettings(context);
    }

    static void openBatteryOptimizationSettings(Context context) {
        startSafely(
                context,
                new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
        );
    }

    static void openBatterySettings(Context context) {
        startSafely(
                context,
                new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS),
                new Intent(Settings.ACTION_SETTINGS)
        );
    }

    static void openDeveloperOptions(Context context) {
        startSafely(
                context,
                new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
                new Intent(Settings.ACTION_SETTINGS)
        );
    }

    static void openStorageSettings(Context context) {
        startSafely(
                context,
                new Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
                new Intent(Settings.ACTION_SETTINGS)
        );
    }

    static void openWirelessSettings(Context context) {
        startSafely(
                context,
                new Intent(Settings.ACTION_WIRELESS_SETTINGS),
                new Intent(Settings.ACTION_SETTINGS)
        );
    }

    static void openDateSettings(Context context) {
        startSafely(
                context,
                new Intent(Settings.ACTION_DATE_SETTINGS),
                new Intent(Settings.ACTION_SETTINGS)
        );
    }

    static void fixFinding(Context context, ReadinessEngine.Finding finding) {
        if (finding == null) return;
        switch (finding.action) {
            case OPEN_APP_GALLERY:
                openAppGallery(context);
                break;
            case OPEN_GBOX_DETAILS:
                openAppDetails(context, SystemSnapshot.GBOX);
                break;
            case OPEN_HUAWEI_APP_LAUNCH:
                openHuaweiAppLaunch(context);
                break;
            case OPEN_BATTERY_OPTIMIZATION:
                openBatteryOptimizationSettings(context);
                break;
            case OPEN_BATTERY_SETTINGS:
                openBatterySettings(context);
                break;
            case OPEN_DEVELOPER_OPTIONS:
                openDeveloperOptions(context);
                break;
            case OPEN_STORAGE_SETTINGS:
                openStorageSettings(context);
                break;
            case OPEN_WIRELESS_SETTINGS:
                openWirelessSettings(context);
                break;
            case OPEN_DATE_SETTINGS:
                openDateSettings(context);
                break;
            case NONE:
            default:
                break;
        }
    }

    private static void startSafely(Context context, Intent primary, Intent fallback) {
        if (!(context instanceof Activity)) primary.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (!(context instanceof Activity)) fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(primary);
        } catch (RuntimeException first) {
            try {
                context.startActivity(fallback);
            } catch (RuntimeException ignored) {
                // No compatible settings activity exists on this ROM.
            }
        }
    }

    private SettingsNavigator() {}
}
