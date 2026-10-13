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
        SystemSnapshot.EnvironmentProfile profile = s.environmentProfile();

        // GBox is optional. A Huawei phone with a native Google stack must never be
        // blocked merely because GBox is absent.
        if (!s.networkInternetCapable || !s.networkValidated) {
            findings.add(new Finding(
                    "NETWORK_UNVALIDATED",
                    Severity.WARNING,
                    "اتصال الإنترنت غير مُتحقق حاليًا، وقد يؤثر ذلك على التطبيقات والخدمات المتصلة."
            ));
        }

        if (s.privilegedProcessProbeSuccess
                && s.currentMaxCachedProcesses > 0
                && s.referenceMaxCachedProcesses > 0
                && s.currentMaxCachedProcesses < s.referenceMaxCachedProcesses) {
            findings.add(new Finding(
                    "DEV_PROCESS_LIMIT_OVERRIDE",
                    Severity.WARNING,
                    "حد العمليات في الخلفية مضبوط على " + s.currentMaxCachedProcesses
                            + " بينما مرجع النظام/الجهاز هو " + s.referenceMaxCachedProcesses
                            + ". أعد Background process limit إلى Standard limit لتحسين تعدد المهام."
            ));
        }

        if (s.alwaysFinishActivities == 1) {
            findings.add(new Finding(
                    "ALWAYS_FINISH_ACTIVITIES",
                    Severity.WARNING,
                    "خيار Don't keep activities مفعّل. هذا يقتل الأنشطة بقوة ويضر بثبات التطبيقات وتعدد المهام."
            ));
        }

        if (s.powerSaveMode) {
            findings.add(new Finding(
                    "POWER_SAVE",
                    Severity.WARNING,
                    "وضع توفير الطاقة مفعّل وقد يقيّد الأداء والعمل في الخلفية."
            ));
        }

        if (profile == SystemSnapshot.EnvironmentProfile.GBOX_HOST && !s.gboxBatteryExempt) {
            findings.add(new Finding(
                    "GBOX_BATTERY_OPTIMIZED",
                    Severity.WARNING,
                    "GBox مثبت لكنه غير موجود في قائمة استثناءات تحسين البطارية."
            ));
        }

        if (s.lowMemory || s.availableRamFraction() < 0.10) {
            findings.add(new Finding(
                    "MEMORY_PRESSURE",
                    Severity.WARNING,
                    "الذاكرة المتاحة منخفضة جدًا الآن؛ قد يزيد ذلك إعادة تحميل التطبيقات والتقطيع."
            ));
        }

        if (s.totalStorageMb > 0 && s.freeStorageFraction() < 0.20) {
            findings.add(new Finding(
                    "LOW_STORAGE",
                    Severity.WARNING,
                    "المساحة الحرة أقل من 20٪؛ انخفاض المساحة الحرة كثيرًا قد يضر بالأداء والاستقرار."
            ));
        }

        if (s.thermalStatusSupported && s.thermalStatus >= PowerManager.THERMAL_STATUS_SEVERE) {
            findings.add(new Finding(
                    "THERMAL_PRESSURE",
                    Severity.WARNING,
                    "النظام في ضغط حراري مرتفع؛ الأداء قد ينخفض تلقائيًا حتى يبرد الهاتف."
            ));
        } else if (s.batteryTempC >= 45.0f) {
            findings.add(new Finding(
                    "BATTERY_HOT",
                    Severity.WARNING,
                    "حرارة البطارية مرتفعة (" + String.format(java.util.Locale.US, "%.1f", s.batteryTempC)
                            + "°C)، وقد يبدأ الجهاز بخفض الأداء حراريًا."
            ));
        }

        if (s.autoTime == 0 || s.autoTimeZone == 0) {
            findings.add(new Finding(
                    "TIME_SETTINGS",
                    Severity.WARNING,
                    "الوقت أو المنطقة الزمنية التلقائية غير مفعلة؛ الوقت الخاطئ قد يسبب مشاكل اتصال وتسجيل دخول."
            ));
        }

        if (s.webViewPackage == null || s.webViewPackage.isEmpty() || "unavailable".equals(s.webViewPackage)) {
            findings.add(new Finding(
                    "WEBVIEW_MISSING",
                    Severity.WARNING,
                    "لم يتم العثور على مزود WebView صالح، وقد تتعطل الشاشات وصفحات تسجيل الدخول المدمجة."
            ));
        }

        if (s.dumpPermissionGranted && !s.privilegedProcessProbeSuccess) {
            findings.add(new Finding(
                    "PROCESS_BUDGET_PROBE_FAILED",
                    Severity.INFO,
                    "صلاحية الفحص المتقدم موجودة، لكن قراءة ميزانية العمليات لم تنجح في هذا الفحص ("
                            + s.privilegedProcessProbeFailure + "). لم يفترض FixHUA قيمة بديلة."
            ));
        }

        boolean partialGoogleStack = s.microgInstalled != s.playStoreInstalled;
        if (partialGoogleStack) {
            findings.add(new Finding(
                    "GOOGLE_STACK_PARTIAL",
                    Severity.INFO,
                    "تم اكتشاف جزء فقط من حزمة Google (Play services / Play Store). هذا ليس عطلًا مؤكدًا لكنه معلومة تشخيصية مهمة."
            ));
        }

        if (s.identityConflict) {
            findings.add(new Finding(
                    "DEVICE_IDENTITY_MIXED",
                    Severity.INFO,
                    "بيئة Huawei موجودة، لكن هوية Build الظاهرة لا تبدو Huawei/Honor. هذا تنبيه توافق فقط."
            ));
        }

        if (s.networkVpn) {
            findings.add(new Finding(
                    "VPN_ACTIVE",
                    Severity.INFO,
                    "يوجد VPN نشط. هذا ليس خطأ بحد ذاته لكنه عامل مهم في تشخيص الشبكة."
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
