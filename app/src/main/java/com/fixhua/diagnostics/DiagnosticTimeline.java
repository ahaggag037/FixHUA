package com.fixhua.diagnostics;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.BatteryManager;
import android.os.PowerManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Lightweight, read-only diagnostic recorder. Samples are written to private
 * app storage and are intended to be copied by the user in a diagnostic report.
 */
final class DiagnosticTimeline {
    private static final String FILE_NAME = "diagnostic_timeline.log";
    private static final long MAX_BYTES = 700_000L;
    private static volatile long previousCpuAvgKhz = -1L;

    private DiagnosticTimeline() {}

    static synchronized void recordSample(
            Context context,
            String mode,
            boolean gboxForeground,
            boolean recentGbox,
            boolean cpuLock,
            String wifiMode,
            int thermalStatus,
            boolean powerSave
    ) {
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            long totalRamMb = mi.totalMem / 1024L / 1024L;
            long availRamMb = mi.availMem / 1024L / 1024L;

            Psi psi = readMemoryPsi();
            CpuFreq cpu = readCpuFrequencies();
            double dropPct = 0.0;
            if (previousCpuAvgKhz > 0L && cpu.avgKhz > 0L && cpu.avgKhz < previousCpuAvgKhz) {
                dropPct = (previousCpuAvgKhz - cpu.avgKhz) * 100.0 / previousCpuAvgKhz;
            }
            if (cpu.avgKhz > 0L) previousCpuAvgKhz = cpu.avgKhz;

            NetworkState network = networkState(context);
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            boolean gboxBatteryExempt = false;
            try {
                gboxBatteryExempt = pm.isIgnoringBatteryOptimizations("com.gbox.android");
            } catch (Throwable ignored) {
            }

            BatteryState battery = batteryState(context);
            String cause = DiagnosticAnalyzer.classify(
                    thermalStatus,
                    availRamMb,
                    totalRamMb,
                    psi.someAvg10,
                    psi.fullAvg10,
                    dropPct,
                    network.validated,
                    gboxBatteryExempt
            );

            String line = "ts=" + now()
                    + " event=sample"
                    + " mode=" + safe(mode)
                    + " gboxFg=" + gboxForeground
                    + " gboxRecent=" + recentGbox
                    + " cpuLock=" + cpuLock
                    + " wifiMode=" + safe(wifiMode)
                    + " thermal=" + thermalStatus
                    + " powerSave=" + powerSave
                    + " ramAvailMB=" + availRamMb
                    + " ramTotalMB=" + totalRamMb
                    + " ramLow=" + mi.lowMemory
                    + " psiSomeAvg10=" + fmt(psi.someAvg10)
                    + " psiFullAvg10=" + fmt(psi.fullAvg10)
                    + " cpuMinKHz=" + cpu.minKhz
                    + " cpuAvgKHz=" + cpu.avgKhz
                    + " cpuMaxKHz=" + cpu.maxKhz
                    + " cpuDropPct=" + fmt(dropPct)
                    + " cpuReadableCores=" + cpu.readableCores
                    + " net=" + safe(network.transport)
                    + " netValidated=" + network.validated
                    + " netMetered=" + network.metered
                    + " batteryPct=" + battery.capacity
                    + " batteryTempC=" + battery.tempC
                    + " batteryCurrentUA=" + battery.currentUa
                    + " gboxBatteryExempt=" + gboxBatteryExempt
                    + " rootStatus=" + safe(RootSessionController.lastStatus(context))
                    + " likelyCause=" + safe(cause)
                    + "\n";

            append(context, line);
        } catch (Throwable t) {
            append(context, "ts=" + now() + " event=sample_error type=" + t.getClass().getSimpleName() + "\n");
        }
    }

    static synchronized void recordEvent(Context context, String event, String detail) {
        append(context, "ts=" + now() + " event=" + safe(event) + " detail=" + safe(detail) + "\n");
    }

    static synchronized String report(Context context, int maxLines) {
        StringBuilder out = new StringBuilder();
        out.append("\n=== DEEP DIAGNOSTIC TIMELINE ===\n");
        out.append("Sampling cadence: mode=deep_test is 1 Hz while the user-started Deep Diagnostic Service is active; GuardService samples are approximately every 5 s while protection is active.\n");
        out.append("User lag markers appear as event=user_lag_marker and should be correlated with samples immediately before and after the marker.\n");
        out.append("Interpretation rule: likelyCause is correlation-based, not proof of root cause.\n");
        out.append("Metric paths: CPU=/sys/devices/system/cpu/cpu*/cpufreq/scaling_cur_freq (fallback cpuinfo_cur_freq); memory PSI=/proc/pressure/memory; RAM=ActivityManager.MemoryInfo; thermal=PowerManager; battery=BatteryManager; network=ConnectivityManager.\n");
        File f = new File(context.getFilesDir(), FILE_NAME);
        if (!f.canRead()) {
            out.append("No timeline samples recorded yet. Start the deep diagnostic session, reproduce the issue, press the lag marker when it occurs, then generate the report.\n");
            return out.toString();
        }

        List<String> tail = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                tail.add(line);
                if (tail.size() > maxLines) tail.remove(0);
            }
        } catch (Throwable t) {
            out.append("timeline_read_error=").append(t.getClass().getSimpleName()).append('\n');
            return out.toString();
        }
        for (String line : tail) out.append(line).append('\n');
        return out.toString();
    }

    static synchronized void clear(Context context) {
        File f = new File(context.getFilesDir(), FILE_NAME);
        if (f.exists()) f.delete();
        previousCpuAvgKhz = -1L;
    }

    private static void append(Context context, String line) {
        try {
            File f = new File(context.getFilesDir(), FILE_NAME);
            if (f.length() > MAX_BYTES) {
                try (FileOutputStream reset = new FileOutputStream(f, false)) {
                    reset.write(("ts=" + now() + " event=timeline_rotated\n").getBytes(StandardCharsets.UTF_8));
                }
            }
            try (FileOutputStream out = new FileOutputStream(f, true)) {
                out.write(line.getBytes(StandardCharsets.UTF_8));
            }
        } catch (Throwable ignored) {
        }
    }

    private static CpuFreq readCpuFrequencies() {
        long min = Long.MAX_VALUE;
        long max = 0L;
        long sum = 0L;
        int count = 0;
        File cpuRoot = new File("/sys/devices/system/cpu");
        File[] cpus = cpuRoot.listFiles((dir, name) -> name.matches("cpu[0-9]+"));
        if (cpus != null) {
            for (File cpu : cpus) {
                long value = readLong(new File(cpu, "cpufreq/scaling_cur_freq"));
                if (value <= 0L) value = readLong(new File(cpu, "cpufreq/cpuinfo_cur_freq"));
                if (value > 0L) {
                    min = Math.min(min, value);
                    max = Math.max(max, value);
                    sum += value;
                    count++;
                }
            }
        }
        return new CpuFreq(count == 0 ? 0L : min, count == 0 ? 0L : sum / count, max, count);
    }

    private static long readLong(File file) {
        if (!file.canRead()) return -1L;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String s = r.readLine();
            return s == null ? -1L : Long.parseLong(s.trim());
        } catch (Throwable ignored) {
            return -1L;
        }
    }

    private static Psi readMemoryPsi() {
        File f = new File("/proc/pressure/memory");
        if (!f.canRead()) return new Psi(0.0, 0.0);
        double some = 0.0;
        double full = 0.0;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("some ")) some = parseAvg10(line);
                if (line.startsWith("full ")) full = parseAvg10(line);
            }
        } catch (Throwable ignored) {
        }
        return new Psi(some, full);
    }

    private static double parseAvg10(String line) {
        String[] parts = line.split("\\s+");
        for (String p : parts) {
            if (p.startsWith("avg10=")) {
                try { return Double.parseDouble(p.substring(6)); } catch (Throwable ignored) { return 0.0; }
            }
        }
        return 0.0;
    }

    private static NetworkState networkState(Context context) {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            Network n = cm.getActiveNetwork();
            NetworkCapabilities nc = n == null ? null : cm.getNetworkCapabilities(n);
            if (nc == null) return new NetworkState("NONE", false, cm.isActiveNetworkMetered());
            String transport = nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ? "WIFI"
                    : nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ? "CELLULAR"
                    : nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ? "ETHERNET"
                    : nc.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ? "VPN" : "OTHER";
            return new NetworkState(
                    transport,
                    nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
                    cm.isActiveNetworkMetered()
            );
        } catch (Throwable t) {
            return new NetworkState("ERROR", false, false);
        }
    }

    private static BatteryState batteryState(Context context) {
        BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        int capacity = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        int current = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
        String temp = "unknown";
        Intent battery = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (battery != null) {
            int raw = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
            if (raw != Integer.MIN_VALUE) temp = String.format(Locale.US, "%.1f", raw / 10.0);
        }
        return new BatteryState(capacity, current, temp);
    }

    private static String safe(String value) {
        if (value == null) return "null";
        return value.replace('\n', ' ').replace('\r', ' ').replace('=', ':').trim();
    }

    private static String fmt(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private static String now() {
        return new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss.SSSZ", Locale.US).format(new Date());
    }

    private static final class CpuFreq {
        final long minKhz, avgKhz, maxKhz;
        final int readableCores;
        CpuFreq(long minKhz, long avgKhz, long maxKhz, int readableCores) {
            this.minKhz = minKhz;
            this.avgKhz = avgKhz;
            this.maxKhz = maxKhz;
            this.readableCores = readableCores;
        }
    }

    private static final class Psi {
        final double someAvg10, fullAvg10;
        Psi(double someAvg10, double fullAvg10) {
            this.someAvg10 = someAvg10;
            this.fullAvg10 = fullAvg10;
        }
    }

    private static final class NetworkState {
        final String transport;
        final boolean validated, metered;
        NetworkState(String transport, boolean validated, boolean metered) {
            this.transport = transport;
            this.validated = validated;
            this.metered = metered;
        }
    }

    private static final class BatteryState {
        final int capacity, currentUa;
        final String tempC;
        BatteryState(int capacity, int currentUa, String tempC) {
            this.capacity = capacity;
            this.currentUa = currentUa;
            this.tempC = tempC;
        }
    }
}
