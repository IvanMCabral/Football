# PB1.2.3B - Public performance baseline

## Measurement conditions

Measurements were collected against the deployed staging backend
`https://manager-staging-api.onrender.com` using one ephemeral authenticated career.
They are warm HTTP observations from PowerShell; no load generator or synthetic
parallel traffic was used. Each endpoint was requested ten times.

## Warm request latency

| Endpoint | Requests | p50 | p95 / max | Status |
|---|---:|---:|---:|---:|
| liveness | 10 | 217 ms | 305 ms | 200 |
| readiness | 10 | 752 ms | 789 ms | 200 |
| auth/me | 10 | 562 ms | 619 ms | 200 |
| dashboard/world-status | 10 | 721 ms | 1,873 ms | 200 |
| dashboard/user-stats | 10 | 563 ms | 635 ms | 200 |
| career/status | 10 | 217 ms | 264 ms | 200 |
| career/players/squad | 10 | 268 ms | 270 ms | 200 |
| career/fixtures/all | 10 | 221 ms | 286 ms | 200 |
| career/standings | 10 | 265 ms | 311 ms | 200 |

## Interactive round timings

- Round start requests: 245-522 ms across six rounds.
- First SSE data event: observed in the bounded public stream probe; exact event
  timestamp was not captured by the PowerShell wrapper.
- Substitution request: HTTP 200 in every round; individual latency was not retained
  by the wrapper.
- Detailed simulation finalization: approximately 42 seconds per round in the
  six-round short-season run. This is simulation duration, not request latency.
- SSE probe: 8-9 data events were received per round before the intentional four-second
  curl limit.

## Cold start observation

After approximately 70 seconds of idle time, liveness remained HTTP 200 at about
290 ms. A true Render sleep/wake cycle was not reproduced, so this is a warm-instance
observation and not a cold-start guarantee.

## Interpretation

The dominant public latency is the managed-service path (Render to Neon/Upstash),
most visible in readiness and world-status. No Redis command counter, payload-size,
or concurrent-load measurement was captured, so none is invented here. The baseline
is suitable for a beta smoke gate, not an SLA or capacity claim.

## Redis observations

The public run used the existing managed Upstash Redis instance. Its provider region
was not captured from the dashboard during this run; command-level latency, commands
per action, value sizes, connection/retry counts, and redundancy were also not
instrumented. Redis is therefore classified as a runtime dependency for career,
match-detail, and live-session support, not as a proven performance bottleneck or a
durability guarantee. The only measured public proxy is readiness (p50 752 ms, p95
789 ms), which includes both database and Redis health checks.
