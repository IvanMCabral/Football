# PB1.2.3H7.9D post-reset performance closure

## Result

**RESET PERFORMANCE P1 CLOSED**

The reset path is functionally correct and the local implementation is
validated. The public warm-reset performance gate is now closed by the bounded
empty-modern atomic finalization described in the RTT graph. No new H7.x gate
was created.

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
- Full backend suite after the final RTT change: **2,642 tests, 0 failures,
  0 errors, 4 skipped** across 267 Surefire reports.
- Public health: the first post-restart readiness probe briefly returned 503
  while the database dependency warmed; the retry gate then returned 5/5
  liveness 200 and readiness 200 with database/Redis UP.
- Render runtime commit tested: `e175d718`; the public service does not expose
  a live SHA, so the remote SHA classification remains `SHA_NOT_EXPOSED`.
- Public Redis provider storage/DBSIZE before and after N=10: not observable
  from the available non-mutating session; no manual Redis command or cleanup
  was executed.

## Remaining P1

None for the reset performance scope. Docker/CI/CD and broader provider
operational gates remain outside this targeted closure and were not changed.

## Evidence

- `docs/deployment/evidence/pb123h79d/reset-performance-before.json`
- `docs/deployment/evidence/pb123h79d/reset-performance-after-local.json`
- `docs/deployment/evidence/pb123h79d/reset-performance-public-n10.json`
- `docs/deployment/evidence/pb123h79d/reset-fast-path-forensics-20260809.json`

## Definitive RTT collapse (e175d718)

The previous profile left roughly 885 ms of cleanup unattributed because it did
not count sequential remote layers. The final runtime records ownership
validation, projection matches, command counts, atomic-script duration and
layer count. For the exact public fixture (`manifestEntries=0`) the graph is:

1. fenced validation and tombstone creation (parallel);
2. manifest members and protected projection discovery (parallel);
3. one bounded atomic finalization (metadata/projections, root last, tombstone
   clear).

That is three sequential remote layers. Public N=3 (3/3 modern, 3/3 HTTP 204)
and N=10 (10/10 modern, 10/10 HTTP 204, 0/10 HTTP 500/503) confirmed the
headers. N=10 client p50/p95 was 1292.5/1309 ms, server 1047/1054 ms and
cleanup 698/705 ms. Local real-Redis empty-manifest and non-empty bounded
profiles remained green. The final verdict is `RESET PERFORMANCE P1 CLOSED`.
