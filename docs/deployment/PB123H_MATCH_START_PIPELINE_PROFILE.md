# PB1.2.3H — Match-start pipeline profile

## Instrumented boundaries

The backend records bounded timings for:

1. HTTP round-start orchestration;
2. career state load;
3. live-session context construction;
4. initial match/round initialization;
5. the round-engine start boundary.

The measurements retain success/error, average, p50, p95 and max values and are emitted without request payloads or personal data. Existing idempotency and the in-flight start guard remain in place.

## Public observation

One authenticated public start after deployment reached:

| Stage | Observation |
|---|---:|
| Click to live route | 5,278 ms |
| First visible live state | observed at minute 22 after the live page connected |
| Hard reload recovery | route preserved; minute 26 visible; no 404/500 text |
| Duplicate start | none observed; one click was used |

The public route was `https://manager-4f952.web.app/games/6775308c-bb78-4db5-9ff7-45efd3b2ab83/round/2/live`. The identifier is retained only as a route-level smoke reference; no career payload is stored.

## What is and is not proven

The HTTP response is not treated as successful until the round route is consultable. The public smoke proved a live route and advancing minute after the start, but it did not provide a separately instrumented time-to-first-SSE packet or per-stage payload sizes. Render logs showed transient `redis.career.load` and dashboard errors during the first wake window; these are retained as P2 evidence rather than hidden.

## Next measurable closure

The remaining profile work is to export request ID, stage duration, payload bytes, round-trip count and first SSE timestamp from an ephemeral test run. That can be done without altering simulation behavior.
