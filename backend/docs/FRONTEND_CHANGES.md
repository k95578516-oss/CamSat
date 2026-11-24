# Intentional frontend / contract changes

The backend preserves every existing request/response shape. The changes below are the only deviations, with the reason for each.
Verified here with `tsc --noEmit` and `eslint` (both clean). The Vitest suite could **not** be run in the supplied sandbox (see VERIFICATION.md).

| Change | Where | Why |
|---|---|---|
| `POST /api/runs/{id}/start` now returns **202** with the run in `RUNNING` instead of blocking until the end | backend; `useRun`/`useRuns` poll every 1.5 s while a run is `RUNNING`/`PAUSED` (`src/hooks/queries.ts`) | removes the long blocking request, enables progress, cancellation and pause (FRONTEND_BACKEND_GAPS: blocking start, no progress, cancel races) |
| `SimulationRunResponse` gains optional `elapsedSimTimeSeconds`, `progressPercent` | `src/types/api.ts` | progress display. Optional in TS, so existing fixtures/tests are unaffected |
| New `POST /api/runs/{id}/pause` and `/resume`; Pause/Resume buttons enabled by run status | `simulationApi.ts`, `queries.ts` (`usePauseRun`, `useResumeRun`), `SimulationPage.tsx` | the status and events already existed; the backend can now honour them reliably |
| Cancel is allowed for `PAUSED` runs and no longer disabled for the whole life of a start request | `SimulationPage.tsx` | cancellation is now cooperative, so the old race no longer exists |
| Progress bar and "simulated time / %" line for running runs | `SimulationPage.tsx` | uses the new optional fields |
| Mission form limits tightened to the backend's validated physical ranges (mass 0.05-50, altitude 20-10000, canopy 0.1-3, wind <= 50, battery >= 1) | `MissionForm.tsx` | otherwise users could submit values the server rejects with `400`; the form now reports it immediately |
| Tests updated/added | `SimulationPage.test.tsx`, `MissionForm.test.tsx` | the old test asserted "pause/resume disabled because no endpoint exists" |

Additive backend behaviour not used by the frontend (documented, no UI work required): `GET /api/auth/me`, `/actuator/health`, `/actuator/info`,
telemetry `fromSeconds`/`toSeconds`/`limit` parameters (default behaviour unchanged, capped at 20 000 rows).

## Remaining items from FRONTEND_BACKEND_GAPS.md
- **Resolved:** per-user ownership and isolation; blocking start; progress info; cancellation races; pause/resume; unbounded telemetry (cap + window params);
  health/version endpoints; configurable CORS; recovery phases now have distinct simulated times; wind drift is modelled (reported as `landingDriftM`; telemetry still has no position columns).
- **Not done (needs a product decision or is out of scope):** refresh tokens / HttpOnly cookie / logout revocation; mission pagination and sort params; event severity;
  CSV/PDF export endpoints; live streaming (polling is used); structured failure reason (still the free-text `finalResult`); latitude/longitude (no launch coordinates);
  extra sensors (pressure, acceleration). The frontend `FRONTEND_BACKEND_GAPS.md` has been annotated accordingly.
