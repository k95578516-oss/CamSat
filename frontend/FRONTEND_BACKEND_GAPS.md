# Frontend ↔ backend gaps

> **Update — new backend in `../backend`.** It resolves: per-user ownership and isolation (every endpoint, incl. dashboard/analytics and
> `/api/runs/recent`); `GET /api/auth/me`; asynchronous start (`202`, poll `GET /api/runs/{id}`); progress fields
> (`progressPercent`, `elapsedSimTimeSeconds`); cooperative cancellation; pause/resume endpoints (the buttons are now enabled by run status);
> telemetry cap plus `fromSeconds`/`toSeconds`/`limit`; `/actuator/health` and `/actuator/info`; configurable CORS (`CORS_ALLOWED_ORIGINS`);
> distinct simulated times for landing, recovery start and recovery completion.
> **Still open:** refresh token/logout, mission pagination, event severity, CSV/PDF export, live streaming, structured failure reasons,
> latitude/longitude (missions have no launch coordinates), extra sensors. The tables below describe the *previous* backend and are kept for history;
> see `../backend/docs/FRONTEND_CHANGES.md` for the current state.

Verified against the backend source in this repository (controllers, DTOs, services, `SecurityConfig`).
Each item says what the frontend does today and what backend change would improve it.
The frontend never invents data to cover a gap.

Legend: **UI today** = how the frontend currently behaves · **Needed** = backend change to remove the limitation.

## Authentication
| Gap | UI today | Needed |
|---|---|---|
| JWT is returned in the response body; no cookie, no refresh token, no logout endpoint | Token kept in `sessionStorage`, session ends at `expiresInSeconds` or on any 401 for an authenticated request | Optional: HttpOnly cookie or refresh-token flow; `POST /api/auth/logout` if revocation is wanted |
| No "current user" endpoint | Account page shows the data from the sign-in response | `GET /api/auth/me` |
| Role exists in the sign-in response but no role-protected endpoints are used by the UI | UI shows the role only; nothing is role-gated | Define admin endpoints, then add role-aware UI |

## Authorization and ownership
| Gap | UI today | Needed |
|---|---|---|
| `Mission` has no owner; mission, run, telemetry, event, recovery and report endpoints are not scoped to the user; `/api/runs/recent` is global | UI shows whatever the API returns. **Do not treat the UI as isolating users.** | Backend ownership model (see backend audit task). The frontend needs no change if the endpoints keep their shapes |
| Dashboard and analytics scoping was not verified | Shown as returned | Scope per user |

## Missions
| Gap | UI today | Needed |
|---|---|---|
| `GET /api/missions` has no pagination or sort parameters (only `?search=`) | Search is server-side; sorting and 10-per-page pagination are client-side | `page`, `size`, `sort` params and a page envelope |
| Mission with runs cannot be updated or deleted (409) | Edit/Delete are disabled with the reason; the edit page explains it | Decide whether to allow edits of unused fields, or version missions |
| No mission status | Detail page shows run history instead | Optional `status` field |

## Simulation control
| Gap | UI today | Needed |
|---|---|---|
| `POST /api/runs/{id}/start` blocks until the simulation completes (single long transaction) | Long request timeout (`VITE_SIMULATION_START_TIMEOUT_MS`, default 180 s), indeterminate progress bar, duplicate-start guard, state re-read after timeout/network errors | Return `202 Accepted` and run asynchronously; then poll `GET /api/runs/{id}` (to be added to `useRun` with `refetchInterval`) |
| No progress information | No percentage is shown | `progressPercent` / `elapsedSimTimeSeconds` on the run |
| `cancel` only changes the stored status; it cannot stop a running loop and races with the blocking start | Cancel is enabled for CREATED/RUNNING runs but disabled while this tab's own start request is in flight | Cooperative cancellation in the executor |
| No pause/resume endpoints (the `PAUSED` status and events exist) | Pause and Resume buttons are shown **disabled** with an explanation | `POST /api/runs/{id}/pause` and `/resume` |
| Start accepts no parameters (everything comes from the mission) | Page shows the mission parameters read-only | Optional per-run overrides (time step, seed) |

## Telemetry
| Gap | UI today | Needed |
|---|---|---|
| `GET /api/runs/{id}/telemetry` returns the full series (up to ~20,000 rows), no pagination or time window | Client-side time filter, chart down-sampling (default 1,000 points), paginated table (50 rows) | `page/size` and `fromSeconds/toSeconds` parameters |
| Only altitude, vertical velocity, temperature and battery exist | Charts exist only for sensors that have data; page states that acceleration and pressure are unavailable | Add sensors to the DTO if wanted |
| `latitude`/`longitude` are present in the DTO but never populated by the simulator; there is no horizontal position or wind drift in telemetry | 3D view is altitude-only and labelled illustrative; table shows position columns only if any sample has them | Simulator must compute horizontal position |
| No streaming/live endpoint | All data is labelled "recorded"; there is a Refresh button | SSE/WebSocket or polling-friendly partial telemetry for running runs |
| No CSV export endpoint | CSV is built in the browser from the loaded rows (formula-injection safe) | `GET /api/runs/{id}/telemetry.csv` for large runs |

## Events
| Gap | UI today | Needed |
|---|---|---|
| Events have no severity | Badge colour is derived from the event **type** in `src/utils/labels.ts` and the page says so | `severity` field |
| No event filtering parameters | Type filter is client-side | `type` filter param |

## Recovery and landing
| Gap | UI today | Needed |
|---|---|---|
| Landing, recovery start and recovery completion are recorded at the same simulated instant; `recoveryStatus` is a free-form string | The three flags are shown exactly as returned; unknown status strings are shown verbatim | Separate recovery phase with duration, and an enum for `recoveryStatus` |

## Reports and analytics
| Gap | UI today | Needed |
|---|---|---|
| No generated report document or export endpoint | Report page is assembled in the browser from `GET /api/runs/{id}/report` and labelled so; CSV and browser print are client-side | PDF/CSV report endpoint |
| No mission success criteria; "result" is a free-form string | Shown as returned | Structured outcome and failure reason |
| Report has no flight duration besides `landingTimeSeconds`; no maximum velocity other than max descent speed | Those two fields are used and labelled as such | Add explicit fields if needed |
| Historical comparison has no endpoint | Bar charts of completed runs of one mission, built from `GET /api/missions/{id}/runs` | Comparison endpoint |

## Operations
| Gap | UI today | Needed |
|---|---|---|
| No health or version endpoint | Connectivity check treats any HTTP answer from `/api/dashboard` as "reachable"; backend version is shown as unknown | Actuator `/actuator/health` (public, minimal) and an info/version endpoint |
| CORS origins are fixed in the backend `SecurityConfig` | Use the Vite dev proxy (see README) or add your frontend origin to the backend | Make allowed origins configurable (`CORS_ALLOWED_ORIGINS`) |

## When the backend is remediated
Only these places should need edits: `src/types/api.ts` (shapes), `src/api/*.ts` (paths), `src/hooks/queries.ts`
(polling/`refetchInterval` for async runs; pagination), and `SimulationPage.tsx` (remove blocking-start handling and
enable pause/resume). Page components read data only through the hooks.
