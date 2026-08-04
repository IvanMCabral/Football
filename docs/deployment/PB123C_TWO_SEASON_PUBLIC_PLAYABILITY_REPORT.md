# PB1.2.3C — Public two-season playability report

## Scope and evidence boundary

This report records the public staging evidence available after the PB1.2.3C
frontend drag-source correction. No gameplay probabilities, datasets, or season
rules were changed. The public services are:

- Frontend: `https://manager-4f952.web.app`
- Backend: `https://manager-staging-api.onrender.com`

The earlier public API run completed a six-round short season with Valencia CF.
The repository also contains the sanitized, reproducible two-season evidence in
`docs/qa/evidence/post_mvp1_two_season_e2e/e2e-api-evidence.json` and its index.
Those artifacts are historical evidence from the same public product flow; they
do not claim a new browser session in this run.

## Public season evidence

| Season | Dates | Formation changes | Substitutions | Refresh/login recovery | Result | Persistence |
|---:|---:|---|---|---|---|---|
| 1 (PB1.2.3B public run) | 1–6 | 4-4-2 / 4-3-3 alternating | 200 each round | Passed | 2W/4D/0L, 10 pts, 4–2, 2nd | Terminal state, fixtures, standings and detail readable |
| 2 (historical short-season artifact) | 1–6 | Included in sanitized E2E artifact | Included in artifact | Passed in artifact | Standings reset and season advanced | Season boundary and IDs reconciled in artifact |

The public API attempt made during this run created an ephemeral account without
printing credentials. Its authenticated world-league read did not return within
the bounded 60-second probe, because a newly registered account has no seeded
world context on the public profile. It was not used to manufacture season
results. The existing season artifacts remain the source of truth for the
two-season claim.

## Consistency checks

- The authoritative match-state endpoint returns the live or terminal snapshot;
  it does not synthesize `0-0` for an unknown state.
- Observed minutes, scores and events were monotonic through the completed public
  season.
- Duplicate round starts were idempotent.
- Season 2 fixtures and standings are separate from Season 1 in the archived
  evidence; round and match identifiers are not reused.
- A real public Render restart drill was not performed, so in-memory live-session
  durability remains a release limitation.

## Current certification result

The functional API evidence supports **JUGABLE CON ESPERAS** for the short-season
flow. A complete fresh browser-only two-season certification is not claimed: the
Chrome bridge failed before tab enumeration, and Firebase redeployment of the
frontend correction could not be authenticated from this session. These are
evidence/deployment gates, not a gameplay regression.
