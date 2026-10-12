package com.fixhua.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Objects;

/** Parses only process-budget facts from {@code dumpsys activity settings}. */
public final class ActivityManagerSettingsParser {
    public static final int UNKNOWN = -1;
    private static final int MAX_LINES = 4096;
    private static final int MAX_LINE_LENGTH = 8192;

    public record ProcessBudget(
            int overrideMaxCachedProcesses,
            int customizedMaxCachedProcesses,
            int platformMaxCachedProcesses,
            int currentMaxCachedProcesses) {

        public int referenceMaxCachedProcesses() {
            if (customizedMaxCachedProcesses > 0) return customizedMaxCachedProcesses;
            if (platformMaxCachedProcesses > 0) return platformMaxCachedProcesses;
            return UNKNOWN;
        }

        public boolean hasUsableCurrentLimit() {
            return currentMaxCachedProcesses > 0;
        }

        public boolean hasNonStandardOverride() {
            int reference = referenceMaxCachedProcesses();
            if (currentMaxCachedProcesses <= 0 || reference <= 0) return false;
            return currentMaxCachedProcesses < reference;
        }
    }

    public ProcessBudget parse(Reader input) throws IOException {
        Objects.requireNonNull(input, "input");
        BufferedReader reader = input instanceof BufferedReader
                ? (BufferedReader) input
                : new BufferedReader(input);

        int override = UNKNOWN;
        int customized = UNKNOWN;
        int platform = UNKNOWN;
        int current = UNKNOWN;
        int lines = 0;
        String line;
        while ((line = reader.readLine()) != null) {
            if (++lines > MAX_LINES) throw new IOException("ActivityManager settings exceeded line limit");
            if (line.length() > MAX_LINE_LENGTH) throw new IOException("ActivityManager settings line too long");
            String trimmed = line.trim();
            if (trimmed.startsWith("mOverrideMaxCachedProcesses=")) {
                override = parseTrailingInt(trimmed, override);
            } else if (trimmed.startsWith("mCustomizedMaxCachedProcesses=")) {
                customized = parseTrailingInt(trimmed, customized);
            } else if (trimmed.startsWith("max_cached_processes=")) {
                platform = parseTrailingInt(trimmed, platform);
            } else if (trimmed.startsWith("CUR_MAX_CACHED_PROCESSES=")) {
                current = parseTrailingInt(trimmed, current);
            }
        }
        return new ProcessBudget(override, customized, platform, current);
    }

    private static int parseTrailingInt(String line, int fallback) {
        int equals = line.lastIndexOf('=');
        if (equals < 0 || equals + 1 >= line.length()) return fallback;
        try {
            return Integer.parseInt(line.substring(equals + 1).trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
