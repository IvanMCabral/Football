# PB1.2.3 Cloud staging release checklist

## PB1.2.3A - Provider provisioning

- [ ] Billing/card decision accepted; budget alert configured.
- [ ] Firebase project and staging domain chosen.
- [ ] Cloud Run region and max-instance policy chosen.
- [ ] Neon project/role/TLS endpoint created.
- [ ] Upstash database/TLS/ACL/region and plan created.
- [ ] Registry chosen and immutable digest retention configured.

## PB1.2.3B - Backend staging

- [ ] Existing image digest deployed with `SPRING_PROFILES_ACTIVE=prod`.
- [ ] All variables supplied from provider secrets; no `.env` or literal secret.
- [ ] Flyway is successful and idempotent.
- [ ] Liveness is 200; readiness is 200 only with DB and Redis.
- [ ] CORS contains exact Firebase/custom origins only.
- [ ] Auth, career, lineup, fixture, standings and detailed match smoke pass.
- [ ] Logs contain request ID but no token/password/SQL internals.

## PB1.2.3C - Frontend staging

- [ ] Add staging build replacement for `apiUrl`; no localhost or relative API in deployed staging.
- [ ] Production artifact inspection has no debug/test-harness chunk or route.
- [ ] Firebase Hosting serves SPA routes, uncached index and immutable hashed assets.
- [ ] Browser smoke passes on login, squad, lineup, match and SSE.
- [ ] API absolute-origin CORS and credentials/token behavior pass.

## PB1.2.3D - Persistence and lifecycle

- [ ] PostgreSQL dump/restore drill PASS.
- [ ] Redis backup/restore or documented disposable-staging limitation PASS.
- [ ] Redis loss returns readiness 503 and recovers explicitly.
- [ ] Cloud Run restart and graceful shutdown drill PASS.
- [ ] SSE stays connected for more than five minutes, receives heartbeats/events and reconnects after restart.

## PB1.2.3E - Beta gate

- [ ] Budget alert and quota dashboards reviewed.
- [ ] 5xx, latency, memory, restarts, DB pool, Redis errors, SSE connections and backup failures alert.
- [ ] Immutable rollback to previous backend digest and previous Firebase release tested.
- [ ] Domain, HTTPS, HSTS/CSP responsibility and support owner documented.
- [ ] Public beta approval signed only after Redis durability policy is accepted.
