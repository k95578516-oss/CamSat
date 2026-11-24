package com.example.camsat.simulation;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic CanSat descent model. Pure Java: no Spring, no I/O, no clock.
 *
 * <h3>Physical assumptions (documented in backend/docs/SIMULATION_MODEL.md)</h3>
 * <ul>
 *   <li>The CanSat is released at the mission release altitude with zero vertical velocity (apogee release).</li>
 *   <li>Vertical dynamics: m dv/dt = -m g + D(v), with quadratic drag D = 0.5 rho Cd A |v| v, integrated by RK4
 *       at a fixed 0.05 s step. Gravity is the constant 9.80665 m/s^2.</li>
 *   <li>Air density and temperature follow the ISA troposphere model: T = 15 C - 6.5 C/km * h and
 *       rho = 1.225 (1 - 2.25577e-5 h)^4.25588 kg/m^3.</li>
 *   <li>Before the parachute opens the body drags with Cd 1.0 over a 0.0034 m^2 frontal area (a 66 mm can).</li>
 *   <li>The parachute deploys 2.0 s after release and inflates linearly over 1.0 s. Canopy area is pi D^2 / 4 and the
 *       canopy drag coefficient is the mission's drag coefficient.</li>
 *   <li>Horizontal motion: the wind blows at a constant speed <em>from</em> the mission wind direction (meteorological
 *       convention). The payload accelerates toward the wind velocity through the same drag force,
 *       dvh/dt = -(0.5 rho Cd A |v_rel| / m) (vh - vwind).</li>
 *   <li>Avionics draw 0.0021 %/s of battery during flight (about 150 mA from a 2000 mAh pack) and 20 % of that after
 *       touchdown (beacon only). Low battery is flagged below 20 %. Running out of battery before touchdown aborts the run.</li>
 *   <li>Touchdown faster than 15 m/s is treated as a destructive impact and fails the run.</li>
 *   <li>After touchdown a recovery team is dispatched 10 s later and walks to the payload at 1.4 m/s; completion takes
 *       20 s of handling plus drift / 1.4 s.</li>
 *   <li>No launch coordinates exist in the mission, so latitude/longitude are not produced.</li>
 * </ul>
 */
public final class FlightSimulator {

    public static final double G = 9.80665;
    public static final double STEP_S = 0.05;
    public static final double SAMPLE_INTERVAL_S = 1.0;
    public static final double MAX_FLIGHT_TIME_S = 7200.0;
    /** Smallest adaptive sub-step (s) and the global budget of integration steps for one flight. */
    public static final double MIN_STEP_S = 1e-4;
    public static final long MAX_STEPS = 6_000_000L;
    /** Any speed above this is physically impossible for this model and indicates numerical failure. */
    public static final double MAX_PLAUSIBLE_SPEED_MPS = 1000.0;
    public static final double BODY_CD = 1.0;
    public static final double BODY_AREA_M2 = 0.0034;
    public static final double DEPLOY_DELAY_S = 2.0;
    public static final double INFLATION_S = 1.0;
    public static final double LOW_BATTERY_PERCENT = 20.0;
    public static final double DRAIN_FLIGHT_PCT_PER_S = 0.0021;
    public static final double DRAIN_LANDED_FACTOR = 0.2;
    public static final double MAX_SURVIVABLE_IMPACT_MPS = 15.0;
    public static final double RECOVERY_DISPATCH_S = 10.0;
    public static final double RECOVERY_HANDLING_S = 20.0;
    public static final double RECOVERY_WALK_MPS = 1.4;

    public enum Flight { READY, DESCENT, PARACHUTE_DEPLOYED, LANDED, RECOVERY, ABORTED }
    public enum EventKind { DESCENT_STARTED, PARACHUTE_DEPLOYED, LOW_BATTERY, LANDING_DETECTED, RECOVERY_STARTED, RECOVERY_COMPLETED }
    public enum Outcome { COMPLETED, FAILED }

    public record Params(double massKg, double releaseAltitudeM, double parachuteDiameterM, double dragCoefficient,
                         double windSpeedMps, double windDirectionDeg, double initialBatteryPercent) {
        public boolean allFinite() {
            return Double.isFinite(massKg) && Double.isFinite(releaseAltitudeM) && Double.isFinite(parachuteDiameterM)
                    && Double.isFinite(dragCoefficient) && Double.isFinite(windSpeedMps)
                    && Double.isFinite(windDirectionDeg) && Double.isFinite(initialBatteryPercent);
        }
    }

