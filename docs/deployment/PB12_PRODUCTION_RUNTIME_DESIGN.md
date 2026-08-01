# PB1.2.1 Production Runtime Design

Date: 2026-08-01

Scope: production runtime artifacts only. No gameplay, simulation, dataset, deployment, cloud resource, CI/CD or infrastructure provisioning changes were made.

## Runtime shape

- Backend: Spring Boot WebFlux packaged as a Java 21 executable JAR and containerized with a multi-stage Dockerfile.
- Frontend: Angular production static artifact prepared for Firebase Hosting.
- PostgreSQL: external managed database, reached through JDBC for Flyway and R2DBC for runtime.
- Redis: external managed Redis, reached through Lettuce with password, optional ACL username and TLS support.
- Logs: stdout/stderr only in production runtime.
- Durable storage: PostgreSQL and Redis. The container filesystem is treated as ephemeral.

## Backend container

The backend Dockerfile uses:

- build image: `maven:3.9.11-eclipse-temurin-21`;
- runtime image: `eclipse-temurin:21.0.8_9-jre-jammy`;
- non-root runtime user: `manager`;
- working directory: `/app`;
- copied runtime artifact: `/app/app.jar`;
- default profile: `prod`;
- default `PORT`: `8080`, overridable by the platform;
- UTC timezone and UTF-8 encoding;
- JVM memory percentages instead of fixed heap sizes;
- `exec java ...` entrypoint so the Java process receives termination signals.

The runtime base intentionally favors Debian/Ubuntu compatibility over Alpine size optimization for the first public beta. `curl`, `ca-certificates` and `tzdata` are installed explicitly for healthcheck, TLS and timezone predictability.

Docker is not installed on this workstation, so the image was validated statically and the actual image build/smoke remains an external local tooling blocker for this phase.

## Cloud runtime contract

The backend is expected to run in a platform such as Cloud Run with:

- one inbound HTTP port supplied through `PORT`;
- no additional inbound ports;
- outbound PostgreSQL and Redis connectivity;
- production secrets supplied by the provider, never by files in the image;
- readiness check on `/api/v1/health/readiness`;
- liveness check on `/api/v1/health/liveness`;
- logs collected from stdout/stderr;
- graceful shutdown timeout compatible with `SHUTDOWN_TIMEOUT`, default `30s`.

## JVM memory guidance

The image default is:

```text
JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75 -XX:InitialRAMPercentage=20 -XX:+ExitOnOutOfMemoryError -Dfile.encoding=UTF-8 -Duser.timezone=UTC -Djava.security.egd=file:/dev/./urandom
```

Recommended starting points:

| Memory | Guidance |
|---|---|
| 512 MiB | Minimum beta smoke only. Watch startup, detailed match memory and GC pressure. |
| 1 GiB | Preferred first staging size. Enough headroom for WebFlux, Flyway, Redis/R2DBC pools and match simulations. |
| 2 GiB | Safer public beta size if concurrent live matches or larger imports are tested. |

## Health behavior

- `/api/v1/health/liveness`: returns `200` with `UP` and does not depend on DB/Redis.
- `/api/v1/health/readiness`: returns `200` when PostgreSQL and Redis are available, otherwise `503`.
- `/api/v1/health`: delegates to readiness.
- `/actuator/health`: exposed, but custom health endpoints are the preferred cloud contract.

## Flyway behavior

Flyway runs on startup using the configured PostgreSQL database. It is fail-fast, uses `baseline-on-migrate=false`, does not repair automatically and does not delete data. Multiple instances must rely on Flyway database locking; first deploy and rollback drills remain PB1.2 cloud gates.

## Frontend runtime

The Angular production build outputs to `dist/demo/browser`. Production build evidence showed hashed assets, no public source maps, and no debug/test-harness lazy chunk. Firebase Hosting is configured as a static SPA with `index.html` no-cache and hashed assets immutable.

## SSE and proxy status

SSE is implemented in the application, but it has not yet been certified behind Cloud Run, Firebase Hosting or a CDN. PB1.2.2 must validate buffering, idle timeout, authentication, reconnect behavior and CORS at the selected edge.

## Verdict for this design

The runtime design is ready for local Docker validation and then cloud staging provisioning. The production JAR smoke is now green with isolated PostgreSQL/Redis, health, Flyway, auth, minimal career creation and shutdown. It is not yet Internet-deployed and does not claim cloud SSE, backup/restore or rollback certification.
