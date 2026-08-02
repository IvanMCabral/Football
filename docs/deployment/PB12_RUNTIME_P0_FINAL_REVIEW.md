# PB1.2.1 Runtime P0 Final Review

Date: 2026-08-01

Verdict: `PB1.2.1 IMPLEMENTATION COMPLETE - DOCKER SMOKE BLOCKED`

## P0 status

| P0 | Status | Evidence |
|---|---|---|
| Production logging writes to local Windows path | CLOSED | Console-only Logback, static guard, JAR smoke localLogArtifacts=0 |
| Root logger DEBUG in production | CLOSED | root defaults to `${LOG_LEVEL_ROOT:-INFO}` |
| Docker healthcheck uses undeclared `wget` | CLOSED STATICALLY | Jammy runtime installs `curl`; healthcheck uses curl/liveness |
| Missing production JAR smoke | CLOSED | `tools/run-production-jar-smoke.ps1` PASS |
| Graceful shutdown not proven | CLOSED | Windows console control event, two JAR runs, no force kill |
| Dependency shutdown/cleanup not fail-closed | CLOSED | PostgreSQL/Redis graceful stop, global force kill false, workspace removed |

## P1 status

| P1 | Status |
|---|---|
| Explicit `server.address` binding | CLOSED |
| Redis SSL contract ambiguity | CLOSED, official variable is `REDIS_SSL` |
| Docker context too broad | IMPROVED |
| Alpine base risk | CLOSED by switching to Jammy |
| JAR reproducibility unknown | CLOSED, matching clean build hash |
| Docker smoke missing | REMAINS EXTERNAL BLOCKER |

## Runtime evidence

- JAR SHA-256: `F7C0C609A97418C9FFDE36821CE5CD7EA0562C32B74EAA6504C9BF1DC08F9FB1`
- JAR size: `42,467,597` bytes
- Smoke port: `61012`
- Definitive graceful smoke port: `50816`
- Second startup port: `50218`
- Liveness: `200`
- Readiness: `200`
- Auth register/login: PASS
- Minimal career creation: PASS
- Flyway successful migrations: `1`
- Shutdown drill: PASS, two graceful shutdowns
- Graceful signal sent: `true`
- Signal used: `CTRL_C_EVENT`, `CTRL_C_EVENT`
- Graceful shutdown observed: `true`
- Shutdown marker order: `true`, `true`
- Force kill used: `false`
- Java exit codes: `130`, `130`
- PostgreSQL graceful stop: PASS, exit `0`
- Redis graceful stop: PASS, exit `0`
- Residual Java/helper processes: `0`
- Residual PostgreSQL/Redis processes: `0`
- Residual app ports: `0`
- Residual PostgreSQL/Redis ports: `0`
- Workspace exists after default PASS: `false`
- Safe summary exists after default PASS: `false`
- Local log artifacts: `0`

## Docker status

Docker is still not installed or not available in the local PATH. Therefore:

- image build not executed;
- image size not measured;
- runtime UID inside container not inspected;
- `docker stop` not drilled;
- image filesystem not scanned.

No Docker result is invented.

## Final decision

PB1.2.1 local P0 remediation is complete. The project is ready for independent re-audit of the local runtime artifacts. The only remaining blocker is external Docker smoke on a Docker-capable machine or CI runner.
