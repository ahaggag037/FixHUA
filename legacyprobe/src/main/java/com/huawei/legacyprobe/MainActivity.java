package com.huawei.legacyprobe;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int REQ_SAVE_REPORT = 1001;

    private TextView statusView;
    private TextView reportView;
    private Button saveButton;
    private Button shareButton;
    private Button copyButton;
    private volatile String report = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        runScan();
    }

    private void buildUi() {
        int pad = dp(16);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("Huawei Legacy Probe 1.0");
        title.setTextSize(24f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title, matchWrap());

        TextView privacy = new TextView(this);
        privacy.setText("فحص تشخيصي فقط. لا يجمع حسابات أو رسائل أو صور أو جهات اتصال أو IMEI أو Serial أو SSID/MAC/IP. التقرير يبقى على الجهاز حتى تحفظه أو تشاركه بنفسك.");
        privacy.setTextSize(14f);
        privacy.setPadding(0, dp(8), 0, dp(12));
        root.addView(privacy, matchWrap());

        Button scanButton = new Button(this);
        scanButton.setText("تشغيل الفحص الكامل");
        scanButton.setOnClickListener(v -> runScan());
        root.addView(scanButton, matchWrap());

        statusView = new TextView(this);
        statusView.setText("جاهز للفحص");
        statusView.setTextSize(16f);
        statusView.setPadding(0, dp(10), 0, dp(10));
        root.addView(statusView, matchWrap());

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER);

        saveButton = actionButton("حفظ TXT", v -> saveReport());
        shareButton = actionButton("مشاركة", v -> shareReport());
        copyButton = actionButton("نسخ", v -> copyReport());
        actions.addView(saveButton, weighted());
        actions.addView(shareButton, weighted());
        actions.addView(copyButton, weighted());
        root.addView(actions, matchWrap());

        reportView = new TextView(this);
        reportView.setTextSize(12f);
        reportView.setTypeface(Typeface.MONOSPACE);
        reportView.setTextIsSelectable(true);
        reportView.setPadding(0, dp(12), 0, dp(24));
        reportView.setText("سيظهر التقرير هنا بعد انتهاء الفحص.");

        ScrollView scroll = new ScrollView(this);
        scroll.addView(reportView, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f));

        setContentView(root);
        setActionButtonsEnabled(false);
    }

    private Button actionButton(String text, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(text);
        b.setOnClickListener(listener);
        return b;
    }

    private void runScan() {
        statusView.setText("جاري الفحص العميق…");
        reportView.setText("يتم الآن قراءة معلومات النظام المسموح بها بدون روت…");
        setActionButtonsEnabled(false);

        Thread worker = new Thread(() -> {
            final String result = ProbeCollector.collect(getApplicationContext());
            runOnUiThread(() -> {
                report = result;
                reportView.setText(result);
                statusView.setText("اكتمل الفحص. احفظ التقرير TXT أو شاركه ثم أرسله في المحادثة.");
                setActionButtonsEnabled(true);
            });
        }, "legacy-probe");
        worker.start();
    }

    private void saveReport() {
        if (report.isEmpty()) return;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE, defaultReportName());
        startActivityForResult(intent, REQ_SAVE_REPORT);
    }

    private void shareReport() {
        if (report.isEmpty()) return;
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, "Huawei Legacy Probe report");
        intent.putExtra(Intent.EXTRA_TEXT, report);
        startActivity(Intent.createChooser(intent, "مشاركة تقرير الفحص"));
    }

    private void copyReport() {
        if (report.isEmpty()) return;
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("Huawei Legacy Probe", report));
        Toast.makeText(this, "تم نسخ التقرير", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_SAVE_REPORT || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        try (OutputStream out = getContentResolver().openOutputStream(uri, "w")) {
            if (out == null) throw new IllegalStateException("Output stream unavailable");
            out.write(report.getBytes(StandardCharsets.UTF_8));
            out.flush();
            Toast.makeText(this, "تم حفظ التقرير", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "تعذر حفظ التقرير: " + e.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
        }
    }

    private String defaultReportName() {
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        return "Huawei-Legacy-Probe-" + stamp + ".txt";
    }

    private void setActionButtonsEnabled(boolean enabled) {
        saveButton.setEnabled(enabled);
        shareButton.setEnabled(enabled);
        copyButton.setEnabled(enabled);
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
