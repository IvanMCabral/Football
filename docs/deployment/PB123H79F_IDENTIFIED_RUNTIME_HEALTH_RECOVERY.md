# PB1.2.3H7.9F — Identified runtime health recovery

## Scope

This gate validates the already identified Render runtime and its public health
dependencies. It does not perform canary selection, world reads, migration,
cleanup, account creation, Redis inspection, redeploy, restart, or any
infrastructure change.

## Runtime identity

- Service: `manager-staging-api`
- Service ID: `srv-d9nvldtaeets73coqiog`
- Branch: `feat/v25d99.20.3.1-runtime-fixes`
- Live SHA: `223fd8913cf3b753a75da48a677ea0e629dd52de`
- Latest productive local runtime: `836c98a69ce9d12f67ed603f1f1ea58b8462a82a`
- Runtime classification: `RUNTIME_EQUIVALENT`
- Deployment: Live / Deployed
- Plan/region: Free / oregon
- Instances: 1
- Autoscaling: OFF
- Process state: `RUNNING`

## Startup and log evidence

The current live deployment logs were inspected read-only. The sanitized
timeline shows:

1. JVM launched with Java 21 and the `prod` profile;
2. R2DBC repository initialization completed;
3. Flyway connected to PostgreSQL `18.4`, validated one migration, and reported
   schema version 1 up to date;
4. Netty bound port `10000`;
5. Spring Boot reported `Started FootballManagerApplication in 121.696
   seconds`;
6. Render reported the service live.

No `OutOfMemoryError`, container memory kill, SIGTERM, crash loop, repeated
restart, Redis quota error, Redis connection exception, PostgreSQL failure, or
reactive scheduler exhaustion was observed in the inspected startup log slice.
The Free plan cold-start warning is present in the provider UI.

The prior symptom was HTTP 000 timeout while the Free service was not warm. In
this run the first bounded liveness probe returned HTTP 200, so the evidence
supports `COLD_START_COMPATIBLE_RECOVERY`; it does not claim a fresh cold-start
benchmark.

## Health recovery gate

The bounded wake-up probe returned `{"status":"UP"}` in 857 ms (HTTP 200).

### Liveness (N=5)

All five samples returned HTTP 200 with `status=UP`:

| Sample | ms |
| ---: | ---: |
| 1 | 292 |
| 2 | 778 |
| 3 | 273 |
| 4 | 264 |
| 5 | 321 |

Informational statistics: p50 `292 ms`, p95 `778 ms`, max `778 ms`.

### Readiness (N=5)

All five samples returned HTTP 200 with `status=UP`, `database=UP`, and
`redis=UP`:

| Sample | ms |
| ---: | ---: |
| 1 | 14,137 |
| 2 | 1,011 |
| 3 | 988 |
| 4 | 778 |
| 5 | 815 |

Informational statistics: p50 `988 ms`, p95 `14,137 ms`, max `14,137 ms`.
The first readiness sample includes the provider/database warm path; all
samples still satisfied the health contract.

## Provider health dependencies

The authenticated Upstash dashboard currently shows database `Manager`, Free
Tier, AWS `sa-east-1`, TLS enabled, storage `253 MB / 256 MB`, `150K / 500K`
commands, and `0 B / 50 GB` bandwidth. Accounting remains
`PROVIDER_ACCOUNTING_COARSE`; exact bytes are not exposed. PING and DBSIZE were
not executed because the available console command surface did not become
safely interactive without copying or exposing a token. No Redis command was
manually issued by this gate.

Neon was not inspected because readiness already reported `database=UP` and no
contradiction required an additional provider query.

## Network control

- `curl.exe` liveness: HTTP 200, body `{"status":"UP"}`, approximately 812 ms
  transfer time;
- PowerShell `Invoke-WebRequest` liveness: HTTP 200, approximately 850 ms;
- classification: `SERVICE_RESPONSIVE`.

The two clients agree; the previous timeout was not reproduced once the
service was running.

## Source review

`HealthController`, `DatabaseHealthProbe`, and `RedisHealthProbe` were reviewed
locally. The probes use bounded two-second Reactor timeouts and return controlled
health values. No `.block()`, `.blockOptional()`, or manual `.subscribe()` was
found in these health sources. No code change is required.

## Boundary and verdict

- Recovery action performed: `NONE`
- Candidate inspected: `NO`
- World read: `NO`
- Planner run: `NO`
- Canary plan: `NO`
- Code/frontend/gameplay changes: 0
- Redis writes/deletes: 0
- PostgreSQL writes: 0
- Render/infrastructure/billing changes: 0
- Redeploy/restart: 0

**PB1.2.3H7.9F IDENTIFIED RUNTIME HEALTH RECOVERED**

Health is closed for this gate. The next operation, if authorized by a separate
gate, is the one-owner canary dry-run; it was not started here.
