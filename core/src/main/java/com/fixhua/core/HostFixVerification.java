package com.fixhua.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Deterministic before/after comparison for host configuration facts. */
public final class HostFixVerification {
    public record Result(
            HostVerificationSnapshot before,
            HostVerificationSnapshot after,
            List<String> resolvedFindingCodes,
            List<String> remainingFindingCodes,
            List<String> introducedFindingCodes,
            boolean processLimitChanged,
            boolean processLimitRestoredToOem,
            boolean force4xMsaaDisabled,
            boolean alwaysFinishActivitiesDisabled) {
        public Result {
            Objects.requireNonNull(before, "before");
            Objects.requireNonNull(after, "after");
            resolvedFindingCodes = immutable(resolvedFindingCodes);
            remainingFindingCodes = immutable(remainingFindingCodes);
            introducedFindingCodes = immutable(introducedFindingCodes);
        }

        public boolean hasVerifiedImprovement() {
            return !resolvedFindingCodes.isEmpty()
                    || processLimitRestoredToOem
                    || force4xMsaaDisabled
                    || alwaysFinishActivitiesDisabled;
        }

        public boolean hasNewProblem() {
            return !introducedFindingCodes.isEmpty();
        }

        private static List<String> immutable(List<String> values) {
            return Collections.unmodifiableList(new ArrayList<>(values));
        }
    }

    public Result compare(HostVerificationSnapshot before, HostVerificationSnapshot after) {
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(after, "after");

        Set<String> beforeCodes = new LinkedHashSet<>(before.activeFindingCodes());
        Set<String> afterCodes = new LinkedHashSet<>(after.activeFindingCodes());

        List<String> resolved = difference(beforeCodes, afterCodes);
        List<String> remaining = intersection(beforeCodes, afterCodes);
        List<String> introduced = difference(afterCodes, beforeCodes);

        boolean processLimitChanged = before.currentMaxCachedProcesses()
                != after.currentMaxCachedProcesses();
        boolean restoredToOem = before.currentMaxCachedProcesses() > 0
                && before.customizedMaxCachedProcesses() > 0
                && before.currentMaxCachedProcesses() < before.customizedMaxCachedProcesses()
                && after.customizedMaxCachedProcesses() > 0
                && after.currentMaxCachedProcesses() >= after.customizedMaxCachedProcesses();
        boolean msaaDisabled = before.force4xMsaa() && !after.force4xMsaa();
        boolean alwaysFinishDisabled = before.alwaysFinishActivities()
                && !after.alwaysFinishActivities();

        return new Result(
                before,
                after,
                resolved,
                remaining,
                introduced,
                processLimitChanged,
                restoredToOem,
                msaaDisabled,
                alwaysFinishDisabled);
    }

    private static List<String> difference(Set<String> left, Set<String> right) {
        List<String> out = new ArrayList<>();
        for (String value : left) {
            if (!right.contains(value)) out.add(value);
        }
        Collections.sort(out);
        return out;
    }

    private static List<String> intersection(Set<String> left, Set<String> right) {
        List<String> out = new ArrayList<>();
        for (String value : left) {
            if (right.contains(value)) out.add(value);
        }
        Collections.sort(out);
        return out;
    }
}
