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

import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;

public final class RuntimeActivity extends Activity {
    private static final String TAG = "FixHUARuntime";
    private static final String BROKER_DESCRIPTOR = "com.fixhua.guard.IHostCapabilityBroker";
    private static final int TRANSACTION_GET_PROTOCOL = IBinder.FIRST_CALL_TRANSACTION;
    private static final int TRANSACTION_GET_ACTIVE_SESSION = IBinder.FIRST_CALL_TRANSACTION + 2;
    private static final int SESSION_PROTOCOL_VERSION = 2;
    private static final AtomicInteger CREATE_COUNT = new AtomicInteger();

    public static final String EXTRA_SESSION_ID = "com.fixhua.runtime.extra.SESSION_ID";
    public static final String EXTRA_ARTIFACT_SHA256 = "com.fixhua.runtime.extra.ARTIFACT_SHA256";
    public static final String EXTRA_PLAN_DIGEST = "com.fixhua.runtime.extra.PLAN_DIGEST";

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

                SessionArgs session = SessionArgs.from(getIntent());
                if (session == null && protocol >= SESSION_PROTOCOL_VERSION) {
                    session = queryActiveSession(service);
                }
                if (session != null) {
                    if (protocol < SESSION_PROTOCOL_VERSION) {
                        throw new SecurityException("Controller protocol does not support Runtime sessions");
                    }
                    runSessionArtifactProbe(service, session);
                } else {
                    // Temporary compatibility fallback for the pre-session acceptance path. It is
                    // retained only until CI populates Controller artifact storage before Runtime
                    // launch; normal Controller-import flow creates an active session.
                    Log.w(TAG, "legacyArtifactFallback=true");
                    runLegacyArtifactProbe();
                }
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

    private SessionArgs queryActiveSession(IBinder broker) throws Exception {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(BROKER_DESCRIPTOR);
            boolean handled = broker.transact(TRANSACTION_GET_ACTIVE_SESSION, data, reply, 0);
            if (!handled) throw new SecurityException("Controller did not handle active-session query");
            reply.readException();
            if (reply.readInt() == 0) return null;
            SessionArgs session = new SessionArgs(
                    reply.readString(),
                    reply.readString(),
                    reply.readString());
            String packageName = reply.readString();
            String recipeId = reply.readString();
            int recipeVersion = reply.readInt();
            Log.i(TAG, "activeSession=true package=" + packageName
                    + " recipe=" + recipeId + ":" + recipeVersion
                    + " sha256=" + session.artifactSha256());
            return session;
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    private void runSessionArtifactProbe(IBinder broker, SessionArgs session) {
        RuntimeApp.assertReady("sessionArtifactProbe");
        try {
            File image = RuntimeSessionArtifact.obtain(
                    this,
                    broker,
                    session.sessionId(),
                    session.artifactSha256(),
                    session.planDigest());
            RuntimeArtifactProbe.Result result = RuntimeArtifactProbe.run(this, image);
            if (!session.artifactSha256().equalsIgnoreCase(result.artifactSha256())) {
                throw new SecurityException("Loaded artifact differs from active session");
            }
            Log.i(TAG, "sessionArtifact=true code=" + result.codeMarker()
                    + " resource=" + result.resourceMarker()
                    + " sha256=" + result.artifactSha256()
                    + " imageReadOnly=" + !image.canWrite());
            status.append("\nSession artifact: verified");
            status.append("\nExternal APK code: " + result.codeMarker());
            status.append("\nExternal APK resource: " + result.resourceMarker());
            status.append("\nArtifact SHA-256: " + result.artifactSha256().substring(0, 12) + "…");
            status.append("\nExecution image: read-only");
        } catch (Exception e) {
            Log.e(TAG, "sessionArtifact=false", e);
            status.append("\nSession artifact failed: " + e.getClass().getSimpleName());
        }
    }

    private void runLegacyArtifactProbe() {
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

    private record SessionArgs(String sessionId, String artifactSha256, String planDigest) {
        SessionArgs {
            if (sessionId == null || sessionId.isBlank() || sessionId.length() > 128) {
                throw new SecurityException("Malformed Runtime session ID");
            }
            validateSha(artifactSha256, "artifactSha256");
            validateSha(planDigest, "planDigest");
        }

        static SessionArgs from(Intent intent) {
            if (intent == null) return null;
            String sessionId = intent.getStringExtra(EXTRA_SESSION_ID);
            String artifactSha256 = intent.getStringExtra(EXTRA_ARTIFACT_SHA256);
            String planDigest = intent.getStringExtra(EXTRA_PLAN_DIGEST);
            boolean any = sessionId != null || artifactSha256 != null || planDigest != null;
            if (!any) return null;
            if (sessionId == null || artifactSha256 == null || planDigest == null) {
                throw new SecurityException("Incomplete Runtime session envelope");
            }
            return new SessionArgs(sessionId, artifactSha256, planDigest);
        }

        private static void validateSha(String value, String field) {
            if (value == null || value.length() != 64) throw new SecurityException("Malformed " + field);
            for (int i = 0; i < value.length(); i++) {
                char c = Character.toLowerCase(value.charAt(i));
                if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) {
                    throw new SecurityException("Malformed " + field);
                }
            }
        }
    }
}
