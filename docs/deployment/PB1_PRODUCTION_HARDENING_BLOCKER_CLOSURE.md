# PB1.1 Production Hardening Blocking Remediation Closure

Date: 2026-08-01

Historical independent audit: `docs/deployment/PB1_PRODUCTION_HARDENING_FINAL_INDEPENDENT_AUDIT.md`

Historical verdict preserved: `PB1.1 HARDENING REJECTED`

This document records the remediation performed after the rejected audit. It does not rewrite the historical verdict.

## Scope

The closure addressed the remaining PB1.1 blockers:

- internal exception details exposed to clients;
- Redis health probe reporting `UP` after authentication/isolation drift;
- Redis integration tests depending on a shared local Redis database;
- final reproducibility of backend and frontend suites.

No gameplay, simulation rules, Docker, Cloud Run, Firebase, CI/CD, deployment, or push was performed.

## P0 blockers closed

| Blocker | Status | Evidence |
| --- | --- | --- |
| Production error leakage | CLOSED | Production exception handling now returns controlled public messages and request IDs for validation, authentication, authorization, state, and unexpected errors. Internal causes remain in logs only. |
| Manual controller errors leaking exception messages | CLOSED | Lineup preview, versus comparison, test harness UUID validation, career advance/continue, substitution, and mutation services were sanitized. |
| Rate limit response inconsistent with production error contract | CLOSED | 429 responses now return JSON with `RATE_LIMITED`, public message, status, and request ID. |
| Redis readiness was not a real Redis probe | CLOSED | Readiness now performs ephemeral Redis `set/get/delete` with TTL on a unique health key and returns HTTP 503 when Redis is unavailable. |
| Redis test isolation drift | CLOSED | Test profile can bootstrap isolated local Redis on a random port, ephemeral password, database 15, and persistence disabled. |

## P1 items closed because they affect Internet exposure

| Area | Status | Evidence |
| --- | --- | --- |
| Health endpoint access | CLOSED | Security allows `/api/v1/health/**`; readiness/liveness can be used by providers without authentication. |
| Redis database property | CLOSED | `spring.data.redis.database` is applied to `RedisStandaloneConfiguration`. |
| Error contract consistency | CLOSED | `ErrorResponseBody` includes `requestId` and keeps backward-compatible constructors for existing tests/callers. |
| Frontend production validation | CLOSED | Development and production builds pass; production build output does not include the test harness lazy chunk. |

## Backend validation

- `mvn -q -DskipTests test-compile`: PASS
- Focused backend tests: PASS
  - `LineupControllerE2ETest`
  - `DetailedCareerMutationServiceTest`
  - `GlobalExceptionHandlerProductionTest`
  - `RedisHealthProbeIntegrationTest`
- Full backend suite run 1: PASS
- Full backend suite run 2: PASS
- Final Surefire XML count: 2553 tests, 0 failures, 0 errors, 4 skipped

Note: the full suite still emits Redis/Lettuce shutdown noise after Spring contexts close, but Surefire exits successfully and XML reports show zero failures and zero errors.

## Frontend validation

- Visible text encoding guard: PASS, 385 files scanned
- `npm run build -- --configuration development`: PASS
- `npm run build`: PASS
- `npm test -- --watch=false --browsers=ChromeHeadless`: PASS
- Result: 1029 SUCCESS, 0 failures, 2 skipped
- Production build inspection: PASS by build output; no `test-harness` lazy chunk is emitted in production.

## Redis durability status

Redis remains runtime-critical for PB1.1. This remediation fixes readiness and test isolation, but does not claim Redis durability is solved.

PB1.2 still requires:

- managed Redis provider selection;
- persistence/backup policy;
- restore drill;
- loss/reconnect drill;
- TTL policy by key family;
- explicit user-facing recovery behavior for interrupted LiveSessions.

## Final PB1.1 blocker assessment

The P0 blockers identified by the rejected final audit are closed in the working tree captured by this remediation.

Remaining items are PB1.2 infrastructure gates, not PB1.1 application hardening blockers.

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
