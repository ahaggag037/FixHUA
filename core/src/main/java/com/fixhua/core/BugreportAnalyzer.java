package com.fixhua.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Privacy-bounded parser for a user-selected Android bugreport ZIP.
 *
 * <p>The analyzer never extracts files and never retains raw lines. It streams the main bugreport
 * text and returns only compatibility facts used by FixHUA. This is intentionally not a general
 * log viewer.</p>
 */
public final class BugreportAnalyzer {
    private static final long MAX_MAIN_TEXT_BYTES = 64L * 1024L * 1024L;
    private static final int MAX_ENTRIES = 20_000;
    private static final int MAX_GBOX_PROCESS_NAMES = 64;

    private static final Pattern INT_VALUE = Pattern.compile("=(-?\\d+)\\s*$");
    private static final Pattern PROCESS_RECORD = Pattern.compile(
            "ProcessRecord\\{[^}]*?\\s\\d+:([^/\\s}]+)/[^}]*}");
    private static final Pattern WEBVIEW_VERSION = Pattern.compile(
            "(?:Current|Preferred) WebView package.*?(com\\.[A-Za-z0-9_.]+).*?([0-9]+(?:\\.[0-9]+){1,5})");

    public record Result(
            int overrideMaxCachedProcesses,
            int customizedMaxCachedProcesses,
            int currentMaxCachedProcesses,
            Set<String> gboxProcessNames,
            int gboxAppProcessKillerMarkers,
            int nullApplicationResourcesMarkers,
            int inputDispatchTimeoutMarkers,
            boolean force4xMsaa,
            boolean alwaysFinishActivities,
            boolean webViewMultiprocess,
            boolean webViewDirty,
            String webViewPackage,
            String webViewVersion,
            long scannedBytes,
            String sourceEntry) {
        public Result {
            gboxProcessNames = Collections.unmodifiableSet(new LinkedHashSet<>(gboxProcessNames));
            webViewPackage = webViewPackage == null ? "" : webViewPackage;
            webViewVersion = webViewVersion == null ? "" : webViewVersion;
            sourceEntry = sourceEntry == null ? "" : sourceEntry;
        }

        public boolean hasDeveloperProcessLimitOverride() {
            return overrideMaxCachedProcesses > 0
                    && customizedMaxCachedProcesses > 0
                    && overrideMaxCachedProcesses < customizedMaxCachedProcesses;
        }

        public HostDoctor.HostRuntimeProfile toHostProfile() {
            int effective = currentMaxCachedProcesses > 0
                    ? currentMaxCachedProcesses
                    : overrideMaxCachedProcesses;
            return new HostDoctor.HostRuntimeProfile(
                    effective > 0 ? effective : HostDoctor.UNKNOWN_LIMIT,
                    customizedMaxCachedProcesses > 0
                            ? customizedMaxCachedProcesses
                            : HostDoctor.UNKNOWN_LIMIT,
                    alwaysFinishActivities,
                    force4xMsaa,
                    !webViewPackage.isBlank(),
                    webViewDirty,
                    webViewMultiprocess,
                    webViewPackage);
        }
    }

    public Result analyzeZip(InputStream input) throws IOException {
        Objects.requireNonNull(input, "input");

        try (ZipInputStream zip = new ZipInputStream(input, StandardCharsets.UTF_8)) {
            int entries = 0;
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > MAX_ENTRIES) {
                    throw new IOException("Bugreport contains too many ZIP entries");
                }
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                if (!isMainBugreportText(name)) continue;
                long declared = entry.getSize();
                if (declared > MAX_MAIN_TEXT_BYTES) {
                    throw new IOException("Bugreport main text exceeds safety limit");
                }
                return parseMainText(zip, name);
            }
        }
        throw new IOException("Android main bugreport text was not found in ZIP");
    }

    private static boolean isMainBugreportText(String name) {
        if (name == null || name.contains("/")) return false;
        return name.startsWith("bugreport-") && name.endsWith(".txt");
    }

    private static Result parseMainText(InputStream stream, String sourceEntry) throws IOException {
        int override = HostDoctor.UNKNOWN_LIMIT;
        int customized = HostDoctor.UNKNOWN_LIMIT;
        int current = HostDoctor.UNKNOWN_LIMIT;
        Set<String> gboxProcesses = new HashSet<>();
        int appProcessKiller = 0;
        int nullApplication = 0;
        int inputTimeouts = 0;
        boolean force4xMsaa = false;
        boolean alwaysFinish = false;
        boolean webViewMultiprocess = false;
        boolean webViewDirty = false;
        String webViewPackage = "";
        String webViewVersion = "";
        long scannedBytes = 0;

        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8), 64 * 1024);
        String line;
        while ((line = reader.readLine()) != null) {
            scannedBytes += line.getBytes(StandardCharsets.UTF_8).length + 1L;
            if (scannedBytes > MAX_MAIN_TEXT_BYTES) {
                throw new IOException("Bugreport main text exceeded safety limit while streaming");
            }

            String trimmed = line.trim();
            if (trimmed.contains("mOverrideMaxCachedProcesses=")) {
                override = trailingInt(trimmed, override);
            } else if (trimmed.contains("mCustomizedMaxCachedProcesses=")) {
                customized = trailingInt(trimmed, customized);
            } else if (trimmed.contains("CUR_MAX_CACHED_PROCESSES=")) {
                current = trailingInt(trimmed, current);
            }

            if (trimmed.contains("always_finish_activities=1")) alwaysFinish = true;
            if (trimmed.contains("debug.egl.force_msaa=true")) force4xMsaa = true;
            if (trimmed.contains("Multiprocess enabled: true")) webViewMultiprocess = true;
            if (trimmed.contains("WebView package dirty: true")) webViewDirty = true;

            if (trimmed.contains("WebView package")) {
                Matcher webView = WEBVIEW_VERSION.matcher(trimmed);
                if (webView.find()) {
                    webViewPackage = webView.group(1);
                    webViewVersion = webView.group(2);
                }
            }

            if (trimmed.contains("ProcessRecord{") && trimmed.contains("com.gbox.android")) {
                Matcher process = PROCESS_RECORD.matcher(trimmed);
                if (process.find() && gboxProcesses.size() < MAX_GBOX_PROCESS_NAMES) {
                    String name = process.group(1);
                    if (name.equals("com.gbox.android") || name.startsWith("com.gbox.android:")) {
                        gboxProcesses.add(name);
                    }
                }
            }

            if (trimmed.contains("AppProcessKiller:")) appProcessKiller++;
            if (trimmed.contains("Application.getResources()")
                    && trimmed.contains("null object reference")) nullApplication++;
            if (trimmed.contains("Input dispatching timed out")) inputTimeouts++;
        }

        return new Result(
                override,
                customized,
                current,
                gboxProcesses,
                appProcessKiller,
                nullApplication,
                inputTimeouts,
                force4xMsaa,
                alwaysFinish,
                webViewMultiprocess,
                webViewDirty,
                webViewPackage,
                webViewVersion,
                scannedBytes,
                sourceEntry);
    }

    private static int trailingInt(String line, int fallback) {
        Matcher matcher = INT_VALUE.matcher(line);
        if (!matcher.find()) return fallback;
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
