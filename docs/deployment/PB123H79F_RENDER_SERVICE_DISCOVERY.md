# PB1.2.3H7.9F — Render service discovery

## Scope and safety

This is a read-only provider identity gate. No Render resource, deployment,
environment variable, plan, instance count, autoscaling setting, Redis data,
PostgreSQL data, or application code was changed.

## Current authenticated account

The authenticated Render session exposed one selectable workspace:

| Workspace | Projects visible | Services visible |
| --- | ---: | ---: |
| Ivan's workspace | 1 | 1 |

The visible project is `My project` (`prj-d9nu6ge1egvs738q4uug`). Its only
visible service is `Football` (`srv-d9nu6gijnfac73bpbhj0`), shown as a Node
service in `oregon` with status `Failed deploy`, updated approximately ten days
ago.

The expected service `manager-staging-api` was not present in the only
accessible workspace/project. No additional workspace or account was exposed
by the current selector, and no ambiguous account was selected automatically.

## Historical service evidence

Repository evidence records a prior, historical deployment of
`manager-staging-api` on the `feat/v25d99.20.3.1-runtime-fixes` branch, Free
plan, one instance, with live SHA
`b6c413f3f8e0a6037b2f0362c6cc0a7893bf3af4`. That evidence is not substituted
for current provider state. The historical service was a Docker/Spring Boot
service, while the currently visible `Football` service is listed as Node; no
stable provider identifier, repository, branch, or deployment lineage connects
them. `Football` is therefore classified as `UNRELATED_SERVICE` for this gate.

## Public hostname correlation

For `manager-staging-api.onrender.com`:

- DNS resolution succeeded and returned Render edge addresses.
- TCP connection completed in approximately 68 ms.
- TLS handshake completed in approximately 93 ms.
- The bounded HTTPS liveness request produced no HTTP response and timed out at
  12 seconds (`HTTP 000`).

Classification: `ENDPOINT_UNRESPONSIVE`. This is consistent with the expected
service not being verifiable in the currently accessible Render account, but it
does not prove deletion.

## Classification

The narrowest evidence-based current classification is
`NOT_VERIFIABLE`: the exact service is absent from the only accessible
workspace/project, and there is insufficient provider metadata to prove a move,
rename, or deletion. Continuing requires access to the Render account or
workspace that owns the historical service.

## Gate result

**PB1.2.3H7.9F BLOCKED_SERVICE_NOT_FOUND**

Runtime SHA, deployment status, deployment timestamp, instance count, and
autoscaling for `manager-staging-api` cannot be reconciled from the current
provider view. Candidate inspection and all mutation/canary work remain out of
scope.
