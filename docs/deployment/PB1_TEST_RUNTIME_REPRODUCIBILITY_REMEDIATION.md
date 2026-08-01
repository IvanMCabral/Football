# PB1.1 Test Runtime Reproducibility Remediation

**Date:** 2026-08-01
**Scope:** final remediation of backend test runtime, Redis readiness semantics, readiness coverage, and HTTP error contract evidence for PB1.1.

## Result

PB1.1 blocker remediation is reproducible locally without `.env`, without a manually started PostgreSQL service, and without a manually started Redis service.

The backend suite now starts isolated test infrastructure automatically from the test runtime:

- PostgreSQL: ephemeral local process started by `PostgresTestEnvironmentPostProcessor` using local PostgreSQL binaries.
- Redis: ephemeral local process started by `RedisTestEnvironmentPostProcessor` using the local `redis-server` binary.
- Redis logical DB: test Spring context uses DB 15.
- Redis DB isolation: test coverage proves DB 15 and DB 0 do not share sentinel keys.
- Flyway: enabled in integration tests against the ephemeral PostgreSQL instance.

Docker/Testcontainers was evaluated but is not available on this workstation (`docker` command not installed). The selected remediation avoids invented credentials and avoids requiring the developer to pre-start services.

## External requirements for tests

The backend tests require installed local binaries, not running services or secrets:

- Java 21.
- Maven.
- PostgreSQL binaries available locally (`initdb`, `postgres`).
- Redis server binary available locally (`redis-server`).

No `.env` file is loaded by the test suite. No production PostgreSQL or Redis instance is used.

## PostgreSQL test isolation

`PostgresTestEnvironmentPostProcessor` creates a temporary PostgreSQL data directory, chooses a random local port, creates ephemeral credentials, and injects JDBC/R2DBC/Flyway properties into the Spring test context before startup.

The importer and baseline contract tests that previously depended on external database variables now use the same ephemeral PostgreSQL runtime directly.

## Redis test isolation

`RedisTestEnvironmentPostProcessor` starts Redis on a random local port with an ephemeral password and no persistence. The Spring test profile is wired to DB 15.

`TestRuntimeIsolationIntegrationTest` verifies:

- application Redis database is 15;
- a DB 15 sentinel is not visible from DB 0;
- a DB 0 sentinel is not visible from DB 15;
- cleanup removes both sentinels.

## Redis readiness cleanup semantics

`RedisHealthProbe` now treats SET/GET correctness as the health signal. Cleanup failure after successful SET/GET is logged but does not mark readiness DOWN, preventing false negative readiness when Redis is available but cleanup is transiently interrupted.

Tests cover:

- cleanup failure after successful probe remains UP;
- wrong readback remains DOWN;
- integration probe leaves no health-check garbage keys.

## Readiness matrix

`HealthControllerReadinessMatrixTest` covers the readiness combinations:

| DB | Redis | HTTP | Status |
| --- | --- | --- | --- |
| UP | UP | 200 | UP |
| DOWN | UP | 503 | DOWN |
| UP | DOWN | 503 | DOWN |
| DOWN | DOWN | 503 | DOWN |

Liveness remains independent and returns UP without checking DB/Redis.

## HTTP error contract

`GlobalExceptionHandler` now centralizes safe responses for expected application errors, authentication/authorization failures, response status errors, and unexpected exceptions.

Production tests verify that sensitive exception text such as internal URLs, driver names, local paths, and raw internal messages is not returned to the client.

## Backend evidence

- `mvn -q -DskipTests test-compile`: PASS.
- `mvn -q test` run 1: PASS, 2564 tests, 0 failures, 0 errors, 4 skipped, approximately 238.7 seconds.
- `mvn -q test` run 2: PASS, 2564 tests, 0 failures, 0 errors, 4 skipped, approximately 238.7 seconds.

Both full backend runs were executed after clearing DB/Redis environment variables from the PowerShell session.

## Frontend evidence

- `node tools/check-visible-text-encoding.mjs`: PASS, 385 files scanned.
- `npm run build -- --configuration development`: PASS.
- `npm run build`: PASS.
- `npm test -- --watch=false --browsers=ChromeHeadless`: PASS, 1029 SUCCESS, 0 failures, 2 skipped.

## Redis durability status

Redis remains runtime-critical for several flows. PB1.1 now validates configuration and test isolation honestly; it does not claim that Redis durability, managed backup, export, or restore drill is solved. That remains a PB1.2 infrastructure gate.
