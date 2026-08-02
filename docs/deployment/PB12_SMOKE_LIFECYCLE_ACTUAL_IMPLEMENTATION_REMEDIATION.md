# PB1.2.1 Smoke Lifecycle Actual Implementation Remediation

Date: 2026-08-01

Historical source of truth preserved:

- `docs/deployment/PB12_SMOKE_LIFECYCLE_DEFINITIVE_INDEPENDENT_AUDIT.md`
- historical verdict: `PB1.2.1 PRODUCTION RUNTIME REJECTED`

## Cause of the previous discrepancy

The previous closure mixed real partial changes with overstated evidence:

- the runner already contained some graceful PostgreSQL/Redis code, but the public test interface was still `-TestMode` and the automated lifecycle evidence was a synthetic `-LifecycleSelfTest`;
- force-kill state was not explicit per Java/helper run, so the global flag was harder to audit;
- helper-created Java PID recovery depended on `java.pid`, not a structured state file written immediately after `CreateProcess`;
- `-KeepArtifacts` preserved too much raw workspace content, including temporary PostgreSQL data;
- reports described the desired final behavior more strongly than the effective audited implementation.

This remediation changes the actual versioned runner and helper, not a temporary copy.

## Implementation changes

- `tools/run-production-jar-smoke.ps1` now exposes the official negative interface: `-LifecycleTestMode <mode>`.
- `-LifecycleSelfTest` now throws because synthetic lifecycle tests are forbidden.
- `tools/GracefulProcessGroupRunner.cs` writes a structured `helper-state.json` immediately after `CreateProcess`.
- `forceKillUsed` is calculated from explicit lifecycle flags:
  - `javaForceKillUsedRun1`;
  - `javaForceKillUsedRun2`;
  - `helperForceKillUsedRun1`;
  - `helperForceKillUsedRun2`;
  - `postgresForceKillUsed`;
  - `redisForceKillUsed`.
- PostgreSQL PASS shutdown uses `pg_ctl stop -D <dataDir> -m fast -w -t <timeout>`.
- Redis PASS shutdown uses authenticated `redis-cli ... SHUTDOWN NOSAVE`.
- Emergency force cleanup remains available only after FAIL paths and is reflected in the relevant lifecycle flag.
- Cleanup uses `Remove-PathFailClosed`, supports files and directories, retries in a bounded way, and fails if the path remains.
- `-KeepArtifacts` sanitizes the preserved workspace by removing `postgres-data` and secret-bearing setup logs.

## Negative modes

All modes execute the same official runner:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools\run-production-jar-smoke.ps1 -SkipBuild -LifecycleTestMode <mode>
```

| Mode | Result |
|---|---|
| `postgres-stop-fails` | FAIL, exit 1, `postgresForceKillUsed=true`, residual processes/ports 0 |
| `redis-stop-fails` | FAIL, exit 1, `redisForceKillUsed=true`, residual processes/ports 0 |
| `helper-fails-after-java` | FAIL, exit 1, Java PID recovered, residual processes/ports 0 |
| `workspace-delete-fails` | FAIL, exit 1, `cleanupVerified=false`, final temp dirs cleaned by finalizer |
| `marker-order-invalid` | FAIL, exit 1, marker order rejected, residual processes/ports 0 |

## Validation

- `mvn -q -DskipTests test-compile`: PASS.
- `mvn -q -Dtest=ProductionRuntimeArtifactGuardTest test`: PASS.
- `mvn -q test`: 2571 tests, 0 failures, 0 errors, 4 skipped.
- Frontend encoding/builds/artifact inspection/tests: PASS, 1029 SUCCESS, 0 failures, 2 skipped.

