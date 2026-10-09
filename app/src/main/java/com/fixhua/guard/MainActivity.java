package com.fixhua.guard;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public final class MainActivity extends Activity {
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
