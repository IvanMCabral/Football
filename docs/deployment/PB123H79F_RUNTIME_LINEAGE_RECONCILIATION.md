# PB1.2.3H7.9F — Runtime lineage reconciliation

## Local Git identity

- Branch: `feat/v25d99.20.3.1-runtime-fixes`
- HEAD before this evidence commit: `1910b26ed6af80764e4c8da78ba3c46294a319e6`
- Upstream: `origin/feat/v25d99.20.3.1-runtime-fixes`
- Divergence: `0 ahead / 0 behind`
- `git diff --check`: clean

## Runtime lineage

The last commit that changed packaged production code before the H7.9F
documentation-only commits is:

`836c98a69ce9d12f67ed603f1f1ea58b8462a82a`

Its production change is `WorldSnapshotOverlay.java`. Every descendant through
the current HEAD was inspected by path:

| Commit range/item | Classification |
| --- | --- |
| `4166aa66` | test/tooling only |
| `0c7118bb` | documentation/evidence only |
| `b8fe8fe8` | documentation/evidence only |
| `928ce7c8` | documentation/evidence only |
| `1910b26e` | documentation/evidence only |

Therefore the latest local productive runtime SHA is
`836c98a69ce9d12f67ed603f1f1ea58b8462a82a`. The current HEAD is not a runtime
SHA; it is a documentation/evidence descendant.

The historical provider runtime SHA
`b6c413f3f8e0a6037b2f0362c6cc0a7893bf3af4` cannot be classified as
`EXACT_RUNTIME`, `RUNTIME_EQUIVALENT`, or `MISMATCH` against the current
provider because the exact service is not visible and its live deployment SHA
is not exposed.

## Current Render identity

| Field | Current evidence |
| --- | --- |
| Exact service | Not found |
| Workspace/project | Only `Ivan's workspace` / `My project` visible |
| Branch | Not verifiable |
| Live SHA | Not verifiable |
| Deployment status | Not verifiable for expected service |
| Timestamp | Not verifiable |
| Plan | Not verifiable for expected service |
| Region | Not verifiable for expected service |
| Instance count | Not verifiable |
| Autoscaling | Not verifiable |

## Upstash read-only state

The authenticated Upstash dashboard still shows database `Manager`, Free Tier,
AWS `sa-east-1`, TLS enabled, and provider-rounded storage `253 MB / 256 MB`.
No exact byte count was exposed. PING and DBSIZE were not executed because the
available console command surface did not become safely interactive without
copying or exposing a token; no token was copied or transmitted. No SCAN, GET,
MEMORY, write, delete, or cleanup operation was attempted.

## Boundary and verdict

- Candidate owner: `NOT_INSPECTED`
- World state: `NOT_INSPECTED`
- Planner: `NOT_RUN`
- Canary plan: `NOT_PRODUCED`
- Redis writes/deletes: 0
- PostgreSQL writes: 0
- Render mutations: 0
- Redeploy/restart: 0

**PB1.2.3H7.9F BLOCKED_SERVICE_NOT_FOUND**
