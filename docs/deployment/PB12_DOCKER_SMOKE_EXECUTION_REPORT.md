# PB1.2.2 Docker Smoke Execution Report

Date: 2026-08-02

## Result

The previous remote Docker smoke is historical evidence from GitHub Actions run [#24](https://github.com/IvanMCabral/Football/actions/runs/30769400337), commit `87e28c8f`, branch `feat/v25d99.20.3.1-runtime-fixes`. The run completed successfully in 2m05s and uploaded the sanitized artifact `pb12-docker-smoke-30769400337` (88.9 KB; artifact digest `sha256:70d557c0309a5f0bc64daa8c71ba160f843cceb21d99e7cc7dfd76afd0947bd0`). A new exact-HEAD run is required after the manifest remediation.

## Runner

The reproducible runner is `tools/run-pb12-docker-smoke.sh`, invoked by `.github/workflows/pb12-docker-smoke.yml`. It builds the checked-out Dockerfile, starts disposable PostgreSQL and authenticated Redis containers, runs the backend with the production profile, executes lifecycle/restart/negative-readiness checks, generates SBOM and Trivy evidence, and uploads sanitized artifacts. Docker is unavailable on the workstation, so the image evidence below is exclusively remote and is not represented as local evidence.

## Remote result JSON

The workflow published the following result summary as a GitHub Actions notice:

| Gate | Observed value | Result |
|---|---:|---|
| Status | `PASS` | PASS |
| Image ID | `sha256:69c0787e75da31ebb907d42b0cac75e1c9b4244924607f02bf513e618337b203` | PASS |
| Image size | 258,928,412 bytes | INFO |
| Runtime user / UID | `manager` / `122` | PASS |
| Java PID 1 | `true` | PASS |
| Docker healthcheck | `true` | PASS |
| Liveness / readiness | `200` / `200` | PASS |
| Register / login / `/me` | `true` / `true` / `true` | PASS |
| Career created / recovered | `true` / `true` | PASS |
| Flyway successful migrations run 1 / run 2 | `1` / `1` | PASS |
| Second startup | `true` | PASS |
| `docker stop` / `docker kill` | `true` / `false` | PASS |
| Graceful shutdown / marker ordering | `true` / `true` | PASS |
| Shutdown duration / exit code | 2,507 ms / `143` | PASS |
| Redis-down readiness | `503` | PASS |
| PostgreSQL-down readiness | `503` | PASS |
| Residual containers / networks | `0` / `0` | PASS |
| Auth temp exists / cleanup verified | `false` / `true` | PASS |
| Final artifact secret scan / leaks | `true` / `0` | PASS |
| Cleanup verified | `true` | PASS |

## Image security evidence

- SBOM: generated with Anchore SBOM Action and retained in the run artifact.
- Trivy scan: `critical=0`, `high=0`, `ignore-unfixed=true`.
- The three Spring advisories from run #22 are absent after the Spring Boot 3.5.16 patch update.
- No credentials are emitted by the runner, result JSON, annotations or artifact names.

## Local validation

- `mvn -q -DskipTests test-compile`: PASS.
- Baseline `mvn -q test`: PASS, 2,572 tests, 0 failures, 0 errors, 4 skipped (Surefire text-report sum). A fresh count is required after the manifest tooling changes.
- Docker CLI/daemon: unavailable locally; no local Docker claim is made.

The historical artifact is published and its remote digest is recorded above. Direct ZIP download from this workstation returned HTTP 401 because GitHub authentication is unavailable; no local manifest/hash claim is made.

## Conclusion

The remote image build, production runtime, authentication/career path, Flyway idempotency, restart, PID 1, graceful stop, dependency scan and cleanup gates passed. Cloud deployment, managed services, backup/restore and edge/SSE validation remain PB1.2.3/cloud gates and are not inferred from this runner.
