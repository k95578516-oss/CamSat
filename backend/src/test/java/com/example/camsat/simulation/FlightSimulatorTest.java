package com.example.camsat.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.camsat.simulation.FlightSimulator.*;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FlightSimulatorTest {
    record Result(List<Sample> samples, List<Event> events, FlightSimulator sim) { }

    static Result run(Params p) {
        FlightSimulator sim = new FlightSimulator(p);
        List<Sample> s = new ArrayList<>(); List<Event> e = new ArrayList<>();
        int guard = 0;
        while (!sim.isFinished() && guard++ < 100_000) { Batch b = sim.nextSamples(); s.addAll(b.samples()); e.addAll(b.events()); }
        assertThat(sim.isFinished()).as("simulation terminates").isTrue();
        return new Result(s, e, sim);
    }

    static final Params TYPICAL = new Params(0.35, 725, 0.5, 1.5, 3.0, 90, 100);

    @Test
    void impactSpeedMatchesAnalyticTerminalVelocity() {
        Result r = run(TYPICAL);
        double cdA = 1.5 * Math.PI * 0.25 * 0.25 + FlightSimulator.BODY_CD * FlightSimulator.BODY_AREA_M2;
        double vt = Math.sqrt(2 * 0.35 * FlightSimulator.G / (FlightSimulator.isaDensity(0) * cdA));
        assertThat(r.sim().outcome()).isEqualTo(Outcome.COMPLETED);
        assertThat(r.sim().impactSpeedMps()).isCloseTo(vt, org.assertj.core.data.Percentage.withPercentage(5));
    }

    @Test
    void driftIsBoundedByWindSpeedTimesFlightTimeAndZeroInCalmAir() {
        Result windy = run(TYPICAL);
        assertThat(windy.sim().landingDriftMeters()).isBetween(1.0, 3.0 * windy.sim().landingTimeSeconds() * 1.05);
        assertThat(run(new Params(0.35, 725, 0.5, 1.5, 0.0, 0, 100)).sim().landingDriftMeters()).isLessThan(0.01);
    }

    @Test
    void telemetryIsPhysicallyOrdered() {
        Result r = run(TYPICAL);
        for (int i = 1; i < r.samples().size(); i++) {
            Sample a = r.samples().get(i - 1), b = r.samples().get(i);
            assertThat(b.elapsedS()).isGreaterThanOrEqualTo(a.elapsedS());
            assertThat(b.altitudeM()).isLessThanOrEqualTo(a.altitudeM() + 1e-6).isGreaterThanOrEqualTo(0);
            assertThat(b.batteryPercent()).isLessThanOrEqualTo(a.batteryPercent() + 1e-9);
        }
        assertThat(r.samples().get(0).state()).isEqualTo(Flight.READY);
        Sample last = r.samples().get(r.samples().size() - 1);
        assertThat(last.state()).isEqualTo(Flight.RECOVERY);
        assertThat(last.altitudeM()).isZero();
        assertThat(last.verticalVelocityMps()).isZero();
    }

    @Test
    void eachFlightEventFiresExactlyOnceInOrder() {
        Result r = run(TYPICAL);
        Map<EventKind, Integer> counts = new EnumMap<>(EventKind.class);
        r.events().forEach(e -> counts.merge(e.kind(), 1, Integer::sum));
        assertThat(counts).containsEntry(EventKind.DESCENT_STARTED, 1).containsEntry(EventKind.PARACHUTE_DEPLOYED, 1)
                .containsEntry(EventKind.LANDING_DETECTED, 1).containsEntry(EventKind.RECOVERY_STARTED, 1)
                .containsEntry(EventKind.RECOVERY_COMPLETED, 1).doesNotContainKey(EventKind.LOW_BATTERY);
        for (int i = 1; i < r.events().size(); i++) assertThat(r.events().get(i).elapsedS()).isGreaterThanOrEqualTo(r.events().get(i - 1).elapsedS());
    }

    @Test
    void identicalInputsGiveIdenticalOutput() {
        Result a = run(TYPICAL), b = run(TYPICAL);
        assertThat(a.samples()).isEqualTo(b.samples());
        assertThat(a.events()).isEqualTo(b.events());
    }

    @Test
    void undersizedParachuteIsADestructiveImpact() {
        Result r = run(new Params(2.0, 300, 0.05, 0.8, 5, 0, 100));
        assertThat(r.sim().outcome()).isEqualTo(Outcome.FAILED);
        assertThat(r.sim().impactSpeedMps()).isGreaterThan(FlightSimulator.MAX_SURVIVABLE_IMPACT_MPS);
    }

    @Test
    void lowBatteryIsFlaggedOnceAndExhaustionAbortsBeforeTouchdown() {
        assertThat(run(new Params(0.35, 725, 0.5, 1.5, 3, 90, 15)).events().stream().filter(e -> e.kind() == EventKind.LOW_BATTERY)).hasSize(1);
        Result dead = run(new Params(0.35, 725, 0.5, 1.5, 3, 90, 0.2));
        assertThat(dead.sim().outcome()).isEqualTo(Outcome.FAILED);
        assertThat(dead.samples().get(dead.samples().size() - 1).state()).isEqualTo(Flight.ABORTED);
        assertThat(dead.events().stream().filter(e -> e.kind() == EventKind.LOW_BATTERY)).hasSize(1);
    }

    @Test
    void stiffLightPayloadUnderLargeCanopyStaysStableOrFailsCleanly() {
        // 0.05 kg under a 3 m canopy: drag stiffness ~150/s. A fixed 0.05 s RK4 step diverges here; adaptive stepping must not.
        Result r = run(new Params(0.05, 725, 3.0, 2.0, 5, 0, 100));
        for (Sample s : r.samples()) {
            assertThat(Double.isFinite(s.altitudeM()) && Double.isFinite(s.verticalVelocityMps())).isTrue();
            assertThat(Math.abs(s.verticalVelocityMps())).isLessThan(FlightSimulator.MAX_PLAUSIBLE_SPEED_MPS);
        }
        if (r.sim().outcome() == Outcome.COMPLETED) assertThat(r.sim().impactSpeedMps()).isLessThan(1.0);
    }

    @Test
    void extremeButValidInputsTerminate() {
        assertThat(run(new Params(50, 10_000, 0.1, 0.1, 50, 359.9, 100)).sim().isFinished()).isTrue();
        assertThat(run(new Params(0.05, 20, 3, 2.0, 50, 10, 1)).sim().isFinished()).isTrue();
        assertThat(run(new Params(0.05, 10_000, 3, 2.0, 0, 0, 100)).sim().isFinished()).isTrue();
    }

    @Test
    void nonFiniteOrNonPhysicalParametersAreRejected() {
        assertThatThrownBy(() -> new FlightSimulator(new Params(Double.NaN, 100, 1, 1, 0, 0, 50))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FlightSimulator(new Params(1, Double.POSITIVE_INFINITY, 1, 1, 0, 0, 50))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FlightSimulator(new Params(1, -5, 1, 1, 0, 0, 50))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FlightSimulator(new Params(1, 100, 1, 1, -1, 0, 50))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FlightSimulator(new Params(1, 100, 1, 1, 0, 0, 0))).isInstanceOf(IllegalArgumentException.class);
    }
}
