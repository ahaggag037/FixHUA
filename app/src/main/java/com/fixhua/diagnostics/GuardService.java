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

/**
 * Active guard for user-started GBox sessions.
 *
 * Non-root mode uses bounded host-side CPU/Wi-Fi locks. If an already-installed
 * root manager exposes su, RootSessionController may temporarily place GBox on
 * the AOSP Doze whitelist and active standby bucket, then restores the previous
 * state when the session ends. This app never attempts to root the phone.
 */
public class GuardService extends Service {
    public static final String ACTION_STOP = "com.fixhua.diagnostics.GUARD_STOP";
    public static final String ACTION_REOPEN_GBOX = "com.fixhua.diagnostics.REOPEN_GBOX";

    public static final String PREFS = "fixhua_guard";
    public static final String KEY_MODE = "guard_mode";
    public static final String MODE_BALANCED = "balanced";
    public static final String MODE_STABILITY = "stability";
    public static final String MODE_ECO = "eco";

    public static volatile boolean active = false;

    private static final String GBOX = "com.gbox.android";
    private static final String CHANNEL_ID = "fixhua_active_guard";
    private static final int NOTIFICATION_ID = 7302;
    private static final long MAX_SESSION_MS = 90L * 60L * 1000L;
    private static final long TICK_MS = 5_000L;
    private static final long RECENT_GBOX_WINDOW_MS = 45_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private PowerManager.WakeLock cpuWakeLock;
    private WifiManager.WifiLock wifiLock;
    private SessionPolicy.WifiMode heldWifiMode = SessionPolicy.WifiMode.NONE;
    private RootSessionController rootController;

