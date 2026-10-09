package com.fixhua.guard;

import android.os.PowerManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class ReadinessEngine {
    enum Severity { BLOCKER, WARNING, INFO }
    enum Status { READY, ATTENTION, BLOCKED }

    static final class Finding {
        final String code;
        final Severity severity;
        final String message;

        Finding(String code, Severity severity, String message) {
            this.code = code;
            this.severity = severity;
            this.message = message;
        }
    }

    static final class Result {
        final Status status;
        final List<Finding> findings;

        Result(Status status, List<Finding> findings) {
            this.status = status;
            this.findings = Collections.unmodifiableList(findings);
        }

        int count(Severity severity) {
            int count = 0;
            for (Finding finding : findings) {
                if (finding.severity == severity) count++;
            }
            return count;
        }

        Finding firstActionable() {
            for (Finding finding : findings) {
                if (finding.severity == Severity.BLOCKER || finding.severity == Severity.WARNING) {
                    return finding;
                }
            }
            return null;
        }
    }

    static Result evaluate(SystemSnapshot s) {
        List<Finding> findings = new ArrayList<>();

        if (!s.gboxInstalled) {
            findings.add(new Finding(
                    "GBOX_MISSING",
                    Severity.BLOCKER,
                    "GBox غير مثبت، لذلك FixHUA لا يستطيع تشغيله أو مراجعة إعداداته."
            ));
        }

        if (!s.networkPresent || !s.networkInternetCapable || !s.networkValidated) {
            findings.add(new Finding(
                    "NETWORK_UNVALIDATED",
                    Severity.WARNING,
                    "اتصال الإنترنت غير مُتحقق حاليًا. هذا قد يسبب بطء بدء GMS أو فشل تسجيل الدخول."
            ));
        }

        if (s.alwaysFinishActivities == 1) {
            findings.add(new Finding(
                    "ALWAYS_FINISH_ACTIVITIES",
                    Severity.WARNING,
                    "خيار Don't keep activities مفعّل. أندرويد سيغلق الأنشطة بقوة ويمكن أن يسبب خروجًا متكررًا."
            ));
        }

        if (s.powerSaveMode) {
            findings.add(new Finding(
                    "POWER_SAVE",
                    Severity.WARNING,
                    "وضع توفير الطاقة مفعّل وقد يقيّد عمل GBox وmicroG في الخلفية."
            ));
        }

        if (s.gboxInstalled && !s.gboxBatteryExempt) {
            findings.add(new Finding(
                    "GBOX_BATTERY_OPTIMIZED",
                    Severity.WARNING,
                    "GBox غير موجود في قائمة استثناءات تحسين البطارية."
            ));
        }

        if (s.lowMemory || s.availableRamFraction() < 0.10) {
            findings.add(new Finding(
                    "MEMORY_PRESSURE",
                    Severity.WARNING,
                    "الذاكرة المتاحة منخفضة جدًا الآن. لا ينصح بتشغيل بيئة افتراضية ثقيلة قبل تحسن الضغط."
            ));
        }

        if (s.totalStorageMb > 0 && s.freeStorageFraction() < 0.20) {
            findings.add(new Finding(
                    "LOW_STORAGE",
                    Severity.WARNING,
                    "المساحة الحرة أقل من 20٪. أجهزة Huawei قد تصبح أقل استقرارًا عندما تقل المساحة الحرة كثيرًا."
            ));
        }

        if (s.thermalStatus >= PowerManager.THERMAL_STATUS_SEVERE) {
            findings.add(new Finding(
                    "THERMAL_PRESSURE",
                    Severity.WARNING,
                    "النظام في ضغط حراري مرتفع؛ الأداء قد ينخفض تلقائيًا حتى يبرد الهاتف."
            ));
        }

        if (s.autoTime == 0 || s.autoTimeZone == 0) {
            findings.add(new Finding(
                    "TIME_SETTINGS",
                    Severity.WARNING,
                    "الوقت أو المنطقة الزمنية التلقائية غير مفعلة؛ ضبط وقت غير صحيح قد يسبب مشاكل تسجيل دخول واتصال مشفر."
            ));
        }

        if (s.webViewPackage == null || s.webViewPackage.isEmpty() || "unavailable".equals(s.webViewPackage)) {
            findings.add(new Finding(
                    "WEBVIEW_MISSING",
                    Severity.WARNING,
                    "لم يتم العثور على مزود WebView صالح. بعض صفحات تسجيل الدخول والشاشات المدمجة قد تفشل."
            ));
        }

        if (!s.microgInstalled) {
            findings.add(new Finding(
                    "MICROG_MISSING",
                    Severity.INFO,
                    "microG غير ظاهر على الجهاز الحقيقي. GBox قد يستخدم بيئته الداخلية، لكن وضع التثبيت على الجهاز الحقيقي يحتاج microG على الأجهزة المدعومة."
            ));
        }

        if (s.identityConflict) {
            findings.add(new Finding(
                    "DEVICE_IDENTITY_MIXED",
                    Severity.INFO,
                    "بيئة Huawei موجودة، لكن هوية Build الظاهرة لا تبدو Huawei/Honor. هذا مجرد تنبيه توافق ولا يقوم FixHUA بتغيير هوية الجهاز."
            ));
        }

        if (s.networkVpn) {
            findings.add(new Finding(
                    "VPN_ACTIVE",
                    Severity.INFO,
                    "يوجد VPN نشط. هذا ليس خطأ بحد ذاته، لكنه عامل مهم عند تشخيص تسجيل الدخول أو بطء GMS."
            ));
        }

        boolean blocker = false;
        boolean warning = false;
        for (Finding finding : findings) {
            blocker |= finding.severity == Severity.BLOCKER;
            warning |= finding.severity == Severity.WARNING;
        }

        Status status = blocker ? Status.BLOCKED : warning ? Status.ATTENTION : Status.READY;
        return new Result(status, findings);
    }

    private ReadinessEngine() {}
}
