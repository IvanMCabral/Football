# PB1.2.3H7 - Redis cleanup execution

The initial section of this report was a fail-closed plan written before
owner evidence was available. It is retained in the historical H7 plan. The
execution below records the completed owner-driven recovery.

## Owner-driven execution

| Measure | Before | After |
|---|---:|---:|
| Upstash bytes | 268,979,370 | 127 MiB reported |
| Quota | 268,435,456 bytes | 256 MiB |
| DBSIZE | 7,380 | 6,144 |
| Proven rooted owners | 0 in provisional plan | 37 |
| Proven orphan-only owners | 0 in provisional plan | 16 |
| Foreign keys in exact deletion set | not applicable | 0 |

The exact deletion set contained 1,333 keys for rooted owners and 240 keys for
orphan-only owners. Five additional exact world keys from disposable H7 smoke
accounts were removed after reset. Rooted cleanup used 14 batches (100 keys in
each of the first 12, then 99 and 33); orphan cleanup used 100, 100 and 40.
Every batch was followed by an exact count and DBSIZE check. `UNLINK` was used
for the provider-side manual cleanup. No `FLUSHDB`, `FLUSHALL`, broad prefix,
or SQL deletion was used.

Per-key `TYPE`, `TTL/PTTL` and selected `MEMORY USAGE` samples were collected
without reading complete payloads. A sanitized owner inventory is stored at
`docs/deployment/evidence/pb123h7/test-owner-inventory.json`.

## Post-cleanup controls

The application lifecycle now performs bounded reactive owner cleanup through
`CareerDataCleanupRepository` before deleting the career root. The adapter
scans only exact owner/career-derived patterns and buffers at most 100 keys.
This prevents the audit harness and public reset flow from recreating the same
orphan families.

Post-cleanup validation is recorded in
`PB123H7_REDIS_RECOVERY_VALIDATION.md`. PostgreSQL was intentionally left
unchanged so ownership evidence remained available during cleanup.
