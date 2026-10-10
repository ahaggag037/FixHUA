package com.fixhua.diagnostics;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AutomaticIncidentDetectorTest {
    private static AutomaticIncidentDetector.Input input(
            long now, long late, double ramRatio, boolean ramLow,
            boolean memAvailable, double memSome, double memFull,
            boolean ioAvailable, double ioSome, double cpuLoad,
            double cpuDrop, int thermal) {
        return new AutomaticIncidentDetector.Input(now, late, ramRatio, ramLow,
                memAvailable, memSome, memFull, ioAvailable, ioSome,
                cpuLoad, cpuDrop, thermal);
    }

    @Test
    public void quietSamplesStayNormal() {
        AutomaticIncidentDetector d = new AutomaticIncidentDetector();
        AutomaticIncidentDetector.Result r = d.update(input(
                1_000, 20, 0.45, false, false, Double.NaN, Double.NaN,
                false, Double.NaN, 0.40, 65.0, 0));
        assertEquals(AutomaticIncidentDetector.State.NORMAL, r.state);
        assertEquals(AutomaticIncidentDetector.Transition.NONE, r.transition);
        assertFalse(d.elevatedSampling());
    }

    @Test
    public void frequencyDropAloneDoesNotDeclareIncident() {
        AutomaticIncidentDetector d = new AutomaticIncidentDetector();
        AutomaticIncidentDetector.Result r = d.update(input(
                1_000, 0, 0.40, false, false, Double.NaN, Double.NaN,
                false, Double.NaN, 0.95, 55.0, 0));
        assertEquals(AutomaticIncidentDetector.State.NORMAL, r.state);
        assertTrue(r.score < 3);
    }

    @Test
    public void strongMultiSignalStallStartsIncidentImmediately() {
        AutomaticIncidentDetector d = new AutomaticIncidentDetector();
        AutomaticIncidentDetector.Result r = d.update(input(
                1_000, 1_300, 0.06, true, true, 15.0, 2.0,
                true, 25.0, 0.90, 50.0, 4));
        assertEquals(AutomaticIncidentDetector.State.INCIDENT, r.state);
        assertEquals(AutomaticIncidentDetector.Transition.INCIDENT_STARTED, r.transition);
        assertTrue(r.incidentId > 0L);
        assertTrue(d.elevatedSampling());
    }

    @Test
    public void incidentClosesOnlyAfterStableRecoveryWindow() {
        AutomaticIncidentDetector d = new AutomaticIncidentDetector();
        d.update(input(1_000, 1_300, 0.06, true, true, 15.0, 2.0,
                true, 25.0, 0.90, 50.0, 4));

        AutomaticIncidentDetector.Result recovery = d.update(input(
                2_000, 0, 0.50, false, false, Double.NaN, Double.NaN,
                false, Double.NaN, 0.30, 0.0, 0));
        assertEquals(AutomaticIncidentDetector.State.RECOVERY, recovery.state);

        AutomaticIncidentDetector.Result tooSoon = d.update(input(
                10_000, 0, 0.50, false, false, Double.NaN, Double.NaN,
                false, Double.NaN, 0.30, 0.0, 0));
        assertEquals(AutomaticIncidentDetector.State.RECOVERY, tooSoon.state);

        AutomaticIncidentDetector.Result closed = d.update(input(
                14_100, 0, 0.50, false, false, Double.NaN, Double.NaN,
                false, Double.NaN, 0.30, 0.0, 0));
        assertEquals(AutomaticIncidentDetector.State.NORMAL, closed.state);
        assertEquals(AutomaticIncidentDetector.Transition.INCIDENT_RECOVERED, closed.transition);
        assertEquals(1, d.completedIncidents());
    }
}
