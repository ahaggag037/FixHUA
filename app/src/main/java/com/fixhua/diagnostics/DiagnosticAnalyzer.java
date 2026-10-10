package com.fixhua.diagnostics;

/**
 * Evidence-based classifier for diagnostic samples.
 *
 * It intentionally reports correlations / likely causes rather than claiming
 * certainty. Inputs come from public Android APIs or read-only proc/sysfs paths.
 */
final class DiagnosticAnalyzer {
    private DiagnosticAnalyzer() {}

    static String classify(
            int thermalStatus,
            long availRamMb,
            long totalRamMb,
            double psiSomeAvg10,
            double psiFullAvg10,
            double cpuDropPct,
            boolean networkValidated,
            boolean gboxBatteryExempt
    ) {
        StringBuilder out = new StringBuilder();

        if (thermalStatus >= 3) {
            add(out, "HIGH: thermal pressure may be throttling CPU/GPU or background work");
        } else if (thermalStatus == 2) {
            add(out, "MEDIUM: moderate thermal pressure; watch for frequency drops");
        }

        double availRatio = totalRamMb > 0 ? (double) availRamMb / (double) totalRamMb : 1.0;
        if (psiFullAvg10 >= 1.0 || psiSomeAvg10 >= 10.0 || availRatio < 0.08) {
            add(out, "HIGH: memory pressure / reclaim stalls are plausible");
        } else if (psiSomeAvg10 >= 3.0 || availRatio < 0.15) {
            add(out, "MEDIUM: elevated memory pressure");
        }

        if (cpuDropPct >= 35.0 && thermalStatus >= 2) {
            add(out, "HIGH: CPU frequency fell sharply while thermal pressure was elevated");
        } else if (cpuDropPct >= 45.0) {
            add(out, "MEDIUM: sharp CPU frequency drop observed; cause not proven");
        }

        if (!networkValidated) {
            add(out, "MEDIUM: active network is not validated; stalls may be connectivity-related");
        }

        if (!gboxBatteryExempt) {
            add(out, "MEDIUM: GBox remains battery-restricted and may be background-limited");
        }

        return out.length() == 0 ? "NONE: no strong anomaly detected in this sample" : out.toString();
    }

    private static void add(StringBuilder out, String item) {
        if (out.length() > 0) out.append(" | ");
        out.append(item);
    }
}
