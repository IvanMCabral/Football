# PB1.2.1 Graceful Shutdown Smoke Report

Date: 2026-08-01

Command:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools\run-production-jar-smoke.ps1 -SkipBuild -KeepArtifactsOnFailure
```

Result:

```json
{"status":"PASS","jar":"football-manager-1.0.0.jar","jarBytes":42467597,"port":58016,"run1PortMode":"PORT","run2Port":64852,"run2PortMode":"SERVER_PORT","javaPid":28192,"javaPidRun2":44996,"postgresPid":47832,"redisPid":50588,"startupDurationMs":6661,"startupDurationMsRun2":6543,"liveness":200,"readiness":200,"registered":true,"login":true,"me":true,"careerCreated":true,"flywaySuccessfulMigrations":1,"secondStartup":true,"gracefulSignalSent":true,"gracefulShutdownObserved":true,"forceKillUsed":false,"shutdownDurationMs":2712,"shutdownDurationMsRun2":2711,"javaExitCode":130,"javaExitCodeRun2":130,"shutdownMarkersObserved":4,"residualProcesses":0,"residualPorts":0,"localLogArtifacts":0}
```

## Run 1

| Check | Result |
|---|---|
| Port mode | `PORT` |
| Port | `58016` |
| Java PID | `28192` |
| Startup duration | `6661 ms` |
| Liveness | `200` |
| Readiness | `200` |
| Register/login/me | PASS |
| Minimal career | PASS |
| Flyway successful migrations | `1` |
| Graceful signal sent | `true` |
| Graceful shutdown observed | `true` |
| Force kill used | `false` |
| Shutdown duration | `2712 ms` |
| Java exit code | `130` |

## Run 2

| Check | Result |
|---|---|
| Port mode | `SERVER_PORT` |
| Port | `64852` |
| Java PID | `44996` |
| Startup duration | `6543 ms` |
| Liveness | `200` |
| Readiness | `200` |
| Login/me after restart | PASS |
| Same temporary DB | PASS |
| Flyway successful migrations | `1` |
| Graceful signal sent | `true` |
| Graceful shutdown observed | `true` |
| Force kill used | `false` |
| Shutdown duration | `2712 ms` |
| Java exit code | `130` |

## Cleanup

| Check | Result |
|---|---|
| Java/helper residual processes | `0` |
| App residual ports | `0` |
| Local log artifacts | `0` |
| Temporary PostgreSQL stopped after backend | PASS |
| Temporary Redis stopped after backend | PASS |
| Temporary workspace cleanup | PASS on successful runs unless `-KeepArtifacts` is used |

## Interpretation

Exit code `130` is expected for a Java process interrupted by a console control event. PASS is based on the combination of signal delivery, Spring shutdown markers, process self-termination, no force kill, bounded duration and zero residuals.


