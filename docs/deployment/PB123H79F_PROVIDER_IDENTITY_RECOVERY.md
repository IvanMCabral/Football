# PB1.2.3H7.9F — Provider identity recovery

## Scope

Read-only recovery of the Render and Upstash provider identities. No canary,
migration, cleanup, Redis writes, PostgreSQL writes, redeploy, restart, or
infrastructure change was performed.

## Local identity

- Branch: `feat/v25d99.20.3.1-runtime-fixes`
- HEAD: `928ce7c86de2cb97e4429ddac025c5c327959945`
- Upstream: `origin/feat/v25d99.20.3.1-runtime-fixes`
- Ahead/behind: `0/0`
- `git diff --check`: clean

## Render

The existing authenticated Render dashboard session was readable. The
workspace/project page exposed one service named **Football**, in region
`oregon`, with status **Failed deploy**. The required service
`manager-staging-api` was not present in the inspected project view.

Consequently the following required identity fields could not be verified from
the provider without selecting another project or changing infrastructure:

- exact service identity (`manager-staging-api`);
- live deployment SHA;
- deployment timestamp for the expected service;
- instance count and autoscaling state.

The expected runtime reference remains `928ce7c86de2cb97e4429ddac025c5c327959945`.
No redeploy or restart was attempted.

Fresh public health probes to `manager-staging-api.onrender.com` timed out with
HTTP status `000` (15-second bounded timeout) for both liveness and readiness.
Therefore a fresh 3/3 health gate could not be established.

## Upstash

The existing authenticated Upstash session exposed the database **Manager**:

- Plan: Free Tier
- Region: AWS `sa-east-1` (Sao Paulo)
- TLS/SSL: Enabled
- Storage: `253 MB / 256 MB` (provider-rounded; exact bytes not exposed)
- Commands: `150K / 500K per month`
- Bandwidth: `0 B / 50 GB`
- Cost: `$0.00`
- Endpoint: intentionally omitted

The database was not modified. PING/DBSIZE were not executed because the
dashboard does not expose a safe authenticated command result without copying
or revealing the access token; the CLI interaction timed out before a command
could be submitted. No token was copied, logged, or transmitted.

Accounting classification: `PROVIDER_ACCOUNTING_COARSE` (storage is rounded and
exact bytes are not exposed). The required local uncertainty bounds remain
32,768 bytes provider safety margin and 65,536 bytes accounting uncertainty.

## Candidate boundary

All candidate work is intentionally out of scope for this gate:

- candidate owner: `NOT_INSPECTED`
- world state: `NOT_INSPECTED`
- source checksum: `NOT_READ`
- catalog: `NOT_INSPECTED`
- planner: `NOT_RUN`
- canary plan: `NOT_PRODUCED`

## Mutation accounting

- Code changes: 0
- Frontend changes: 0
- Gameplay changes: 0
- Redis writes/deletes: 0
- PostgreSQL writes: 0
- Render mutations: 0
- Infrastructure changes: 0
- Billing changes: 0
- Redeploys/restarts: 0

## Verdict

**PB1.2.3H7.9F BLOCKED_PROVIDER_IDENTITY**

The authenticated dashboards are reachable, but the expected Render service,
runtime identity, single-instance contract, and fresh health gate are not
currently verifiable. No canary or migration work is authorized by this
document.
