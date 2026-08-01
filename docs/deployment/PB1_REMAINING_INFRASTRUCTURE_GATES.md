# PB1 Remaining Infrastructure Gates

Date: 2026-08-01

These gates remain outside PB1.1 by explicit scope and are required for PB1.2 Internet runtime. PB1.1 blocker remediation closed the application-level P0 findings; this document tracks infrastructure work only:

- Docker/buildpack/cloud runtime artifact.
- CI/CD pipeline and deploy gates.
- Managed PostgreSQL backup automation.
- Restore drill with measured RTO/RPO.
- Managed Redis provider with persistence/export policy.
- Redis loss/reconnect drill.
- SSE validation behind the selected proxy/CDN.
- Cloud graceful shutdown drill.
- Production domain, HTTPS, HSTS and CSP finalization.

These items do not reopen PB1.1 hardening if documented honestly; they block PB1.2 public runtime until completed.

---

## 2026-08-01 PB1.2.1 runtime artifacts

PB1.2.1 added local production runtime artifacts without creating cloud resources:

- backend Dockerfile and `.dockerignore`;
- frontend Firebase Hosting configuration;
- production artifact inspection for the Angular build;
- runtime environment contract;
- container security notes;
- local staging smoke report;
- remaining cloud gates.

Docker is not installed on the current workstation, so Docker build/run smoke is still an infrastructure gate. The artifact exists, but image size, runtime UID inspection and `docker stop` graceful shutdown evidence must be collected on a Docker-capable machine or build runner.

Remaining PB1.2 gates after PB1.2.1:

- Docker image build/smoke.
- Managed PostgreSQL staging.
- Managed Redis staging with TLS and persistence policy.
- Cloud health/readiness validation.
- SSE validation behind the selected proxy/CDN.
- PostgreSQL backup/restore drill.
- Redis loss/reconnect drill.
- Provider graceful shutdown drill.
- CI/CD and rollback.

---

## 2026-08-01 definitive test runtime closure

The blocker closure rejection has been remediated in a later change set. See:

- `docs/deployment/PB1_TEST_RUNTIME_REPRODUCIBILITY_REMEDIATION.md`
- `docs/deployment/PB1_TEST_RUNTIME_REPRODUCIBILITY_FINAL_REVIEW.md`

Current reproducibility evidence:

- Backend compile: PASS.
- Backend full suite run 1: 2564 tests, 0 failures, 0 errors, 4 skipped.
- Backend full suite run 2: 2564 tests, 0 failures, 0 errors, 4 skipped.
- Frontend: encoding guard PASS, development build PASS, production build PASS, 1029 SUCCESS, 0 failures, 2 skipped.
- Test infrastructure: no `.env`, no manually started PostgreSQL, no manually started Redis; local PostgreSQL and Redis binaries are launched as ephemeral test processes.
- Redis durability remains a PB1.2 infrastructure gate, not a closed PB1.1 durability claim.
