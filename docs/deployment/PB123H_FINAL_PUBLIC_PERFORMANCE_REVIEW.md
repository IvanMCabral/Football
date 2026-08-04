# PB1.2.3H — Final public performance review

## Verdict

**COMPLETED WITH ISSUES**

The final backend and frontend revisions were deployed and verified against the real public URLs. Warm dashboard, squad and matches navigation was measured before/after, the round start reached a consultable live route, a live minute advanced, and a hard reload recovered the same route. No P0 was found. The evidence is not sufficient for an unconditional completion because the full twelve-operation H6 matrix, request-level cache/payload metrics, isolated first-SSE timing and a fresh cold-start sample were not available.

## Public deployment

- Backend: `https://manager-staging-api.onrender.com`
- Backend revision: `af9403c9f719d43ea445be22eda24996f5a0c1bf`
- Frontend: `https://manager-4f952.web.app`
- Frontend revision: `ecd284ec039f4994310e04370a2d9f82878ab411`
- Production artifact: 53 files scanned, no source maps and no test-harness references.
- Production npm audit: 0 critical, 0 high, 0 moderate, 0 low.

## Health and recovery

- liveness: HTTP 200 (`UP`), 211 ms observed;
- readiness: HTTP 200 (`UP`, database `UP`, Redis `UP`), 2,045 ms observed;
- protected backend root: HTTP 401, as expected without an application credential;
- public live route after one start click: reached in 5,278 ms;
- first live state: minute 22 observed and advancing;
- hard reload: route remained available at minute 26 with no 404/500 text.

## Gate classification

| Gate | Status | Evidence |
|---|---|---|
| No gameplay change | PASS | only caching, instrumentation and loading-state code changed |
| Warm navigation before/after | PASS | three surfaces, N=10 each, raw samples committed |
| Catalog de-duplication | PASS | singleton/shareReplay implementation and focused tests |
| Duplicate start guard | PASS | frontend disabled state, backend guard, one-click public smoke |
| Public deploy | PASS | Render `af9403c9`, Firebase `ecd284e` artifact |
| Health | PASS | liveness/readiness public checks |
| Live/reload smoke | PASS | live minute advanced and reload recovered |
| Full H6 timing matrix | WARNING | not independently exposed by browser surface |
| Cold-start measurement | WARNING | not deliberately induced in this run |
| Redis/cache hit-miss profile | WARNING | runtime errors observed; hit/miss and payload export still absent |

## Open items

- P0: none.
- P1: repeatable start-round stage profile is still needed; the single public start was 5.278 s, above the warm target.
- P2: complete H6 matrix, request-level payload/cache counters, isolated first-SSE timestamp and cold-start run; investigate transient Redis career-load/dashboard errors seen during first wake.
- P3: publish a sanitized performance export tied to correlation IDs.

## Tester readiness

Prepared for controlled external testers: **yes, with the documented warnings**. Prepared for unrestricted public beta: **not yet certified by this H report** until the P1/P2 measurements are closed.

Evidence index: `docs/deployment/evidence/pb123h/`.
