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
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

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
    private Button advancedModeButton;

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
        title.setText("FixHUA Optimizer 2.1.1");
        title.setTextSize(28f);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText("تحسين استقرار GBox على Huawei — تنفيذ آمن + تحقق بعد كل خطوة");
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

        advancedModeButton = button("الوضع المتقدم (ADB اختياري)");
        advancedModeButton.setOnClickListener(v -> showAdvancedModeDialog());
        root.addView(advancedModeButton, matchWrapTop());

        Button advancedDiagnostics = button("التشخيص المتقدم / Bug Report / Runtime");
        advancedDiagnostics.setOnClickListener(v -> startActivity(new Intent(this, MainActivity.class)));
        root.addView(advancedDiagnostics, matchWrapTop());

        actionView = cardText(15f, true);
        actionView.setText("سيعرض FixHUA هنا ما تم تنفيذه وهل نجح التحقق بعده.");
        actionView.setPadding(dp(12), dp(14), dp(12), dp(14));
        root.addView(actionView, matchWrapLargeTop());

        findingsView = cardText(15f, false);
        findingsView.setText("النتائج ستظهر بعد الفحص.");
        findingsView.setTextIsSelectable(true);
        root.addView(findingsView, matchWrapLargeTop());

        TextView note = new TextView(this);
        note.setText("FixHUA لا يدّعي أنه غيّر إعدادًا محميًا إذا لم يستطع. التغييرات التي تسمح بها صلاحياته تُنفّذ مباشرة وتُفحص بعدها. الإعدادات التي يحميها Android تُفتح في المكان الصحيح، ثم يعيد FixHUA الفحص عند رجوعك ليثبت هل تغيرت فعلاً.");
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
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || token != generation.get()) return;
                render(snapshot, result);
                if (verifyPending) verifyPendingRepair(snapshot, result);
                if (userRequested) Toast.makeText(this, "تم الفحص من جديد", Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void render(SystemSnapshot s, ReadinessEngine.Result result) {
        String status = result.status == ReadinessEngine.Status.READY ? "جاهز"
                : result.status == ReadinessEngine.Status.BLOCKED ? "غير جاهز" : "يحتاج ضبط";
        boolean secure = RepairEngine.hasWriteSecureSettings(this);

        StringBuilder summary = new StringBuilder();
        summary.append("الحالة: ").append(status).append('\n');
        summary.append("GBox: ").append(s.gboxInstalled ? s.gboxVersion : "غير مثبت").append('\n');
        summary.append("Huawei: ").append(s.huaweiEnvironment ? "مكتشفة" : "غير مؤكدة").append('\n');
        summary.append("الإنترنت: ").append(s.networkValidated ? "متحقق" : "غير متحقق").append('\n');
        summary.append("توفير الطاقة: ").append(s.powerSaveMode ? "مفعل" : "متوقف").append('\n');
        summary.append("الذاكرة المتاحة: ").append(s.availableRamMb).append(" ميجابايت").append('\n');
        summary.append("إجمالي الذاكرة: ").append(s.totalRamMb).append(" ميجابايت").append('\n');
        if (s.privilegedProcessProbeSuccess) {
            summary.append("حد العمليات: ").append(s.currentMaxCachedProcesses)
                    .append(" / المرجع ").append(s.referenceMaxCachedProcesses).append('\n');
        }
        summary.append("الإصلاح المباشر المتقدم: ").append(secure ? "مفعل" : "غير مفعل");
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
        advancedModeButton.setText(secure ? "الوضع المتقدم: مفعل" : "الوضع المتقدم (ADB اختياري)");
    }

    private void smartRepair() {
        actionView.setText("أعيد الفحص قبل أي تغيير…");
        final int token = generation.incrementAndGet();
        executor.execute(() -> {
            SystemSnapshot snapshot = SystemSnapshot.capture(getApplicationContext());
            ReadinessEngine.Result result = ReadinessEngine.evaluate(snapshot);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || token != generation.get()) return;
                render(snapshot, result);
                ReadinessEngine.Finding finding = pickBestFinding(result);
                if (finding == null) {
                    actionView.setText("✓ لا يوجد إصلاح ضروري الآن حسب الفحص الحالي.");
                    return;
                }
                startRepair(finding);
            });
        });
    }

    private void startRepair(ReadinessEngine.Finding finding) {
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
                showProtectedDeveloperRepair(finding);
                break;

            case NEEDS_SETTINGS:
                pendingFindingCode = finding.code;
                returningFromRepair = true;
                actionView.setText("فتحت صفحة النظام المطلوبة. بعد تنفيذ التغيير والرجوع سأعيد الفحص تلقائيًا.");
                SettingsNavigator.fixFinding(this, finding);
                break;

            case FAILED:
                actionView.setText("✗ فشل التغيير الآمن: " + direct.message());
                break;

            case NOT_SUPPORTED:
            default:
                actionView.setText("لا يوجد تغيير تلقائي آمن لهذه الحالة. " + direct.message());
                SettingsNavigator.fixFinding(this, finding);
                break;
        }
    }

    private void showProtectedDeveloperRepair(ReadinessEngine.Finding finding) {
        String instruction;
        if ("DEV_PROCESS_LIMIT_OVERRIDE".equals(finding.code)) {
            instruction = "غيّر Background process limit إلى Standard limit. هذه هي المشكلة التي ظهر أنها تقلل ميزانية عمليات GBox.";
        } else if ("ALWAYS_FINISH_ACTIVITIES".equals(finding.code)) {
            instruction = "أوقف Don't keep activities.";
        } else {
            instruction = "عدّل الإعداد المشار إليه ثم ارجع إلى FixHUA.";
        }

        new AlertDialog.Builder(this)
                .setTitle("إعداد يحميه Android")
                .setMessage(instruction + "\n\nسأعيد الفحص تلقائيًا عند رجوعك ولن أعتبر الإصلاح ناجحًا إلا إذا استطعت التحقق منه.")
                .setPositiveButton("فتح خيارات المطور", (d, w) -> {
                    if ("DEV_PROCESS_LIMIT_OVERRIDE".equals(finding.code)) {
                        ProcessBudgetImportStore.invalidate(this);
                    }
                    pendingFindingCode = finding.code;
                    returningFromRepair = true;
                    actionView.setText("بانتظار تعديل «" + shortLabel(finding.code) + "» ثم الرجوع للتحقق.");
                    SettingsNavigator.openDeveloperOptions(this);
                })
                .setNeutralButton("تفعيل الوضع المتقدم", (d, w) -> showAdvancedModeDialog())
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void verifyPendingRepair(SystemSnapshot snapshot, ReadinessEngine.Result result) {
        String code = pendingFindingCode;
        if (code == null) return;
        pendingFindingCode = null;

        if (RepairEngine.isResolved(code, snapshot, result)) {
            actionView.setText("✓ تم التحقق: إصلاح «" + shortLabel(code) + "» نجح ولم تعد المشكلة ظاهرة.");
            return;
        }

        if ("DEV_PROCESS_LIMIT_OVERRIDE".equals(code) && !snapshot.privilegedProcessProbeSuccess) {
            actionView.setText("⚠ رجعت من تعديل حد العمليات، لكن Android لم يسمح بقراءة القيمة المحمية مباشرة الآن. لم أسجل الإصلاح كنجاح. استخدم الوضع المتقدم أو استورد Bug Report جديدًا لإثبات القيمة.");
            return;
        }

        actionView.setText("⚠ الفحص بعد الرجوع لم يثبت زوال «" + shortLabel(code)
                + "». لم يسجل FixHUA هذه الخطوة كإصلاح ناجح.");
    }

    private void showAdvancedModeDialog() {
        boolean enabled = RepairEngine.hasWriteSecureSettings(this);
        String state = enabled ? "WRITE_SECURE_SETTINGS مفعلة بالفعل." : "WRITE_SECURE_SETTINGS غير مفعلة.";
        new AlertDialog.Builder(this)
                .setTitle("الوضع المتقدم")
                .setMessage(state
                        + "\n\nهذا الوضع اختياري ولا يحتاج Root. من ADB نفّذ الأمرين بعد تثبيت النسخة النهائية. بعدها يستطيع FixHUA تنفيذ بعض تغييرات Settings.Global مباشرة وقراءة تشخيص أعمق، ثم التحقق منها.")
                .setPositiveButton("نسخ أوامر ADB", (d, w) -> copyBootstrapCommands())
                .setNeutralButton("التشخيص المتقدم", (d, w) -> startActivity(new Intent(this, MainActivity.class)))
                .setNegativeButton("إغلاق", null)
                .show();
    }

    private void copyBootstrapCommands() {
        String commands = "adb shell pm grant com.fixhua.guard android.permission.WRITE_SECURE_SETTINGS\n"
                + "adb shell pm grant com.fixhua.guard android.permission.DUMP";
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("FixHUA bootstrap", commands));
            Toast.makeText(this, "تم نسخ أوامر الوضع المتقدم", Toast.LENGTH_SHORT).show();
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
                render(snapshot, result);
                if (!snapshot.gboxInstalled) {
                    actionView.setText("⛔ GBox غير مثبت.");
                    return;
                }
                int warnings = result.count(ReadinessEngine.Severity.WARNING);
                if (warnings == 0) {
                    boolean launched = SettingsNavigator.launchGBox(this);
                    actionView.setText(launched
                            ? "✓ الفحص لم يجد تحذيرًا مهمًا؛ تم تشغيل GBox."
                            : "✗ تعذر تشغيل GBox من النظام.");
                    return;
                }
                ReadinessEngine.Finding best = pickBestFinding(result);
                new AlertDialog.Builder(this)
                        .setTitle("يوجد " + warnings + " تحذير قبل التشغيل")
                        .setMessage(best == null ? "يمكنك تشغيل GBox الآن أو إصلاح التحذيرات أولًا."
                                : "الأهم الآن: " + best.message)
                        .setPositiveButton("إصلاح أولًا", (d, w) -> startRepair(best))
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
