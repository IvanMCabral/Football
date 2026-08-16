# MANAGER — PB1.2.3H7.9F Final zero-cost closure

Date: 2026-08-16
Repository: `D:\ProyectosOpenCode\MANAGER`
Branch: `feat/v25d99.20.3.1-runtime-fixes`
Scope: documentary and operational closure only

## Verdict

**PB1.2.3H7.9F BLOCKED_FREE_PLATFORM_CAPABILITY**

The one-owner World V2 canary is not authorized at the current zero-cost
platform boundary. This is a provider-capability limitation, not a World V2
semantic failure, not a runner failure, and not a Redis or PostgreSQL failure.

No migration, preparation, commit marker, catalog write, cleanup, provider
change, billing change, or gameplay change was performed by this closure.

## Executive result

The local World V2 safety work and the H7.9F.1 runner are already independently
approved. The exact production-context service is healthy enough to continue
normal MVP operation, but Render Free does not expose a safe, isolated,
one-shot execution boundary that inherits the service's PostgreSQL and Redis
secrets. Render Free explicitly excludes One-Off Jobs and Shell, while
pre-deploy execution and additional worker capacity are paid capabilities.

The remaining alternatives would either redeploy or restart the live service,
copy production credentials outside the provider, expose an administrative
endpoint, or add a paid resource. Each is rejected under the strict USD 0
constraint.

## Authorities and lineage

| Authority | Value | Status |
| --- | --- | --- |
| Certified runtime commit | `24560a2b69fd94fce273ebb6aa7fe41b9e81c7ed` | exact live service SHA |
| Certified owner authority SHA-256 | `7fcae17b55af464cc929f242689bb456d9cc06718cf47d2e9642966041cd71e8` | retained |
| World V2 source SHA-256 | `2fe7dff53f2c6f07d222337cdda0a6963841e229aef721d2e11a395ba3d4d68f` | unchanged authority |
| Semantic plan SHA-256 | `298b32c98e0052269896f3f9c9aa9a89e1f70e3fa15019ee061d7247c751ebc7a` | unchanged authority |
| Catalog fingerprint | `1e654bec389796d232aba91685ac87d9ef1de08bcf3f5a7da563fdedbfb27000` | retained; no public catalog created |
| World V2 local safety | APPROVED | independent evidence retained |
| H7.9F.1 runner | INDEPENDENTLY APPROVED | execution surface is the remaining blocker |

The historical H7.9F reports remain unchanged and retain their original
exploratory verdicts. This document supersedes them only as the current
zero-cost operational classification.

## Exact provider boundary

Read-only inspection of the existing Render service recorded:

| Field | Observed value |
| --- | --- |
| Workspace | Ivan's workspace |
| Service | `manager-staging-api` |
| Service id | `srv-d9nvldtaeets73coqiog` |
| Plan | Free |
| Runtime | Docker web service |
| Region | Oregon (US West) |
| Branch | `feat/v25d99.20.3.1-runtime-fixes` |
| Live SHA | `24560a2b69fd94fce273ebb6aa7fe41b9e81c7ed` |
| Status | Live |
| Instances | 1 |
| Autoscaling | Off |
| PostgreSQL context | Present in the normal service |
| Redis context | Present in the normal service |
| Exact application | Present in the normal service |

The service configuration remains the ordinary `prod` profile. No canary
profile, enable flag, or EXECUTE confirmation is retained in the normal
startup path. The local `render.yaml` declares `autoDeployTrigger: off`; the
dashboard was observed with Auto-Deploy set to **On Commit**. That discrepancy
is recorded as an operational follow-up. It is not changed here, and it is a
reason not to push this documentation commit automatically.

### Missing free execution surfaces

| Surface | Free capability | Consequence |
| --- | --- | --- |
| One-Off Job | Not supported for Free instance types | no isolated canary process |
| Shell | Not supported for Free instance types | no provider-side manual runner |
| Paid Pre-Deploy command | Paid execution boundary | not available at USD 0 |
| Additional worker/cron | Paid service/capacity | rejected |
| Secure alternate one-shot with inherited env | Not available | local/CI process cannot safely inherit production secrets |

These capabilities are documented by Render's official free-service and
One-Off Job documentation. The current service has no supported way to run the
approved runner once, with the live PostgreSQL/Redis credentials, without
touching the live web process.

## Rejected alternatives

The following options were explicitly evaluated and rejected:

- temporary start-command or profile activation: would replace/restart the
  live service, create an outage or startup-loop risk, and is not a one-shot
  boundary;
- deploy-hook abuse: a hook can trigger a deploy but cannot provide a secure,
  isolated inherited environment;
- public admin/canary endpoint: would create a new production attack surface
  and is outside the approved runner contract;
- copying production credentials to a workstation or CI job: violates the
  secret-boundary and evidence requirements;
