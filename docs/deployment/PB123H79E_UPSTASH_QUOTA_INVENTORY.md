# PB1.2.3H7.9E — Upstash quota owner-scoped inventory

## Verdict

`SAFE_CLEANUP_BLOCKED`

This execution is an inventory and dry-run only. No Redis key was deleted and
no PostgreSQL row was changed. The next cleanup action requires explicit user
authorization after a non-empty, owner-proven candidate set exists.

## Provider baseline

Observed 2026-08-10 at 17:02 (America/Argentina/Buenos_Aires) in the
authenticated Upstash console and CLI:

| Item | Observation |
|---|---|
| Database | `Manager` |
| Plan | Free Tier |
| Region | AWS `sa-east-1` (Sao Paulo) |
| Storage | `256 MB / 256 MB` (provider display; exact bytes unavailable) |
| Quota | `268,435,456` bytes |
| Commands | `131K / 500K` per month |
| Bandwidth | `0 B / 50 GB` |
| Cost | `USD 0.00` |
| PING | `PONG` |
| DBSIZE | `9,638` |

Only `PING`, `DBSIZE`, exact `EXISTS`, and owner-scoped `SCAN MATCH` reads were
used. No `DEL`, `UNLINK`, `FLUSH*`, `KEYS *`, `EVAL`, `MEMORY STATS`, or global
scan was executed.

Machine-readable baseline: `evidence/pb123h79e/quota-cleanup-before.json`.

## Current source of key ownership

The inventory follows the current `RedisCareerDataCleanupRepository` and the
current Redis adapters, not historical key lists. The exact templates are:

| Family | Current template | Expected lifecycle |
|---|---|---|
| Career root | `career:{ownerId}` | root deleted last; 30-day TTL |
| World | `world:{ownerId}` | configured 30-day TTL |
| User projections | `user:{ownerId}:*` (game keys protected) | owner-scoped discovery |
| Career index | `user:{ownerId}:career-ids` | 31-day TTL |
| Runtime | `runtime:match:{ownerId}:*` | 2-hour TTL |
| Match state | `match:state:{ownerId}:*` | 24-hour TTL |
| Commands | `match:commands:{ownerId}:*` | 24-hour TTL |
| Owner mapping | `career-owner:{careerId}` | 31-day TTL |
| Generation | `career-generation:{careerId}` | 31-day TTL |
| Mapping token | `career-mapping-token:{careerId}` | 31-day TTL |
| Manifest | `career-cleanup-members:{careerId}` | 31-day lifecycle metadata |
| Manifest version | `career-cleanup-manifest-version:{careerId}` | 31-day lifecycle metadata |
| Match detail | `career:{careerId}:match-detail:*` | configured 30-day TTL |
| Match baseline | `career:{careerId}:match-baseline:*` | 7-day TTL |
| Cleanup tombstone | `career-cleanup:{ownerId}` | 15-minute retry window |

The cleanup implementation also protects `user:{ownerId}:game:*` and
`user:{ownerId}:game-ids`; those game namespaces were not candidates.

## Owner discovery (PostgreSQL, SELECT-only)

The live schema exposed `users` and `games`. Seventy-three users and zero game
rows were inspected. Twenty-one users form a controlled audit cohort: the
identifiers use the repository's audit naming convention (`audit` or
`audit_<12 hex>`), test-only address forms, and a tight creation burst on
2026-07-31. Every row has role `USER` and zero PostgreSQL game ownership. The
PB123 audit reports corroborate this controlled identity convention; that
historical material is provenance evidence only, not a current Redis result.

The remaining 52 users are protected. No namespace discovery was attempted for
them. No ambiguous owner was promoted to a deletion candidate.

The 21 audit owners are classified `ABANDONED_TEST`: they have no current game
ownership and no current owner-scoped Redis namespace. Their short ID hashes are
in `evidence/pb123h79e/quota-owner-inventory.json`; full IDs and personal fields
are intentionally omitted from evidence.

## Owner-scoped Redis result

For each of the 21 proven audit owners the following exact checks returned no
key: career root, world, career index, and cleanup tombstone. A further 84
owner-scoped scans covered user projections, runtime, match state, and commands;
all returned cursor `0` with no matching key. No career ID could therefore be
derived, so career-scoped mappings, manifests, details, and baselines had no
safe target.

| Measure | Result |
|---|---:|
| Proven audit owners checked | 21 |
| Protected owners | 52 |
| Owner-scoped scans | 84 |
| Non-empty owner-scoped scans | 0 |
| Career IDs discovered | 0 |
| Candidate keys | 0 |
| Foreign-owner keys in candidate set | 0 |
| Unknown-owner keys in candidate set | 0 |
| Ambiguous-owner keys in candidate set | 0 |

The family-level machine-readable inventory is
`evidence/pb123h79e/quota-key-family-inventory.json`.

## Size, growth and retention findings

There were no candidate keys on which `TYPE`, `TTL/PTTL`, or `MEMORY USAGE`
could safely be run. Candidate exact bytes are therefore zero; protected-owner
bytes remain intentionally unknown. The provider's rounded storage display
does not expose an exact byte count in the current UI.

The historical healthy comparison of approximately 6,150 keys versus the
current 9,638 is a delta of approximately 3,488 keys. Because global scans and
protected-owner discovery are prohibited in this phase, the dominant family
cannot be attributed honestly. The evidence does not support claiming that
world snapshots, detailed matches, baselines, live state, or audit accounts are
the dominant contributor.

Source inspection confirms bounded TTLs for most families, but observed TTL
classification for protected owners is intentionally `UNKNOWN`. The complete
retention matrix is in `evidence/pb123h79e/quota-retention-audit.json`.

## Dry-run projection

The candidate set is empty, so the dry run reconciles exactly at zero requested
future `UNLINK` operations. Projected state is unchanged: `DBSIZE=9,638` and
provider storage remains `256 MB / 256 MB`. The safe candidate set cannot prove
recovery below 200 MB or the preferred 160–180 MB range.

Dry-run evidence: `evidence/pb123h79e/quota-cleanup-dry-run.json` and
`evidence/pb123h79e/quota-safe-candidate-set.json`.

## Safety conclusion

The only safe conclusion is `SAFE_CLEANUP_BLOCKED`: the proven audit owners
currently own no Redis keys. Expanding discovery to the 52 protected users,
using broad prefixes, or deleting by historical identity would violate the
owner-scoped fail-closed rule. No deletion authorization is requested by this
 report.
