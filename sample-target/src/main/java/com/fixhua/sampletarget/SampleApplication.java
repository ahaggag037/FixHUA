package com.fixhua.sampletarget;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.os.IBinder;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Ordinary target Application used to prove FixHUA can construct app state before Activity launch. */
public final class SampleApplication extends Application {
    private static final String TAG = "FixHUASampleApp";
    private static final String RUNTIME_BRIDGE_PERMISSION =
            "com.fixhua.guard.permission.RUNTIME_BRIDGE";
    private static volatile String snapshot = "created=false";

    @Override
    public void onCreate() {
        super.onCreate();
        Object connectivity = getSystemService(Context.CONNECTIVITY_SERVICE);
        String permission = checkSelfPermission(RUNTIME_BRIDGE_PERMISSION)
                == PackageManager.PERMISSION_GRANTED ? "granted" : "denied";
        boolean bridgeBound = tryBindControllerBridge();
        Context appContext = getApplicationContext();
        String guestData = exerciseGuestData();
        snapshot = "created=true"
                + ";package=" + getPackageName()
                + ";bridgePerm=" + permission
                + ";connectivity=" + (connectivity != null ? "host" : "missing")
                + ";bridgeBind=" + (bridgeBound ? "unexpected" : "denied")
                + ";appContext=" + (appContext == null ? "null" : appContext.getClass().getName())
                + ";" + guestData;
        Log.i(TAG, "applicationProbe=true " + snapshot);
    }

    public static String snapshot() {
        return snapshot;
    }

    private String exerciseGuestData() {
        try {
            int previousFileState = readCounterFile();
            int fileState = previousFileState + 1;
            try (FileOutputStream out = openFileOutput("r11-state.txt", Context.MODE_PRIVATE)) {
                out.write(Integer.toString(fileState).getBytes(StandardCharsets.UTF_8));
            }

            SharedPreferences preferences = getSharedPreferences("r11-state", Context.MODE_PRIVATE);
            int prefsState = preferences.getInt("count", 0) + 1;
            if (!preferences.edit().putInt("count", prefsState).commit()) {
                throw new IllegalStateException("guest preferences commit failed");
            }

            SQLiteDatabase database = openOrCreateDatabase("r11-state.db", Context.MODE_PRIVATE, null);
            try {
                database.execSQL("CREATE TABLE IF NOT EXISTS proof (id INTEGER PRIMARY KEY, value TEXT)");
                database.execSQL("INSERT OR REPLACE INTO proof(id, value) VALUES(1, 'ready')");
            } finally {
                database.close();
            }

            File dataDir = getDataDir();
            File filesDir = getFilesDir();
            boolean dataDirConsistent = dataDir != null
                    && getApplicationInfo() != null
                    && dataDir.getAbsolutePath().equals(getApplicationInfo().dataDir);
            boolean filesPrivate = filesDir != null
                    && dataDir != null
                    && filesDir.getAbsolutePath().startsWith(dataDir.getAbsolutePath() + File.separator);
            boolean databaseReady = getDatabasePath("r11-state.db").isFile();
            boolean fileVisible = false;
            for (String name : fileList()) {
                if ("r11-state.txt".equals(name)) {
                    fileVisible = true;
                    break;
                }
            }
            return "guestData=true"
                    + ";fileState=" + fileState
                    + ";prefsState=" + prefsState
                    + ";db=" + databaseReady
                    + ";fileVisible=" + fileVisible
                    + ";dataDirConsistent=" + dataDirConsistent
                    + ";filesPrivate=" + filesPrivate;
        } catch (Exception failure) {
            Log.e(TAG, "guestDataProbe=false", failure);
            return "guestData=false;guestError=" + failure.getClass().getSimpleName();
        }
    }

    private int readCounterFile() throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                openFileInput("r11-state.txt"), StandardCharsets.UTF_8))) {
            String line = reader.readLine();
            return line == null || line.isBlank() ? 0 : Integer.parseInt(line.trim());
        } catch (FileNotFoundException missing) {
            return 0;
        }
    }

    private boolean tryBindControllerBridge() {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(
                "com.fixhua.guard",
                "com.fixhua.guard.HostCapabilityBrokerService"));
        try {
            return bindService(intent, new ServiceConnection() {
                @Override public void onServiceConnected(ComponentName name, IBinder service) {}
                @Override public void onServiceDisconnected(ComponentName name) {}
            }, Context.BIND_AUTO_CREATE);
        } catch (SecurityException expected) {
            return false;
        }
    }
}
