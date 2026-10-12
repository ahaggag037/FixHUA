package com.fixhua.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Converts HostDoctor findings into a deterministic, user-actionable remediation order.
 *
 * <p>The planner never claims to change Android global/secure settings itself. It only selects
 * the narrowest settings surface that a normal, unprivileged FixHUA build can legitimately open.
 * Findings with no safe host-side action remain visible as observe-only evidence.</p>
 */
public final class HostRemediationPlanner {
    public enum Action {
        OPEN_DEVELOPER_OPTIONS,
        OPEN_WEBVIEW_SETTINGS,
        OBSERVE_ONLY
    }

    public record Step(
            String code,
            HostDoctor.Severity severity,
            int priority,
            Action action,
            String summary,
            String recommendedAction) {
        public Step {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(severity, "severity");
            Objects.requireNonNull(action, "action");
            Objects.requireNonNull(summary, "summary");
            Objects.requireNonNull(recommendedAction, "recommendedAction");
            if (code.isBlank()) throw new IllegalArgumentException("code");
            if (priority < 0 || priority > 100) throw new IllegalArgumentException("priority");
        }

        public boolean actionable() {
            return action != Action.OBSERVE_ONLY;
        }
    }

    public record Plan(List<Step> steps) {
        public Plan {
            steps = Collections.unmodifiableList(new ArrayList<>(steps));
        }

        public Step firstActionable() {
            for (Step step : steps) {
                if (step.actionable()) return step;
            }
            return null;
        }

        public boolean hasActionableStep() {
            return firstActionable() != null;
        }
    }

    public Plan plan(List<HostDoctor.Finding> findings) {
        Objects.requireNonNull(findings, "findings");
        List<Step> steps = new ArrayList<>();
        for (HostDoctor.Finding finding : findings) {
            steps.add(toStep(finding));
        }
        steps.sort(Comparator.comparingInt(Step::priority).reversed());
        return new Plan(steps);
    }

    private static Step toStep(HostDoctor.Finding finding) {
        Objects.requireNonNull(finding, "finding");
        switch (finding.code()) {
            case "DEV_PROCESS_LIMIT_OVERRIDE":
                return step(finding, 100, Action.OPEN_DEVELOPER_OPTIONS);
            case "RUNTIME_PROCESS_BUDGET_CONFLICT":
                return step(finding, 95, Action.OPEN_DEVELOPER_OPTIONS);
            case "ALWAYS_FINISH_ACTIVITIES":
                return step(finding, 90, Action.OPEN_DEVELOPER_OPTIONS);
            case "WEBVIEW_MISSING":
                return step(finding, 85, Action.OPEN_WEBVIEW_SETTINGS);
            case "WEBVIEW_DIRTY":
                return step(finding, 75, Action.OPEN_WEBVIEW_SETTINGS);
            case "FORCE_4X_MSAA":
                return step(finding, 60, Action.OPEN_DEVELOPER_OPTIONS);
            default:
                return step(finding, severityPriority(finding.severity()), Action.OBSERVE_ONLY);
        }
    }

    private static Step step(HostDoctor.Finding finding, int priority, Action action) {
        return new Step(
                finding.code(),
                finding.severity(),
                priority,
                action,
                finding.summary(),
                finding.recommendedAction());
    }

    private static int severityPriority(HostDoctor.Severity severity) {
        return switch (severity) {
            case HIGH -> 80;
            case WARNING -> 50;
            case INFO -> 10;
        };
    }
}
