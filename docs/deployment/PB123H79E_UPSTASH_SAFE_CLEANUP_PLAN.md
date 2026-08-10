# PB1.2.3H7.9E — Safe cleanup plan (not authorized)

## Status

`SAFE_CLEANUP_BLOCKED`

This is a precomputed plan only. It contains zero batches because the current
proven audit owners have zero attributable Redis keys. No delete command was
executed.

## Candidate reconciliation

| Field | Value |
|---|---:|
| Candidate keys discovered | 0 |
| Unique candidate keys | 0 |
| Duplicates removed | 0 |
| Planned future UNLINK requests | 0 |
| Maximum future batch size | 100 |
| Foreign-owner keys | 0 |
| Unknown-owner keys | 0 |
| Ambiguous-owner keys | 0 |
| Exact candidate bytes | 0 |
| Sampled candidate bytes | 0 |
| Unknown candidate bytes | 0 |

The reconciliation equation is therefore `candidateDiscovered = candidateUnique
= plannedUNLINKRequested = 0`.

## Future batch contract

If a later, explicitly authorized inventory discovers a proven owner key, each
batch must contain at most 100 exact keys and must be revalidated immediately
before deletion. The order is:

1. runtime, state and commands;
2. match detail and baseline;
3. user projections and world;
4. manifest, mapping, generation, index and tombstone metadata;
5. career root last.

The current production cleanup lifecycle remains the canonical order. A key is
excluded if its owner mapping is missing, contradictory, or cannot be tied to
the exact PostgreSQL owner. A future batch must record owner hash, career hash,
family, exact key list, ownership proof, measured bytes (or `UNKNOWN`), and the
post-batch `DBSIZE`/provider usage observation.

## Rollback and reconstruction

No rollback payload is produced by this inventory. Career/world data is
reconstructible only where the application contract explicitly supports it;
Redis deletion must not be treated as reversible. PostgreSQL is not mutated
before Redis ownership has been retained for audit. Tombstones and discovery
metadata must remain available until a complete lifecycle operation converges.

## Projected state (zero-action dry run)

| Metric | Current | Projected |
|---|---:|---:|
| DBSIZE | 9,638 | 9,638 |
| Storage | 256 MB / 256 MB | 256 MB / 256 MB |
| Freed bytes | n/a | 0 |
| Below 200 MB | no evidence | no |
| Preferred 160–180 MB | no evidence | no |

The dry-run record is
`evidence/pb123h79e/quota-cleanup-dry-run.json`.

## Authorization boundary

Actual deletions: **0**. Cleanup authorized: **NO**. The next action is to
wait for explicit user authorization only after a new owner-scoped inventory
produces a non-empty, fully attributable candidate set. Broad discovery or
 protected-owner cleanup is not an acceptable substitute.
