package com.fixhua.guard;

import android.os.PowerManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class ReadinessEngine {
    enum Severity { BLOCKER, WARNING, INFO }
    enum Status { READY, ATTENTION, BLOCKED }
    enum Action {
        NONE,
        OPEN_APP_GALLERY,
        OPEN_GBOX_DETAILS,
        OPEN_HUAWEI_APP_LAUNCH,
        OPEN_BATTERY_OPTIMIZATION,
        OPEN_BATTERY_SETTINGS,
        OPEN_DEVELOPER_OPTIONS,
        OPEN_STORAGE_SETTINGS,
        OPEN_WIRELESS_SETTINGS,
        OPEN_DATE_SETTINGS
    }

    static final class Finding {
        final String code;
        final Severity severity;
        final String message;
        final Action action;

        Finding(String code, Severity severity, String message) {
            this(code, severity, message, Action.NONE);
        }

        Finding(String code, Severity severity, String message, Action action) {
            this.code = code;
            this.severity = severity;
            this.message = message;
            this.action = action == null ? Action.NONE : action;
        }

        boolean isActionable() {
            return action != Action.NONE;
        }
    }

    static final class Result {
        final Status status;
        final List<Finding> findings;

        Result(Status status, List<Finding> findings) {
            this.status = status;
            this.findings = Collections.unmodifiableList(new ArrayList<>(findings));
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
                if ((finding.severity == Severity.BLOCKER || finding.severity == Severity.WARNING)
                        && finding.isActionable()) {
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
                    "GBox غير مثبت. افتح AppGallery وابحث عن GBox أو ثبّته من مصدره الموثوق.",
                    Action.OPEN_APP_GALLERY
            ));
        } else {
            if (!s.gboxEnabled) {
                findings.add(new Finding(
                        "GBOX_DISABLED",
                        Severity.BLOCKER,
                        "GBox مثبت لكنه معطّل على النظام. افتح معلومات التطبيق وتأكد أنه مفعّل.",
                        Action.OPEN_GBOX_DETAILS
                ));
            } else if (!s.gboxLaunchable) {
                findings.add(new Finding(
                        "GBOX_NOT_LAUNCHABLE",
                        Severity.BLOCKER,
                        "GBox ظاهر كحزمة مثبتة لكن النظام لا يعرض نشاط تشغيل صالحًا له.",
                        Action.OPEN_GBOX_DETAILS
                ));
            }
            if (s.gboxSuspended) {
                findings.add(new Finding(
                        "GBOX_SUSPENDED",
                        Severity.WARNING,
                        "GBox معلّق بواسطة النظام أو سياسة إدارة التطبيقات.",
                        Action.OPEN_GBOX_DETAILS
                ));
            }
        }

        if (s.alwaysFinishActivities == 1) {
            findings.add(new Finding(
                    "ALWAYS_FINISH_ACTIVITIES",
                    Severity.WARNING,
                    "خيار Don't keep activities مفعّل. أندرويد سيغلق الأنشطة بقوة وقد يسبب خروجًا متكررًا.",
                    Action.OPEN_DEVELOPER_OPTIONS
            ));
        }

        if (s.powerSaveMode) {
            findings.add(new Finding(
                    "POWER_SAVE",
                    Severity.WARNING,
                    "وضع توفير الطاقة مفعّل وقد يقيّد GBox وخدماته في الخلفية.",
                    Action.OPEN_BATTERY_SETTINGS
            ));
        }

        if (s.gboxInstalled && !s.gboxBatteryExempt) {
            findings.add(new Finding(
                    "GBOX_BATTERY_OPTIMIZED",
                    Severity.WARNING,
                    s.huaweiEnvironment
                            ? "GBox ليس مستثنى من تحسين البطارية. على Huawei راجع أيضًا App launch / التشغيل في الخلفية؛ الاستثناء وحده لا يضمن الاستمرارية."
                            : "GBox ليس مستثنى من تحسين البطارية وقد يتأثر عند العمل في الخلفية.",
                    s.huaweiEnvironment ? Action.OPEN_HUAWEI_APP_LAUNCH : Action.OPEN_BATTERY_OPTIMIZATION
            ));
        }

        if (s.networkObserved) {
            if (!s.networkPresent) {
                findings.add(new Finding(
                        "NETWORK_OFFLINE",
                        Severity.WARNING,
                        "لا توجد شبكة نشطة حاليًا. GBox وتسجيل الدخول لن يعملا بصورة طبيعية بدون اتصال.",
                        Action.OPEN_WIRELESS_SETTINGS
                ));
            } else if (!s.networkInternetCapable) {
                findings.add(new Finding(
                        "NETWORK_NO_INTERNET",
                        Severity.WARNING,
                        "الشبكة موجودة لكن أندرويد لا يراها قادرة على الوصول للإنترنت.",
                        Action.OPEN_WIRELESS_SETTINGS
                ));
            } else if (!s.networkValidated) {
                findings.add(new Finding(
                        "NETWORK_UNVALIDATED",
                        Severity.WARNING,
                        "الشبكة موجودة لكن التحقق من الإنترنت لم يكتمل. قد يكون السبب بوابة تسجيل دخول أو DNS/VPN أو اتصالًا متقطعًا.",
                        Action.OPEN_WIRELESS_SETTINGS
                ));
            }
        } else {
            findings.add(new Finding(
                    "NETWORK_UNKNOWN",
                    Severity.INFO,
                    "تعذر قراءة حالة الشبكة بشكل موثوق؛ FixHUA لن يفترض أنها معطلة."
            ));
        }

        if (s.networkCaptivePortal) {
            findings.add(new Finding(
                    "CAPTIVE_PORTAL",
                    Severity.INFO,
                    "النظام يشير إلى بوابة تسجيل دخول للشبكة؛ أكمل تسجيل الشبكة قبل تشخيص GBox."
            ));
        }

        if (s.lowMemory) {
            findings.add(new Finding(
                    "MEMORY_PRESSURE",
                    Severity.WARNING,
                    "أندرويد نفسه أبلغ عن ضغط ذاكرة منخفضة. أغلق الأحمال الثقيلة غير الضرورية ثم أعد التجربة."
            ));
        } else if (s.totalRamMb > 0 && s.availableRamFraction() < 0.05) {
            findings.add(new Finding(
                    "RAM_BUSY",
                    Severity.INFO,
                    "RAM الحرة قليلة، لكن أندرويد لم يعلن low-memory. هذا وحده لا يثبت وجود مشكلة لأن النظام يستخدم RAM كذاكرة مؤقتة."
            ));
        }

        if (s.lowRamDevice) {
            findings.add(new Finding(
                    "LOW_RAM_DEVICE",
                    Severity.INFO,
                    "النظام يصنّف الجهاز Low-RAM؛ تشغيل حاوية تطبيقات قد يكون أكثر حساسية للضغط."
            ));
        }

        if (s.totalStorageMb > 0) {
            if (s.freeStorageMb <= 4096 || s.freeStorageFraction() <= 0.10) {
                findings.add(new Finding(
                        "LOW_STORAGE",
                        Severity.WARNING,
                        "المساحة الحرة حرجة تقريبًا (10٪ أو أقل، أو قرابة 4GB أو أقل). حرّر مساحة ثم أعد الفحص.",
                        Action.OPEN_STORAGE_SETTINGS
                ));
            } else if (s.freeStorageMb <= 8192 || s.freeStorageFraction() <= 0.20) {
                findings.add(new Finding(
                        "STORAGE_TIGHT",
                        Severity.INFO,
                        "المساحة الحرة محدودة. ليست عطلًا بحد ذاتها، لكنها قد تزيد مشاكل التحديث والكاش."
                ));
            }
        }

        if (s.thermalStatus >= PowerManager.THERMAL_STATUS_SEVERE) {
            findings.add(new Finding(
                    "THERMAL_PRESSURE",
                    Severity.WARNING,
                    "النظام في ضغط حراري مرتفع؛ الأداء قد ينخفض تلقائيًا حتى يبرد الهاتف."
            ));
        } else if (s.thermalStatus >= PowerManager.THERMAL_STATUS_MODERATE) {
            findings.add(new Finding(
                    "THERMAL_WARM",
                    Severity.INFO,
                    "الهاتف دافئ والنظام بدأ يلاحظ ضغطًا حراريًا. قارن الأداء بعد أن يبرد الجهاز."
            ));
        }

        if (!Float.isNaN(s.batteryTempC) && s.batteryTempC >= 45f
                && s.thermalStatus < PowerManager.THERMAL_STATUS_SEVERE) {
            findings.add(new Finding(
                    "BATTERY_HOT",
                    Severity.INFO,
                    "حرارة البطارية مرتفعة نسبيًا؛ لا تعتبرها FixHUA سببًا مؤكدًا وحدها."
            ));
        }

        if (s.deviceIdleMode) {
            findings.add(new Finding(
                    "DEVICE_IDLE",
                    Severity.INFO,
                    "الجهاز في وضع Doze/Idle؛ قد تتأخر بعض الأعمال الخلفية حتى يستيقظ الجهاز."
            ));
        }

        if (s.autoTime == 0 || s.autoTimeZone == 0) {
            findings.add(new Finding(
                    "TIME_SETTINGS",
                    Severity.WARNING,
                    "الوقت أو المنطقة الزمنية التلقائية غير مفعلة؛ وقت غير صحيح قد يسبب مشاكل تسجيل دخول واتصال مشفر.",
                    Action.OPEN_DATE_SETTINGS
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
                    "microG غير ظاهر على النظام المضيف. هذا لا يمنع وضع GBox الحاوي لأن GBox قد يستخدم بيئته الداخلية."
            ));
        }

        if (s.identityConflict) {
            findings.add(new Finding(
                    "DEVICE_IDENTITY_MIXED",
                    Severity.INFO,
                    "توجد مكونات Huawei لكن هوية Build لا تبدو Huawei/Honor. FixHUA يسجل ذلك كتوافق مختلط ولا يغيّر هوية الجهاز."
            ));
        }

        if (s.networkVpn) {
            findings.add(new Finding(
                    "VPN_ACTIVE",
                    Severity.INFO,
                    "يوجد VPN نشط. هذا ليس خطأ بحد ذاته، لكنه عامل مهم عند تشخيص تسجيل الدخول أو بطء خدمات Google."
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
