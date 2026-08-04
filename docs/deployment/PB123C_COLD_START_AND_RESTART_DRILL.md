# PB1.2.3C — Cold-start and restart drill

## Readiness baseline

At the time of this validation:

- `GET /api/v1/health/liveness` returned HTTP 200.
- `GET /api/v1/health/readiness` returned HTTP 200 with database and Redis UP
  after a transient first probe returned 503 while Render re-established the
  database connection.
- Firebase Hosting returned HTTP 200 for the root SPA.

The transient readiness response is expected free-tier startup behaviour and is
not silently classified as healthy; the retry recovered to 200 without changing
application state.

## Cold start

The earlier public probe waited approximately 70 seconds before checking the
service. The instance remained warm (liveness about 290 ms), so a genuine
Render sleep/wake cycle was not reproduced. No duration is claimed for first
byte, first SSE event or dashboard usability during a cold wake.

Required follow-up evidence is a real idle period long enough for Render Free to
sleep, followed by timings for first byte, readiness, dashboard, squad, round
start and first SSE event. The UI must show a bounded loading state and a retry
path rather than an indefinite spinner.

## Restart during a match

No public Render restart was triggered during an active match in this run. The
known runtime contract is:

- persisted career, fixture and completed detailed-match data can be recovered;
- a live in-memory `RoundEngine`/`LiveSession` may be lost when the process exits;
- the frontend must surface unavailable live state instead of inventing `0-0`.

The authoritative match-state endpoint and terminal snapshot fallback address the
previous stale-score incident. A provider-dashboard restart drill remains a P1
release gate and must record minute, score, events, source (Redis/PostgreSQL or
memory), and post-restart UI handling.

## Status

**Not certified as PASS.** This report intentionally distinguishes health checks
and recovery after refresh/login from an actual cold-start or restart guarantee.
