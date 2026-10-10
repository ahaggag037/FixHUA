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
import android.os.StatFs;
import android.os.SystemClock;

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

/** Lightweight read-only device-wide timeline recorder. */
final class DiagnosticTimeline {
    private static final String FILE_NAME = "diagnostic_timeline.log";
    private static final long MAX_BYTES = 2_500_000L;
    private static volatile long previousCpuAvgKhz = -1L;
    private static volatile long previousCpuTotal = -1L;
    private static volatile long previousCpuIdle = -1L;
    private static volatile long previousSampleElapsed = -1L;

    private DiagnosticTimeline() {}

    static final class SampleSnapshot {
        final long elapsedMs;
        final long sampleGapMs;
        final long samplerLateMs;
        final double ramAvailableRatio;
        final boolean ramLow;
        final boolean memoryPsiAvailable;
        final double memoryPsiSomeAvg10;
        final double memoryPsiFullAvg10;
        final boolean ioPsiAvailable;
        final double ioPsiSomeAvg10;
        final double cpuLoad;
        final double cpuDropPct;
        final int thermalStatus;

        SampleSnapshot(long elapsedMs, long sampleGapMs, long samplerLateMs,
                       double ramAvailableRatio, boolean ramLow,
                       boolean memoryPsiAvailable, double memoryPsiSomeAvg10,
                       double memoryPsiFullAvg10, boolean ioPsiAvailable,
                       double ioPsiSomeAvg10, double cpuLoad, double cpuDropPct,
                       int thermalStatus) {
            this.elapsedMs = elapsedMs;
            this.sampleGapMs = sampleGapMs;
            this.samplerLateMs = samplerLateMs;
            this.ramAvailableRatio = ramAvailableRatio;
            this.ramLow = ramLow;
            this.memoryPsiAvailable = memoryPsiAvailable;
            this.memoryPsiSomeAvg10 = memoryPsiSomeAvg10;
            this.memoryPsiFullAvg10 = memoryPsiFullAvg10;
            this.ioPsiAvailable = ioPsiAvailable;
            this.ioPsiSomeAvg10 = ioPsiSomeAvg10;
            this.cpuLoad = cpuLoad;
            this.cpuDropPct = cpuDropPct;
            this.thermalStatus = thermalStatus;
        }
    }

    static synchronized SampleSnapshot recordSample(
            Context context,
            String mode,
            boolean gboxForeground,
            boolean recentGbox,
            boolean cpuLock,
            String wifiMode,
            int thermalStatus,
            boolean powerSave
    ) {
        return recordSample(context, mode, gboxForeground, recentGbox, cpuLock, wifiMode,
                thermalStatus, powerSave, 0L, "none", 0L, 0, "none");
    }

