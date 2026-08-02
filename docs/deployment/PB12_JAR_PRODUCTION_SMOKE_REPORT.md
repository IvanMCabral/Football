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
{"status":"PASS","jar":"football-manager-1.0.0.jar","jarBytes":42467597,"port":50816,"run1PortMode":"PORT","run2Port":50218,"run2PortMode":"SERVER_PORT","javaPid":18668,"javaPidRun2":43776,"postgresPid":46776,"redisPid":34752,"startupDurationMs":6550,"startupDurationMsRun2":6553,"liveness":200,"readiness":200,"registered":true,"login":true,"me":true,"careerCreated":true,"flywaySuccessfulMigrations":1,"secondStartup":true,"signalUsedRun1":"CTRL_C_EVENT","signalUsedRun2":"CTRL_C_EVENT","signalFallbackUsedRun1":false,"signalFallbackUsedRun2":false,"javaGracefulRun1":true,"javaGracefulRun2":true,"javaGracefulStop":true,"postgresGracefulStop":true,"redisGracefulStop":true,"gracefulSignalSent":true,"gracefulShutdownObserved":true,"javaForceKillUsed":false,"postgresForceKillUsed":false,"redisForceKillUsed":false,"forceKillUsed":false,"shutdownDurationMs":2728,"shutdownDurationMsRun2":2744,"javaExitCode":130,"javaExitCodeRun2":130,"postgresExitCode":0,"redisExitCode":0,"shutdownStartMarkerRun1":true,"shutdownCompleteMarkerRun1":true,"shutdownMarkerOrderRun1":true,"shutdownStartMarkerRun2":true,"shutdownCompleteMarkerRun2":true,"shutdownMarkerOrderRun2":true,"shutdownMarkersObserved":4,"residualJavaProcesses":0,"residualHelperProcesses":0,"residualPostgresProcesses":0,"residualRedisProcesses":0,"residualProcesses":0,"residualHttpPorts":0,"residualPostgresPorts":0,"residualRedisPorts":0,"residualPorts":0,"localLogArtifacts":0,"workspaceExists":false,"safeSummaryExists":false,"residualTempArtifacts":0,"cleanupVerified":true}
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
- No force kill was used in the passing run for Java, PostgreSQL or Redis.
- PostgreSQL stopped gracefully with `pg_ctl stop -m fast -w` and exit code `0`.
- Redis stopped gracefully with authenticated `SHUTDOWN NOSAVE` and exit code `0`.
- Java exit code was `130`, consistent with console interrupt termination after graceful shutdown.
- Java/helper/PostgreSQL/Redis processes and HTTP/PostgreSQL/Redis ports had zero residuals.
- No root `logs/` or `app.log` artifact was created or changed by the application.
- Java process terminated by itself after the graceful signal in both runs.
- Temporary workspace cleanup was verified fail-closed on PASS.

## Known limitation

The shutdown drill validates the Spring/JAR process on Windows using a real console control event. It does not validate container PID 1 or `docker stop`; Docker remains unavailable locally.
