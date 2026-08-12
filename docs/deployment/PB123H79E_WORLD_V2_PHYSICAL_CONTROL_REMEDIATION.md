# World V2 physical control remediation

This remediation closes the evidence-integrity finding without changing the
public provider, gameplay, simulation, fixtures, frontend or dataset.

## Physical fault injection

`WorldV2PhysicalFaultInjection` is test-only. It first reads a committed
`world:{owner}` envelope produced by the product migration path, mutates the
serialized `overlay` JSON, writes the malformed string back to the same
ephemeral Redis key with the original TTL, and then the test constructs a new
repository before invoking `findByUserId`.

The helper records only a key hash, pre/post SHA-256, mutation kind, Redis
type, TTL and boolean evidence flags. Payloads and identities are not emitted.
The sanitized four-record export is committed at
`docs/deployment/evidence/pb123h79e/world-v2-final-evidence-integrity/physical-fault-evidence.ndjson`.

The four previously proxy controls now physically mutate Redis and fail via a
fresh product reader:

| Control | Physical mutation | Product result |
|---|---|---|
| MISSING_CUSTOM_TEAM | remove the custom-team entry from `customTeams` | rejected by overlay/player reference validation |
| MISSING_CUSTOM_PLAYER | remove the custom-player entry while its alias remains | rejected by alias/reference validation |
| LOST_LEAGUE_RELATION | add the assigned league to `removedCanonicalLeagueIds` | rejected by relation validation |
| MISSING_LEGACY_ALIAS | remove the required legacy alias | rejected by declared-alias validation |

All four have `redisTouched=true`, different pre/post checksums,
`malformedPersistedState=true`, `inMemoryOnlyMutation=false`, a fresh product
graph and no comparator-only path.

## Honest authority

`WorldV2NegativeControlAuthorityV4` defines the allowed modes:

`REDIS_PHYSICAL`, `JVM_PROCESS_PHYSICAL`, `BUILD_DISCOVERY_PHYSICAL`,
`PRE_WRITE_GUARD`, and `SOURCE_PROVEN`.

The fresh recount is 33 unique controls: 26 Redis physical, 1 pre-write guard
(`MISSING_TTL`), 6 source-proven, 0 duplicates, 0 material proxies, 0 false
mode labels and 0 false passes. The evidence meta-tests reject missing Redis
mutation, unchanged checksums, comparator-only claims, missing product entry
points, duplicate invariants and unknown modes.

## Scope

No production Redis or PostgreSQL was changed. The physical mutations run only
inside the isolated integration Redis database and are cleaned by the test
fixture lifecycle.
