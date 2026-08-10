# PB1.2.3H7.9E — Redis growth root-cause analysis

## Current evidence

The provider reports `256 MB / 256 MB`, with `DBSIZE=9,638`. The retained
post-cleanup comparison is approximately `DBSIZE=6,144`, a growth of about
`3,494` keys. The global census reconciles exactly; it does not rely on a
partial or sampled key count.

## What grew

| Family | Keys | Share of key count |
|---|---:|---:|
| User projections | 8,955 | 92.9% |
| Game/index | 262 | 2.7% |
| Match baseline | 126 | 1.3% |
| Match detail | 120 | 1.2% |
| World | 102 | 1.1% |
| Career/lifecycle metadata and roots | 73 | 0.8% |

The count driver is therefore the owner projection catalog: teams, players,
leagues, standings and related game indexes under `user:{ownerId}:...`. The
storage sample points to a second, more important dimension: world snapshots
and career roots are much larger per key than projections. A 100-key sample
measured 29.8 MB of world values and 10.2 MB of career roots. These numbers are
exact for the sampled keys only and are deliberately not extrapolated.

## Ownership mismatch

All 144 Redis owner UUIDs are absent from the current PostgreSQL `users` table.
Eleven career IDs have valid internal owner mappings but those owners are also
absent from PostgreSQL. Eleven other career IDs have no owner mapping at all.
This makes stale Redis namespaces or a Redis/PostgreSQL snapshot mismatch the
strongest current explanation for the accumulated data. It is not yet proof
that every namespace is disposable test data.

The 21 current PB123 audit accounts have zero Redis keys, so the present growth
is not explained by those known test accounts. No current PostgreSQL user owns
the observed Redis data.

## Retention signals

The code contracts are approximately:

- career root: 30 days;
- world: configured 30 days;
- career index/mapping/generation: 31 days;
- match detail: configured 30 days;
- baseline: 7 days;
- runtime: 2 hours;
- state and commands: 24 hours;
- cleanup tombstone: 15 minutes.

In exact samples, roots and baselines had active TTLs. User projection/game
keys had no TTL as their current writers use persistent catalog values. Eleven
world samples and 18 detail samples had no TTL despite current configuration;
these are likely legacy/old-writer records or values written while the optional
TTL was unset. The remaining 9,538 keys were not TTL-sampled.

No runtime/state/commands keys were present, so live-session retention is not
the current count driver.

## Root-cause conclusion

The evidence supports a two-part root cause:

1. **Identity/lifecycle divergence:** Redis contains 144 owner namespaces that
   are no longer represented in the current PostgreSQL snapshot, plus 11 career
   namespaces without owner metadata.
2. **High-retention projection and snapshot footprint:** 8,955 persistent user
   projections and large world/career values remain in Redis; sampled world and
   detail values also reveal no-TTL legacy records.

The evidence does not justify naming one exact byte-dominant family for all
9,638 keys because the provider does not expose exact total bytes and a full
`MEMORY USAGE` pass was intentionally avoided.

## Required remediation before cleanup

Before any deletion authorization, reconcile that Redis and PostgreSQL belong to
the same staging snapshot, identify whether the 144 Redis-only owners are
disposable or stale real accounts, and review the legacy world/detail writers.
Only an owner-proofed, explicitly authorized batch may then be planned. No
gameplay or simulation change is required by this evidence.
