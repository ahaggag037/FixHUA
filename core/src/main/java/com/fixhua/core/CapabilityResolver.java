package com.fixhua.core;

import static com.fixhua.core.CoreModels.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CapabilityResolver {
    public static final class ResolutionException extends Exception {
        public ResolutionException(String message) { super(message); }
    }

    public ResolutionPlan resolve(CapabilityRequest request, List<ProviderDescriptor> providers)
            throws ResolutionException {
        Map<Integer, List<ProviderDescriptor>> byCapability = index(providers);
        LinkedHashMap<Integer, ResolutionStep> steps = new LinkedHashMap<>();
        resolveCapability(request.capabilityId(), request, byCapability, steps, new HashSet<>());
        List<ResolutionStep> ordered = new ArrayList<>(steps.values());
        return new ResolutionPlan(ordered, digest(ordered));
    }

    private void resolveCapability(
            int capabilityId,
            CapabilityRequest rootRequest,
            Map<Integer, List<ProviderDescriptor>> byCapability,
            LinkedHashMap<Integer, ResolutionStep> steps,
            Set<Integer> visiting) throws ResolutionException {
        if (steps.containsKey(capabilityId)) return;
        if (!visiting.add(capabilityId)) {
            throw new ResolutionException("Dependency cycle at capability " + capabilityId);
        }

        List<ProviderDescriptor> candidates = byCapability.getOrDefault(capabilityId, List.of()).stream()
                .filter(p -> semanticAcceptable(p.semantics(), rootRequest.minimumSemantics()))
                .filter(p -> p.truthScopes().stream().anyMatch(rootRequest.allowedTruthScopes()::contains))
                .filter(p -> rootRequest.destructiveAllowed() || !p.destructive())
                .sorted(providerComparator(rootRequest.allowedTruthScopes()))
                .toList();

        ResolutionException lastFailure = null;
        for (ProviderDescriptor provider : candidates) {
            LinkedHashMap<Integer, ResolutionStep> trialSteps = new LinkedHashMap<>(steps);
            Set<Integer> trialVisiting = new HashSet<>(visiting);
            try {
                for (int dep : provider.requires()) {
                    resolveCapability(dep, rootRequest, byCapability, trialSteps, trialVisiting);
                }
                TruthScope chosenScope = chooseTruthScope(provider.truthScopes(), rootRequest.allowedTruthScopes());
                trialSteps.put(capabilityId, new ResolutionStep(
                        capabilityId,
                        provider.providerId(),
                        provider.providerVersion(),
                        provider.authorityPlane(),
                        chosenScope,
                        provider.semantics()));
                steps.clear();
                steps.putAll(trialSteps);
                visiting.remove(capabilityId);
                return;
            } catch (ResolutionException failure) {
                lastFailure = failure;
            }
        }

        visiting.remove(capabilityId);
        if (lastFailure != null) throw lastFailure;
        throw new ResolutionException("No provider for capability " + capabilityId
                + " matching truth=" + rootRequest.allowedTruthScopes()
                + " semantics>=" + rootRequest.minimumSemantics()
                + " destructiveAllowed=" + rootRequest.destructiveAllowed());
    }

    private static Map<Integer, List<ProviderDescriptor>> index(List<ProviderDescriptor> providers) {
        Map<Integer, List<ProviderDescriptor>> out = new HashMap<>();
        for (ProviderDescriptor provider : providers) {
            for (int capability : provider.produces()) {
                out.computeIfAbsent(capability, ignored -> new ArrayList<>()).add(provider);
            }
        }
        return out;
    }

    private static Comparator<ProviderDescriptor> providerComparator(Set<TruthScope> allowedScopes) {
        return Comparator
                .comparingInt((ProviderDescriptor p) -> p.semantics().ordinal())
                .thenComparingInt(p -> bestScopeRank(p.truthScopes(), allowedScopes))
                .thenComparingInt(p -> p.authorityPlane().ordinal())
                .thenComparingInt(p -> p.destructive() ? 1 : 0)
                .thenComparingInt(ProviderDescriptor::frictionRank)
                .thenComparingInt(ProviderDescriptor::overheadRank)
                .thenComparing(ProviderDescriptor::providerId)
                .thenComparing(ProviderDescriptor::providerVersion);
    }

    private static int bestScopeRank(Set<TruthScope> offered, Set<TruthScope> allowed) {
        int best = Integer.MAX_VALUE;
        for (TruthScope scope : offered) {
            if (allowed.contains(scope)) best = Math.min(best, scope.ordinal());
        }
        return best;
    }

    private static TruthScope chooseTruthScope(Set<TruthScope> offered, Set<TruthScope> allowed)
            throws ResolutionException {
        return offered.stream()
                .filter(allowed::contains)
                .min(Comparator.comparingInt(Enum::ordinal))
                .orElseThrow(() -> new ResolutionException("No compatible truth scope"));
    }

    private static boolean semanticAcceptable(ProviderSemantics offered, ProviderSemantics minimum) {
        return offered.ordinal() <= minimum.ordinal();
    }

    private static String digest(List<ResolutionStep> steps) throws ResolutionException {
        StringBuilder canonical = new StringBuilder("fixhua-plan-v1\n");
        for (ResolutionStep step : steps) {
            canonical.append(step.capabilityId()).append('|')
                    .append(step.providerId()).append('|')
                    .append(step.providerVersion()).append('|')
                    .append(step.authorityPlane()).append('|')
                    .append(step.truthScope()).append('|')
                    .append(step.semantics()).append('\n');
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new ResolutionException("SHA-256 unavailable");
        }
    }
}
