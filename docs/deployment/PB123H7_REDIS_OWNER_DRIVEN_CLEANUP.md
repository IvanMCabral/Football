# PB1.2.3H7 - Owner-driven Redis cleanup

## Scope and safety

This report records the recovery after Upstash Free Tier crossed its 256 MiB
limit. Cleanup was restricted to owners proven to be PB1.2.3 audit accounts by
PostgreSQL ownership and controlled username evidence. Real or unknown owners
were protected. No `FLUSHDB`, `FLUSHALL`, broad prefix delete, PostgreSQL
deletion, credential rotation, or plan change was performed.

## Owner discovery

The Neon `manager-staging` schema was inspected with SELECT statements only.
The relevant ownership tables are `users` and `games`; career state is stored
in Redis under the user-owned career projection. 116 accounts matched
controlled audit prefixes (`pb123`, `audit`, `h3`-`h7`, `test`, `smoke`,
`perf`, or `example.invalid`). The set was treated as proven only when the
username was a controlled audit label and the Redis namespace was owned by the
exact PostgreSQL UUID. 37 owners had career roots and 16 more had only
orphaned user/world projections. All other accounts remained protected.

## Lifecycle evidence

| Operation | Source | Behaviour before H7 | Behaviour after H7 |
|---|---|---|---|
| Reset career | `CareerCommandController.resetCareer` lines 106-145 | Delegated to `CareerSessionService`, then best-effort SQL game cleanup | Same public contract; cleanup now precedes career root deletion |
| Harness replacement | `TestHarnessUseCaseImpl.createCustom` lines 190-209 | Called `deleteCareer` before creating the next fixture | Inherits complete owner-scoped cleanup |
| Career delete | `CareerSessionService.deleteCareer` lines 96-107 | Deleted only `career:{userId}` | Reads career id, stops registries, removes exact owner families, then deletes root |
| Redis adapter | `RedisCareerRepository.deleteById` lines 99-105 | Deleted only the root key | Kept as root operation; cleanup adapter handles projections |

The cleanup adapter uses bounded reactive `SCAN` patterns derived from the
authenticated owner and career IDs. It covers `career:{userId}`,
`world:{userId}`, `user:{userId}:*`, `runtime:match:{userId}:*`,
`match:state:{userId}:*`, `match:commands:{userId}:*`,
`career:{careerId}:match-detail:*`, and
`career:{careerId}:match-baseline:*`. Keys are deduplicated and deleted in
buffers of at most 100. No private test shim was added.

## Result

The exact owner set contained 1,333 keys for 37 rooted owners and 240 keys for
16 orphan-only owners. Five exact `world:{userId}` keys belonging to temporary
H7 smoke accounts were removed after reset. Cleanup used 14 batches for the
rooted set (12x100, 1x99, 1x33) and 3 batches for orphan-only data (100, 100,
40). A short-hash inventory is in
`docs/deployment/evidence/pb123h7/test-owner-inventory.json`.

Upstash moved from 268,979,370 bytes (268,435,456-byte quota) and DBSIZE 7,380
to 127 MiB reported usage and DBSIZE 6,144. A controlled PING, read, SET and
UNLINK probe succeeded after cleanup. No data outside the proven owner set was
observed in the exact key lists.

## Remaining protection

The provider dashboard is the source of storage truth. Unknown/real owners,
active runtime state, and any key that cannot be attributed by exact owner ID
remain protected. A future orphan job must use the same ownership proof and
bounded batches; it must not turn this procedure into a global prefix purge.
