# PB1.2.3H6 — World initialization remediation

## Implemented

- Production frontend no longer calls the non-production La Liga seed route.
- Production calls `POST /api/v1/dashboard/reload-world` with authenticated
  ownership.
- Development/local behavior keeps the historical seed route for tooling.
- Focused catalog tests cover both contracts.
- Frontend commit `0830966` was deployed to Firebase and its public hashes
  match the released assets.

## Not closed

The public runtime currently reports Redis `DOWN` and readiness `503`. The
reload endpoint consequently returns a controlled public 500 and new accounts
cannot reliably materialize their world. No retry loop or fake catalog was
added to hide this failure.

The newly supplied provider evidence shows the Free-tier storage quota is
already exceeded (257 MB used versus 256 MB). This is now classified as the
primary P0/P gate. It must be confirmed with read-only Redis commands before
any cleanup or rotation decision.

Required inventory (not yet available from this workspace): `DBSIZE`,
`MEMORY STATS`, sampled `MEMORY USAGE`, prefix distribution, TTL/no-TTL counts,
and top key sizes. Values must be reported as counts and sizes only; never
include key values, passwords or tokens.

## Required operational recovery

Restore the configured managed Redis service and verify, without changing the
dataset:

1. three consecutive readiness responses are 200 with database and Redis UP;
2. a new account can load all three leagues and its teams;
3. three independent accounts pass the complete smoke;
4. ten independent warm match-start rows satisfy the H5 thresholds.

Until those checks pass, the public certification must remain rejected.

## Safe cleanup policy (design only)

No cleanup was executed. After the inventory is captured, only explicitly
identified ephemeral test accounts, expired keys and duplicate abandoned
careers may be removed. Durable career, world snapshot, standings, detailed
match and live-session keys require an export/restore decision and a verified
owner mapping before deletion. A second read-only inventory must demonstrate a
clear margin below 256 MB, followed by Redis UP, readiness 200 and a fresh
reload-world smoke.
