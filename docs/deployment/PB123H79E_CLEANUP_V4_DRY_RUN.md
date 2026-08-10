# PB1.2.3H7.9E - Cleanup V4 dry run

This is a plan only. No Redis command was sent.

| Field | Value |
|---|---:|
| Planned batches | 0 |
| Maximum batch | 100 |
| Planned keys | 0 |
| Actual Redis deletions | 0 |
| Root-last | yes, for any future authorized run |
| Broad-pattern deletion | no |
| FLUSHDB/FLUSHALL | no |

If the canonical gate is later completed and a user authorizes cleanup, exact
owner/career lists must be rebuilt and rechecked before batches of at most 100
keys. The intended order is runtime/state/commands, detail/baseline,
projections, world, lifecycle metadata, mappings/indexes and career roots last.

Current next action: wait for authenticated read-only access to the exact Neon
project/branch, then repeat the full-schema search.
