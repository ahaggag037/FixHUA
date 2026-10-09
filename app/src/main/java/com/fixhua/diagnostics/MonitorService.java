package com.fixhua.diagnostics;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import java.io.File;
import java.io.FileWriter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MonitorService extends Service {
    static final String ACTION_STOP = "com.fixhua.diagnostics.STOP_MONITOR";
    static final String FILE_NAME = "gbox-session-monitor.log";
    private static final String CHANNEL_ID = "fixhua_monitor";
    private static final int NOTIFICATION_ID = 4101;
    private ScheduledExecutorService executor;

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setContentTitle("FixHUA monitoring")
                .setContentText("Sampling system conditions while you test GBox")
                .setOngoing(true)
                .build();
        startForeground(NOTIFICATION_ID, notification);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }

        if (executor != null) return START_NOT_STICKY;

        File log = new File(getFilesDir(), FILE_NAME);
        try (FileWriter writer = new FileWriter(log, false)) {
            writer.write("FixHUA GBox session monitor v0.1\n");
            writer.write("Samples every 5 seconds for up to 5 minutes. No personal content is captured.\n");
        } catch (Exception ignored) {
        }

        executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(() -> appendSample(log), 0, 5, TimeUnit.SECONDS);
        executor.schedule((Runnable) this::stopSelf, 5, TimeUnit.MINUTES);
        return START_NOT_STICKY;
    }

    private void appendSample(File log) {
        try (FileWriter writer = new FileWriter(log, true)) {
            writer.write(DiagnosticCollector.sampleLine(this));
            writer.write('\n');
        } catch (Exception ignored) {
        }
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "FixHUA monitor",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Visible notification while diagnostic monitoring is active");
            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.createNotificationChannel(channel);
        }
    }

    @Override
    public void onDestroy() {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
