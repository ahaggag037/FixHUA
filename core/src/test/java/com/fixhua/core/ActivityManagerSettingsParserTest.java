package com.fixhua.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.StringReader;
import org.junit.Test;

public final class ActivityManagerSettingsParserTest {
    private final ActivityManagerSettingsParser parser = new ActivityManagerSettingsParser();

    @Test
    public void huaweiDump_prefersCustomizedOemReference() throws Exception {
        String text = """
                ACTIVITY MANAGER SETTINGS (dumpsys activity settings):
                 max_cached_processes=32
                 mOverrideMaxCachedProcesses=4
                 mCustomizedMaxCachedProcesses=32
                 CUR_MAX_CACHED_PROCESSES=4
                 CUR_MAX_EMPTY_PROCESSES=2
                """;

        ActivityManagerSettingsParser.ProcessBudget budget = parser.parse(new StringReader(text));

        assertEquals(4, budget.overrideMaxCachedProcesses());
        assertEquals(32, budget.customizedMaxCachedProcesses());
        assertEquals(32, budget.platformMaxCachedProcesses());
        assertEquals(4, budget.currentMaxCachedProcesses());
        assertEquals(32, budget.referenceMaxCachedProcesses());
        assertTrue(budget.hasNonStandardOverride());
    }

    @Test
    public void aospStandardLimit_usesPlatformReference() throws Exception {
        String text = """
                ACTIVITY MANAGER SETTINGS (dumpsys activity settings):
                 max_cached_processes=32
                 CUR_MAX_CACHED_PROCESSES=32
                 CUR_MAX_EMPTY_PROCESSES=16
                """;

        ActivityManagerSettingsParser.ProcessBudget budget = parser.parse(new StringReader(text));

        assertEquals(-1, budget.overrideMaxCachedProcesses());
        assertEquals(-1, budget.customizedMaxCachedProcesses());
        assertEquals(32, budget.referenceMaxCachedProcesses());
        assertFalse(budget.hasNonStandardOverride());
        assertTrue(budget.hasUsableCurrentLimit());
    }

    @Test
    public void unknownReference_neverInventsConflict() throws Exception {
        ActivityManagerSettingsParser.ProcessBudget budget = parser.parse(
                new StringReader("CUR_MAX_CACHED_PROCESSES=4\n"));

        assertEquals(-1, budget.referenceMaxCachedProcesses());
        assertFalse(budget.hasNonStandardOverride());
    }
}
