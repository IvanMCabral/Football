# PB1 Remaining Infrastructure Gates

Date: 2026-07-31

These gates remain outside PB1.1 by explicit scope and are required for PB1.2 Internet runtime:

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
