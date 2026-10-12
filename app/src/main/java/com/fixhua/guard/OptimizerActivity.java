package com.fixhua.guard;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Product-facing optimizer home. The older controller remains available as Advanced diagnostics. */
public final class OptimizerActivity extends Activity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicInteger generation = new AtomicInteger();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private TextView statusView;
    private TextView findingsView;
    private TextView actionView;
    private Button repairButton;
    private Button helperButton;

    private volatile SystemSnapshot lastSnapshot;
    private volatile ReadinessEngine.Result lastResult;
    private String pendingFindingCode;
    private boolean returningFromRepair;
    private boolean firstResume = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
        scan(false, false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (firstResume) {
            firstResume = false;
            return;
        }
        if (returningFromRepair) {
            returningFromRepair = false;
            mainHandler.postDelayed(() -> scan(true, false), 450L);
        } else {
            scan(false, false);
        }
    }

    @Override
    protected void onDestroy() {
        generation.incrementAndGet();
        executor.shutdownNow();
        super.onDestroy();
    }

    private View buildUi() {
        int pad = dp(20);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText("FixHUA Optimizer 2.1");
        title.setTextSize(28f);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText("تحسين استقرار GBox على Huawei — إصلاح + تحقق، وليس مجرد روابط إعدادات");
        subtitle.setTextSize(14f);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, dp(6), 0, dp(14));
        root.addView(subtitle, matchWrap());

        statusView = cardText(16f, true);
        statusView.setText("جاري فحص الجهاز…");
        root.addView(statusView, matchWrap());

        repairButton = button("فحص وإصلاح أهم مشكلة");
        repairButton.setOnClickListener(v -> smartRepair());
        root.addView(repairButton, matchWrapTop());

        Button verify = button("إعادة الفحص والتحقق الآن");
        verify.setOnClickListener(v -> scan(false, true));
        root.addView(verify, matchWrapTop());

        Button launch = button("فحص ثم تشغيل GBox");
        launch.setOnClickListener(v -> launchGboxWithFreshCheck());
        root.addView(launch, matchWrapTop());

        helperButton = button("تفعيل مساعد الإصلاح التلقائي");
        helperButton.setOnClickListener(v -> showHelperDialog());
        root.addView(helperButton, matchWrapTop());

        Button advanced = button("التشخيص المتقدم / Bug Report / Runtime");
        advanced.setOnClickListener(v -> startActivity(new Intent(this, MainActivity.class)));
        root.addView(advanced, matchWrapTop());

        actionView = cardText(15f, true);
        actionView.setText("سيعرض FixHUA هنا ما نفذه وما إذا تم التحقق منه.");
        actionView.setPadding(dp(12), dp(14), dp(12), dp(14));
        root.addView(actionView, matchWrapLargeTop());

        findingsView = cardText(15f, false);
        findingsView.setText("النتائج ستظهر بعد الفحص.");
        findingsView.setTextIsSelectable(true);
        root.addView(findingsView, matchWrapLargeTop());

        TextView note = new TextView(this);
        note.setText("الوضع العادي لا يغيّر إعدادات محمية بصمت. مساعد إمكانية الوصول ينفذ فقط إصلاحات Developer options التي طلبتها أنت، ثم يتوقف. الوضع المتقدم عبر ADB اختياري ويتيح تغييرات آمنة يمكن التحقق منها داخل التطبيق.");
        note.setTextSize(13f);
        note.setPadding(0, dp(16), 0, dp(24));
        root.addView(note, matchWrap());
        return scroll;
    }

    private void scan(boolean verifyPending, boolean userRequested) {
        final int token = generation.incrementAndGet();
        statusView.setText("جاري فحص الجهاز…");
        executor.execute(() -> {
            SystemSnapshot snapshot = SystemSnapshot.capture(getApplicationContext());
            ReadinessEngine.Result result = ReadinessEngine.evaluate(snapshot);
            String accessibilityOutcome = verifyPending
                    ? SafeRepairAccessibilityService.consumeOutcome(getApplicationContext())
                    : null;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || token != generation.get()) return;
                lastSnapshot = snapshot;
                lastResult = result;
                render(snapshot, result);
                if (verifyPending) verifyPendingRepair(snapshot, result, accessibilityOutcome);
                if (userRequested) Toast.makeText(this, "تم الفحص من جديد", Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void render(SystemSnapshot s, ReadinessEngine.Result result) {
        String status = result.status == ReadinessEngine.Status.READY ? "جاهز"
                : result.status == ReadinessEngine.Status.BLOCKED ? "غير جاهز" : "يحتاج ضبط";
        boolean helper = SafeRepairAccessibilityService.isEnabled(this);
        boolean secure = RepairEngine.hasWriteSecureSettings(this);

        StringBuilder summary = new StringBuilder();
        summary.append("الحالة: ").append(status).append('\n');
        summary.append("GBox: ").append(s.gboxInstalled ? s.gboxVersion : "غير مثبت").append('\n');
        summary.append("Huawei: ").append(s.huaweiEnvironment ? "مكتشفة" : "غير مؤكدة").append('\n');
        summary.append("الإنترنت: ").append(s.networkValidated ? "متحقق" : "غير متحقق").append('\n');
        summary.append("توفير الطاقة: ").append(s.powerSaveMode ? "مفعل" : "متوقف").append('\n');
        summary.append("RAM متاح: ").append(s.availableRamMb).append(" / ").append(s.totalRamMb).append(" MB").append('\n');
        if (s.privilegedProcessProbeSuccess) {
            summary.append("حد العمليات: ").append(s.currentMaxCachedProcesses)
                    .append(" / المرجع ").append(s.referenceMaxCachedProcesses).append('\n');
        }
        summary.append("مساعد الإصلاح: ").append(helper ? "فعال" : "غير مفعل").append('\n');
        summary.append("صلاحية الإصلاح المتقدم: ").append(secure ? "مفعلة" : "غير مفعلة");
        statusView.setText(summary.toString());

        StringBuilder findings = new StringBuilder();
        if (result.findings.isEmpty()) {
            findings.append("✓ لم يجد FixHUA مشكلة واضحة في الفحص الحالي.");
        } else {
            findings.append("المشاكل المرصودة:\n\n");
            int i = 1;
            for (ReadinessEngine.Finding finding : result.findings) {
                String marker = finding.severity == ReadinessEngine.Severity.BLOCKER ? "⛔"
                        : finding.severity == ReadinessEngine.Severity.WARNING ? "⚠" : "ℹ";
                findings.append(i++).append(". ").append(marker).append(' ')
                        .append(finding.message).append("\n\n");
            }
        }
        findingsView.setText(findings.toString());

        ReadinessEngine.Finding next = pickBestFinding(result);
        repairButton.setEnabled(next != null);
        repairButton.setText(next == null ? "لا توجد مشكلة تحتاج إصلاحًا" : "إصلاح: " + shortLabel(next.code));
        helperButton.setText(helper ? "مساعد الإصلاح التلقائي: فعال" : "تفعيل مساعد الإصلاح التلقائي");
    }

    private void smartRepair() {
        actionView.setText("أعيد الفحص قبل أي تغيير…");
        final int token = generation.incrementAndGet();
        executor.execute(() -> {
            SystemSnapshot snapshot = SystemSnapshot.capture(getApplicationContext());
            ReadinessEngine.Result result = ReadinessEngine.evaluate(snapshot);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || token != generation.get()) return;
                lastSnapshot = snapshot;
                lastResult = result;
                render(snapshot, result);
                ReadinessEngine.Finding finding = pickBestFinding(result);
                if (finding == null) {
                    actionView.setText("✓ لا يوجد إصلاح ضروري الآن حسب الفحص الحالي.");
                    return;
                }
                startRepair(snapshot, finding);
            });
        });
    }

    private void startRepair(SystemSnapshot before, ReadinessEngine.Finding finding) {
        if ("GBOX_MISSING".equals(finding.code)) {
            actionView.setText("⛔ GBox غير مثبت؛ لا يوجد تغيير محلي يستطيع FixHUA تنفيذه قبل تثبيته.");
            return;
        }

        RepairEngine.Result direct = RepairEngine.applyDirect(this, finding);
        switch (direct.outcome()) {
            case APPLIED:
                pendingFindingCode = finding.code;
                actionView.setText("تم تنفيذ التغيير. جاري التحقق من القيمة الجديدة…");
                mainHandler.postDelayed(() -> scan(true, false), 500L);
                break;

            case NEEDS_ACCESSIBILITY:
                if (SafeRepairAccessibilityService.isEnabled(this)) {
                    if ("DEV_PROCESS_LIMIT_OVERRIDE".equals(finding.code)) {
                        ProcessBudgetImportStore.invalidate(this);
                    }
                    pendingFindingCode = finding.code;
                    SafeRepairAccessibilityService.request(this, finding.code);
                    returningFromRepair = true;
                    actionView.setText("بدأ إصلاح " + shortLabel(finding.code)
                            + ". سينفذ المساعد التغيير داخل Developer options فقط، ثم سأتحقق عند العودة.");
                    SettingsNavigator.openDeveloperOptions(this);
                } else {
                    showAccessibilityRequired(finding.code);
                }
                break;

            case NEEDS_SETTINGS:
                pendingFindingCode = finding.code;
                returningFromRepair = true;
                actionView.setText("فتح FixHUA صفحة النظام المطلوبة. بعد التعديل والعودة سأعيد الفحص تلقائيًا.");
                SettingsNavigator.fixFinding(this, finding);
                break;

            case FAILED:
                actionView.setText("✗ فشل التغيير الآمن: " + direct.message());
                break;

            case NOT_SUPPORTED:
            default:
                actionView.setText("لا يوجد إصلاح تلقائي آمن لهذه الحالة. " + direct.message());
                break;
        }
    }

    private void verifyPendingRepair(
            SystemSnapshot snapshot,
            ReadinessEngine.Result result,
            String accessibilityOutcome) {
        String code = pendingFindingCode;
        if (code == null) return;
        pendingFindingCode = null;

        if (RepairEngine.isResolved(code, snapshot, result)) {
            actionView.setText("✓ تم تنفيذ إصلاح «" + shortLabel(code) + "» والتحقق من أن المشكلة لم تعد ظاهرة.");
            return;
        }

        if ("DEV_PROCESS_LIMIT_OVERRIDE".equals(code)
                && "PROCESS_LIMIT_STANDARD_SELECTED".equals(accessibilityOutcome)
                && !snapshot.privilegedProcessProbeSuccess) {
            actionView.setText("✓ تم اختيار Standard limit فعليًا داخل Developer options. "
                    + "تعذر فقط قراءة القيمة المحمية بعد التغيير؛ فعّل الفحص المتقدم إن أردت إثباتًا رقميًا من ActivityManager.");
            return;
        }

        if ("TIMEOUT".equals(accessibilityOutcome)) {
            actionView.setText("✗ لم يكتمل الإصلاح خلال المهلة. لم يعتبر FixHUA أي تغيير ناجحًا.");
            return;
        }

        actionView.setText("⚠ تم الرجوع من خطوة الإصلاح لكن الفحص لم يثبت زوال «"
                + shortLabel(code) + "». لم أسجلها كإصلاح ناجح.");
    }

    private void showAccessibilityRequired(String code) {
        new AlertDialog.Builder(this)
                .setTitle("مساعد الإصلاح غير مفعل")
                .setMessage("لإصلاح «" + shortLabel(code) + "» تلقائيًا بدون Root، فعّل خدمة FixHUA مرة واحدة في إمكانية الوصول. الخدمة لا تنقر أي شيء إلا بعد ضغطك على زر الإصلاح داخل FixHUA، ومهمتها محصورة في Developer options.")
                .setPositiveButton("فتح إمكانية الوصول", (d, w) -> {
                    returningFromRepair = false;
                    try {
                        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                    } catch (RuntimeException failure) {
                        Toast.makeText(this, "تعذر فتح إعدادات إمكانية الوصول", Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void showHelperDialog() {
        boolean enabled = SafeRepairAccessibilityService.isEnabled(this);
        String state = enabled ? "الخدمة مفعلة الآن." : "الخدمة غير مفعلة الآن.";
        new AlertDialog.Builder(this)
                .setTitle("مساعد FixHUA")
                .setMessage(state
                        + "\n\nإمكانية الوصول هي المسار بدون كمبيوتر لإعادة Background process limit إلى Standard limit وإيقاف Don't keep activities.\n\nيوجد أيضًا bootstrap اختياري عبر ADB لتفعيل WRITE_SECURE_SETTINGS وDUMP لإصلاحات مباشرة وقراءة أعمق.")
                .setPositiveButton("فتح إمكانية الوصول", (d, w) -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))
                .setNeutralButton("نسخ أوامر ADB الاختيارية", (d, w) -> copyBootstrapCommands())
                .setNegativeButton("إغلاق", null)
                .show();
    }

    private void copyBootstrapCommands() {
        String commands = "adb shell pm grant com.fixhua.guard android.permission.WRITE_SECURE_SETTINGS\n"
                + "adb shell pm grant com.fixhua.guard android.permission.DUMP";
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("FixHUA bootstrap", commands));
            Toast.makeText(this, "تم نسخ أوامر bootstrap", Toast.LENGTH_SHORT).show();
        }
    }

    private void launchGboxWithFreshCheck() {
        actionView.setText("جاري الفحص قبل تشغيل GBox…");
        final int token = generation.incrementAndGet();
        executor.execute(() -> {
            SystemSnapshot snapshot = SystemSnapshot.capture(getApplicationContext());
            ReadinessEngine.Result result = ReadinessEngine.evaluate(snapshot);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || token != generation.get()) return;
                lastSnapshot = snapshot;
                lastResult = result;
                render(snapshot, result);
                if (!snapshot.gboxInstalled) {
                    actionView.setText("⛔ GBox غير مثبت.");
                    return;
                }
                int warnings = result.count(ReadinessEngine.Severity.WARNING);
                if (warnings == 0) {
                    SettingsNavigator.launchGBox(this);
                    actionView.setText("✓ الفحص لم يجد تحذيرًا مهمًا؛ تم إرسال أمر تشغيل GBox.");
                    return;
                }
                ReadinessEngine.Finding best = pickBestFinding(result);
                new AlertDialog.Builder(this)
                        .setTitle("يوجد " + warnings + " تحذير قبل التشغيل")
                        .setMessage(best == null ? "يمكنك تشغيل GBox الآن أو إصلاح التحذيرات أولًا."
                                : "الأهم الآن: " + best.message)
                        .setPositiveButton("إصلاح أولًا", (d, w) -> startRepair(snapshot, best))
                        .setNegativeButton("تشغيل رغم ذلك", (d, w) -> SettingsNavigator.launchGBox(this))
                        .show();
            });
        });
    }

    private static ReadinessEngine.Finding pickBestFinding(ReadinessEngine.Result result) {
        if (result == null) return null;
        String[] priority = {
                "GBOX_MISSING",
                "DEV_PROCESS_LIMIT_OVERRIDE",
                "ALWAYS_FINISH_ACTIVITIES",
                "GBOX_BATTERY_OPTIMIZED",
                "POWER_SAVE",
                "WEBVIEW_MISSING",
                "NETWORK_UNVALIDATED",
                "LOW_STORAGE",
                "THERMAL_PRESSURE",
                "TIME_SETTINGS",
                "MEMORY_PRESSURE"
        };
        for (String wanted : priority) {
            for (ReadinessEngine.Finding finding : result.findings) {
                if (wanted.equals(finding.code)
                        && (finding.severity == ReadinessEngine.Severity.BLOCKER
                        || finding.severity == ReadinessEngine.Severity.WARNING)) {
                    return finding;
                }
            }
        }
        return result.firstActionable();
    }

    private static String shortLabel(String code) {
        if (code == null) return "المشكلة";
        switch (code) {
            case "DEV_PROCESS_LIMIT_OVERRIDE": return "حد عمليات الخلفية";
            case "ALWAYS_FINISH_ACTIVITIES": return "Don't keep activities";
            case "GBOX_BATTERY_OPTIMIZED": return "بطارية GBox";
            case "POWER_SAVE": return "توفير الطاقة";
            case "NETWORK_UNVALIDATED": return "اتصال الشبكة";
            case "LOW_STORAGE": return "المساحة الحرة";
            case "THERMAL_PRESSURE": return "الضغط الحراري";
            case "TIME_SETTINGS": return "الوقت التلقائي";
            case "WEBVIEW_MISSING": return "WebView";
            case "MEMORY_PRESSURE": return "ضغط الذاكرة";
            case "GBOX_MISSING": return "GBox غير مثبت";
            default: return code;
        }
    }

    private Button button(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setMinHeight(dp(52));
        return button;
    }

    private TextView cardText(float sizeSp, boolean bold) {
        TextView text = new TextView(this);
        text.setTextSize(sizeSp);
        if (bold) text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        text.setPadding(dp(12), dp(12), dp(12), dp(12));
        return text;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams matchWrapTop() {
        LinearLayout.LayoutParams p = matchWrap();
        p.topMargin = dp(7);
        return p;
    }

    private LinearLayout.LayoutParams matchWrapLargeTop() {
        LinearLayout.LayoutParams p = matchWrap();
        p.topMargin = dp(16);
        return p;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
