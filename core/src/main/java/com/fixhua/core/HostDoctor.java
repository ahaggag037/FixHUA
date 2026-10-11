package com.fixhua.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic host compatibility rules derived from the frozen FixHUA architecture.
 *
 * <p>This class deliberately accepts observations as data rather than reading Android hidden APIs.
 * Android/OEM adapters can populate {@link HostRuntimeProfile}; tests can exercise the exact same
 * rules without an emulator or privileged binder access.</p>
 */
public final class HostDoctor {
    public static final int UNKNOWN_LIMIT = -1;

    public enum Severity {
        INFO,
        WARNING,
        HIGH
    }

    public record Finding(
            String code,
            Severity severity,
            String summary,
            String recommendedAction) {
        public Finding {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(severity, "severity");
            Objects.requireNonNull(summary, "summary");
            Objects.requireNonNull(recommendedAction, "recommendedAction");
            if (code.isBlank()) throw new IllegalArgumentException("code");
        }
    }

    public record HostRuntimeProfile(
            int effectiveCachedProcessLimit,
            int customizedCachedProcessLimit,
            boolean alwaysFinishActivities,
            boolean force4xMsaa,
            boolean webViewInstalled,
            boolean webViewDirty,
            boolean webViewMultiprocess,
            String webViewPackage) {
        public HostRuntimeProfile {
            if (effectiveCachedProcessLimit == 0 || effectiveCachedProcessLimit < UNKNOWN_LIMIT) {
                throw new IllegalArgumentException("effectiveCachedProcessLimit");
            }
            if (customizedCachedProcessLimit == 0 || customizedCachedProcessLimit < UNKNOWN_LIMIT) {
                throw new IllegalArgumentException("customizedCachedProcessLimit");
            }
            webViewPackage = webViewPackage == null ? "" : webViewPackage;
        }
    }

    public record RuntimeBudget(String runtimeId, int expectedHostProcesses) {
        public RuntimeBudget {
            Objects.requireNonNull(runtimeId, "runtimeId");
            if (runtimeId.isBlank()) throw new IllegalArgumentException("runtimeId");
            if (expectedHostProcesses <= 0) throw new IllegalArgumentException("expectedHostProcesses");
        }
    }

    public List<Finding> evaluate(HostRuntimeProfile host, RuntimeBudget runtime) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(runtime, "runtime");

        List<Finding> findings = new ArrayList<>();

        if (isDeveloperProcessLimitOverride(host)) {
            findings.add(new Finding(
                    "DEV_PROCESS_LIMIT_OVERRIDE",
                    Severity.HIGH,
                    "Android cached-process capacity is overridden below the device/OEM default.",
                    "Restore Developer options > Background process limit to Standard limit unless a specific workload requires otherwise."));
        }

        if (host.effectiveCachedProcessLimit() > 0
                && runtime.expectedHostProcesses() > host.effectiveCachedProcessLimit()) {
            findings.add(new Finding(
                    "RUNTIME_PROCESS_BUDGET_CONFLICT",
                    Severity.HIGH,
                    "Runtime " + runtime.runtimeId() + " expects " + runtime.expectedHostProcesses()
                            + " host processes while the effective cached-process limit is "
                            + host.effectiveCachedProcessLimit() + ".",
                    "Use a lower-process runtime topology or remove the non-standard host process limit."));
        }

        if (host.alwaysFinishActivities()) {
            findings.add(new Finding(
                    "ALWAYS_FINISH_ACTIVITIES",
                    Severity.HIGH,
                    "Developer option 'Don't keep activities' is enabled and can destroy activity state immediately.",
                    "Turn off 'Don't keep activities' for normal compatibility testing and daily use."));
        }

        if (host.force4xMsaa()) {
            findings.add(new Finding(
                    "FORCE_4X_MSAA",
                    Severity.WARNING,
                    "Force 4x MSAA is enabled globally and can add GPU/memory overhead to OpenGL ES 2.0 workloads.",
                    "Turn off Force 4x MSAA unless a specific application has a measured need for it."));
        }

        if (!host.webViewInstalled()) {
            findings.add(new Finding(
                    "WEBVIEW_MISSING",
                    Severity.HIGH,
                    "No valid host WebView provider is installed.",
                    "Install or repair a system-supported WebView provider before diagnosing WebView-dependent apps."));
        } else if (host.webViewDirty()) {
            findings.add(new Finding(
                    "WEBVIEW_DIRTY",
                    Severity.WARNING,
                    "The host WebView update service reports a dirty/incomplete provider state.",
                    "Allow the supported WebView provider to finish updating or restore the OEM provider."));
        }

        if (host.webViewInstalled() && !host.webViewMultiprocess()) {
            findings.add(new Finding(
                    "WEBVIEW_SINGLE_PROCESS",
                    Severity.INFO,
                    "WebView multiprocess mode is disabled.",
                    "Keep this as an observed compatibility fact; change it only for a recipe that explicitly requires a different mode."));
        }

        return Collections.unmodifiableList(findings);
    }

    public boolean isDeveloperProcessLimitOverride(HostRuntimeProfile host) {
        Objects.requireNonNull(host, "host");
        return host.effectiveCachedProcessLimit() > 0
                && host.customizedCachedProcessLimit() > 0
                && host.effectiveCachedProcessLimit() < host.customizedCachedProcessLimit();
    }

    public boolean hasHighSeverityConflict(List<Finding> findings) {
        Objects.requireNonNull(findings, "findings");
        return findings.stream().anyMatch(f -> f.severity() == Severity.HIGH);
    }
}
