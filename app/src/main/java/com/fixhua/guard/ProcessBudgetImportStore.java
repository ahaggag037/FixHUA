package com.fixhua.guard;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.concurrent.TimeUnit;

/** Stores only sanitized process-budget integers imported through the protected shell entrypoint. */
final class ProcessBudgetImportStore {
    private static final String PREFS = "fixhua_process_budget_import";
    private static final String KEY_OVERRIDE = "override";
    private static final String KEY_CUSTOMIZED = "customized";
    private static final String KEY_PLATFORM = "platform";
    private static final String KEY_CURRENT = "current";
    private static final String KEY_CAPTURED_AT = "captured_at";
    private static final long MAX_AGE_MS = TimeUnit.HOURS.toMillis(24);
    private static final long MAX_FUTURE_SKEW_MS = TimeUnit.MINUTES.toMillis(5);

    record Stored(ProcessBudgetImport.Payload payload, long capturedAtMillis) {}

    static boolean save(Context context, ProcessBudgetImport.Payload payload, long nowMillis) {
        if (payload == null || !payload.isValid() || nowMillis <= 0L) return false;
        return prefs(context).edit()
                .putInt(KEY_OVERRIDE, payload.overrideMaxCachedProcesses())
                .putInt(KEY_CUSTOMIZED, payload.customizedMaxCachedProcesses())
                .putInt(KEY_PLATFORM, payload.platformMaxCachedProcesses())
                .putInt(KEY_CURRENT, payload.currentMaxCachedProcesses())
                .putLong(KEY_CAPTURED_AT, nowMillis)
                .commit();
    }

    static Stored loadFresh(Context context, long nowMillis) {
        SharedPreferences prefs = prefs(context);
        if (!prefs.contains(KEY_CURRENT) || !prefs.contains(KEY_CAPTURED_AT)) return null;

        long capturedAt = prefs.getLong(KEY_CAPTURED_AT, -1L);
        long age = nowMillis - capturedAt;
        if (capturedAt <= 0L || age < -MAX_FUTURE_SKEW_MS || age > MAX_AGE_MS) return null;

        ProcessBudgetImport.Payload payload = new ProcessBudgetImport.Payload(
                prefs.getInt(KEY_OVERRIDE, ProcessBudgetImport.UNKNOWN),
                prefs.getInt(KEY_CUSTOMIZED, ProcessBudgetImport.UNKNOWN),
                prefs.getInt(KEY_PLATFORM, ProcessBudgetImport.UNKNOWN),
                prefs.getInt(KEY_CURRENT, ProcessBudgetImport.UNKNOWN));
        return payload.isValid() ? new Stored(payload, capturedAt) : null;
    }

    /** Drop a previously imported snapshot before a change that can alter the process limit. */
    static void invalidate(Context context) {
        prefs(context).edit().clear().apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private ProcessBudgetImportStore() {}
}
