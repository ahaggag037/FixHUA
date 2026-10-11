package com.fixhua.runtime;

import android.os.IBinder;
import android.os.Parcel;

/**
 * Session-scoped PackageManager-like projection. V1 deliberately exposes only the imported
 * package. It never falls through to the host PackageManager and therefore cannot accidentally
 * leak or inherit host package visibility semantics.
 */
final class SyntheticPackageManagerView {
    private static final String BROKER_DESCRIPTOR = "com.fixhua.guard.IHostCapabilityBroker";
    private static final int TRANSACTION_QUERY_SYNTHETIC_PACKAGE = IBinder.FIRST_CALL_TRANSACTION + 4;
    private static final String EXPECTED_TRUTH_SCOPE = "SYNTHETIC_VIEW";

    record PackageProjection(
            String packageName,
            String versionName,
            long versionCode,
            int minSdk,
            int targetSdk,
            String applicationClassName,
            String signingSha256,
            String truthScope) {}

    record QueryResult(String queryPackage, boolean found, String truthScope, PackageProjection projection) {
        QueryResult {
            if (queryPackage == null || queryPackage.isBlank()) {
                throw new SecurityException("Synthetic package query missing");
            }
            if (!EXPECTED_TRUTH_SCOPE.equals(truthScope)) {
                throw new SecurityException("Unexpected package query truth scope");
            }
            if (found != (projection != null)) {
                throw new SecurityException("Malformed package query result");
            }
            if (projection != null && !queryPackage.equals(projection.packageName())) {
                throw new SecurityException("Synthetic package result does not match query");
            }
        }
    }

    static QueryResult query(
            IBinder broker,
            String sessionId,
            String artifactSha256,
            String planDigest,
            String queryPackage) throws Exception {
        validatePackageName(queryPackage);
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(BROKER_DESCRIPTOR);
            data.writeString(sessionId);
            data.writeString(artifactSha256);
            data.writeString(planDigest);
            data.writeString(queryPackage);
            boolean handled = broker.transact(TRANSACTION_QUERY_SYNTHETIC_PACKAGE, data, reply, 0);
            if (!handled) throw new SecurityException("Controller rejected package query");
            reply.readException();
            String truthScope = reply.readString();
            boolean found = reply.readInt() != 0;
            PackageProjection projection = null;
            if (found) {
                projection = new PackageProjection(
                        reply.readString(),
                        reply.readString(),
                        reply.readLong(),
                        reply.readInt(),
                        reply.readInt(),
                        reply.readString(),
                        reply.readString(),
                        reply.readString());
                validateProjection(projection);
            }
            if (reply.dataAvail() != 0) {
                throw new SecurityException("Unexpected package query reply payload");
            }
            return new QueryResult(queryPackage, found, truthScope, projection);
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    private static void validateProjection(PackageProjection projection) {
        validatePackageName(projection.packageName());
        if (!EXPECTED_TRUTH_SCOPE.equals(projection.truthScope())) {
            throw new SecurityException("Unexpected package projection truth scope");
        }
        if (projection.versionCode() < 0 || projection.minSdk() < 0 || projection.targetSdk() < 0) {
            throw new SecurityException("Malformed synthetic package projection");
        }
        String signingSha = projection.signingSha256();
        if (signingSha == null || signingSha.length() != 64) {
            throw new SecurityException("Synthetic signing projection missing");
        }
        for (int i = 0; i < signingSha.length(); i++) {
            char c = Character.toLowerCase(signingSha.charAt(i));
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) {
                throw new SecurityException("Malformed synthetic signing projection");
            }
        }
    }

    private static void validatePackageName(String value) {
        if (value == null || value.isBlank() || value.length() > 255
                || value.startsWith(".") || value.endsWith(".") || value.contains("..")) {
            throw new SecurityException("Malformed package query");
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean allowed = (c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '_' || c == '.';
            if (!allowed) throw new SecurityException("Malformed package query");
        }
    }

    private SyntheticPackageManagerView() {}
}