    public record Sample(double elapsedS, double altitudeM, double verticalVelocityMps, double temperatureC,
                         double batteryPercent, Flight state, double horizontalDistanceM) { }

    public record Event(EventKind kind, double elapsedS, String message) { }

    /** Everything produced by one call to {@link #nextSamples()}. */
    public record Batch(List<Sample> samples, List<Event> events) { }

    private final Params p;
    private final double windToX; // east component of wind velocity (m/s)
    private final double windToY; // north component
    private final double chuteArea;

    private double t = 0, h, v = 0, x = 0, y = 0, vx, vy, battery;
    private Flight state = Flight.READY;
    private boolean chuteDeployed, lowBatteryFlagged, landed, started;
    private double deployTime = Double.NaN;
    private double landingTime = Double.NaN, impactSpeed = Double.NaN, landingDrift = Double.NaN;
    private double recoveryStartT = Double.NaN, recoveryEndT = Double.NaN;
    private long steps;
    private int recoveryStage;
    private boolean finished;
    private Outcome outcome;
    private String result;

    public FlightSimulator(Params params) {
        if (params == null || !params.allFinite()) throw new IllegalArgumentException("Simulation parameters must be finite numbers");
        if (params.massKg() <= 0 || params.releaseAltitudeM() <= 0 || params.parachuteDiameterM() <= 0
                || params.dragCoefficient() <= 0 || params.windSpeedMps() < 0
                || params.initialBatteryPercent() <= 0 || params.initialBatteryPercent() > 100) {
            throw new IllegalArgumentException("Simulation parameters are outside the physically valid range");
        }
        this.p = params;
        double toBearing = Math.toRadians(params.windDirectionDeg() + 180.0);
        this.windToX = params.windSpeedMps() * Math.sin(toBearing);
        this.windToY = params.windSpeedMps() * Math.cos(toBearing);
        this.chuteArea = Math.PI * params.parachuteDiameterM() * params.parachuteDiameterM() / 4.0;
        this.h = params.releaseAltitudeM();
        this.battery = params.initialBatteryPercent();
        // The payload leaves the carrier moving with the wind (it has been drifting with the carrier).
        this.vx = windToX;
        this.vy = windToY;
    }

    public boolean isFinished() { return finished; }
    public Outcome outcome() { return outcome; }
    public String result() { return result; }
    public double landingTimeSeconds() { return landingTime; }
    public double landingDriftMeters() { return landingDrift; }
    public double impactSpeedMps() { return impactSpeed; }
    public double elapsedSeconds() { return t; }
    public double releaseAltitude() { return p.releaseAltitudeM(); }

    /** Fraction of the flight completed, estimated from altitude lost (0..100). */
    public double progressPercent() {
        if (finished) return 100.0;
        if (!landed) return Math.min(95.0, 95.0 * (p.releaseAltitudeM() - Math.max(h, 0)) / p.releaseAltitudeM());
        return 97.0;
    }

    public static double isaTemperatureC(double altM) { return 15.0 - 0.0065 * altM; }

    public static double isaDensity(double altM) {
        double a = Math.max(0.0, Math.min(altM, 11000.0));
        return 1.225 * Math.pow(1.0 - 2.25577e-5 * a, 4.25588);
    }

    /** Effective drag area times drag coefficient (Cd*A) at flight time tt. */
    private double cdA(double tt) {
        double base = BODY_CD * BODY_AREA_M2;
        if (!chuteDeployed || Double.isNaN(deployTime)) return base;
        double frac = Math.min(1.0, Math.max(0.0, (tt - deployTime) / INFLATION_S));
        return base + frac * p.dragCoefficient() * chuteArea;
    }

    private Sample sample(Flight st) {
        return new Sample(round(t, 3), round(Math.max(h, 0), 3), round(v, 4), round(isaTemperatureC(Math.max(h, 0)), 3),
                round(Math.max(battery, 0), 4), st, Math.hypot(x, y));
    }

    private static double round(double value, int dp) {
        double s = Math.pow(10, dp);
        return Math.round(value * s) / s;
    }

