# PB1.2.1 Smoke Lifecycle Actual Implementation Report

Date: 2026-08-01

## Pre-change verification

The audit file `docs/deployment/PB12_SMOKE_LIFECYCLE_DEFINITIVE_INDEPENDENT_AUDIT.md` was present as an untracked historical audit and is now preserved in Git with its `PB1.2.1 PRODUCTION RUNTIME REJECTED` verdict.

Commands inspected before implementation:

- `git status --short`;
- `git log --oneline -12`;
- `git show --stat --oneline cffcd4fc`;
- `git show --stat --oneline 2bb2436e`;
- `git show --stat --oneline 5b3580aa`;
- `git show 5b3580aa:tools/run-production-jar-smoke.ps1`;
- `git show HEAD:tools/run-production-jar-smoke.ps1`.

Conclusion: the previous commits were real, but incomplete relative to the definitive audit. The largest mismatch was not a different runner path; it was an insufficiently auditable implementation and a synthetic negative self-test.

## Default smoke

Command:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools\run-production-jar-smoke.ps1 -SkipBuild
```

Result:

```json
{"status":"PASS","jar":"football-manager-1.0.0.jar","jarBytes":42467597,"port":63645,"run1PortMode":"PORT","run2Port":64166,"run2PortMode":"SERVER_PORT","javaPid":53740,"javaPidRun2":44044,"postgresPid":64144,"redisPid":34568,"startupDurationMs":6448,"startupDurationMsRun2":6629,"liveness":200,"readiness":200,"registered":true,"login":true,"me":true,"careerCreated":true,"flywaySuccessfulMigrations":1,"secondStartup":true,"signalUsedRun1":"CTRL_C_EVENT","signalUsedRun2":"CTRL_C_EVENT","signalFallbackUsedRun1":false,"signalFallbackUsedRun2":false,"javaGracefulRun1":true,"javaGracefulRun2":true,"javaGracefulStop":true,"postgresGracefulStop":true,"redisGracefulStop":true,"gracefulSignalSent":true,"gracefulShutdownObserved":true,"javaForceKillUsedRun1":false,"javaForceKillUsedRun2":false,"helperForceKillUsedRun1":false,"helperForceKillUsedRun2":false,"postgresForceKillUsed":false,"redisForceKillUsed":false,"forceKillUsed":false,"shutdownDurationMs":2722,"shutdownDurationMsRun2":2723,"javaExitCode":130,"javaExitCodeRun2":130,"postgresExitCode":0,"redisExitCode":0,"shutdownStartMarkerRun1":true,"shutdownCompleteMarkerRun1":true,"shutdownMarkerOrderRun1":true,"shutdownStartMarkerRun2":true,"shutdownCompleteMarkerRun2":true,"shutdownMarkerOrderRun2":true,"shutdownMarkersObserved":4,"residualJavaProcesses":0,"residualHelperProcesses":0,"residualPostgresProcesses":0,"residualRedisProcesses":0,"residualProcesses":0,"residualHttpPorts":0,"residualPostgresPorts":0,"residualRedisPorts":0,"residualPorts":0,"localLogArtifacts":0,"workspaceExists":false,"safeSummaryExists":false,"residualTempArtifacts":0,"cleanupVerified":true}
```

## KeepArtifacts smoke

Command:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools\run-production-jar-smoke.ps1 -SkipBuild -KeepArtifacts
```

Result summary:

- PASS;
- run 1 port `50785`;
- run 2 port `57796`;
- `forceKillUsed=false`;
- `postgresGracefulStop=true`;
- `redisGracefulStop=true`;
- residual processes/ports: 0;
- preserved sanitized file count: 21;
- `postgres-data` removed;
- secret-like hits: 0;
- final temp smoke directories after manual cleanup: 0.

## Backend and frontend

| Area | Result |
|---|---|
| Backend test compile | PASS |
| Runner lifecycle test | PASS, five real negative modes |
| Backend full suite | 2571 tests, 0 failures, 0 errors, 4 skipped |
| Frontend encoding | PASS, 385 files scanned |
| Frontend development build | PASS |
| Frontend production build | PASS |
| Frontend artifact inspection | PASS, 52 files scanned |
| Frontend tests | 1029 SUCCESS, 0 failures, 2 skipped |

## Verdict

`PB1.2.1 IMPLEMENTATION COMPLETE - DOCKER SMOKE BLOCKED`

Docker remains the only unexecuted gate because Docker is unavailable locally and was not installed.
