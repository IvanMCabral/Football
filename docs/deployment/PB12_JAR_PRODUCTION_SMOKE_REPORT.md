# PB1.2.1 Production JAR Smoke Report

Date: 2026-08-01

Runner:

- `tools/run-production-jar-smoke.ps1`

Command used:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/run-production-jar-smoke.ps1 -SkipBuild
```

`-SkipBuild` was used after two clean reproducible package builds had already produced the tested JAR.

## Environment isolation

| Item | Result |
|---|---|
| `.env` loaded | No |
| Principal DB used | No |
| Local Redis service required | No |
| PostgreSQL | Temporary process, random port |
| Redis | Temporary process, random port, password auth |
| DB user | `manager_smoke`, generated for the smoke |
| JWT secret | Random 96 bytes, Base64 |
| Spring profile | `prod` |
| App port | Random non-standard port |
| Server address | `0.0.0.0` |

## Result

```json
{"status":"PASS","jar":"football-manager-1.0.0.jar","jarBytes":42467597,"port":61012,"serverAddress":"0.0.0.0","liveness":200,"readiness":200,"registered":true,"login":true,"userId":"a4298c30-732d-4265-ab7e-d4c09fafea2f","careerCreated":true,"flywaySuccessfulMigrations":1,"localLogArtifacts":0,"shutdownMs":50,"postgresTemp":true,"redisTemp":true}
```

## Verified behavior

- Production startup validation passed with safe synthetic values.
- Flyway applied migration V1 to the temporary DB.
- Three-league importer loaded 3 countries, 3 leagues, 70 clubs, 70 teams, 1680 players and 3360 trait rows into the temporary DB.
- Liveness returned `200`.
- Readiness returned `200`.
- `/api/v1/auth/register` returned an access token.
- `/api/v1/auth/login` returned an access token.
- `/api/v1/auth/me` returned the registered user.
- `/api/v1/games` created a minimal game/career using DB-selected league/team IDs.
- No root `logs/` or `app.log` artifact was created or changed by the application.
- Java process terminated in the shutdown drill.
- Temporary PostgreSQL and Redis processes were stopped.

## Known limitation

The shutdown drill validates the Spring/JAR process on Windows. It does not validate container PID 1 or `docker stop`; Docker remains unavailable locally.
