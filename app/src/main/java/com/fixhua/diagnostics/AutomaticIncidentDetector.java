package com.fixhua.diagnostics;

import java.util.ArrayList;
import java.util.List;

/** Lightweight state machine for automatic lag/stall incident detection. */
final class AutomaticIncidentDetector {
    enum State { NORMAL, SUSPECTED, INCIDENT, RECOVERY }
    enum Transition { NONE, SUSPECTED, FALSE_ALARM, INCIDENT_STARTED, RECOVERY_STARTED, INCIDENT_RECOVERED }

    static final class Input {
        final long nowElapsedMs;
        final long samplerLateMs;
        final double ramAvailableRatio;
        final boolean ramLow;
        final boolean memoryPsiAvailable;
        final double memoryPsiSomeAvg10;
        final double memoryPsiFullAvg10;
        final boolean ioPsiAvailable;
        final double ioPsiSomeAvg10;
        final double cpuLoad;
        final double cpuDropPct;
        final int thermalStatus;

        Input(long nowElapsedMs, long samplerLateMs, double ramAvailableRatio, boolean ramLow,
              boolean memoryPsiAvailable, double memoryPsiSomeAvg10, double memoryPsiFullAvg10,
              boolean ioPsiAvailable, double ioPsiSomeAvg10, double cpuLoad, double cpuDropPct,
              int thermalStatus) {
            this.nowElapsedMs = nowElapsedMs;
            this.samplerLateMs = samplerLateMs;
            this.ramAvailableRatio = ramAvailableRatio;
            this.ramLow = ramLow;
            this.memoryPsiAvailable = memoryPsiAvailable;
            this.memoryPsiSomeAvg10 = memoryPsiSomeAvg10;
            this.memoryPsiFullAvg10 = memoryPsiFullAvg10;
            this.ioPsiAvailable = ioPsiAvailable;
            this.ioPsiSomeAvg10 = ioPsiSomeAvg10;
            this.cpuLoad = cpuLoad;
            this.cpuDropPct = cpuDropPct;
            this.thermalStatus = thermalStatus;
        }
    }

    static final class Result {
        final State state;
        final Transition transition;
        final int score;
        final String reasons;
        final long incidentId;

        Result(State state, Transition transition, int score, String reasons, long incidentId) {
            this.state = state;
            this.transition = transition;
            this.score = score;
            this.reasons = reasons;
            this.incidentId = incidentId;
        }
    }

    private State state = State.NORMAL;
    private long stateSinceMs;
    private long currentIncidentId;
    private long nextIncidentId = 1L;
    private int completedIncidents;

    State state() { return state; }
    long currentIncidentId() { return currentIncidentId; }
    int completedIncidents() { return completedIncidents; }
    boolean elevatedSampling() { return state != State.NORMAL; }

    Result update(Input in) {
        Score scored = score(in);
        int score = scored.value;
        boolean suspicious = score >= 3;
        boolean hardSamplerStall = in.samplerLateMs >= 1_200L;
        boolean strong = score >= 6 || hardSamplerStall;
        boolean calm = score <= 1;
        Transition transition = Transition.NONE;

        if (stateSinceMs == 0L) stateSinceMs = in.nowElapsedMs;

        switch (state) {
            case NORMAL:
                if (strong) {
                    beginIncident(in.nowElapsedMs);
                    transition = Transition.INCIDENT_STARTED;
                } else if (suspicious) {
                    state = State.SUSPECTED;
                    stateSinceMs = in.nowElapsedMs;
                    transition = Transition.SUSPECTED;
                }
                break;
            case SUSPECTED:
                if (strong || (suspicious && in.nowElapsedMs - stateSinceMs >= 1_500L)) {
                    beginIncident(in.nowElapsedMs);
                    transition = Transition.INCIDENT_STARTED;
                } else if (!suspicious && in.nowElapsedMs - stateSinceMs >= 2_000L) {
                    state = State.NORMAL;
                    stateSinceMs = in.nowElapsedMs;
                    transition = Transition.FALSE_ALARM;
                }
                break;
            case INCIDENT:
                if (calm) {
                    state = State.RECOVERY;
                    stateSinceMs = in.nowElapsedMs;
                    transition = Transition.RECOVERY_STARTED;
                }
                break;
            case RECOVERY:
                if (suspicious || hardSamplerStall) {
                    state = State.INCIDENT;
                    stateSinceMs = in.nowElapsedMs;
                } else if (calm && in.nowElapsedMs - stateSinceMs >= 12_000L) {
                    state = State.NORMAL;
                    stateSinceMs = in.nowElapsedMs;
                    completedIncidents++;
                    transition = Transition.INCIDENT_RECOVERED;
                    currentIncidentId = 0L;
                }
                break;
        }

        return new Result(state, transition, score, scored.reasons, currentIncidentId);
    }

    private void beginIncident(long now) {
        state = State.INCIDENT;
        stateSinceMs = now;
        currentIncidentId = nextIncidentId++;
    }

    private static Score score(Input in) {
        int value = 0;
        List<String> reasons = new ArrayList<>();

        if (in.samplerLateMs >= 1_200L) { value += 4; reasons.add("sampler_late>=1200ms"); }
        else if (in.samplerLateMs >= 600L) { value += 3; reasons.add("sampler_late>=600ms"); }
        else if (in.samplerLateMs >= 250L) { value += 1; reasons.add("sampler_late>=250ms"); }

        if (in.ramLow) { value += 4; reasons.add("ramLow"); }
        if (in.ramAvailableRatio >= 0.0 && in.ramAvailableRatio < 0.08) {
            value += 3; reasons.add("ram_available<8%");
        } else if (in.ramAvailableRatio >= 0.0 && in.ramAvailableRatio < 0.15) {
            value += 1; reasons.add("ram_available<15%");
        }

        if (in.memoryPsiAvailable) {
            if (in.memoryPsiFullAvg10 >= 1.0) { value += 3; reasons.add("memory_psi_full>=1"); }
            if (in.memoryPsiSomeAvg10 >= 10.0) { value += 2; reasons.add("memory_psi_some>=10"); }
            else if (in.memoryPsiSomeAvg10 >= 3.0) { value += 1; reasons.add("memory_psi_some>=3"); }
        }

        if (in.ioPsiAvailable) {
            if (in.ioPsiSomeAvg10 >= 20.0) { value += 2; reasons.add("io_psi_some>=20"); }
            else if (in.ioPsiSomeAvg10 >= 8.0) { value += 1; reasons.add("io_psi_some>=8"); }
        }

        if (in.cpuLoad >= 0.85 && in.cpuDropPct >= 45.0) {
            value += 2; reasons.add("cpu_loaded_freq_drop>=45%");
        } else if (in.cpuLoad >= 0.70 && in.cpuDropPct >= 30.0) {
            value += 1; reasons.add("cpu_loaded_freq_drop>=30%");
        }

        if (in.thermalStatus >= 4) { value += 2; reasons.add("thermal_severe+"); }
        else if (in.thermalStatus >= 3) { value += 1; reasons.add("thermal_moderate+"); }

        String detail = reasons.isEmpty() ? "none" : String.join(",", reasons);
        return new Score(value, detail);
    }

    static String confidenceForScore(int score) {
        if (score >= 7) return "HIGH";
        if (score >= 4) return "MEDIUM";
        return "LOW";
    }

    private static final class Score {
        final int value;
        final String reasons;
        Score(int value, String reasons) {
            this.value = value;
            this.reasons = reasons;
        }
    }
}
