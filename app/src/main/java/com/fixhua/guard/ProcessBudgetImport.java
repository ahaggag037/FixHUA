package com.fixhua.guard;

import com.fixhua.core.ActivityManagerSettingsParser;

/** Pure validation model for process-budget facts imported from an authorized shell probe. */
final class ProcessBudgetImport {
    static final int UNKNOWN = -1;
    static final int MAX_REASONABLE_PROCESS_LIMIT = 1024;

    record Payload(
            int overrideMaxCachedProcesses,
            int customizedMaxCachedProcesses,
            int platformMaxCachedProcesses,
            int currentMaxCachedProcesses) {

        boolean isValid() {
            return validRequired(currentMaxCachedProcesses)
                    && validOptional(overrideMaxCachedProcesses)
                    && validOptional(customizedMaxCachedProcesses)
                    && validOptional(platformMaxCachedProcesses);
        }

        int referenceMaxCachedProcesses() {
            if (customizedMaxCachedProcesses > 0) return customizedMaxCachedProcesses;
            if (platformMaxCachedProcesses > 0) return platformMaxCachedProcesses;
            return UNKNOWN;
        }

        ActivityManagerSettingsParser.ProcessBudget toProcessBudget() {
            return new ActivityManagerSettingsParser.ProcessBudget(
                    overrideMaxCachedProcesses,
                    customizedMaxCachedProcesses,
                    platformMaxCachedProcesses,
                    currentMaxCachedProcesses);
        }
    }

    private static boolean validRequired(int value) {
        return value >= 1 && value <= MAX_REASONABLE_PROCESS_LIMIT;
    }

    private static boolean validOptional(int value) {
        return value == UNKNOWN || validRequired(value);
    }

    private ProcessBudgetImport() {}
}
