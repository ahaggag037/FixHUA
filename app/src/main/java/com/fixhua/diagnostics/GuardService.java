package com.fixhua.diagnostics;

import android.app.AppOpsManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

public class GuardService extends Service {
    public static final String ACTION_STOP = "com.fixhua.diagnostics.GUARD_STOP";
    public static final String ACTION_STARTED = "com.fixhua.diagnostics.GUARD_STARTED";
    public static final String ACTION_REOPEN_GBOX = "com.fixhua.diagnostics.REOPEN_GBOX";

    public static final String PREFS = "fixhua_guard";
    public static final String KEY_MODE = "guard_mode";
    public static final String MODE_BALANCED = "balanced";
    public static final String MODE_STABILITY = "stability";
    public static final String MODE_ECO = "eco";

    public static volatile boolean active = false;

    private static final String GBOX = "com.gbox.android";
    private static final String CHANNEL_ID = "fixhua_guard";
    private static final int NOTIFICATION_ID = 7301;
    private static final long MAX_SESSION_MS = 2L * 60L * 60L * 1000L;
    private static final long TICK_MS = 10_000L;
    private static final long RECENT_GBOX_WINDOW_MS = 35_000L;

    private PowerManager.WakeLock wakeLock;
    private WifiManager.WifiLock wifiLock;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private long sessionStartedAt;
    private long lastGboxSeenAt;
    private boolean gboxForeground;
    private boolean usageAccess;
    private int peakThermal;
    private int focusEntries;

    private final Runnable watchdog = new Runnable() {
        @Override
        public void run() {
            runAdaptiveTick();
            handler.postDelayed(this, TICK_MS);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        active = true;
        sessionStartedAt = System.currentTimeMillis();
        usageAccess = hasUsageAccess(this);
        incrementLong("sessions", 1L);
        createChannel();
        startForeground(NOTIFICATION_ID, buildNotification("Starting adaptive protection…"));
        handler.post(watchdog);
        handler.postDelayed(this::stopSelf, MAX_SESSION_MS);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        sendBroadcast(new Intent(ACTION_STARTED).setPackage(getPackageName()));
        runAdaptiveTick();
        return START_STICKY;
    }

    private void runAdaptiveTick() {
        String mode = getMode(this);
        usageAccess = hasUsageAccess(this);
        String foreground = usageAccess ? currentForegroundPackage() : null;
        boolean nowGboxForeground = GBOX.equals(foreground);
        long now = System.currentTimeMillis();

        if (nowGboxForeground) {
            lastGboxSeenAt = now;
            if (!gboxForeground) {
                focusEntries++;
                incrementLong("gbox_focus_entries", 1L);
            }
        }
        gboxForeground = nowGboxForeground;

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        int thermal = PowerManager.THERMAL_STATUS_NONE;
        try {
            thermal = pm.getCurrentThermalStatus();
            if (thermal > peakThermal) peakThermal = thermal;
        } catch (Throwable ignored) {
        }

        boolean validatedWifi = isValidatedWifi();
        boolean recentGbox = lastGboxSeenAt > 0 && now - lastGboxSeenAt <= RECENT_GBOX_WINDOW_MS;
        boolean cpuWanted;
        boolean wifiWanted;

        if (MODE_STABILITY.equals(mode)) {
            cpuWanted = true;
            wifiWanted = validatedWifi;
        } else if (MODE_ECO.equals(mode)) {
            cpuWanted = usageAccess && nowGboxForeground;
            wifiWanted = false;
        } else {
            // Balanced: keep protection only around real GBox activity when Usage Access is available.
            // Without Usage Access, preserve v1.0 behavior so protection still works out of the box.
            cpuWanted = usageAccess ? (nowGboxForeground || recentGbox) : true;
            wifiWanted = validatedWifi && (usageAccess ? (nowGboxForeground || recentGbox) : true);
        }

        // Wi-Fi high-performance mode is the first thing we drop under real thermal pressure.
        if (thermal >= PowerManager.THERMAL_STATUS_SEVERE) {
            wifiWanted = false;
        }

        setCpuLock(cpuWanted);
        setWifiLock(wifiWanted);
        updateNotification(mode, foreground, thermal, validatedWifi);
    }

    private void setCpuLock(boolean wanted) {
        try {
            if (wakeLock == null) {
                PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FixHUA:AdaptiveRuntimeGuard");
                wakeLock.setReferenceCounted(false);
            }
            if (wanted && !wakeLock.isHeld()) wakeLock.acquire();
            if (!wanted && wakeLock.isHeld()) wakeLock.release();
        } catch (Throwable ignored) {
        }
    }

    private void setWifiLock(boolean wanted) {
        try {
            if (wifiLock == null) {
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                if (wm != null) {
                    wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "FixHUA:AdaptiveHighPerfWifi");
                    wifiLock.setReferenceCounted(false);
                }
            }
            if (wifiLock == null) return;
            if (wanted && !wifiLock.isHeld()) wifiLock.acquire();
            if (!wanted && wifiLock.isHeld()) wifiLock.release();
        } catch (Throwable ignored) {
        }
    }

