package com.fixhua.guard;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import com.fixhua.core.ActivityManagerSettingsParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Optional P2 read-only host probe.
 *
 * <p>This class has no free-form command surface. When the user has explicitly bootstrapped the
 * platform development permission {@code android.permission.DUMP}, it runs exactly one fixed
 * command: {@code /system/bin/dumpsys activity settings}. Raw output is redirected to an
 * app-private temporary file, bounded, parsed for process-budget integers, then deleted. Only
 * fixed-key presence flags, parsed integers, output size and fixed error categories are logged;
 * arbitrary dumpsys text is never logged or persisted.</p>
 */
final class PrivilegedHostProbe {
    private static final String TAG = "FixHUAHostProbe";
    private static final long TIMEOUT_SECONDS = 5L;
    private static final long MAX_DUMP_BYTES = 512L * 1024L;
    private static final int MAX_DIAGNOSTIC_LINES = 4096;
    private static final int MAX_DIAGNOSTIC_LINE_LENGTH = 8192;

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

    private record SanitizedDiagnostics(
            long outputBytes,
            boolean hasOverrideKey,
            boolean hasCustomizedKey,
            boolean hasPlatformKey,
            boolean hasCurrentKey,
            String knownErrorMarker) {}

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
            long outputBytes = raw.length();
            if (outputBytes > MAX_DUMP_BYTES) {
                Log.w(TAG,
                        "dumpPermission=true probeSuccess=false reason=OUTPUT_TOO_LARGE outputBytes="
                                + outputBytes);
                return failure("OUTPUT_TOO_LARGE");
            }

            ActivityManagerSettingsParser.ProcessBudget budget;
            try (FileReader reader = new FileReader(raw)) {
                budget = new ActivityManagerSettingsParser().parse(reader);
            }
            SanitizedDiagnostics diagnostics = collectSanitizedDiagnostics(raw, outputBytes);
            if (!budget.hasUsableCurrentLimit()) {
                Log.w(TAG,
                        "dumpPermission=true probeSuccess=false reason=CURRENT_LIMIT_MISSING"
                                + " outputBytes=" + diagnostics.outputBytes()
                                + " override=" + budget.overrideMaxCachedProcesses()
                                + " customized=" + budget.customizedMaxCachedProcesses()
                                + " platform=" + budget.platformMaxCachedProcesses()
                                + " current=" + budget.currentMaxCachedProcesses()
                                + " keyOverride=" + diagnostics.hasOverrideKey()
                                + " keyCustomized=" + diagnostics.hasCustomizedKey()
                                + " keyPlatform=" + diagnostics.hasPlatformKey()
                                + " keyCurrent=" + diagnostics.hasCurrentKey()
                                + " marker=" + diagnostics.knownErrorMarker());
                return new Result(true, false, budget, "CURRENT_LIMIT_MISSING");
            }

            Log.i(TAG,
                    "dumpPermission=true probeSuccess=true current="
                            + budget.currentMaxCachedProcesses()
                            + " reference=" + budget.referenceMaxCachedProcesses()
                            + " override=" + budget.overrideMaxCachedProcesses()
                            + " conflict=" + budget.hasNonStandardOverride()
                            + " outputBytes=" + diagnostics.outputBytes());
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

    private static SanitizedDiagnostics collectSanitizedDiagnostics(File raw, long outputBytes)
            throws Exception {
        boolean hasOverride = false;
        boolean hasCustomized = false;
        boolean hasPlatform = false;
        boolean hasCurrent = false;
        String marker = "NONE";
        int lines = 0;

        try (BufferedReader reader = new BufferedReader(new FileReader(raw))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (++lines > MAX_DIAGNOSTIC_LINES) {
                    marker = "DIAGNOSTIC_LINE_LIMIT";
                    break;
                }
                if (line.length() > MAX_DIAGNOSTIC_LINE_LENGTH) {
                    marker = "DIAGNOSTIC_LINE_TOO_LONG";
                    break;
                }
                hasOverride |= line.contains("mOverrideMaxCachedProcesses");
                hasCustomized |= line.contains("mCustomizedMaxCachedProcesses");
                hasPlatform |= line.contains("max_cached_processes");
                hasCurrent |= line.contains("CUR_MAX_CACHED_PROCESSES");

                if ("NONE".equals(marker)) {
                    String lower = line.toLowerCase(Locale.ROOT);
                    if (lower.contains("permission denial") || lower.contains("permission denied")) {
                        marker = "PERMISSION_DENIAL";
                    } else if (lower.contains("securityexception")) {
                        marker = "SECURITY_EXCEPTION";
                    } else if (lower.contains("can't find service") || lower.contains("service not found")) {
                        marker = "SERVICE_NOT_FOUND";
                    } else if (lower.contains("not found") && lower.contains("dumpsys")) {
                        marker = "COMMAND_NOT_FOUND";
                    }
                }
            }
        }
        return new SanitizedDiagnostics(
                outputBytes,
                hasOverride,
                hasCustomized,
                hasPlatform,
                hasCurrent,
                marker);
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
