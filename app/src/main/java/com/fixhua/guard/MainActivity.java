package com.fixhua.guard;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.fixhua.core.BugreportAnalyzer;
import com.fixhua.core.HostDoctor;
import com.fixhua.core.HostFixVerification;
import com.fixhua.core.HostRemediationPlanner;
import com.fixhua.core.HostVerificationSnapshot;
import com.fixhua.core.SyntheticPackageRecord;

import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public final class MainActivity extends Activity {
    private static final int REQUEST_BUGREPORT_ZIP = 4101;
    private static final int REQUEST_ARTIFACT_APK = 4102;
    private static final String RUNTIME_PACKAGE = "com.fixhua.runtime";
    private static final String RUNTIME_ACTIVITY = "com.fixhua.runtime.RuntimeActivity";
    private static final String EXTRA_SESSION_ID = "com.fixhua.runtime.extra.SESSION_ID";
    private static final String EXTRA_ARTIFACT_SHA256 = "com.fixhua.runtime.extra.ARTIFACT_SHA256";
    private static final String EXTRA_PLAN_DIGEST = "com.fixhua.runtime.extra.PLAN_DIGEST";
    private static final String HOST_VERIFY_PREFS = "fixhua_host_verification";
    private static final String HOST_VERIFY_SNAPSHOT = "last_sanitized_snapshot";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicInteger refreshGeneration = new AtomicInteger();

    private TextView summaryView;
    private TextView findingsView;
    private Button fixNextButton;

    private volatile SystemSnapshot lastSnapshot;
    private volatile ReadinessEngine.Result lastResult;
    private volatile HostRemediationPlanner.Step lastHostRemediationStep;
    private boolean suppressNextResumeRefresh;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        summaryView = findViewById(R.id.summary);
        findingsView = findViewById(R.id.findings);
        fixNextButton = findViewById(R.id.fixNext);

        findViewById(R.id.checkNow).setOnClickListener(v -> refresh(true));
        findViewById(R.id.importArtifact).setOnClickListener(v -> openArtifactPicker());
        fixNextButton.setOnClickListener(v -> fixMostImportant());
        findViewById(R.id.launchGbox).setOnClickListener(v -> launchWithPreflight());
        findViewById(R.id.recoveryCenter).setOnClickListener(v -> showRecoveryCenter());
        findViewById(R.id.huaweiSettings).setOnClickListener(v -> SettingsNavigator.openHuaweiAppLaunch(this));
        findViewById(R.id.batterySettings).setOnClickListener(v -> SettingsNavigator.openBatteryOptimizationSettings(this));
        findViewById(R.id.gboxInfo).setOnClickListener(v -> SettingsNavigator.openAppDetails(this, SystemSnapshot.GBOX));
        findViewById(R.id.importBugreport).setOnClickListener(v -> openBugreportPicker());
        findViewById(R.id.shareReport).setOnClickListener(v -> shareFreshReport());

        refresh(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (summaryView == null) return;
        if (suppressNextResumeRefresh) {
            suppressNextResumeRefresh = false;
            return;
        }
        refresh(false);
    }

    @Override
    protected void onDestroy() {
        refreshGeneration.incrementAndGet();
        executor.shutdownNow();
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) {
            Toast.makeText(this, "لم يتم اختيار ملف", Toast.LENGTH_LONG).show();
            return;
        }
        if (requestCode == REQUEST_BUGREPORT_ZIP) {
            analyzeBugreport(uri);
        } else if (requestCode == REQUEST_ARTIFACT_APK) {
            importArtifact(uri);
        }
    }

    private void refresh(boolean userRequested) {
        lastHostRemediationStep = null;
        final int generation = refreshGeneration.incrementAndGet();
        summaryView.setText("جاري فحص الجهاز…");
        executor.execute(() -> {
            SystemSnapshot snapshot = SystemSnapshot.capture(getApplicationContext());
            ReadinessEngine.Result result = ReadinessEngine.evaluate(snapshot);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || generation != refreshGeneration.get()) return;
                lastSnapshot = snapshot;
                lastResult = result;
                render(snapshot, result);
                if (userRequested) {
                    Toast.makeText(this, "تم تحديث الفحص", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void render(SystemSnapshot s, ReadinessEngine.Result result) {
        String status;
        switch (result.status) {
            case BLOCKED:
                status = "غير جاهز";
                break;
            case ATTENTION:
                status = "يحتاج ضبط";
                break;
            default:
                status = "جاهز";
        }

        StringBuilder summary = new StringBuilder();
        summary.append("الحالة: ").append(status).append('\n');
        summary.append("GBox: ").append(s.gboxInstalled ? s.gboxVersion : "غير مثبت").append('\n');
        summary.append("بيئة Huawei: ").append(s.huaweiEnvironment ? "مكتشفة" : "غير مؤكدة").append('\n');
        summary.append("الإنترنت: ").append(s.networkValidated ? "متحقق" : "غير متحقق").append('\n');
        summary.append("توفير الطاقة: ").append(s.powerSaveMode ? "مفعل" : "متوقف").append('\n');
        summary.append("RAM متاح: ").append(s.availableRamMb).append(" / ").append(s.totalRamMb).append(" MB").append('\n');
        summary.append("المساحة الحرة: ")
                .append(String.format(Locale.US, "%.0f%%", s.freeStorageFraction() * 100.0))
                .append('\n');
        summary.append("الحرارة (حالة النظام): ").append(s.thermalStatus);
        summaryView.setText(summary.toString());

        StringBuilder findings = new StringBuilder();
        if (result.findings.isEmpty()) {
            findings.append("✓ لم يجد FixHUA مشاكل واضحة في الفحص الحالي.\n");
        } else {
            findings.append("النتائج:\n\n");
            int index = 1;
            for (ReadinessEngine.Finding finding : result.findings) {
                String marker = finding.severity == ReadinessEngine.Severity.BLOCKER ? "⛔"
                        : finding.severity == ReadinessEngine.Severity.WARNING ? "⚠" : "ℹ";
                findings.append(index++).append(". ").append(marker).append(' ')
                        .append(finding.message).append("\n\n");
            }
        }
        findingsView.setText(findings.toString());

        ReadinessEngine.Finding actionable = result.firstActionable();
        fixNextButton.setEnabled(actionable != null);
        fixNextButton.setText(actionable == null ? "لا توجد مشكلة تحتاج إجراء" : "إصلاح أهم مشكلة");
    }

    private void openArtifactPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/vnd.android.package-archive");
        suppressNextResumeRefresh = true;
        try {
            startActivityForResult(intent, REQUEST_ARTIFACT_APK);
        } catch (RuntimeException e) {
            Intent fallback = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("*/*");
            startActivityForResult(fallback, REQUEST_ARTIFACT_APK);
        }
    }

    private void importArtifact(Uri uri) {
        findingsView.setText("جاري استيراد APK…\nيتم حساب SHA-256 وقراءة metadata فقط؛ لن يتم تنفيذ كود التطبيق داخل Controller.");
        executor.execute(() -> {
            try {
                ArtifactImporter.ImportedArtifact imported = new ArtifactImporter(this).importUri(uri);
                String text = formatArtifactResult(imported);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    findingsView.setText(text);
                    new AlertDialog.Builder(this)
                            .setTitle("تم استيراد APK")
                            .setMessage(imported.reusedExistingArtifact()
                                    ? "هذا الـAPK موجود بالفعل بنفس SHA-256؛ تم استخدام النسخة المخزنة بدون تكرار."
                                    : "تم حفظ نسخة content-addressed للـAPK داخل التخزين الخاص بـFixHUA وجعلها read-only.")
                            .setPositiveButton(
                                    "تشغيل داخل FixHUA Runtime",
                                    (dialog, which) -> launchRuntimeSession(imported.runtimeSession()))
                            .setNegativeButton("لاحقًا", null)
                            .show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    findingsView.setText("تعذر استيراد APK:\n" + e.getClass().getSimpleName()
                            + ": " + String.valueOf(e.getMessage()));
                });
            }
        });
    }

    private static String formatArtifactResult(ArtifactImporter.ImportedArtifact imported) {
        SyntheticPackageRecord record = imported.record();
        StringBuilder text = new StringBuilder();
        text.append("Synthetic Artifact V1:\n\n");
        text.append("• Package: ").append(record.packageName()).append('\n');
        text.append("• Version: ").append(record.versionName().isBlank() ? "(بدون اسم)" : record.versionName())
                .append(" (").append(record.versionCode()).append(")\n");
        text.append("• SDK: min ").append(record.minSdk())
                .append(" / target ").append(record.targetSdk()).append('\n');
        text.append("• SHA-256: ").append(record.artifactSha256()).append('\n');
        text.append("• الحجم: ")
                .append(String.format(Locale.US, "%.2f MB", record.artifactSizeBytes() / (1024.0 * 1024.0)))
                .append('\n');
        text.append("• Native/JNI: ").append(record.hasNativeCode() ? "موجود" : "غير موجود").append('\n');
        text.append("• Debuggable: ").append(record.debuggable() ? "نعم" : "لا").append('\n');
        text.append("• Requested permissions: ").append(record.requestedPermissions().size()).append('\n');
        text.append("• Signing certs: ").append(record.signingCertificateSha256().size()).append('\n');
        text.append("• Content address: ").append(record.contentAddress()).append('\n');
        text.append("• Store: Controller private + read-only artifact\n");
        text.append("• Controller code execution: ممنوع\n");
        text.append("• Runtime Session: تم إنشاؤها ويمكن تشغيلها في Runtime UID منفصل.");
        return text.toString();
    }

    private void launchRuntimeSession(RuntimeSessionStore.Session session) {
        if (session == null) {
            Toast.makeText(this, "Runtime Session غير متاحة", Toast.LENGTH_LONG).show();
            return;
        }

        int signatureMatch = getPackageManager().checkSignatures(getPackageName(), RUNTIME_PACKAGE);
        if (signatureMatch != android.content.pm.PackageManager.SIGNATURE_MATCH) {
            new AlertDialog.Builder(this)
                    .setTitle("FixHUA Runtime غير جاهز")
                    .setMessage("يجب تثبيت FixHUA Runtime المطابق والموقّع بنفس هوية Controller. تم إيقاف التشغيل بدل استخدام Runtime غير موثوق.")
                    .setPositiveButton("حسنًا", null)
                    .show();
            return;
        }

        Intent runtime = new Intent()
                .setClassName(RUNTIME_PACKAGE, RUNTIME_ACTIVITY)
                .putExtra(EXTRA_SESSION_ID, session.sessionId())
                .putExtra(EXTRA_ARTIFACT_SHA256, session.artifactSha256())
                .putExtra(EXTRA_PLAN_DIGEST, session.planDigest());
        try {
            startActivity(runtime);
        } catch (RuntimeException failure) {
            new AlertDialog.Builder(this)
                    .setTitle("تعذر تشغيل FixHUA Runtime")
                    .setMessage(failure.getClass().getSimpleName() + ": " + String.valueOf(failure.getMessage()))
                    .setPositiveButton("حسنًا", null)
                    .show();
        }
    }

    private void openBugreportPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/zip");
        suppressNextResumeRefresh = true;
        try {
            startActivityForResult(intent, REQUEST_BUGREPORT_ZIP);
        } catch (RuntimeException e) {
            intent.setType("*/*");
            startActivityForResult(intent, REQUEST_BUGREPORT_ZIP);
        }
    }

    private void analyzeBugreport(Uri uri) {
        findingsView.setText("جاري تحليل Full Bug Report محليًا…\nلن يتم رفع الملف أو الاحتفاظ بالسجلات الخام.");
        executor.execute(() -> {
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                if (input == null) throw new IllegalStateException("تعذر فتح الملف المحدد");
                BugreportAnalyzer.Result report = new BugreportAnalyzer().analyzeZip(input);
                HostDoctor doctor = new HostDoctor();
                int observedProcesses = Math.max(1, report.gboxProcessNames().size());
                List<HostDoctor.Finding> hostFindings = doctor.evaluate(
                        report.toHostProfile(),
                        new HostDoctor.RuntimeBudget("observed-gbox", observedProcesses));
                HostRemediationPlanner.Plan remediationPlan =
                        new HostRemediationPlanner().plan(hostFindings);

                HostVerificationSnapshot currentVerificationSnapshot =
                        HostVerificationSnapshot.from(report, hostFindings);
                HostVerificationSnapshot previousVerificationSnapshot = loadHostVerificationSnapshot();
                HostFixVerification.Result verification = previousVerificationSnapshot == null
                        ? null
                        : new HostFixVerification().compare(
                                previousVerificationSnapshot,
                                currentVerificationSnapshot);
                saveHostVerificationSnapshot(currentVerificationSnapshot);

                String text = formatBugreportResult(report, remediationPlan, verification);
                HostRemediationPlanner.Step nextStep = remediationPlan.firstActionable();
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    lastHostRemediationStep = nextStep;
                    findingsView.setText(text);
                    fixNextButton.setEnabled(nextStep != null);
                    fixNextButton.setText(nextStep == null
                            ? "لا يوجد إصلاح آمن مباشر من التقرير"
                            : "إصلاح أهم مشكلة من التقرير");

                    String dialogMessage;
                    if (verification != null && verification.hasVerifiedImprovement()) {
                        dialogMessage = "تم التحقق من تغييرات إعدادات فعلية مقارنة بالتقرير السابق. "
                                + "راجع قسم «التحقق بعد الإصلاح» للتفاصيل.";
                    } else if (nextStep == null) {
                        dialogMessage = "تم استخراج حقائق التوافق محليًا فقط. لا يوجد إجراء إعدادات آمن يمكن لـFixHUA اقتراحه مباشرة من هذه النتائج.";
                    } else {
                        dialogMessage = "تم إنشاء خطة إصلاح مرتبة حسب الأولوية. الخطوة الأولى: "
                                + nextStep.recommendedAction()
                                + " بعد تعديل الإعداد، أنشئ Full Bug Report جديدًا واستورده ليتم التحقق من التغيير.";
                    }

                    AlertDialog.Builder dialog = new AlertDialog.Builder(this)
                            .setTitle("تحليل Bug Report اكتمل")
                            .setMessage(dialogMessage)
                            .setNegativeButton("إغلاق", null);
                    if (nextStep != null) {
                        dialog.setPositiveButton(
                                "فتح الإعداد المطلوب",
                                (d, which) -> openHostRemediationStep(nextStep));
                    } else {
                        dialog.setPositiveButton("حسنًا", null);
                    }
                    dialog.show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    lastHostRemediationStep = null;
                    findingsView.setText("تعذر تحليل Bug Report:\n" + e.getClass().getSimpleName()
                            + ": " + String.valueOf(e.getMessage()));
                });
            }
        });
    }

    private HostVerificationSnapshot loadHostVerificationSnapshot() {
        SharedPreferences prefs = getSharedPreferences(HOST_VERIFY_PREFS, MODE_PRIVATE);
        String encoded = prefs.getString(HOST_VERIFY_SNAPSHOT, null);
        if (encoded == null || encoded.isBlank()) return null;
        try {
            return HostVerificationSnapshot.decode(encoded);
        } catch (IllegalArgumentException invalid) {
            prefs.edit().remove(HOST_VERIFY_SNAPSHOT).apply();
            return null;
        }
    }

    private void saveHostVerificationSnapshot(HostVerificationSnapshot snapshot) {
        getSharedPreferences(HOST_VERIFY_PREFS, MODE_PRIVATE)
                .edit()
                .putString(HOST_VERIFY_SNAPSHOT, snapshot.encode())
                .apply();
    }

    private static String formatBugreportResult(
            BugreportAnalyzer.Result report,
            HostRemediationPlanner.Plan remediationPlan,
            HostFixVerification.Result verification) {
        StringBuilder text = new StringBuilder();
        text.append("نتائج Full Bug Report:\n\n");

        if (report.currentMaxCachedProcesses() > 0) {
            text.append("• حد العمليات الفعلي: ").append(report.currentMaxCachedProcesses()).append('\n');
        }
        if (report.customizedMaxCachedProcesses() > 0) {
            text.append("• حد الجهاز/OEM المرجعي: ")
                    .append(report.customizedMaxCachedProcesses()).append('\n');
        }
        text.append("• عمليات GBox المرصودة: ").append(report.gboxProcessNames().size()).append('\n');
        text.append("• مؤشرات AppProcessKiller: ").append(report.gboxAppProcessKillerMarkers()).append('\n');
        text.append("• مؤشرات null Application/Resources: ")
                .append(report.nullApplicationResourcesMarkers()).append('\n');
        text.append("• Input-dispatch timeout markers: ")
                .append(report.inputDispatchTimeoutMarkers()).append('\n');
        text.append("• Force 4x MSAA: ").append(report.force4xMsaa() ? "مفعل" : "غير ظاهر كمفعل").append('\n');
        text.append("• Don't keep activities: ")
                .append(report.alwaysFinishActivities() ? "مفعل" : "غير ظاهر كمفعل").append('\n');
        if (!report.webViewPackage().isBlank()) {
            text.append("• WebView: ").append(report.webViewPackage());
            if (!report.webViewVersion().isBlank()) text.append(' ').append(report.webViewVersion());
            text.append('\n');
        }

        if (!remediationPlan.steps().isEmpty()) {
            text.append("\nخطة الإصلاح حسب الأولوية:\n");
            int index = 1;
            for (HostRemediationPlanner.Step step : remediationPlan.steps()) {
                String marker = step.severity() == HostDoctor.Severity.HIGH ? "⛔"
                        : step.severity() == HostDoctor.Severity.WARNING ? "⚠" : "ℹ";
                text.append(index++).append(". ").append(marker).append(' ')
                        .append(step.summary()).append('\n');
                text.append("  ↳ ").append(step.recommendedAction());
                if (!step.actionable()) text.append(" [ملاحظة فقط — لا تغيير تلقائي]");
                text.append('\n');
            }
        }

        text.append("\nالتحقق بعد الإصلاح:\n");
        if (verification == null) {
            text.append("• تم حفظ ملخص مُعقّم لهذه النتيجة كنقطة مقارنة محلية.\n");
            text.append("• بعد تعديل الإعدادات، أنشئ Full Bug Report جديدًا واستورده للتحقق من التغيير.\n");
        } else {
            appendVerification(text, verification);
        }

        text.append("\nملاحظة: أرقام crash/ANR التاريخية لا تُستخدم لإثبات التحسن بين تقريرين.\n");
        text.append("تم تحليل ")
                .append(String.format(Locale.US, "%.1f MB", report.scannedBytes() / (1024.0 * 1024.0)))
                .append(" من النص الرئيسي دون استخراج ZIP إلى التخزين أو حفظ السجل الخام.");
        return text.toString();
    }

    private static void appendVerification(
            StringBuilder text,
            HostFixVerification.Result verification) {
        HostVerificationSnapshot before = verification.before();
        HostVerificationSnapshot after = verification.after();

        if (verification.processLimitChanged()) {
            text.append("• حد العمليات: ")
                    .append(before.currentMaxCachedProcesses())
                    .append(" → ")
                    .append(after.currentMaxCachedProcesses());
            if (verification.processLimitRestoredToOem()) {
                text.append(" ✓ عاد إلى الحد القياسي/OEM");
            }
            text.append('\n');
        } else {
            text.append("• حد العمليات لم يتغير: ")
                    .append(after.currentMaxCachedProcesses()).append('\n');
        }

        if (verification.force4xMsaaDisabled()) {
            text.append("• Force 4x MSAA: ON → OFF ✓\n");
        }
        if (verification.alwaysFinishActivitiesDisabled()) {
            text.append("• Don't keep activities: ON → OFF ✓\n");
        }

        if (!verification.resolvedFindingCodes().isEmpty()) {
            text.append("• مشاكل لم تعد موجودة: ")
                    .append(formatFindingCodes(verification.resolvedFindingCodes())).append('\n');
        }
        if (!verification.remainingFindingCodes().isEmpty()) {
            text.append("• مشاكل ما زالت موجودة: ")
                    .append(formatFindingCodes(verification.remainingFindingCodes())).append('\n');
        }
        if (!verification.introducedFindingCodes().isEmpty()) {
            text.append("• مشاكل جديدة في التقرير الحالي: ")
                    .append(formatFindingCodes(verification.introducedFindingCodes())).append('\n');
        }
        if (!verification.hasVerifiedImprovement() && !verification.hasNewProblem()) {
            text.append("• لم يظهر تغيير إعدادات قابل للتحقق مقارنة بالتقرير السابق.\n");
        }
    }

    private static String formatFindingCodes(List<String> codes) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < codes.size(); i++) {
            if (i > 0) out.append("، ");
            out.append(findingLabel(codes.get(i)));
        }
        return out.toString();
    }

    private static String findingLabel(String code) {
        switch (code) {
            case "DEV_PROCESS_LIMIT_OVERRIDE":
                return "حد العمليات غير القياسي";
            case "RUNTIME_PROCESS_BUDGET_CONFLICT":
                return "تعارض ميزانية عمليات GBox";
            case "ALWAYS_FINISH_ACTIVITIES":
                return "Don't keep activities";
            case "FORCE_4X_MSAA":
                return "Force 4x MSAA";
            case "WEBVIEW_MISSING":
                return "WebView غير متاح";
            case "WEBVIEW_DIRTY":
                return "WebView يحتاج إعادة تهيئة";
            case "WEBVIEW_SINGLE_PROCESS":
                return "WebView أحادي العملية";
            default:
                return code;
        }
    }

    private void openHostRemediationStep(HostRemediationPlanner.Step step) {
        suppressNextResumeRefresh = true;
        SettingsNavigator.fixHostStep(this, step);
    }

    private void fixMostImportant() {
        HostRemediationPlanner.Step hostStep = lastHostRemediationStep;
        if (hostStep != null) {
            openHostRemediationStep(hostStep);
            return;
        }

        ReadinessEngine.Result result = lastResult;
        if (result == null) {
            refresh(true);
            return;
        }
        ReadinessEngine.Finding finding = result.firstActionable();
        if (finding == null) {
            Toast.makeText(this, "لا توجد مشكلة تحتاج إجراء الآن", Toast.LENGTH_SHORT).show();
            return;
        }
        SettingsNavigator.fixFinding(this, finding);
    }

    private void launchWithPreflight() {
        final int generation = refreshGeneration.incrementAndGet();
        summaryView.setText("جاري الفحص قبل التشغيل…");
        executor.execute(() -> {
            SystemSnapshot snapshot = SystemSnapshot.capture(getApplicationContext());
            ReadinessEngine.Result result = ReadinessEngine.evaluate(snapshot);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || generation != refreshGeneration.get()) return;
                lastSnapshot = snapshot;
                lastResult = result;
                render(snapshot, result);

                if (!snapshot.gboxInstalled) {
                    new AlertDialog.Builder(this)
                            .setTitle("GBox غير مثبت")
                            .setMessage("لم يعثر FixHUA على حزمة GBox على الجهاز.")
                            .setPositiveButton("حسنًا", null)
                            .show();
                    return;
                }

                if (result.count(ReadinessEngine.Severity.WARNING) > 0) {
                    new AlertDialog.Builder(this)
                            .setTitle("قبل تشغيل GBox")
                            .setMessage(buildWarningSummary(result))
                            .setPositiveButton("تشغيل الآن", (d, w) -> launchGBox())
                            .setNegativeButton("إلغاء", null)
                            .show();
                } else {
                    launchGBox();
                }
            });
        });
    }

    private String buildWarningSummary(ReadinessEngine.Result result) {
        StringBuilder text = new StringBuilder("وجد FixHUA نقاطًا قد تؤثر على الاستقرار:\n\n");
        int shown = 0;
        for (ReadinessEngine.Finding finding : result.findings) {
            if (finding.severity != ReadinessEngine.Severity.WARNING) continue;
            text.append("• ").append(finding.message).append('\n');
            if (++shown >= 4) break;
        }
        text.append("\nيمكنك التشغيل الآن أو الرجوع وإصلاحها أولًا.");
        return text.toString();
    }

    private void launchGBox() {
        if (!SettingsNavigator.launchGBox(this)) {
            Toast.makeText(this, "تعذر تشغيل GBox من النظام", Toast.LENGTH_LONG).show();
        }
    }

    private void showRecoveryCenter() {
        String[] items = new String[]{
                "فتح معلومات GBox",
                "فتح Huawei App launch",
                "فتح تحسين البطارية",
                "فتح إعدادات البطارية",
                "فتح خيارات المطور",
                "فتح إعدادات التخزين",
                "فتح إعدادات الشبكة",
                "تشغيل GBox"
        };
        new AlertDialog.Builder(this)
                .setTitle("مركز إصلاح GBox")
                .setItems(items, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            SettingsNavigator.openAppDetails(this, SystemSnapshot.GBOX);
                            break;
                        case 1:
                            SettingsNavigator.openHuaweiAppLaunch(this);
                            break;
                        case 2:
                            SettingsNavigator.openBatteryOptimizationSettings(this);
                            break;
                        case 3:
                            SettingsNavigator.openBatterySettings(this);
                            break;
                        case 4:
                            SettingsNavigator.openDeveloperOptions(this);
                            break;
                        case 5:
                            SettingsNavigator.openStorageSettings(this);
                            break;
                        case 6:
                            SettingsNavigator.openWirelessSettings(this);
                            break;
                        case 7:
                            launchGBox();
                            break;
                        default:
                            break;
                    }
                })
                .setNegativeButton("إغلاق", null)
                .show();
    }

    private void shareFreshReport() {
        Toast.makeText(this, "جاري إنشاء التقرير…", Toast.LENGTH_SHORT).show();
        executor.execute(() -> {
            SystemSnapshot snapshot = SystemSnapshot.capture(getApplicationContext());
            ReadinessEngine.Result result = ReadinessEngine.evaluate(snapshot);
            String report = ReportBuilder.build(snapshot, result);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                Intent share = new Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_SUBJECT, "FixHUA v2.1 report")
                        .putExtra(Intent.EXTRA_TEXT, report);
                try {
                    startActivity(Intent.createChooser(share, "مشاركة تقرير FixHUA"));
                } catch (RuntimeException e) {
                    findingsView.setText(report);
                    Toast.makeText(this, "لا يوجد تطبيق مشاركة؛ تم عرض التقرير داخل FixHUA", Toast.LENGTH_LONG).show();
                }
            });
        });
    }
}
