package com.fixhua.runtime;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Process;
import android.util.Log;
import android.widget.TextView;

public final class RuntimeActivity extends Activity {
    private static final String TAG = "FixHUARuntime";
    private static final String BROKER_DESCRIPTOR = "com.fixhua.guard.IHostCapabilityBroker";
    private static final int TRANSACTION_GET_PROTOCOL = IBinder.FIRST_CALL_TRANSACTION;

    private TextView status;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
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
        status = new TextView(this);
        status.setPadding(32, 48, 32, 32);
        status.setText("Runtime UID: " + Process.myUid() + "\nConnecting to Controller…");
        setContentView(status);

        Intent intent = new Intent();
        intent.setComponent(new ComponentName(
                "com.fixhua.guard",
                "com.fixhua.guard.HostCapabilityBrokerService"));
        boolean started = bindService(intent, connection, Context.BIND_AUTO_CREATE);
        if (!started) {
            Log.e(TAG, "Controller bridge unavailable");
            status.setText("Runtime UID: " + Process.myUid() + "\nController bridge unavailable");
        }
    }

    @Override
    protected void onDestroy() {
        try {
            unbindService(connection);
        } catch (IllegalArgumentException ignored) {
        }
        super.onDestroy();
    }
}
