# PB1.2.3H7.9E — Safe candidate set V2

## Status

`SAFE_CANDIDATE_SET_INSUFFICIENT`

The global census found exact names for all 9,638 keys, but no current Redis
owner is present in PostgreSQL and no known PB123 disposable account owns a
key. The fail-closed V2 set therefore contains zero deletion-eligible keys.

## Classification

| Group | Keys | Treatment |
|---|---:|---|
| Proven disposable PB123 test owners | 0 | No current Redis keys |
| PostgreSQL-absent owner namespaces | 9,478 | `POTENTIAL_ORPHAN`; protected pending reconciliation |
| Career namespaces without owner mapping | 160 | `POTENTIAL_ORPHAN`; protected |
| Foreign/contradictory/unknown keys | 0 | None observed |
| Safe deletion-eligible V2 keys | 0 | No future `UNLINK` batch |

The 9,478 owner-scoped keys are attributable to exact UUIDs in their key names,
and the 160 career keys are attributable to exact career IDs. That proves
namespace attribution, not disposable ownership. The current DB absence could
be caused by stale Redis, a provider/database snapshot mismatch, or deleted
users. The safe set remains empty until that distinction is resolved.

## Dry run

`discovered=0`, `unique=0`, `plannedUNLINK=0`, `foreign=0`, `unknown=0` for the
safe set. Projected state is unchanged: `DBSIZE=9,638`, storage display
`256 MB / 256 MB`, and zero freed bytes. The potential orphan keys are not
counted as safe freed storage.

Actual deletions: **0**. Cleanup authorized: **NO**. Next action:
`WAIT FOR EXPLICIT CLEANUP AUTHORIZATION`.

Machine-readable evidence:

- `evidence/pb123h79e/safe-candidate-set-v2.json`
- `evidence/pb123h79e/safe-cleanup-v2-dry-run.json`
- `evidence/pb123h79e/global-keyspace-orphans.json`