    static synchronized SampleSnapshot recordSample(
            Context context,
            String mode,
            boolean gboxForeground,
            boolean recentGbox,
            boolean cpuLock,
            String wifiMode,
            int thermalStatus,
            boolean powerSave,
            long samplerLateMs,
            String incidentState,
            long incidentId,
            int incidentScore,
            String incidentReasons
    ) {
        long elapsed = SystemClock.elapsedRealtime();
        long gapMs = previousSampleElapsed <= 0L ? 0L : Math.max(0L, elapsed - previousSampleElapsed);
        previousSampleElapsed = elapsed;

        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            long totalRamMb = mi.totalMem / 1024L / 1024L;
            long availRamMb = mi.availMem / 1024L / 1024L;
            double ramRatio = mi.totalMem > 0L ? (double) mi.availMem / (double) mi.totalMem : -1.0;

            Pressure memPsi = readPressure(new File("/proc/pressure/memory"));
            Pressure ioPsi = readPressure(new File("/proc/pressure/io"));
            CpuFreq cpu = readCpuFrequencies();
            double cpuLoad = readCpuLoad();
            double dropPct = 0.0;
            if (previousCpuAvgKhz > 0L && cpu.avgKhz > 0L && cpu.avgKhz < previousCpuAvgKhz) {
                dropPct = (previousCpuAvgKhz - cpu.avgKhz) * 100.0 / previousCpuAvgKhz;
            }
            if (cpu.avgKhz > 0L) previousCpuAvgKhz = cpu.avgKhz;

            NetworkState network = networkState(context);
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            boolean gboxBatteryExempt = false;
            boolean interactive = true;
            try {
                gboxBatteryExempt = pm.isIgnoringBatteryOptimizations("com.gbox.android");
                interactive = pm.isInteractive();
            } catch (Throwable ignored) {
            }

            BatteryState battery = batteryState(context);
            boolean usageAccess = UsageAccessProbe.hasAccess(context);
            String foreground = usageAccess ? UsageAccessProbe.currentForegroundPackage(context) : null;
            boolean observedGboxForeground = gboxForeground || "com.gbox.android".equals(foreground);
            int visibleProcesses = visibleProcessCount(am);
            double storageFreePct = storageFreePercent(context);

            String cause = DiagnosticAnalyzer.classify(
                    thermalStatus,
                    availRamMb,
                    totalRamMb,
                    memPsi.available ? memPsi.someAvg10 : 0.0,
                    memPsi.available ? memPsi.fullAvg10 : 0.0,
                    dropPct,
                    network.validated,
                    gboxBatteryExempt
            );

            String line = "ts=" + now()
                    + " monoMs=" + elapsed
                    + " event=sample"
                    + " mode=" + safe(mode)
                    + " incidentState=" + safe(incidentState)
                    + " incidentId=" + incidentId
                    + " incidentScore=" + incidentScore
                    + " incidentConfidence=" + AutomaticIncidentDetector.confidenceForScore(incidentScore)
                    + " incidentReasons=" + safe(incidentReasons)
                    + " samplerLateMs=" + samplerLateMs
                    + " sampleGapMs=" + gapMs
                    + " usageAccess=" + usageAccess
                    + " fgPkg=" + safe(foreground == null ? "UNAVAILABLE" : foreground)
                    + " visibleProcCount=" + visibleProcesses
                    + " gboxFg=" + observedGboxForeground
                    + " gboxRecent=" + recentGbox
                    + " cpuLock=" + cpuLock
                    + " wifiMode=" + safe(wifiMode)
                    + " interactive=" + interactive
                    + " thermal=" + thermalStatus
                    + " powerSave=" + powerSave
                    + " ramAvailMB=" + availRamMb
                    + " ramTotalMB=" + totalRamMb
                    + " ramAvailRatio=" + fmt(ramRatio)
                    + " ramLow=" + mi.lowMemory
                    + " memoryPsiAvailable=" + memPsi.available
                    + " psiSomeAvg10=" + fmt(memPsi.someAvg10)
                    + " psiFullAvg10=" + fmt(memPsi.fullAvg10)
                    + " ioPsiAvailable=" + ioPsi.available
                    + " ioPsiSomeAvg10=" + fmt(ioPsi.someAvg10)
                    + " ioPsiFullAvg10=" + fmt(ioPsi.fullAvg10)
                    + " cpuLoad=" + fmt(cpuLoad)
                    + " cpuMinKHz=" + cpu.minKhz
                    + " cpuAvgKHz=" + cpu.avgKhz
                    + " cpuMaxKHz=" + cpu.maxKhz
                    + " cpuDropPct=" + fmt(dropPct)
                    + " cpuReadableCores=" + cpu.readableCores
                    + " storageFreePct=" + fmt(storageFreePct)
                    + " net=" + safe(network.transport)
                    + " netValidated=" + network.validated
                    + " netMetered=" + network.metered
                    + " batteryPct=" + battery.capacity
                    + " batteryTempC=" + battery.tempC
                    + " batteryCurrentUA=" + battery.currentUa
                    + " gboxBatteryExempt=" + gboxBatteryExempt
                    + " rootStatus=" + safe(RootSessionController.lastStatus(context))
                    + " correlationHint=" + safe(cause)
                    + "\n";

            append(context, line);
            return new SampleSnapshot(elapsed, gapMs, samplerLateMs, ramRatio, mi.lowMemory,
                    memPsi.available, memPsi.someAvg10, memPsi.fullAvg10,
                    ioPsi.available, ioPsi.someAvg10, cpuLoad, dropPct, thermalStatus);
        } catch (Throwable t) {
            append(context, "ts=" + now() + " monoMs=" + elapsed
                    + " event=sample_error type=" + t.getClass().getSimpleName() + "\n");
            return new SampleSnapshot(elapsed, gapMs, samplerLateMs, -1.0, false,
                    false, Double.NaN, Double.NaN, false, Double.NaN,
                    -1.0, 0.0, thermalStatus);
        }
    }

    static synchronized void recordEvent(Context context, String event, String detail) {
        append(context, "ts=" + now() + " monoMs=" + SystemClock.elapsedRealtime()
                + " event=" + safe(event) + " detail=" + safe(detail) + "\n");
    }

    static synchronized String report(Context context, int maxLines) {
        StringBuilder out = new StringBuilder();
        out.append("\n=== DEVICE DIAGNOSTIC TIMELINE ===\n");
        out.append("Sampling: 1 Hz normally; temporarily 2 Hz while an automatic incident is suspected/active/recovering.\n");
        out.append("Automatic incident states: NORMAL -> SUSPECTED -> INCIDENT -> RECOVERY -> NORMAL.\n");
        out.append("samplerLateMs and sampleGapMs expose service stalls instead of silently hiding missing data.\n");
        out.append("PSI unavailable is written as availability=false and NA, never as a synthetic zero.\n");
        out.append("Package names may be recorded only when Usage Access is available; app content is never collected.\n");
        out.append("Interpretation rule: correlationHint/incidentScore are evidence-ranking signals, not proof of root cause.\n");
        File f = new File(context.getFilesDir(), FILE_NAME);
        if (!f.canRead()) {
            out.append("No timeline samples recorded yet. Start a diagnostic session and use the phone normally.\n");
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
        previousCpuTotal = -1L;
        previousCpuIdle = -1L;
        previousSampleElapsed = -1L;
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

    private static double readCpuLoad() {
        File stat = new File("/proc/stat");
        if (!stat.canRead()) return -1.0;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(stat), StandardCharsets.UTF_8))) {
            String line = r.readLine();
            if (line == null || !line.startsWith("cpu ")) return -1.0;
            String[] p = line.trim().split("\\s+");
            if (p.length < 8) return -1.0;
            long user = parseLong(p, 1), nice = parseLong(p, 2), system = parseLong(p, 3);
            long idle = parseLong(p, 4), iowait = parseLong(p, 5), irq = parseLong(p, 6), softirq = parseLong(p, 7);
            long steal = p.length > 8 ? parseLong(p, 8) : 0L;
            long idleAll = idle + iowait;
            long total = user + nice + system + idleAll + irq + softirq + steal;
            double load = -1.0;
            if (previousCpuTotal >= 0L && total > previousCpuTotal) {
                long totalDelta = total - previousCpuTotal;
                long idleDelta = Math.max(0L, idleAll - previousCpuIdle);
                load = totalDelta <= 0L ? -1.0 : Math.max(0.0, Math.min(1.0,
                        (double) (totalDelta - idleDelta) / (double) totalDelta));
            }
            previousCpuTotal = total;
            previousCpuIdle = idleAll;
            return load;
        } catch (Throwable ignored) {
            return -1.0;
        }
    }

    private static long parseLong(String[] values, int index) {
        try { return Long.parseLong(values[index]); } catch (Throwable ignored) { return 0L; }
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

    private static Pressure readPressure(File f) {
        if (!f.canRead()) return new Pressure(false, Double.NaN, Double.NaN);
        double some = Double.NaN;
        double full = Double.NaN;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("some ")) some = parseAvg10(line);
                if (line.startsWith("full ")) full = parseAvg10(line);
            }
            return new Pressure(true, some, full);
        } catch (Throwable ignored) {
            return new Pressure(false, Double.NaN, Double.NaN);
        }
    }

    private static double parseAvg10(String line) {
        String[] parts = line.split("\\s+");
        for (String p : parts) {
            if (p.startsWith("avg10=")) {
                try { return Double.parseDouble(p.substring(6)); } catch (Throwable ignored) { return Double.NaN; }
            }
        }
        return Double.NaN;
    }

    private static int visibleProcessCount(ActivityManager am) {
        try {
            List<ActivityManager.RunningAppProcessInfo> processes = am.getRunningAppProcesses();
            return processes == null ? 0 : processes.size();
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private static double storageFreePercent(Context context) {
        try {
            StatFs stat = new StatFs(context.getFilesDir().getAbsolutePath());
            long total = stat.getTotalBytes();
            return total <= 0L ? -1.0 : (stat.getAvailableBytes() * 100.0 / total);
        } catch (Throwable ignored) {
            return -1.0;
        }
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
            return new NetworkState(transport,
                    nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
                    cm.isActiveNetworkMetered());
        } catch (Throwable t) {
            return new NetworkState("ERROR", false, false);
        }
    }

    private static BatteryState batteryState(Context context) {
        BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        int capacity = bm == null ? -1 : bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        int current = bm == null ? 0 : bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
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
        if (Double.isNaN(value) || Double.isInfinite(value)) return "NA";
        return String.format(Locale.US, "%.3f", value);
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

    private static final class Pressure {
        final boolean available;
        final double someAvg10, fullAvg10;
        Pressure(boolean available, double someAvg10, double fullAvg10) {
            this.available = available;
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
