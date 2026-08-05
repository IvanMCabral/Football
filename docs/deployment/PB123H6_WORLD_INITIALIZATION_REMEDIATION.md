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

## Required operational recovery

Restore the configured managed Redis service and verify, without changing the
dataset:

1. three consecutive readiness responses are 200 with database and Redis UP;
2. a new account can load all three leagues and its teams;
3. three independent accounts pass the complete smoke;
4. ten independent warm match-start rows satisfy the H5 thresholds.

Until those checks pass, the public certification must remain rejected.
