# PB1.2.3H7 - Redis storage inventory

## Scope and safety

This is a read-only inventory of the authenticated Upstash `Manager` database.
No key was deleted and no payload containing user data, credentials, or tokens
was exported. The inventory is intentionally fail-closed because the provider
is already over its free-tier quota.

## Provider evidence

| Measurement | Observed value |
|---|---:|
| Plan | Free Tier |
| Region | `sa-east-1` (Sao Paulo) |
| Dashboard storage | 257 MB / 256 MB |
| Raw provider quota error | 268,979,370 / 268,435,456 bytes |
| Commands | 52K / 500K |
| Cost | USD 0.00 |
| DBSIZE | 7,380 keys |

The quota error was returned by a read-only command:

`ERR DB capacity quota exceeded. Threshold: 268435456 bytes, Usage: 268979370 bytes.`

This is direct evidence for classification **P - FREE-TIER STORAGE LIMIT
EXCEEDED**. Redis commands that allocate or aggregate memory are rejected while
the quota is exceeded.

## Key families observed

Incremental `SCAN` was used; `KEYS *` was not used. The following families were
observed in the returned key names (identifiers are not reproduced here):

| Family | Evidence | Classification | Deletion decision |
|---|---:|---|---|
| `career:{id}` | present | Durable/reconstructible only after ownership verification | Protect |
| `career:{id}:match-baseline:{match}` | 271 keys in one validated `SCAN MATCH` pass | Reconstructible with seven-day retention | Protect until full inventory |
| `career:{id}:match-detail:{match}` | present | Durable/reconstructible status not yet proven for every row | Protect |
| `world:{id}` | code-defined, not safely counted while CLI bridge degraded | Durable/reconstructible only after ownership verification | Protect |
| `match:commands:{...}` | code-defined, 24-hour TTL | Ephemeral | Do not delete without exact count |
| Runtime/live/session families | code-defined, short TTL | Ephemeral | Do not delete without exact count |

The application source confirms the intended retention policy for several
families: career 30 days, standings 30 days, runtime match 2 hours, match state
24 hours, commands 24 hours, and baseline 7 days. `world:{userId}` is documented
without an automatic TTL. These are code-level policies, not proof that every
existing key currently has the expected TTL.

## Measurement limits

The Upstash web CLI exposed the following provider limitations during the
read-only run:

- `MEMORY STATS` returned `ERR Command is not available: 'MEMORY STATS'`.
- `INFO MEMORY` returned no usable memory section.
- An aggregation attempt using `EVAL` was rejected with the quota error above.
- The browser CLI bridge then timed out locating `input.upstash-cli-stdin` while
  the console was reloading, so a complete multi-page scan could not be
  validated without risking stale or partial counts.

One representative `MEMORY USAGE` sample was 43,380 bytes for a baseline key.
That sample is not extrapolated to the database and is not a top-30 claim.

Consequently, exact total bytes by prefix, top-30 sizes, complete TTL buckets,
or full orphan/duplicate cardinalities are **not claimed**. No safe candidate
can be proven from this partial inventory.

## Root-cause conclusion

The storage quota breach is confirmed. The observed baseline/detail families are
the leading application-level consumers, but a dominant family percentage and
per-user full-world replication cannot be established safely until the provider
accepts read-only inspection again or an authorized export is available.
