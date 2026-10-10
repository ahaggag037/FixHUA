package com.fixhua.diagnostics;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

/**
 * User-started read-only deep diagnostic capture.
 * Samples at 1 Hz for short sessions so brief frequency / memory / thermal
 * transitions are less likely to be missed. No tuning commands are issued.
 */
public class DeepDiagnosticService extends Service {
    public static final String ACTION_STOP = "com.fixhua.diagnostics.DEEP_DIAG_STOP";
    public static final String PREFS = "fixhua_deep_diag";
    private static final String CHANNEL = "fixhua_deep_diag";
    private static final int NOTIFICATION_ID = 7310;
    private static final long TICK_MS = 1_000L;
    private static final long MAX_CAPTURE_MS = 20L * 60L * 1000L;

    public static volatile boolean active = false;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private long startedAt;

    private final Runnable sampler = new Runnable() {
        @Override
        public void run() {
            sample();
            handler.postDelayed(this, TICK_MS);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        active = true;
        startedAt = System.currentTimeMillis();
        createChannel();
        startForeground(NOTIFICATION_ID, notification("Deep diagnostic capture is running"));
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putLong("started_at", startedAt)
                .putLong("samples", 0L)
                .apply();
        DiagnosticTimeline.recordEvent(this, "deep_test_started", "1Hz read-only capture");
        handler.post(sampler);
        handler.postDelayed(this::stopSelf, MAX_CAPTURE_MS);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    private void sample() {
        int thermal = PowerManager.THERMAL_STATUS_NONE;
        boolean powerSave = false;
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            thermal = pm.getCurrentThermalStatus();
            powerSave = pm.isPowerSaveMode();
        } catch (Throwable ignored) {
        }

        DiagnosticTimeline.recordSample(
                this,
                "deep_test",
                false,
                false,
                false,
                "observe_only",
                thermal,
                powerSave
        );
        long samples = getSharedPreferences(PREFS, MODE_PRIVATE).getLong("samples", 0L) + 1L;
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putLong("samples", samples).apply();
        if (samples % 10L == 0L) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(NOTIFICATION_ID, notification("Captured " + samples + " samples"));
        }
    }

    static long sampleCount(android.content.Context context) {
        return context.getSharedPreferences(PREFS, MODE_PRIVATE).getLong("samples", 0L);
    }

    static long startedAt(android.content.Context context) {
        return context.getSharedPreferences(PREFS, MODE_PRIVATE).getLong("started_at", 0L);
    }

    private Notification notification(String text) {
        Intent openIntent = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent open = PendingIntent.getActivity(
                this, 31, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        Intent stopIntent = new Intent(this, DeepDiagnosticService.class).setAction(ACTION_STOP);
        PendingIntent stop = PendingIntent.getService(
                this, 32, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setContentTitle("FixHUA Deep Test")
                .setContentText(text)
                .setContentIntent(open)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(new Notification.Action.Builder(
                        android.R.drawable.ic_menu_close_clear_cancel, "Stop", stop).build())
                .build();
    }

    private void createChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL,
                "FixHUA Deep Diagnostics",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("Visible while a user-started read-only diagnostic capture is active");
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.createNotificationChannel(channel);
    }

    @Override
    public void onDestroy() {
        active = false;
        handler.removeCallbacksAndMessages(null);
        long duration = Math.max(0L, System.currentTimeMillis() - startedAt);
        DiagnosticTimeline.recordEvent(this, "deep_test_stopped", "durationMs=" + duration + " samples=" + sampleCount(this));
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putLong("last_duration_ms", duration).apply();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
