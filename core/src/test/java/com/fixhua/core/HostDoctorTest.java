package com.fixhua.core;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public final class HostDoctorTest {
    private final HostDoctor doctor = new HostDoctor();

    @Test
    public void bugreportCase_processLimitFourAgainstNineProcessRuntime_isHighConflict() {
        HostDoctor.HostRuntimeProfile host = new HostDoctor.HostRuntimeProfile(
                4,
                32,
                false,
                true,
                true,
                false,
                true,
                "com.huawei.webview");
        HostDoctor.RuntimeBudget gboxLike = new HostDoctor.RuntimeBudget("gbox-like", 9);

        List<HostDoctor.Finding> findings = doctor.evaluate(host, gboxLike);

        assertTrue(findings.stream().anyMatch(f ->
                f.code().equals("DEV_PROCESS_LIMIT_OVERRIDE")
                        && f.severity() == HostDoctor.Severity.HIGH));
        assertTrue(findings.stream().anyMatch(f ->
                f.code().equals("RUNTIME_PROCESS_BUDGET_CONFLICT")
                        && f.severity() == HostDoctor.Severity.HIGH));
        assertTrue(findings.stream().anyMatch(f -> f.code().equals("FORCE_4X_MSAA")));
        assertTrue(doctor.hasHighSeverityConflict(findings));
    }

    @Test
    public void syntheticSlice_oneProcessWithStandardLimit_hasNoProcessConflict() {
        HostDoctor.HostRuntimeProfile host = new HostDoctor.HostRuntimeProfile(
                32,
                32,
                false,
                false,
                true,
                false,
                true,
                "com.huawei.webview");
        HostDoctor.RuntimeBudget slice = new HostDoctor.RuntimeBudget("tsr-v1", 1);

        List<HostDoctor.Finding> findings = doctor.evaluate(host, slice);

        assertFalse(findings.stream().anyMatch(f -> f.code().equals("DEV_PROCESS_LIMIT_OVERRIDE")));
        assertFalse(findings.stream().anyMatch(f -> f.code().equals("RUNTIME_PROCESS_BUDGET_CONFLICT")));
        assertFalse(doctor.hasHighSeverityConflict(findings));
    }

    @Test
    public void dontKeepActivities_isAlwaysHighRiskForLifecycleRuntime() {
        HostDoctor.HostRuntimeProfile host = new HostDoctor.HostRuntimeProfile(
                HostDoctor.UNKNOWN_LIMIT,
                HostDoctor.UNKNOWN_LIMIT,
                true,
                false,
                true,
                false,
                true,
                "com.huawei.webview");

        List<HostDoctor.Finding> findings = doctor.evaluate(
                host,
                new HostDoctor.RuntimeBudget("tsr-v1", 1));

        assertTrue(findings.stream().anyMatch(f ->
                f.code().equals("ALWAYS_FINISH_ACTIVITIES")
                        && f.severity() == HostDoctor.Severity.HIGH));
    }

    @Test
    public void missingWebView_isHighRiskButSingleProcessWebViewIsInformational() {
        HostDoctor.HostRuntimeProfile missing = new HostDoctor.HostRuntimeProfile(
                HostDoctor.UNKNOWN_LIMIT,
                HostDoctor.UNKNOWN_LIMIT,
                false,
                false,
                false,
                false,
                false,
                "");
        List<HostDoctor.Finding> missingFindings = doctor.evaluate(
                missing,
                new HostDoctor.RuntimeBudget("tsr-v1", 1));
        assertTrue(missingFindings.stream().anyMatch(f ->
                f.code().equals("WEBVIEW_MISSING")
                        && f.severity() == HostDoctor.Severity.HIGH));

        HostDoctor.HostRuntimeProfile singleProcess = new HostDoctor.HostRuntimeProfile(
                HostDoctor.UNKNOWN_LIMIT,
                HostDoctor.UNKNOWN_LIMIT,
                false,
                false,
                true,
                false,
                false,
                "com.huawei.webview");
        List<HostDoctor.Finding> singleFindings = doctor.evaluate(
                singleProcess,
                new HostDoctor.RuntimeBudget("tsr-v1", 1));
        assertTrue(singleFindings.stream().anyMatch(f ->
                f.code().equals("WEBVIEW_SINGLE_PROCESS")
                        && f.severity() == HostDoctor.Severity.INFO));
    }
}
