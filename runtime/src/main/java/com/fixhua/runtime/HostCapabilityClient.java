package com.fixhua.runtime;

import android.os.IBinder;
import android.os.Parcel;

import com.fixhua.core.SyntheticSlicePlans;

/** Narrow Runtime client for Controller-owned host capabilities authorized by the active plan. */
final class HostCapabilityClient {
    private static final String BROKER_DESCRIPTOR = "com.fixhua.guard.IHostCapabilityBroker";
    private static final int TRANSACTION_EXECUTE_HOST_CAPABILITY = IBinder.FIRST_CALL_TRANSACTION + 5;
    private static final String HOST_REAL = "HOST_REAL";

    record GBoxFact(boolean installed, String versionName, long versionCode, String truthScope) {}

    static GBoxFact queryGBox(
            IBinder broker,
            String sessionId,
            String artifactSha256,
            String planDigest) throws Exception {
        Parcel reply = execute(
                broker,
                sessionId,
                artifactSha256,
                planDigest,
                SyntheticSlicePlans.CAPABILITY_HOST_GBOX_PACKAGE_FACT);
        try {
            int capabilityId = reply.readInt();
            if (capabilityId != SyntheticSlicePlans.CAPABILITY_HOST_GBOX_PACKAGE_FACT) {
                throw new SecurityException("Unexpected host capability response");
            }
            String truthScope = reply.readString();
            if (!HOST_REAL.equals(truthScope)) {
                throw new SecurityException("Host package fact did not carry HOST_REAL truth");
            }
            boolean installed = reply.readInt() != 0;
            String versionName = reply.readString();
            long versionCode = reply.readLong();
            if (!installed && (versionCode != 0L || (versionName != null && !versionName.isEmpty()))) {
                throw new SecurityException("Malformed absent host package fact");
            }
            return new GBoxFact(
                    installed,
                    versionName == null ? "" : versionName,
                    versionCode,
                    truthScope);
        } finally {
            reply.recycle();
        }
    }

    static void requireDenied(
            IBinder broker,
            String sessionId,
            String artifactSha256,
            String planDigest,
            int capabilityId) throws Exception {
        Parcel reply = null;
        try {
            reply = execute(broker, sessionId, artifactSha256, planDigest, capabilityId);
        } catch (SecurityException expected) {
            return;
        } finally {
            if (reply != null) reply.recycle();
        }
        throw new SecurityException("Out-of-plan host capability was unexpectedly admitted: " + capabilityId);
    }

    private static Parcel execute(
            IBinder broker,
            String sessionId,
            String artifactSha256,
            String planDigest,
            int capabilityId) throws Exception {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(BROKER_DESCRIPTOR);
            data.writeString(sessionId);
            data.writeString(artifactSha256);
            data.writeString(planDigest);
            data.writeInt(capabilityId);
            boolean handled = broker.transact(
                    TRANSACTION_EXECUTE_HOST_CAPABILITY, data, reply, 0);
            if (!handled) throw new SecurityException("Controller rejected host capability transaction");
            reply.readException();
            return reply;
        } catch (Exception failure) {
            reply.recycle();
            throw failure;
        } finally {
            data.recycle();
        }
    }

    private HostCapabilityClient() {}
}
