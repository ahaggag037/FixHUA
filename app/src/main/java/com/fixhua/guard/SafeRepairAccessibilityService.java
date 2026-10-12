package com.fixhua.guard;

import android.accessibilityservice.AccessibilityService;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.Locale;

/**
 * Narrow, user-enabled helper for two Developer options repairs only.
 * It is dormant unless OptimizerActivity has queued a specific repair request.
 */
public final class SafeRepairAccessibilityService extends AccessibilityService {
    private static final String PREFS = "fixhua_safe_repair";
    private static final String KEY_REQUEST = "request";
    private static final String KEY_PHASE = "phase";
    private static final String KEY_REQUESTED_AT = "requested_at";
    private static final String KEY_OUTCOME = "outcome";
    private static final long REQUEST_TIMEOUT_MS = 120_000L;

    private static final String[] PROCESS_LABELS = {
            "background process limit",
            "حد عمليات الخلفية",
            "حد العمليات في الخلفية"
    };
    private static final String[] STANDARD_LABELS = {
            "standard limit",
            "الحد القياسي",
            "الحد القياسي للعمليات"
    };
    private static final String[] KEEP_ACTIVITIES_LABELS = {
            "don't keep activities",
            "عدم الاحتفاظ بالأنشطة",
            "لا تحتفظ بالأنشطة"
    };

    static boolean isEnabled(Context context) {
        String enabled = Settings.Secure.getString(
                context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) return false;
        ComponentName mine = new ComponentName(context, SafeRepairAccessibilityService.class);
        String flattened = mine.flattenToString();
        String flattenedShort = mine.flattenToShortString();
        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(enabled);
        while (splitter.hasNext()) {
            String component = splitter.next();
            if (flattened.equalsIgnoreCase(component) || flattenedShort.equalsIgnoreCase(component)) {
                return true;
            }
        }
        return false;
    }

    static void request(Context context, String findingCode) {
        prefs(context).edit()
                .putString(KEY_REQUEST, findingCode)
                .putInt(KEY_PHASE, 0)
                .putLong(KEY_REQUESTED_AT, System.currentTimeMillis())
                .remove(KEY_OUTCOME)
                .apply();
    }

    static String consumeOutcome(Context context) {
        SharedPreferences p = prefs(context);
        String value = p.getString(KEY_OUTCOME, null);
        if (value != null) p.edit().remove(KEY_OUTCOME).apply();
        return value;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        SharedPreferences p = prefs(this);
        String request = p.getString(KEY_REQUEST, null);
        if (request == null || request.isBlank()) return;

        long requestedAt = p.getLong(KEY_REQUESTED_AT, 0L);
        if (requestedAt <= 0L || System.currentTimeMillis() - requestedAt > REQUEST_TIMEOUT_MS) {
            finish("TIMEOUT");
            return;
        }

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        try {
            if ("DEV_PROCESS_LIMIT_OVERRIDE".equals(request)) {
                handleProcessLimit(root, p.getInt(KEY_PHASE, 0));
            } else if ("ALWAYS_FINISH_ACTIVITIES".equals(request)) {
                handleKeepActivities(root);
            } else {
                finish("UNSUPPORTED_REQUEST");
            }
        } finally {
            root.recycle();
        }
    }

    private void handleProcessLimit(AccessibilityNodeInfo root, int phase) {
        if (phase == 0) {
            AccessibilityNodeInfo label = findByAnyText(root, PROCESS_LABELS);
            if (label != null) {
                try {
                    if (clickNodeOrClickableParent(label)) {
                        prefs(this).edit().putInt(KEY_PHASE, 1).apply();
                    }
                } finally {
                    label.recycle();
                }
            }
            return;
        }

        AccessibilityNodeInfo standard = findByAnyText(root, STANDARD_LABELS);
        if (standard != null) {
            try {
                if (clickNodeOrClickableParent(standard)) {
                    finish("PROCESS_LIMIT_STANDARD_SELECTED");
                }
            } finally {
                standard.recycle();
            }
        }
    }

    private void handleKeepActivities(AccessibilityNodeInfo root) {
        AccessibilityNodeInfo label = findByAnyText(root, KEEP_ACTIVITIES_LABELS);
        if (label == null) return;
        try {
            AccessibilityNodeInfo toggle = findCheckableNear(label);
            if (toggle == null) return;
            try {
                if (!toggle.isChecked()) {
                    finish("ALWAYS_FINISH_ALREADY_OFF");
                    return;
                }
                if (clickNodeOrClickableParent(toggle)) {
                    finish("ALWAYS_FINISH_TOGGLED_OFF");
                }
            } finally {
                toggle.recycle();
            }
        } finally {
            label.recycle();
        }
    }

    private void finish(String outcome) {
        prefs(this).edit()
                .remove(KEY_REQUEST)
                .remove(KEY_PHASE)
                .remove(KEY_REQUESTED_AT)
                .putString(KEY_OUTCOME, outcome)
                .apply();
    }

    private static AccessibilityNodeInfo findByAnyText(AccessibilityNodeInfo root, String[] needles) {
        if (root == null) return null;
        CharSequence text = root.getText();
        CharSequence desc = root.getContentDescription();
        if (matches(text, needles) || matches(desc, needles)) {
            return AccessibilityNodeInfo.obtain(root);
        }
        for (int i = 0; i < root.getChildCount(); i++) {
            AccessibilityNodeInfo child = root.getChild(i);
            if (child == null) continue;
            try {
                AccessibilityNodeInfo found = findByAnyText(child, needles);
                if (found != null) return found;
            } finally {
                child.recycle();
            }
        }
        return null;
    }

    private static boolean matches(CharSequence value, String[] needles) {
        if (value == null) return false;
        String lower = value.toString().trim().toLowerCase(Locale.ROOT);
        for (String needle : needles) {
            if (lower.contains(needle.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private static AccessibilityNodeInfo findCheckableNear(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo cursor = AccessibilityNodeInfo.obtain(node);
        for (int depth = 0; depth < 4 && cursor != null; depth++) {
            AccessibilityNodeInfo checkable = findCheckableDescendant(cursor, 0);
            if (checkable != null) {
                cursor.recycle();
                return checkable;
            }
            AccessibilityNodeInfo parent = cursor.getParent();
            cursor.recycle();
            cursor = parent;
        }
        if (cursor != null) cursor.recycle();
        return null;
    }

    private static AccessibilityNodeInfo findCheckableDescendant(AccessibilityNodeInfo node, int depth) {
        if (node == null || depth > 3) return null;
        if (node.isCheckable()) return AccessibilityNodeInfo.obtain(node);
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            try {
                AccessibilityNodeInfo found = findCheckableDescendant(child, depth + 1);
                if (found != null) return found;
            } finally {
                child.recycle();
            }
        }
        return null;
    }

    private static boolean clickNodeOrClickableParent(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo cursor = AccessibilityNodeInfo.obtain(node);
        for (int depth = 0; depth < 5 && cursor != null; depth++) {
            if (cursor.isClickable() && cursor.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                cursor.recycle();
                return true;
            }
            AccessibilityNodeInfo parent = cursor.getParent();
            cursor.recycle();
            cursor = parent;
        }
        if (cursor != null) cursor.recycle();
        return false;
    }

    @Override
    public void onInterrupt() {
        // No persistent action is required when Android interrupts accessibility delivery.
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
