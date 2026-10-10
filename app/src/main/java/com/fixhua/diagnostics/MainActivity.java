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
    private Button copyReportButton;

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
        if (intent == null) return;
        if (GuardService.ACTION_REOPEN_GBOX.equals(intent.getAction())) {
            statusView.postDelayed(this::launchGBox, 150);
        } else if (DeepDiagnosticService.ACTION_VIEW_REPORT.equals(intent.getAction())) {
            Toast.makeText(this,
                    DeepDiagnosticService.hasLatestReport(this)
                            ? "جلسة التشخيص اكتملت — التقرير جاهز للنسخ"
                            : "الجلسة انتهت لكن لا يوجد تقرير محفوظ",
                    Toast.LENGTH_LONG).show();
        }
    }

    private View buildUi() {
        int pad = dp(18);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(pad, pad, pad, pad);
        content.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        TextView title = new TextView(this);
        title.setText("FixHUA Auto System Diagnostics v3.3");
        title.setTextSize(25);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        content.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("تشخيص تلقائي للهاتف كله: تسجيل خفيف على thread مستقل، كشف التهنيج آليًا، رفع مؤقت لدقة القياس أثناء الحادثة، تقرير نهائي وإشعار عند اكتماله. لا يوجد زر يدوي للتهنيج.");
        subtitle.setTextSize(15);
        subtitle.setPadding(0, dp(8), 0, dp(14));
        content.addView(subtitle);

        statusView = new TextView(this);
        statusView.setTextSize(14);
        statusView.setTypeface(Typeface.MONOSPACE);
        statusView.setPadding(dp(12), dp(12), dp(12), dp(12));
        content.addView(statusView);

        Button rootProbe = button("فحص صلاحية Root المتاحة");
        rootProbe.setOnClickListener(v -> probeRoot());
        content.addView(rootProbe);

        Button start = button("ابدأ Root Guard وافتح GBox");
        start.setOnClickListener(v -> startProtected());
        content.addView(start);

        modeButton = button("");
        modeButton.setOnClickListener(v -> cycleMode());
        content.addView(modeButton);

        Button usage = button("تفعيل Usage Access لمراقبة تغيّر التطبيقات");
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

        copyReportButton = button("نسخ آخر تقرير مكتمل");
        copyReportButton.setOnClickListener(v -> copyLatestReport());
        content.addView(copyReportButton);

        Button stop = button("إيقاف الحماية وإرجاع تغييرات الجلسة");
        stop.setOnClickListener(v -> stopGuard());
        content.addView(stop);

        Button refresh = button("تحديث الحالة");
        refresh.setOnClickListener(v -> refreshStatus());
        content.addView(refresh);

        TextView note = new TextView(this);
        note.setText("حدود الرؤية: بدون Root/ADB لا يستطيع Android لتطبيق عادي كشف كل خدمات وDrivers وBinder أو سجلات التطبيقات الأخرى. FixHUA يسجل كل ما تسمح به الواجهات المتاحة ويضع UNAVAILABLE بدل اختراع قيمة. عند منح Usage Access قد يسجل أسماء حزم التطبيقات وتغيّر foreground، لكنه لا يجمع محتوى الرسائل أو الصور أو الحسابات أو كلمات السر.");
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
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(7);
        b.setLayoutParams(lp);
        return b;
    }

    private void refreshStatus() {
        String mode = GuardService.getMode(this);
        boolean usage = UsageAccessProbe.hasAccess(this);
        StringBuilder b = new StringBuilder();
        b.append("الحماية: ").append(GuardService.active ? "مفعلة ✓" : "متوقفة").append('\n');
        b.append("الوضع: ").append(modeLabel(mode)).append('\n');
        b.append("Root: ").append(RootSessionController.lastStatus(this)).append('\n');
        b.append("Usage Access self-test: ").append(usage ? "PASS ✓" : "غير متاح").append('\n');
        b.append("Foreground الآن: ").append(usage ? safe(UsageAccessProbe.currentForegroundPackage(this)) : "UNAVAILABLE").append('\n');
        b.append("GBox: ").append(packageSummary(GBOX)).append('\n');
        b.append("GBox battery exemption: ").append(ignoreState(GBOX)).append('\n');
        b.append("FixHUA battery exemption: ").append(ignoreState(getPackageName())).append('\n');
        b.append("آخر حالة Guard: ").append(GuardService.lastState(this)).append('\n');
        b.append("التشخيص التلقائي: ").append(DeepDiagnosticService.active ? "يعمل ✓" : "متوقف").append('\n');
        b.append("حالة الكاشف: ").append(DeepDiagnosticService.detectorState(this)).append('\n');
        b.append("العينات: ").append(DeepDiagnosticService.sampleCount(this)).append('\n');
        b.append("الحوادث المكتملة: ").append(DeepDiagnosticService.incidentCount(this)).append('\n');
        long diagStarted = DeepDiagnosticService.startedAt(this);
        if (diagStarted > 0L) b.append("بدأ التشخيص: ").append(formatTimestamp(diagStarted)).append('\n');
        b.append("آخر تقرير: ").append(DeepDiagnosticService.hasLatestReport(this) ? "جاهز ✓" : "غير موجود").append('\n');
        statusView.setText(b.toString());

        modeButton.setText("وضع الحماية: " + modeLabel(mode) + " — اضغط للتغيير");
        diagnosticButton.setText(DeepDiagnosticService.active
                ? "إنهاء جلسة التشخيص وبناء التقرير"
                : "ابدأ جلسة التشخيص التلقائي (حتى 30 دقيقة)");
        copyReportButton.setEnabled(DeepDiagnosticService.hasLatestReport(this));
    }

    private void probeRoot() {
        Toast.makeText(this, "سيظهر طلب Root فقط إذا كان su موجودًا بالفعل", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            boolean root = RootSessionController.probeAndStore(getApplicationContext());
            runOnUiThread(() -> {
                Toast.makeText(this, root ? "تم تأكيد Root ✓" : "Root غير متاح — سيعمل الوضع العادي",
                        Toast.LENGTH_LONG).show();
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
            statusView.postDelayed(this::launchGBox, 250);
            statusView.postDelayed(this::refreshStatus, 850);
        } catch (Throwable t) {
            Toast.makeText(this, "تعذر بدء الحماية: " + t.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
        }
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
            try { startService(stop); } catch (Throwable t) { stopService(new Intent(this, DeepDiagnosticService.class)); }
            Toast.makeText(this, "جارٍ إنهاء الجلسة وبناء التقرير… سيصلك إشعار عند اكتماله", Toast.LENGTH_LONG).show();
            statusView.postDelayed(this::refreshStatus, 900);
            return;
        }
        try {
            startForegroundService(new Intent(this, DeepDiagnosticService.class));
            Toast.makeText(this, "بدأ التشخيص التلقائي — استخدم الهاتف طبيعيًا ولا تحتاج لأي زر أثناء التهنيج", Toast.LENGTH_LONG).show();
            statusView.postDelayed(this::refreshStatus, 700);
        } catch (Throwable t) {
            Toast.makeText(this, "تعذر بدء التشخيص: " + t.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
        }
    }

    private void copyLatestReport() {
        String report = DeepDiagnosticService.readLatestReport(this);
        if (report == null || report.isEmpty()) {
            Toast.makeText(this, "لا يوجد تقرير مكتمل حتى الآن", Toast.LENGTH_LONG).show();
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard == null) {
            Toast.makeText(this, "تعذر الوصول للحافظة", Toast.LENGTH_LONG).show();
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("FixHUA diagnostic report", report));
        Toast.makeText(this, "تم نسخ التقرير الكامل ✓", Toast.LENGTH_LONG).show();
    }

    private void stopGuard() {
        Intent stop = new Intent(this, GuardService.class).setAction(GuardService.ACTION_STOP);
        try { startService(stop); } catch (Throwable ignored) { stopService(new Intent(this, GuardService.class)); }
        Toast.makeText(this, "تم طلب إيقاف الحماية وإرجاع تغييرات الجلسة", Toast.LENGTH_SHORT).show();
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

    private void openUsageAccess() {
        try {
            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "تعذر فتح Usage Access على هذا النظام", Toast.LENGTH_LONG).show();
        }
    }

    private void openHuaweiStartupManager() {
        String[][] candidates = new String[][]{
                {"com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"},
                {"com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"}
        };
        for (String[] candidate : candidates) {
            try {
                Intent intent = new Intent();
                intent.setComponent(new ComponentName(candidate[0], candidate[1]));
                startActivity(intent);
                return;
            } catch (Throwable ignored) {
            }
        }
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
        } catch (Throwable t) {
            Toast.makeText(this, "تعذر فتح إعدادات الخلفية تلقائيًا", Toast.LENGTH_LONG).show();
        }
    }

    private void requestOwnBatteryExemption() {
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm.isIgnoringBatteryOptimizations(getPackageName())) {
                Toast.makeText(this, "FixHUA خارج تقييد البطارية بالفعل ✓", Toast.LENGTH_LONG).show();
                return;
            }
            startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName())));
        } catch (Throwable t) {
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Throwable ignored) {
                Toast.makeText(this, "تعذر فتح إعدادات البطارية", Toast.LENGTH_LONG).show();
            }
        }
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

    private String ignoreState(String pkg) {
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            return pm.isIgnoringBatteryOptimizations(pkg) ? "مستثنى ✓" : "مقيد";
        } catch (Throwable t) {
            return "UNKNOWN";
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

    private String formatTimestamp(long millis) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(millis));
    }

    private String safe(String value) {
        return value == null || value.trim().isEmpty() ? "UNAVAILABLE" : value;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
