# PB1.2.3H7 - Redis recovery validation

## Provider recovery

After owner-driven cleanup, Upstash accepted PING, read, SET and UNLINK.
Reported usage was 127 MiB of 256 MiB and DBSIZE was 6,144. No global flush or
plan change was used.

## Render health

Three consecutive readiness pairs were green:

| Probe | Liveness | Readiness | Database | Redis |
|---:|---:|---:|---|---|
| 1 | 200 | 200 | UP | UP |
| 2 | 200 | 200 | UP | UP |
| 3 | 200 | 200 | UP | UP |

Authenticated `POST /api/v1/dashboard/reload-world` returned 200.

## Disposable-account smoke

One controlled account completed registration (200), authentication/me (200),
world reload (200), league/team loading (200), career creation (201), career
status, squad, auto-select, lineup, confirmation, standings and fixtures. The
career reset returned 204 and removed its owner data. A separate round smoke
started a round with 200 and reset successfully. The SSE request remained an
open stream without a first event during a six-second bounded probe; this is
recorded as **inconclusive**, not as a false green.

N=10 was not resumed. A follow-up must capture a first SSE event and a full
recovery/reconnect drill before controlled external testers are admitted.
