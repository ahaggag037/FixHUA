package com.fixhua.guard;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import com.fixhua.core.ActivityManagerSettingsParser;

import java.io.File;
import java.io.FileReader;
import java.util.concurrent.TimeUnit;

/**
 * Optional P2 read-only host probe.
 *
 * <p>This class has no free-form command surface. When the user has explicitly bootstrapped the
 * platform development permission {@code android.permission.DUMP}, it runs exactly one fixed
 * command: {@code /system/bin/dumpsys activity settings}. Raw output is redirected to an
 * app-private temporary file, bounded, parsed for four process-budget integers, then deleted.</p>
 */
final class PrivilegedHostProbe {
    private static final String TAG = "FixHUAHostProbe";
    private static final long TIMEOUT_SECONDS = 5L;
    private static final long MAX_DUMP_BYTES = 512L * 1024L;

    record Result(
            boolean dumpPermissionGranted,
            boolean success,
            ActivityManagerSettingsParser.ProcessBudget processBudget,
            String failureReason) {

        static Result permissionMissing() {
            return new Result(
                    false,
                    false,
                    new ActivityManagerSettingsParser.ProcessBudget(-1, -1, -1, -1),
                    "DUMP_NOT_GRANTED");
        }
    }

    static Result capture(Context context) {
        Context app = context.getApplicationContext();
        if (app.checkSelfPermission(Manifest.permission.DUMP) != PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "dumpPermission=false probeSuccess=false reason=DUMP_NOT_GRANTED");
            return Result.permissionMissing();
        }

        File raw = null;
        Process process = null;
        try {
            raw = File.createTempFile("fixhua-am-settings-", ".tmp", app.getCacheDir());
            ProcessBuilder builder = new ProcessBuilder(
                    "/system/bin/dumpsys",
                    "activity",
                    "settings");
            builder.redirectErrorStream(true);
            builder.redirectOutput(raw);
            process = builder.start();

            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor(1, TimeUnit.SECONDS);
                Log.w(TAG, "dumpPermission=true probeSuccess=false reason=TIMEOUT");
                return failure("TIMEOUT");
            }
            if (process.exitValue() != 0) {
                Log.w(TAG, "dumpPermission=true probeSuccess=false reason=EXIT_" + process.exitValue());
                return failure("EXIT_" + process.exitValue());
            }
            if (raw.length() > MAX_DUMP_BYTES) {
                Log.w(TAG, "dumpPermission=true probeSuccess=false reason=OUTPUT_TOO_LARGE");
                return failure("OUTPUT_TOO_LARGE");
            }

            ActivityManagerSettingsParser.ProcessBudget budget;
            try (FileReader reader = new FileReader(raw)) {
                budget = new ActivityManagerSettingsParser().parse(reader);
            }
            if (!budget.hasUsableCurrentLimit()) {
                Log.w(TAG, "dumpPermission=true probeSuccess=false reason=CURRENT_LIMIT_MISSING");
                return new Result(true, false, budget, "CURRENT_LIMIT_MISSING");
            }

            Log.i(TAG,
                    "dumpPermission=true probeSuccess=true current="
                            + budget.currentMaxCachedProcesses()
                            + " reference=" + budget.referenceMaxCachedProcesses()
                            + " override=" + budget.overrideMaxCachedProcesses()
                            + " conflict=" + budget.hasNonStandardOverride());
            return new Result(true, true, budget, "");
        } catch (Exception failure) {
            Log.w(TAG,
                    "dumpPermission=true probeSuccess=false reason="
                            + failure.getClass().getSimpleName());
            return failure(failure.getClass().getSimpleName());
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
            if (raw != null && raw.exists() && !raw.delete()) {
                // Raw dump remains only in app-private cache if deletion unexpectedly fails.
                raw.deleteOnExit();
            }
        }
    }

    private static Result failure(String reason) {
        return new Result(
                true,
                false,
                new ActivityManagerSettingsParser.ProcessBudget(-1, -1, -1, -1),
                reason);
    }

    private PrivilegedHostProbe() {}
}
