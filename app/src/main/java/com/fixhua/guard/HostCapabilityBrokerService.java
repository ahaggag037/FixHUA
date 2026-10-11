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
    static final int PROTOCOL_VERSION = 2;
    static final int TRANSACTION_GET_PROTOCOL = IBinder.FIRST_CALL_TRANSACTION;
    static final int TRANSACTION_OPEN_SESSION_ARTIFACT = IBinder.FIRST_CALL_TRANSACTION + 1;
    private static final String EXPECTED_RUNTIME_PACKAGE = "com.fixhua.runtime";

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
                reply.writeNoException();
                reply.writeInt(PROTOCOL_VERSION);
                return true;
            }

            if (code == TRANSACTION_OPEN_SESSION_ARTIFACT) {
                data.enforceInterface(DESCRIPTOR);
                String sessionId = bounded(data.readString(), 128, "sessionId");
                String artifactSha256 = bounded(data.readString(), 64, "artifactSha256");
                String planDigest = bounded(data.readString(), 64, "planDigest");
                enforceNoTrailingData(data);

                try {
                    File artifact = new RuntimeSessionStore(HostCapabilityBrokerService.this)
                            .requireArtifactFile(sessionId, artifactSha256, planDigest);
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

            return super.onTransact(code, data, reply, flags);
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return bridge;
    }

    private void enforceTrustedRuntimeCaller() {
        int uid = Binder.getCallingUid();
        PackageManager pm = getPackageManager();
        String[] packages = pm.getPackagesForUid(uid);
        if (packages == null) {
            throw new SecurityException("Caller UID has no visible packages: " + uid);
        }

        boolean runtimeAtUid = false;
        for (String pkg : packages) {
            if (EXPECTED_RUNTIME_PACKAGE.equals(pkg)) {
                runtimeAtUid = true;
                break;
            }
        }
        if (!runtimeAtUid) {
            throw new SecurityException("Caller UID is not FixHUA Runtime: " + uid);
        }

        if (pm.checkSignatures(getPackageName(), EXPECTED_RUNTIME_PACKAGE)
                != PackageManager.SIGNATURE_MATCH) {
            throw new SecurityException("FixHUA Runtime signing identity mismatch");
        }
    }

    private static String bounded(String value, int exactOrMax, String field) {
        if (value == null || value.isBlank() || value.length() > exactOrMax) {
            throw new SecurityException("Malformed " + field);
        }
        if (("artifactSha256".equals(field) || "planDigest".equals(field))
                && value.length() != exactOrMax) {
            throw new SecurityException("Malformed " + field);
        }
        return value;
    }

    private static void enforceNoTrailingData(Parcel data) {
        if (data.dataAvail() != 0) throw new SecurityException("Unexpected broker payload");
    }
}
