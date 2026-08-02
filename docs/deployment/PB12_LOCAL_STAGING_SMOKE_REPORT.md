# PB1.2.1 Local Staging Smoke Report

Date: 2026-08-01

## Tooling

| Check | Result |
|---|---|
| Java | 21.0.8 |
| Maven | 3.9.11 |
| Node | v24.9.0 |
| npm | 11.6.0 |
| Docker | Not installed / not recognized |

## Backend artifact

- JAR: `target/football-manager-1.0.0.jar`
- JAR size: `42,467,597` bytes
- Dockerfile: present
- `.dockerignore`: present
- Docker image: not built locally because Docker is unavailable
- Image size: not measurable locally because Docker is unavailable

## Static validation

| Area | Result |
|---|---|
| Dockerfile uses explicit tags | PASS |
| Final runtime non-root user | PASS (`manager`) |
| Conservative Jammy runtime base | PASS |
| Explicit curl healthcheck tool | PASS |
| Profile defaults to `prod` | PASS |
| Port configurable through `PORT` | PASS |
| `server.address` configurable through `SERVER_ADDRESS` | PASS |
| UTC/UTF-8 configured | PASS |
| `.env` excluded from Docker context | PASS |
| logs/backups/dumps excluded | PASS |
| frontend `node_modules` and `dist` excluded | PASS |
| healthcheck targets liveness | PASS |

## Production JAR smoke

| Check | Result |
|---|---|
| Temporary PostgreSQL | PASS |
| Temporary Redis with auth | PASS |
| `.env` not loaded | PASS |
| `prod` profile | PASS |
| Random non-standard `PORT` | PASS (`61012`) |
| `SERVER_ADDRESS=0.0.0.0` | PASS |
| Flyway V1 applied | PASS |
| Three-league import in temporary DB | PASS |
| Liveness | 200 |
| Readiness | 200 |
| Register/login | PASS |
| Minimal game/career | PASS |
| Local log artifacts | 0 |
| Shutdown drill | PASS, 50 ms |

Definitive graceful shutdown run:

| Check | Result |
|---|---|
| Run 1 port mode | `PORT` |
| Run 1 port | `50816` |
| Run 2 port mode | `SERVER_PORT` |
| Run 2 port | `50218` |
| Second startup against same DB | PASS |
| Graceful signal sent | `true` |
| Graceful shutdown observed | `true` |
| Force kill used | `false` |
| Signal used | `CTRL_C_EVENT`, `CTRL_C_EVENT` |
| Shutdown marker order | `true`, `true` |
| Shutdown durations | `2728 ms`, `2744 ms` |
| Java exit codes | `130`, `130` |
| PostgreSQL exit code | `0` |
| Redis exit code | `0` |
| Residual processes | `0` |
| Residual ports | `0` |
| PostgreSQL graceful stop | PASS, `pg_ctl stop -m fast`, exit `0` |
| Redis graceful stop | PASS, `SHUTDOWN NOSAVE`, exit `0` |
| Workspace exists after default PASS | `false` |
| Safe summary exists | `false` |
| Residual temp artifacts | `0` |

JAR SHA-256 after reproducible clean builds:

```text
F7C0C609A97418C9FFDE36821CE5CD7EA0562C32B74EAA6504C9BF1DC08F9FB1
```

## Direct JAR smoke

The direct Windows JAR smoke now passes with ephemeral PostgreSQL and Redis. It validates the packaged artifact, production profile startup, Flyway, health, auth, minimal career creation, absence of local log artifacts and shutdown. This still does not replace the required Docker smoke because Docker is unavailable on this workstation.

## Frontend artifact

| Check | Result |
|---|---|
| Development build | PASS |
| Production build | PASS |
| Production output | `dist/demo/browser` |
| Artifact inspection | PASS, 52 files scanned |
| Test harness absent from production artifact | PASS |
| Localhost absent from production artifact | PASS |
| Source map references absent | PASS |

## Suites

| Suite | Result |
|---|---|
| Backend `mvn -q -DskipTests test-compile` | PASS |
| Backend `mvn -q test` | 2571 tests, 0 failures, 0 errors, 4 skipped |
| Frontend encoding guard | PASS, 385 files scanned |
| Frontend development build | PASS |
| Frontend production build | PASS |
| Frontend artifact inspection | PASS, 52 files scanned |
| Frontend tests | 1029 SUCCESS, 0 failures, 2 skipped |

## Docker smoke status

Status: externally blocked on this workstation because `docker` is not installed.

Required follow-up when Docker is available:

1. Build image twice.
2. Run with isolated PostgreSQL and Redis.
3. Verify liveness/readiness.
4. Register/login minimal user.
5. Create minimal career.
6. Smoke SSE endpoint.
7. Stop container and verify graceful shutdown.
8. Inspect image user, size and absence of secrets.