    /**
     * Produces the next group of telemetry samples and events. Call repeatedly until {@link #isFinished()}.
     * Each call advances the flight by one sample interval (or emits the post-touchdown sequence in one batch).
     */
    public Batch nextSamples() {
        if (finished) throw new IllegalStateException("Simulation already finished");
        List<Sample> samples = new ArrayList<>();
        List<Event> events = new ArrayList<>();
        if (landed) { // touchdown already reported: advance the recovery sequence one stage per call
            stageRecovery(samples, events);
            return new Batch(samples, events);
        }
        if (!started) {
            started = true;
            samples.add(sample(Flight.READY));
            state = Flight.DESCENT;
            events.add(new Event(EventKind.DESCENT_STARTED, 0.0, "Released at " + round(h, 1) + " m; free descent started"));
            flagLowBattery(events);
            return new Batch(samples, events);
        }
        double target = t + SAMPLE_INTERVAL_S;
        while (t < target - 1e-9) {
            if (++steps > MAX_STEPS) {
                fail("Integration step budget exhausted (" + MAX_STEPS + " steps)", t, events);
                break;
            }
            deployIfDue(events); // must precede step sizing so the inflating canopy is accounted for
            if (!step(adaptiveDt(target - t), events)) {
                break; // touchdown, abort, or failure ended the in-flight phase
            }
        }
        if (finished) {
            samples.add(sample(state));
            return new Batch(samples, events);
        }
        samples.add(sample(state));
        return new Batch(samples, events);
    }

    private void deployIfDue(List<Event> events) {
        if (!chuteDeployed && t + 1e-12 >= DEPLOY_DELAY_S) {
            chuteDeployed = true;
            deployTime = t;
            state = Flight.PARACHUTE_DEPLOYED;
            events.add(new Event(EventKind.PARACHUTE_DEPLOYED, round(t, 3), "Parachute deployed at " + round(h, 1) + " m"));
        }
    }

    /**
     * Step size: at most STEP_S, small enough that dt * (drag stiffness) stays below 1 so that RK4 is stable for
     * light payloads under large canopies, and clipped to land exactly on the parachute deployment instant.
     */
    private double adaptiveDt(double remaining) {
        double dt = Math.min(STEP_S, remaining);
        if (!chuteDeployed && t < DEPLOY_DELAY_S - 1e-12 && t + dt > DEPLOY_DELAY_S) dt = DEPLOY_DELAY_S - t;
        double stiffness = isaDensity(Math.max(h, 0)) * cdA(t + dt) * Math.max(Math.abs(v), 0.5) / p.massKg();
        if (stiffness > 0) dt = Math.min(dt, 1.0 / stiffness);
        return Math.max(dt, Math.min(MIN_STEP_S, remaining));
    }

