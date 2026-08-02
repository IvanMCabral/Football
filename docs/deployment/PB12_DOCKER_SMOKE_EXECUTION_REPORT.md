# PB1.2.2 Docker Smoke Execution Report

Date: 2026-08-02

## Result

The remote Docker smoke is green on GitHub Actions run [#22](https://github.com/IvanMCabral/Football/actions/runs/30755616038), commit `3ee8d2a4`, branch `feat/v25d99.20.3.1-runtime-fixes`. The run completed successfully in 2m19s and uploaded the sanitized artifact `pb12-docker-smoke-30755616038` (88.2 KB; artifact digest `sha256:23cfd415d8a0521d5cbf04b64b48ec02ead4e40050610e404dab789ef3b52997`).

## Runner

The reproducible runner is `tools/run-pb12-docker-smoke.sh`, invoked by `.github/workflows/pb12-docker-smoke.yml`. It builds the checked-out Dockerfile, starts disposable PostgreSQL and authenticated Redis containers, runs the backend with the production profile, executes lifecycle/restart/negative-readiness checks, generates SBOM and Trivy evidence, and uploads sanitized artifacts. Docker is unavailable on the workstation, so the image evidence below is exclusively remote and is not represented as local evidence.

## Remote result JSON

The workflow published the following result summary as a GitHub Actions notice:

| Gate | Observed value | Result |
|---|---:|---|
| Status | `PASS` | PASS |
| Image ID | `sha256:f5e3853cfecd62f78967578efd1dbe88aacb1401cf453a789d5a406c9849b372` | PASS |
| Image size | 258,908,085 bytes | INFO |
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
| Shutdown duration / exit code | 2,505 ms / `143` | PASS |
| Redis-down readiness | `503` | PASS |
| PostgreSQL-down readiness | `503` | PASS |
| Residual containers / networks | `0` / `0` | PASS |
| Cleanup verified | `true` | PASS |

## Image security evidence

- SBOM: generated with Anchore SBOM Action and retained in the run artifact.
- Trivy scan: `critical=0`, `high=3`, `ignore-unfixed=true`.
- The three remaining high findings are reported by the workflow as current Spring Data/Spring Expression/Spring WebFlux advisories. They do not fail the PB1.2.2 gate, which fails only on fixable critical vulnerabilities, and remain visible for dependency maintenance.
- No credentials are emitted by the runner, result JSON, annotations or artifact names.

## Local validation

- `mvn -q -DskipTests test-compile`: PASS.
- `mvn -q test`: PASS, 2,778 tests, 0 failures, 0 errors, 4 skipped. The suite is larger than the earlier 2,571-test baseline because subsequent runtime/security tests are now included; no tests were removed or disabled.
- Docker CLI/daemon: unavailable locally; no local Docker claim is made.

## Conclusion

The remote image build, production runtime, authentication/career path, Flyway idempotency, restart, PID 1, graceful stop, dependency scan and cleanup gates passed. Cloud deployment, managed services, backup/restore and edge/SSE validation remain PB1.2.3/cloud gates and are not inferred from this runner.
