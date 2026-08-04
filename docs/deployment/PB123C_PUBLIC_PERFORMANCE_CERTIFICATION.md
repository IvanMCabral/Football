# PB1.2.3C — Public performance certification

## Measurement method

The measurements below are the warm public observations from the previous
authenticated short-season run. Each REST endpoint was requested ten times from
PowerShell against Render. They are not an SLA or a load-test result.

| Operation | Samples | p50 | p95 / max | Requests | UX classification |
|---|---:|---:|---:|---:|---|
| Liveness | 10 | 217 ms | 305 ms | 10 | Excellent |
| Readiness | 10 | 752 ms | 789 ms | 10 | Acceptable |
| Auth/me | 10 | 562 ms | 619 ms | 10 | Acceptable |
| Dashboard world status | 10 | 721 ms | 1,873 ms | 10 | Acceptable/slow tail |
| Squad | 10 | 268 ms | 270 ms | 10 | Excellent |
| Fixtures | 10 | 221 ms | 286 ms | 10 | Excellent |
| Standings | 10 | 265 ms | 311 ms | 10 | Excellent |
| Round start | 6 | 245–522 ms | — | 6 | Acceptable |
| Substitution | 6 | not retained | not retained | 6 | HTTP 200; timing gap |
| First SSE event | 6 | not retained | not retained | 6 | Observed; timestamp gap |
| Round finalization | 6 | ~42 s | — | 6 | Slow but usable simulation |

## Cold/warm distinction

The Render free instance stayed warm after a 70-second idle probe (liveness about
290 ms). This is not a cold-start measurement. A real sleep/wake and restart
drill remain open and are recorded separately in
`PB123C_COLD_START_AND_RESTART_DRILL.md`.

## Request and storage observations

No duplicate-request regression was observed in the public short-season flow;
duplicate round starts were idempotent. Redis command counts, payload sizes,
provider region and per-command latency were not instrumented, so they are not
invented here. Readiness includes both managed PostgreSQL and Redis and is the
only measured storage proxy.

## Verdict

The measured warm path is **JUGABLE CON ESPERAS**: routine screens are mostly
sub-second, while each detailed round takes roughly 42 seconds to finalize. It is
acceptable for beta testers with visible progress feedback, but not evidence of
production capacity or a strict latency SLA.
