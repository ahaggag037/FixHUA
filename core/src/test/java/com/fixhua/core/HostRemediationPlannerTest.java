package com.fixhua.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public final class HostRemediationPlannerTest {
    private final HostRemediationPlanner planner = new HostRemediationPlanner();

    @Test
    public void jlnLikeProcessPressure_prioritizesRestoringStandardProcessLimit() {
        List<HostDoctor.Finding> findings = List.of(
                new HostDoctor.Finding(
                        "FORCE_4X_MSAA",
                        HostDoctor.Severity.WARNING,
                        "MSAA enabled",
                        "Disable it"),
                new HostDoctor.Finding(
                        "RUNTIME_PROCESS_BUDGET_CONFLICT",
                        HostDoctor.Severity.HIGH,
                        "9 processes vs 4",
                        "Use standard limit"),
                new HostDoctor.Finding(
                        "DEV_PROCESS_LIMIT_OVERRIDE",
                        HostDoctor.Severity.HIGH,
                        "4 vs OEM 32",
                        "Restore Standard limit"));

        HostRemediationPlanner.Plan plan = planner.plan(findings);
        HostRemediationPlanner.Step first = plan.firstActionable();

        assertNotNull(first);
        assertEquals("DEV_PROCESS_LIMIT_OVERRIDE", first.code());
        assertEquals(100, first.priority());
        assertEquals(HostRemediationPlanner.Action.OPEN_DEVELOPER_OPTIONS, first.action());
        assertEquals("RUNTIME_PROCESS_BUDGET_CONFLICT", plan.steps().get(1).code());
    }

    @Test
    public void webViewRepair_routesToWebViewSettings() {
        HostRemediationPlanner.Plan plan = planner.plan(List.of(
                new HostDoctor.Finding(
                        "WEBVIEW_DIRTY",
                        HostDoctor.Severity.WARNING,
                        "dirty provider",
                        "repair provider")));

        assertEquals(
                HostRemediationPlanner.Action.OPEN_WEBVIEW_SETTINGS,
                plan.firstActionable().action());
    }

    @Test
    public void unknownFinding_isPreservedButNeverPretendsToHaveAHostFix() {
        HostRemediationPlanner.Plan plan = planner.plan(List.of(
                new HostDoctor.Finding(
                        "UNKNOWN_RUNTIME_SIGNAL",
                        HostDoctor.Severity.HIGH,
                        "runtime signal",
                        "collect more evidence")));

        assertFalse(plan.hasActionableStep());
        assertNull(plan.firstActionable());
        assertEquals(1, plan.steps().size());
        assertEquals(HostRemediationPlanner.Action.OBSERVE_ONLY, plan.steps().get(0).action());
        assertTrue(plan.steps().get(0).priority() > 0);
    }
}
