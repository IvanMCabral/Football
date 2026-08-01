# PB1.2.1 Graceful Shutdown Remediation

Date: 2026-08-01

Historical audit preserved:

- `docs/deployment/PB12_RUNTIME_P0_DEFINITIVE_INDEPENDENT_AUDIT.md`
- Historical verdict: `PB1.2.1 PRODUCTION RUNTIME REJECTED`

This document records the remediation performed after that rejection. It does not rewrite the historical audit.

## Signal mechanism

Windows does not expose Unix `SIGTERM` directly to a Java process. The remediation therefore uses a small versioned tooling helper:

- `tools/GracefulProcessGroupRunner.cs`

The helper:

1. starts the production JAR in a new Windows process group;
2. creates a dedicated console for that process;
3. waits for the smoke runner to request shutdown;
4. attaches to the child console;
5. sends a Windows console control event;
6. waits for the process to terminate by itself;
7. records exit code, duration and whether force kill was used.

This is equivalent to a graceful console termination request for a foreground Java/Spring process. It is not `Stop-Process`, `taskkill /F`, `Process.destroyForcibly` or closing dependencies first.

## Runner changes

Updated:

- `tools/run-production-jar-smoke.ps1`

The runner now fails if any required condition is missing:

- PostgreSQL startup;
- Redis startup;
- production JAR startup;
- liveness `200`;
- readiness `200`;
- register/login;
- `/auth/me`;
- league/team IDs;
- minimal career creation;
- exactly one successful Flyway migration;
- second startup against the same temporary DB;
- graceful shutdown for both runs;
- no force kill in PASS;
- zero residual Java/helper processes;
- zero residual app ports;
- no local log artifacts.

## Runtime sequence

The passing smoke executes:

1. temporary PostgreSQL;
2. temporary Redis with auth;
3. JAR run 1 with `PORT`;
4. health checks;
5. auth and minimal career creation;
6. Flyway check;
7. graceful shutdown run 1;
8. JAR run 2 with `SERVER_PORT`, same DB;
9. health checks;
10. login recovery;
11. Flyway check again;
12. graceful shutdown run 2;
13. residual process/port check;
14. dependency cleanup;
15. temporary workspace cleanup on success.

## Evidence

Observed PASS:

```json
{"status":"PASS","jar":"football-manager-1.0.0.jar","jarBytes":42467597,"port":58016,"run1PortMode":"PORT","run2Port":64852,"run2PortMode":"SERVER_PORT","javaPid":28192,"javaPidRun2":44996,"postgresPid":47832,"redisPid":50588,"startupDurationMs":6661,"startupDurationMsRun2":6543,"liveness":200,"readiness":200,"registered":true,"login":true,"me":true,"careerCreated":true,"flywaySuccessfulMigrations":1,"secondStartup":true,"gracefulSignalSent":true,"gracefulShutdownObserved":true,"forceKillUsed":false,"shutdownDurationMs":2712,"shutdownDurationMsRun2":2711,"javaExitCode":130,"javaExitCodeRun2":130,"shutdownMarkersObserved":4,"residualProcesses":0,"residualPorts":0,"localLogArtifacts":0}
```

## Docker limitation

This closes the local JAR/Spring graceful shutdown P0. It still does not prove Docker PID 1 behavior or `docker stop`, because Docker remains unavailable on this workstation.


