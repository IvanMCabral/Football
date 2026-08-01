# PB1.2.1 Runtime P0 Remediation

Date: 2026-08-01

Historical audit preserved separately:

- `docs/deployment/PB12_PRODUCTION_RUNTIME_ARTIFACTS_INDEPENDENT_AUDIT.md`
- `docs/deployment/PB12_RUNTIME_P0_DEFINITIVE_INDEPENDENT_AUDIT.md`
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
- fails if the minimal game/career is not created;
- verifies Flyway successful migration count is exactly `1`;
- shuts down the first backend with a Windows console control event;
- starts the same JAR a second time against the same temporary DB;
- verifies Flyway is not reapplied incorrectly;
- verifies login and `/auth/me` still work after restart;
- shuts down the second backend with the same graceful signal mechanism;
- verifies no force kill was used in PASS;
- verifies Java/helper processes and app ports have zero residuals before dependency cleanup;
- checks no local log artifact was created or changed;
- stops the Java process by graceful console control event, not `Stop-Process`;
- stops PostgreSQL and Redis.

Observed PASS:

```json
{"status":"PASS","jar":"football-manager-1.0.0.jar","jarBytes":42467597,"port":58016,"run1PortMode":"PORT","run2Port":64852,"run2PortMode":"SERVER_PORT","javaPid":28192,"javaPidRun2":44996,"postgresPid":47832,"redisPid":50588,"startupDurationMs":6661,"startupDurationMsRun2":6543,"liveness":200,"readiness":200,"registered":true,"login":true,"me":true,"careerCreated":true,"flywaySuccessfulMigrations":1,"secondStartup":true,"gracefulSignalSent":true,"gracefulShutdownObserved":true,"forceKillUsed":false,"shutdownDurationMs":2712,"shutdownDurationMsRun2":2711,"javaExitCode":130,"javaExitCodeRun2":130,"shutdownMarkersObserved":4,"residualProcesses":0,"residualPorts":0,"localLogArtifacts":0}
```

### 4. Definitive graceful shutdown evidence

Closed.

Created:

- `tools/GracefulProcessGroupRunner.cs`

The helper is tooling only. It is compiled into the temporary smoke workspace with PowerShell `Add-Type`, starts the JAR in a Windows process group with a dedicated console, attaches to that console and sends a console control event. This is the Windows equivalent of asking a foreground Java/Spring process to terminate gracefully. It is not `Stop-Process`, `taskkill /F` or `Process.destroyForcibly`.

The runner distinguishes:

- graceful signal sent;
- graceful shutdown observed;
- force kill fallback.

For PASS:

- `gracefulSignalSent=true`;
- `gracefulShutdownObserved=true`;
- `forceKillUsed=false`.

The observed run met all three conditions in both startup/shutdown cycles.

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
