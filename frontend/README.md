# CamSat ground-station frontend

React + TypeScript + Vite single-page app for the CamSat Spring Boot backend: missions, simulation runs,
telemetry, events, recovery, reports and an altitude-based 3D replay.

> **Status of this code:** the source was written without network access, so `npm install`, `tsc`, `eslint`,
> `vitest` and `vite build` have **not been run**. Expect to fix a few type or lint errors on the first run
> (see "First run checklist"). Nothing here has been verified in a browser.

## Prerequisites
- Node.js 20.19+ or 22.12+ (required by Vite 7) and npm 10+
- The CamSat backend running (default `http://localhost:8080`) with MySQL available

## Install (Windows PowerShell)
```powershell
cd path\to\CamSatProject\frontend
npm install
Copy-Item .env.example .env
```

## Configure
Edit `.env` (never commit it):

| Variable | Purpose | Default |
|---|---|---|
| `VITE_API_BASE_URL` | Backend base URL, no trailing slash. **Empty** = same-origin requests through the Vite proxy / a reverse proxy | `http://localhost:8080` |
| `DEV_PROXY_TARGET` | Dev server only: where `/api` is proxied when `VITE_API_BASE_URL` is empty | `http://localhost:8080` |
| `VITE_API_TIMEOUT_MS` | Timeout for normal calls | `15000` |
| `VITE_SIMULATION_START_TIMEOUT_MS` | Timeout for the start call (the new backend answers immediately with 202; kept for older backends) | `180000` |

`VITE_*` values are public in the built JavaScript. Never put secrets in them.

## Run locally alongside the backend
Terminal 1 (backend, from the repository root):
```powershell
$env:JWT_SECRET = "<your dev secret, at least 32 bytes>"   # use whatever variables your backend reads
.\mvnw.cmd spring-boot:run
```
Terminal 2 (frontend):
```powershell
cd frontend
npm run dev
```
Open http://localhost:5173.

**CORS:** the backend currently allows only fixed origins in its `SecurityConfig` (local development origins).
If the browser reports a CORS error, either run on an allowed origin/port, or avoid CORS entirely by leaving
`VITE_API_BASE_URL=` empty so the browser calls `http://localhost:5173/api/...` and Vite proxies to the backend.
Do not disable CORS or security in the backend to make the UI work.

## Scripts
```powershell
npm run dev         # dev server with hot reload
npm run typecheck   # tsc --noEmit
npm run lint        # eslint
npm test            # vitest run (jsdom)
npm run build       # typecheck + production build into dist\
npm run preview     # serve dist\ locally
```

## First run checklist
```powershell
npm install
npm run typecheck   # fix any reported type errors
npm run lint
npm test
npm run build
```
Versions in `package.json` are caret ranges chosen for mutual compatibility (React 19, Vite 7, Tailwind 4,
R3F 9 / drei 10, Recharts 3, Zod 3.25 with @hookform/resolvers 5) but were **not resolved or locked** here;
commit the generated `package-lock.json` after the first successful install.

## Routes
`/login`, `/register` · `/` overview · `/missions`, `/missions/new`, `/missions/:id`, `/missions/:id/edit` ·
`/simulation` · `/telemetry` · `/visualization` · `/events` · `/recovery` · `/reports` · `/settings` ·
`/forbidden` · anything else → 404. Mission/run selection is kept in the URL (`?mission=1&run=5`).

## Backend endpoints used
`POST /api/auth/login|register` · `GET/POST /api/missions`, `GET/PUT/DELETE /api/missions/{id}` ·
`POST/GET /api/missions/{id}/runs`, `GET /api/runs/{id}`, `GET /api/runs/recent`,
`POST /api/runs/{id}/start|cancel` · `GET /api/runs/{id}/telemetry`, `/telemetry/latest-reading` ·
`GET /api/runs/{id}/events`, `/events/latest`, `/events/count` · `GET /api/runs/{id}/recovery` ·
`GET /api/runs/{id}/report` · `GET /api/dashboard`, `/api/analytics`.
Known limitations are listed in [FRONTEND_BACKEND_GAPS.md](FRONTEND_BACKEND_GAPS.md).

## Authentication behaviour
The backend returns the JWT in the response body. The app stores it in `sessionStorage` (cleared when the tab
closes), sends it as `Authorization: Bearer`, signs out when it expires, and signs out on any 401 to an
authenticated request. There is no refresh flow. Route protection in the UI is a convenience only; the backend
decides what each token may access. The token is never logged.

## Build and deploy
```powershell
npm run build
```
`dist\` is static. Serve it from any static host (nginx, IIS, S3 + CDN, Netlify...) and:
1. Set `VITE_API_BASE_URL` **at build time** to the deployed backend URL (or leave it empty and reverse-proxy `/api` to the backend on the same origin; this avoids CORS).
2. Add the deployed frontend origin to the backend's allowed CORS origins.
3. Make the host fall back to `index.html` for unknown paths (single-page-app routing).

nginx example:
```nginx
location / { try_files $uri /index.html; }
location /api/ { proxy_pass http://backend:8080; }
```

## Common problems
| Symptom | Likely cause |
|---|---|
| "Cannot reach the CamSat backend" | Backend not running, wrong `VITE_API_BASE_URL`, or CORS blocked the call (check the browser console) |
| Browser console shows a CORS error | Frontend origin is not allowed by the backend; use the dev proxy (empty `VITE_API_BASE_URL`) |
| Redirected to sign-in with "session has ended" | Token expired or backend rejected it (401) |
| Start simulation times out | Raise `VITE_SIMULATION_START_TIMEOUT_MS`; the run may still finish on the server, the page re-reads its state |
| Blank 3D view | WebGL unavailable; use the Telemetry page |
| Edit/Delete mission disabled | The mission already has runs (backend rule) |

## Project layout
```
src/api/          axios client, error normalisation, one module per backend area
src/types/api.ts  DTO types (single place to update after backend changes)
src/hooks/        TanStack Query hooks, URL run selection, playback, settings
src/features/auth AuthProvider (session, expiry, 401 handling)
src/components/   ui primitives, layout, charts, 3D scene, forms
src/pages/        route pages (lazy loaded)
src/utils/        formatting, labels, CSV, telemetry helpers
```
