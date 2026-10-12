package com.fixhua.guard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ProcessBudgetImportTest {
    @Test
    public void huaweiFourVsThirtyTwoIsValidAndKeepsReference() {
        ProcessBudgetImport.Payload payload = new ProcessBudgetImport.Payload(-1, 32, 32, 4);
        assertTrue(payload.isValid());
        assertEquals(32, payload.referenceMaxCachedProcesses());
        assertEquals(4, payload.toProcessBudget().currentMaxCachedProcesses());
        assertTrue(payload.toProcessBudget().hasNonStandardOverride());
    }

    @Test
    public void currentOnlyIsValidWithoutInventingReference() {
        ProcessBudgetImport.Payload payload = new ProcessBudgetImport.Payload(-1, -1, -1, 32);
        assertTrue(payload.isValid());
        assertEquals(-1, payload.referenceMaxCachedProcesses());
        assertFalse(payload.toProcessBudget().hasNonStandardOverride());
    }

    @Test
    public void rejectsMissingZeroNegativeAndUnboundedCurrent() {
        assertFalse(new ProcessBudgetImport.Payload(-1, 32, 32, -1).isValid());
        assertFalse(new ProcessBudgetImport.Payload(-1, 32, 32, 0).isValid());
        assertFalse(new ProcessBudgetImport.Payload(-1, 32, 32, 1025).isValid());
    }

    @Test
    public void rejectsInvalidOptionalValues() {
        assertFalse(new ProcessBudgetImport.Payload(0, 32, 32, 4).isValid());
        assertFalse(new ProcessBudgetImport.Payload(-1, 0, 32, 4).isValid());
        assertFalse(new ProcessBudgetImport.Payload(-1, 32, 2048, 4).isValid());
    }
}
