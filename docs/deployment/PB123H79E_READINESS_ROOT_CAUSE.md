# PB1.2.3H7.9E — Readiness root cause

## Verdict

`FREE_TIER_STORAGE_LIMIT` — Upstash Manager is at exactly 256 MB / 256 MB.
Redis CLI returned `PING=PONG` and `DBSIZE=9638`; PostgreSQL remains UP. The
application readiness failure is therefore caused by the exhausted provider
storage ceiling, not by PostgreSQL or an unbounded readiness publisher.

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

The authenticated Chrome session exposed Render and Upstash. Upstash showed
Free Tier, AWS sa-east-1, storage 256 MB / 256 MB, 131K / 500K commands, 0 B /
50 GB bandwidth, PING=PONG, and DBSIZE=9638. Render logs confirmed the deployed
SHA, production profile, successful Neon/Flyway connection, and a running
process; the visible log window contained no Redis driver exception.

## Recovery decision

No code, Redis, database, plan, billing, or infrastructure change was made. The
next operation requires explicit owner-scoped cleanup authorization under the
approved H7 lifecycle; until then, stop with `BLOCKED_UPSTASH_QUOTA`.
