package com.fixhua.guard;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.PowerManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public final class MainActivity extends Activity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicInteger refreshGeneration = new AtomicInteger();

    private TextView summaryView;
    private TextView findingsView;
    private TextView scanMetaView;
    private Button fixNextButton;

    private volatile SystemSnapshot lastSnapshot;
    private volatile ReadinessEngine.Result lastResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        summaryView = findViewById(R.id.summary);
        findingsView = findViewById(R.id.findings);
        scanMetaView = findViewById(R.id.scanMeta);
        fixNextButton = findViewById(R.id.fixNext);

        findViewById(R.id.checkNow).setOnClickListener(v -> refresh(true));
        fixNextButton.setOnClickListener(v -> fixMostImportant());
        findViewById(R.id.launchGbox).setOnClickListener(v -> launchWithPreflight());
        findViewById(R.id.captureIncident).setOnClickListener(v -> captureIncidentSnapshot());
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
        summaryView.setText(R.string.scanning_device);
        executor.execute(() -> {
            SystemSnapshot snapshot = SystemSnapshot.capture(getApplicationContext());
            ReadinessEngine.Result result = ReadinessEngine.evaluate(snapshot);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || generation != refreshGeneration.get()) return;
                lastSnapshot = snapshot;
                lastResult = result;
                render(snapshot, result);
                if (userRequested) {
                    Toast.makeText(this, R.string.scan_updated, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void render(SystemSnapshot s, ReadinessEngine.Result result) {
        String status = statusLabel(result.status);
        String gboxState = !s.gboxInstalled
                ? getString(R.string.not_installed)
                : !s.gboxEnabled
                ? getString(R.string.disabled)
                : !s.gboxLaunchable
                ? getString(R.string.not_launchable)
                : s.gboxSuspended
                ? getString(R.string.suspended)
                : s.gboxVersion;

        StringBuilder summary = new StringBuilder();
        summary.append(getString(R.string.summary_status, status)).append('\n');
        summary.append(getString(R.string.summary_gbox, gboxState)).append('\n');
        summary.append(getString(R.string.summary_huawei, s.huaweiEnvironment ? getString(R.string.detected) : getString(R.string.uncertain))).append('\n');
        summary.append(getString(R.string.summary_network, networkLabel(s))).append('\n');
        summary.append(getString(R.string.summary_power_save, s.powerSaveMode ? getString(R.string.enabled) : getString(R.string.off))).append('\n');
        if (s.totalRamMb > 0) {
            summary.append(getString(R.string.summary_ram, s.availableRamMb, s.totalRamMb)).append('\n');
        } else {
            summary.append(getString(R.string.summary_ram_unknown)).append('\n');
        }
        if (s.totalStorageMb > 0) {
            summary.append(getString(R.string.summary_storage,
                    String.format(Locale.US, "%.0f%%", s.freeStorageFraction() * 100.0))).append('\n');
        } else {
            summary.append(getString(R.string.summary_storage_unknown)).append('\n');
        }
        summary.append(getString(R.string.summary_thermal, thermalLabel(s.thermalStatus)));
        if (s.batteryPercent >= 0) {
            summary.append('\n').append(getString(R.string.summary_battery, s.batteryPercent));
        }
        summaryView.setText(summary.toString());

        scanMetaView.setText(getString(R.string.last_scan,
                new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date())));

        StringBuilder findings = new StringBuilder();
        if (result.findings.isEmpty()) {
            findings.append(getString(R.string.no_obvious_issues));
        } else {
            findings.append(getString(R.string.results_header)).append("\n\n");
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
        if (actionable != null) {
            fixNextButton.setText(R.string.fix_most_important);
        } else if (result.status == ReadinessEngine.Status.READY) {
            fixNextButton.setText(R.string.no_action_needed);
        } else {
            fixNextButton.setText(R.string.no_safe_direct_action);
        }
    }

    private String statusLabel(ReadinessEngine.Status status) {
        switch (status) {
            case BLOCKED:
                return getString(R.string.status_blocked);
            case ATTENTION:
                return getString(R.string.status_attention);
            case READY:
            default:
                return getString(R.string.status_ready);
        }
    }

    private String networkLabel(SystemSnapshot s) {
        if (!s.networkObserved) return getString(R.string.unknown);
        if (!s.networkPresent) return getString(R.string.network_offline);
        if (!s.networkInternetCapable) return getString(R.string.network_no_internet);
        if (!s.networkValidated) return getString(R.string.network_unvalidated);
        return getString(R.string.network_validated);
    }

    private String thermalLabel(int thermalStatus) {
        if (thermalStatus < 0) return getString(R.string.unknown);
        switch (thermalStatus) {
            case PowerManager.THERMAL_STATUS_NONE:
                return getString(R.string.thermal_none);
            case PowerManager.THERMAL_STATUS_LIGHT:
                return getString(R.string.thermal_light);
            case PowerManager.THERMAL_STATUS_MODERATE:
                return getString(R.string.thermal_moderate);
            case PowerManager.THERMAL_STATUS_SEVERE:
                return getString(R.string.thermal_severe);
            case PowerManager.THERMAL_STATUS_CRITICAL:
                return getString(R.string.thermal_critical);
            case PowerManager.THERMAL_STATUS_EMERGENCY:
                return getString(R.string.thermal_emergency);
            case PowerManager.THERMAL_STATUS_SHUTDOWN:
                return getString(R.string.thermal_shutdown);
            default:
                return getString(R.string.unknown);
        }
    }

    private void fixMostImportant() {
        ReadinessEngine.Result result = lastResult;
        if (result == null) {
            refresh(true);
            return;
        }
        ReadinessEngine.Finding finding = result.firstActionable();
        if (finding == null) {
            Toast.makeText(this, R.string.no_safe_direct_action_toast, Toast.LENGTH_SHORT).show();
            return;
        }
        SettingsNavigator.fixFinding(this, finding);
    }

    private void launchWithPreflight() {
        final int generation = refreshGeneration.incrementAndGet();
        summaryView.setText(R.string.preflight_scanning);
        executor.execute(() -> {
            SystemSnapshot snapshot = SystemSnapshot.capture(getApplicationContext());
            ReadinessEngine.Result result = ReadinessEngine.evaluate(snapshot);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || generation != refreshGeneration.get()) return;
                lastSnapshot = snapshot;
                lastResult = result;
                render(snapshot, result);

                if (result.count(ReadinessEngine.Severity.BLOCKER) > 0) {
                    ReadinessEngine.Finding blocker = firstFinding(result, ReadinessEngine.Severity.BLOCKER);
                    AlertDialog.Builder dialog = new AlertDialog.Builder(this)
                            .setTitle(R.string.cannot_launch_gbox)
                            .setMessage(blocker == null ? getString(R.string.gbox_not_ready) : blocker.message)
                            .setNegativeButton(R.string.close, null);
                    if (blocker != null && blocker.isActionable()) {
                        dialog.setPositiveButton(R.string.open_relevant_setting,
                                (d, w) -> SettingsNavigator.fixFinding(this, blocker));
                    }
                    dialog.show();
                    return;
                }

                if (result.count(ReadinessEngine.Severity.WARNING) > 0) {
                    new AlertDialog.Builder(this)
                            .setTitle(R.string.before_launching_gbox)
                            .setMessage(buildWarningSummary(result))
                            .setPositiveButton(R.string.launch_now, (d, w) -> launchGBox())
                            .setNegativeButton(R.string.cancel, null)
                            .show();
                } else {
                    launchGBox();
                }
            });
        });
    }

    private ReadinessEngine.Finding firstFinding(ReadinessEngine.Result result, ReadinessEngine.Severity severity) {
        for (ReadinessEngine.Finding finding : result.findings) {
            if (finding.severity == severity) return finding;
        }
        return null;
    }

    private String buildWarningSummary(ReadinessEngine.Result result) {
        StringBuilder text = new StringBuilder(getString(R.string.warning_summary_intro)).append("\n\n");
        int shown = 0;
        for (ReadinessEngine.Finding finding : result.findings) {
            if (finding.severity != ReadinessEngine.Severity.WARNING) continue;
            text.append("• ").append(finding.message).append('\n');
            if (++shown >= 4) break;
        }
        text.append('\n').append(getString(R.string.warning_summary_outro));
        return text.toString();
    }

    private void launchGBox() {
        if (!SettingsNavigator.launchGBox(this)) {
            Toast.makeText(this, R.string.gbox_launch_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void captureIncidentSnapshot() {
        Toast.makeText(this, R.string.capturing_incident, Toast.LENGTH_SHORT).show();
        executor.execute(() -> {
            SystemSnapshot snapshot = SystemSnapshot.capture(getApplicationContext());
            ReadinessEngine.Result result = ReadinessEngine.evaluate(snapshot);
            String report = ReportBuilder.buildIncident(snapshot, result);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                lastSnapshot = snapshot;
                lastResult = result;
                render(snapshot, result);
                new AlertDialog.Builder(this)
                        .setTitle(R.string.incident_snapshot_ready)
                        .setMessage(R.string.incident_snapshot_note)
                        .setPositiveButton(R.string.share, (d, w) -> shareText(getString(R.string.incident_subject), report))
                        .setNeutralButton(R.string.copy_report, (d, w) -> copyReport(report))
                        .setNegativeButton(R.string.close, null)
                        .show();
            });
        });
    }

    private void showRecoveryCenter() {
        String[] items = getResources().getStringArray(R.array.recovery_actions);
        new AlertDialog.Builder(this)
                .setTitle(R.string.recovery_center)
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
                            captureIncidentSnapshot();
                            break;
                        case 8:
                            launchGBox();
                            break;
                        default:
                            break;
                    }
                })
                .setNegativeButton(R.string.close, null)
                .show();
    }

    private void shareFreshReport() {
        Toast.makeText(this, R.string.creating_report, Toast.LENGTH_SHORT).show();
        executor.execute(() -> {
            SystemSnapshot snapshot = SystemSnapshot.capture(getApplicationContext());
            ReadinessEngine.Result result = ReadinessEngine.evaluate(snapshot);
            String report = ReportBuilder.build(snapshot, result);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                shareText(getString(R.string.report_subject), report);
            });
        });
    }

    private void shareText(String subject, String report) {
        Intent share = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, subject)
                .putExtra(Intent.EXTRA_TEXT, report);
        try {
            startActivity(Intent.createChooser(share, getString(R.string.share_report)));
        } catch (RuntimeException e) {
            findingsView.setText(report);
            Toast.makeText(this, R.string.share_unavailable, Toast.LENGTH_LONG).show();
        }
    }

    private void copyReport(String report) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            Toast.makeText(this, R.string.copy_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.report_subject), report));
        Toast.makeText(this, R.string.report_copied, Toast.LENGTH_SHORT).show();
    }
}
