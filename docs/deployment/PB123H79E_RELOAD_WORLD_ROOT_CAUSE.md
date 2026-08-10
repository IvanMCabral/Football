# PB1.2.3H7.9E — Reload-world root-cause review

## Evidence

The real public pre-instrumentation N=10 baseline returned 6 HTTP 200 and 4
HTTP 500 responses. Successful client latency had p50 11,143 ms and maximum
28,096 ms. This is a diagnostic baseline, not an acceptance result.

Instrumented samples attributed most server time to canonical SQL and relation
loading, followed by team/player loading and serialization. Representative
successful timings included canonical 6.6–18.9 s, league/team work 2.1–7.9 s,
players 4.5–13.1 s, Redis write 0.6–2.7 s, and status assembly 2.0–5.6 s.
These ranges span deploys and provider state and must not be read as a single
stable percentile.

## Current blocker

The public readiness endpoint later returned HTTP 503 with `database: UP` and
`redis: DOWN`. A formal post-change N=20 therefore was not run. Provider
storage was not inspected or modified during this phase. The current release
decision is **RELOAD-WORLD PERFORMANCE NOT CLOSED**; the dominant unresolved
gate is Redis availability, followed by the need for a fresh N=20 after it is
healthy.

## Code-level findings

The request is now stage-instrumented, canonical relation reads are bulked, the
reload path avoids redundant relation writes, and status assembly reuses the
materialized snapshot. The temporary immutable catalog cache was reverted
because caching a provider error would make a transient Redis outage sticky.

## Attribution table

The following table records what can be classified from the observed diagnostic
headers. Stable p50/p95 values are deliberately left uncalculated because the
healthy-provider N=3/N=20 gates were not completed; nested stages also overlap
their parent server interval.

| Stage | p50 | p95 | % server p50 | Remote calls | Classification |
| --- | ---: | ---: | ---: | ---: | --- |
| Canonical load | NOT_CALCULATED | NOT_CALCULATED | NOT_CALCULATED | PostgreSQL bulk reads | A_POSTGRES_CANONICAL_LOAD |
| League/team relations | NOT_CALCULATED | NOT_CALCULATED | NOT_CALCULATED | PostgreSQL relation query | G_SEQUENTIAL_IO / A_POSTGRES_CANONICAL_LOAD |
| Team/player load | NOT_CALCULATED | NOT_CALCULATED | NOT_CALCULATED | PostgreSQL bulk reads | A_POSTGRES_CANONICAL_LOAD |
| Serialization | NOT_CALCULATED | NOT_CALCULATED | NOT_CALCULATED | none | D_SERIALIZATION |
| Ownership and Redis write | NOT_CALCULATED | NOT_CALCULATED | NOT_CALCULATED | Redis commands | H_PROVIDER_NETWORK_RTT (observed, not isolated) |
| Status assembly | NOT_CALCULATED | NOT_CALCULATED | NOT_CALCULATED | career/status reads | I_DUPLICATE_WORK (the materialized snapshot reread was removed) |

Observed successful samples exposed all headers, but they are not sufficient to
claim an 80% formal attribution or a performance pass.
