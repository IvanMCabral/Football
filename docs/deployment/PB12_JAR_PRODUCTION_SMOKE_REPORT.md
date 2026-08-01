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
{"status":"PASS","jar":"football-manager-1.0.0.jar","jarBytes":42467597,"port":58016,"run1PortMode":"PORT","run2Port":64852,"run2PortMode":"SERVER_PORT","javaPid":28192,"javaPidRun2":44996,"postgresPid":47832,"redisPid":50588,"startupDurationMs":6661,"startupDurationMsRun2":6543,"liveness":200,"readiness":200,"registered":true,"login":true,"me":true,"careerCreated":true,"flywaySuccessfulMigrations":1,"secondStartup":true,"gracefulSignalSent":true,"gracefulShutdownObserved":true,"forceKillUsed":false,"shutdownDurationMs":2712,"shutdownDurationMsRun2":2711,"javaExitCode":130,"javaExitCodeRun2":130,"shutdownMarkersObserved":4,"residualProcesses":0,"residualPorts":0,"localLogArtifacts":0}
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
- The runner fails if the minimal game/career is not created.
- First startup used `PORT`.
- Second startup used `SERVER_PORT` against the same temporary DB.
- Flyway remained stable at exactly one successful migration after the second startup.
- Both shutdowns used a Windows console control event delivered by `GracefulProcessGroupRunner`.
- Both shutdowns observed Spring graceful shutdown log markers.
- No force kill was used in the passing run.
- Java exit code was `130`, consistent with console interrupt termination after graceful shutdown.
- Java/helper processes and app ports had zero residuals before PostgreSQL/Redis cleanup.
- No root `logs/` or `app.log` artifact was created or changed by the application.
- Java process terminated by itself after the graceful signal in both runs.
- Temporary PostgreSQL and Redis processes were stopped.

## Known limitation

The shutdown drill validates the Spring/JAR process on Windows using a real console control event. It does not validate container PID 1 or `docker stop`; Docker remains unavailable locally.
