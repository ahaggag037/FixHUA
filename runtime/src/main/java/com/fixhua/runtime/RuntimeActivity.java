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
    private static final int SESSION_PROTOCOL_VERSION = 5;
    private static final String ABSENT_PACKAGE_PROBE = "com.fixhua.synthetic.absent";
    private static final int OUT_OF_PLAN_CAPABILITY_PROBE = 9999;
    private static final AtomicInteger CREATE_COUNT = new AtomicInteger();

    public static final String EXTRA_SESSION_ID = "com.fixhua.runtime.extra.SESSION_ID";
    public static final String EXTRA_ARTIFACT_SHA256 = "com.fixhua.runtime.extra.ARTIFACT_SHA256";
    public static final String EXTRA_PLAN_DIGEST = "com.fixhua.runtime.extra.PLAN_DIGEST";
    public static final String EXTRA_ACCEPTANCE_SECURITY_PROBE =
            "com.fixhua.runtime.extra.ACCEPTANCE_SECURITY_PROBE";

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
                if (!service.transact(TRANSACTION_GET_PROTOCOL, data, reply, 0)) {
                    throw new IllegalStateException("Controller rejected protocol request");
                }
                reply.readException();
                int protocol = reply.readInt();
                Log.i(TAG, "bridgeAuthenticated=true protocol=" + protocol + " runtimeUid=" + Process.myUid());
                status.setText("Runtime UID: " + Process.myUid()
                        + "\nController bridge: authenticated\nProtocol: " + protocol);

                if (protocol < SESSION_PROTOCOL_VERSION) {
                    throw new SecurityException("Controller protocol does not support host capability delegation");
                }

                SessionArgs session = SessionArgs.from(getIntent());
                if (session == null) session = queryActiveSession(service);
                if (session == null) {
                    Log.w(TAG, "sessionRequired=true activeSession=false executionBlocked=true");
                    status.append("\nNo active Controller-approved session.\nArtifact execution is blocked.");
                    return;
                }

                SyntheticPackageIdentityShim.Identity identity = SyntheticPackageIdentityShim.query(
                        service, session.sessionId(), session.artifactSha256(), session.planDigest());
                Log.i(TAG, "packageIdentityShim=true package=" + identity.packageName()
                        + " versionCode=" + identity.versionCode()
                        + " targetSdk=" + identity.targetSdk()
                        + " truth=" + identity.truthScope());

                SyntheticPackageManagerView.QueryResult selfView = SyntheticPackageManagerView.query(
                        service, session.sessionId(), session.artifactSha256(), session.planDigest(),
                        identity.packageName());
                if (!selfView.found() || selfView.projection() == null) {
                    throw new SecurityException("Imported package missing from synthetic package view");
                }
                SyntheticPackageManagerView.QueryResult absentView = SyntheticPackageManagerView.query(
                        service, session.sessionId(), session.artifactSha256(), session.planDigest(),
                        ABSENT_PACKAGE_PROBE);
                if (absentView.found()) {
                    throw new SecurityException("Synthetic package view fabricated an unconfigured package");
                }
                Log.i(TAG, "packageManagerView=true package=" + selfView.projection().packageName()
                        + " selfFound=true absentFound=false truth=" + selfView.truthScope());

                HostCapabilityClient.GBoxFact gboxFact = HostCapabilityClient.queryGBox(
                        service,
                        session.sessionId(),
                        session.artifactSha256(),
                        session.planDigest());
                Log.i(TAG, "hostCapability=true capability=1002 gboxInstalled=" + gboxFact.installed()
                        + " version=" + gboxFact.versionName()
                        + " versionCode=" + gboxFact.versionCode()
                        + " truth=" + gboxFact.truthScope());

                if (getIntent() != null
                        && getIntent().getBooleanExtra(EXTRA_ACCEPTANCE_SECURITY_PROBE, false)) {
                    HostCapabilityClient.requireDenied(
                            service,
                            session.sessionId(),
                            session.artifactSha256(),
                            session.planDigest(),
                            OUT_OF_PLAN_CAPABILITY_PROBE);
                    Log.i(TAG, "outOfPlanDenied=true capability=" + OUT_OF_PLAN_CAPABILITY_PROBE);
                }

                status.append("\nSynthetic package: " + selfView.projection().packageName());
                status.append("\nSynthetic version: " + selfView.projection().versionName()
                        + " (" + selfView.projection().versionCode() + ")");
                status.append("\nSynthetic targetSdk: " + selfView.projection().targetSdk());
                status.append("\nPackage view: imported package only");
                status.append("\nSynthetic truth: " + selfView.truthScope());
                status.append("\nHost GBox: " + (gboxFact.installed()
                        ? gboxFact.versionName() + " (" + gboxFact.versionCode() + ")"
                        : "not installed"));
                status.append("\nHost truth: " + gboxFact.truthScope());

                runSessionArtifactProbe(service, session, selfView.projection().packageName());
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
                "com.fixhua.guard", "com.fixhua.guard.HostCapabilityBrokerService"));
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
            if (!broker.transact(TRANSACTION_GET_ACTIVE_SESSION, data, reply, 0)) {
                throw new SecurityException("Controller did not handle active-session query");
            }
            reply.readException();
            if (reply.readInt() == 0) return null;
            SessionArgs session = new SessionArgs(
                    reply.readString(), reply.readString(), reply.readString());
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

    private void runSessionArtifactProbe(
            IBinder broker,
            SessionArgs session,
            String syntheticPackageName) {
        RuntimeApp.assertReady("sessionArtifactProbe");
        try {
            File sourceImage = RuntimeSessionArtifact.obtain(
                    this, broker, session.sessionId(), session.artifactSha256(), session.planDigest());
            RuntimeArtifactProbe.Result result = RuntimeArtifactProbe.run(
                    this,
                    sourceImage,
                    broker,
                    session.sessionId(),
                    session.artifactSha256(),
                    session.planDigest(),
                    syntheticPackageName);
            if (!session.artifactSha256().equalsIgnoreCase(result.sourceSha256())) {
                throw new SecurityException("Loaded source artifact differs from active session");
            }
            String expectedPrefix = "package=" + syntheticPackageName
                    + ";gms=absent;bridgePerm=denied;";
            if (!result.appViewMarker().startsWith(expectedPrefix)) {
                throw new SecurityException("Transformed app did not consume synthetic context/package/permission view");
            }
            Log.i(TAG, "sessionArtifact=true code=" + result.codeMarker()
                    + " resource=" + result.resourceMarker()
                    + " sourceSha256=" + result.sourceSha256()
                    + " transformedSha256=" + result.transformedSha256()
                    + " transformCallsites=" + result.rewrittenCallsites()
                    + " pmCallsites=" + result.packageManagerCallsites()
                    + " contextCallsites=" + result.contextIdentityCallsites()
                    + " safeStubCallsites=" + result.safeStubCallsites()
                    + " appView=" + result.appViewMarker()
                    + " sourceReadOnly=" + !sourceImage.canWrite());
            status.append("\nSession source: verified");
            status.append("\nDEX callsite transform: " + result.rewrittenCallsites());
            status.append("\nPermission SAFE_STUB callsites: " + result.safeStubCallsites());
            status.append("\nSynthetic Context package: " + syntheticPackageName);
            status.append("\nForeign app view: " + result.appViewMarker());
            status.append("\nExternal APK code: " + result.codeMarker());
            status.append("\nExternal APK resource: " + result.resourceMarker());
            status.append("\nSource SHA-256: " + result.sourceSha256().substring(0, 12) + "…");
            status.append("\nDerived image SHA-256: " + result.transformedSha256().substring(0, 12) + "…");
        } catch (Exception e) {
            Log.e(TAG, "sessionArtifact=false", e);
            status.append("\nSession artifact failed: " + e.getClass().getSimpleName());
        }
    }

    @Override
    protected void onDestroy() {
        RuntimeApp.assertReady("activityOnDestroy");
        if (bound) {
            try { unbindService(connection); } catch (IllegalArgumentException ignored) {}
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