- GitHub Actions: the existing workflow uses ephemeral smoke credentials and
  has no production PostgreSQL/Redis/Upstash Management API secrets;
- another Render resource, Cron, Worker, Starter or paid One-Off Job: violates
  the permanent USD 0 requirement.

No unsafe workaround is being presented as evidence of a successful canary.

## Migration and cleanup state

| Operation | Current state |
| --- | --- |
| Public migration | **NO** |
| `PREPARED` marker | **NO** |
| `COMMITTED` marker | **NO** |
| Public catalog creation | **NO** |
| Redis cleanup | **NO** |
| PostgreSQL mutation | **NO** |
| Provider mutation | **NO** |
| Billing/plan change | **NO** |
| Legacy worlds | remain authoritative and preserved |

The existing legacy worlds are not deleted. A blocked migration is not
equivalent to a safe-to-delete migration. The quota pressure observed in the
H7.9E/H7.9F evidence therefore remains a known risk rather than a reason to
perform an unverified cleanup.

## Storage and quota risk

Upstash Free remains a constrained staging resource. Earlier authenticated
evidence recorded storage near the Free quota; this closure does not repeat
provider accounting, write, deletion, or cleanup operations. The only safe
classification is **quota risk retained / cleanup not authorized**.

Redis is not declared solved or restored by this document. The absence of a
safe one-shot boundary prevents the owner-scoped canary and its associated
cleanup proof from being executed at USD 0.

## Conditions to reopen H7.9F

The gate may be reopened only when one of these conditions is true:

1. Render adds One-Off Jobs to the Free plan with the required inherited
   environment boundary;
2. Render provides a secure free process boundary that can run the approved
   runner without replacing the live web service;
3. the existing free trusted execution environment can securely inherit the
   service's PostgreSQL/Redis credentials and the read-only Management API
   credential without manual copying;
4. hosting moves to another genuinely free platform that provides an
   equivalent safe one-shot boundary.

Even then, execution authorization, public migration and cleanup authorization
must be granted separately. This document does not grant any of them.

## Next three product slices (not implemented here)

The candidates below are drawn from `PROJECT-STATUS.md`,
`docs/backend-refactor-limpieza-v1.md`, the active documentation index, and the
post-MVP quality audit. They are ordered by product value first, then risk,
H7.9F dependency, and compatibility with a free tier.

### 1. Player stamina and injury progression

*Product value:* highest; this is the next functional recommendation in the
project status and makes the manager decisions more professional.
*Risk:* medium/high because the feature touches minute simulation, ratings and
live UI.
*H7.9F dependency:* none if implemented and tested locally with bounded
fixtures; it must not require new durable Redis families.
*Free-tier fit:* good when stamina/injury state is part of the existing match
payload and bounded persistence is preserved.

### 2. Split the remaining harness and lineup responsibilities

*Product value:* high for reliable tactical iteration and tester confidence.
*Risk:* medium; the documented debt is concentrated in
`TestHarnessUseCaseImpl` and `LineupCommandUseCaseImpl`.
*H7.9F dependency:* none; this is a local architecture and testability slice.
*Free-tier fit:* excellent; it changes no provider capacity and can reduce
future accidental storage-producing test runs.

### 3. Promotion/relegation end-to-end certification

*Product value:* high for season completeness; the active QA scope marks it
`IMPLEMENTED BUT NOT FULLY CERTIFIED`.
*Risk:* medium; it needs a fresh short-league run and restart/recovery evidence
but no rule change is implied.
*H7.9F dependency:* independent of the blocked World V2 migration; it must use
bounded local fixtures until a safe public execution surface exists.
*Free-tier fit:* conditional; do not create large public careers or add Redis
retention while quota headroom is unresolved.

No slice above is implemented, scheduled as a provider action, or authorized
as a substitute for H7.9F.

## Authorization and severity

- P0: **0** newly opened by this closure.
- P1: **1** — missing safe zero-cost provider execution capability.
- P2: **2** — retained Upstash quota risk; Render dashboard/config auto-deploy
  discrepancy requiring an operational decision before any push.
- P3: **0**.

The P1 is a platform limitation under the stated cost constraint. It is not a
defect in the approved World V2 semantics or runner implementation.

All authorizations remain **NO**:

- CANARY EXECUTION AUTHORIZED = NO
- PUBLIC MIGRATION AUTHORIZED = NO
- PUBLIC CLEANUP AUTHORIZED = NO
- PROVIDER MUTATION AUTHORIZED = NO
- BILLING CHANGE AUTHORIZED = NO

## Evidence

Sanitized machine-readable evidence is stored at:

`docs/deployment/evidence/pb123h79f/final-zero-cost-closure/closure.json`

It contains hashes, service metadata and capability classifications only; no
passwords, tokens, connection strings or provider secrets are persisted.
