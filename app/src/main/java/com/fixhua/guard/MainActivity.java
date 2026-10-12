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
import com.fixhua.core.HostVerificationReport;
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
    private static final String HOST_VERIFY_PREVIOUS_SNAPSHOT = "previous_sanitized_snapshot";

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
        summary.append("توفير الطاقة: ").append(s.powerSaveMode ? "مفعّل" : "غير مفعّل").append('\n');
        summary.append(String.format(Locale.US, "الذاكرة المتاحة: %d MB من %d MB", s.availableRamMb, s.totalRamMb))
                .append('\n');
        summary.append("المساحة الحرة: ")
                .append(String.format(Locale.US, "%.0f%%", s.freeStorageFraction() * 100.0))
                .append('\n');
        summary.append("الحالة الحرارية: ").append(formatThermalStatus(s.thermalStatus));
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

    private static String formatThermalStatus(int thermalStatus) {
        switch (thermalStatus) {
            case 0:
                return "طبيعية";
            case 1:
                return "ارتفاع خفيف";
            case 2:
                return "ارتفاع متوسط";
            case 3:
                return "مرتفعة";
            case 4:
                return "حرجة";
            case 5:
                return "طارئة";
            case 6:
                return "إيقاف حراري";
            default:
                return "غير متاحة";
        }
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
                .putExtra(EXTRA_SESSION_ID, session.sessionId)
                .putExtra(EXTRA_ARTIFACT_SHA256, session.artifactSha256)
                .putExtra(EXTRA_PLAN_DIGEST, session.planDigest);
        try {
            startActivity(runtime);
        } catch (RuntimeException e) {
            Toast.makeText(this, "تعذر تشغيل FixHUA Runtime: " + e.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
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
            Intent fallback = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("*/*");
            startActivityForResult(fallback, REQUEST_BUGREPORT_ZIP);
        }
    }

    private void analyzeBugreport(Uri uri) {
        findingsView.setText("جاري تحليل bugreport محليًا…\nلا يتم رفع التقرير خارج الجهاز.");
        executor.execute(() -> {
            try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
                if (inputStream == null) throw new IllegalStateException("تعذر فتح الملف");
                BugreportAnalyzer.Report report = BugreportAnalyzer.analyzeZip(inputStream);
                HostRemediationPlanner.Plan plan = HostRemediationPlanner.fromBugreport(report);
                lastHostRemediationStep = plan.firstStep();
                String text = formatBugreportReport(report, plan);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    findingsView.setText(text);
                    updateFixButtonForHostPlan();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    lastHostRemediationStep = null;
                    findingsView.setText("تعذر تحليل bugreport:\n" + e.getClass().getSimpleName()
                            + ": " + String.valueOf(e.getMessage()));
                    updateFixButtonForHostPlan();
                });
            }
        });
    }

    private String formatBugreportReport(BugreportAnalyzer.Report report, HostRemediationPlanner.Plan plan) {
        StringBuilder text = new StringBuilder();
        text.append("نتيجة تحليل bugreport:\n\n");
        text.append("• الملفات التي تم فحصها: ").append(report.scannedEntries).append('\n');
        text.append("• GBox host processes المرصودة: ").append(report.gboxHostProcessCount).append('\n');
        if (report.currentMaxCachedProcesses >= 0) {
            text.append("• CUR_MAX_CACHED_PROCESSES: ").append(report.currentMaxCachedProcesses).append('\n');
        }
        if (report.customizedMaxCachedProcesses >= 0) {
            text.append("• Customized max cached: ").append(report.customizedMaxCachedProcesses).append('\n');
        }
        text.append("• Always finish activities: ").append(report.alwaysFinishActivities ? "مفعل" : "غير ظاهر كمفعل").append('\n');
        if (report.webViewPackage != null && !report.webViewPackage.isBlank()) {
            text.append("• WebView package: ").append(report.webViewPackage).append('\n');
        }
        text.append('\n');
        if (report.findings.isEmpty()) {
            text.append("لم يجد FixHUA تعارضًا مؤكّدًا في الحقول التي يفهمها هذا الإصدار.\n");
        } else {
            int index = 1;
            for (BugreportAnalyzer.Finding finding : report.findings) {
                text.append(index++).append(". ").append(finding.message).append('\n');
            }
        }
        if (!plan.steps.isEmpty()) {
            text.append("\nخطة الإصلاح المحلية:\n");
            for (HostRemediationPlanner.Step step : plan.steps) {
                text.append("• P").append(step.priority).append(" — ").append(step.title);
                if (!step.actionable) text.append(" (مراقبة فقط)");
                text.append('\n');
            }
        }
        text.append("\nملاحظة: العدادات التاريخية للـcrash/ANR ليست إثباتًا أن الحالة الحالية ما زالت سيئة؛ التحقق الحقيقي يتم بإعادة الالتقاط بعد الإجراء.");
        return text.toString();
    }

    private void updateFixButtonForHostPlan() {
        HostRemediationPlanner.Step step = lastHostRemediationStep;
        if (step != null && step.actionable) {
            fixNextButton.setEnabled(true);
            fixNextButton.setText("إصلاح أهم مشكلة");
            return;
        }
        ReadinessEngine.Result result = lastResult;
        ReadinessEngine.Finding actionable = result == null ? null : result.firstActionable();
        fixNextButton.setEnabled(actionable != null);
        fixNextButton.setText(actionable == null ? "لا توجد مشكلة تحتاج إجراء" : "إصلاح أهم مشكلة");
    }

    private void fixMostImportant() {
        HostRemediationPlanner.Step hostStep = lastHostRemediationStep;
        if (hostStep != null && hostStep.actionable) {
            captureHostVerificationBefore();
            SettingsNavigator.openForFinding(this, hostStep.code);
            return;
        }

        ReadinessEngine.Result result = lastResult;
        if (result == null) return;
        ReadinessEngine.Finding finding = result.firstActionable();
        if (finding == null) {
            Toast.makeText(this, "لا توجد مشكلة قابلة للإصلاح الآن", Toast.LENGTH_SHORT).show();
            return;
        }

        RepairEngine.RepairResult repair = RepairEngine.repair(this, finding.code);
        Toast.makeText(this, repair.message, Toast.LENGTH_LONG).show();
        if (!repair.requiresManualAction) {
            refresh(false);
        }
    }

    private void captureHostVerificationBefore() {
        SystemSnapshot snapshot = lastSnapshot;
        ReadinessEngine.Result result = lastResult;
        if (snapshot == null) return;
        HostVerificationSnapshot sanitized = HostVerificationSnapshot.fromLiveSnapshot(snapshot, result);
        SharedPreferences prefs = getSharedPreferences(HOST_VERIFY_PREFS, MODE_PRIVATE);
        String current = prefs.getString(HOST_VERIFY_SNAPSHOT, null);
        SharedPreferences.Editor editor = prefs.edit();
        if (current != null && !current.isBlank()) {
            editor.putString(HOST_VERIFY_PREVIOUS_SNAPSHOT, current);
        }
        editor.putString(HOST_VERIFY_SNAPSHOT, sanitized.serialize()).apply();
    }

    private void showRecoveryCenter() {
        startActivity(new Intent(this, OptimizerActivity.class));
    }

    private void launchWithPreflight() {
        SystemSnapshot snapshot = lastSnapshot;
        ReadinessEngine.Result result = lastResult;
        if (snapshot == null || result == null) {
            Toast.makeText(this, "يجب فحص الجهاز أولًا", Toast.LENGTH_LONG).show();
            refresh(true);
            return;
        }
        if (!snapshot.gboxInstalled) {
            Toast.makeText(this, "GBox غير مثبت", Toast.LENGTH_LONG).show();
            return;
        }
        if (result.status == ReadinessEngine.Status.BLOCKED) {
            new AlertDialog.Builder(this)
                    .setTitle("FixHUA أوقف التشغيل الوقائي")
                    .setMessage("يوجد مانع واضح في الفحص الحالي. أصلح المشكلة ذات الأولوية أولًا ثم أعد الفحص.")
                    .setPositiveButton("حسنًا", null)
                    .show();
            return;
        }
        Intent launch = getPackageManager().getLaunchIntentForPackage(SystemSnapshot.GBOX);
        if (launch == null) {
            Toast.makeText(this, "تعذر العثور على شاشة تشغيل GBox", Toast.LENGTH_LONG).show();
            return;
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(launch);
    }

    private void shareFreshReport() {
        SystemSnapshot snapshot = lastSnapshot;
        ReadinessEngine.Result result = lastResult;
        if (snapshot == null || result == null) {
            Toast.makeText(this, "شغّل فحص الجهاز أولًا", Toast.LENGTH_LONG).show();
            return;
        }
        String report = buildShareReport(snapshot, result);
        Intent share = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, "FixHUA report")
                .putExtra(Intent.EXTRA_TEXT, report);
        startActivity(Intent.createChooser(share, "مشاركة تقرير FixHUA"));
    }

    private String buildShareReport(SystemSnapshot snapshot, ReadinessEngine.Result result) {
        StringBuilder text = new StringBuilder();
        text.append("FixHUA sanitized report\n");
        text.append("GBox: ").append(snapshot.gboxInstalled ? snapshot.gboxVersion : "not-installed").append('\n');
        text.append("Huawei environment: ").append(snapshot.huaweiEnvironment).append('\n');
        text.append("Network validated: ").append(snapshot.networkValidated).append('\n');
        text.append("Power save: ").append(snapshot.powerSaveMode).append('\n');
        text.append("RAM MB: ").append(snapshot.availableRamMb).append('/').append(snapshot.totalRamMb).append('\n');
        text.append("Storage free ratio: ")
                .append(String.format(Locale.US, "%.3f", snapshot.freeStorageFraction())).append('\n');
        text.append("Thermal status: ").append(snapshot.thermalStatus).append('\n');
        text.append("GBox battery exemption: ").append(snapshot.gboxIgnoringBatteryOptimizations).append('\n');
        text.append("Controller battery exemption: ").append(snapshot.controllerIgnoringBatteryOptimizations).append('\n');
        text.append("Imported process-budget probe: ")
                .append(snapshot.importedProcessBudgetAvailable ? "available" : "none").append('\n');
        if (snapshot.importedProcessBudgetAvailable) {
            text.append("Imported process budget current/reference: ")
                    .append(snapshot.importedProcessBudgetCurrent).append('/')
                    .append(snapshot.importedProcessBudgetReference).append('\n');
        }
        text.append("Privileged process-budget probe: ")
                .append(snapshot.processBudgetProbeAttempted ? "attempted" : "not-attempted").append('\n');
        if (snapshot.processBudgetProbeAttempted) {
            text.append("Privileged probe success: ").append(snapshot.processBudgetProbeSuccess).append('\n');
            if (!snapshot.processBudgetProbeSuccess) {
                text.append("Privileged probe failure: ").append(snapshot.processBudgetProbeFailure).append('\n');
            }
        }
        text.append("Findings:\n");
        for (ReadinessEngine.Finding finding : result.findings) {
            text.append("- ").append(finding.code).append(" [").append(finding.severity).append("]\n");
        }
        appendHostVerification(text, snapshot, result);
        return text.toString();
    }

    private void appendHostVerification(
            StringBuilder text,
            SystemSnapshot currentSnapshot,
            ReadinessEngine.Result currentResult) {
        SharedPreferences prefs = getSharedPreferences(HOST_VERIFY_PREFS, MODE_PRIVATE);
        String encoded = prefs.getString(HOST_VERIFY_SNAPSHOT, null);
        if (encoded == null || encoded.isBlank()) return;
        try {
            HostVerificationSnapshot before = HostVerificationSnapshot.deserialize(encoded);
            HostVerificationSnapshot after = HostVerificationSnapshot.fromLiveSnapshot(currentSnapshot, currentResult);
            HostFixVerification.Result verification = HostFixVerification.compare(before, after);
            text.append('\n').append(HostVerificationReport.render(before, after, verification));
        } catch (RuntimeException ignored) {
            // Corrupt local verification state must never block the main sanitized report.
        }
    }
}