package com.fixhua.diagnostics;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/** User-started device-wide automatic diagnostic session. */
public class DeepDiagnosticService extends Service {
    public static final String ACTION_STOP = "com.fixhua.diagnostics.DEEP_DIAG_STOP";
    public static final String ACTION_VIEW_REPORT = "com.fixhua.diagnostics.VIEW_DIAG_REPORT";
    public static final String PREFS = "fixhua_deep_diag";

    private static final String CHANNEL = "fixhua_deep_diag";
    private static final String RESULT_CHANNEL = "fixhua_diag_results";
    private static final int NOTIFICATION_ID = 7310;
    private static final int RESULT_NOTIFICATION_ID = 7311;
    private static final long NORMAL_TICK_MS = 1_000L;
    private static final long INCIDENT_TICK_MS = 500L;
    private static final long MAX_CAPTURE_MS = 30L * 60L * 1000L;
    private static final long WAKELOCK_TIMEOUT_MS = MAX_CAPTURE_MS + 120_000L;
    private static final String REPORT_FILE = "last_diagnostic_report.txt";

    public static volatile boolean active = false;

    private HandlerThread samplerThread;
    private Handler worker;
    private PowerManager.WakeLock wakeLock;
    private AutomaticIncidentDetector detector;
    private final AtomicBoolean finalizing = new AtomicBoolean(false);
    private long startedAtWall;
    private long nextDueElapsed;
    private long samples;
    private long sessionId;

    private final Runnable sampler = new Runnable() {
        @Override
        public void run() {
            if (finalizing.get()) return;
            sampleOnce();
            long interval = detector != null && detector.elevatedSampling()
                    ? INCIDENT_TICK_MS : NORMAL_TICK_MS;
            nextDueElapsed = SystemClock.elapsedRealtime() + interval;
            worker.postDelayed(this, interval);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        active = true;
        detector = new AutomaticIncidentDetector();
        createChannels();
        startForeground(NOTIFICATION_ID, runningNotification("Preparing device-wide capture…"));
        acquireBoundedWakeLock();

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        long oldStart = prefs.getLong("started_at", 0L);
        boolean interrupted = prefs.getBoolean("session_active", false)
                && oldStart > 0L
                && System.currentTimeMillis() - oldStart < MAX_CAPTURE_MS;

        if (interrupted) {
            startedAtWall = oldStart;
            sessionId = prefs.getLong("session_id", oldStart);
            samples = prefs.getLong("samples", 0L);
            DiagnosticTimeline.recordEvent(this, "diagnostic_service_recreated",
                    "continuing_session=" + sessionId + " previousSamples=" + samples);
        } else {
            DiagnosticTimeline.clear(this);
            startedAtWall = System.currentTimeMillis();
            sessionId = startedAtWall;
            samples = 0L;
            prefs.edit()
                    .putLong("started_at", startedAtWall)
                    .putLong("session_id", sessionId)
                    .putLong("samples", 0L)
                    .putLong("incident_count", 0L)
                    .remove("last_error")
                    .apply();
            DiagnosticTimeline.recordEvent(this, "diagnostic_session_started",
                    "session=" + sessionId + " normalHz=1 incidentHz=2 autoDetection=true");
        }

        prefs.edit().putBoolean("session_active", true).apply();

        samplerThread = new HandlerThread("FixHUA-DiagnosticSampler", android.os.Process.THREAD_PRIORITY_BACKGROUND);
        samplerThread.start();
        worker = new Handler(samplerThread.getLooper());
        nextDueElapsed = SystemClock.elapsedRealtime();
        worker.post(sampler);
        worker.postDelayed(() -> finalizeSession("max_duration"),
                Math.max(1_000L, MAX_CAPTURE_MS - Math.max(0L, System.currentTimeMillis() - startedAtWall)));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            finalizeSession("user_stop");
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    private void sampleOnce() {
        long nowElapsed = SystemClock.elapsedRealtime();
        long lateMs = Math.max(0L, nowElapsed - nextDueElapsed);
        int thermal = PowerManager.THERMAL_STATUS_NONE;
        boolean powerSave = false;
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            thermal = pm.getCurrentThermalStatus();
            powerSave = pm.isPowerSaveMode();
        } catch (Throwable ignored) {
        }

        AutomaticIncidentDetector.State beforeState = detector.state();
        long beforeId = detector.currentIncidentId();
        DiagnosticTimeline.SampleSnapshot snapshot = DiagnosticTimeline.recordSample(
                this,
                "auto_system_diag",
                false,
                false,
                false,
                "observe_only",
                thermal,
                powerSave,
                lateMs,
                beforeState.name(),
                beforeId,
                0,
                "pending"
        );

        AutomaticIncidentDetector.Result result = detector.update(new AutomaticIncidentDetector.Input(
                snapshot.elapsedMs,
                Math.max(snapshot.samplerLateMs, Math.max(0L, snapshot.sampleGapMs - NORMAL_TICK_MS)),
                snapshot.ramAvailableRatio,
                snapshot.ramLow,
                snapshot.memoryPsiAvailable,
                snapshot.memoryPsiSomeAvg10,
                snapshot.memoryPsiFullAvg10,
                snapshot.ioPsiAvailable,
                snapshot.ioPsiSomeAvg10,
                snapshot.cpuLoad,
                snapshot.cpuDropPct,
                snapshot.thermalStatus
        ));

        if (result.transition != AutomaticIncidentDetector.Transition.NONE) {
            DiagnosticTimeline.recordEvent(this,
                    "auto_incident_" + result.transition.name().toLowerCase(),
                    "id=" + result.incidentId
                            + " state=" + result.state
                            + " score=" + result.score
                            + " confidence=" + AutomaticIncidentDetector.confidenceForScore(result.score)
                            + " reasons=" + result.reasons);
        }

        samples++;
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putLong("samples", samples)
                .putString("detector_state", result.state.name())
                .putLong("active_incident_id", result.incidentId)
                .putLong("incident_count", detector.completedIncidents())
                .apply();

        if (samples % 15L == 0L || result.transition == AutomaticIncidentDetector.Transition.INCIDENT_STARTED) {
            updateRunningNotification(result);
        }
    }

    private void updateRunningNotification(AutomaticIncidentDetector.Result result) {
        String text = "Samples " + samples + " • " + result.state.name();
        if (result.incidentId > 0L) text += " #" + result.incidentId;
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIFICATION_ID, runningNotification(text));
    }

