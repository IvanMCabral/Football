# PB1.2.3B - Full-season E2E and state consistency report

## Scope

This report records the public staging validation for the MVP 1 runtime after the
authoritative match-state correction. It covers one complete short season, state
reads during and after live simulation, substitutions, formation changes, SSE,
refresh/recovery, and the regression that previously presented a finished score as
`0-0`.

No dataset, provider, probability, or gameplay-rule change was made.

## Incident: 3-0 to 0-0

The incident had two independent failure paths:

1. The frontend requested `GET /api/v1/match-engine/{matchId}/state`, while the
   backend did not expose that route. A 404 was then interpreted by the UI fallback
   as an empty score.
2. Round cleanup removed the live engine and the simulation cleanup used the user
   UUID instead of the registered round UUID. A subsequent read could therefore
   lose the in-memory terminal snapshot.

The correction adds the authenticated match-state endpoint, returns the live
`RoundEngine` snapshot while the round is active, and returns the persisted session
state after the engine is stopped. Unknown matches remain a controlled 404; the
implementation never fabricates a `0-0` state. Cleanup now resolves and unregisters
the actual round identifier from the processed match IDs.

Evidence: the focused controller, round-id lookup, orchestrator, and idempotency
tests pass; the public completed-match state read returned HTTP 200 with
`currentMinute: 90`, `status: FINISHED`, the final score, and events.

## Public environment

- Frontend: `https://manager-4f952.web.app`
- Backend: `https://manager-staging-api.onrender.com`
- Browser origin used for CORS validation: the frontend URL above.

The public run used an ephemeral test account and the existing Valencia CF career.
No password, token, or provider credential is recorded here.

## Full-season execution

Configuration: four teams per division, six rounds, two matches per round. The
lineup was alternated between `4-4-2` and `4-3-3` for successive rounds. Every round
was started twice to exercise idempotency; the duplicate request did not create a
second simulation.

| Round | Formation | Matches | User match | State endpoint | Substitution | SSE data events | Start (ms) |
|---:|---|---:|---|---:|---:|---:|---:|
| 1 | 4-4-2 | 2 | 0-0 | 200 | 200 | 8 | 522 |
| 2 | 4-3-3 | 2 | 2-2 | 200 | 200 | 8 | 338 |
| 3 | 4-4-2 | 2 | 0-1 | 200 | 200 | 8 | 426 |
| 4 | 4-3-3 | 2 | 0-0 | 200 | 200 | 9 | 245 |
| 5 | 4-4-2 | 2 | 0-1 | 200 | 200 | 9 | 253 |
| 6 | 4-3-3 | 2 | 0-0 | 200 | 200 | 9 | 246 |

Rounds 1-5 ended in `WAITING_USER`; round 6 ended in `FINISHED` with
`currentRound=6` and `totalRounds=6`. Login recovery after completion succeeded.
The final career record remained `FINISHED`, with Valencia CF second in the four-team
standings (10 points, 2 wins, 4 draws, 0 losses, 4 goals for, 2 against).

## State consistency checks

- Score never regressed during the observed round streams.
- Minutes and event sequence advanced monotonically; the terminal snapshot reported
  minute 90 and `FINISHED`.
- Formation changes were accepted before each round and were reflected in the round
  request; substitutions returned HTTP 200 and did not replace the persisted final
  score.
- SSE emitted live data and then closed when the short public observation window
  ended. The curl timeout used for the bounded probe is expected and is not an
  application error.
- Refresh/re-login recovered career status, standings, fixtures, squad, detailed
  match data, and the terminal match state.
- A process restart/recovery drill was not performed against the public Render
  instance in this run. Live in-memory sessions remain a documented restart risk;
  persisted career and detailed-match data remain the recovery source.

## Endpoint results

The following calls returned HTTP 200 for the completed public career: career status,
squad, all fixtures, world status, user stats, standings, detailed match, match
events/ratings/statistics, and the new match-engine state endpoint. CORS returned the
exact configured frontend origin with credentials enabled. Unknown state IDs returned
a controlled 404 in the focused WebFlux tests.

## Browser validation boundary

The real Chrome session could not be inspected in this run. The Chrome skill's
Node bridge initialization failed before tab enumeration with:

`failed to write kernel assets: El sistema no puede encontrar la ruta especificada. (os error 3)`

This is a local Computer Use bridge failure, not a staging HTTP failure. Therefore a
new screenshot-based desktop/laptop/mobile review is not claimed here. A previous
production browser observation also showed an application `_dragRef` TypeError during
marker drag; MetaMask `contentscript.js` listener/ObjectMultiplex warnings were
extension noise and are not treated as application defects. The drag error remains
an open UI follow-up.

## Changes and validation

- `0af59ba6` - restored the authoritative live match state endpoint and terminal
  fallback.
- `bf8cebd3` - aligned round cleanup tests with registry ownership.
- The integration-test base now stops any leftover round engines before resetting
  shared test state, preventing background scheduler callbacks from contending with
  later WebTestClient assertions.
- Frontend production artifact inspection passed; the production output contains no
  test-harness lazy chunk.
- Public readiness, liveness, CORS, SSE, recovery, and full-season API probes passed.

Final local validation counts:

- Backend: 2,582 tests, 0 failures, 0 errors, 4 skipped (`mvn -q test`).
- Frontend: 1,038 SUCCESS, 0 failures, 2 skipped; encoding guard passed.
- Frontend development and production builds passed; production artifact inspection
  scanned 52 files and passed.

An earlier full-suite attempt exhausted the local temporary disk and exposed a
background-engine test-isolation timeout. Temporary test data was removed, the
isolation cleanup was added, and the exact full suite was rerun green. No product
fallback or test assertion was weakened.

## Open items

1. Re-run a real Chrome visual pass after the Computer Use bridge is repaired.
2. Investigate and regression-test the `_dragRef` marker-drag console error.
3. Execute a public-instance restart drill and measure cold start under the actual
   Render sleep policy.
