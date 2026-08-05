# PB1.2.3H7 - Redis storage root cause

## Verdict

**P - FREE-TIER STORAGE LIMIT EXCEEDED** is confirmed as the immediate runtime
blocker. Upstash reported 257 MB against a 256 MB Free Tier limit, and Redis
returned the exact capacity-quota error at 268,979,370 bytes versus a
268,435,456-byte threshold.

## Evidence from the owner inventory

Individual samples showed a world snapshot of approximately 2.48 MB and a
career root of approximately 0.98 MB. Career child detail/baseline keys were
tens of KB per match in the sampled owner. The cleanup set contained no active
`runtime:match`, `match:state`, or `match:commands` keys, so those families
were not the immediate quota driver in this incident. The dominant contributors
were retained per-user world/career projections and orphaned test-owner data,
multiplied across audit runs.

The original lifecycle deleted only `career:{userId}`. It did not discover
`world:{userId}`, user projection children, detailed-match, baseline, runtime,
state, or command families. The public reset endpoint then performed a
best-effort Game cleanup, which could not repair Redis orphan families. The
test harness called that same incomplete reset before creating a new fixture.

## Structural correction

`CareerDataCleanupRepository` is now an application port implemented by
`RedisCareerDataCleanupRepository`. `CareerSessionService.deleteCareer` reads
the owned career id, stops live registries, invokes bounded cleanup, and only
then deletes the career root. Errors propagate and prevent the root delete, so
a failed cleanup cannot silently claim success. Tests cover both an existing
career id and the no-root case through public service behaviour.

No gameplay, simulation, probabilities, fixtures, or PostgreSQL data were
changed. Retention classifications and remaining restore work are recorded in
`PB123H7_REDIS_RETENTION_REMEDIATION.md`.
