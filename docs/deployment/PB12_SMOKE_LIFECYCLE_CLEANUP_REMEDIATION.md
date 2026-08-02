# PB1.2.1 Smoke Lifecycle Cleanup Remediation

Date: 2026-08-01

Historical audit preserved:

- `docs/deployment/PB12_GRACEFUL_SHUTDOWN_DEFINITIVE_INDEPENDENT_AUDIT.md`
- Historical verdict: `PB1.2.1 PRODUCTION RUNTIME REJECTED`

This remediation is superseded by the actual implementation closure in:

- `docs/deployment/PB12_SMOKE_LIFECYCLE_ACTUAL_IMPLEMENTATION_REMEDIATION.md`
- `docs/deployment/PB12_SMOKE_LIFECYCLE_ACTUAL_IMPLEMENTATION_REPORT.md`

It remains as historical context for the first cleanup pass. The actual implementation closure replaced the synthetic self-test with executable `-LifecycleTestMode` flows.

## P0 closure

| P0 | Status | Evidence |
|---|---|---|
| PostgreSQL/Redis force kill could occur in PASS | CLOSED | PASS now requires graceful PostgreSQL and Redis stop and global `forceKillUsed=false` |
| Cleanup was not fail-closed | CLOSED | PASS is emitted only after cleanup verification; default run reports `workspaceExists=false`, `safeSummaryExists=false`, `residualTempArtifacts=0` |
| Java could remain orphaned if helper failed after PID creation | CLOSED | Runner reads Java PID immediately and attempts graceful signal before force cleanup on FAIL |

## Dependency shutdown

PostgreSQL uses the official PostgreSQL control path:

```text
pg_ctl stop -D <tempDataDir> -m fast -w -t <timeout>
```

Redis uses authenticated shutdown without printing the password:

```text
redis-cli -h 127.0.0.1 -p <port> --no-auth-warning -a <redacted> SHUTDOWN NOSAVE
```

In PASS:

- `postgresGracefulStop=true`;
- `redisGracefulStop=true`;
- `postgresForceKillUsed=false`;
- `redisForceKillUsed=false`;
- `postgresExitCode=0`;
- `redisExitCode=0`.

## Java signal evidence

`GracefulProcessGroupRunner` now records:

- signal attempted;
- signal used;
- fallback usage;
- Win32 error values;
- signal timestamp;
- Java PID and process group ID;
- Java exit code.

The final smoke used `CTRL_C_EVENT` for both JAR runs and did not use fallback.

## Marker validation

The runner validates marker order per run:

1. `Commencing graceful shutdown`;
2. `Graceful shutdown complete` or `Shutdown completed`.

PASS requires both markers and valid order for both runs.

## Negative lifecycle tests

The current runner provides controlled `-LifecycleTestMode` failures from the official `tools/run-production-jar-smoke.ps1` file. `-LifecycleSelfTest` now fails intentionally because synthetic lifecycle evidence is forbidden. Verified negative modes:

- `postgres-stop-fails`;
- `redis-stop-fails`;
- `helper-fails-after-java`;
- `workspace-delete-fails`;
- `marker-order-invalid`.

Each mode exited non-zero and emitted `status=FAIL`; none emitted `status=PASS`.
