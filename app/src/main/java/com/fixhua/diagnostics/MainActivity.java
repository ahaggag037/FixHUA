package com.fixhua.diagnostics;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String GBOX = "com.gbox.android";

    private TextView statusView;
    private Button modeButton;
    private Button diagnosticButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 42);
        }
        setContentView(buildUi());
        refreshStatus();
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private void handleIntent(Intent intent) {
        if (intent != null && GuardService.ACTION_REOPEN_GBOX.equals(intent.getAction())) {
            statusView.postDelayed(this::launchGBox, 150);
        }
    }

    private View buildUi() {
        int pad = dp(18);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(pad, pad, pad, pad);
        content.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        TextView title = new TextView(this);
        title.setText("FixHUA Root Guard v3.2");
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        content.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("حماية GBox + تشخيص لحظي قابل للنسخ. جلسة التشخيص تقرأ مؤشرات الذاكرة/PSI وCPU والحرارة والبطارية والشبكة كل ثانية، وزر التهنيجة يضع علامة زمنية دقيقة داخل السجل.");
        subtitle.setTextSize(15);
        subtitle.setPadding(0, dp(8), 0, dp(14));
        content.addView(subtitle);

        statusView = new TextView(this);
        statusView.setTextSize(15);
        statusView.setTypeface(Typeface.MONOSPACE);
        statusView.setPadding(dp(12), dp(12), dp(12), dp(12));
        content.addView(statusView);

        Button rootProbe = button("فحص وتفعيل صلاحية Root المتاحة");
        rootProbe.setOnClickListener(v -> probeRoot());
        content.addView(rootProbe);

        Button start = button("ابدأ Root Guard وافتح GBox");
        start.setOnClickListener(v -> startProtected());
        content.addView(start);

        modeButton = button("");
        modeButton.setOnClickListener(v -> cycleMode());
        content.addView(modeButton);

        Button usage = button("تفعيل Usage Access للحماية التكيفية");
        usage.setOnClickListener(v -> openUsageAccess());
        content.addView(usage);

        Button huawei = button("فتح إعدادات Huawei للخلفية والتشغيل التلقائي");
        huawei.setOnClickListener(v -> openHuaweiStartupManager());
        content.addView(huawei);

        Button battery = button("السماح لـ FixHUA بالعمل دون تقييد البطارية");
        battery.setOnClickListener(v -> requestOwnBatteryExemption());
        content.addView(battery);

        Button reopen = button("فتح GBox فقط");
        reopen.setOnClickListener(v -> launchGBox());
        content.addView(reopen);

        diagnosticButton = button("");
        diagnosticButton.setOnClickListener(v -> toggleDeepDiagnostics());
        content.addView(diagnosticButton);

        Button lagMarker = button("حصلت تهنيجة الآن — ضع علامة زمنية");
        lagMarker.setOnClickListener(v -> markLagNow());
        content.addView(lagMarker);

        Button report = button("إنشاء ونسخ تقرير الحالة الكامل");
        report.setOnClickListener(v -> generateAndCopyReport(report));
        content.addView(report);

        Button stop = button("إيقاف الحماية وإرجاع تغييرات الجلسة");
        stop.setOnClickListener(v -> stopGuard());
        content.addView(stop);

        Button refresh = button("تحديث الحالة");
        refresh.setOnClickListener(v -> refreshStatus());
        content.addView(refresh);

        TextView note = new TextView(this);
        note.setText("الخصوصية: تقرير FixHUA لا يجمع الحسابات أو الرسائل أو الصور أو جهات الاتصال أو الموقع أو Android ID أو الرقم التسلسلي أو IP أو كلمات السر. FixHUA لا يعمل Root للجهاز ولا يفتح Bootloader، ولا يعطل حماية الحرارة، ولا يلمس Play Integrity/DRM.");
        note.setTextSize(13);
        note.setPadding(0, dp(12), 0, dp(24));
        content.addView(note);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        return scroll;
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(16);
        b.setMinHeight(dp(50));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp.bottomMargin = dp(7);
        b.setLayoutParams(lp);
        return b;
    }

    private void refreshStatus() {
        String mode = GuardService.getMode(this);
        StringBuilder b = new StringBuilder();
        b.append("الحماية: ").append(GuardService.active ? "مفعلة ✓" : "متوقفة").append('\n');
        b.append("الوضع: ").append(modeLabel(mode)).append('\n');
        b.append("Root: ").append(RootSessionController.lastStatus(this)).append('\n');
        b.append("Usage Access: ")
                .append(GuardService.hasUsageAccess(this) ? "مفعل ✓" : "غير مفعل — تعمل حماية Basic")
                .append('\n');
        b.append("GBox: ").append(packageSummary(GBOX)).append('\n');
        b.append("GBox battery exemption: ").append(ignoreState(GBOX)).append('\n');
        b.append("FixHUA battery exemption: ").append(ignoreState(getPackageName())).append('\n');
        b.append("آخر حالة تشغيل: ").append(GuardService.lastState(this)).append('\n');
        b.append("الجلسات: ").append(GuardService.stat(this, "sessions")).append('\n');
        b.append("وقت الحماية الكلي: ").append(formatDuration(GuardService.stat(this, "total_guard_ms"))).append('\n');
        b.append("آخر peak thermal: ").append(GuardService.stat(this, "last_peak_thermal")).append('\n');
        b.append("التشخيص اللحظي: ").append(DeepDiagnosticService.active ? "يعمل ✓" : "متوقف").append('\n');
        b.append("عينات التشخيص: ").append(DeepDiagnosticService.sampleCount(this)).append('\n');
        long diagStarted = DeepDiagnosticService.startedAt(this);
        if (diagStarted > 0L) b.append("بدأ التشخيص: ").append(formatTimestamp(diagStarted)).append('\n');
        statusView.setText(b.toString());
        modeButton.setText("وضع الحماية: " + modeLabel(mode) + " — اضغط للتغيير");
        diagnosticButton.setText(DeepDiagnosticService.active
                ? "إيقاف جلسة التشخيص وحفظ السجل"
                : "ابدأ جلسة تشخيص دقيقة (قراءة كل ثانية)");
    }

    private void probeRoot() {
        Toast.makeText(this, "سيظهر طلب Root إذا كان su متاحًا", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            boolean root = RootSessionController.probeAndStore(getApplicationContext());
            runOnUiThread(() -> {
                Toast.makeText(
                        MainActivity.this,
                        root ? "تم تأكيد Root ✓" : "Root غير متاح — سيعمل الوضع العادي",
                        Toast.LENGTH_LONG
                ).show();
                refreshStatus();
            });
        }, "FixHUA-RootProbe").start();
    }

    private void startProtected() {
        if (!isInstalled(GBOX)) {
            Toast.makeText(this, "GBox غير مثبت على الهاتف", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            startForegroundService(new Intent(this, GuardService.class));
        } catch (Throwable t) {
            Toast.makeText(this, "تعذر بدء الحماية: " + t.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
            return;
        }
        statusView.postDelayed(this::launchGBox, 250);
        statusView.postDelayed(this::refreshStatus, 850);
    }

    private void launchGBox() {
        Intent launch = getPackageManager().getLaunchIntentForPackage(GBOX);
        if (launch == null) {
            Toast.makeText(this, "تعذر العثور على واجهة تشغيل GBox", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(launch);
        } catch (Throwable t) {
            Toast.makeText(this, "فشل فتح GBox: " + t.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
        }
    }

    private void toggleDeepDiagnostics() {
        if (DeepDiagnosticService.active) {
            Intent stop = new Intent(this, DeepDiagnosticService.class).setAction(DeepDiagnosticService.ACTION_STOP);
            try {
                startService(stop);
            } catch (Throwable t) {
                stopService(new Intent(this, DeepDiagnosticService.class));
            }
            Toast.makeText(this, "تم إيقاف جلسة التشخيص وحفظ السجل", Toast.LENGTH_SHORT).show();
            statusView.postDelayed(this::refreshStatus, 500);
            return;
        }
        try {
            startForegroundService(new Intent(this, DeepDiagnosticService.class));
            Toast.makeText(this, "بدأ التشخيص: قراءة كل ثانية لمدة أقصاها 20 دقيقة", Toast.LENGTH_LONG).show();
            statusView.postDelayed(this::refreshStatus, 600);
        } catch (Throwable t) {
            Toast.makeText(this, "تعذر بدء التشخيص: " + t.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
        }
    }

    private void markLagNow() {
        DiagnosticTimeline.recordEvent(this, "user_lag_marker", "user_pressed_lag_marker_now");
        if (DeepDiagnosticService.active) {
            Toast.makeText(this, "تم تسجيل لحظة التهنيج ✓ — استمر قليلًا قبل إيقاف التشخيص", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "تم تسجيل العلامة، لكن جلسة التشخيص غير مفعلة؛ شغّلها للحصول على عينات قبل/بعد المشكلة", Toast.LENGTH_LONG).show();
        }
    }

    private void generateAndCopyReport(Button reportButton) {
        reportButton.setEnabled(false);
        reportButton.setText("جارٍ إنشاء التقرير…");
        DiagnosticTimeline.recordEvent(this, "report_requested", "user_requested_copyable_report");
        new Thread(() -> {
            try {
                String report = DiagnosticBundle.build(getApplicationContext());
                runOnUiThread(() -> {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (clipboard == null) {
                        Toast.makeText(MainActivity.this, "تعذر الوصول إلى الحافظة", Toast.LENGTH_LONG).show();
                    } else {
                        clipboard.setPrimaryClip(ClipData.newPlainText("FixHUA diagnostic report", report));
                        Toast.makeText(MainActivity.this, "تم إنشاء التقرير ونسخه للحافظة ✓ — الصقه وأرسله لي", Toast.LENGTH_LONG).show();
                    }
                    reportButton.setEnabled(true);
                    reportButton.setText("إنشاء ونسخ تقرير الحالة الكامل");
                    refreshStatus();
                });
            } catch (Throwable t) {
                runOnUiThread(() -> {
                    reportButton.setEnabled(true);
                    reportButton.setText("إنشاء ونسخ تقرير الحالة الكامل");
                    Toast.makeText(MainActivity.this, "فشل إنشاء التقرير: " + t.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
                });
            }
        }, "FixHUA-ReportBuilder").start();
    }

    private void stopGuard() {
        Intent stop = new Intent(this, GuardService.class).setAction(GuardService.ACTION_STOP);
        try {
            startService(stop);
        } catch (Throwable ignored) {
            stopService(new Intent(this, GuardService.class));
        }
        Toast.makeText(this, "تم إيقاف الحماية وسيتم إرجاع تغييرات الجلسة", Toast.LENGTH_SHORT).show();
        statusView.postDelayed(this::refreshStatus, 900);
    }

    private void cycleMode() {
        String current = GuardService.getMode(this);
        String next;
        if (GuardService.MODE_BALANCED.equals(current)) next = GuardService.MODE_STABILITY;
        else if (GuardService.MODE_STABILITY.equals(current)) next = GuardService.MODE_ECO;
        else next = GuardService.MODE_BALANCED;
        GuardService.setMode(this, next);
        Toast.makeText(this, "تم اختيار: " + modeLabel(next), Toast.LENGTH_SHORT).show();
        refreshStatus();
    }

    private String modeLabel(String mode) {
        if (GuardService.MODE_STABILITY.equals(mode)) return "أقصى استقرار";
        if (GuardService.MODE_ECO.equals(mode)) return "اقتصادي";
        return "متوازن";
    }

    private String packageSummary(String pkg) {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(pkg, 0);
            return "موجود — " + (info.versionName == null ? "?" : info.versionName);
        } catch (PackageManager.NameNotFoundException e) {
            return "غير موجود";
        }
    }

    private boolean isInstalled(String pkg) {
        try {
            getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private String ignoreState(String pkg) {
        if (!pkg.equals(getPackageName()) && !isInstalled(pkg)) return "غير مثبت";
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            return pm.isIgnoringBatteryOptimizations(pkg) ? "غير مقيد ✓" : "مقيد ⚠";
        } catch (Throwable t) {
            return "غير معروف";
        }
    }

    private void openUsageAccess() {
        try {
            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
            Toast.makeText(this, "فعّل Usage Access لـ FixHUA", Toast.LENGTH_LONG).show();
        } catch (Throwable t) {
            Toast.makeText(this, "تعذر فتح Usage Access", Toast.LENGTH_LONG).show();
        }
    }

    private void requestOwnBatteryExemption() {
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm.isIgnoringBatteryOptimizations(getPackageName())) {
                Toast.makeText(this, "FixHUA بالفعل غير مقيد بالبطارية", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Throwable t) {
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Throwable ignored) {
                Toast.makeText(this, "تعذر فتح إعدادات البطارية على هذا النظام", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void openHuaweiStartupManager() {
        String[][] targets = new String[][]{
                {"com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"},
                {"com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"},
                {"com.huawei.systemmanager", "com.huawei.systemmanager.power.ui.HwPowerManagerActivity"}
        };
        for (String[] target : targets) {
            try {
                Intent i = new Intent();
                i.setComponent(new ComponentName(target[0], target[1]));
                startActivity(i);
                Toast.makeText(this, "اسمح لـ GBox وFixHUA بالتشغيل التلقائي والعمل في الخلفية", Toast.LENGTH_LONG).show();
                return;
            } catch (ActivityNotFoundException ignored) {
            } catch (Throwable ignored) {
            }
        }
        try {
            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            Toast.makeText(this, "لم أجد مدير Huawei المباشر؛ فتحت إعدادات البطارية العامة بدلًا منه", Toast.LENGTH_LONG).show();
        } catch (Throwable ignored) {
            Toast.makeText(this, "تعذر فتح إعدادات الخلفية على هذا الإصدار", Toast.LENGTH_LONG).show();
        }
    }

    private String formatDuration(long ms) {
        long minutes = Math.max(0L, ms / 60_000L);
        if (minutes < 60L) return minutes + " دقيقة";
        return (minutes / 60L) + "س " + (minutes % 60L) + "د";
    }

    private String formatTimestamp(long epochMs) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date(epochMs));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
