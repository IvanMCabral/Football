# PB1.2.3H7.9D post-reset performance closure

## Result

**RESET PERFORMANCE P1 NOT CLOSED**

The reset path is functionally correct and the local implementation is
validated, but the public warm-reset performance gate remains open. No new
H7.x gate was created and the tactical evidence gap was not reopened.

## Root cause and changes

The original public N=5 profile was stable at 8.121–8.207 s because cleanup
performed independent Redis discovery scans sequentially and issued a separate
destructive round trip for each populated family. The source profile showed
the deterministic cost concentrated in the discovery/delete graph, not in
gameplay or simulation.

The remediation keeps ownership and generation validation before discovery,
runs read-only family scans with bounded concurrency (8), coalesces child
deletions into ordered batches of at most 100, keeps the career root in a
separate final batch, overlaps the initial tombstone write with the read-only
index lookup, and skips the redundant index read when an explicit career id is
already validated. Tombstone, retry, fencing, accounting and root-last
semantics remain unchanged.

## Measurements

### Before (retained public N=5)

| metric | value |
|---|---:|
| samples | 5 |
| p50 | 8,160 ms |
| p95 | 8,207 ms |
| max | 8,207 ms |
| HTTP 204 | 5/5 |
| HTTP 503 | 0 |

The previous deployment did not expose per-stage server timings. The source
profile identified sequential discovery and per-family destructive calls as
the dominant stages; coordinator/registry and tombstone-clear timings were
not separately exported.

### After (local real-Redis N=20)

| metric | value |
|---|---:|
| samples | 20 |
| p50 | 4 ms |
| p95 | 5 ms |
| max | 5 ms |
| status | 20/20 completed |

The local stage profile showed bounded discovery in roughly 0–2 ms per family
and one coalesced child batch plus the final root batch. No destructive
operation runs concurrently with another destructive operation.

### After (public warm N=10, superseded deployment)

Reset timings in milliseconds: **3559, 3087, 4111, 3907, 3248, 3254, 3033,
3396, 3064, 3130**.

| metric | value |
|---|---:|
| samples | 10 |
| HTTP 204 | 10/10 |
| HTTP 503 | 0 |
| HTTP 500 | 0 |
| p50 | 3,251 ms |
| p95 | 4,111 ms |
| max | 4,111 ms |
| target p50 | <=1,500 ms (not met) |
| target p95 | <=3,000 ms (not met) |

The N=10 setup flow completed register, authenticated world access, career
creation, auto-select and lineup confirmation for every successful sample.
One reload-world request returned a transient 502 during the batch; the
account flow and reset still completed with 204 and no reset 5xx response.

### Final forensic deployment (runtime `9c69eace`)

The public marker and headers now prove the intended path: N=3 was 3/3
`MODERN_MANIFEST`, version `1`, scan count `1`, and N=10 was 10/10
`MODERN_MANIFEST`, 204, with no 500 or 503 responses. Final client timings
were 2,647–2,718 ms (p50 2,669 ms, p95 2,718 ms), server p50/p95
2,455/2,480 ms, and cleanup p50/p95 2,106/2,130 ms. Representative stage
headers were discovery 175 ms, manifest read 175 ms, projection scan 174 ms,
child unlink 174 ms, metadata 175 ms, root unlink 174 ms, and tombstone 174
ms. No stage dominates the total; the exact classification is
`L_PROVIDER_GENERAL_LATENCY`.

## Invariants

- Owner B isolation: preserved by the existing owner/generation validation and
  real-Redis integration suite.
- Root-last: preserved; root is always a separate final destructive batch.
- Stale generation and corrupt index: fail closed in focused and real-Redis
  tests.
- Retry/tombstone: preserved; partial failures remain retryable and the
  tombstone is cleared only after successful cleanup.
- Batch limit: maximum 100 keys.
- Accounting: requested/deleted/shortfall accounting remains enforced.
- Gameplay, frontend, database, fixtures and datasets: unchanged.

## Validation

- `mvn -q -DskipTests test-compile`: PASS.
- Focused cleanup unit and real-Redis tests: PASS.
- Full backend suite: **2,639 tests, 0 failures, 0 errors, 4 skipped**.
- Public health: the first post-restart readiness probe briefly returned 503
  while the database dependency warmed; the retry gate then returned 5/5
  liveness 200 and readiness 200 with database/Redis UP.
- Render runtime commit tested: `9c69eace`; the public service does not expose
  a live SHA, so the remote SHA classification remains `SHA_NOT_EXPOSED`.
- Public Redis provider storage/DBSIZE before and after N=10: not observable
  from the available non-mutating session; no manual Redis command or cleanup
  was executed.

## Remaining P1

The remaining P1 is provider-side warm reset latency. The current source-level
fix is safe and materially improves local execution, but public latency still
fails the explicit p50/p95 gate. Further work should obtain server-side stage
timings from the deployment logs and reduce provider round trips without
relaxing ownership, fencing or root-last guarantees.

## Evidence

- `docs/deployment/evidence/pb123h79d/reset-performance-before.json`
- `docs/deployment/evidence/pb123h79d/reset-performance-after-local.json`
- `docs/deployment/evidence/pb123h79d/reset-performance-public-n10.json`
- `docs/deployment/evidence/pb123h79d/reset-fast-path-forensics-20260809.json`
