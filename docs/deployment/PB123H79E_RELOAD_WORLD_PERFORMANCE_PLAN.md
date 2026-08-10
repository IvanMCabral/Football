# PB1.2.3H7.9E — Reload-world performance plan

## Scope

This phase targets only the warm `POST /api/v1/dashboard/reload-world` path. It
does not reopen reset acceptance (H7.9D), gameplay, simulation, fixtures,
calendar, datasets, or frontend behaviour.

## Measurement contract

The endpoint now emits sanitized response headers for server and stage timing:

`X-Reload-Server-Ms`, `X-Reload-Canonical-Ms`, `X-Reload-League-Team-Ms`,
`X-Reload-Leagues-Ms`, `X-Reload-Players-Ms`, `X-Reload-Assembly-Ms`,
`X-Reload-Serialize-Ms`, `X-Reload-Ownership-Ms`, `X-Reload-Redis-Ms`,
`X-Reload-Response-Ms`, `X-Reload-Status-Ms`, count headers, and serialized byte
count. They contain durations and aggregate counts only; no user IDs, keys,
tokens, credentials, or payloads.

Formal public gate: N=20 fresh accounts, HTTP 200 for every request, zero 500
and 503 responses, client p50 <= 2500 ms, p95 <= 4000 ms. The desired warm
experience is <= 1500 ms p50. Attribution requires timing headers on at least
80% of successful samples.

## Execution status

The pre-instrumentation public N=10 baseline is retained in the evidence
directory. A formal post-change N=20 could not be executed because the public
readiness gate reported Redis DOWN. No provider cleanup or manual Redis change
was performed. Consequently this phase remains open and is explicitly not a
performance approval.

## Safe continuation

When the provider is healthy, run N=3 first, then N=20 only if N=3 is fully
green. Keep the response headers and request IDs with sanitized suffixes in the
evidence files. If Redis becomes unavailable again, stop the gate and record
the outage rather than interpreting failed requests as a benchmark.
