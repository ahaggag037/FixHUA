package com.fixhua.diagnostics;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * User-started host-side stability guard for GBox sessions.
 *
 * The service does not modify GBox or Google components. It keeps the host CPU awake and,
 * while Wi-Fi is in use, requests Android's high-performance Wi-Fi mode. A foreground
 * notification keeps the session explicit and gives the user a one-tap stop action.
 */
public class TurboService extends Service {
    public static final String ACTION_START = "com.fixhua.diagnostics.action.START_TURBO";
    public static final String ACTION_STOP = "com.fixhua.diagnostics.action.STOP_TURBO";

    private static final String CHANNEL_ID = "fixhua_turbo";
    private static final int NOTIFICATION_ID = 5101;

    private static volatile boolean running;

    private PowerManager.WakeLock cpuWakeLock;
    private WifiManager.WifiLock wifiLock;
    private ScheduledExecutorService watchdog;
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;

    private volatile boolean networkValidated;
    private volatile String networkTransport = "checking";

    public static boolean isRunning() {
        return running;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        acquirePerformanceLocks();
        registerNetworkCallback();
        startForeground(NOTIFICATION_ID, buildNotification());
        running = true;

        watchdog = Executors.newSingleThreadScheduledExecutor();
        watchdog.scheduleWithFixedDelay(() -> {
            try {
                acquirePerformanceLocks();
                updateNotification();
            } catch (Throwable ignored) {
                // A failed refresh must not terminate the stability session.
            }
        }, 20, 20, TimeUnit.SECONDS);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }

        acquirePerformanceLocks();
        updateNotification();
        running = true;
        return START_STICKY;
    }

    private synchronized void acquirePerformanceLocks() {
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (cpuWakeLock == null) {
            cpuWakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "FixHUA:TurboCpu"
            );
            cpuWakeLock.setReferenceCounted(false);
        }
        if (!cpuWakeLock.isHeld()) {
            cpuWakeLock.acquire();
        }

        WifiManager wifiManager = (WifiManager) getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null) {
            if (wifiLock == null) {
                wifiLock = wifiManager.createWifiLock(
                        WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                        "FixHUA:TurboWifi"
                );
                wifiLock.setReferenceCounted(false);
            }
            if (!wifiLock.isHeld()) {
                wifiLock.acquire();
            }
        }
    }

    private void registerNetworkCallback() {
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) return;

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                refreshNetworkState(network);
            }

            @Override
            public void onCapabilitiesChanged(Network network, NetworkCapabilities caps) {
                updateNetworkState(caps);
            }

            @Override
            public void onLost(Network network) {
                networkValidated = false;
                networkTransport = "reconnecting";
                updateNotification();
            }
        };

        try {
            connectivityManager.registerDefaultNetworkCallback(networkCallback);
            Network current = connectivityManager.getActiveNetwork();
            if (current != null) refreshNetworkState(current);
        } catch (Throwable ignored) {
            networkCallback = null;
        }
    }

    private void refreshNetworkState(Network network) {
        if (connectivityManager == null || network == null) return;
        NetworkCapabilities caps = connectivityManager.getNetworkCapabilities(network);
        updateNetworkState(caps);
    }

    private void updateNetworkState(NetworkCapabilities caps) {
        if (caps == null) {
            networkValidated = false;
            networkTransport = "none";
        } else {
            networkValidated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                networkTransport = "Wi-Fi";
            } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                networkTransport = "mobile";
            } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                networkTransport = "ethernet";
            } else {
                networkTransport = "network";
            }
        }
        updateNotification();
    }

    private Notification buildNotification() {
        Intent openApp = new Intent(this, MainActivity.class);
        PendingIntent openPending = PendingIntent.getActivity(
                this,
                1,
                openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent stop = new Intent(this, TurboService.class);
        stop.setAction(ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(
                this,
                2,
                stop,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String networkText = networkValidated
                ? networkTransport + " ready"
                : networkTransport + " / waiting for validated internet";

        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("FixHUA Turbo is active")
                .setContentText("CPU + Wi-Fi stability guard • " + networkText)
                .setContentIntent(openPending)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(new Notification.Action.Builder(
                        null,
                        "Stop Turbo",
                        stopPending
                ).build())
                .build();
    }

    private void updateNotification() {
        try {
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            manager.notify(NOTIFICATION_ID, buildNotification());
        } catch (Throwable ignored) {
        }
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "FixHUA Turbo",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Visible while the user-started GBox stability session is active");
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            manager.createNotificationChannel(channel);
        }
    }

    @Override
    public void onDestroy() {
        running = false;

        if (watchdog != null) {
            watchdog.shutdownNow();
            watchdog = null;
        }

        if (connectivityManager != null && networkCallback != null) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            } catch (Throwable ignored) {
            }
        }
        networkCallback = null;

        if (wifiLock != null && wifiLock.isHeld()) {
            try {
                wifiLock.release();
            } catch (Throwable ignored) {
            }
        }
        wifiLock = null;

        if (cpuWakeLock != null && cpuWakeLock.isHeld()) {
            try {
                cpuWakeLock.release();
            } catch (Throwable ignored) {
            }
        }
        cpuWakeLock = null;

        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
