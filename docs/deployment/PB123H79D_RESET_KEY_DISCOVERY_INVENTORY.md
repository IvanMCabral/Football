# PB1.2.3H7.9D reset key-discovery inventory

This inventory is the source-level contract for the targeted reset P1
remediation. It does not include payloads or provider data.

| Family / template | Productive writer | ownerId | careerId / generation | TTL | Modern handling | Legacy handling |
|---|---|---|---|---:|---|---|
| `career:{ownerId}` | `RedisCareerRepository` | yes | yes / yes | 30d | exact root-last delete | exact pattern in fallback |
| `user:{ownerId}:career-ids` | `RedisCareerRepository` | yes | owner index | 31d | exact delete | exact pattern |
| `career-owner:{careerId}` | `RedisCareerRepository` | yes | yes / no | 31d | exact delete after validation | exact pattern |
| `career-generation:{careerId}` | `RedisCareerRepository` | yes | yes / value | 31d | exact delete after validation | exact pattern |
| `career-mapping-token:{careerId}` | `RedisCareerRepository` | yes | yes / value | 31d | exact delete after validation | exact pattern |
| `career:{careerId}:match-detail:{matchId}` | `DetailedMatchRedisAdapter` | context | yes / yes | configured, default 30d | bounded cleanup manifest | career-scoped SCAN |
| `career:{careerId}:match-baseline:{matchId}` | `BaselineStateRedisAdapter` | context | yes / yes | 7d | bounded cleanup manifest | career-scoped SCAN |
| `runtime:match:{ownerId}:{matchId}` | `RedisMatchRuntimeRepository` | context | yes / yes | 2h | bounded cleanup manifest | owner-scoped SCAN |
| `match:state:{ownerId}:{matchId}` | `RedisMatchStateRepository` | context | yes / yes | 24h | bounded cleanup manifest | owner-scoped SCAN |
| `match:commands:{ownerId}:{matchId}` | `RedisMatchCommandRepository` | context | yes / yes | 24h | bounded cleanup manifest | owner-scoped SCAN |
| `world:{ownerId}` | `RedisWorldRepository` | yes | optional before career | configured | deterministic exact key | exact pattern |
| `user:{ownerId}:*` projections | team/player/league/standing adapters | usually owner only | not consistently available | adapter-specific | one protected fallback SCAN (games excluded) | protected SCAN |

## Manifest contract

Modern careers are marked by:

- `career-cleanup-manifest-version:{careerId} = 1`;
- exact key references in the bounded set
  `career-cleanup-members:{careerId}`.

Only lifecycle-owned child keys are registered. Registration runs inside the
career coordinator and validates owner mapping, generation, and tombstone
absence in one fenced Redis script. A stale callback cannot add an entry or
write its child.

The set has a hard maximum of 1,024 entries and a 31-day TTL. Registration is
idempotent. Exceeding the cap fails closed before the child write; it never
silently falls back to an untracked write. Legacy careers have no explicit
marker and retain the complete twelve-family SCAN fallback. An empty manifest
is therefore modern only when the version marker is present.

## Reset command graph

Legacy reset discovers twelve logical families. A modern explicit-career reset
uses one protected projection SCAN, reads the exact manifest, deletes children
in batches of at most 100, deletes lifecycle metadata, and deletes the root
last. No global scan, `KEYS`, or provider-wide discovery is used.

The projection fallback remains because several historical user-scoped
adapters do not carry career identity in their write contract. It excludes game
keys, which have their own owner index and lifecycle.

## Storage budget

The hard cap is 1,024 references per career. Using a conservative 112-byte
average encoded member estimate gives less than 128 KiB per manifest and less
than 32 MiB for 256 worst-case careers, before Redis set overhead. This is
bounded metadata only; snapshots and match payloads are never duplicated.

The manifest is deleted during successful reset and expires after 31 days on
an abandoned lifecycle. A retry can remove the same exact set without a broad
discovery pass.
