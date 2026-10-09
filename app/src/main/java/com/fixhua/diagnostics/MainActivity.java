package com.fixhua.diagnostics;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
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

import java.util.Locale;

public class MainActivity extends Activity {
    private static final String GBOX = "com.gbox.android";
    private static final String MICROG = "com.google.android.gms";
    private static final String PLAY_STORE = "com.android.vending";

    private TextView statusView;
    private TextView detailsView;
    private Button modeButton;

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
        if (statusView != null) refreshStatus();
    }

    private void handleIntent(Intent intent) {
        if (intent != null && GuardService.ACTION_REOPEN_GBOX.equals(intent.getAction())) {
            statusView.postDelayed(() -> startProtected(false), 150);
        }
    }

    private View buildUi() {
        int pad = dp(18);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        TextView title = new TextView(this);
        title.setText("FixHUA Guard v1.1");
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("حماية تكيفية لـ GBox: تقلل الحماية عندما لا تحتاجها، ترفعها أثناء الاستخدام، وتراقب الجلسة بدون Root.");
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

        Button repair = button("إصلاح جلسة عالقة ثم فتح GBox");
        repair.setOnClickListener(v -> startProtected(true));
        root.addView(repair);

        modeButton = button("");
        modeButton.setOnClickListener(v -> cycleMode());
        root.addView(modeButton);

        Button usage = button("تفعيل الاكتشاف الذكي للتطبيق النشط");
        usage.setOnClickListener(v -> openUsageAccess());
        root.addView(usage);

        Button stability = button("فتح إعدادات Huawei للعمل في الخلفية");
        stability.setOnClickListener(v -> openHuaweiStartupManager());
        root.addView(stability);

        Button selfBattery = button("السماح لـ FixHUA بالعمل دون تقييد البطارية");
        selfBattery.setOnClickListener(v -> requestOwnBatteryExemption());
        root.addView(selfBattery);

        Button batteryList = button("فحص إعدادات Battery Optimization");
        batteryList.setOnClickListener(v -> openBatteryOptimizationList());
        root.addView(batteryList);

        Button stop = button("إيقاف حماية FixHUA");
        stop.setOnClickListener(v -> stopGuard());
        root.addView(stop);

        Button refresh = button("تحديث الحالة والإحصائيات");
        refresh.setOnClickListener(v -> refreshStatus());
        root.addView(refresh);

        Button report = button("عرض التقرير التقني الكامل");
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
        String mode = GuardService.getMode(this);
        StringBuilder b = new StringBuilder();
        b.append("الحماية: ").append(GuardService.active ? "مفعلة ✓" : "متوقفة").append('\n');
        b.append("الوضع: ").append(modeLabel(mode)).append('\n');
        b.append("الاكتشاف الذكي: ")
                .append(GuardService.hasUsageAccess(this) ? "مفعل ✓" : "غير مفعل ⚠")
                .append('\n');
        b.append("GBox: ").append(packageSummary(GBOX)).append('\n');
        b.append("microG: ").append(packageSummary(MICROG)).append('\n');
        b.append("Play Store: ").append(packageSummary(PLAY_STORE)).append('\n');

        b.append("\nقيود البطارية:\n");
        b.append("• GBox: ").append(ignoreState(GBOX)).append('\n');
        b.append("• microG: ").append(ignoreState(MICROG)).append('\n');
        b.append("• Play Store: ").append(ignoreState(PLAY_STORE)).append('\n');
        b.append("• FixHUA: ").append(ignoreState(getPackageName())).append('\n');

        long sessions = GuardService.stat(this, "sessions");
        long total = GuardService.stat(this, "total_guard_ms");
        long last = GuardService.stat(this, "last_session_ms");
        long focus = GuardService.stat(this, "gbox_focus_entries");
        b.append("\nإحصائيات الحماية:\n");
        b.append("• الجلسات: ").append(sessions).append('\n');
        b.append("• وقت الحماية الكلي: ").append(formatDuration(total)).append('\n');
        b.append("• آخر جلسة: ").append(formatDuration(last)).append('\n');
        b.append("• مرات دخول GBox للواجهة: ").append(focus).append('\n');

        statusView.setText(b.toString());
        if (modeButton != null) {
            modeButton.setText("وضع الحماية: " + modeLabel(mode) + " — اضغط للتغيير");
        }
    }

    private String modeLabel(String mode) {
        if (GuardService.MODE_STABILITY.equals(mode)) return "أقصى استقرار";
        if (GuardService.MODE_ECO.equals(mode)) return "اقتصادي";
        return "متوازن";
    }

    private void cycleMode() {
        String current = GuardService.getMode(this);
        String next;
        if (GuardService.MODE_BALANCED.equals(current)) {
            next = GuardService.MODE_STABILITY;
        } else if (GuardService.MODE_STABILITY.equals(current)) {
            next = GuardService.MODE_ECO;
        } else {
            next = GuardService.MODE_BALANCED;
        }
        GuardService.setMode(this, next);
        Toast.makeText(this, "تم اختيار: " + modeLabel(next), Toast.LENGTH_SHORT).show();
        refreshStatus();
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
                SharedPreferences p = getSharedPreferences(GuardService.PREFS, MODE_PRIVATE);
                p.edit().putLong("manual_recoveries", p.getLong("manual_recoveries", 0L) + 1L).apply();
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
        }, cleanFirst ? 900 : 300);
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

    private void openUsageAccess() {
        try {
            Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
            startActivity(intent);
            Toast.makeText(this, "فعّل Usage Access لـ FixHUA حتى تعمل الحماية التكيفية", Toast.LENGTH_LONG).show();
        } catch (Throwable t) {
            Toast.makeText(this, "تعذر فتح إعداد Usage Access", Toast.LENGTH_LONG).show();
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
                Toast.makeText(this, "اسمح لـ GBox وmicroG وFixHUA بالعمل في الخلفية والتشغيل التلقائي", Toast.LENGTH_LONG).show();
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
            StringBuilder extra = new StringBuilder();
            extra.append("\n\n=== FIXHUA v1.1 RUNTIME ===\n");
            extra.append("mode=").append(GuardService.getMode(this)).append('\n');
            extra.append("usage_access=").append(GuardService.hasUsageAccess(this)).append('\n');
            extra.append("sessions=").append(GuardService.stat(this, "sessions")).append('\n');
            extra.append("total_guard_ms=").append(GuardService.stat(this, "total_guard_ms")).append('\n');
            extra.append("last_session_ms=").append(GuardService.stat(this, "last_session_ms")).append('\n');
            extra.append("gbox_focus_entries=").append(GuardService.stat(this, "gbox_focus_entries")).append('\n');
            extra.append("manual_recoveries=").append(GuardService.stat(this, "manual_recoveries")).append('\n');
            extra.append("last_peak_thermal=").append(GuardService.stat(this, "last_peak_thermal")).append('\n');
            String report = DiagnosticCollector.collect(this) + extra;
            runOnUiThread(() -> detailsView.setText(report));
        }).start();
    }

    private String formatDuration(long ms) {
        if (ms <= 0L) return "0 دقيقة";
        long minutes = ms / 60_000L;
        long hours = minutes / 60L;
        minutes %= 60L;
        if (hours > 0L) return String.format(Locale.US, "%dس %dد", hours, minutes);
        return String.format(Locale.US, "%d دقيقة", Math.max(1L, minutes));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
