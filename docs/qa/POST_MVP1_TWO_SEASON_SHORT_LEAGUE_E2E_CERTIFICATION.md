# Post-MVP 1 Two-Season Short League E2E Certification

Date: 2026-07-31  
Branch: `feat/v25d99.20.3.1-runtime-fixes`  
Root baseline before this closure: `fb933f48`  
Frontend baseline before this closure: `e68cffc`

## 1. Verdict

`TWO-SEASON E2E APPROVED`

The two-season short-league browser/API E2E is approved. The MVP loop was verified with a real UI-created career, two completed six-round seasons, live match tactical changes, substitutions in final timelines, restart/recovery, Redis restart recovery, mathematically verified standings, and full green backend/frontend suites.

No P0 or P1 issue remains open for the certified scope.

## 2. Environment

- Backend: Spring Boot local runtime on `localhost:8080`, profiles `local,career-mutations`.
- Frontend: Angular dev server on `localhost:4200`.
- Browser: Codex in-app browser against the real local UI.
- Redis: authenticated local Redis on `localhost:6379`; restarted during validation.
- Database: main PostgreSQL `football_manager`; no dataset import or direct DB mutation was used to complete matches.

## 3. P2 fixes closed

- CareerSetup visible text mojibake corrected: `Fácil`, `Difícil`, `Rápida`, `ª`.
- CareerSetup now waits for the real league team count before submitting a career, preventing a transient `teamsPerDivision = 0`.
- `/api/v1/career/start` now treats omitted `teamsPerDivision` as all league teams, while explicit values keep the requested size and invalid values are rejected.
- `GameController#getMatchState` is fail-closed when a live snapshot has no valid owner.
- User-division fixture endpoints now return only the user's division; league-wide endpoints remain available for global views.
- `/api/v1/match-engine/matches/{matchId}/pause|resume|stop` now uses the authenticated user instead of passing `null` to the match service.

## 4. Career setup

Audit user: `two_season_ui_1785514375642@manager.local`  
Country: Spain  
Team: Real Sociedad  
Short league size: 4 teams per division  
Expected rounds: 6 per season

Evidence:

- `docs/qa/evidence/post_mvp1_two_season_e2e/08-register-second-ui-user.png`
- `docs/qa/evidence/post_mvp1_two_season_e2e/09-ui-four-team-preview.png`
- `docs/qa/evidence/post_mvp1_two_season_e2e/14-ui-career-created-or-error.png`
- `docs/qa/evidence/post_mvp1_two_season_e2e/17-lineup-confirmed-11.png`

Confirmed:

- Career created from UI.
- Standings division size: 4 clubs.
- Rounds: 6.
- User team included.
- Lineup confirmed with 11/11 players.

## 5. Fixture contract

Runtime oracle:

- `/api/v1/career/fixtures?round=1`: 2 matches for the user's 4-team division.
- `/api/v1/career/fixtures/all`: 6 rounds for the user's division.
- `/api/v1/career/fixtures/league?round=1`: 10 matches across 5 Spanish short divisions.

For a 4-team double round-robin:

- 12 division matches total.
- 2 matches per round.
- Each team plays 6.
- No duplicate match IDs were found in the certified season fixtures.

## 6. Season 1

Live match:

- Route: `/games/59073caa-a5bd-45a3-a48e-b72087b7afc2/round/1/live`.
- User match: `6f48ada8-8ca7-444b-a954-50b0aabda6ca`.
- Style change: accepted.
- Formation change: accepted with 11 slots.
- Manual substitution: accepted at minute 66.
- Final state: `FINISHED`, minute 90, FC Barcelona 1 - 0 Real Sociedad.
- Timeline contained substitution events.
- Natural yellow cards and injury/discipline events were observed during the live simulation.

Evidence:

- `22-round1-live-real.png`
- `24-round1-substitution-applied.png`
- `27-round1-finished-compatible-resume.png`
- `30-season1-round3.png`
- `31-season1-final-standings.png`

Final standings were independently recomputed from completed fixture results and matched API standings:

| Team | P | W | D | L | GF | GA | GD | Pts |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Real Madrid | 6 | 2 | 4 | 0 | 4 | 1 | 3 | 10 |
| FC Barcelona | 6 | 2 | 4 | 0 | 2 | 0 | 2 | 10 |
| Atletico de Madrid | 6 | 1 | 3 | 2 | 2 | 3 | -1 | 6 |
| Real Sociedad | 6 | 1 | 1 | 4 | 1 | 5 | -4 | 4 |

Integrity:

- Division fixtures: 12.
- Completed fixtures: 12.
- Sum of played: 24.
- No pending fixture in the division.
- No duplicate match IDs.

## 7. Restart and recovery

Backend restart was performed after season 1 round 1. The career recovered as:

- Season: 1.
- Round: 2.
- Phase: `WAITING_USER`.
- User team: Real Sociedad.
- Standings and completed round state persisted.

