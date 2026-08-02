# PB1.2.1 Smoke Lifecycle Cleanup Report

Date: 2026-08-01

Command:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools\run-production-jar-smoke.ps1 -SkipBuild
```

Result:

```json
{"status":"PASS","jar":"football-manager-1.0.0.jar","jarBytes":42467597,"port":63645,"run1PortMode":"PORT","run2Port":64166,"run2PortMode":"SERVER_PORT","javaPid":53740,"javaPidRun2":44044,"postgresPid":64144,"redisPid":34568,"startupDurationMs":6448,"startupDurationMsRun2":6629,"liveness":200,"readiness":200,"registered":true,"login":true,"me":true,"careerCreated":true,"flywaySuccessfulMigrations":1,"secondStartup":true,"signalUsedRun1":"CTRL_C_EVENT","signalUsedRun2":"CTRL_C_EVENT","signalFallbackUsedRun1":false,"signalFallbackUsedRun2":false,"javaGracefulRun1":true,"javaGracefulRun2":true,"javaGracefulStop":true,"postgresGracefulStop":true,"redisGracefulStop":true,"gracefulSignalSent":true,"gracefulShutdownObserved":true,"javaForceKillUsedRun1":false,"javaForceKillUsedRun2":false,"helperForceKillUsedRun1":false,"helperForceKillUsedRun2":false,"postgresForceKillUsed":false,"redisForceKillUsed":false,"forceKillUsed":false,"shutdownDurationMs":2722,"shutdownDurationMsRun2":2723,"javaExitCode":130,"javaExitCodeRun2":130,"postgresExitCode":0,"redisExitCode":0,"shutdownStartMarkerRun1":true,"shutdownCompleteMarkerRun1":true,"shutdownMarkerOrderRun1":true,"shutdownStartMarkerRun2":true,"shutdownCompleteMarkerRun2":true,"shutdownMarkerOrderRun2":true,"shutdownMarkersObserved":4,"residualJavaProcesses":0,"residualHelperProcesses":0,"residualPostgresProcesses":0,"residualRedisProcesses":0,"residualProcesses":0,"residualHttpPorts":0,"residualPostgresPorts":0,"residualRedisPorts":0,"residualPorts":0,"localLogArtifacts":0,"workspaceExists":false,"safeSummaryExists":false,"residualTempArtifacts":0,"cleanupVerified":true}
```

## Negative modes

Command family:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools\run-production-jar-smoke.ps1 -SkipBuild -LifecycleTestMode <mode>
```

Results:

| Mode | Expected | Observed |
|---|---|---|
| `postgres-stop-fails` | FAIL | non-zero exit, `status=FAIL`, no PASS |
| `redis-stop-fails` | FAIL | non-zero exit, `status=FAIL`, no PASS |
| `helper-fails-after-java` | FAIL | non-zero exit, `status=FAIL`, no PASS |
| `workspace-delete-fails` | FAIL | non-zero exit, `status=FAIL`, no PASS |
| `marker-order-invalid` | FAIL | non-zero exit, `status=FAIL`, no PASS |

There is no synthetic self-test anymore. Each mode executes the real smoke runner path and returns a non-zero exit code.

## Keep artifacts mode

`-KeepArtifacts` was also executed. It preserved a sanitized workspace intentionally but still reported:

- zero residual Java/helper/PostgreSQL/Redis processes;
- zero residual HTTP/PostgreSQL/Redis ports;
- `forceKillUsed=false`;
- no safe summary outside the workspace;
- no `postgres-data`;
- 21 files preserved;
- 0 secret-like hits for Redis password, DB password, JWT or Authorization tokens.

The preserved workspace was removed after inspection; final temp smoke directory count was `0`.
