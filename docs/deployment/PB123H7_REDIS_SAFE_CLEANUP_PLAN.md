# PB1.2.3H7 - Redis safe cleanup plan

## Gate

Cleanup is **not authorized** by this run. The required complete inventory,
exact counts, and proof of test-only/orphan/expired status are unavailable while
the database is over quota and the provider CLI cannot provide memory
aggregation. This is deliberate fail-closed behavior.

## Candidate policy

Only the following may become candidates after a complete inventory:

1. keys owned by explicitly identified PB1.2.3A-H6 test accounts;
2. orphaned keys whose owner and canonical PostgreSQL record are absent;
3. expired or logically expired ephemeral sessions;
4. duplicate reconstructible snapshots with a verified canonical replacement;
5. reconstructible caches with an unambiguous rebuild path.

Protect real users, active careers, active rounds, credentials, world data with
unknown ownership, and every family classified `UNKNOWN`.

## Required execution record

Before each batch, record the sanitized pattern, exact key count, estimated
bytes, evidence, expected impact, and reconstruction/rollback path. Use
`UNLINK` in batches of 100-500 keys, verify the count and storage after every
batch, and stop on any discrepancy. Never use `FLUSHDB`, `FLUSHALL`, or a broad
unverified prefix deletion.

## Target

The recovery target is below 200 MB, preferably 160-180 MB. A reading near the
256 MB limit is not considered recovered.