    private void finalizeSession(String reason) {
        if (!finalizing.compareAndSet(false, true)) return;
        if (worker == null) {
            stopSelf();
            return;
        }
        worker.removeCallbacks(sampler);
        worker.post(() -> {
            DiagnosticTimeline.recordEvent(this, "diagnostic_session_finalizing",
                    "reason=" + reason + " samples=" + samples
                            + " detectorState=" + detector.state()
                            + " completedIncidents=" + detector.completedIncidents());
            boolean success = false;
            String error = null;
            try {
                String report = DiagnosticBundle.build(getApplicationContext());
                saveLatestReport(report);
                success = true;
            } catch (Throwable t) {
                error = t.getClass().getSimpleName();
            }

            long duration = Math.max(0L, System.currentTimeMillis() - startedAtWall);
            SharedPreferences.Editor edit = getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putBoolean("session_active", false)
                    .putLong("last_duration_ms", duration)
                    .putLong("samples", samples)
                    .putLong("incident_count", detector.completedIncidents())
                    .putString("last_finish_reason", reason)
                    .putLong("last_finished_at", System.currentTimeMillis());
            if (error != null) edit.putString("last_error", error);
            else edit.remove("last_error");
            edit.apply();

            postCompletionNotification(success, error);
            releaseWakeLock();
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        });
    }

