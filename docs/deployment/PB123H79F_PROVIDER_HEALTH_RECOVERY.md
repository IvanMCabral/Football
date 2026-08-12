# PB1.2.3H7.9F provider health recovery

## Verdict

**PB1.2.3H7.9F BLOCKED_RENDER_RUNTIME**

The public service recovered naturally after an initial cold-start-like delay:
the first diagnostic liveness probes timed out, then a bounded retry returned
`200 {"status":"UP"}` and the service remained stable. The mandatory health
samples passed after recovery. The gate nevertheless stops because the
authenticated Render dashboard was not available, so the live deployment SHA,
instance count and autoscaling state cannot be verified. No canary or owner
inspection is allowed without that identity gate.

## Git identity

| Field | Value |
|---|---|
| Branch | `feat/v25d99.20.3.1-runtime-fixes` |
| Local HEAD | `b8fe8fe8` |
| Upstream HEAD | `b8fe8fe8` |
| Ahead/behind | `0/0` |
| `git diff --check` | clean |

## Runtime and dashboard

The public endpoint is `https://manager-staging-api.onrender.com`. A read-only
dashboard navigation resolved to the Render sign-in page; no authenticated
service metadata or runtime logs were exposed. Therefore:

- service: `manager-staging-api` (public endpoint inferred from the retained
  deployment URL; dashboard visibility not verified);
- live SHA: `NOT_VERIFIABLE`;
- branch, deployment status/timestamp, plan, region: `NOT_VERIFIABLE`;
- instance count: `NOT_VERIFIABLE`;
- autoscaling: `NOT_VERIFIABLE`;
- single-instance: `NOT_VERIFIABLE`;
- runtime classification: `BLOCKED_RENDER_RUNTIME`.

Public health responses do not establish revision identity or scaling.

## Health recovery

The initial 60-second diagnostic probes from both PowerShell and `curl.exe`
timed out with HTTP 000 and zero response bytes. A bounded retry sequence then
returned liveness `200` on attempt 2 and remained healthy on attempts 3–6.
After recovery, five liveness samples and five readiness samples separated by
two seconds all returned HTTP 200.

Readiness body was consistently:

`{"status":"UP","database":"UP","redis":"UP"}`

The first stable readiness sample took 11,883 ms; subsequent samples were
757–899 ms. This is consistent with a free-tier cold start, but provider logs
were unavailable, so the classification remains `FREE_TIER_COLD_START_RECOVERED`
with runtime identity still blocked.

## Source health contract

The inspected source remains unchanged from the certified implementation:

- liveness is an immediate reactive `Mono` response;
- database readiness uses `SELECT 1` with a two-second timeout and fail-closed
  error handling;
- Redis readiness uses a short-lived health probe with a two-second timeout and
  cleanup. Those writes are internal side effects of the public health probe,
  not manual audit mutations.

No new blocking or manual subscription was introduced by this gate. No source
regression was found.

## Provider and database access

No authenticated Render, Upstash or Neon dashboard session was available in the
connected browser. Consequently, current Upstash plan/quota/storage/DBSIZE and
Neon project/branch/database/`SELECT 1` cannot be verified. Historical values
were not reused as current evidence.

## Safety boundary

- recovery action performed: `NONE`;
- Redis writes/deletes by this audit: `0` direct;
- PostgreSQL writes: `0`;
- provider/infrastructure changes: `0`;
- canary execution authorized: **NO**;
- bulk migration authorized: **NO**;
- cleanup authorized: **NO**.

The gate stops before owner selection, candidate reads, planner execution,
capacity calculation and any migration-related operation.

