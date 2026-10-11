package com.fixhua.sampletarget;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.util.Log;

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
        snapshot = "created=true"
                + ";package=" + getPackageName()
                + ";bridgePerm=" + permission
                + ";connectivity=" + (connectivity != null ? "host" : "missing")
                + ";bridgeBind=" + (bridgeBound ? "unexpected" : "denied")
                + ";appContext=" + (appContext == null ? "null" : appContext.getClass().getName());
        Log.i(TAG, "applicationProbe=true " + snapshot);
    }

    public static String snapshot() {
        return snapshot;
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
