# PB1.2.3H7.1 - Redis isolation and traceability remediation

## Scope

This local change set closes the owner-isolation and orphan-discovery findings
without touching Upstash, gameplay, probabilities, fixtures, SQL rows, or
public deployment.

## Owner-scoped lifecycle

`CareerSessionService.deleteCareer` now resolves the owned career first,
stops only `RoundEngine` instances registered with the exact user/career
metadata, clears only `MatchSession` instances for that owner, runs Redis
cleanup, and deletes the root last. A cleanup error propagates and prevents
the root delete. Global `stopAllEngines` and `clearAllSessions` remain only as
administrative/test lifecycle operations; public reset and season continuation
use scoped operations.

`RoundController` assigns owner metadata before publishing a round engine.
The registries never expose their mutable maps and do not use partial UUID
matching.

## Orphan strategy

`RedisCareerRepository` maintains the exact set
`user:{userId}:career-ids` on career save. Existing roots lazily add their
career id only when the membership is absent; reads do not renew an existing
index indefinitely. Cleanup reads this owner-scoped set, so a missing
`career:{userId}` root can still discover its exact
`career:{careerId}:match-detail:*` and `career:{careerId}:match-baseline:*`
families. If both root and index are absent, cleanup returns an explicit empty
result and never performs a global career scan.

## Result contract

`CareerDataCleanupResult` records patterns, discovered hits, unique keys,
requested/deleted counts, batches, maximum batch size, short owner hash,
career count, per-family counts, partial failure and duration. Redis deletion
counts are checked against the requested batch. Expiration between SCAN and
delete is represented by `requested > deleted`, not treated as an error. A
failure is wrapped in `CareerDataCleanupException`; callers cannot report
successful root deletion.

All scans are exact UUID-anchored patterns, incremental, sequential and
buffered at 100 keys. No `KEYS *`, global career scan, `block()` or
fire-and-forget subscription was added.
