# PB1.2.3H7.9D provider-side reset diagnosis

## Result

**RESET PERFORMANCE P1 NOT CLOSED**

No new H7.x gate was created. This document records the targeted provider-side
diagnosis and the safe runtime changes made after the previous closure report.

## Correlated public N=10

Successful reset samples (all HTTP 204):

| sample | client ms | server ms | career ms | cleanup ms |
|---:|---:|---:|---:|---:|
| 1 | 2028 | 1706 | 1356 | 1262 |
| 2 | 1864 | 1641 | 1293 | 1293 |
| 3 | 1783 | 1575 | 1229 | 1229 |
| 4 | 1836 | 1587 | 1241 | 1241 |
| 5 | 1775 | 1567 | 1216 | 1148 |
| 6 | 1613 | 1406 | 1059 | 1059 |
| 7 | 1605 | 1403 | 1056 | 1055 |
| 8 | 1754 | 1503 | 1156 | 1156 |
| 9 | 1603 | 1398 | 1052 | 1051 |
| 10 | 1603 | 1399 | 1052 | 1052 |

Aggregates:

- client p50: **1764.5 ms**;
- client p95/max: **2028 ms**;
- server p50: **1535 ms**;
- server p95/max: **1706 ms**;
- client-minus-server median: approximately **230 ms**;
- 204: 10/10;
- 500: 0;
- 503: 0.

Classification: **SERVER_REDIS_BOUND**, with a smaller network-edge component.
The server consumes nearly all elapsed time; the browser/client is not the
cause. Within the server, Redis cleanup is the dominant measured stage. The
career lookup is zero when the owner snapshot is cached and falls back to
roughly 0.8–1.8 s when it is not.

## Command graph

For an explicit career reset the logical graph is:

- career lookup: PostgreSQL/Redis career repository fallback, or cached
  snapshot;
- one tombstone `SET`;
- one owner-mapping `GET`;
- bounded owner-scoped `SCAN` families (12 logical patterns, no global scan);
- one coalesced child `UNLINK` batch and one final career-root `UNLINK` batch;
- one tombstone `UNLINK`/delete after success;
- game projection cleanup uses owner-index `SMEMBERS`, then parallel game/index
  `UNLINK` operations.

`EXISTS` runs only to explain a Redis short count. No shortfall or timeout was
observed in the public N=10. No keys, tokens, UUIDs or payloads are logged.

The exact network round-trip count is provider-dependent and is not exposed by
the public API; server stage headers and source-level command graph are the
available evidence. The measured profile proves the bottleneck is remote Redis
work rather than client/network edge latency.

## Changes applied

- bounded parallel read-only discovery;
- global child deletion batches capped at 100;
- root isolated as the final destructive batch;
- explicit-career reset avoids redundant owner-index lookup;
- tombstone write and ownership validation overlap when safe;
- redundant tombstone phase update removed; initial tombstone remains durable
  and retryable;
- game projection cleanup uses a single owner-index operation with parallel
  game/index unlink;
- reset uses the current career snapshot when available;
- sanitized `X-Reset-Server-Ms`, `X-Reset-Career-Ms`, `X-Reset-Game-Ms` and
  stage timing headers for operational diagnosis.

No gameplay, simulation, fixtures, datasets, frontend, database schema,
public Redis data, billing or infrastructure plan changed.

## Correctness and validation

- owner isolation: PASS;
- root-last: PASS;
- stale generation: PASS;
- retry/tombstone: PASS;
- batch max: 100;
- accounting and short-count handling: PASS;
- local real-Redis N=20: PASS (p50 4 ms, p95 5 ms, max 5 ms);
- backend full suite: 2633 tests, 0 failures, 0 errors, 4 skipped;
- final public health retry: liveness/readiness 200, DB/Redis UP.

The explicit target remains p50 <=1500 ms and p95 <=3000 ms. The p95 passes,
but p50 does not; therefore the P1 remains open and is not represented as
closed.

Evidence: `docs/deployment/evidence/pb123h79d/provider-diagnosis-n10.json`.