    private void saveLatestReport(String report) throws Exception {
        File target = new File(getFilesDir(), REPORT_FILE);
        File tmp = new File(getFilesDir(), REPORT_FILE + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp, false)) {
            out.write(report.getBytes(StandardCharsets.UTF_8));
            out.flush();
            out.getFD().sync();
        }
        if (target.exists() && !target.delete()) {
            throw new IllegalStateException("cannot_replace_old_report");
        }
        if (!tmp.renameTo(target)) {
            throw new IllegalStateException("cannot_commit_report");
        }
    }

    static String readLatestReport(Context context) {
        File f = new File(context.getFilesDir(), REPORT_FILE);
        if (!f.canRead()) return null;
        StringBuilder out = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) out.append(line).append('\n');
            return out.toString();
        } catch (Throwable ignored) {
            return null;
        }
    }

    static long sampleCount(Context context) {
        return context.getSharedPreferences(PREFS, MODE_PRIVATE).getLong("samples", 0L);
    }

    static long startedAt(Context context) {
        return context.getSharedPreferences(PREFS, MODE_PRIVATE).getLong("started_at", 0L);
    }

    static long incidentCount(Context context) {
        return context.getSharedPreferences(PREFS, MODE_PRIVATE).getLong("incident_count", 0L);
    }

    static String detectorState(Context context) {
        return context.getSharedPreferences(PREFS, MODE_PRIVATE).getString("detector_state", "NORMAL");
    }

    static boolean hasLatestReport(Context context) {
        return new File(context.getFilesDir(), REPORT_FILE).canRead();
    }

    private Notification runningNotification(String text) {
        Intent openIntent = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent open = PendingIntent.getActivity(this, 31, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent stopIntent = new Intent(this, DeepDiagnosticService.class).setAction(ACTION_STOP);
        PendingIntent stop = PendingIntent.getService(this, 32, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setContentTitle("FixHUA Auto Diagnostics")
                .setContentText(text)
                .setContentIntent(open)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .addAction(new Notification.Action.Builder(
                        android.R.drawable.ic_menu_close_clear_cancel, "Finish session", stop).build())
                .build();
    }

    private void postCompletionNotification(boolean success, String error) {
        Intent openIntent = new Intent(this, MainActivity.class)
                .setAction(ACTION_VIEW_REPORT)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent open = PendingIntent.getActivity(this, 33, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String title = success ? "اكتملت جلسة التشخيص" : "انتهت الجلسة — تعذر إنشاء التقرير";
        String text = success
                ? "التقرير جاهز • " + samples + " عينة • " + detector.completedIncidents() + " حوادث مكتملة"
                : "خطأ التقرير: " + (error == null ? "unknown" : error);
        Notification n = new Notification.Builder(this, RESULT_CHANNEL)
                .setSmallIcon(success ? android.R.drawable.stat_sys_download_done : android.R.drawable.stat_notify_error)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(open)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_STATUS)
                .build();
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(RESULT_NOTIFICATION_ID, n);
    }

    private void createChannels() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel running = new NotificationChannel(
                CHANNEL, "FixHUA Auto Diagnostics", NotificationManager.IMPORTANCE_LOW);
        running.setDescription("Visible while a user-started device-wide diagnostic session is active");
        nm.createNotificationChannel(running);

        NotificationChannel results = new NotificationChannel(
                RESULT_CHANNEL, "FixHUA Diagnostic Results", NotificationManager.IMPORTANCE_DEFAULT);
        results.setDescription("Alerts when a diagnostic session finishes and the report is ready");
        nm.createNotificationChannel(results);
    }

    private void acquireBoundedWakeLock() {
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FixHUA:DiagnosticCapture");
            wakeLock.setReferenceCounted(false);
            wakeLock.acquire(WAKELOCK_TIMEOUT_MS);
        } catch (Throwable ignored) {
        }
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            try { wakeLock.release(); } catch (Throwable ignored) {}
        }
        wakeLock = null;
    }

    @Override
    public void onDestroy() {
        active = false;
        releaseWakeLock();
        if (samplerThread != null) {
            samplerThread.quitSafely();
            samplerThread = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
