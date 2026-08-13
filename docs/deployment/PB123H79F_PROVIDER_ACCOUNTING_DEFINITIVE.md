# PB1.2.3H7.9F — Provider accounting evidence

This document records the provider-side baseline used by the one-owner dry-run.
It is read-only evidence; no Redis data or configuration was changed.

## Observed dashboard values

| Field | Value |
|---|---|
| Database | Manager |
| Plan | Free Tier |
| Region | AWS sa-east-1 (Sao Paulo) |
| Storage display | 253 MB / 256 MB |
| Commands | 150K / 500K per month |
| Bandwidth | 0 B / 50 GB |
| TLS | Enabled |
| `PING` | `PONG` |
| `DBSIZE` | 9555 |

## Accounting classification

`PROVIDER_ACCOUNTING_TOO_COARSE`

The dashboard exposes an integer MB display only.  No exact byte API was used
and no undocumented conversion from the display to bytes was assumed.  The
official Upstash pricing reference states that the Free plan has a 256 MB
maximum data size and that total storage includes the data stored at replicas
and regions:

<https://upstash.com/pricing/redis>

That source does not define whether the displayed integer is rounded, truncated,
bucketed, allocator-backed, or inclusive of provider metadata for this account.
Therefore the lower/upper byte bounds for the current dataset remain
`UNKNOWN`, and no capacity gate can be approved from `253 MB` alone.

## Consequence

The dry-run stopped earlier on `CANARY_BLOCKED_RUNTIME_CHANGED`.  Even after
runtime reconciliation, a future canary must obtain a provider-side accounting
bound conservative enough to prove the transient peak plus safety reserves.  A
local Redis `MEMORY USAGE` estimate is not interchangeable with Upstash durable
storage accounting.

No credentials, tokens, payloads, keys, or personal data are included here.

## Rechecked on 2026-08-13

The authenticated Details and Usage surfaces still expose only `253 MB / 256
MB`; no precise byte field, metric, or documented account-level conversion was
available. Read-only CLI checks returned `PING=PONG` and `DBSIZE=9555`.

The classification remains `PROVIDER_ACCOUNTING_TOO_COARSE`: both lower and
upper provider byte bounds are unknown. The official Upstash pricing page
confirms the Free maximum data size of 256 MB and explains that total storage
is calculated across replicas/regions, but does not establish dashboard
rounding or same-key transient-overlap semantics. No provider settings,
credentials, data, or billing state were changed.

## Management Stats Recovery (2026-08-13)

The authenticated console was inspected at Account Settings → Developer API.
It showed only **Create API key** and no existing usable key. The create action
was not selected. The management API state is therefore `NO_API_KEY` and the
documented `GET /v2/redis/stats/{id}` request was not sent.

The official contract documents HTTP Basic authentication and a byte-valued
`current_storage` response field:

* <https://upstash.com/docs/devops/developer-api/authentication>
* <https://upstash.com/docs/devops/developer-api/redis/get_database_stats>

Exact current storage remains unavailable until an already-existing key is
provided. No key was created. Two fresh health rounds returned liveness 200/200
but readiness 503/503 with `database=DOWN` and `redis=UP`; this is recorded in
`evidence/pb123h79f/upstash-management-stats-recovery/health.json`.

## Definitive recovery recheck (2026-08-13)

The console now lists an existing `ManagerKey`, but does not reveal its secret.
No API key is available to the local runtime, so the Management API stats call
remains unexecuted and `current_storage` remains unknown. No credential was
created, rotated, deleted, logged or stored.

Read-only Redis checks remain `PING=PONG`, `DBSIZE=9555`, with the planned
catalog key absent. The service recovered naturally: liveness `2/2` and
readiness `2/2` are HTTP 200 with `database=UP` and `redis=UP`. The prior
readiness failure is classified as `TRANSIENT_DATABASE_RECOVERY`.

## Secure credential stats recheck (2026-08-13)

The secure runtime variable names were present. The single documented
Management API GET returned HTTP `401 Unauthorized`; no credential material,
Authorization header or response secret was persisted. Exact
`current_storage`, exact quota and available headroom therefore remain
unknown; the minimum known required headroom remains `2,436,344` bytes and the
capacity classification remains `PROVIDER_ACCOUNTING_UNBOUNDED`.

After a bounded warm-up, liveness and readiness both passed `2/2` at HTTP 200
with `database=UP` and `redis=UP`. This does not close the accounting gate or
authorize a canary.

## Authenticated Chrome recovery attempt (2026-08-13)

The authenticated native Upstash session verified the Personal account,
database `Manager`, Free Tier and AWS `sa-east-1`. The Developer API page
listed two existing key metadata rows, `Manager2` and `ManagerKey`; neither
secret was exposed or read, and no key mutation occurred.

The single documented stats GET, using the secure runtime pair, returned HTTP
`401 Unauthorized`. A browser-session request to the cross-origin API was
blocked by the client, so console authentication cannot replace Basic API-key
authentication. Exact storage and quota remain unknown; accounting remains
`PROVIDER_ACCOUNTING_UNBOUNDED` and the one-owner canary is not authorized.

After bounded retry, liveness and readiness both passed `2/2` at HTTP 200 with
`database=UP` and `redis=UP`.

## Key replacement and definitive stats (2026-08-13)

Exactly one authorized key, `H79FAccounting`, was created. Its secret was used
only transiently and was not printed or persisted; no prior key was deleted or
rotated. The database-list GET returned HTTP `200` and the stats GET returned
HTTP `200` with exact `current_storage=264,967,931` bytes. No exact quota field
was returned, and the rounded dashboard label is not treated as an exact byte
quota. Consequently available headroom and cushion remain unknown and the
capacity classification remains `PROVIDER_ACCOUNTING_UNBOUNDED`.
The returned `total_monthly_storage` is a usage metric, not a quota.

Read-only validation remains `PING=PONG`, `DBSIZE=9555`, catalog absent,
liveness/readiness `2/2` HTTP 200, and database/Redis `UP`. The canary,
migration and cleanup remain unauthorized.
