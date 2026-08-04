# PB1.2.3H — Public performance and loading remediation

## Scope and result

This remediation covers the frozen H1–H8 scope: catalog loading, match-start latency, progressive loading states, measurement, regression, deployment and public smoke. Gameplay rules, probabilities, results, calendar and simulation semantics were not changed.

Result: **COMPLETED WITH ISSUES**. The measured warm navigation surfaces improved and the final revisions are public, but the complete H6 matrix (all twelve operations, payload sizes and request-level cache hit/miss) and an independently induced cold-start sample were not available from the browser surface. Those gaps remain explicitly classified below.

## Root causes found

- Catalog data was requested from multiple screens without a shared client lifecycle. The frontend now uses `WorldCatalogService`, a root singleton with `shareReplay`, explicit invalidation and non-blocking dashboard prefetch.
- Round start had no visible phase and could be triggered repeatedly from a fast click/route transition. The frontend now disables the start action while one operation is active; the backend keeps the in-flight/idempotent guard and records stage timings.
- Render Free wake-up and Redis career reads are separate effects. Render's own UI warns that an idle instance can add 50 seconds or more; runtime metrics also recorded transient career-load/dashboard errors during the first wake window. This is not treated as a generic explanation or as evidence that Redis solved the path.
- PostgreSQL and Redis were healthy at the post-deploy readiness check. No SQL or Redis writes were performed to manufacture measurements.

## Changes

- Backend: `RuntimeOperationMetrics` now keeps bounded count, success/error, average, p50, p95 and max samples without payload or personal data. `RoundController` measures career load, context creation, initialization and the round-engine start boundary.
- Frontend: catalog requests are shared and cached for the session; dashboard prefetch is non-blocking; catalog invalidation follows seed/retry; start progress is shown as `Preparando fecha` and the button is disabled for the active operation.
- Validation: focused tests, complete suites, both builds, production artifact inspection, production dependency audit, Firebase deployment, Render revision and public authenticated smoke were executed.

## Evidence

Sanitized raw evidence is in `docs/deployment/evidence/pb123h/`. No token, cookie, email, connection string or complete career snapshot is stored.

## Remaining classification

- P0: none observed.
- P1: the public start path reached the live route in 5.278 s in the single public sample; this is above the warm target and needs a repeatable request-level profile before being called resolved.
- P2: full H6 operation matrix, payload sizes, browser network timings, cold-start timing and Redis hit/miss telemetry remain incomplete; transient Redis career-load/dashboard errors were visible in the first wake window.
- P3: expose correlation between request ID, stage metrics and first SSE event in a sanitized performance export.
