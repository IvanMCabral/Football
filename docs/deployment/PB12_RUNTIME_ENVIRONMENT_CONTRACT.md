# PB1.2.1 Runtime Environment Contract

Date: 2026-08-01

## Required backend variables

| Variable | Required | Notes |
|---|---:|---|
| `SPRING_PROFILES_ACTIVE` | Yes | Must be `prod` in public staging/production. |
| `PORT` | Yes in cloud | Platform-provided HTTP port. Defaults to `8080` if absent. |
| `SERVER_ADDRESS` | Recommended | Use `0.0.0.0` in containers. |
| `DB_HOST` | Yes | Managed PostgreSQL host. |
| `DB_PORT` | Yes | PostgreSQL port. |
| `DB_NAME` | Yes | Database name. |
| `DB_USER` | Yes | Application database user. |
| `DB_PASSWORD` | Yes | Secret, never versioned. |
| `REDIS_HOST` | Yes | Managed Redis host. |
| `REDIS_PORT` | Yes | Redis port. |
| `REDIS_USERNAME` | Provider-specific | Optional ACL username. |
| `REDIS_PASSWORD` | Yes | Secret, required in prod. |
| `REDIS_SSL` | Yes in managed cloud | `true` for providers that require TLS. |
| `JWT_SECRET` | Yes | At least 64 UTF-8 bytes and not an unsafe known value. |
| `JWT_EXPIRATION` | Recommended | Positive, reasonable access token TTL. |
| `JWT_REFRESH_EXPIRATION` | Recommended | Positive, reasonable refresh token TTL. |
| `APP_CORS_ALLOWED_ORIGINS` | Yes | Explicit HTTPS origins only in production. |
| `APP_RATE_LIMIT_ENABLED` | Recommended | Keep enabled in production. |
| `SHUTDOWN_TIMEOUT` | Optional | Default `30s`. |
| `JAVA_OPTS` | Optional | Extra Java flags. |
| `JAVA_TOOL_OPTIONS` | Optional | Base image default already provides safe memory/encoding/timezone flags. |

## Filesystem contract

The production container must not rely on durable local disk. The application must treat local filesystem writes as temporary only. Durable game state remains in PostgreSQL/Redis according to the current PB1.1 architecture and Redis runtime strategy.

## Network contract

Inbound:

- exactly one HTTP port from `PORT`.

Outbound:

- PostgreSQL over TCP;
- Redis over TCP/TLS;
- no local filesystem, Windows path, or principal DB dependency.

## Observability contract

- Logs go to stdout/stderr.
- Request IDs are propagated in response headers and logs.
- Health endpoints expose no sensitive internals.
- Provider-level log retention and alerting are PB1.2.2 gates.

## Readiness gate

The application is ready only when both PostgreSQL and Redis probes succeed. Redis is not treated as optional in PB1.2.1.
