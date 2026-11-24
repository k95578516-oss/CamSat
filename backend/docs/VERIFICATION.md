# Verification report

Honest statement of what was executed while building this, in the environment that was available (Linux sandbox, JDK 21,
Node 22, **no Maven, no Docker, no network, no MySQL**, and a `node_modules` folder installed on Windows).

## Executed and passed
| Check | Result |
|---|---|
| Flight simulator (`FlightSimulator.java`) compiled with `javac` and exercised by a standalone harness mirroring `FlightSimulatorTest` | **22/22 checks passed**: impact speed equals the analytic terminal velocity (4.337 m/s), landing-time and drift bounds, ordering, single-fire events, determinism, undersized-parachute failure, low-battery event once, battery-exhaustion abort, termination on extreme inputs, rejection of NaN/negative inputs |
| Extra simulator scenarios used by the integration tests (300 m, 3000 m, 10 000 m, 1 % battery, stiff 0.05 kg / 3 m canopy) | Behaviour measured and the test assertions were set from the measured values (e.g. 1 % battery at 3000 m aborts at 722 m) |
| Bug found and fixed by that harness | fixed-step RK4 diverged for light payloads under large canopies (reported as a bogus "destructive impact"); replaced by stiffness-adaptive stepping with a step budget |
| `scripts/check_contract.py` — every interface in `src/types/api.ts` vs the Java records | **15 interfaces, 0 drift** |
| Syntax of all 71 Java files (main + test) via `javac` parse phase; every unresolved symbol is a framework class (Spring, JPA, jjwt, AssertJ, MockMvc, Flyway) and none refers to project code | no syntax errors; no project-internal symbol errors |
| `npm run typecheck` (frontend) | passes |
| `npm run lint` (frontend) | passes |

## NOT executed (could not be, in this environment) — run these locally
| Check | Why not run | Command |
|---|---|---|
| **Compilation of the Spring layer and the whole Maven build** | Maven and the dependency jars are not available offline | `mvn clean package` |
| **All Spring integration tests** (`*ApiTest`, `SimulationLifecycleTest`, `SimulationControlTest`, `ContractTest`, `SchemaAndConstraintsTest`, `RunRecoveryTest`, `RestartPersistenceTest`, `JwtServiceTest`) | need the Spring/Hibernate/H2 jars | `mvn test` |
| `MySqlMigrationTest` (Flyway on real MySQL 8, FK and unique constraints) | needs Docker (auto-skipped without it) | `mvn test` with Docker Desktop running |
| Flyway migration `V1` on MySQL 8 | no MySQL available (written in portable DDL; also exercised on H2 by the tests above once run) | start the app against the database from `scripts/db-setup.sql` |
| `npm test` (Vitest) | the supplied `node_modules` contains only Windows native binaries (`@rollup/rollup-win32-*`, `@esbuild/win32-x64`) and there is no network to reinstall | `cd frontend; npm test` |
| `npm run build` | same reason | `cd frontend; npm run build` |
| End-to-end browser <-> backend run | none of the above could be started | `.\scripts\run-dev.ps1` + `npm run dev` |

**Treat the Spring layer as written but not yet compiled or run.** Expect that the first `mvn test` may surface small compile or
wiring errors I could not see; the most likely places are Hibernate constructor-expression queries (`SimulationRunRepository.recent`,
`TelemetryReadingRepository.stats`), HQL enum literals, and test helpers. Paste the first failure back and it can be fixed quickly.

## Frontend tests I changed without being able to run them
`SimulationPage.test.tsx` (the old "pause/resume disabled" test replaced by three new ones; `pause`/`resume` added to the mocked API) and
`MissionForm.test.tsx` (extra rejection cases). They typecheck and lint, but have not been executed.

## Known limitations
- **Single instance only**: pause/stop flags and the worker pool are in memory (see README).
- No login rate limiting / account lockout; no refresh tokens or logout revocation (tokens simply expire).
- Role `ADMIN` exists in the token/model but no admin endpoints exist, so there is no cross-user access at all.
- `latitude`/`longitude` are always `null` (no launch coordinates in a mission).
- No pagination for missions; telemetry has window/limit parameters, default cap 20 000 rows.
- Hibernate schema validation is disabled (known false mismatches on Hibernate 6); Flyway is the schema authority.
- Mission edit/delete is blocked once *any* run exists (the existing frontend rule); there is no mission versioning.
- The Maven wrapper (`mvnw`) was not generated (needs Maven); run `mvn -N wrapper:wrapper` once if wanted.
- Physics are a documented simplified model (see SIMULATION_MODEL.md), not a certified flight simulator.
