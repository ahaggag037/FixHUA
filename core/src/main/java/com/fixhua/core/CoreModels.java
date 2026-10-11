package com.fixhua.core;

import java.util.List;
import java.util.Set;

public final class CoreModels {
    public enum TruthScope {
        HOST_REAL,
        HOST_DELEGATED,
        SYNTHETIC_VIEW,
        GUEST_REAL
    }

    public enum AuthorityPlane {
        P0_PUBLIC,
        P1_USER_AUTHORIZED,
        P2_PERSISTENT_DELEGATED,
        P3_SHELL_BINDER,
        P4_HUAWEI_TRUSTED,
        P5_SYNTHETIC,
        P6_GUEST_ROOT,
        P7_HOST_ROOT
    }

    public enum ProviderSemantics {
        EXACT,
        EQUIVALENT,
        DEGRADED,
        FALLBACK_ONLY
    }

    public enum Persistence {
        STATIC,
        USER_GRANTED,
        BOOTSTRAPPED,
        LIVE_BROKER,
        SESSION
    }

    public record ProviderDescriptor(
            String providerId,
            String providerVersion,
            AuthorityPlane authorityPlane,
            Set<Integer> produces,
            Set<Integer> requires,
            Set<TruthScope> truthScopes,
            ProviderSemantics semantics,
            Persistence persistence,
            boolean destructive,
            int frictionRank,
            int overheadRank) {
        public ProviderDescriptor {
            if (providerId == null || providerId.isBlank()) throw new IllegalArgumentException("providerId");
            if (providerVersion == null || providerVersion.isBlank()) throw new IllegalArgumentException("providerVersion");
            produces = Set.copyOf(produces);
            requires = Set.copyOf(requires);
            truthScopes = Set.copyOf(truthScopes);
            if (produces.isEmpty()) throw new IllegalArgumentException("produces");
            if (truthScopes.isEmpty()) throw new IllegalArgumentException("truthScopes");
            if (frictionRank < 0 || overheadRank < 0) throw new IllegalArgumentException("ranks must be non-negative");
        }
    }

    public record CapabilityRequest(
            int capabilityId,
            Set<TruthScope> allowedTruthScopes,
            ProviderSemantics minimumSemantics,
            boolean destructiveAllowed) {
        public CapabilityRequest {
            if (capabilityId <= 0) throw new IllegalArgumentException("capabilityId");
            allowedTruthScopes = Set.copyOf(allowedTruthScopes);
            if (allowedTruthScopes.isEmpty()) throw new IllegalArgumentException("allowedTruthScopes");
        }
    }

    public record ResolutionStep(
            int capabilityId,
            String providerId,
            String providerVersion,
            AuthorityPlane authorityPlane,
            TruthScope truthScope,
            ProviderSemantics semantics) {}

    public record ResolutionPlan(
            List<ResolutionStep> steps,
            String digestSha256) {
        public ResolutionPlan {
            steps = List.copyOf(steps);
            if (steps.isEmpty()) throw new IllegalArgumentException("steps");
            if (digestSha256 == null || digestSha256.length() != 64) throw new IllegalArgumentException("digestSha256");
        }
    }

    public record VirtualClientIdentity(
            String virtualPackageName,
            String originalSigningDigest,
            String syntheticSigningView,
            int virtualUid,
            int virtualPid,
            int hostUid,
            String runtimeInstanceId) {}

    private CoreModels() {}
}
