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

## Evidence boundaries

No database rows, Redis keys, tokens, passwords or administrative/debug
endpoints were used. The evidence uses only public UI flows, public health
checks and authenticated calls created by the public registration flow.
