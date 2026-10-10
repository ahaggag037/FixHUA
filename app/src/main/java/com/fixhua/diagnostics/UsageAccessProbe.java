package com.fixhua.diagnostics;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.Process;

/** Vendor-tolerant Usage Access self-test and foreground package probe. */
final class UsageAccessProbe {
    private UsageAccessProbe() {}

    static boolean hasAccess(Context context) {
        try {
            AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
            int mode = appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.getPackageName()
            );
            if (mode == AppOpsManager.MODE_ALLOWED) return true;
        } catch (Throwable ignored) {
        }

        try {
            UsageStatsManager usm = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
            long end = System.currentTimeMillis();
            UsageEvents events = usm.queryEvents(end - 60_000L, end);
            UsageEvents.Event event = new UsageEvents.Event();
            while (events != null && events.hasNextEvent()) {
                events.getNextEvent(event);
                if (event.getPackageName() != null) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    static String currentForegroundPackage(Context context) {
        try {
            UsageStatsManager usm = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
            long end = System.currentTimeMillis();
            UsageEvents events = usm.queryEvents(end - 15_000L, end);
            UsageEvents.Event event = new UsageEvents.Event();
            String latest = null;
            long latestTime = 0L;
            while (events != null && events.hasNextEvent()) {
                events.getNextEvent(event);
                int type = event.getEventType();
                if ((type == UsageEvents.Event.ACTIVITY_RESUMED
                        || type == UsageEvents.Event.MOVE_TO_FOREGROUND)
                        && event.getTimeStamp() >= latestTime) {
                    latestTime = event.getTimeStamp();
                    latest = event.getPackageName();
                }
            }
            return latest;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
