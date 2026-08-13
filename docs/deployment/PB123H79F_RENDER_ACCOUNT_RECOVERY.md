# PB1.2.3H7.9F — Render account/workspace recovery

## Scope and safety

This gate recovers the Render account and workspace that own the historical
`manager-staging-api` service. The inspection was read-only. No service,
deployment, project, environment variable, scaling setting, plan, billing
configuration, Redis data, PostgreSQL data, or application code was changed.

## Account and workspace

The existing authenticated Render session exposes the personal account display
identity **Ivan Cabral** (email intentionally omitted). The workspace switcher
exposes exactly one context:

| Workspace | Membership | Plan | Projects | Services |
| --- | --- | --- | ---: | ---: |
| Ivan's workspace | 1 member | hobby | 1 | 1 |

No additional team, organization, invitation, or account choice was exposed;
no account switch was required and no identity was selected automatically.

## Exact service recovered

The current workspace overview now exposes the exact historical service:

- Service: `manager-staging-api`
- Service ID: `srv-d9nvldtaeets73coqiog`
- Workspace/project: Ivan's workspace / `My project`
- Repository: `IvanMCabral / Football`
- Branch: `feat/v25d99.20.3.1-runtime-fixes`
- Runtime: Docker
- Public hostname: `manager-staging-api.onrender.com`
- Plan: Free
- Region: `oregon`
- Deployment status: Live / Deployed
- Live SHA: `223fd8913cf3b753a75da48a677ea0e629dd52de`
- Deployment timestamp: 2026-08-12 22:26:47 GMT-3

The stable provider service ID and the exact repository, branch, and hostname
prove continuity with the historical service. The earlier visible `Football`
service (`srv-d9nu6gijnfac73bpbhj0`) was a separate, unrelated Node service.

## Runtime reconciliation

The latest local productive runtime SHA is
`836c98a69ce9d12f67ed603f1f1ea58b8462a82a`. The live SHA is a later
documentation/evidence descendant. Intervening commits were inspected:

- `4166aa66`: tests/tooling only;
- `0c7118bb`: documentation/evidence only;
- `b8fe8fe8`: documentation/evidence only;
- `928ce7c8`: documentation/evidence only;
- `1910b26e`: documentation/evidence only;
- `223fd891`: documentation/evidence only.

Runtime classification: **RUNTIME_EQUIVALENT**. No packaged production code
differs from the latest productive runtime on this lineage.

## Scaling and deployment state

The service scaling page reports:

- Manual instances: `1`;
- Autoscaling: `Off` (disabled on Free plan);
- Single-instance contract: **proven**.

No scaling control was changed.

## Public hostname correlation and health sample

The hostname is displayed by the recovered Render service itself, so correlation
does not rely on DNS alone. One bounded read-only probe was made for each health
endpoint as allowed by this gate:

- liveness: timeout after 30 seconds, HTTP `000`;
- readiness: timeout after 30 seconds, HTTP `000`.

These timeouts are recorded as the current health sample only. No repeated
benchmark or recovery operation was attempted. The service identity and runtime
gate are independent from the later stable-health gate.

## Provider boundaries

The existing Upstash observation remains historical/current-dashboard evidence:
`Manager`, Free Tier, AWS `sa-east-1`, `253 MB / 256 MB`, coarse provider
accounting. This account-recovery gate intentionally did not execute PING,
DBSIZE, SCAN, GET, MEMORY, or any other Redis command.

Candidate owner, world state, planner, and canary were not inspected or run.

Mutation accounting: code 0; frontend 0; gameplay 0; Redis writes/deletes 0;
PostgreSQL writes 0; Render mutations 0; redeploy 0; restart 0; billing changes
0.

## Result

**PB1.2.3H7.9F RENDER ACCOUNT RECOVERED — SERVICE FOUND**

The exact service, current workspace/project, live deployment, runtime lineage,
and single-instance contract are now proven. This document stops at identity
recovery; it does not authorize provider accounting preflight, candidate
selection, canary, migration, or cleanup.
