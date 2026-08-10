# PB1.2.3H7.9E — Orphan cleanup plan (not executed)

## Preconditions

Cleanup must remain stopped until all of the following are freshly proven:

1. Render PostgreSQL dependency and Neon branch are the same canonical
   database.
2. A SELECT-only scan covers every canonical ownership column/table, not just
   `users`.
3. Every Redis owner and career ID in the candidate set is absent from that
   authority and has no contradictory Redis mapping.
4. A human explicitly authorizes deletion.

## Future execution boundary

If authorization is later granted, use only exact owner/career key lists from a
new evidence file, with batches of at most 100 keys and `UNLINK` preferred.
Re-check ownership before every batch, record `DBSIZE` and provider storage
after each batch, and stop on any discrepancy. Preserve root-last lifecycle
ordering and do not use `FLUSHDB`, `FLUSHALL`, broad prefixes or SQL-first
deletion.

The current planned batch count is zero and the current planned deletion count
is zero. No cleanup command, account deletion, PostgreSQL mutation or Redis
mutation was executed.

## Current gate

`ORPHAN_CLEANUP_BLOCKED` — canonical PostgreSQL authority and Neon snapshot
identity are not freshly verifiable from the current authenticated sessions.
