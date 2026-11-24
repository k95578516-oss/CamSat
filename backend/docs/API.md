# API reference

All endpoints are under `/api` and return JSON. Timestamps are ISO-8601 UTC strings (`2026-10-11T09:30:00.123456Z`).
Nullable fields are always present as explicit `null`. Except `register`, `login`, `GET /actuator/health` and `GET /actuator/info`,
every endpoint requires `Authorization: Bearer <jwt>`.

**Ownership:** every mission/run/telemetry/event/recovery/report/dashboard/analytics query is scoped to the authenticated user
(identity comes from the verified JWT only). A resource that belongs to someone else is reported as `404`, exactly like a missing one.

## Errors (`ApiErrorBody`)
```json
{ "timestamp": "…", "status": 400, "error": "Bad Request", "message": "Validation failed. Check the highlighted fields.",
  "fieldErrors": { "windSpeedMps": "Wind speed cannot be negative" } }
```
`fieldErrors` is `null` unless validation failed. `400` validation/malformed body/bad path id · `401` missing, invalid or expired token,
bad credentials · `403` authenticated but not allowed · `404` missing or not yours · `409` state conflict (duplicate email,
mission has runs, invalid run transition) · `500` unexpected (generic message only) · `503` simulator at capacity.

## Authentication
| Method & path | Body | Success | Notes |
|---|---|---|---|
| `POST /api/auth/register` | `{name,email,password}` | `201 AuthResponse` | email is lower-cased; password 8-72 chars, BCrypt-hashed; duplicate -> `409` |
| `POST /api/auth/login` | `{email,password}` | `200 AuthResponse` | wrong password and unknown email give the same `401` message |
| `GET /api/auth/me` | | `200 {id,name,email,role,createdAt}` | additive; not used by the current frontend |

`AuthResponse`: `token, tokenType("Bearer"), expiresInSeconds, userId, name, email, role, createdAt`.

## Missions
| Method & path | Success | Notes |
|---|---|---|
| `GET /api/missions?search=` | `200 MissionResponse[]` | case-insensitive name search; newest first |
| `POST /api/missions` | `201 MissionResponse` | validated, see limits below |
| `GET /api/missions/{id}` | `200` | |
| `PUT /api/missions/{id}` | `200` | `409` once the mission has any run |
| `DELETE /api/missions/{id}` | `204` | `409` once the mission has any run |

Limits (justification in SIMULATION_MODEL.md): `name` 1-100 chars · `payloadMassKg` 0.05-50 · `releaseAltitudeM` 20-10000 ·
`parachuteDiameterM` 0.1-3 · `dragCoefficient` 0.1-2 · `windSpeedMps` 0-50 · `windDirectionDeg` [0,360) · `initialBatteryPercent` 1-100.

## Runs
| Method & path | Success | Notes |
|---|---|---|
| `POST /api/missions/{missionId}/runs` | `201 SimulationRunResponse` (`CREATED`) | |
| `GET /api/missions/{missionId}/runs` | `200 []` | newest first |
| `GET /api/runs/{id}` | `200` | poll this while `RUNNING`/`PAUSED` |
| `GET /api/runs/recent` | `200 []` | 10 most recent runs of the caller |
| `POST /api/runs/{id}/start` | **`202`** run in `RUNNING` | asynchronous; `409` unless `CREATED`; `503` if the worker pool is full |
| `POST /api/runs/{id}/pause` | `200` (`PAUSED`) | `409` unless `RUNNING` |
| `POST /api/runs/{id}/resume` | `200` (`RUNNING`) | `409` unless `PAUSED` |
| `POST /api/runs/{id}/cancel` | `200` (`CANCELLED`) | from `CREATED`/`RUNNING`/`PAUSED`; `409` if already terminal |

`SimulationRunResponse`: `id, missionId, status, startedAt, endedAt, landingTimeSeconds, landingDriftM, finalResult` plus the
additive `elapsedSimTimeSeconds, progressPercent`.

State machine: `CREATED -> RUNNING <-> PAUSED -> COMPLETED | FAILED`; `CREATED|RUNNING|PAUSED -> CANCELLED`. Every transition is a conditional
`UPDATE ... WHERE status = :expected`, so of N concurrent requests exactly one wins and the others get `409`.

## Telemetry, events, recovery, report
| Path | Success | Notes |
|---|---|---|
| `GET /api/runs/{id}/telemetry?fromSeconds=&toSeconds=&limit=` | `200 TelemetryReadingResponse[]` | oldest first; no params = whole series, capped at 20 000 rows; `limit<1` or `from>to` -> `400` |
| `GET /api/runs/{id}/telemetry/latest-reading` | `200` | `404` while no telemetry exists |
| `GET /api/runs/{id}/events` | `200 FlightEventResponse[]` | ordered by `elapsedTimeSeconds`, then id |
| `GET /api/runs/{id}/events/latest` | `200 []` | the 5 newest, newest first |
| `GET /api/runs/{id}/events/count` | `200 <number>` | |
| `GET /api/runs/{id}/recovery` | `200 RecoveryResponse` | derived from persisted run state and events |
| `GET /api/runs/{id}/report` | `200 MissionReportResponse` | aggregates computed in the database |

`recoveryStatus`: `PENDING` (not landed) · `NOT_STARTED` (landed, or cancelled) · `IN_PROGRESS` · `COMPLETED` · `FAILED`.
`latitude`/`longitude` are always `null`: missions carry no launch coordinates, so no position is fabricated.

## Dashboard
`GET /api/dashboard` -> `DashboardResponse`; `GET /api/analytics` -> `AnalyticsResponse`.
Definitions: *active* = RUNNING + PAUSED · `successRatePercent` = COMPLETED / (COMPLETED + FAILED) x 100 (0 when none finished;
cancelled/unfinished excluded) · averages use COMPLETED runs only and are `null` when there are none ·
`averageMaximumDescentSpeedMps` = mean of each completed run's own peak descent speed · `runsByStatus` lists only statuses that occur.

## Operations
`GET /actuator/health` (public, minimal `{"status":"UP"}`) · `GET /actuator/info` (build name/version).
