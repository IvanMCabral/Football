# PB1.2.1 Container Security

Date: 2026-08-01

## Implemented artifact controls

- Final runtime image uses `eclipse-temurin:21.0.8_9-jre-jammy`, not `latest`.
- Build stage uses `maven:3.9.11-eclipse-temurin-21`, not `latest`.
- `curl`, `ca-certificates` and `tzdata` are installed explicitly and apt cache is removed.
- Runtime process runs as non-root user `manager`.
- Runtime working directory is `/app`.
- Only the packaged JAR is copied from the build stage to the final image.
- `.dockerignore` excludes `.env`, logs, backups, dumps, Git metadata, frontend build output, `node_modules`, screenshots, temporary files and common secret material.
- No production secret value is present in `Dockerfile`.
- No debug port, remote JMX port or heap dump path is configured.
- Healthcheck targets the custom liveness endpoint with explicitly installed `curl`.
- JVM exits on OOM instead of limping in an undefined state.
- Production Logback writes to console only; no file appender or Windows path remains in runtime resources.

## Not yet validated locally

Docker is unavailable on this workstation, so the following must be executed where Docker exists:

- image build;
- image rebuild reproducibility check;
- final image size;
- runtime UID/GID inspection;
- filesystem layer inspection for `.env` or secrets;
- package vulnerability scan;
- `docker stop` graceful shutdown drill.

## Expected cloud posture

- Secrets supplied through provider environment/secret manager.
- Logs captured by provider stdout/stderr collector.
- No writable persistent volume mounted.
- No privileged mode.
- No extra Linux capabilities.
- HTTPS terminated at the selected edge/provider.

## Open security gates

The absence of Docker locally blocks only container smoke evidence, not the static artifact. PB1.2.2 must perform the image inspection and provider security checks before public staging.
