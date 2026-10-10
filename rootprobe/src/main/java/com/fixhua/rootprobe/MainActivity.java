package com.fixhua.rootprobe;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private TextView reportView;
    private ProgressBar progress;
    private Button runButton;
    private Button copyButton;
    private Button shareButton;
    private volatile String latestReport = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
        runProbe();
    }

    private View buildUi() {
        int pad = dp(16);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("FixHUA Root Feasibility Probe");
        title.setTextSize(22f);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Read-only probe for bootloader, verified boot, OEM-unlock hints, partition layout, root state and diagnostic visibility. It does not reboot, flash, unlock, root, write properties, or touch partitions.");
        subtitle.setTextSize(14f);
        subtitle.setPadding(0, dp(8), 0, dp(12));
        root.addView(subtitle);

        progress = new ProgressBar(this);
        progress.setIndeterminate(true);
        root.addView(progress, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);

        runButton = button("Run again");
        runButton.setOnClickListener(v -> runProbe());
        actions.addView(runButton, weighted());

        copyButton = button("Copy report");
        copyButton.setEnabled(false);
        copyButton.setOnClickListener(v -> copyReport());
        actions.addView(copyButton, weighted());

        shareButton = button("Share report");
        shareButton.setEnabled(false);
        shareButton.setOnClickListener(v -> shareReport());
        actions.addView(shareButton, weighted());

        root.addView(actions);

        Button developerButton = button("Open Developer Options");
        developerButton.setOnClickListener(v -> openDeveloperOptions());
        root.addView(developerButton);

        TextView privacy = new TextView(this);
        privacy.setText("Privacy: this probe intentionally does not collect IMEI, serial number, Android ID, accounts, location, messages, photos, contacts, passwords, or user files.");
        privacy.setTextSize(12f);
        privacy.setPadding(0, dp(8), 0, dp(8));
        root.addView(privacy);

        reportView = new TextView(this);
        reportView.setText("Preparing read-only probe…");
        reportView.setTextSize(12f);
        reportView.setTypeface(Typeface.MONOSPACE);
        reportView.setTextIsSelectable(true);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(reportView);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(scroll, scrollParams);

        return root;
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        return b;
    }

    private LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
    }

    private void runProbe() {
        progress.setVisibility(View.VISIBLE);
        runButton.setEnabled(false);
        copyButton.setEnabled(false);
        shareButton.setEnabled(false);
        reportView.setText("Collecting read-only evidence…\nThis usually takes only a few seconds.");

        new Thread(() -> {
            String report;
            try {
                report = new ProbeCollector(this).collect();
            } catch (Throwable t) {
                report = "FIXHUA_ROOT_FEASIBILITY_PROBE\nschema=1\nprobe_error="
                        + t.getClass().getSimpleName() + ":" + String.valueOf(t.getMessage()) + "\n";
            }
            final String completed = report;
            runOnUiThread(() -> {
                latestReport = completed;
                reportView.setText(completed);
                progress.setVisibility(View.GONE);
                runButton.setEnabled(true);
                copyButton.setEnabled(true);
                shareButton.setEnabled(true);
            });
        }, "FixHUA-RootProbe").start();
    }

    private void copyReport() {
        if (latestReport.isEmpty()) return;
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("FixHUA Root Probe", latestReport));
            Toast.makeText(this, "Report copied", Toast.LENGTH_SHORT).show();
        }
    }

    private void shareReport() {
        if (latestReport.isEmpty()) return;
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, "FixHUA Root Feasibility Probe");
        send.putExtra(Intent.EXTRA_TEXT, latestReport);
        startActivity(Intent.createChooser(send, "Share diagnostic report"));
    }

    private void openDeveloperOptions() {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
        } catch (Throwable t) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (Throwable ignored) {
                Toast.makeText(this, "Could not open settings", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
