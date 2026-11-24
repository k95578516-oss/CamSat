# CamSat backend

Spring Boot 3 / Java 21 / MySQL 8 backend for the CamSat frontend (`../frontend`). It implements exactly the API the
frontend's `src/types/api.ts` and `src/api/*` expect, with JWT authentication, per-user data isolation and a deterministic
CanSat descent simulation that runs asynchronously.

- API reference: [docs/API.md](docs/API.md)
- Physics, assumptions and validation limits: [docs/SIMULATION_MODEL.md](docs/SIMULATION_MODEL.md)
- Every intentional frontend/contract change: [docs/FRONTEND_CHANGES.md](docs/FRONTEND_CHANGES.md)
- What has and has not been verified: [docs/VERIFICATION.md](docs/VERIFICATION.md)

## Prerequisites (Windows)

| Tool | Version | Check |
|---|---|---|
| JDK | 21 | `java -version` |
| Maven | 3.9+ | `mvn -v` |
| MySQL | 8.0 | `mysql --version` |
| Node.js (frontend) | 20+ | `node -v` |
| Docker Desktop | optional, only for the MySQL Testcontainers test | `docker version` |

## 1. Create the database (once)

Edit the password in `scripts\db-setup.sql`, then in PowerShell:

```powershell
mysql -u root -p < scripts\db-setup.sql
```

Flyway creates every table automatically on first start (`src/main/resources/db/migration`). Hibernate never changes the schema (`ddl-auto: none`).

For an existing non-empty local `cam_sat` database created before Flyway was enabled, the dev profile baselines it at version 1. **Only use this with a schema that already matches `V1__initial_schema.sql`;** if its tables are incomplete or different, back it up and reconcile the schema before starting the app. Fresh empty databases still run V1 normally. Production does not enable automatic baselining.

## 2. Configure

```powershell
Copy-Item .env.example .env
notepad .env      # set DB_PASSWORD (must match step 1); JWT_SECRET is optional in dev
```

Never commit `.env`. Variables:

| Variable | Dev default | Prod | Meaning |
|---|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `dev` | `prod` | `prod` has no defaults and fails fast when something is missing |
| `DB_URL` | local MySQL `cam_sat` | required | JDBC URL |
| `DB_USERNAME` / `DB_PASSWORD` | `camsat` / `camsat_dev_password` | required | database credentials |
| `JWT_SECRET` | random per process (tokens die on restart) | **required, >= 32 chars** | HS256 signing secret |
| `JWT_EXPIRATION_SECONDS` | `3600` | optional | token lifetime, returned as `expiresInSeconds` |
| `SERVER_PORT` | `8080` | optional | HTTP port |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173,http://127.0.0.1:5173` | required if browsers call the API cross-origin | exact origins, comma separated; `*` is rejected |
| `SIM_SAMPLE_DELAY_MS` | `25` | optional | wall-clock delay per simulated second; `0` = as fast as possible |

## 3. Run the backend

```powershell
.\scripts\run-dev.ps1          # loads .env, then: mvn spring-boot:run
# or manually:
mvn spring-boot:run
```

Check it: `Invoke-RestMethod http://localhost:8080/actuator/health` returns `status: UP`; `/actuator/info` returns the build version.

Optional Maven wrapper (so others do not need Maven installed): `mvn -N wrapper:wrapper -Dmaven=3.9.9`, then use `.\mvnw.cmd`.

## 4. Run the frontend against it

Two supported ways (see `../frontend/README.md`):

```powershell
cd ..\frontend
npm install
# A) direct (needs CORS): .env has VITE_API_BASE_URL=http://localhost:8080  (already the default in .env.example)
# B) Vite proxy, no CORS needed: set VITE_API_BASE_URL= (empty); the dev server proxies /api to DEV_PROXY_TARGET
npm run dev                    # http://localhost:5173
```

## 5. Test and build

```powershell
mvn test                       # unit + integration tests (H2 in MySQL mode; no Docker needed)
mvn verify                     # same, plus packaging. MySqlMigrationTest runs in both if Docker is available, else it is skipped
mvn clean package              # target\camsat-backend-0.1.0.jar
java -jar target\camsat-backend-0.1.0.jar    # with SPRING_PROFILES_ACTIVE=prod and all variables set

python scripts\check_contract.py ..\frontend\src\types\api.ts     # TS interface vs Java record drift check
cd ..\frontend; npm run typecheck; npm run lint; npm test; npm run build
```

## Operational notes

- **Run exactly one backend instance.** Pause/cancel flags and the worker pool live in memory. Run state transitions are
  conditional database updates, so they are safe, but a second instance could not signal the first one's workers.
  On startup, runs left `RUNNING`/`PAUSED` by a crash or restart are closed as `FAILED` ("Interrupted by a backend restart").
- Put the API behind HTTPS (reverse proxy) in production; the JWT travels in the `Authorization` header.
- Failed-login rate limiting is not implemented (see docs/VERIFICATION.md, limitations). Add it at the reverse proxy.
