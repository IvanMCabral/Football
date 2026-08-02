# PB1.2.1 Graceful Shutdown Smoke Report

Date: 2026-08-01

Command:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools\run-production-jar-smoke.ps1 -SkipBuild -KeepArtifactsOnFailure
```

Result:

```json
{"status":"PASS","jar":"football-manager-1.0.0.jar","jarBytes":42467597,"port":50816,"run1PortMode":"PORT","run2Port":50218,"run2PortMode":"SERVER_PORT","javaPid":18668,"javaPidRun2":43776,"postgresPid":46776,"redisPid":34752,"startupDurationMs":6550,"startupDurationMsRun2":6553,"liveness":200,"readiness":200,"registered":true,"login":true,"me":true,"careerCreated":true,"flywaySuccessfulMigrations":1,"secondStartup":true,"signalUsedRun1":"CTRL_C_EVENT","signalUsedRun2":"CTRL_C_EVENT","signalFallbackUsedRun1":false,"signalFallbackUsedRun2":false,"javaGracefulRun1":true,"javaGracefulRun2":true,"javaGracefulStop":true,"postgresGracefulStop":true,"redisGracefulStop":true,"gracefulSignalSent":true,"gracefulShutdownObserved":true,"javaForceKillUsed":false,"postgresForceKillUsed":false,"redisForceKillUsed":false,"forceKillUsed":false,"shutdownDurationMs":2728,"shutdownDurationMsRun2":2744,"javaExitCode":130,"javaExitCodeRun2":130,"postgresExitCode":0,"redisExitCode":0,"shutdownStartMarkerRun1":true,"shutdownCompleteMarkerRun1":true,"shutdownMarkerOrderRun1":true,"shutdownStartMarkerRun2":true,"shutdownCompleteMarkerRun2":true,"shutdownMarkerOrderRun2":true,"shutdownMarkersObserved":4,"residualJavaProcesses":0,"residualHelperProcesses":0,"residualPostgresProcesses":0,"residualRedisProcesses":0,"residualProcesses":0,"residualHttpPorts":0,"residualPostgresPorts":0,"residualRedisPorts":0,"residualPorts":0,"localLogArtifacts":0,"workspaceExists":false,"safeSummaryExists":false,"residualTempArtifacts":0,"cleanupVerified":true}
```

## Run 1

| Check | Result |
|---|---|
| Port mode | `PORT` |
| Port | `50816` |
| Java PID | `18668` |
| Startup duration | `6550 ms` |
| Liveness | `200` |
| Readiness | `200` |
| Register/login/me | PASS |
| Minimal career | PASS |
| Flyway successful migrations | `1` |
| Graceful signal sent | `true` |
| Graceful shutdown observed | `true` |
| Force kill used | `false` |
| Signal used | `CTRL_C_EVENT` |
| Shutdown marker order | `true` |
| Shutdown duration | `2728 ms` |
| Java exit code | `130` |

## Run 2

| Check | Result |
|---|---|
| Port mode | `SERVER_PORT` |
| Port | `50218` |
| Java PID | `43776` |
| Startup duration | `6553 ms` |
| Liveness | `200` |
| Readiness | `200` |
| Login/me after restart | PASS |
| Same temporary DB | PASS |
| Flyway successful migrations | `1` |
| Graceful signal sent | `true` |
| Graceful shutdown observed | `true` |
| Force kill used | `false` |
| Signal used | `CTRL_C_EVENT` |
| Shutdown marker order | `true` |
| Shutdown duration | `2744 ms` |
| Java exit code | `130` |

## Cleanup

| Check | Result |
|---|---|
| Java/helper residual processes | `0` |
| PostgreSQL/Redis residual processes | `0` |
| App residual ports | `0` |
| PostgreSQL/Redis residual ports | `0` |
| PostgreSQL exit code | `0` |
| Redis exit code | `0` |
| Local log artifacts | `0` |
| Temporary PostgreSQL stopped after backend | PASS, graceful via `pg_ctl stop -m fast` |
| Temporary Redis stopped after backend | PASS, graceful via `SHUTDOWN NOSAVE` |
| Temporary workspace cleanup | PASS, `workspaceExists=false`, `safeSummaryExists=false`, `residualTempArtifacts=0` |
| Global force kill in PASS | `false` |

## Interpretation

Exit code `130` is expected for a Java process interrupted by a console control event. PASS is based on the combination of signal delivery, Spring shutdown markers, process self-termination, no force kill, bounded duration and zero residuals.