    private boolean isValidatedWifi() {
        try {
            ConnectivityManager cm = getSystemService(ConnectivityManager.class);
            Network network = cm.getActiveNetwork();
            if (network == null) return false;
            NetworkCapabilities caps = cm.getNetworkCapabilities(network);
            return caps != null
                    && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                    && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private String currentForegroundPackage() {
        try {
            UsageStatsManager usm = (UsageStatsManager) getSystemService(USAGE_STATS_SERVICE);
            long end = System.currentTimeMillis();
            UsageEvents events = usm.queryEvents(end - 20_000L, end);
            UsageEvents.Event event = new UsageEvents.Event();
            String latest = null;
            long latestTime = 0L;
            while (events.hasNextEvent()) {
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

    static boolean hasUsageAccess(Context context) {
        try {
            AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
            int mode = appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    context.getPackageName()
            );
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Throwable ignored) {
            return false;
        }
    }

    static String getMode(Context context) {
        return context.getSharedPreferences(PREFS, MODE_PRIVATE)
                .getString(KEY_MODE, MODE_BALANCED);
    }

    static void setMode(Context context, String mode) {
        context.getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putString(KEY_MODE, mode)
                .apply();
    }

    static long stat(Context context, String key) {
        return context.getSharedPreferences(PREFS, MODE_PRIVATE).getLong(key, 0L);
    }

    private void incrementLong(String key, long amount) {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        p.edit().putLong(key, p.getLong(key, 0L) + amount).apply();
    }

    private void updateNotification(String mode, String foreground, int thermal, boolean validatedWifi) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null) return;

        String modeText = MODE_STABILITY.equals(mode) ? "Stability"
                : MODE_ECO.equals(mode) ? "Eco" : "Balanced";
        String state;
        if (!usageAccess) {
            state = modeText + " • basic protection • enable Usage Access for adaptive mode";
        } else if (GBOX.equals(foreground)) {
            state = modeText + " • GBox active • adaptive protection ON";
        } else {
            state = modeText + " • waiting for GBox";
        }
        if (thermal >= PowerManager.THERMAL_STATUS_SEVERE) {
            state += " • thermal safeguard";
        } else if (validatedWifi && wifiLock != null && wifiLock.isHeld()) {
            state += " • Wi-Fi boost";
        }
        nm.notify(NOTIFICATION_ID, buildNotification(state));
    }

    private Notification buildNotification(String text) {
        Intent openIntent = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent open = PendingIntent.getActivity(
                this,
                10,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent reopenIntent = new Intent(this, MainActivity.class)
                .setAction(ACTION_REOPEN_GBOX)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent reopen = PendingIntent.getActivity(
                this,
                12,
                reopenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent stopIntent = new Intent(this, GuardService.class).setAction(ACTION_STOP);
        PendingIntent stop = PendingIntent.getService(
                this,
                11,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        return builder
                .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                .setContentTitle("FixHUA Guard v1.1")
                .setContentText(text)
                .setContentIntent(open)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .addAction(new Notification.Action.Builder(
                        android.R.drawable.ic_media_play,
                        "Reopen GBox",
                        reopen
                ).build())
                .addAction(new Notification.Action.Builder(
                        android.R.drawable.ic_menu_close_clear_cancel,
                        "Stop protection",
                        stop
                ).build())
                .build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "FixHUA adaptive runtime protection",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Visible while FixHUA protects a GBox session");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    @Override
    public void onDestroy() {
        active = false;
        handler.removeCallbacksAndMessages(null);

        long duration = Math.max(0L, System.currentTimeMillis() - sessionStartedAt);
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        p.edit()
                .putLong("last_session_ms", duration)
                .putLong("total_guard_ms", p.getLong("total_guard_ms", 0L) + duration)
                .putLong("last_peak_thermal", peakThermal)
                .putLong("last_focus_entries", focusEntries)
                .apply();

        try {
            if (wifiLock != null && wifiLock.isHeld()) wifiLock.release();
        } catch (Throwable ignored) {
        }
        try {
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        } catch (Throwable ignored) {
        }
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
