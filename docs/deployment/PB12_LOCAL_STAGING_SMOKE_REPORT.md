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
- JAR size: `42,487,084` bytes
- Dockerfile: present
- `.dockerignore`: present
- Docker image: not built locally because Docker is unavailable
- Image size: not measurable locally because Docker is unavailable

## Static validation

| Area | Result |
|---|---|
| Dockerfile uses explicit tags | PASS |
| Final runtime non-root user | PASS (`manager`) |
| Profile defaults to `prod` | PASS |
| Port configurable through `PORT` | PASS |
| UTC/UTF-8 configured | PASS |
| `.env` excluded from Docker context | PASS |
| logs/backups/dumps excluded | PASS |
| frontend `node_modules` and `dist` excluded | PASS |
| healthcheck targets liveness | PASS |

## Direct JAR smoke

A direct Windows JAR smoke was attempted with ephemeral PostgreSQL and Redis. PostgreSQL started, but the script did not reach application startup before the terminal timeout. No result was invented from this run. This does not replace the required Docker smoke.

Useful finding: local PostgreSQL temporary startup works, but the direct smoke harness needs simplification before it can serve as a reliable Windows-only preflight.

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
