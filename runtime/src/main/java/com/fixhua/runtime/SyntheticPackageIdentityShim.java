package com.fixhua.runtime;

import android.os.IBinder;
import android.os.Parcel;

/** First typed Synthetic PackageManager view: immutable identity of the imported session package. */
final class SyntheticPackageIdentityShim {
    private static final String BROKER_DESCRIPTOR = "com.fixhua.guard.IHostCapabilityBroker";
    private static final int TRANSACTION_GET_SYNTHETIC_PACKAGE_IDENTITY = IBinder.FIRST_CALL_TRANSACTION + 3;

    record Identity(
            String packageName,
            String versionName,
            long versionCode,
            int minSdk,
            int targetSdk,
            String applicationClassName,
            String signingSha256,
            String truthScope) {}

    static Identity query(
            IBinder broker,
            String sessionId,
            String artifactSha256,
            String planDigest) throws Exception {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(BROKER_DESCRIPTOR);
            data.writeString(sessionId);
            data.writeString(artifactSha256);
            data.writeString(planDigest);
            boolean handled = broker.transact(
                    TRANSACTION_GET_SYNTHETIC_PACKAGE_IDENTITY, data, reply, 0);
            if (!handled) throw new SecurityException("Controller rejected package identity query");
            reply.readException();
            Identity identity = new Identity(
                    reply.readString(),
                    reply.readString(),
                    reply.readLong(),
                    reply.readInt(),
                    reply.readInt(),
                    reply.readString(),
                    reply.readString(),
                    reply.readString());
            validate(identity, artifactSha256);
            return identity;
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    private static void validate(Identity identity, String artifactSha256) {
        if (identity.packageName() == null || identity.packageName().isBlank()) {
            throw new SecurityException("Synthetic package name missing");
        }
        if (!"SYNTHETIC_VIEW".equals(identity.truthScope())) {
            throw new SecurityException("Unexpected package identity truth scope");
        }
        if (identity.versionCode() < 0 || identity.minSdk() < 0 || identity.targetSdk() < 0) {
            throw new SecurityException("Malformed synthetic package identity");
        }
        if (identity.signingSha256() == null || identity.signingSha256().length() != 64) {
            throw new SecurityException("Synthetic signing view missing");
        }
        if (artifactSha256 == null || artifactSha256.length() != 64) {
            throw new SecurityException("Malformed session artifact hash");
        }
    }

    private SyntheticPackageIdentityShim() {}
}
