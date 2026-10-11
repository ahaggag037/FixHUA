package com.fixhua.runtime;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Process;
import android.util.Log;
import android.widget.TextView;

import java.util.concurrent.atomic.AtomicInteger;

public final class RuntimeActivity extends Activity {
    private static final String TAG = "FixHUARuntime";
    private static final String BROKER_DESCRIPTOR = "com.fixhua.guard.IHostCapabilityBroker";
    private static final int TRANSACTION_GET_PROTOCOL = IBinder.FIRST_CALL_TRANSACTION;
    private static final AtomicInteger CREATE_COUNT = new AtomicInteger();

    private TextView status;
    private boolean bound;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            RuntimeApp.assertReady("controllerBridgeConnected");
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(BROKER_DESCRIPTOR);
                boolean handled = service.transact(TRANSACTION_GET_PROTOCOL, data, reply, 0);
                if (!handled) throw new IllegalStateException("Controller rejected protocol request");
                reply.readException();
                int protocol = reply.readInt();
                Log.i(TAG, "bridgeAuthenticated=true protocol=" + protocol + " runtimeUid=" + Process.myUid());
                status.setText("Runtime UID: " + Process.myUid()
                        + "\nController bridge: authenticated"
                        + "\nProtocol: " + protocol);
                runArtifactProbe();
            } catch (Exception e) {
                Log.e(TAG, "bridgeAuthenticated=false", e);
                status.setText("Runtime UID: " + Process.myUid()
                        + "\nController bridge failed: " + e.getClass().getSimpleName()
                        + ": " + String.valueOf(e.getMessage()));
            } finally {
                data.recycle();
                reply.recycle();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.w(TAG, "Controller bridge disconnected");
            status.setText("Runtime UID: " + Process.myUid() + "\nController bridge disconnected");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        RuntimeApp.assertReady("activityOnCreate");
        int createCount = CREATE_COUNT.incrementAndGet();

        status = new TextView(this);
        status.setPadding(32, 48, 32, 32);
        status.setText("Runtime UID: " + Process.myUid() + "\nConnecting to Controller…");
        setContentView(status);

        Log.i(TAG, "activityReady=true createCount=" + createCount
                + " configGeneration=" + RuntimeApp.configurationGeneration()
                + " orientation=" + getResources().getConfiguration().orientation);

        Intent intent = new Intent();
        intent.setComponent(new ComponentName(
                "com.fixhua.guard",
                "com.fixhua.guard.HostCapabilityBrokerService"));
        bound = bindService(intent, connection, Context.BIND_AUTO_CREATE);
        if (!bound) {
            Log.e(TAG, "Controller bridge unavailable");
            status.setText("Runtime UID: " + Process.myUid() + "\nController bridge unavailable");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        RuntimeApp.assertReady("activityOnResume");
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        RuntimeApp.assertReady("activityConfigurationChanged");
        Log.i(TAG, "activityConfigChanged=true orientation=" + newConfig.orientation
                + " configGeneration=" + RuntimeApp.configurationGeneration());
    }

    private void runArtifactProbe() {
        RuntimeApp.assertReady("artifactProbe");
        try {
            RuntimeArtifactProbe.Result result = RuntimeArtifactProbe.run(this);
            Log.i(TAG, "artifactProbe=true code=" + result.codeMarker()
                    + " resource=" + result.resourceMarker()
                    + " sha256=" + result.artifactSha256());
            status.append("\nExternal APK code: " + result.codeMarker());
            status.append("\nExternal APK resource: " + result.resourceMarker());
            status.append("\nArtifact SHA-256: " + result.artifactSha256().substring(0, 12) + "…");
        } catch (Exception e) {
            Log.e(TAG, "artifactProbe=false", e);
            status.append("\nArtifact probe unavailable: " + e.getClass().getSimpleName());
        }
    }

    @Override
    protected void onDestroy() {
        RuntimeApp.assertReady("activityOnDestroy");
        if (bound) {
            try {
                unbindService(connection);
            } catch (IllegalArgumentException ignored) {
            }
            bound = false;
        }
        super.onDestroy();
    }
}
