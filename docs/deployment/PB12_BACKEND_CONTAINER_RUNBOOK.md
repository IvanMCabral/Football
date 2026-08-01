# PB1.2.1 Backend Container Runbook

Date: 2026-08-01

## Build

When Docker is available:

```powershell
Set-Location D:\ProyectosOpenCode\MANAGER
docker build -t manager-backend:pb1.2.1 .
```

Expected behavior:

- Maven builds the JAR inside the build stage.
- The final image contains only the JRE, non-root user, `/app/app.jar` and runtime metadata.
- `.env`, logs, backups, dumps, frontend `node_modules`, frontend `dist`, Git history and local captures are excluded by `.dockerignore`.
- The runtime base is Jammy, not Alpine, to reduce first-beta TLS/DNS/native-library uncertainty.

## Run

Example with provider-style environment variables:

```powershell
docker run --rm `
  -p 8080:8080 `
  -e SPRING_PROFILES_ACTIVE=prod `
  -e PORT=8080 `
  -e SERVER_ADDRESS=0.0.0.0 `
  -e DB_HOST=... `
  -e DB_PORT=5432 `
  -e DB_NAME=football_manager `
  -e DB_USER=... `
  -e DB_PASSWORD=... `
  -e REDIS_HOST=... `
  -e REDIS_PORT=6379 `
  -e REDIS_USERNAME=... `
  -e REDIS_PASSWORD=... `
  -e REDIS_SSL=true `
  -e JWT_SECRET=... `
  -e APP_CORS_ALLOWED_ORIGINS=https://beta.example.com `
  manager-backend:pb1.2.1
```

Do not paste real secrets into shared terminals or documentation.

## Checks

```powershell
Invoke-WebRequest http://localhost:8080/api/v1/health/liveness
Invoke-WebRequest http://localhost:8080/api/v1/health/readiness
```

Expected:

- liveness: `200`;
- readiness: `200` when PostgreSQL and Redis are reachable, otherwise `503`;
- no internal details in production responses.

## Port contract

The application binds through Spring's `server.port=${PORT:${SERVER_PORT:8080}}`. The Dockerfile also sets `SERVER_ADDRESS=0.0.0.0`; cloud platforms should supply `PORT`.

## Shutdown drill

When Docker is available:

```powershell
docker stop --time 35 <container-id>
```

Expected:

- SIGTERM reaches Java because the entrypoint uses `exec`;
- Spring graceful shutdown begins;
- Redis and PostgreSQL clients close;
- the process exits before the platform timeout.

## Current local limitation

Docker is not installed in the current workstation:

```text
docker: command not found / not recognized
```

Therefore the container image size, runtime UID inspection and `docker stop` drill are PB1.2.1 external smoke items, not fabricated results.

## JAR preflight without Docker

Before Docker is available, run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/run-production-jar-smoke.ps1
```

This validates the packaged production JAR with temporary PostgreSQL/Redis, random `PORT`, `SERVER_ADDRESS=0.0.0.0`, liveness, readiness, Flyway, auth, minimal career creation, a second startup against the same temporary DB, and graceful shutdown through a Windows console control event. PASS requires `gracefulSignalSent=true`, `gracefulShutdownObserved=true` and `forceKillUsed=false`.

This proves Spring/JAR graceful shutdown on Windows. It does not prove container PID 1 or `docker stop`; that remains a Docker-capable runner gate.
