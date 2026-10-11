package com.fixhua.guard;

import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.Parcelable;
import android.os.RemoteException;

import java.io.File;

public final class HostCapabilityBrokerService extends Service {
    static final String DESCRIPTOR = "com.fixhua.guard.IHostCapabilityBroker";
    static final int PROTOCOL_VERSION = 4;
    static final int TRANSACTION_GET_PROTOCOL = IBinder.FIRST_CALL_TRANSACTION;
    static final int TRANSACTION_OPEN_SESSION_ARTIFACT = IBinder.FIRST_CALL_TRANSACTION + 1;
    static final int TRANSACTION_GET_ACTIVE_SESSION = IBinder.FIRST_CALL_TRANSACTION + 2;
    static final int TRANSACTION_GET_SYNTHETIC_PACKAGE_IDENTITY = IBinder.FIRST_CALL_TRANSACTION + 3;
    static final int TRANSACTION_QUERY_SYNTHETIC_PACKAGE = IBinder.FIRST_CALL_TRANSACTION + 4;
    private static final String EXPECTED_RUNTIME_PACKAGE = "com.fixhua.runtime";
    private static final String SYNTHETIC_TRUTH_SCOPE = "SYNTHETIC_VIEW";

    private final Binder bridge = new Binder() {
        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                throws RemoteException {
            if (code == INTERFACE_TRANSACTION) {
                reply.writeString(DESCRIPTOR);
                return true;
            }

            enforceTrustedRuntimeCaller();

            if (code == TRANSACTION_GET_PROTOCOL) {
                data.enforceInterface(DESCRIPTOR);
                enforceNoTrailingData(data);
                reply.writeNoException();
                reply.writeInt(PROTOCOL_VERSION);
                return true;
            }

            if (code == TRANSACTION_GET_ACTIVE_SESSION) {
                data.enforceInterface(DESCRIPTOR);
                enforceNoTrailingData(data);
                try {
                    RuntimeSessionStore.Session session = new RuntimeSessionStore(
                            HostCapabilityBrokerService.this).active();
                    reply.writeNoException();
                    if (session == null) {
                        reply.writeInt(0);
                    } else {
                        reply.writeInt(1);
                        writeSessionEnvelope(reply, session);
                    }
                    return true;
                } catch (SecurityException failure) {
                    throw failure;
                } catch (Exception failure) {
                    RemoteException remote = new RemoteException("Active Runtime session unavailable");
                    remote.initCause(failure);
                    throw remote;
                }
            }

            if (code == TRANSACTION_OPEN_SESSION_ARTIFACT) {
                data.enforceInterface(DESCRIPTOR);
                SessionRequest request = readSessionRequest(data);
                try {
                    File artifact = new RuntimeSessionStore(HostCapabilityBrokerService.this)
                            .requireArtifactFile(request.sessionId(), request.artifactSha256(), request.planDigest());
                    try (ParcelFileDescriptor descriptor = ParcelFileDescriptor.open(
                            artifact, ParcelFileDescriptor.MODE_READ_ONLY)) {
                        reply.writeNoException();
                        reply.writeInt(1);
                        descriptor.writeToParcel(reply, Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
                    }
                    return true;
                } catch (SecurityException failure) {
                    throw failure;
                } catch (Exception failure) {
                    RemoteException remote = new RemoteException("Session artifact unavailable");
                    remote.initCause(failure);
                    throw remote;
                }
            }

            if (code == TRANSACTION_GET_SYNTHETIC_PACKAGE_IDENTITY) {
                data.enforceInterface(DESCRIPTOR);
                SessionRequest request = readSessionRequest(data);
                try {
                    RuntimeSessionStore.Session session = new RuntimeSessionStore(
                            HostCapabilityBrokerService.this).requireActive(
                                    request.sessionId(), request.artifactSha256(), request.planDigest());
                    reply.writeNoException();
                    writePackageProjection(reply, session);
                    return true;
                } catch (SecurityException failure) {
                    throw failure;
                } catch (Exception failure) {
                    RemoteException remote = new RemoteException("Synthetic package identity unavailable");
                    remote.initCause(failure);
                    throw remote;
                }
            }

            if (code == TRANSACTION_QUERY_SYNTHETIC_PACKAGE) {
                data.enforceInterface(DESCRIPTOR);
                SessionRequest request = readSessionRequestPrefix(data);
                String queryPackage = packageName(data.readString(), "queryPackage");
                enforceNoTrailingData(data);
                try {
                    RuntimeSessionStore.Session session = new RuntimeSessionStore(
                            HostCapabilityBrokerService.this).requireActive(
                                    request.sessionId(), request.artifactSha256(), request.planDigest());
                    reply.writeNoException();
                    reply.writeString(SYNTHETIC_TRUTH_SCOPE);
                    if (session.packageName().equals(queryPackage)) {
                        reply.writeInt(1);
                        writePackageProjection(reply, session);
                    } else {
                        reply.writeInt(0);
                    }
                    return true;
                } catch (SecurityException failure) {
                    throw failure;
                } catch (Exception failure) {
                    RemoteException remote = new RemoteException("Synthetic package query unavailable");
                    remote.initCause(failure);
                    throw remote;
                }
            }

            return super.onTransact(code, data, reply, flags);
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return bridge;
    }

    private static void writeSessionEnvelope(Parcel reply, RuntimeSessionStore.Session session) {
        reply.writeString(session.sessionId());
        reply.writeString(session.artifactSha256());
        reply.writeString(session.planDigest());
        reply.writeString(session.packageName());
        reply.writeString(session.recipeId());
        reply.writeInt(session.recipeVersion());
    }

    private static void writePackageProjection(Parcel reply, RuntimeSessionStore.Session session) {
        reply.writeString(session.packageName());
        reply.writeString(session.versionName());
        reply.writeLong(session.versionCode());
        reply.writeInt(session.minSdk());
        reply.writeInt(session.targetSdk());
        reply.writeString(session.applicationClassName());
        reply.writeString(session.primarySigningSha256());
        reply.writeString(SYNTHETIC_TRUTH_SCOPE);
    }

    private static SessionRequest readSessionRequest(Parcel data) {
        SessionRequest request = readSessionRequestPrefix(data);
        enforceNoTrailingData(data);
        return request;
    }

    private static SessionRequest readSessionRequestPrefix(Parcel data) {
        String sessionId = bounded(data.readString(), 128, false, "sessionId");
        String artifactSha256 = bounded(data.readString(), 64, true, "artifactSha256");
        String planDigest = bounded(data.readString(), 64, true, "planDigest");
        return new SessionRequest(sessionId, artifactSha256, planDigest);
    }

    private void enforceTrustedRuntimeCaller() {
        int uid = Binder.getCallingUid();
        PackageManager pm = getPackageManager();
        String[] packages = pm.getPackagesForUid(uid);
        if (packages == null) throw new SecurityException("Caller UID has no visible packages: " + uid);

        boolean runtimeAtUid = false;
        for (String pkg : packages) {
            if (EXPECTED_RUNTIME_PACKAGE.equals(pkg)) {
                runtimeAtUid = true;
                break;
            }
        }
        if (!runtimeAtUid) throw new SecurityException("Caller UID is not FixHUA Runtime: " + uid);
        if (pm.checkSignatures(getPackageName(), EXPECTED_RUNTIME_PACKAGE)
                != PackageManager.SIGNATURE_MATCH) {
            throw new SecurityException("FixHUA Runtime signing identity mismatch");
        }
    }

    private static String bounded(String value, int exactOrMax, boolean exact, String field) {
        if (value == null || value.isBlank() || value.length() > exactOrMax
                || (exact && value.length() != exactOrMax)) {
            throw new SecurityException("Malformed " + field);
        }
        return value;
    }

    private static String packageName(String value, String field) {
        String normalized = bounded(value, 255, false, field);
        if (normalized.startsWith(".") || normalized.endsWith(".") || normalized.contains("..")) {
            throw new SecurityException("Malformed " + field);
        }
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            boolean allowed = (c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '_' || c == '.';
            if (!allowed) throw new SecurityException("Malformed " + field);
        }
        return normalized;
    }

    private static void enforceNoTrailingData(Parcel data) {
        if (data.dataAvail() != 0) throw new SecurityException("Unexpected broker payload");
    }

    private record SessionRequest(String sessionId, String artifactSha256, String planDigest) {}
}
