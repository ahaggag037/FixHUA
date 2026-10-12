package com.fixhua.guard;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.provider.Settings;

/** Small, explicit repair engine. It only mutates settings that can be verified afterwards. */
final class RepairEngine {
    enum Outcome {
        APPLIED,
        NEEDS_DEVELOPER_OPTIONS,
        NEEDS_SETTINGS,
        NOT_SUPPORTED,
        FAILED
    }

    record Result(Outcome outcome, String message) {}

    static boolean hasWriteSecureSettings(Context context) {
        return context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
                == PackageManager.PERMISSION_GRANTED;
    }

    static Result applyDirect(Context context, ReadinessEngine.Finding finding) {
        if (finding == null) return new Result(Outcome.NOT_SUPPORTED, "لا توجد مشكلة قابلة للإصلاح.");
        try {
            switch (finding.code) {
                case "ALWAYS_FINISH_ACTIVITIES":
                    if (!hasWriteSecureSettings(context)) {
                        return new Result(
                                Outcome.NEEDS_DEVELOPER_OPTIONS,
                                "إيقاف Don't keep activities يحتاج Developer options أو صلاحية WRITE_SECURE_SETTINGS.");
                    }
                    if (!Settings.Global.putInt(
                            context.getContentResolver(),
                            Settings.Global.ALWAYS_FINISH_ACTIVITIES,
                            0)) {
                        return new Result(Outcome.FAILED, "رفض النظام تغيير Don't keep activities.");
                    }
                    return new Result(Outcome.APPLIED, "تم طلب إيقاف Don't keep activities.");

                case "TIME_SETTINGS":
                    if (!hasWriteSecureSettings(context)) {
                        return new Result(
                                Outcome.NEEDS_SETTINGS,
                                "الوقت والمنطقة الزمنية التلقائية يحتاجان صفحة إعدادات النظام في الوضع العادي.");
                    }
                    boolean time = Settings.Global.putInt(
                            context.getContentResolver(), Settings.Global.AUTO_TIME, 1);
                    boolean zone = Settings.Global.putInt(
                            context.getContentResolver(), Settings.Global.AUTO_TIME_ZONE, 1);
                    if (!time || !zone) {
                        return new Result(Outcome.FAILED, "لم يقبل النظام أحد تغييري الوقت التلقائي.");
                    }
                    return new Result(Outcome.APPLIED, "تم تفعيل الوقت والمنطقة الزمنية التلقائية.");

                case "DEV_PROCESS_LIMIT_OVERRIDE":
                    return new Result(
                            Outcome.NEEDS_DEVELOPER_OPTIONS,
                            "حد عمليات الخلفية محمي بخدمة ActivityManager؛ يجب تغييره من Developer options إلى Standard limit.");

                case "POWER_SAVE":
                case "GBOX_BATTERY_OPTIMIZED":
                case "LOW_STORAGE":
                case "NETWORK_UNVALIDATED":
                case "GBOX_MISSING":
                    return new Result(Outcome.NEEDS_SETTINGS, "هذه المشكلة تحتاج إعداد النظام المناسب ثم إعادة التحقق.");

                default:
                    return new Result(Outcome.NOT_SUPPORTED, "لا يوجد تغيير تلقائي آمن لهذه المشكلة.");
            }
        } catch (RuntimeException failure) {
            return new Result(
                    Outcome.FAILED,
                    failure.getClass().getSimpleName() + ": " + String.valueOf(failure.getMessage()));
        }
    }

    static boolean isResolved(
            String findingCode,
            SystemSnapshot snapshot,
            ReadinessEngine.Result result) {
        if (findingCode == null || snapshot == null || result == null) return false;
        if ("DEV_PROCESS_LIMIT_OVERRIDE".equals(findingCode)) {
            return snapshot.privilegedProcessProbeSuccess
                    && snapshot.currentMaxCachedProcesses > 0
                    && snapshot.referenceMaxCachedProcesses > 0
                    && snapshot.currentMaxCachedProcesses >= snapshot.referenceMaxCachedProcesses;
        }
        for (ReadinessEngine.Finding finding : result.findings) {
            if (findingCode.equals(finding.code)) return false;
        }
        return true;
    }

    private RepairEngine() {}
}
