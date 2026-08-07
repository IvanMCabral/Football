# PB1.2.3H7.5 — Final local review

## Scope and verdict

This review is the post-remediation companion to the historical H7.4 audit;
the historical `REJECTED` report is intentionally unchanged. The local code
now fences career-derived writers with owner, career and generation context,
blocks world orphan recreation, and uses tokenized compensation.

## P0 closure

| Finding | Evidence in code/tests | Status |
|---|---|---|
| Stale callback can recreate data | `CareerWriteContext`; coordinator validation; stale career and world real-Redis tests | CLOSED |
| World writer recreates orphan snapshot | explicit `saveInitial` guard plus `saveWithContext`; stale-world real-Redis test | CLOSED |
| Compensation deletes a later save mapping | compare-and-delete Redis script and concurrent-token real-Redis test | CLOSED |

## Operational contract

Coordination is single-instance only. A distributed lock/fencing service is a
required gate before horizontal scaling. Cleanup is root-last, retryable and
bounded by coordinator, Redis operation and total-operation timeouts. Tombstone
metadata is short-lived and is never used as an ownership source.

## Validation record

* `mvn -q -DskipTests test-compile`: green.
* Focused lifecycle, Redis adapter, world-write and WebFlux error-handler
  tests: green.
* Real Redis cleanup integration: 13 tests, 0 failures, 0 errors, 0 skipped,
  including the H7.5 stale career, stale world and compensation race
  scenarios.
* Full backend suite: 2,623 tests, 0 failures, 0 errors, 4 skipped, 264
  Surefire suites, 432.520 seconds of reported test time.
* `ShotCoordinateAttachmentTest`: three consecutive runs, each exit 0.
* Frontend, gameplay, datasets and remote providers are unchanged.

## Remaining gates

Public Render/Upstash validation, multi-instance guarantees and N=10 public
load are intentionally not claimed by this local remediation. They remain
independent follow-up gates.
