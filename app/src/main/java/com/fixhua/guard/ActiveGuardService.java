package com.fixhua.guard;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
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
 * Explicit, reversible, non-root stability guard for a user-started GBox session.
 *
 * The guard uses only host-side Android APIs: a bounded PARTIAL_WAKE_LOCK and,
 * when safe, a Wi-Fi performance/low-latency lock. It never roots the device,
 * never writes kernel/sysfs state, and automatically backs off on severe heat.
 */
public final class ActiveGuardService extends Service {
    public static final String ACTION_STOP = "com.fixhua.guard.ACTIVE_GUARD_STOP";

    static final String PREFS = "fixhua_active_guard";
    static final String KEY_MODE = "guard_mode";
    static final String MODE_BALANCED = "balanced";
    static final String MODE_STABILITY = "stability";
    static final String MODE_ECO = "eco";

    private static final String GBOX = "com.gbox.android";
    private static final String CHANNEL_ID = "fixhua_active_guard";
    private static final int NOTIFICATION_ID = 7404;
    private static final long MAX_SESSION_MS = 90L * 60L * 1000L;
    private static final long TICK_MS = 5_000L;
    private static final long RECENT_GBOX_WINDOW_MS = 45_000L;

    public static volatile boolean active;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private PowerManager.WakeLock cpuWakeLock;
    private WifiManager.WifiLock wifiLock;
    private SessionPolicy.WifiMode heldWifiMode = SessionPolicy.WifiMode.NONE;

    private long sessionStartedAt;
    private long lastGboxSeenAt;
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
        prefs().edit().putBoolean("active", true).apply();
        incrementLong("sessions", 1L);
        createChannel();
        startForeground(NOTIFICATION_ID, buildNotification("بدء الحماية النشطة…"));
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
        return START_NOT_STICKY;
    }

    private void runAdaptiveTick() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        int thermal = PowerManager.THERMAL_STATUS_NONE;
        boolean interactive = true;
        boolean powerSave = false;
        try {
            if (pm != null) {
                thermal = pm.getCurrentThermalStatus();
                interactive = pm.isInteractive();
                powerSave = pm.isPowerSaveMode();
            }
        } catch (Throwable ignored) {
        }
        peakThermal = Math.max(peakThermal, thermal);

        boolean usageAccess = UsageAccessProbe.hasAccess(this);
        String foreground = usageAccess ? UsageAccessProbe.currentForegroundPackage(this) : null;
        boolean gboxForeground = GBOX.equals(foreground);
        long now = System.currentTimeMillis();
        if (gboxForeground) lastGboxSeenAt = now;

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
        String modeLabel = MODE_STABILITY.equals(mode) ? "ثبات"
                : MODE_ECO.equals(mode) ? "اقتصادي" : "متوازن";
        StringBuilder b = new StringBuilder(modeLabel).append(" • ");
        if (thermal >= PowerManager.THERMAL_STATUS_SEVERE) {
            b.append("حماية حرارية");
        } else if (!usageAccess) {
            b.append("حماية الجلسة الصريحة");
        } else if (gboxForeground) {
            b.append("GBox نشط");
        } else {
            b.append("انتظار GBox");
        }
        if (decision.wifiMode == SessionPolicy.WifiMode.LOW_LATENCY) b.append(" • Wi-Fi latency منخفض");
        if (decision.wifiMode == SessionPolicy.WifiMode.HIGH_PERF) b.append(" • Wi-Fi أداء عالٍ");
        if (decision.cpuWakeLock) b.append(" • CPU مستيقظ");
        return b.toString();
    }

    private synchronized void setCpuLock(boolean wanted) {
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm == null) return;
            if (cpuWakeLock == null) {
                cpuWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FixHUA:ActiveGuardCpu");
                cpuWakeLock.setReferenceCounted(false);
            }
            if (wanted && !cpuWakeLock.isHeld()) cpuWakeLock.acquire(MAX_SESSION_MS);
            if (!wanted && cpuWakeLock.isHeld()) cpuWakeLock.release();
        } catch (Throwable ignored) {
        }
    }

    @SuppressWarnings("deprecation")
    private synchronized void setWifiMode(SessionPolicy.WifiMode wanted) {
        try {
            if (wanted == SessionPolicy.WifiMode.NONE) {
                releaseWifiLock();
                heldWifiMode = SessionPolicy.WifiMode.NONE;
                return;
            }
            if (wanted == heldWifiMode && wifiLock != null && wifiLock.isHeld()) return;
            releaseWifiLock();
            heldWifiMode = SessionPolicy.WifiMode.NONE;

            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm == null || !wm.isWifiEnabled()) return;

            int lockMode = WifiManager.WIFI_MODE_FULL_HIGH_PERF;
            if (wanted == SessionPolicy.WifiMode.LOW_LATENCY && Build.VERSION.SDK_INT >= 29) {
                lockMode = WifiManager.WIFI_MODE_FULL_LOW_LATENCY;
            }
            wifiLock = wm.createWifiLock(lockMode, "FixHUA:ActiveGuardWifi");
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
            if (cm == null) return false;
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

    static String getMode(Context context) {
        return context.getSharedPreferences(PREFS, MODE_PRIVATE)
                .getString(KEY_MODE, MODE_BALANCED);
    }

    static void setMode(Context context, String mode) {
        if (!MODE_BALANCED.equals(mode) && !MODE_STABILITY.equals(mode) && !MODE_ECO.equals(mode)) {
            mode = MODE_BALANCED;
        }
        context.getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putString(KEY_MODE, mode)
                .apply();
    }

    static String lastState(Context context) {
        return context.getSharedPreferences(PREFS, MODE_PRIVATE)
                .getString("last_runtime_state", "لم تبدأ جلسة بعد");
    }

    static boolean isMarkedActive(Context context) {
        return active || context.getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean("active", false);
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private void saveRuntimeState(String state, int thermal, SessionPolicy.Decision decision) {
        prefs().edit()
                .putString("last_runtime_state", state)
                .putLong("last_thermal", thermal)
                .putLong("cpu_lock", decision.cpuWakeLock ? 1L : 0L)
                .putLong("wifi_mode", decision.wifiMode.ordinal())
                .apply();
    }

    private void incrementLong(String key, long amount) {
        SharedPreferences p = prefs();
        p.edit().putLong(key, p.getLong(key, 0L) + amount).apply();
    }

    private Notification buildNotification(String text) {
        Intent openIntent = new Intent(this, OptimizerActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent open = PendingIntent.getActivity(
                this, 21, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent stopIntent = new Intent(this, ActiveGuardService.class).setAction(ACTION_STOP);
        PendingIntent stop = PendingIntent.getService(
                this, 22, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                .setContentTitle("FixHUA Active Guard")
                .setContentText(text)
                .setContentIntent(open)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .addAction(new Notification.Action.Builder(
                        android.R.drawable.ic_menu_close_clear_cancel, "إيقاف", stop).build())
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
                    "FixHUA Active Guard",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("حماية مؤقتة لجلسة GBox يبدأها المستخدم ويستطيع إيقافها في أي وقت");
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    @Override
    public void onDestroy() {
        active = false;
        handler.removeCallbacksAndMessages(null);
        releaseWifiLock();
        if (cpuWakeLock != null && cpuWakeLock.isHeld()) {
            try {
                cpuWakeLock.release();
            } catch (Throwable ignored) {
            }
        }
        cpuWakeLock = null;

        long duration = Math.max(0L, System.currentTimeMillis() - sessionStartedAt);
        SharedPreferences p = prefs();
        p.edit()
                .putBoolean("active", false)
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
