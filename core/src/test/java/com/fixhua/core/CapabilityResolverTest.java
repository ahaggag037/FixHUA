package com.fixhua.core;

import static com.fixhua.core.CoreModels.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.Test;

public class CapabilityResolverTest {
    private static ProviderDescriptor provider(
            String id,
            AuthorityPlane plane,
            Set<Integer> produces,
            Set<Integer> requires,
            Set<TruthScope> scopes,
            ProviderSemantics semantics,
            boolean destructive) {
        return new ProviderDescriptor(
                id,
                "1",
                plane,
                produces,
                requires,
                scopes,
                semantics,
                Persistence.STATIC,
                destructive,
                0,
                0);
    }

    @Test
    public void exactSemanticsBeatLowerPrivilegeDegradedProvider() throws Exception {
        CapabilityResolver resolver = new CapabilityResolver();
        CapabilityRequest request = new CapabilityRequest(
                10,
                Set.of(TruthScope.HOST_REAL),
                ProviderSemantics.DEGRADED,
                false);

        ProviderDescriptor degradedP0 = provider(
                "public-degraded",
                AuthorityPlane.P0_PUBLIC,
                Set.of(10),
                Set.of(),
                Set.of(TruthScope.HOST_REAL),
                ProviderSemantics.DEGRADED,
                false);
        ProviderDescriptor exactP1 = provider(
                "user-exact",
                AuthorityPlane.P1_USER_AUTHORIZED,
                Set.of(10),
                Set.of(),
                Set.of(TruthScope.HOST_REAL),
                ProviderSemantics.EXACT,
                false);

        ResolutionPlan plan = resolver.resolve(request, List.of(degradedP0, exactP1));
        assertEquals("user-exact", plan.steps().get(0).providerId());
    }

    @Test
    public void lowerPrivilegeWinsWhenSemanticsMatch() throws Exception {
        CapabilityResolver resolver = new CapabilityResolver();
        CapabilityRequest request = new CapabilityRequest(
                11,
                Set.of(TruthScope.HOST_REAL),
                ProviderSemantics.EXACT,
                false);

        ProviderDescriptor p3 = provider(
                "shell",
                AuthorityPlane.P3_SHELL_BINDER,
                Set.of(11),
                Set.of(),
                Set.of(TruthScope.HOST_REAL),
                ProviderSemantics.EXACT,
                false);
        ProviderDescriptor p0 = provider(
                "public",
                AuthorityPlane.P0_PUBLIC,
                Set.of(11),
                Set.of(),
                Set.of(TruthScope.HOST_REAL),
                ProviderSemantics.EXACT,
                false);

        ResolutionPlan plan = resolver.resolve(request, List.of(p3, p0));
        assertEquals("public", plan.steps().get(0).providerId());
    }

    @Test
    public void syntheticProviderCannotSatisfyHostOnlyRequirement() {
        CapabilityResolver resolver = new CapabilityResolver();
        CapabilityRequest request = new CapabilityRequest(
                59,
                Set.of(TruthScope.HOST_REAL),
                ProviderSemantics.EXACT,
                false);
        ProviderDescriptor synthetic = provider(
                "synthetic-pm",
                AuthorityPlane.P5_SYNTHETIC,
                Set.of(59),
                Set.of(),
                Set.of(TruthScope.SYNTHETIC_VIEW),
                ProviderSemantics.EXACT,
                false);

        CapabilityResolver.ResolutionException ex = assertThrows(
                CapabilityResolver.ResolutionException.class,
                () -> resolver.resolve(request, List.of(synthetic)));
        assertTrue(ex.getMessage().contains("No provider"));
    }

    @Test
    public void dependencyCycleIsRejected() {
        CapabilityResolver resolver = new CapabilityResolver();
        CapabilityRequest request = new CapabilityRequest(
                1,
                Set.of(TruthScope.HOST_REAL),
                ProviderSemantics.EXACT,
                false);
        ProviderDescriptor a = provider(
                "a",
                AuthorityPlane.P0_PUBLIC,
                Set.of(1),
                Set.of(2),
                Set.of(TruthScope.HOST_REAL),
                ProviderSemantics.EXACT,
                false);
        ProviderDescriptor b = provider(
                "b",
                AuthorityPlane.P0_PUBLIC,
                Set.of(2),
                Set.of(1),
                Set.of(TruthScope.HOST_REAL),
                ProviderSemantics.EXACT,
                false);

        CapabilityResolver.ResolutionException ex = assertThrows(
                CapabilityResolver.ResolutionException.class,
                () -> resolver.resolve(request, List.of(a, b)));
        assertTrue(ex.getMessage().contains("cycle"));
    }

    @Test
    public void destructiveProviderNeedsExplicitAllowance() {
        CapabilityResolver resolver = new CapabilityResolver();
        ProviderDescriptor destructive = provider(
                "clear-data",
                AuthorityPlane.P3_SHELL_BINDER,
                Set.of(15),
                Set.of(),
                Set.of(TruthScope.HOST_DELEGATED),
                ProviderSemantics.EXACT,
                true);

        CapabilityRequest denied = new CapabilityRequest(
                15,
                Set.of(TruthScope.HOST_DELEGATED),
                ProviderSemantics.EXACT,
                false);
        assertThrows(
                CapabilityResolver.ResolutionException.class,
                () -> resolver.resolve(denied, List.of(destructive)));

        CapabilityRequest allowed = new CapabilityRequest(
                15,
                Set.of(TruthScope.HOST_DELEGATED),
                ProviderSemantics.EXACT,
                true);
        ResolutionPlan plan = resolver.resolve(allowed, List.of(destructive));
        assertEquals("clear-data", plan.steps().get(0).providerId());
    }

    @Test
    public void planDigestIsDeterministic() throws Exception {
        CapabilityResolver resolver = new CapabilityResolver();
        CapabilityRequest request = new CapabilityRequest(
                22,
                Set.of(TruthScope.SYNTHETIC_VIEW),
                ProviderSemantics.EXACT,
                false);
        ProviderDescriptor provider = provider(
                "synthetic-pm",
                AuthorityPlane.P5_SYNTHETIC,
                Set.of(22),
                Set.of(),
                Set.of(TruthScope.SYNTHETIC_VIEW),
                ProviderSemantics.EXACT,
                false);

        String a = resolver.resolve(request, List.of(provider)).digestSha256();
        String b = resolver.resolve(request, List.of(provider)).digestSha256();
        assertEquals(a, b);
        assertEquals(64, a.length());
    }
}
