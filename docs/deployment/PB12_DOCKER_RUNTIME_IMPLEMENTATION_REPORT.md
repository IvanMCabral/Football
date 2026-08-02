# PB1.2.2 Docker Runtime Implementation Report

Date: 2026-08-02

## Implementation

Added a reproducible Docker smoke runner and GitHub Actions workflow. The runner creates only unique, disposable resources, generates no reusable credentials, builds manager-backend:pb12, inspects the image, executes the production profile, performs the lifecycle/restart/readiness matrix and emits a machine-readable result. The workflow retains sanitized evidence for seven days.

## Local evidence

The existing Windows JAR smoke and full backend/frontend suites remain green. Docker is unavailable on this workstation, therefore no local image or container result is claimed.

## Remote evidence

GitHub Actions run [#22](https://github.com/IvanMCabral/Football/actions/runs/30755616038) passed on commit `3ee8d2a4` in 2m19s. Its sanitized artifact is `pb12-docker-smoke-30755616038` (88.2 KB; digest `sha256:23cfd415d8a0521d5cbf04b64b48ec02ead4e40050610e404dab789ef3b52997`). The result recorded image ID `sha256:f5e3853cfecd62f78967578efd1dbe88aacb1401cf453a789d5a406c9849b372`, size 258,908,085 bytes, non-root `manager` UID 122, Java PID 1, healthy Docker healthcheck, liveness/readiness 200/200, register/login/me, career creation/recovery, Flyway 1/1, second startup, graceful docker stop, no docker kill, marker ordering, 2,505 ms shutdown, exit 143, 503 readiness while Redis/PostgreSQL were down, zero residual resources, and verified cleanup. SBOM generation passed; Trivy reported 0 critical and 3 high findings.

## Limitations

This work does not deploy to Cloud Run/Firebase, configure a domain, provision managed PostgreSQL/Redis or perform cloud backup/restore. Those remain separate PB1.2.3/cloud gates.
