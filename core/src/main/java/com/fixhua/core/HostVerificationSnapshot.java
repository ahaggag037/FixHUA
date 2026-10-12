package com.fixhua.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * Privacy-bounded snapshot used only to verify host-configuration changes between imported
 * bugreports. It intentionally excludes raw log lines, package lists, identifiers and historical
 * crash counters.
 */
public record HostVerificationSnapshot(
        int currentMaxCachedProcesses,
        int customizedMaxCachedProcesses,
        int observedGboxProcesses,
        boolean force4xMsaa,
        boolean alwaysFinishActivities,
        boolean webViewInstalled,
        boolean webViewDirty,
        boolean webViewMultiprocess,
        List<String> activeFindingCodes) {

    private static final String VERSION = "HVS1";

    public HostVerificationSnapshot {
        if (currentMaxCachedProcesses < 0) throw new IllegalArgumentException("currentMaxCachedProcesses");
        if (customizedMaxCachedProcesses < 0) throw new IllegalArgumentException("customizedMaxCachedProcesses");
        if (observedGboxProcesses < 0) throw new IllegalArgumentException("observedGboxProcesses");
        Objects.requireNonNull(activeFindingCodes, "activeFindingCodes");
        List<String> normalized = new ArrayList<>();
        for (String code : activeFindingCodes) {
            if (code == null || code.isBlank()) continue;
            if (!code.matches("[A-Z0-9_]+")) throw new IllegalArgumentException("finding code");
            if (!normalized.contains(code)) normalized.add(code);
        }
        Collections.sort(normalized);
        activeFindingCodes = Collections.unmodifiableList(normalized);
    }

    public static HostVerificationSnapshot from(
            BugreportAnalyzer.Result report,
            List<HostDoctor.Finding> findings) {
        Objects.requireNonNull(report, "report");
        Objects.requireNonNull(findings, "findings");
        List<String> codes = new ArrayList<>();
        for (HostDoctor.Finding finding : findings) {
            codes.add(finding.code());
        }
        return new HostVerificationSnapshot(
                Math.max(0, report.currentMaxCachedProcesses()),
                Math.max(0, report.customizedMaxCachedProcesses()),
                report.gboxProcessNames().size(),
                report.force4xMsaa(),
                report.alwaysFinishActivities(),
                !report.webViewPackage().isBlank(),
                report.webViewDirty(),
                report.webViewMultiprocess(),
                codes);
    }

    /** Deterministic compact form suitable for app-private preferences. */
    public String encode() {
        StringJoiner codes = new StringJoiner(",");
        for (String code : activeFindingCodes) codes.add(code);
        return VERSION
                + ";cur=" + currentMaxCachedProcesses
                + ";oem=" + customizedMaxCachedProcesses
                + ";gbox=" + observedGboxProcesses
                + ";msaa=" + bit(force4xMsaa)
                + ";finish=" + bit(alwaysFinishActivities)
                + ";wv=" + bit(webViewInstalled)
                + ";dirty=" + bit(webViewDirty)
                + ";multi=" + bit(webViewMultiprocess)
                + ";find=" + codes;
    }

    public static HostVerificationSnapshot decode(String encoded) {
        if (encoded == null || encoded.isBlank()) throw new IllegalArgumentException("encoded");
        String[] parts = encoded.split(";", -1);
        if (parts.length != 10 || !VERSION.equals(parts[0])) {
            throw new IllegalArgumentException("snapshot version/shape");
        }
        int cur = parseInt(parts[1], "cur");
        int oem = parseInt(parts[2], "oem");
        int gbox = parseInt(parts[3], "gbox");
        boolean msaa = parseBool(parts[4], "msaa");
        boolean finish = parseBool(parts[5], "finish");
        boolean wv = parseBool(parts[6], "wv");
        boolean dirty = parseBool(parts[7], "dirty");
        boolean multi = parseBool(parts[8], "multi");
        String rawFindings = value(parts[9], "find");
        List<String> findings = new ArrayList<>();
        if (!rawFindings.isBlank()) {
            Collections.addAll(findings, rawFindings.split(","));
        }
        return new HostVerificationSnapshot(cur, oem, gbox, msaa, finish, wv, dirty, multi, findings);
    }

    private static int bit(boolean value) {
        return value ? 1 : 0;
    }

    private static int parseInt(String part, String key) {
        try {
            int value = Integer.parseInt(value(part, key));
            if (value < 0) throw new IllegalArgumentException(key);
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key, e);
        }
    }

    private static boolean parseBool(String part, String key) {
        String value = value(part, key);
        if ("1".equals(value)) return true;
        if ("0".equals(value)) return false;
        throw new IllegalArgumentException(key);
    }

    private static String value(String part, String key) {
        String prefix = key + "=";
        if (!part.startsWith(prefix)) throw new IllegalArgumentException(key);
        return part.substring(prefix.length());
    }
}
