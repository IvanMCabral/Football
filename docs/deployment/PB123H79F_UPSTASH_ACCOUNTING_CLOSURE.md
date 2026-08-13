# PB1.2.3H7.9F — Upstash exact headroom and accounting closure

**Observation:** 2026-08-13T16:55:18Z
**Scope:** authenticated, read-only provider forensics. No migration, cleanup,
Redis write, PostgreSQL write, account creation, provider change or billing
change was performed.

## Result

`PB1.2.3H7.9F ONE-OWNER CANARY BLOCKED_ACCOUNTING`

The semantic plan remains valid and deterministic, but the provider does not
expose enough exact byte information in the authenticated dashboard surface to
prove a conservative admission bound. This is an accounting blocker, not a
semantic or ownership blocker.

The canonical accounting plan is
`evidence/pb123h79f/upstash-accounting-closure/accounting-plan.json` with
`accountingPlanSha256=449fc3e0e78f5417f38233e1730c76cd5a17521ea211162a2b3d2f7aefa2b18f`.
The hash is calculated over the canonical UTF-8 artifact with its
`accountingPlanSha256` member set to `null`.

## Fresh provider baseline

| Field | Observation |
|---|---|
| Database | `Manager` |
| Plan | `Free Tier` |
| Region | AWS Sao Paulo, Brazil (`sa-east-1`) |
| Storage display | `253 MB / 256 MB` |
| Dashboard progress | `98.7082%` (rendered progress width; not treated as byte evidence) |
| Monthly commands | `150K / 500K` |
| Bandwidth | `0 B / 50 GB` |
| Cost | `$0.00` |
| PING | `PONG` |
| DBSIZE | `9555` |

The CLI recheck also confirmed the selected owner world key at `2,483,493`
bytes with `PTTL=-1`, and the planned catalog key was absent. These are Redis
read observations, not provider quota measurements.

## Exactness assessment

* **Exact byte field found in the dashboard:** **NO**. The rendered dashboard
  and its data-size chart expose only integer `MB` labels.
* **Official field available in principle:** **YES**. The official Upstash
  Get Database Stats schema documents `current_storage` in bytes, but no
  authenticated response containing the current value was available through
  the connected read-only dashboard session.
* **Display unit:** `MB`, provider semantics not specified as decimal MB or
  MiB in the observed page.
* **Display rounding semantics:** `UNKNOWN`. The HTML exposes a rounded label
  and a CSS progress percentage; neither proves truncation, floor, ceiling or
  nearest rounding of provider bytes.
* **Quota exact bytes:** `UNKNOWN`. Official pricing states a Free max data size
  of `256 MB`, but does not establish the byte conversion used by this database
  surface.
* **Provider used exact:** `UNKNOWN`.
* **Provider lower bound:** `UNKNOWN`.
* **Provider upper bound:** `UNKNOWN`.
* **Transient overwrite accounting:** `OVERWRITE_TRANSIENT_UNKNOWN`. Official
  material reviewed does not establish whether old and new values coexist for
  admission accounting, nor whether replicas are included for this Free
  regional database.
* **Provider-specific uncertainty:** `UNKNOWN`.

Official references: [Upstash Redis pricing and limits](https://upstash.com/pricing/redis),
[Get Database Stats schema](https://upstash.com/docs/devops/developer-api/redis/get_database_stats),
[Upstash metrics and charts](https://upstash.com/docs/redis/howto/metrics-and-charts),
and [capacity-quota behavior](https://upstash.com/docs/redis/troubleshooting/db_capacity_quota_exceeded).

## Canary requirement

The already-certified owner-specific semantic plan is unchanged:

| Component | Bytes |
|---|---:|
| Conservative candidate peak (old + new overlap) | 2,338,040 |
| Provider safety reserve | 32,768 |
| Local accounting uncertainty | 65,536 |
| Minimum known required headroom | **2,436,344** |
| Provider-specific uncertainty | UNKNOWN |
| Total required headroom | UNKNOWN_TOTAL |

Arithmetic: `2,338,040 + 32,768 + 65,536 = 2,436,344` bytes.

Because the provider upper bound is unknown, minimum available headroom and the
cushion after all reserves are both `UNKNOWN`. The displayed `253 MB / 256 MB`
cannot be used as a capacity pass.

## Health and plan revalidation

Two fresh public GET attempts were made after the accounting observation:

| Endpoint | Result |
|---|---|
| `/api/v1/health/liveness` | HTTP `000`, no response, 20-second timeout |
| `/api/v1/health/readiness` | HTTP `000`, no response, 20-second timeout |

Therefore current health is **NOT ESTABLISHED**; no 2/2 health pass is claimed.
The semantic inputs remain unchanged in local evidence: productive runtime
`836c98a69ce9d12f67ed603f1f1ea58b8462a82a`, live Render SHA
`ccb2723f9e5718d5e15150463b35756336eef260` (`RUNTIME_EQUIVALENT`), selected
source SHA unchanged, canonical fingerprint unchanged, catalog key still
absent, and DBSIZE remained `9555`. The semantic plan SHA remains
`298b32c98e0052269896f3f9caa9a89e1f70e3fa15019ee061d7247c751ebc7a`.

## Authorization boundary

* Ready for one-owner execution authorization: **NO**
* Canary execution authorized: **NO**
* Bulk migration authorized: **NO**
* Cleanup authorized: **NO**
* Redis durable writes/deletes: `0`
* PostgreSQL mutations: `0`
* Provider/configuration/billing changes: `0`

No code, gameplay, frontend, fixtures or datasets were modified.

## Evidence

The canonical accounting observation is in
`docs/deployment/evidence/pb123h79f/upstash-accounting-closure/`. The prior
semantic dry-run remains authoritative for ownership, references, aliases,
fingerprint and repeatability; it was not reopened.