    /** Returns false when the in-flight phase ended during this step. */
    private boolean step(double dt, List<Event> events) {
        double h0 = h, v0 = v, t0 = t;
        // RK4 on (h, v) with drag area frozen per sub-evaluation time.
        double k1h = v0, k1v = acc(h0, v0, t0);
        double k2h = v0 + 0.5 * dt * k1v, k2v = acc(h0 + 0.5 * dt * k1h, v0 + 0.5 * dt * k1v, t0 + 0.5 * dt);
        double k3h = v0 + 0.5 * dt * k2v, k3v = acc(h0 + 0.5 * dt * k2h, v0 + 0.5 * dt * k2v, t0 + 0.5 * dt);
        double k4h = v0 + dt * k3v, k4v = acc(h0 + dt * k3h, v0 + dt * k3v, t0 + dt);
        double hN = h0 + dt / 6.0 * (k1h + 2 * k2h + 2 * k3h + k4h);
        double vN = v0 + dt / 6.0 * (k1v + 2 * k2v + 2 * k3v + k4v);

        // Horizontal relaxation toward the wind velocity.
        double rho = isaDensity(Math.max(h0, 0));
        double relV = Math.sqrt(Math.pow(vx - windToX, 2) + Math.pow(vy - windToY, 2) + v0 * v0);
        double rate = 0.5 * rho * cdA(t0) * relV / p.massKg();
        double decay = Math.exp(-rate * dt);
        double vxN = windToX + (vx - windToX) * decay;
        double vyN = windToY + (vy - windToY) * decay;
        double xN = x + 0.5 * (vx + vxN) * dt;
        double yN = y + 0.5 * (vy + vyN) * dt;

        if (!Double.isFinite(hN) || !Double.isFinite(vN) || !Double.isFinite(xN) || !Double.isFinite(yN)
                || Math.abs(vN) > MAX_PLAUSIBLE_SPEED_MPS) {
            fail("Numerical instability: unphysical or non-finite state", t0 + dt, events);
            return false;
        }
        double drainPerS = DRAIN_FLIGHT_PCT_PER_S;
        double batN = battery - drainPerS * dt;

        if (hN <= 0.0) {
            double frac = h0 <= 0 ? 0 : h0 / (h0 - hN);
            double tl = t0 + frac * dt;
            h = 0; v = v0 + frac * (vN - v0); t = tl;
            x = x + frac * (xN - x); y = y + frac * (yN - y);
            vx = vx + frac * (vxN - vx); vy = vy + frac * (vyN - vy);
            battery = battery - drainPerS * frac * dt;
            landed = true;
            landingTime = round(tl, 3);
            impactSpeed = Math.abs(v);
            landingDrift = round(Math.hypot(x, y), 2);
            state = Flight.LANDED;
            events.add(new Event(EventKind.LANDING_DETECTED, landingTime,
                    "Touchdown at " + round(impactSpeed, 2) + " m/s, " + round(landingDrift, 1) + " m from release point"));
            if (impactSpeed > MAX_SURVIVABLE_IMPACT_MPS) {
                finished = true; outcome = Outcome.FAILED;
                result = "Destructive impact at " + round(impactSpeed, 2) + " m/s (limit " + MAX_SURVIVABLE_IMPACT_MPS
                        + " m/s); parachute too small or deployed too late";
            }
            return false;
        }
        h = hN; v = vN; x = xN; y = yN; vx = vxN; vy = vyN; t = round(t0 + dt, 9); battery = batN;
        flagLowBattery(events);
        if (battery <= 0.0) {
            battery = 0;
            state = Flight.ABORTED;
            finished = true; outcome = Outcome.FAILED;
            result = "Battery depleted at " + round(h, 1) + " m before touchdown; telemetry lost";
            return false;
        }
        if (t > MAX_FLIGHT_TIME_S) {
            fail("Flight exceeded the maximum simulated duration of " + (int) MAX_FLIGHT_TIME_S + " s", t, events);
            return false;
        }
        return true;
    }

    /** dv/dt = -g - (0.5 rho Cd*A |v| v) / m : drag always opposes the direction of motion. */
    private double acc(double alt, double vel, double tt) {
        double rho = isaDensity(Math.max(alt, 0));
        return -G - 0.5 * rho * cdA(tt) * Math.abs(vel) * vel / p.massKg();
    }

    private void flagLowBattery(List<Event> events) {
        if (!lowBatteryFlagged && battery < LOW_BATTERY_PERCENT) {
            lowBatteryFlagged = true;
            events.add(new Event(EventKind.LOW_BATTERY, round(t, 3), "Battery below " + (int) LOW_BATTERY_PERCENT + " % (" + round(battery, 2) + " %)"));
        }
    }

    private void fail(String reason, double at, List<Event> events) {
        t = at; state = Flight.ABORTED; finished = true; outcome = Outcome.FAILED; result = reason;
    }

    /** Stage 1: recovery team dispatched. Stage 2: payload recovered, run complete. */
    private void stageRecovery(List<Sample> samples, List<Event> events) {
        double drain = DRAIN_FLIGHT_PCT_PER_S * DRAIN_LANDED_FACTOR;
        v = 0; // at rest on the ground after the touchdown reading
        if (recoveryStage == 0) {
            double at = landingTime + RECOVERY_DISPATCH_S;
            battery = Math.max(0, battery - drain * (at - t));
            t = at;
            recoveryStartT = at;
            recoveryStage = 1;
            samples.add(sample(Flight.RECOVERY));
            events.add(new Event(EventKind.RECOVERY_STARTED, round(at, 3), "Recovery team dispatched"));
            return;
        }
        double doneAt = recoveryStartT + RECOVERY_HANDLING_S + landingDrift / RECOVERY_WALK_MPS;
        battery = Math.max(0, battery - drain * (doneAt - t));
        t = doneAt;
        recoveryEndT = doneAt;
        recoveryStage = 2;
        samples.add(sample(Flight.RECOVERY));
        events.add(new Event(EventKind.RECOVERY_COMPLETED, round(doneAt, 3), "Payload recovered " + round(landingDrift, 1) + " m from release point"));
        finished = true; outcome = Outcome.COMPLETED;
        result = "Landed at " + round(impactSpeed, 2) + " m/s after " + round(landingTime, 1) + " s, drift "
                + round(landingDrift, 1) + " m; recovery completed";
    }
}
