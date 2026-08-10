# PB1.2.3H7.9E — Global Upstash keyspace inventory

## Result

`GLOBAL_KEYSPACE_ATTRIBUTION_COMPLETE`

This report is a read-only census. It does not authorize cleanup and it did not
execute a mutation. The census was allowed to use one sequential global `SCAN`
because the earlier owner-scoped inventory could not explain the keyspace.

## Provider and reconciliation

Observed in the authenticated Upstash `Manager` console/CLI on 2026-08-10:

| Metric | Value |
|---|---:|
| Plan | Free Tier |
| Region | AWS `sa-east-1` |
| Storage | `256 MB / 256 MB` (rounded provider display) |
| Quota | `268,435,456` bytes |
| PING | `PONG` |
| DBSIZE before/after | `9,638 / 9,638` |
| SCAN mode | one sequential chain, `COUNT 100` |
| Iterations | 97 |
| Keys enumerated | 9,638 |
| Unique keys | 9,638 |
| Duplicates | 0 |

The exact reconciliation is:

`classifiedKeys 9,638 + unknownKeys 0 = totalKeys 9,638 = DBSIZE 9,638`.

No payload was read or exported. No `DEL`, `UNLINK`, `EVAL`, `FLUSHDB`,
`FLUSHALL`, write, or broad cleanup command was run.

Machine-readable reconciliation: `evidence/pb123h79e/global-keyspace-family-counts.json`.

## Families by count

| Family | Keys | Key template/interpretation |
|---|---:|---|
| User projections | 8,955 | `user:{ownerId}:...` excluding game/index classification |
| Game/index | 262 | `user:{ownerId}:game...` and game-related indexes |
| Match baseline | 126 | `career:{careerId}:match-baseline:{matchId}` |
| Match detail | 120 | `career:{careerId}:match-detail:{matchId}` |
| World | 102 | `world:{ownerId}` |
| Career roots | 21 | `career:{ownerId}` |
| Career generation | 11 | `career-generation:{careerId}` |
| Career mapping token | 11 | `career-mapping-token:{careerId}` |
| Career owner mapping | 11 | `career-owner:{careerId}` |
| Career index | 11 | `user:{ownerId}:career-ids` |
| Manifest version | 7 | `career-cleanup-manifest-version:{careerId}` |
| Manifest members | 1 | `career-cleanup-members:{careerId}` |
| Global/non-owner | 0 | No observed catalog/global/cache/system family |
| Unknown prefix | 0 | No unclassified prefix |

There were no runtime, match-state, match-command, or cleanup-tombstone keys in
this census. The dominant family by count is user projection (8,955, 92.9%).

## Owner attribution

UUIDs were extracted only from key names and joined against the PostgreSQL
`users` table with SELECT-only queries. The join found:

| Classification | Owners | Keys |
|---|---:|---:|
| Redis owners discovered | 144 | 9,478 owner-attributed keys |
| PostgreSQL users present | 0 | 0 |
| PostgreSQL users absent | 144 | 9,478 |
| Current protected users with Redis keys | 0 | 0 |
| Proven disposable PB123 test owners with Redis | 0 | 0 |

This does not prove that all Redis-only owners are disposable. It proves that
the current PostgreSQL snapshot contains none of the 144 Redis owner UUIDs.
They are therefore classified `POSTGRES_ABSENT_ORPHAN` / `POTENTIAL_ORPHAN`,
not automatically safe to delete. The top-owner distribution has a minimum of
1 key, median 77, and maximum 128 keys. Sanitized top-owner evidence is in
`global-keyspace-owner-attribution.json`.

The 21 audit users identified in the prior owner-scoped inventory still have
zero Redis keys. No protected PostgreSQL user was reclassified as a test user.

## Career attribution

Twenty-two distinct career IDs were found in detail, baseline, and lifecycle
metadata. Eleven had a `career-owner:{careerId}` mapping and matching
`user:{ownerId}:career-ids` membership. Eleven had no owner mapping and are
`ORPHAN_CAREER_NAMESPACE`. No contradictory mapping was observed.

The mapped career namespaces contain 127 keys. The missing-mapping namespaces
contain 160 detail/baseline keys. Full sanitized career attribution is in
`global-keyspace-career-attribution.json`.

## Type, TTL and size sampling

Representative exact-key checks reported expected Redis types: strings for
roots, world, projections, details, baselines and metadata; sets for career
indexes and manifest members.

`MEMORY USAGE` was sampled on 100 exact keys, totaling 41,388,164 measured
bytes. This is a sample, not a total; 9,538 keys remain unmeasured. The largest
sampled families were world (29,801,916 bytes), career roots (10,161,646),
baseline (800,356), and detail (615,000). No extrapolation was performed.

Among the 100 TTL samples, 33 were active and 67 had no TTL. The no-TTL sample
was concentrated in user projections/game indexes (writers intentionally use
non-expiring values), plus 11 world and 18 detail samples despite current
30-day configuration. Those world/detail observations are legacy/old-writer
signals requiring lifecycle review, not proof that every key is defective.

Evidence:

- `global-keyspace-ttl-census.json`
- `global-keyspace-size-sampling.json`

## Orphans and candidate set V2

The census found no unknown prefixes or foreign mappings. It did find:

- 9,478 keys in namespaces whose owner UUID is absent from PostgreSQL;
- 160 career detail/baseline keys without a career-owner mapping;
- zero proven disposable test-owner keys.

These are `POTENTIAL_ORPHAN` only. PostgreSQL absence can reflect a stale Redis
database, a database snapshot mismatch, or a deleted user; it is not sufficient
to identify a safe human-data deletion. Consequently the safe candidate set V2
contains zero deletion-eligible keys even though 9,638 potential orphan keys
are documented for a later, explicitly authorized review.

No cleanup was performed. The dry-run and candidate evidence are:

- `safe-candidate-set-v2.json`
- `safe-cleanup-v2-dry-run.json`
- `global-keyspace-orphans.json`

## Limitations and next gate

The provider exposes only rounded storage in the current UI, so exact total
bytes and exact top-owner byte ordering cannot be proven from this read-only
run. The current PostgreSQL/Redis identity mismatch must be reconciled before
any deletion. No H7.9E N3/N20 or public cleanup may run while storage remains at
the quota.

Actual deletions: **0**. Cleanup authorized: **NO**. Next action:
`WAIT FOR EXPLICIT CLEANUP AUTHORIZATION`.
