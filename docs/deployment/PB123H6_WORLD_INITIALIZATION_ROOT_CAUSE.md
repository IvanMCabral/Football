# PB1.2.3H6 — World initialization root cause

## Verdict of this investigation

The original H5 public 404 had a confirmed frontend contract cause. The
subsequent public run exposed a second, independent runtime blocker: Redis was
down while PostgreSQL remained up. The H6 gate therefore remains **REJECTED**.

## Historical 404

Revision `267053e` called `POST /api/v1/world/seed-la-liga?userId=...` from the
production career setup page. `LaLigaSeedController` is explicitly excluded
from `prod`, so the request had no production mapping. The browser surfaced the
sanitized `El recurso solicitado no existe.` message.

## Corrected responsibility

Frontend revision `0830966` moved production initialization to
`POST /api/v1/dashboard/reload-world`. The controller derives the user from
the authenticated principal and materializes the snapshot from the imported
PostgreSQL dataset. The historical seed route remains local-only.

## Current public blocker

During the H6 run, the public readiness contract reported:

| Check | Result |
|---|---:|
| liveness | 200 |
| readiness | 503 |
| PostgreSQL | UP |
| Redis | DOWN |

Authenticated calls to `POST /api/v1/dashboard/reload-world` returned 500
`INTERNAL_ERROR` with request IDs recorded in
`evidence/pb123h6/public-world-init-reproduction.json`. This is not a missing
league or a synthetic-data fallback. It is a runtime dependency failure while
the per-user world snapshot is being materialized and stored.

### New provider evidence

The authenticated Upstash dashboard reports the `Manager` database on Free
Tier in `sa-east-1` at **257 MB / 256 MB** storage, with 52K / 500K monthly
commands, 0 B bandwidth and USD 0.00 cost. This is consistent with the Redis
health failure and is now the leading root-cause classification:

**P — FREE-TIER STORAGE LIMIT EXCEEDED.**

The dashboard observation is recorded, but it is not a substitute for the
required key-level inventory. The current workspace has no Upstash host,
username or provider token, and no authenticated provider connector is
available in this session. Therefore `DBSIZE`, `MEMORY STATS`, per-key memory,
TTL counts and prefix distribution are not claimed or fabricated.

No key was deleted. No account, career, world snapshot, live-session or
detailed-match data was modified.

## Evidence boundaries

No database rows, Redis keys, tokens, passwords or administrative/debug
endpoints were used. The evidence uses only public UI flows, public health
checks and authenticated calls created by the public registration flow.
