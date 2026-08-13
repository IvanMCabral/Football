# PB1.2.3H7.9F — One-owner World V2 canary definitive dry-run

**Mode:** read-only, zero provider writes.  The canary was not executed.

## Result

`PB1.2.3H7.9F ONE-OWNER CANARY BLOCKED_RUNTIME`

The mandatory runtime precheck found a changed live commit.  The gate requires
an exact runtime identity before owner discovery or any migration planning:

| Check | Required | Observed | Result |
|---|---|---|---|
| Render service | `srv-d9nvldtaeets73coqiog` | `srv-d9nvldtaeets73coqiog` | PASS |
| Expected live SHA | `223fd8913cf3b753a75da48a677ea0e629dd52de` | `a9f5b51040d44f83cb38f8c2f5fbd468365dcebc` | FAIL |
| Latest productive runtime reference | `836c98a69ce9d12f67ed603f1f1ea58b8462a82a` | unchanged reference | INFO |
| Deployment | Live / Deployed | Live / Deployed | PASS |
| Instances | 1 | 1 | PASS |
| Autoscaling | OFF | OFF | PASS |

Because the SHA changed, the gate's stop condition
`CANARY_BLOCKED_RUNTIME_CHANGED` applies.  No candidate owner was selected,
no world payload was read, and no semantic or capacity plan was admitted.

## Read-only prechecks

Render health was sampled after a bounded warm-up:

- liveness: 2/2 HTTP 200, `{"status":"UP"}` (388 ms, 284 ms);
- readiness: 2/2 HTTP 200, `database=UP`, `redis=UP` (1294 ms, 788 ms).

The Upstash Manager dashboard was read without copying credentials:

- database: Manager;
- plan: Free Tier;
- region: AWS sa-east-1;
- displayed storage: 253 MB / 256 MB;
- commands: 150K / 500K per month;
- bandwidth: 0 B / 50 GB;
- `PING`: `PONG`;
- `DBSIZE`: 9555.

The provider display is not an exact byte counter.  The official pricing page
confirms a 256 MB maximum data size, but it does not establish the dashboard's
rounding or transient-write accounting.  It is therefore classified as
`PROVIDER_ACCOUNTING_TOO_COARSE` for a capacity authorization.

## Owner and migration boundary

The retained PostgreSQL/Redis reconciliation evidence identifies current users
for the known Redis owner set, and the proven audit-owner set has no current
owner-scoped Redis keys.  Since the runtime identity failed first, this run did
not perform a new owner payload read, did not choose a real account, and did not
rely on historical candidate data as current canary evidence.

Consequently the following were intentionally not produced:

- legacy world checksum, serialized bytes, `MEMORY USAGE` and `PTTL`;
- reference inventory and semantic comparator result;
- catalog existence/fingerprint validation;
- PREPARED/COMMITTED representations;
- transient peak and remaining-headroom calculation;
- canary plan hash.

## Zero-write proof

| Surface | Operations in this run |
|---|---:|
| Redis durable migration writes/deletes | 0 |
| Redis health-probe ephemeral operations | readiness invokes an ephemeral set/read/delete probe |
| PostgreSQL writes | 0 |
| Render mutations | 0 |
| Migration transitions | 0 |
| PREPARED/COMMITTED/catalog writes | 0 |
| Account/career creation | 0 |

The Upstash console commands were read-only `PING` and `DBSIZE`.  The two
readiness requests necessarily exercised the product's Redis health probe,
which creates, reads and deletes an ephemeral diagnostic key.  That side
effect is not a migration write and no durable owner/catalog key was touched,
but it means a literal provider-level zero-write claim is not made.  No
`SCAN`, owner `GET`, `UNLINK`, `EXPIRE`, `PEXPIRE`, `SADD`, `HSET` or mutating
`EVAL` was used by the dry-run itself.

## Authorization boundary

`CANARY EXECUTION AUTHORIZED = NO`
`BULK MIGRATION AUTHORIZED = NO`
`CLEANUP AUTHORIZED = NO`

The next admissible run must first reconcile the service back to the expected
SHA (or issue a new gate with an explicitly updated expected SHA), then repeat
the owner-scoped, read-only planning sequence.  No provider change is implied
by this report.

## H7.9F runtime-equivalence reconciliation (2026-08-13)

The prior exact-SHA stop is superseded by the runtime-authority check in
`PB123H79F_RUNTIME_EQUIVALENCE_FOR_CANARY.md`. The Render live commit
`ccb2723f9e5718d5e15150463b35756336eef260` contains the productive runtime
authority `836c98a69ce9d12f67ed603f1f1ea58b8462a82a`; every descendant after
that authority was inspected and is tests, tooling, documentation or evidence
only. The current runtime classification is `RUNTIME_EQUIVALENT`.

Health remained green (pre and post 2/2 liveness/readiness, database and Redis
UP). Upstash remained at `253 MB / 256 MB`, `DBSIZE=9555`, with exact byte
accounting unavailable. Canonical owner evidence still yielded zero eligible
disposable test owners: all 144 exact Redis owners collide with current
PostgreSQL users and the 21 proven audit owners have no current owner-scoped
Redis keys. The reconciled verdict is therefore
`PB1.2.3H7.9F ONE-OWNER CANARY BLOCKED_OWNER`.

No world payload, catalog, reference graph or semantic representation was
read; no migration or cleanup authorization exists.

## H7.9F safe-owner provenance recovery (2026-08-13)

The exact Neon `manager-staging` / `production` / `neondb` authority was
verified read-only. The retained 144-owner set was reused without a global
Redis scan. Fresh canonical owner queries proved four controlled PB123G audit
owners by independent username, email and timestamp signals; the remaining
140 current owners remain protected. Evidence is in
`evidence/pb123h79f/safe-test-owner-provenance/owner-provenance.json`.

One proven owner was inspected with exact Redis reads only. Its legacy world
matches the owner, is 2,483,461 serialized bytes (2,483,493 Redis physical
bytes), has no TTL, and has no career root/index or active child families. The
payload contains 3 leagues, 70 teams and 1,680 players. The retained catalog
fingerprints were absent. Because the current legacy payload has no committed
catalog identity and the independent reconstruction proof records material
semantic differences, the migration planner was not admitted and no
PREPARED/COMMITTED state was created.

Fresh health after warm-up was 2/2 liveness and 2/2 readiness with database and
Redis UP. Upstash remained at `253 MB / 256 MB` and `DBSIZE=9555`; provider
byte accounting is still coarse. Durable Redis writes/deletes and PostgreSQL
writes remained zero.

Current result: `PB1.2.3H7.9F ONE-OWNER CANARY BLOCKED_SEMANTIC`.
