package com.fixhua.diagnostics;

import android.Manifest;
import android.app.Activity;
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
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final String GBOX_PACKAGE = "com.gbox.android";

    private TextView statusView;
    private Button turboButton;
    private Button stopButton;
    private Button protectionButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
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

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(pad, pad, pad, dp(28));

        TextView title = new TextView(this);
        title.setText("FixHUA Turbo v1.0");
        title.setTextSize(27);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        content.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Host-side stability mode for Huawei + GBox");
        subtitle.setTextSize(16);
        subtitle.setPadding(0, dp(4), 0, dp(14));
        content.addView(subtitle);

        TextView explanation = new TextView(this);
        explanation.setText(
                "Turbo is a real runtime guard, not a RAM cleaner. While active it keeps the CPU awake, " +
                "requests Android's high-performance Wi-Fi mode, maintains a foreground guard and " +
                "re-acquires the locks if Huawei releases them. Use it before opening GBox."
        );
        explanation.setTextSize(14);
        explanation.setPadding(dp(12), dp(12), dp(12), dp(12));
        content.addView(explanation);

        statusView = new TextView(this);
        statusView.setTypeface(Typeface.MONOSPACE);
        statusView.setTextSize(14);
        statusView.setPadding(dp(12), dp(14), dp(12), dp(14));
        content.addView(statusView);

        turboButton = button("START TURBO & OPEN GBOX");
        turboButton.setOnClickListener(v -> startTurboAndOpenGBox());
        content.addView(turboButton);

        stopButton = button("Stop Turbo");
        stopButton.setOnClickListener(v -> stopTurbo());
        content.addView(stopButton);

        protectionButton = button("Protect FixHUA from battery optimization");
        protectionButton.setOnClickListener(v -> requestFixhuaProtection());
        content.addView(protectionButton);

        Button huaweiLaunch = button("Open Huawei background / app-launch settings");
        huaweiLaunch.setOnClickListener(v -> openHuaweiStartupManager());
        content.addView(huaweiLaunch);

        Button openGBox = button("Open GBox without Turbo");
        openGBox.setOnClickListener(v -> launchGBox());
        content.addView(openGBox);

        TextView note = new TextView(this);
        note.setText(
                "Recommended one-time setup: protect FixHUA from battery optimization and allow it to run in the background in Huawei settings. " +
                "Your GBox package is already power-whitelisted on the device report you supplied.\n\n" +
                "Turbo deliberately trades extra battery use for a more stable active GBox session. Stop it from this screen or the persistent notification when you finish."
        );
        note.setTextSize(13);
        note.setPadding(dp(4), dp(12), dp(4), 0);
        content.addView(note);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        return scroll;
    }

    private Button button(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setMinHeight(dp(52));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.bottomMargin = dp(8);
        button.setLayoutParams(params);
        return button;
    }

    private void startTurboAndOpenGBox() {
        if (!isPackageInstalled(GBOX_PACKAGE)) {
            Toast.makeText(this, "GBox is not installed on the host system.", Toast.LENGTH_LONG).show();
            return;
        }

        Intent turbo = new Intent(this, TurboService.class);
        turbo.setAction(TurboService.ACTION_START);
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(turbo);
            } else {
                startService(turbo);
            }
        } catch (Throwable t) {
            Toast.makeText(this, "Unable to start Turbo: " + t.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
            return;
        }

        Toast.makeText(this, "FixHUA Turbo active", Toast.LENGTH_SHORT).show();
        statusView.postDelayed(() -> {
            refreshStatus();
            launchGBox();
        }, 450);
    }

    private void stopTurbo() {
        try {
            Intent stop = new Intent(this, TurboService.class);
            stop.setAction(TurboService.ACTION_STOP);
            startService(stop);
            stopService(new Intent(this, TurboService.class));
        } catch (Throwable ignored) {
        }
        statusView.postDelayed(this::refreshStatus, 250);
        Toast.makeText(this, "Turbo stopped", Toast.LENGTH_SHORT).show();
    }

    private void launchGBox() {
        Intent launch = getPackageManager().getLaunchIntentForPackage(GBOX_PACKAGE);
        if (launch == null) {
            Toast.makeText(this, "GBox launch activity was not found.", Toast.LENGTH_LONG).show();
            return;
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(launch);
        } catch (Throwable t) {
            Toast.makeText(this, "GBox launch failed: " + t.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
        }
    }

    private void requestFixhuaProtection() {
        if (isIgnoringBatteryOptimizations(getPackageName())) {
            Toast.makeText(this, "FixHUA is already excluded from battery optimization.", Toast.LENGTH_LONG).show();
            refreshStatus();
            return;
        }

        Intent request = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
        request.setData(Uri.parse("package:" + getPackageName()));
        try {
            startActivity(request);
        } catch (Throwable ignored) {
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Throwable t) {
                openOwnAppDetails();
            }
        }
    }

    private void openHuaweiStartupManager() {
        String[][] components = new String[][]{
                {"com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"},
                {"com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"}
        };

        for (String[] component : components) {
            try {
                Intent intent = new Intent();
                intent.setComponent(new ComponentName(component[0], component[1]));
                startActivity(intent);
                return;
            } catch (Throwable ignored) {
            }
        }

        Toast.makeText(this, "Huawei manager screen is protected on this ROM; opening FixHUA app settings instead.", Toast.LENGTH_LONG).show();
        openOwnAppDetails();
    }

    private void openOwnAppDetails() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + getPackageName()));
        try {
            startActivity(intent);
        } catch (Throwable ignored) {
        }
    }

    private void refreshStatus() {
        boolean gboxInstalled = isPackageInstalled(GBOX_PACKAGE);
        boolean gboxProtected = isIgnoringBatteryOptimizations(GBOX_PACKAGE);
        boolean fixhuaProtected = isIgnoringBatteryOptimizations(getPackageName());
        boolean turbo = TurboService.isRunning();

        StringBuilder status = new StringBuilder();
        status.append("DEVICE       : ")
                .append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
        status.append("ANDROID      : ").append(Build.VERSION.RELEASE)
                .append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n");
        status.append("GBOX         : ")
                .append(gboxInstalled ? "installed " + packageVersion(GBOX_PACKAGE) : "not installed")
                .append('\n');
        status.append("GBOX POWER   : ").append(gboxProtected ? "protected" : "not protected").append('\n');
        status.append("FIXHUA POWER : ").append(fixhuaProtected ? "protected" : "needs one-time protection").append('\n');
        status.append("TURBO        : ").append(turbo ? "ACTIVE" : "stopped");

        statusView.setText(status.toString());

        if (turboButton != null) turboButton.setEnabled(gboxInstalled && !turbo);
        if (stopButton != null) stopButton.setEnabled(turbo);
        if (protectionButton != null) protectionButton.setEnabled(!fixhuaProtected);
    }

    private boolean isPackageInstalled(String packageName) {
        try {
            getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private String packageVersion(String packageName) {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(packageName, 0);
            return info.versionName == null ? "" : info.versionName;
        } catch (Throwable ignored) {
            return "";
        }
    }

    private boolean isIgnoringBatteryOptimizations(String packageName) {
        try {
            PowerManager manager = (PowerManager) getSystemService(POWER_SERVICE);
            return manager != null && manager.isIgnoringBatteryOptimizations(packageName);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
