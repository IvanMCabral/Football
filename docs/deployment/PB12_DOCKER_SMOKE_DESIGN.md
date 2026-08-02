# PB1.2.2 Docker Smoke Design

Date: 2026-08-02

## Scope

This runner validates the production backend image without changing gameplay, simulation, datasets or cloud resources. It is intentionally limited to a disposable GitHub Actions Docker network.

## Topology

- Backend: image built from the repository Dockerfile, bound to port 8080 in the container.
- PostgreSQL: official postgres:16.4-alpine, disposable container with a job-generated password.
- Redis: official redis:7.2.5-alpine, disposable authenticated container with the default ACL user.
- Network: unique manager-pb12-* network per workflow run.
- No bind mounts, persistent database volumes or provider credentials.

## Backend contract

The backend runs with profile prod, SERVER_ADDRESS=0.0.0.0, PORT=8080, a 64-byte-plus random JWT secret, explicit database/Redis variables and APP_CORS_ALLOWED_ORIGINS=http://localhost:4200. The first startup enables the existing three-league importer to make the disposable database usable through public APIs; the restart does not re-import.

The container uses a non-root user, 768 MiB memory, a read-only root filesystem and a tmpfs for /tmp. The Dockerfile healthcheck calls only liveness, so dependency failures are tested separately through readiness.

## Gates

The script validates image metadata and filesystem contents, non-root UID, PID 1, Docker HEALTHCHECK, liveness/readiness, register/login/me, league/team lookup, career creation, Flyway stability, restart recovery, Redis-down and PostgreSQL-down readiness, stdout logging, no unexpected writes, graceful docker stop, and fail-closed cleanup.

The result is written as target/pb12-docker-smoke-result.json. Its values are derived from Docker, HTTP responses, PostgreSQL queries and process state; the runner never fabricates a PASS result.

## Out of scope

Cloud Run, Firebase, managed services, domain/HTTPS configuration, backup/restore drills and deployment are PB1.2.3/cloud gates and are not performed by this workflow.
