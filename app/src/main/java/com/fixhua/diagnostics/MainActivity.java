package com.fixhua.diagnostics;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

public class MainActivity extends Activity {
    private TextView reportView;
    private String snapshot = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 42);
        }
        setContentView(buildUi());
        runSnapshot();
    }

    private View buildUi() {
        int pad = dp(16);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("FixHUA Diagnostics v0.1");
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView privacy = new TextView(this);
        privacy.setText("Read-only diagnostic build. It does not collect accounts, contacts, messages, photos, location, Android ID, serial number, or IP address. Nothing is uploaded automatically.");
        privacy.setPadding(0, dp(8), 0, dp(12));
        root.addView(privacy);

        Button scan = button("Run fresh snapshot");
        scan.setOnClickListener(v -> runSnapshot());
        root.addView(scan);

        Button monitor = button("Start 5-minute monitor & open GBox");
        monitor.setOnClickListener(v -> startMonitorAndGBox());
        root.addView(monitor);

        Button stop = button("Stop monitor");
        stop.setOnClickListener(v -> stopMonitor());
        root.addView(stop);

        Button load = button("Load session monitor log");
        load.setOnClickListener(v -> showCombinedReport());
        root.addView(load);

        Button share = button("Share report with ChatGPT / another app");
        share.setOnClickListener(v -> shareReport());
        root.addView(share);

        ScrollView scroll = new ScrollView(this);
        reportView = new TextView(this);
        reportView.setTextSize(12);
        reportView.setTypeface(Typeface.MONOSPACE);
        reportView.setTextIsSelectable(true);
        reportView.setPadding(0, dp(12), 0, dp(24));
        scroll.addView(reportView);
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
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp.bottomMargin = dp(6);
        b.setLayoutParams(lp);
        return b;
    }

    private void runSnapshot() {
        reportView.setText("Collecting…");
        new Thread(() -> {
            String result = DiagnosticCollector.collect(this);
            runOnUiThread(() -> {
                snapshot = result;
                reportView.setText(result);
            });
        }).start();
    }

    private void startMonitorAndGBox() {
        Intent service = new Intent(this, MonitorService.class);
        startForegroundService(service);
        Toast.makeText(this, "Monitoring started for up to 5 minutes", Toast.LENGTH_SHORT).show();

        reportView.postDelayed(() -> {
            Intent launch = getPackageManager().getLaunchIntentForPackage("com.gbox.android");
            if (launch != null) {
                try {
                    startActivity(launch);
                } catch (Throwable t) {
                    Toast.makeText(this, "GBox launch failed: " + t.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
                }
            } else {
                Toast.makeText(this, "GBox package com.gbox.android was not found. Monitor is still running.", Toast.LENGTH_LONG).show();
            }
        }, 700);
    }

    private void stopMonitor() {
        Intent stop = new Intent(this, MonitorService.class);
        stop.setAction(MonitorService.ACTION_STOP);
        startService(stop);
        Toast.makeText(this, "Monitor stop requested", Toast.LENGTH_SHORT).show();
    }

    private void showCombinedReport() {
        String combined = combinedReport();
        reportView.setText(combined);
    }

    private void shareReport() {
        String combined = combinedReport();
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, "FixHUA diagnostic report");
        send.putExtra(Intent.EXTRA_TEXT, combined);
        startActivity(Intent.createChooser(send, "Share FixHUA report"));
    }

    private String combinedReport() {
        if (snapshot.isEmpty()) {
            snapshot = DiagnosticCollector.collect(this);
        }
        String log = readMonitorLog();
        return snapshot + "\n\n=== GBOX SESSION MONITOR ===\n" + log;
    }

    private String readMonitorLog() {
        File f = new File(getFilesDir(), MonitorService.FILE_NAME);
        if (!f.isFile()) return "No monitoring session has been recorded yet.\n";
        StringBuilder b = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = r.readLine()) != null) b.append(line).append('\n');
        } catch (Exception e) {
            return "Unable to read monitor log: " + e.getClass().getSimpleName() + "\n";
        }
        return b.toString();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
