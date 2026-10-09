package com.fixhua.diagnostics;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.ActivityNotFoundException;
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

public class MainActivity extends Activity {
    private static final String GBOX = "com.gbox.android";
    private static final String MICROG = "com.google.android.gms";
    private static final String PLAY_STORE = "com.android.vending";

    private TextView statusView;
    private TextView detailsView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 42);
        }
        setContentView(buildUi());
        refreshStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (statusView != null) refreshStatus();
    }

    private View buildUi() {
        int pad = dp(18);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        TextView title = new TextView(this);
        title.setText("FixHUA Guard v1.0");
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("حماية تشغيل GBox وتقليل التعليق الناتج عن النوم الخلفي وطاقة Wi‑Fi وحالات التشغيل العالقة. يعمل بدون Root.");
        subtitle.setTextSize(15);
        subtitle.setPadding(0, dp(8), 0, dp(14));
        root.addView(subtitle);

        statusView = new TextView(this);
        statusView.setTextSize(15);
        statusView.setTypeface(Typeface.MONOSPACE);
        statusView.setPadding(dp(12), dp(12), dp(12), dp(12));
        root.addView(statusView);

        Button protect = button("تشغيل GBox بحماية FixHUA");
        protect.setOnClickListener(v -> startProtected(false));
        root.addView(protect);

        Button repair = button("إصلاح تشغيل عالق ثم فتح GBox");
        repair.setOnClickListener(v -> startProtected(true));
        root.addView(repair);

        Button stability = button("إعداد الاستقرار مرة واحدة");
        stability.setOnClickListener(v -> openHuaweiStartupManager());
        root.addView(stability);

        Button selfBattery = button("السماح لـ FixHUA بالعمل دون تقييد البطارية");
        selfBattery.setOnClickListener(v -> requestOwnBatteryExemption());
        root.addView(selfBattery);

        Button stop = button("إيقاف حماية FixHUA");
        stop.setOnClickListener(v -> stopGuard());
        root.addView(stop);

        Button refresh = button("تحديث حالة التوافق");
        refresh.setOnClickListener(v -> refreshStatus());
        root.addView(refresh);

        Button report = button("عرض التقرير التقني");
        report.setOnClickListener(v -> loadTechnicalReport());
        root.addView(report);

        ScrollView scroll = new ScrollView(this);
        detailsView = new TextView(this);
        detailsView.setTextSize(12);
        detailsView.setTypeface(Typeface.MONOSPACE);
        detailsView.setTextIsSelectable(true);
        detailsView.setPadding(0, dp(12), 0, dp(30));
        scroll.addView(detailsView);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        return root;
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
        StringBuilder b = new StringBuilder();
        b.append("الحماية: ").append(GuardService.active ? "مفعلة" : "متوقفة").append('\n');
        b.append("GBox: ").append(packageSummary(GBOX)).append('\n');
        b.append("microG: ").append(packageSummary(MICROG)).append('\n');
        b.append("Play Store: ").append(packageSummary(PLAY_STORE)).append('\n');
        b.append("\nقيود البطارية:\n");
        b.append("• GBox: ").append(ignoreState(GBOX)).append('\n');
        b.append("• microG: ").append(ignoreState(MICROG)).append('\n');
        b.append("• Play Store: ").append(ignoreState(PLAY_STORE)).append('\n');
        b.append("• FixHUA: ").append(ignoreState(getPackageName())).append('\n');
        statusView.setText(b.toString());
    }

    private String packageSummary(String pkg) {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(pkg, 0);
            String version = info.versionName == null ? "?" : info.versionName;
            return "موجود — " + version;
        } catch (PackageManager.NameNotFoundException e) {
            return "غير موجود";
        }
    }

    private String ignoreState(String pkg) {
        if (!isInstalled(pkg) && !pkg.equals(getPackageName())) return "غير مثبت";
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            return pm.isIgnoringBatteryOptimizations(pkg) ? "غير مقيد ✓" : "مقيد ⚠";
        } catch (Throwable t) {
            return "غير معروف";
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

    private void startProtected(boolean cleanFirst) {
        if (!isInstalled(GBOX)) {
            Toast.makeText(this, "GBox غير مثبت على الهاتف", Toast.LENGTH_LONG).show();
            return;
        }

        if (cleanFirst) {
            try {
                ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
                am.killBackgroundProcesses(GBOX);
            } catch (Throwable ignored) {
            }
        }

        try {
            startForegroundService(new Intent(this, GuardService.class));
        } catch (Throwable t) {
            Toast.makeText(this, "تعذر بدء الحماية: " + t.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
        }

        statusView.postDelayed(() -> {
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
        }, cleanFirst ? 900 : 350);
    }

    private void stopGuard() {
        Intent stop = new Intent(this, GuardService.class).setAction(GuardService.ACTION_STOP);
        try {
            startService(stop);
        } catch (Throwable ignored) {
            stopService(new Intent(this, GuardService.class));
        }
        Toast.makeText(this, "تم طلب إيقاف الحماية", Toast.LENGTH_SHORT).show();
        statusView.postDelayed(this::refreshStatus, 500);
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
            openBatteryOptimizationList();
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
                Toast.makeText(this, "اجعل GBox وmicroG وFixHUA مسموحًا لها بالعمل في الخلفية", Toast.LENGTH_LONG).show();
                return;
            } catch (ActivityNotFoundException ignored) {
            } catch (Throwable ignored) {
            }
        }
        openBatteryOptimizationList();
    }

    private void openBatteryOptimizationList() {
        try {
            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        } catch (Throwable t) {
            openGBoxAppInfo();
        }
    }

    private void openGBoxAppInfo() {
        try {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            i.setData(Uri.parse("package:" + GBOX));
            startActivity(i);
        } catch (Throwable ignored) {
        }
    }

    private void loadTechnicalReport() {
        detailsView.setText("جاري جمع التقرير…");
        new Thread(() -> {
            String report = DiagnosticCollector.collect(this);
            runOnUiThread(() -> detailsView.setText(report));
        }).start();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
