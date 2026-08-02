# PB1.2 Remaining Cloud Gates

Date: 2026-08-02

These gates are intentionally outside PB1.2.1. They must be closed before public staging/beta.

## PB1.2.2 Docker gate

PASS. The reproducible workflow `.github/workflows/pb12-docker-smoke.yml` and runner `tools/run-pb12-docker-smoke.sh` passed at the exact final HEAD in [GitHub Actions run #25](https://github.com/IvanMCabral/Football/actions/runs/30771583693). The run verified the approved Docker runtime, graceful lifecycle and Trivy result of 0 critical / 0 high. The workstation still has no Docker daemon, so the image result remains remote evidence.

## PB1.2.3 design handoff

The provider, topology, backup/restore, SSE, cost and release gates are documented in the `PB123_*` documents. No cloud resource was created. Provisioning remains a human-approved PB1.2.3A action.

## P0 before Internet exposure

- Choose and create managed PostgreSQL staging.
- Choose and create managed Redis staging with TLS and persistence policy.
- Configure production secrets in the provider.
- Run Flyway against staging and verify fail-fast behavior.
- Run liveness/readiness checks from the provider.
- Verify frontend API routing and production CORS with real domains.
- Validate SSE through the selected edge/proxy.
- Execute backup and restore drill for PostgreSQL.
- Execute Redis loss/reconnect drill and define RPO/RTO.
- Confirm container PID 1 and `docker stop` graceful shutdown behavior on the selected cloud runtime (the disposable Docker runner already passes this gate).

## P1 before broader beta

- Add CI/CD build and deploy gates.
- Add image vulnerability scanning.
- Add provider log retention and alerts.
- Add uptime checks.
- Add rollback drill.
- Add HSTS and final CSP after domain is stable.
- Add budget/cost alerts.

## Not blockers for PB1.2.1

- No cloud resource exists yet.
- No domain exists yet.
- No Docker smoke could run locally because Docker is absent; the remote PB1.2.2 smoke is green.
- No public deploy was performed.
- Production JAR smoke is green locally with temporary PostgreSQL/Redis, two startups, graceful Windows console shutdown, graceful PostgreSQL/Redis shutdown, fail-closed cleanup, executable negative lifecycle modes and zero residual processes/ports. Docker/PID 1 shutdown is also green in the remote PB1.2.2 run.
