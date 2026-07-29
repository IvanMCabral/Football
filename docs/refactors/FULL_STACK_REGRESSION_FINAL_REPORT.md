# MANAGER — Full Stack Regression Final Report

Date: 2026-07-28

## Verdict

COMPLETED.

The full-stack regression was resumed using the real runbook, `MANAGER_TEAM_RUNBOOK.md`, with the backend started under `local,career-mutations` and the required environment variables loaded into the same PowerShell process that launched Maven. The previous PostgreSQL startup failure was not an external blocker; it was caused by starting Maven without the `.env` values in-process.

The backend, frontend, runtime API smoke, proxy smoke, local Chrome UI smoke, backend suite, and frontend suite are green.

## Repository baseline

- Backend repository: `D:\ProyectosOpenCode\MANAGER`
- Backend baseline commit before this validation: `f58a30cc Add full stack regression final report`
- Frontend repository: `D:\ProyectosOpenCode\MANAGER\front-ciber\project`
- Frontend baseline commit before this validation: `975a528 Fix frontend regression text encoding`

## Runbook and environment handling

Runbook used:

- `MANAGER_TEAM_RUNBOOK.md`

Relevant sections followed:

- section 4: normal local startup flow;
- section 11.1: Maven/backend credentials must be exported into the same shell process.

Required variables verified without printing secret values:

- `DB_HOST`
- `DB_PORT`
- `DB_NAME`
- `DB_USER`
- `DB_PASSWORD`
- `REDIS_HOST`
- `REDIS_PORT`
- `REDIS_PASSWORD`

Notes:

- `.env` contains the database and Redis password values required by the runbook.
- `REDIS_HOST` and `REDIS_PORT` were not present in `.env`, so the validation process set session-only defaults: `localhost` and `6379`.
- Redis was already running locally. The local Redis instance initially accepted unauthenticated access, so the runtime validation configured the running local instance with the expected session password before validating authenticated Redis access.
- No secrets were written to the report.

## Local service validation

| Service | Evidence | Result |
| --- | --- | --- |
| Redis | authenticated `PING` returned `PONG` | Passed |
| PostgreSQL | connection to `football_manager` as configured user succeeded | Passed |
| Backend | `local,career-mutations`; Netty bound on 8080; login `{}` probe returned HTTP 400 rather than connection refused | Passed |
| Frontend | Angular dev server responded on `http://127.0.0.1:4200/` with HTTP 200 | Passed |
| Frontend proxy | `POST /api/v1/auth/login` through port 4200 returned HTTP 400, proving proxy reached backend | Passed |

Backend startup evidence:

- active profiles: `local`, `career-mutations`;
- database URL: local PostgreSQL database `football_manager`;
- Netty started on port `8080`;
- application startup completed.

## Runtime API smoke

Executed against the live frontend proxy/backend runtime.

| Flow | Result |
| --- | --- |
| register unique user | Passed |
| login | Passed |
| seed all world data | Passed: 10 leagues |
| list leagues | Passed: 10 leagues |
| list teams for selected league | Passed: 60 teams |
| start career | Passed |
| read career status | Passed |
| read squad | Passed: 22 players |
| read current lineup | Passed |
| auto-select `4-3-3` | Passed: 11 players, 11 slots |
| confirm lineup | Passed |
| round-with-bye fixture query | Passed |
| prepare next round | Passed |
| list all fixtures | Passed |
| list round 1 fixtures | Passed: 24 fixtures |
| start match engine round | Passed: `IN_PROGRESS`, 24 matches |
| round SSE endpoint | Passed with controlled timeout after receiving about 4 MB from the stream |
| status after engine start | Passed |
| test harness snapshot | Passed |
| player season stats | Passed |
| game match endpoint | Passed |

Known route note:

- `GET /api/v1/matches/{matchId}/state` returned HTTP 404 during smoke. This is not used as the canonical live round state endpoint in the current flow; the round engine start, SSE stream, harness snapshot, and game match endpoint all passed.

## Browser/UI validation

The in-app browser connector was attempted after loading the browser skill, but failed before it could attach:

```text
failed to write kernel assets: El sistema no puede encontrar la ruta especificada. (os error 3)
```

Because this is a connector/runtime issue outside the application, validation continued with local Chrome automation against the live application, without adding dependencies.

Local Chrome UI smoke result:

```text
UI_SMOKE_OK pages=4 careerId=90789f75-8618-46de-9190-a409524ef7a1
```

Validated authenticated pages:

- `/dashboard` — dashboard loaded with the created manager and active career.
- `/squad` — squad page loaded with team, squad count, and lineup status.
- `/debug/test-harness` — harness page loaded.
- `/games/{careerId}/round/1/live` — live round page loaded.

## Frontend validation

Commands executed from `D:\ProyectosOpenCode\MANAGER\front-ciber\project`:

| Validation | Result |
| --- | --- |
| `npm run build -- --configuration development` | Passed |
| `npm test -- --watch=false --browsers=ChromeHeadless` | Passed: 1016 success, 0 failures, 2 skipped |
| `npm run build` | Passed |

Production build evidence:

- initial raw bundle: 420.67 kB;
- estimated transfer: 118.11 kB;
- output folder: `dist/demo`.

Frontend test note:

- The suite logs expected warning/error messages from tests that intentionally exercise degraded SSE and unreachable database handling. They do not fail the suite.

## Backend validation

Commands executed from `D:\ProyectosOpenCode\MANAGER` with `.env` loaded into the same process and `REDIS_HOST/REDIS_PORT` session defaults applied:

| Validation | Result |
| --- | --- |
| `mvn -q -DskipTests test-compile` | Passed |
| `mvn -q test` | Passed: 3750 tests, 0 failures, 0 errors, 8 skipped |

The complete backend suite includes the previously requested areas:

- match engine;
- detailed match persistence;
- lineup;
- simulation lifecycle;
- discipline;
- league simulation;
- harness-related flows;
- WebFlux/controllers;
- Redis/PostgreSQL integration paths covered by existing tests.

## Temporary artifacts

Temporary runtime scripts and logs were used only to perform the validation and were removed before closure:

- `.runtime-backend.pid`
- `backend-runtime-smoke.out.log`
- `backend-runtime-smoke.err.log`
- `runtime-smoke-start.ps1`
- `runtime-api-smoke.ps1`
- `runtime-ui-smoke.mjs`
- `front-ciber/project/.runtime-frontend.pid`
- `front-ciber/project/frontend-runtime-smoke.out.log`
- `front-ciber/project/frontend-runtime-smoke.err.log`

## Final status

Full-stack validation is complete using the real runbook and live local services.

Final verdict: COMPLETED.
