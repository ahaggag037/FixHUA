package com.fixhua.runtime;

import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

/**
 * Context boundary presented to imported code. Host storage and privileged Runtime-family IPC are
 * never inherited implicitly; explicitly modeled surfaces are redirected to the synthetic world.
 */
final class SyntheticRestrictedContext extends ContextWrapper {
    private final Context syntheticApplicationContext;
    private final GuestDataPlane dataPlane;

    SyntheticRestrictedContext(Context host) {
        this(host, null, null);
    }

    SyntheticRestrictedContext(Context host, Context syntheticApplicationContext) {
        this(host, syntheticApplicationContext, null);
    }

    SyntheticRestrictedContext(
            Context host,
            Context syntheticApplicationContext,
            GuestDataPlane dataPlane) {
        super(host);
        this.syntheticApplicationContext = syntheticApplicationContext;
        this.dataPlane = dataPlane;
    }

    @Override
    public String getPackageName() {
        return ContextHooks.getPackageName(this);
    }

    @Override
    public Context getApplicationContext() {
        return syntheticApplicationContext != null ? syntheticApplicationContext : this;
    }

    @Override
    public int checkSelfPermission(String permission) {
        return ContextHooks.checkSelfPermission(this, permission);
    }

    @Override
    public Object getSystemService(String name) {
        return ContextHooks.getSystemService(getBaseContext(), name);
    }

    @Override
    public File getDataDir() {
        return requireDataPlane().root();
    }

    @Override
    public File getFilesDir() {
        return requireDataPlane().filesDir();
    }

    @Override
    public File getCacheDir() {
        return requireDataPlane().cacheDir();
    }

    @Override
    public File getCodeCacheDir() {
        return requireDataPlane().codeCacheDir();
    }

    @Override
    public File getNoBackupFilesDir() {
        return requireDataPlane().noBackupFilesDir();
    }

    @Override
    public FileInputStream openFileInput(String name) throws FileNotFoundException {
        return new FileInputStream(requireDataPlane().filePath(name));
    }

    @Override
    public FileOutputStream openFileOutput(String name, int mode) throws FileNotFoundException {
        if (mode != MODE_PRIVATE && mode != MODE_APPEND) {
            throw new SecurityException("Unsupported guest file mode");
        }
        return new FileOutputStream(requireDataPlane().filePath(name), mode == MODE_APPEND);
    }

    @Override
    public boolean deleteFile(String name) {
        File target = requireDataPlane().filePath(name);
        return !target.exists() || target.delete();
    }

    @Override
    public String[] fileList() {
        return requireDataPlane().fileList();
    }

    @Override
    public SharedPreferences getSharedPreferences(String name, int mode) {
        if (mode != MODE_PRIVATE) throw new SecurityException("Unsupported guest preferences mode");
        GuestDataPlane plane = requireDataPlane();
        if (name == null || name.isBlank() || name.length() > 120
                || name.contains("/") || name.contains("\\")) {
            throw new SecurityException("Malformed guest preferences name");
        }
        // Logical guest namespace on the Runtime's private Context backend. The identity digest keeps
        // different package/signing identities isolated even when the package name is reused.
        return getBaseContext().getSharedPreferences(
                "fixhua_guest_" + plane.identityDigest() + "_" + name, MODE_PRIVATE);
    }

    @Override
    public File getDatabasePath(String name) {
        return requireDataPlane().databasePath(name);
    }

    @Override
    public SQLiteDatabase openOrCreateDatabase(
            String name, int mode, SQLiteDatabase.CursorFactory factory) {
        if (mode != MODE_PRIVATE) throw new SecurityException("Unsupported guest database mode");
        return SQLiteDatabase.openOrCreateDatabase(requireDataPlane().databasePath(name), factory);
    }

    @Override
    public SQLiteDatabase openOrCreateDatabase(
            String name,
            int mode,
            SQLiteDatabase.CursorFactory factory,
            DatabaseErrorHandler errorHandler) {
        if (mode != MODE_PRIVATE) throw new SecurityException("Unsupported guest database mode");
        return SQLiteDatabase.openDatabase(
                requireDataPlane().databasePath(name),
                factory,
                SQLiteDatabase.CREATE_IF_NECESSARY,
                errorHandler);
    }

    @Override
    public boolean deleteDatabase(String name) {
        File file = requireDataPlane().databasePath(name);
        return !file.exists() || SQLiteDatabase.deleteDatabase(file);
    }

    @Override
    public String[] databaseList() {
        return requireDataPlane().databaseList();
    }

    @Override
    public ApplicationInfo getApplicationInfo() {
        ApplicationInfo info = new ApplicationInfo(getBaseContext().getApplicationInfo());
        GuestDataPlane plane = requireDataPlane();
        info.packageName = plane.packageName();
        info.dataDir = plane.root().getAbsolutePath();
        info.credentialProtectedDataDir = info.dataDir;
        info.deviceProtectedDataDir = info.dataDir;
        return info;
    }

    @Override
    public boolean bindService(Intent service, ServiceConnection conn, int flags) {
        emitBlockedBinding(service);
        return false;
    }

    @Override
    public boolean bindService(
            Intent service,
            int flags,
            java.util.concurrent.Executor executor,
            ServiceConnection conn) {
        emitBlockedBinding(service);
        return false;
    }

    @Override
    public boolean bindIsolatedService(
            Intent service,
            int flags,
            String instanceName,
            java.util.concurrent.Executor executor,
            ServiceConnection conn) {
        emitBlockedBinding(service);
        return false;
    }

    @Override
    public ComponentName startService(Intent service) {
        throw new SecurityException("Imported code cannot start arbitrary host services");
    }

    @Override
    public ComponentName startForegroundService(Intent service) {
        throw new SecurityException("Imported code cannot start arbitrary host foreground services");
    }

    private GuestDataPlane requireDataPlane() {
        if (dataPlane == null) {
            throw new SecurityException("Guest data plane is unavailable for this synthetic Context");
        }
        return dataPlane;
    }

    private static void emitBlockedBinding(Intent intent) {
        String target = "implicit";
        if (intent != null && intent.getComponent() != null) {
            target = intent.getComponent().flattenToShortString();
        }
        ExecutionEvidenceEmitter.emit(
                com.fixhua.core.SyntheticSlicePlans.CAPABILITY_SYNTHETIC_SLICE_V1,
                "runtime.restricted-context-v2",
                com.fixhua.core.ExecutionEvidence.Decision.UNSUPPORTED,
                com.fixhua.core.CoreModels.TruthScope.SYNTHETIC_VIEW,
                "bind-service",
                "DENIED:" + sanitize(target));
    }

    private static String sanitize(String value) {
        String out = value.replace('|', '_').replace('\n', '_').replace('\r', '_');
        return out.length() <= 120 ? out : out.substring(0, 120);
    }
}
