# PB1.2.1 Production Runtime Final Review

Date: 2026-08-01

Verdict: `PB1.2.1 IMPLEMENTATION COMPLETE - DOCKER SMOKE BLOCKED`

## Summary

PB1.2.1 created the backend container artifact, frontend Firebase Hosting artifact and runtime documentation needed to move toward Internet staging. No gameplay, simulation, dataset, cloud resource, deploy or CI/CD change was made.

## Evidence

| Area | Evidence | Result |
|---|---|---|
| Backend Dockerfile | Multi-stage Maven build, Java 21 runtime, explicit tags | PASS |
| Runtime user | Dockerfile creates and uses `manager` | PASS |
| Secrets | `.env`, dumps, backups, logs and secret material excluded by `.dockerignore` | PASS |
| Port | Application uses `PORT` with fallback to `SERVER_PORT`/`8080` | PASS |
| Address | Application uses `SERVER_ADDRESS` with default `0.0.0.0` | PASS |
| Health | Custom liveness/readiness endpoints exist and are allowed by security | PASS |
| Graceful shutdown | `server.shutdown=graceful`, timeout configurable, entrypoint uses `exec` | PASS |
| Flyway | Startup migrations enabled, no dangerous baseline/repair setting | PASS |
| Production JAR smoke | Temporary PostgreSQL/Redis, health, auth, minimal career, shutdown | PASS |
| Frontend production | Build passed; artifact inspection passed; no test harness in production dist | PASS |
| Firebase config | SPA rewrite and cache headers prepared without project ID | PASS |
| Docker smoke | Docker CLI unavailable locally | BLOCKED EXTERNALLY |

## Test status

| Suite | Result |
|---|---|
| Backend test-compile | PASS |
| Backend full suite | 2568 tests, 0 failures, 0 errors, 4 skipped |
| Frontend encoding guard | PASS, 385 files scanned |
| Frontend development build | PASS |
| Frontend production build | PASS |
| Frontend artifact inspection | PASS, 52 files scanned |
| Frontend tests | 1029 SUCCESS, 0 failures, 2 skipped |

Docker-specific evidence is intentionally not invented because Docker is unavailable on this workstation.

## Production JAR smoke evidence

- Runner: `tools/run-production-jar-smoke.ps1`
- JAR: `football-manager-1.0.0.jar`
- JAR bytes: `42,467,597`
- SHA-256: `F7C0C609A97418C9FFDE36821CE5CD7EA0562C32B74EAA6504C9BF1DC08F9FB1`
- Port: `61012`
- Liveness: `200`
- Readiness: `200`
- Register/login: PASS
- Minimal game/career: PASS
- Flyway successful migrations: `1`
- Local log artifacts: `0`
- Shutdown: PASS

## P0 status

Closed for PB1.2.1:

- backend runtime artifact exists;
- frontend hosting artifact exists;
- runtime contract exists;
- production artifact inspection exists;
- production JAR smoke is green;
- documentation is honest about remaining gates.

Remaining outside PB1.2.1:

- Docker image build/smoke on a Docker-capable machine;
- cloud resource creation;
- cloud health and shutdown drills;
- SSE behind edge/proxy;
- backup/restore and Redis durability drills.

## Final decision

The implementation is complete for PB1.2.1, but Docker smoke is externally blocked by missing Docker on this workstation. The next phase can proceed only after Docker-capable validation or a cloud/build-runner smoke.
