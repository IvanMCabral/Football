# PB1.2.2 Docker Smoke Execution Report

Date: 2026-08-02

## Implementation

The reproducible runner is tools/run-pb12-docker-smoke.sh, invoked by .github/workflows/pb12-docker-smoke.yml. It builds the real image from the checked-out Dockerfile, starts disposable PostgreSQL and authenticated Redis containers, runs the backend in prod, executes the lifecycle and negative-readiness matrix, generates SBOM/Trivy evidence and uploads sanitized artifacts.

## Local execution

Docker is not available on the workstation (docker is not on PATH), so image build, container inspection, PID 1 and docker stop have not been claimed locally. The pre-existing production JAR smoke remains green and is not a substitute for container evidence.

## Remote execution

Remote GitHub Actions execution is intentionally recorded only after a real run is dispatched and observed. Until then:

- workflow run ID: pending;
- image build: not executed remotely;
- container health/PID 1: pending;
- auth/career/restart/Flyway: pending;
- graceful docker stop: pending;
- readiness-negative matrix: pending;
- SBOM and vulnerability counts: pending.

This report must be updated with the actual run URL, artifact name and result JSON; no values are pre-filled.
