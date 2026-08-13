# PB1.2.3H7.9F - Safe test-owner provenance recovery

## Scope and safety

This gate used the authenticated Neon `manager-staging` production branch and
the retained exact 144-owner Redis set. All PostgreSQL statements were
`SELECT`; Redis inspection used only exact owner keys, metadata commands and
bounded owner-scoped patterns. No account, career, Redis key, provider setting
or database row was created, changed or deleted.

## Canonical identity

| Field | Observed |
|---|---|
| Neon project | `manager-staging` |
| Branch | `production` |
| Database/schema | `neondb` / `public` |
| Endpoint | `ep-blue-wind-acaugpzx` |
| Public tables | 26 |
| Current users/games/teams | 373 / 0 / 70 |
| Flyway | 1 |
| Redis owner set | 144 |
| Owner-set SHA-256 | `6ab0903d031b9b03a5daea0f82abe6fdb38254ab26cf32a8b01c3b0351e69402` |

The current Neon browser session visibly matched the exact Render dependency;
no branch was selected by name alone.

## Provenance result

Four of the 144 current PostgreSQL users meet the positive proof standard:

- username follows the documented `audit_<12 hex>` controlled-audit form;
- email follows the `audit.pb123g.<token>@example.com` audit convention;
- all four were created in a tight burst on 2026-08-04, the execution date of
  the PB123G audit;
- each exact UUID is present in the retained Redis owner set;
- each has role `USER` and no PostgreSQL game or managed-team ownership.

The four owners are recorded only by short MD5 hashes in the machine-readable
evidence. The remaining 140 current owners are `PROTECTED_HUMAN_OR_UNKNOWN`.
They were not inspected beyond the canonical owner query. Age, inactivity,
zero games, staging, or a synthetic-looking name was never used alone as proof.

Evidence: `evidence/pb123h79f/safe-test-owner-provenance/owner-provenance.json`.

## Selected owner read-only inspection

The lowest-complexity proven owner is `6d963e62a2a6095b976ca78156a7ef0a`.
Its exact `world:{owner}` key is a legacy string with:

- serialized UTF-8 bytes: 2,483,461;
- Redis `MEMORY USAGE`: 2,483,493 bytes;
- `PTTL`: -1 (no expiry);
- SHA-256: `2fe7dff53f2c6f07d222337cdda0a6963841e229aef721d2e11a395ba3d4d68f`;
- payload owner matches the selected owner;
- 3 leagues, 70 teams and 1,680 players;
- no career root, career index, cleanup tombstone, runtime, state or command
  key for this owner.

The other three proven owners have the same read-only shape: one legacy world
key, no career root/index, no TTL, and 2,483,493 bytes of Redis physical usage.
No protected owner namespace was read.

## Reference and semantic gates

The fresh Neon schema query confirms `games.user_id -> users.id` with `CASCADE`
and `teams.manager_id -> users.id` with `NO ACTION`; the selected owner has no
rows on either surface. The selected legacy world has no career, lineup or
active-match references, so its owner reference graph is empty and
reference-safe for inspection.

The payload is `LEGACY` (no V2 storage envelope or catalog fingerprint). The
two retained catalog fingerprints were checked read-only and both keys were
absent. A semantic migration plan was therefore not admitted: the current
legacy representation has no committed catalog identity, and the independent
World V2 reconstruction evidence records material identity, timestamp and
owner-projection differences. No PREPARED or COMMITTED representation was
created.

Result: `BLOCKED_SEMANTIC`; provider capacity is an independent blocker because
Upstash exposes only `253 MB / 256 MB`, `DBSIZE=9555`, not a conservative byte
upper bound.

## Runtime and zero-write proof

The productive runtime authority is `836c98a69ce9d12f67ed603f1f1ea58b8462a82a`;
Render live is `ccb2723f9e5718d5e15150463b35756336eef260`, classified
`RUNTIME_EQUIVALENT`, single instance with autoscaling off. Fresh health after
warm-up was 2/2 liveness 200 and 2/2 readiness 200 (`database=UP`,
`redis=UP`).

Redis `DBSIZE` was 9,555 before and after. Durable Redis writes/deletes,
PostgreSQL writes, provider changes, account creation and career creation were
all zero. Readiness's existing ephemeral diagnostic probe is recorded
separately in the health evidence.

## Gate result

`PB1.2.3H7.9F ONE-OWNER CANARY BLOCKED_SEMANTIC`

The owner provenance gate is closed with positive proof, but no canary write or
migration is authorized. A future canary requires a committed semantic plan,
valid catalog admission and a provider-side storage bound.

Evidence directory:
`docs/deployment/evidence/pb123h79f/safe-test-owner-provenance/`.
