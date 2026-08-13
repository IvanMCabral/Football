# PB1.2.3H7.9F — Upstash Management Stats Recovery

**Observed:** 2026-08-13T17:08:35Z
**Scope:** read-only recovery of the documented Upstash management statistics
surface. No API key was created, rotated or deleted. No Redis, PostgreSQL,
provider, billing or application mutation was performed.

## Result

`USER_INTERACTION_REQUIRED_UPSTASH_MANAGEMENT_API_KEY`

An Upstash Management API key is required to call the documented GET database
stats endpoint. No key was created.

## Official contract verified

The official contract is:

* endpoint: `GET /v2/redis/stats/{id}`;
* authentication: HTTP Basic using the account email and Management API key;
* response field: `current_storage`, documented as the current storage used in
  bytes.

Sources: [Get Database Stats](https://upstash.com/docs/devops/developer-api/redis/get_database_stats),
[Upstash API authentication](https://upstash.com/docs/devops/developer-api/authentication),
and [Developer API setup](https://upstash.com/docs/devops/developer-api/introduction).

## Account inspection

The existing authenticated Upstash console was opened at Account Settings →
Developer API. The page showed **Create API key** and no existing usable key.
The Create action was not selected. This is classified as `NO_API_KEY`, not as
an authentication failure and not as permission to create credentials.

The database identity was recovered from the existing Manager console URL and
recorded only as this SHA-256 hash:

`146284aa2fc364dddbeeb54b92f570b3980cb5dadadbd3b363ec88538402094e`

| Field | Value |
|---|---|
| Database | `Manager` |
| Database ID recovered | `YES` (hash above only) |
| Plan | `Free Tier` |
| Region | `AWS sa-east-1` |
| Management API key state | `NO_API_KEY` |
| Key created | `NO` |
| Stats endpoint called | `NO — no key available` |
| Stats endpoint reachable | `NOT_TESTED` |

## Current provider baseline

Read-only CLI checks remain:

* storage display: `253 MB / 256 MB`;
* `PING`: `PONG`;
* `DBSIZE`: `9555`.

No exact `current_storage` value was obtained. Provider accounting therefore
remains `PROVIDER_ACCOUNTING_UNBOUNDED`; exact quota, available headroom and
provider-specific transient uncertainty remain unknown.

## Health after inspection

Two bounded public rounds were executed without changing infrastructure:

| Round | Liveness | Readiness | Database | Redis |
|---:|---:|---:|---|---|
| 1 | 200 (`UP`) | 503 (`DOWN`) | DOWN | UP |
| 2 | 200 (`UP`) | 503 (`DOWN`) | DOWN | UP |

This does not alter the accounting result and does not authorize a canary.

## Authorization boundary

* canary execution authorized: `NO`;
* bulk migration authorized: `NO`;
* cleanup authorized: `NO`;
* user interaction required: `YES`.

Required human action, if exact provider accounting is still desired:

> Create or provide an already-existing Upstash Management API key through the
> official console, then report that it is available. Do not paste the key into
> chat or commit it. This task intentionally did not create one.

The prior semantic plan, selected owner provenance and migration evidence were
not reopened.

## Definitive recovery recheck (2026-08-13)

The Developer API page now shows an existing key named `ManagerKey` with a
future expiry date. The secret is not displayed or recoverable from the
console, and no local runtime environment contains an Upstash Management API
key. Classification: `KEY_EXISTS_BUT_SECRET_NOT_AVAILABLE`.

The documented stats request was therefore not attempted; no credential was
printed, persisted or transmitted. `current_storage` remains unavailable and
the accounting classification remains `PROVIDER_ACCOUNTING_UNBOUNDED`.

Fresh read-only checks confirmed `PING=PONG`, `DBSIZE=9555`, and the planned
catalog key remains absent. Health recovered without a restart or deployment:
liveness `2/2` HTTP 200 and readiness `2/2` HTTP 200 with `database=UP` and
`redis=UP`. This is classified as `TRANSIENT_DATABASE_RECOVERY`.

User interaction remains required: provide the already-created Management API
key through a secure runtime channel (never chat, Markdown, JSON or Git). No
new key should be created, rotated or deleted.

## Secure credential stats recheck (2026-08-13)

Both secure runtime variable names were present and were used only for the
single documented GET request. The provider returned HTTP `401 Unauthorized`;
no credential material, Authorization header or response secret was recorded.
Consequently `current_storage`, exact quota and available headroom remain
unknown. The accounting gate is blocked by provider authentication, not by a
capacity conclusion.

After a bounded warm-up, the required read-only health gate passed: liveness
`2/2` HTTP 200 and readiness `2/2` HTTP 200, both reporting `database=UP` and
`redis=UP`. No Redis, PostgreSQL, catalog, deployment, billing or credential
mutation was performed.

## Authenticated Chrome recovery attempt (2026-08-13)

The existing authenticated native Upstash Chrome session verified the
Personal account context, database `Manager`, Free Tier, AWS `sa-east-1`, and
the Developer API page. Two existing key metadata rows were visible
(`Manager2` and `ManagerKey`); their secrets were not displayed or read. No
key was created, rotated or deleted.

The documented Basic-authenticated `GET /v2/redis/stats/{databaseId}` was
attempted with the secure runtime pair and returned HTTP `401 Unauthorized`.
The browser session itself could not call the cross-origin API endpoint
(`ERR_BLOCKED_BY_CLIENT`), so it cannot substitute for Developer API Basic
authentication. No response body, cookie, token or Authorization header was
persisted.

This proves an authentication failure for the supplied pair but does not
distinguish the email/key account mismatch from an invalid or expired key.
Because the console can replace keys but the existing secrets are unrecoverable,
user interaction is required before another accounting attempt. Exact usage,
quota and headroom remain unknown. The read-only health gate passed after a
bounded retry: liveness `2/2` and readiness `2/2` HTTP 200 with database and
Redis `UP`.
