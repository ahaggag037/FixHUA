package com.fixhua.runtime;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import com.fixhua.core.CoreModels;
import com.fixhua.core.ExecutionEvidence;
import com.fixhua.core.SyntheticSlicePlans;

/** Entry points used by transformed foreign bytecode. */
public final class PackageManagerHooks {
    public static PackageInfo getPackageInfo(
            PackageManager ignoredHostPackageManager,
            String packageName,
            int flags) throws PackageManager.NameNotFoundException {
        SyntheticExecutionScope.Environment environment = SyntheticExecutionScope.require();
        String subject = "package:" + safeSubject(packageName);
        try {
            SyntheticPackageManagerView.QueryResult result = environment.packageQuery().query(packageName);
            if (!result.found() || result.projection() == null) {
                ExecutionEvidenceEmitter.emit(
                        SyntheticSlicePlans.CAPABILITY_SYNTHETIC_PACKAGE_QUERY,
                        "runtime.synthetic-package-query",
                        ExecutionEvidence.Decision.SYNTHETIC,
                        CoreModels.TruthScope.SYNTHETIC_VIEW,
                        subject,
                        "NOT_FOUND");
                throw new PackageManager.NameNotFoundException(packageName);
            }
            SyntheticPackageManagerView.PackageProjection projection = result.projection();
            ExecutionEvidenceEmitter.emit(
                    SyntheticSlicePlans.CAPABILITY_SYNTHETIC_PACKAGE_QUERY,
                    "runtime.synthetic-package-query",
                    ExecutionEvidence.Decision.SYNTHETIC,
                    CoreModels.TruthScope.SYNTHETIC_VIEW,
                    subject,
                    "FOUND");
            PackageInfo info = new PackageInfo();
            info.packageName = projection.packageName();
            info.versionName = projection.versionName();
            info.setLongVersionCode(projection.versionCode());
            ApplicationInfo app = new ApplicationInfo();
            app.packageName = projection.packageName();
            app.className = projection.applicationClassName();
            app.minSdkVersion = projection.minSdk();
            app.targetSdkVersion = projection.targetSdk();
            info.applicationInfo = app;
            return info;
        } catch (PackageManager.NameNotFoundException expected) {
            throw expected;
        } catch (SecurityException security) {
            throw security;
        } catch (Exception failure) {
            SecurityException wrapped = new SecurityException("Synthetic package query failed");
            wrapped.initCause(failure);
            throw wrapped;
        }
    }

    private static String safeSubject(String value) {
        if (value == null || value.isBlank()) return "unknown";
        String sanitized = value.replace('|', '_').replace('\n', '_').replace('\r', '_');
        return sanitized.length() <= 120 ? sanitized : sanitized.substring(0, 120);
    }

    private PackageManagerHooks() {}
}
