package com.fixhua.sampletarget;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.widget.TextView;

public final class SampleActivity extends Activity {
    private static final String TAG = "FixHUASample";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String baseline = ProbeEntry.run(this);
        Context appContext = getApplicationContext();
        String appContextPackage = appContext == null ? "null" : appContext.getPackageName();
        String appContextClass = appContext == null ? "null" : appContext.getClass().getName();
        Log.i(TAG, "hostBaseline=true " + baseline
                + ";appContextPackage=" + appContextPackage
                + ";appContextClass=" + appContextClass);
        TextView text = new TextView(this);
        text.setPadding(32, 48, 32, 32);
        text.setText("FixHUA Sample Target\nHost baseline:\n" + baseline
                + "\nApplication context: " + appContextPackage
                + " / " + appContextClass);
        setContentView(text);
        verifyControllerBridgeIsDenied();
    }

    private void verifyControllerBridgeIsDenied() {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(
                "com.fixhua.guard",
                "com.fixhua.guard.HostCapabilityBrokerService"));
        try {
            boolean bound = bindService(intent, new ServiceConnection() {
                @Override public void onServiceConnected(ComponentName name, IBinder service) {
                    Log.e(TAG, "SECURITY_FAILURE: untrusted sample bound to Controller bridge");
                }
                @Override public void onServiceDisconnected(ComponentName name) {}
            }, Context.BIND_AUTO_CREATE);
            if (!bound) Log.i(TAG, "bridgeDenied=true reason=bindService_false");
            else Log.e(TAG, "SECURITY_FAILURE: bindService returned true for untrusted sample");
        } catch (SecurityException expected) {
            Log.i(TAG, "bridgeDenied=true reason=SecurityException");
        }
    }
}
