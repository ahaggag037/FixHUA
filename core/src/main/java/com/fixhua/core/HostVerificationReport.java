package com.fixhua.core;

import java.util.List;
import java.util.Objects;

/**
 * Human-readable, privacy-bounded host verification report.
 *
 * <p>This report deliberately contains only the sanitized snapshot schema. It never includes raw
 * bugreport lines, device identifiers, package inventories, SSIDs, accounts, serials, crash traces,
 * or historical crash/ANR counters.</p>
 */
public final class HostVerificationReport {
    private HostVerificationReport() {}

    public static String render(
            HostVerificationSnapshot current,
            HostFixVerification.Result comparison) {
        Objects.requireNonNull(current, "current");
        StringBuilder out = new StringBuilder(1024);
        out.append("FixHUA Sanitized Host Verification v1\n");
        out.append("privacy=raw_bugreport_not_retained\n");
        out.append("scope=host_compatibility_facts_only\n\n");

        out.append("[CURRENT]\n");
        out.append("effective_cached_process_limit=")
                .append(current.currentMaxCachedProcesses()).append('\n');
        out.append("oem_cached_process_limit=")
                .append(current.customizedMaxCachedProcesses()).append('\n');
        out.append("observed_gbox_processes=")
                .append(current.observedGboxProcesses()).append('\n');
        out.append("force_4x_msaa=").append(current.force4xMsaa()).append('\n');
        out.append("always_finish_activities=")
                .append(current.alwaysFinishActivities()).append('\n');
        out.append("webview_installed=").append(current.webViewInstalled()).append('\n');
        out.append("webview_dirty=").append(current.webViewDirty()).append('\n');
        out.append("webview_multiprocess=").append(current.webViewMultiprocess()).append('\n');
        out.append("active_findings=").append(join(current.activeFindingCodes())).append("\n\n");

        out.append("[COMPARISON]\n");
        if (comparison == null) {
            out.append("baseline=none\n");
            out.append("verified_improvement=false\n");
            out.append("new_problem=false\n");
        } else {
            out.append("baseline=previous_imported_sanitized_snapshot\n");
            out.append("previous_effective_cached_process_limit=")
                    .append(comparison.before().currentMaxCachedProcesses()).append('\n');
            out.append("process_limit_changed=").append(comparison.processLimitChanged()).append('\n');
            out.append("process_limit_restored_to_oem=")
                    .append(comparison.processLimitRestoredToOem()).append('\n');
            out.append("force_4x_msaa_disabled=")
                    .append(comparison.force4xMsaaDisabled()).append('\n');
            out.append("always_finish_activities_disabled=")
                    .append(comparison.alwaysFinishActivitiesDisabled()).append('\n');
            out.append("resolved_findings=").append(join(comparison.resolvedFindingCodes())).append('\n');
            out.append("remaining_findings=").append(join(comparison.remainingFindingCodes())).append('\n');
            out.append("introduced_findings=").append(join(comparison.introducedFindingCodes())).append('\n');
            out.append("verified_improvement=").append(comparison.hasVerifiedImprovement()).append('\n');
            out.append("new_problem=").append(comparison.hasNewProblem()).append('\n');
        }

        out.append("\ncausality_note=configuration differences only; historical crash/ANR counters are not used as proof of improvement\n");
        return out.toString();
    }

    private static String join(List<String> values) {
        if (values == null || values.isEmpty()) return "none";
        return String.join(",", values);
    }
}
