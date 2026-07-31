# POST MVP 1 full browser E2E playable certification

Date: 2026-07-31

Branch: `feat/v25d99.20.3.1-runtime-fixes`

Verdict: **APPROVED WITH ISSUES**

## Scope

This certification exercised the playable manager loop through the in-app browser, public HTTP APIs, PostgreSQL, Redis-backed recovery, and full automated suites. It focused on real player-facing behavior, not architecture review.

## Code fixes made during certification

### Career setup single-division contract

The career setup UI described the default as one complete division, but the frontend omitted `teamsPerDivision` when no manual subdivision was selected. The backend compatibility default then created short 5-team divisions.

Fix:

- `front-ciber/project/src/app/features/career/career-setup.component.ts`
  - sends `teamsPerDivision = totalTeams` for the default single-division option;
  - keeps explicit smaller subdivision choices unchanged;
  - fixed visible mojibake in the same setup labels (`Fácil`, `Difícil`, `Rápida`, `ª`).
- `front-ciber/project/src/app/features/career/career-setup.component.spec.ts`
  - added a regression test proving the single-division payload sends all league teams.

Post-fix observed career creation:

| Country | Team | Teams | Rounds | Division | Promotions |
|---|---:|---:|---:|---|---|
| Spain | Real Sociedad | 20 | 38 | PRIMERA | false |
| Argentina | Rosario Central | 30 | 58 | PRIMERA | false |
| Brazil | Fluminense | 20 | 38 | PRIMERA | false |

### Live tactical mutation snapshot refresh

The formation mutation endpoint returned success but the live public snapshot could lag behind the manager action.

Fix:

- `src/main/java/com/footballmanager/application/service/match/TacticalChangeService.java`
  - refreshes the detailed match snapshot immediately after recording the tactical change.

Observed after backend restart:

- formation command: HTTP 200, `success=true`;
- public live state changed controlled team from `4-4-2` to `3-4-3`;
- public timeline exposed one `TACTICAL_CHANGE` event with pixel coordinates.

### Live match authorization

The live match detail endpoint was globally readable by `matchId`. Cross-user access now rejects private live match state.

Fix:

- `src/main/java/com/footballmanager/adapters/in/web/game/GameController.java`
  - validates `MatchStateSnapshot.userId()` against the authenticated user;
  - returns `403 FORBIDDEN` without a response body for another user's live match.
- `src/test/java/com/footballmanager/adapters/in/web/game/GameControllerV25D79Test.java`
  - keeps owner happy-path assertions;
  - adds cross-user rejection coverage.

Observed in browser/API E2E:

- intruder read of active live match returned `403`.

## Browser evidence

Evidence directory:

`docs/qa/evidence/post_mvp1_playable_e2e/`

Representative screenshots captured:

- `00-register-page.png`
- `02-dashboard-after-register.png`
- `06-squad-loaded-spain.png`
- `08-lineup-confirmed.png`
- `12-live-formation-modal.png`
- `15-live-formation-433-applied.png`
- `17-live-resumed-after-substitution.png`
- `24-season2-squad.png`
- `26-spain-new-career-squad-autoselect.png`
- `27-argentina-career-squad-autoselect.png`
- `28-brazil-career-squad-autoselect.png`
- `40-brazil-recovery-squad-after-backend-restart.png`
- `43-brazil-live-events-after-mutations.png`
- `44-brazil-live-events-round-finished.png`

## Browser/API flows validated

### User and career creation

- Register/login through UI completed.
- Career creation through UI completed.
- Corrected single-division careers created for Spain, Argentina, and Brazil.
- Squad page rendered real teams, real player names, traits, energy, form, injuries/suspensions, and no visible `undefined`/`null`/mojibake in the sampled smoke pages.

### Lineup and pre-match

- Auto-select produced `11 / 11`.
- Confirm and play navigated to live match.
- Season 2 recovery case with injuries initially showed `10 / 11`; auto-select recovered to `11 / 11` and enabled confirmation.

### Live match

- Match advanced minute-by-minute in browser.
- Pause/resume worked.
- Round finishing advanced career round.
- Formation mutation through public API changed the controlled team's public live formation.
- Substitution through public API returned success and appeared exactly once in the live timeline before finishing:
  - `SUBSTITUTION` count after mutation: `1`;
  - `substitutionsRemaining`: `4`.
- Tactical change appeared exactly once:
  - `TACTICAL_CHANGE` count after mutation: `1`.

### Restart/recovery

- Backend was restarted using `.env` in the same PowerShell session.
- `/api/v1/health` returned healthy after restart.
- Brazil career recovered from Redis/DB at season 1, round 5.
- Browser loaded squad after restart and continued to a new live round.

### Round/standings

- Brazil rounds advanced from 1 through 7 during this certification.
- Standings updated after completed rounds.
- Earlier evidence in this run also includes a full short-season closure and season 2 start from the legacy 5-team division path; that path exposed the single-division bug fixed above.

## Database integrity evidence

Principal DB: `football_manager`

| Metric | Result |
|---|---:|
| Countries | 3 |
| Leagues | 3 |
| Clubs | 70 |
| Teams | 70 |
| Players | 1680 |
| Player traits | 3360 |
| Players without exactly two traits | 0 |
| Orphan traits | 0 |
| Duplicate `(source_system, source_id)` | 0 |
| Missing source refs | 0 |
| Corrupt names | 0 |
| Estimated positions | 1561 |

## Automated validation

Backend:

- `mvn -q -DskipTests test-compile`: green.
- `mvn -q "-Dtest=TacticalChangeServiceTest,GameControllerV25D79Test,DetailedLiveSessionTest" test`: green.
- `mvn -q test`: 2522 tests, 0 failures, 0 errors, 4 skipped.

Frontend:

- `npm run build -- --configuration development`: green.
- `npm run build`: green.
- `npm test -- --watch=false --browsers=ChromeHeadless`: 1023 SUCCESS, 0 failures, 2 skipped.

## Remaining issues

### P2 — full corrected long-season browser marathon not completed

After fixing the single-division payload, new full-league careers correctly produce 38-round Spain/Brazil seasons and 58-round Argentina seasons. Browser smoke and multiple live rounds were validated post-fix, but a full 38/58-round browser marathon to season closure was not repeated after the fix.

This is not a code failure observed during the run, but it prevents the stricter `FULL BROWSER E2E PLAYABLE APPROVED` verdict for the literal “complete season through browser on corrected full league” criterion.

### P2 — many player positions remain marked as estimated

All source refs are present and no corrupt names were found, but `position_estimated = true` remains for 1561 players. This was already a dataset-quality tradeoff, not a runtime blocker.

## Final verdict

**APPROVED WITH ISSUES**

No P0/P1 runtime bugs remain from this certification:

- single-division career creation was corrected;
- live tactical mutation is visible in public state;
- live substitution is visible once in public state;
- live match cross-user access is rejected;
- backend and frontend suites are green;
- browser smoke for Spain, Argentina, and Brazil is green;
- DB integrity checks are green.

The only reason this is not `FULL BROWSER E2E PLAYABLE APPROVED` is the uncompleted post-fix full 38/58-round browser marathon.
