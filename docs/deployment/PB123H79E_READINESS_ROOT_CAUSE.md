# PB1.2.3H7.9E — Readiness root cause

## Verdict

`REDIS_DOWN` — provider readiness is blocked by Redis, not PostgreSQL.

## Direct evidence

- Five liveness probes: HTTP 200, `{"status":"UP"}`.
- Three readiness probes separated by three seconds: HTTP 503,
  `{"status":"DOWN","database":"UP","redis":"DOWN"}`.
- No public account was created and no reload-world request was started.

## Application path (read-only inspection)

`HealthController.readiness()` creates independent database and Redis Monos and
combines them with `Mono.zip`. `DatabaseHealthProbe.isAvailable()` executes
`SELECT 1` with a two-second timeout. `RedisHealthProbe.isAvailable()` performs
a short set/read/delete probe with a two-second timeout and maps errors to
`false`. There is no `block()`, manual `subscribe()`, or unbounded publisher in
this readiness path.

The public body therefore directly proves that PostgreSQL is reachable while
the Redis health probe is failing. The readiness implementation is not being
classified as the primary fault in this gate.

## Provider access

The authenticated Chrome session exposed no usable tabs during this run, so
Render logs, Upstash read-only console metrics, and Neon console state are
`NOT_VERIFIABLE`. No credentials, dashboards, Redis commands, or database
operations were used.

## Recovery decision

No code, Redis, database, plan, billing, or infrastructure change is authorized
by this phase. The required recovery is provider-side Redis availability; after
readiness returns 200 with both dependencies UP, resume the existing H7.9E N3
then N20 gate.
