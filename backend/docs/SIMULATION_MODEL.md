# Simulation model and validation limits

Implemented in `simulation/FlightSimulator.java` (pure Java, no I/O, no clock). Deterministic: identical mission parameters always give
byte-identical telemetry and events (there is no randomness, so no seed is needed).

## Physics
- **Release:** at `releaseAltitudeM` with zero vertical velocity (apogee release); horizontal velocity starts equal to the wind.
- **Vertical dynamics:** `dv/dt = -g - 0.5 * rho * (Cd*A) * |v| * v / m`, `g = 9.80665 m/s^2`, integrated with RK4.
- **Atmosphere:** ISA troposphere, `T = 15 C - 0.0065 C/m * h`, `rho = 1.225 * (1 - 2.25577e-5 h)^4.25588`.
- **Body drag before the canopy:** `Cd = 1.0`, `A = 0.0034 m^2` (a 66 mm can).
- **Parachute:** deploys 2.0 s after release, inflates linearly over 1.0 s; canopy area `pi D^2 / 4`, drag coefficient = mission `dragCoefficient`.
- **Wind:** constant speed, `windDirectionDeg` is the direction the wind blows *from*. Horizontal velocity relaxes toward the wind velocity
  through the same drag force. Drift = horizontal distance from the release point at touchdown (`landingDriftM`).
- **Battery:** 0.0021 %/s in flight (about 150 mA from a 2000 mAh pack), 20 % of that after touchdown. `LOW_BATTERY` fires once below 20 %.
  Battery reaching 0 before touchdown aborts the run (`FAILED`, final state `ABORTED`).
- **Touchdown:** time and speed are linearly interpolated inside the step. Impact above 15 m/s is a destructive impact (`FAILED`).
- **Recovery (simulated phase after touchdown):** team dispatched 10 s after landing (`RECOVERY_STARTED`); completes after
  20 s handling + drift / 1.4 m/s walking (`RECOVERY_COMPLETED`), then `SIMULATION_COMPLETED`.
- **Position:** none. Latitude/longitude are `null` because the mission has no launch coordinates.
- Not modelled: ascent (the model starts at release), wind shear or gusts, horizontal canopy dynamics, sensor noise, canopy swing/oscillation.

## Numerical safety
- Fixed maximum step 0.05 s; the step shrinks automatically so `dt * drag stiffness < 1` (needed: a 0.05 kg payload under a 3 m canopy has a
  drag rate of about 150 /s and diverges with a fixed 0.05 s RK4 step; this was found and fixed during verification).
- Step clipped exactly to the parachute deployment instant; minimum step 1e-4 s.
- Hard stops: 6 000 000 integration steps, 7 200 s simulated time, any non-finite state, any speed above 1 000 m/s -> `FAILED` with a reason.
- Parameters are re-checked for finiteness and positivity before a run is accepted.

## Concurrency and lifecycle
- `start` persists `RUNNING` (+ `SIMULATION_STARTED`) with one conditional UPDATE and returns `202`; a bounded thread pool (2 core, 4 max, queue 20) simulates in the background.
  A full pool reverts the run to `CREATED` and returns `503`.
- No transaction spans a simulation. Each 1 s sample batch (telemetry + events + progress) is one short transaction that first takes a
  row lock on the run **only if it is still `RUNNING`**. A concurrent cancel/pause/finish therefore commits strictly before or after a batch;
  telemetry is never written after a cancel committed.
- Events are idempotent through `UNIQUE(run_id, dedupe_key)`; pause/resume use `TYPE:n` keys.

## Validation limits and why
| Field | Range | Justification |
|---|---|---|
| `payloadMassKg` | 0.05 - 50 | CanSat class payloads are 0.3-0.5 kg; the range also covers small drop probes. Below 0.05 kg, drag stiffness makes the model both numerically expensive and unrealistic. |
| `releaseAltitudeM` | 20 - 10 000 | Below 20 m the parachute (2 s delay) cannot open before ground contact; above 10 km the troposphere ISA model stops being a sound approximation (11 km tropopause). |
| `parachuteDiameterM` | 0.1 - 3 | Practical canopy sizes for such payloads; larger canopies on light payloads give > 1 h descents. |
| `dragCoefficient` | 0.1 - 2.0 | Typical canopy `Cd` is 0.5-1.5; this matches the limits the frontend form already enforced. |
| `windSpeedMps` | 0 - 50 | Above ~50 m/s (storm force) a CanSat launch would not be attempted. |
| `windDirectionDeg` | [0, 360) | Compass bearing. |
| `initialBatteryPercent` | 1 - 100 | 0 % could not power the avionics at all. |
| `name` | 1 - 100 chars | matches the frontend form; database column is `VARCHAR(100)`. |
| Impact limit 15 m/s, low-battery 20 %, deploy delay 2 s, avionics drain | model constants | engineering assumptions, defined in one place (`FlightSimulator` constants) and listed above. They are not mission inputs. |