Redis restart was performed after completing the certified two-season path. Redis was restarted with the existing authenticated config and an empty runtime directory to avoid loading an incompatible local RDB file. The career recovered through backend/API/UI as:

- Season: 3.
- Round: 1.
- Phase: `PRE_MATCH`.
- User team: Real Sociedad.

Evidence:

- `28-recovery-after-backend-restart.png`
- `37-recovery-after-redis-restart.png`

## 8. Promotion/relegation classification

`teamsPerDivision = 4` creates competitive short divisions and the runtime reported `promotionsAvailable = true` after season 1.

The certification validated season transition and division membership reset for season 2. Full promotion/relegation semantics are classified as:

`IMPLEMENTED BUT NOT FULLY CERTIFIED BY THIS TWO-SEASON SHORT-LEAGUE PASS`

This does not block approval because the MVP approval criteria were season continuity, standings integrity, and absence of duplicated/corrupt fixtures. A dedicated promotion/relegation certification should assert exact moved clubs and division sizes across every subdivision.

## 9. Season 2

Season 2 creation:

- Continue result: success.
- New season: 2.
- Current round: 1.
- Total rounds: 6.
- Initial standings: four teams, all zeroed.
- Season 1 evidence remained intact in saved API evidence.

Evidence:

- `32-season2-created-standings-zero.png`
- `33-season2-round1-live.png`
- `34-season2-tactics-substitution.png`
- `35-season2-round1-timeline-final.png`
- `36-season2-final-standings.png`

Live match:

- User match: `325c5c8d-e0a0-436f-ae3f-9a07f9f937ce`.
- Style change: `DEFENSIVE`, accepted at minute 9.
- Formation change: accepted with 11 slots at minute 9.
- Manual substitution: accepted at minute 9.
- Final state: `FINISHED`, minute 90, Villarreal CF 0 - 0 Real Sociedad.
- Timeline contained substitution events.

Final standings independently matched API standings:

| Team | P | W | D | L | GF | GA | GD | Pts |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Real Betis | 6 | 1 | 5 | 0 | 3 | 1 | 2 | 8 |
| Real Sociedad | 6 | 2 | 2 | 2 | 2 | 3 | -1 | 8 |
| Athletic Club | 6 | 1 | 4 | 1 | 1 | 1 | 0 | 7 |
| Villarreal CF | 6 | 0 | 5 | 1 | 1 | 2 | -1 | 5 |

Integrity:

- Division fixtures: 12.
- Completed fixtures: 12.
- Sum of played: 24.
- No pending fixture in the division.
- No duplicate match IDs.

## 10. Idempotency

Checked:

- Retry `continue` while already in `PRE_MATCH` did not create a new season.
- Retry `next-round` while already in `PRE_MATCH` returned a controlled success/no-op shape and did not duplicate fixtures.
- Fixture match IDs remained unique after retries.

Note: after season 2 was legitimately finished, a new `continue` call started season 3. That is expected behavior, not duplicate creation for the same transition.

## 11. DB/API integrity

The E2E did not directly mutate DB state to complete matches. It used public UI and public backend APIs.

Saved evidence:

- `docs/qa/evidence/post_mvp1_two_season_e2e/e2e-api-evidence.json`

The evidence contains:

- Season 1 fixtures/results/standings.
- Season 2 fixtures/results/standings.
- Independent standings calculations.
- Live match final states.
- Substitution timeline data.
- Transition/idempotency responses.

No duplicate match IDs were found in the certified current-season fixture sets. No cross-season standings carry-over was observed: season 2 standings started from zero and completed independently.

## 12. Tests and builds

Backend:

- `mvn -q -DskipTests test-compile`: green.
- Focused backend suite: green.
- `mvn -q test`: `2529` tests, `0` failures, `0` errors, `4` skipped.

Frontend:

- `npm run build -- --configuration development`: green.
- `npm run build`: green.
- `npm test -- --watch=false --browsers=ChromeHeadless`: `1026 SUCCESS`, `0` failures, `2` skipped.
- Visible text encoding guard: passed, `381` files scanned.

The count increments are expected because this closure added targeted tests for CareerSetup behavior and authenticated match control endpoints.

## 13. P0/P1/P2 status

P0:

- None open.

P1:

- None open.

P2:

- CareerSetup mojibake: closed.
- CareerSetup transient team count: closed.
- Null live match owner authorization: closed.
- Short-league fixture contract: closed.
- Authenticated match control endpoint: closed.

Remaining non-blocking follow-up:

- Run a dedicated promotion/relegation certification to assert exact clubs moved between all generated short divisions.

## 14. Readiness

The short-league MVP flow is playable and stable enough to continue feature work:

- Career setup works from UI.
- Two seasons can be completed.
- Live tactical changes and substitutions affect the live session and timeline.
- Standings match independent calculations.
- Backend and Redis restarts recover the career.
- Full backend/frontend suites are green.

Conclusion: `TWO-SEASON E2E APPROVED`.
