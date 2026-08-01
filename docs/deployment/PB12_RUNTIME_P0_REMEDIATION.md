# PB1.2.1 Runtime P0 Remediation

Date: 2026-08-01

Historical audit preserved separately:

- `docs/deployment/PB12_PRODUCTION_RUNTIME_ARTIFACTS_INDEPENDENT_AUDIT.md`
- Historical verdict: `PB1.2.1 PRODUCTION RUNTIME REJECTED`

This document records the remediation after that rejected audit. It does not rewrite historical evidence.

## P0 findings closed

### 1. Production logging incompatible with Linux/container

Closed.

`src/main/resources/logback.xml` now uses console output only:

- no `RollingFileAppender`;
- no `FileAppender`;
- no absolute Windows path;
- no `D:/` or `C:/`;
- no local `logs/` output;
- root log level defaults to `INFO`;
- app log level defaults to `INFO`;
- both are configurable through `LOG_LEVEL_ROOT` and `LOG_LEVEL_APP`;
- UTF-8 encoder;
- request/correlation ID included with `%X{requestId:-no-request-id}`.

Static guard:

- `ProductionRuntimeArtifactGuardTest.logbackUsesConsoleOnlyAndDoesNotReferenceLocalFiles`

Runtime smoke evidence:

- production JAR smoke created `0` local log artifacts.

### 2. Docker healthcheck depends on an undeclared tool

Closed statically.

The runtime base was changed from Alpine to Jammy:

- previous: `eclipse-temurin:21.0.8_9-jre-alpine`;
- current: `eclipse-temurin:21.0.8_9-jre-jammy`.

`curl`, `ca-certificates` and `tzdata` are installed explicitly with `--no-install-recommends`, and apt cache is removed.

The Docker healthcheck now uses:

```text
curl --fail --silent --show-error --max-time 3 http://127.0.0.1:${PORT}/api/v1/health/liveness
```

Docker is still unavailable locally, so the healthcheck is not claimed as image-executed yet.

### 3. Missing real production JAR smoke

Closed.

Created:

- `tools/run-production-jar-smoke.ps1`

The smoke:

- builds the JAR unless `-SkipBuild` is used;
- starts a temporary PostgreSQL process with a random TCP port;
- creates a safe non-default DB user and DB;
- starts a temporary Redis process with password auth;
- does not load `.env`;
- uses `SPRING_PROFILES_ACTIVE=prod`;
- uses a random non-standard `PORT`;
- sets `SERVER_ADDRESS=0.0.0.0`;
- uses a 96-byte random JWT secret encoded as Base64;
- imports the three-league dataset into the temporary DB only;
- verifies liveness `200`;
- verifies readiness `200`;
- registers a user;
- logs in;
- creates a minimal game/career;
- verifies Flyway successful migration count;
- checks no local log artifact was created or changed;
- terminates the Java process;
- stops PostgreSQL and Redis.

Observed PASS:

```json
{"status":"PASS","jar":"football-manager-1.0.0.jar","jarBytes":42467597,"port":61012,"serverAddress":"0.0.0.0","liveness":200,"readiness":200,"registered":true,"login":true,"userId":"a4298c30-732d-4265-ab7e-d4c09fafea2f","careerCreated":true,"flywaySuccessfulMigrations":1,"localLogArtifacts":0,"shutdownMs":50,"postgresTemp":true,"redisTemp":true}
```

## P1 directly related

### Runtime base image

Closed by choosing a conservative Debian/Ubuntu-based Temurin runtime.

Reason:

- lower musl/glibc uncertainty;
- predictable CA/DNS/TLS behavior;
- explicit `curl` healthcheck tooling;
- better first public-beta troubleshooting trade-off than Alpine size optimization.

### `server.address`

Closed.

`application.yaml` now includes:

```yaml
server:
  address: ${SERVER_ADDRESS:0.0.0.0}
  port: ${PORT:${SERVER_PORT:8080}}
```

### Redis SSL contract

Closed.

Official variable remains:

```text
REDIS_SSL
```

`REDIS_SSL_ENABLED` is no longer documented as an alternate public contract.

### Docker context

Improved.

`.dockerignore` now excludes:

- `docs/`;
- root markdown docs;
- full `front-ciber/`;
- logs;
- backups;
- dumps;
- screenshots/media;
- temp files;
- test reports;
- previous `target`.

Approximate required backend context from `pom.xml + src`: `12,678,689` bytes across `1027` files.

### JAR reproducibility

Closed.

Added:

```xml
<project.build.outputTimestamp>2026-08-01T00:00:00Z</project.build.outputTimestamp>
```

Two clean builds produced the same artifact:

- SHA-256: `F7C0C609A97418C9FFDE36821CE5CD7EA0562C32B74EAA6504C9BF1DC08F9FB1`
- size: `42,467,597` bytes

## Not closed in this phase

Docker image build/run/inspect is still blocked by missing Docker on this workstation. No image size or container UID runtime claim is invented.
