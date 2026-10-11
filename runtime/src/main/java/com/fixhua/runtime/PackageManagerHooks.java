package com.fixhua.runtime;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

/** Entry points used by transformed foreign bytecode. */
public final class PackageManagerHooks {
    /**
     * Synthetic replacement for PackageManager#getPackageInfo(String,int).
     * The host PackageManager argument is intentionally not consulted: transformed code must see
     * the declared synthetic view or fail closed, never silently inherit host package visibility.
     */
    public static PackageInfo getPackageInfo(
            PackageManager ignoredHostPackageManager,
            String packageName,
            int flags) throws PackageManager.NameNotFoundException {
        SyntheticExecutionScope.Environment environment = SyntheticExecutionScope.require();
        try {
            SyntheticPackageManagerView.QueryResult result = environment.packageQuery().query(packageName);
            if (!result.found() || result.projection() == null) {
                throw new PackageManager.NameNotFoundException(packageName);
            }
            SyntheticPackageManagerView.PackageProjection projection = result.projection();
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

    private PackageManagerHooks() {}
}
