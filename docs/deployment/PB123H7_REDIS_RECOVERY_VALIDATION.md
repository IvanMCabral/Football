# PB1.2.3H7 - Redis recovery validation

## Current state

Recovery was not reached. Public requests to the Render service timed out with
no response during this run; therefore no new readiness-qualified smoke was
started and no readiness result is fabricated.

Historical H6 evidence recorded liveness 200, readiness 503 with PostgreSQL UP
and Redis DOWN, and authenticated `reload-world` responses of 500. The current
run confirms that the storage condition remains unresolved; it does not claim
that the historical HTTP values are current.

## Required post-cleanup gates

After storage is safely below quota:

1. verify Upstash is active;
2. obtain three consecutive Render liveness 200 and readiness 200 responses,
   with both database and Redis UP;
3. run authenticated `POST /api/v1/dashboard/reload-world`;
4. create one disposable account and verify leagues, teams, career, squad,
   lineup 11/11, round, first SSE, reload, and recovery;
5. only then resume the N=10 performance run.

Current status: Redis DOWN/unavailable, readiness not qualified, reload-world
not re-run, new-account smoke not run, N=10 not prepared, controlled testers not
prepared.
