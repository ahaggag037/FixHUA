package com.fixhua.core;

/** Structured, bounded evidence emitted for every compatibility decision in a managed Runtime. */
public final class ExecutionEvidence {
    public enum Decision {
        HOST,
        SHIM,
        SYNTHETIC,
        DELEGATED,
        SAFE_STUB,
        UNSUPPORTED
    }

    public record Event(
            long sequence,
            int capabilityId,
            String providerId,
            Decision decision,
            CoreModels.TruthScope truthScope,
            String subject,
            String outcome) {
        public Event {
            if (sequence <= 0) throw new IllegalArgumentException("sequence");
            if (capabilityId <= 0) throw new IllegalArgumentException("capabilityId");
            providerId = bounded(providerId, "providerId", 96);
            if (decision == null) throw new IllegalArgumentException("decision");
            if (truthScope == null) throw new IllegalArgumentException("truthScope");
            subject = bounded(subject, "subject", 160);
            outcome = bounded(outcome, "outcome", 160);
        }

        public String canonicalLine() {
            return "v=1"
                    + "|seq=" + sequence
                    + "|capability=" + capabilityId
                    + "|provider=" + providerId
                    + "|decision=" + decision
                    + "|truth=" + truthScope
                    + "|subject=" + subject
                    + "|outcome=" + outcome;
        }
    }

    private static String bounded(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field);
        if (value.length() > maxLength) throw new IllegalArgumentException(field + " too long");
        if (value.indexOf('|') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            throw new IllegalArgumentException(field + " contains reserved characters");
        }
        return value;
    }

    private ExecutionEvidence() {}
}