    private long sessionStartedAt;
    private long lastGboxSeenAt;
    private boolean lastForeground;
    private int peakThermal;

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
        incrementLong("sessions", 1L);
        rootController = new RootSessionController(this);
        createChannel();
        startForeground(NOTIFICATION_ID, buildNotification("Starting Root Guard…"));
        handler.post(watchdog);
        handler.postDelayed(this::stopSelf, MAX_SESSION_MS);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        runAdaptiveTick();
        return START_STICKY;
    }

    private void runAdaptiveTick() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        int thermal = PowerManager.THERMAL_STATUS_NONE;
        boolean interactive = true;
        boolean powerSave = false;
        try {
            thermal = pm.getCurrentThermalStatus();
            interactive = pm.isInteractive();
            powerSave = pm.isPowerSaveMode();
        } catch (Throwable ignored) {
        }
        peakThermal = Math.max(peakThermal, thermal);

        boolean usageAccess = hasUsageAccess(this);
        String foreground = usageAccess ? currentForegroundPackage() : null;
        boolean gboxForeground = GBOX.equals(foreground);
        long now = System.currentTimeMillis();

        if (gboxForeground) {
            lastGboxSeenAt = now;
            if (!lastForeground) incrementLong("gbox_focus_entries", 1L);
        }
        lastForeground = gboxForeground;

        boolean recentGbox = lastGboxSeenAt > 0 && now - lastGboxSeenAt <= RECENT_GBOX_WINDOW_MS;
        boolean validatedWifi = isValidatedWifi();
        String mode = getMode(this);

        SessionPolicy.Decision decision = SessionPolicy.decide(
                mode,
                usageAccess,
                gboxForeground,
                recentGbox,
                interactive,
                validatedWifi,
                powerSave,
                thermal
        );

        setCpuLock(decision.cpuWakeLock);
        setWifiMode(decision.wifiMode);
        if (rootController != null) {
            rootController.tick(gboxForeground || recentGbox || !usageAccess);
        }

        String state = stateLabel(mode, usageAccess, gboxForeground, thermal, decision);
        saveRuntimeState(state, thermal, decision);
        updateNotification(state);
    }

    private String stateLabel(
            String mode,
            boolean usageAccess,
            boolean gboxForeground,
            int thermal,
            SessionPolicy.Decision decision
    ) {
        String modeLabel = MODE_STABILITY.equals(mode) ? "Stability"
                : MODE_ECO.equals(mode) ? "Eco" : "Balanced";
        StringBuilder b = new StringBuilder(modeLabel).append(" • ");
        if (thermal >= PowerManager.THERMAL_STATUS_SEVERE) {
            b.append("thermal safeguard");
        } else if (!usageAccess) {
            b.append("explicit-session protection");
        } else if (gboxForeground) {
            b.append("GBox active");
        } else {
            b.append("waiting / grace window");
        }
        if (decision.wifiMode == SessionPolicy.WifiMode.LOW_LATENCY) b.append(" • Wi-Fi low latency");
        if (decision.wifiMode == SessionPolicy.WifiMode.HIGH_PERF) b.append(" • Wi-Fi high perf");
        if (decision.cpuWakeLock) b.append(" • CPU awake");
        String root = RootSessionController.lastStatus(this);
        if (root.contains("نشط")) b.append(" • Root Guard");
        return b.toString();
    }

    private synchronized void setCpuLock(boolean wanted) {
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (cpuWakeLock == null) {
                cpuWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FixHUA:RootGuardCpu");
                cpuWakeLock.setReferenceCounted(false);
            }
            if (wanted && !cpuWakeLock.isHeld()) cpuWakeLock.acquire(MAX_SESSION_MS);
            if (!wanted && cpuWakeLock.isHeld()) cpuWakeLock.release();
        } catch (Throwable ignored) {
        }
    }

    private synchronized void setWifiMode(SessionPolicy.WifiMode wanted) {
        try {
            if (wanted == heldWifiMode && wifiLock != null && wifiLock.isHeld()) return;
            releaseWifiLock();
            heldWifiMode = SessionPolicy.WifiMode.NONE;
            if (wanted == SessionPolicy.WifiMode.NONE) return;

            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm == null || !wm.isWifiEnabled()) return;

            int lockMode = WifiManager.WIFI_MODE_FULL_HIGH_PERF;
            if (wanted == SessionPolicy.WifiMode.LOW_LATENCY && Build.VERSION.SDK_INT >= 29) {
                lockMode = WifiManager.WIFI_MODE_FULL_LOW_LATENCY;
            }
            wifiLock = wm.createWifiLock(lockMode, "FixHUA:RootGuardWifi");
            wifiLock.setReferenceCounted(false);
            wifiLock.acquire();
            heldWifiMode = wanted;
        } catch (Throwable ignored) {
            releaseWifiLock();
            heldWifiMode = SessionPolicy.WifiMode.NONE;
        }
    }

    private void releaseWifiLock() {
        if (wifiLock != null && wifiLock.isHeld()) {
            try {
                wifiLock.release();
            } catch (Throwable ignored) {
            }
        }
        wifiLock = null;
    }

    private boolean isValidatedWifi() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
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
            UsageEvents events = usm.queryEvents(end - 30_000L, end);
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

    static String lastState(Context context) {
        return context.getSharedPreferences(PREFS, MODE_PRIVATE)
                .getString("last_runtime_state", "not started yet");
    }

    private void saveRuntimeState(String state, int thermal, SessionPolicy.Decision decision) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString("last_runtime_state", state)
                .putLong("last_thermal", thermal)
                .putLong("cpu_lock", decision.cpuWakeLock ? 1L : 0L)
                .putLong("wifi_mode", decision.wifiMode.ordinal())
                .apply();
    }

    private void incrementLong(String key, long amount) {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        p.edit().putLong(key, p.getLong(key, 0L) + amount).apply();
    }

    private Notification buildNotification(String text) {
        Intent openIntent = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent open = PendingIntent.getActivity(
                this, 10, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent reopenIntent = new Intent(this, MainActivity.class)
                .setAction(ACTION_REOPEN_GBOX)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent reopen = PendingIntent.getActivity(
                this, 11, reopenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent stopIntent = new Intent(this, GuardService.class).setAction(ACTION_STOP);
        PendingIntent stop = PendingIntent.getService(
                this, 12, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                .setContentTitle("FixHUA Root Guard v3")
                .setContentText(text)
                .setContentIntent(open)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .addAction(new Notification.Action.Builder(
                        android.R.drawable.ic_media_play, "Open GBox", reopen).build())
                .addAction(new Notification.Action.Builder(
                        android.R.drawable.ic_menu_close_clear_cancel, "Stop", stop).build())
                .build();
    }

    private void updateNotification(String text) {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(NOTIFICATION_ID, buildNotification(text));
        } catch (Throwable ignored) {
        }
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "FixHUA Root Guard",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Visible while the user-started GBox protection session is running");
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    @Override
    public void onDestroy() {
        active = false;
        handler.removeCallbacksAndMessages(null);
        if (rootController != null) {
            rootController.close();
            rootController = null;
        }
        releaseWifiLock();
        if (cpuWakeLock != null && cpuWakeLock.isHeld()) {
            try {
                cpuWakeLock.release();
            } catch (Throwable ignored) {
            }
        }
        cpuWakeLock = null;

        long duration = Math.max(0L, System.currentTimeMillis() - sessionStartedAt);
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        p.edit()
                .putLong("last_session_ms", duration)
                .putLong("total_guard_ms", p.getLong("total_guard_ms", 0L) + duration)
                .putLong("last_peak_thermal", peakThermal)
                .apply();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
