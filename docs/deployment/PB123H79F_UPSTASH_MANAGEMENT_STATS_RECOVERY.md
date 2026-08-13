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
