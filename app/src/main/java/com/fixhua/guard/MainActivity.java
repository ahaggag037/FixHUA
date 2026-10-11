package com.fixhua.guard;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.fixhua.core.BugreportAnalyzer;
import com.fixhua.core.HostDoctor;

import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public final class MainActivity extends Activity {
    private static final int REQUEST_BUGREPORT_ZIP = 4101;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicInteger refreshGeneration = new AtomicInteger();

    private TextView summaryView;
    private TextView findingsView;
    private Button fixNextButton;

    private volatile SystemSnapshot lastSnapshot;
    private volatile ReadinessEngine.Result lastResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        summaryView = findViewById(R.id.summary);
        findingsView = findViewById(R.id.findings);
        fixNextButton = findViewById(R.id.fixNext);

        findViewById(R.id.checkNow).setOnClickListener(v -> refresh(true));
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
        if (summaryView != null) refresh(false);
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
        if (requestCode != REQUEST_BUGREPORT_ZIP || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) {
            Toast.makeText(this, "لم يتم اختيار ملف Bug Report", Toast.LENGTH_LONG).show();
            return;
        }
        analyzeBugreport(uri);
    }

    private void refresh(boolean userRequested) {
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

    private void openBugreportPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/zip");
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
                String text = formatBugreportResult(report, hostFindings);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    findingsView.setText(text);
                    new AlertDialog.Builder(this)
                            .setTitle("تحليل Bug Report اكتمل")
                            .setMessage("تم استخراج حقائق التوافق محليًا فقط. لم يتم رفع الملف ولم يحتفظ FixHUA بالسجلات الخام.")
                            .setPositiveButton("حسنًا", null)
                            .show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    findingsView.setText("تعذر تحليل Bug Report:\n" + e.getClass().getSimpleName()
                            + ": " + String.valueOf(e.getMessage()));
                });
            }
        });
    }

    private static String formatBugreportResult(
            BugreportAnalyzer.Result report,
            List<HostDoctor.Finding> hostFindings) {
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

        if (!hostFindings.isEmpty()) {
            text.append("\nHost Doctor:\n");
            for (HostDoctor.Finding finding : hostFindings) {
                String marker = finding.severity() == HostDoctor.Severity.HIGH ? "⛔"
                        : finding.severity() == HostDoctor.Severity.WARNING ? "⚠" : "ℹ";
                text.append(marker).append(' ').append(finding.summary()).append('\n');
                text.append("  ↳ ").append(finding.recommendedAction()).append('\n');
            }
        }

        text.append("\nتم تحليل ")
                .append(String.format(Locale.US, "%.1f MB", report.scannedBytes() / (1024.0 * 1024.0)))
                .append(" من النص الرئيسي دون استخراج ZIP إلى التخزين.");
        return text.toString();
    }

    private void fixMostImportant() {
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
                        .putExtra(Intent.EXTRA_SUBJECT, "FixHUA v2 report")
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
