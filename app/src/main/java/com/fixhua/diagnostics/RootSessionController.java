package com.fixhua.diagnostics;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Conservative root-only session helper for GBox.
 *
 * Scope is intentionally narrow: temporary Android lifecycle exemptions that are
 * reversible at the end of the explicit user-started session. It does not touch
 * thermal protections, kernel frequency limits, LMKD configuration, device
 * identity, Play Integrity, DRM, or expose a general root shell endpoint.
 */
final class RootSessionController {
    private static final String GBOX = "com.gbox.android";
    private static final long RECHECK_MS = 30_000L;

    private final Context context;
    private boolean probed;
    private boolean rootAvailable;
    private boolean snapshotTaken;
    private boolean initiallyWhitelisted;
    private boolean whitelistAddedByUs;
    private String initialStandbyBucket;
    private boolean applied;
    private long lastTickAt;

    RootSessionController(Context context) {
        this.context = context.getApplicationContext();
    }

    static boolean probeAndStore(Context context) {
        boolean root = RootShell.hasRoot();
        SharedPreferences p = context.getSharedPreferences(GuardService.PREFS, Context.MODE_PRIVATE);
        p.edit()
                .putBoolean("root_available", root)
                .putString("root_guard_status", root ? "Root متاح ✓" : "Root غير متاح")
                .apply();
        return root;
    }

    static String lastStatus(Context context) {
        SharedPreferences p = context.getSharedPreferences(GuardService.PREFS, Context.MODE_PRIVATE);
        return p.getString("root_guard_status", "لم يتم فحص Root بعد");
    }

    void tick(boolean protectWindow) {
        long now = System.currentTimeMillis();
        if (now - lastTickAt < RECHECK_MS && protectWindow == applied) return;
        lastTickAt = now;

        if (!probed) {
            rootAvailable = probeAndStore(context);
            probed = true;
        }
        if (!rootAvailable) {
            saveStatus("Root غير متاح — الحماية العادية فقط");
            return;
        }

        if (protectWindow) {
            ensureApplied();
        } else if (applied) {
            restore();
        } else {
            saveStatus("Root جاهز • في انتظار جلسة GBox");
        }
    }

    void close() {
        if (rootAvailable && applied) restore();
    }

    private void ensureSnapshot() {
        if (snapshotTaken) return;
        RootShell.Result whitelist = RootShell.run("cmd deviceidle whitelist", 2500L);
        initiallyWhitelisted = whitelist.ok() && whitelist.output.contains(GBOX);

        RootShell.Result bucket = RootShell.run("am get-standby-bucket " + GBOX, 2500L);
        String parsed = bucket.ok() ? bucket.output.trim() : "";
        initialStandbyBucket = validBucket(parsed) ? parsed : null;
        snapshotTaken = true;
    }

    private void ensureApplied() {
        ensureSnapshot();

        boolean whitelistOk = initiallyWhitelisted;
        if (!initiallyWhitelisted && !whitelistAddedByUs) {
            RootShell.Result add = RootShell.run("cmd deviceidle whitelist +" + GBOX, 2500L);
            whitelistAddedByUs = add.ok();
            whitelistOk = whitelistAddedByUs;
        }

        RootShell.Result bucket = RootShell.run("am set-standby-bucket " + GBOX + " active", 2500L);
        applied = whitelistOk || bucket.ok();

        if (applied) {
            String parts = (whitelistOk ? "Doze whitelist" : "")
                    + (whitelistOk && bucket.ok() ? " + " : "")
                    + (bucket.ok() ? "standby=active" : "");
            saveStatus("Root Guard نشط ✓" + (parts.isEmpty() ? "" : " • " + parts));
        } else {
            saveStatus("Root متاح لكن لم تُطبق أي حماية Root");
        }
    }

    private void restore() {
        boolean bucketRestored = true;
        if (initialStandbyBucket != null) {
            bucketRestored = RootShell.run(
                    "am set-standby-bucket " + GBOX + " " + initialStandbyBucket,
                    2500L
            ).ok();
        }

        boolean whitelistRestored = true;
        if (whitelistAddedByUs && !initiallyWhitelisted) {
            whitelistRestored = RootShell.run("cmd deviceidle whitelist -" + GBOX, 2500L).ok();
        }

        if (bucketRestored && whitelistRestored) {
            saveStatus("Root Guard رجّع الإعدادات الأصلية ✓");
        } else {
            saveStatus("Root Guard توقف • راجع إعدادات الخلفية إذا لزم");
        }
        applied = false;
        whitelistAddedByUs = false;
        snapshotTaken = false;
        initialStandbyBucket = null;
    }

    private void saveStatus(String status) {
        context.getSharedPreferences(GuardService.PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean("root_available", rootAvailable)
                .putString("root_guard_status", status)
                .apply();
    }

    private static boolean validBucket(String value) {
        if (value == null || value.isEmpty()) return false;
        if (value.matches("[0-9]+")) return true;
        return value.equals("active")
                || value.equals("working_set")
                || value.equals("frequent")
                || value.equals("rare")
                || value.equals("restricted")
                || value.equals("never")
                || value.equals("exempted");
    }
}
