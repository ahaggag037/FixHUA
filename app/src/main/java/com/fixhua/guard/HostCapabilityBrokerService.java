package com.fixhua.guard;

import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;

public final class HostCapabilityBrokerService extends Service {
    static final String DESCRIPTOR = "com.fixhua.guard.IHostCapabilityBroker";
    static final int PROTOCOL_VERSION = 1;
    static final int TRANSACTION_GET_PROTOCOL = IBinder.FIRST_CALL_TRANSACTION;
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
}
