# Post MVP 1 full browser E2E fixes independent audit

Date: 2026-07-31

Root repository: `D:\ProyectosOpenCode\MANAGER`

Frontend repository: `D:\ProyectosOpenCode\MANAGER\front-ciber\project`

Verdict: **E2E FIXES APPROVED WITH ISSUES**

## 1. Git

Audited root commits:

- `3c215859 Fix live match tactical state and authz`
- `fb933f48 docs: add playable browser E2E evidence`

Audited frontend commit:

- `e68cffc Fix career setup single division payload`

Root status before report creation was clean. Frontend status was clean.

`git diff --check` results:

- Root current working tree: clean.
- Root range `295b28c6..fb933f48`: clean.
- Frontend current working tree: clean.

Root changes in the audited range are consistent with the declared scope:

- live tactical/authz code changes;
- regression tests;
- E2E certification documents;
- browser evidence PNGs.

Frontend changes in `e68cffc` are limited to:

- `src/app/features/career/career-setup.component.ts`;
- `src/app/features/career/career-setup.component.spec.ts`.

## 2. CareerSetup fix

Original defect:

- The UI label for the default option promised one full division with all league teams.
- Before the fix, `selectedTeamsPerDivision = null` caused the frontend to omit `teamsPerDivision`.
- `/api/v1/career/start` still keeps a legacy backend fallback of `5` when `teamsPerDivision` is null, so the UI-created "single division" career became a 5-team subdivision career.

Current frontend behavior:

- If no subdivision is selected, `startCareer()` now resolves `totalTeamsInLeague$` and sends `teamsPerDivision = totalTeams`.
- If the user explicitly selects a smaller value, the selected value is preserved.
- The added spec `starts a single-division career by sending every league team in teamsPerDivision` verifies the outbound public HTTP payload at the component boundary.

Assessment:

- The player-facing UI bug is fixed.
- The test would fail conceptually against the old implementation because the expected `teamsPerDivision: 20` was not present.
- The test is useful but narrow: it directly spies on `HttpClient.post` rather than exercising a full browser click path.

Residual issue:

- Direct API callers that omit `teamsPerDivision` on `/api/v1/career/start` still receive the legacy fallback `5`. This does not break the fixed UI path, but it keeps a surprising public API default.

## 3. Team count selection

Frontend values:

- The UI computes available values from `2` through `teams.length`.
- Minimum selectable value: `2`.
- Maximum selectable value: total teams in the selected league.
- Default "single division" is represented as `null` in the UI, then converted to total teams before POST.

Backend validation:

- `CreateCareerSnapshotUseCaseImpl` rejects values `< 2` and values greater than the league team count.
- Division assignment sorts teams and slices them into divisions of the requested size.
- Remainders of one team become free teams; remainders of two or more form a final smaller division.

User team inclusion:

- Career creation validates that the selected user team belongs to the league.
- The selected team is cloned into the career and assigned as the user team.

Public API audit run:

- Created audit user through `/api/v1/auth/register`.
- Loaded available leagues through `/api/v1/world/leagues`.
- Selected Brazilian Serie A, Flamengo.
- Requested `/division-preview?teamsPerDivision=4`.
- Started `/api/v1/career/start` with `teamsPerDivision: 4`.

Observed result:

- Available teams: `20`.
- Preview divisions: `5`.
- Preview sizes: `4, 4, 4, 4, 4`.
- Career start: created.
- Career status: current round `1`, total rounds `6`.
- Standings rows: `4`.

Mathematical check:

- Four teams in one division, double round-robin: `2 * (4 - 1) = 6` rounds.
- Standings rows for the user's division: `4`.

## 4. Short league viability

Minimum public API audit:

- Created a Spanish Primera Division career with `teamsPerDivision: 2`.
- Observed total rounds: `2`.
- Observed standings rows: `2`.

Mathematical check:

- Two teams, double round-robin: `2 * (2 - 1) = 2` rounds.

The short-season mechanism is technically viable for a future two-season E2E because a 2-team division can close in two rounds and a 4-team division can close in six rounds.

Residual issue:

- `/api/v1/career/fixtures?round=1` returned all league/division matches for the round in the sampled short career, while the method name and comments describe user-division fixtures. This did not corrupt standings or total-round math, but the endpoint contract is ambiguous and should be cleaned before relying on it as a strict E2E oracle.

## 5. Tactical state

Audited file:

- `src/main/java/com/footballmanager/application/service/match/TacticalChangeService.java`

Cause:

- Formation/style mutation could succeed but the public live snapshot could remain stale.

Current behavior:

- Formation change mutates the live context and records a `TACTICAL_CHANGE` event.
- The audited fix calls `session.refreshDetailedSnapshot()` after recording the tactical change.
- Focused tests confirm formation/style mutation paths are green.

Assessment:

- The fix updates the authoritative live session state, not only the HTTP response.
- Browser/API evidence records a public live formation change and one tactical event in the timeline.

## 6. Tactical impact

Evidence reviewed:

- E2E certification documents public API mutation after live match start.
- Evidence screenshots include live formation modal, selected 4-3-3/3-4-3 states, post-mutation live state, and finished rounds.
- Focused backend tests for `TacticalChangeService`, style changes, formation changes, substitution, and live persistence all passed.

Assessment:

- The state mutation is observable in the live snapshot and timeline.
- The audit did not independently re-run a complete same-seed A/B statistical comparison over possession, xG, shots, channels, and timeline. The existing certification claims API/browser mutation evidence, not a full statistical engine sensitivity proof.

## 7. Authorization

Audited file:

- `src/main/java/com/footballmanager/adapters/in/web/game/GameController.java`

Implemented fix:

- `GET /api/v1/games/match/{matchId}` now loads the live snapshot and returns `403 FORBIDDEN` when `snapshot.userId()` exists and differs from the authenticated user.
- `GameControllerV25D79Test` verifies owner happy path and cross-user forbidden path with no private body.

Related endpoints:

- Career read/status endpoints derive the user from `Authentication`.
- Round start passes authenticated user id into the use case.
- Formation/style/substitution endpoints resolve live sessions by authenticated `(userId, matchId)`, which prevents another user from mutating a session they do not own.

Public API audit:

- Created distinct owner and intruder audit users.
- Intruder `/api/v1/career/status` returned only the intruder context, not the owner's career.

Residual hardening issue:

- `GameController#getMatchState` allows access when `snapshot.userId()` is null. Current production live snapshots are expected to carry the user id, and tests cover populated owner/non-owner cases. A null-owner snapshot should still be denied or treated as not found to avoid a future regression.

## 8. Regression tests

Frontend:

- `CareerSetupComponent` spec checks the corrected single-division payload.
- It asserts the observable outbound payload, not merely an internal variable.
- It does not cover all explicit values (`2`, `4`, `6`, max), invalid payloads, or browser interaction.

Backend:

- `GameControllerV25D79Test`: 3 tests, all green.
- `TacticalChangeServiceTest`: 11 tests, all green.
- `FormationChangeControllerE2ETest`: 7 tests, all green.
- `StyleChangeControllerE2ETest`: 5 tests, all green.
- `SubstitutionControllerE2ETest`: 9 tests, all green.
- `RoundControllerLiveMatchPersistenceE2ETest`: 1 test, green.

Assessment:

- No weakened assertions were found in the audited diffs.
- No reflection-based compatibility shim was observed in these audited tests.
- Coverage is adequate for the fixes, with the limitations noted above.

## 9. Browser evidence

Evidence directory:

- `docs/qa/evidence/post_mvp1_playable_e2e/`

Evidence count:

- `53` PNG files.

Representative files present:

- `00-register-page.png`
- `03-career-spain-select-league.png`
- `06-squad-loaded-spain.png`
- `08-lineup-confirmed.png`
- `12-live-formation-modal.png`
- `15-live-formation-433-applied.png`
- `17-live-resumed-after-substitution.png`
- `28-brazil-career-squad-autoselect.png`
- `40-brazil-recovery-squad-after-backend-restart.png`
- `43-brazil-live-events-after-mutations.png`
- `44-brazil-live-events-round-finished.png`
- `season1_round_10_finished.png`

Assessment:

- Browser evidence exists and covers the main playable loop.
- Some mutation evidence is explicitly mixed browser/API evidence, which is acceptable for this audit but should not be described as purely browser-only.
- The previous certification report contains mojibake and overstates that visible CareerSetup mojibake was fixed; current CareerSetup source still contains strings like `Fácil`, `Difícil`, `Rápida`, `ª`, and broken emoji sequences.

## 10. Backend validation

Commands executed:

- `mvn -q -DskipTests test-compile`
- `mvn -q "-Dtest=GameControllerV25D79Test,TacticalChangeServiceTest,StyleChangeControllerE2ETest,FormationChangeControllerE2ETest,SubstitutionControllerE2ETest,RoundControllerLiveMatchPersistenceE2ETest" test`
- `mvn -q test`

Results:

- Test compile: green.
- Focused tests: green.
- Full suite: `2522` tests, `0` failures, `0` errors, `4` skipped.

## 11. Frontend validation

Commands executed:

- `npm run build -- --configuration development`
- `npm run build`
- `npm test -- --watch=false --browsers=ChromeHeadless`

Results:

- Development build: green.
- Production build: green.
- Headless tests: `TOTAL: 1023 SUCCESS`.
- Karma total: `1025` specs, with `2` skipped, `0` failures.
- Visible text encoding guard passed across `381` scanned files, but it did not catch the CareerSetup mojibake noted in this audit.

## 12. Database

Database checks were executed against the configured principal database using `.env` without printing secrets.

Results:

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
| Estimated positions | 1561 |

## 13. P0 findings

None found.

## 14. P1 findings

None found that invalidates the audited fixes.

## 15. P2 findings

1. CareerSetup still contains visible-source mojibake despite the previous report saying it was fixed.
2. `/api/v1/career/start` still defaults omitted `teamsPerDivision` to `5`; the fixed UI no longer triggers it, but direct API behavior remains surprising.
3. Short-career fixture endpoints are contractually confusing: standings and round count are correct, but sampled fixture responses return all league/division matches rather than only the user's division despite naming/comments.
4. Live match authz should defensively reject/null-handle snapshots with missing `userId`.
5. A complete corrected 38/58-round browser-only marathon was not independently repeated in this audit; short-season viability was proven instead.
6. The existing E2E certification report contains mojibake and should not be treated as final polished documentation.

## 16. Readiness for two-season E2E

Ready with caveats.

The app can create short divisions through public API, and the math is correct:

- 2-team division: 2 rounds.
- 4-team division: 6 rounds.

This makes a two-season E2E technically practical. Before using the fixture endpoints as strict assertions, clarify which endpoint represents only the user's division versus all league divisions.

## 17. Conclusion

The main E2E fixes are accepted:

- frontend single-division payload is corrected for the UI path;
- team count is selectable and honored for public career creation;
- short careers produce mathematically valid total rounds and standings;
- live tactical state refresh is observable in snapshot/timeline paths;
- live match cross-user read is covered for populated ownership;
- backend and frontend suites are green;
- principal DB integrity remains green;
- browser evidence exists for the playable loop.

The verdict is **E2E FIXES APPROVED WITH ISSUES** because remaining issues are quality, documentation, contract clarity, and hardening concerns rather than observed P0/P1 runtime failures in the audited fixes.

## 18. Remediation closure added on 2026-07-31

The P2 issues from this historical audit were remediated and validated in the two-season short-league E2E closure:

- CareerSetup visible mojibake was corrected and covered by tests.
- CareerSetup now waits for the real league team count before submitting the start request.
- Omitted `teamsPerDivision` now means all league teams; invalid explicit sizes remain rejected.
- Live match state authorization now fails closed when a snapshot has no valid owner.
- User-division fixture endpoints were clarified by behavior and tests: division endpoints return only the user's division, while league endpoints remain global.
- Authenticated match-control endpoints under `/api/v1/match-engine/matches/{matchId}` now pass the authenticated user to the service.

Validation evidence:

- Backend `mvn -q -DskipTests test-compile`: green.
- Backend focused P2 suite: green.
- Backend `mvn -q test`: 2529 tests, 0 failures, 0 errors, 4 skipped.
- Frontend development build: green.
- Frontend production build: green.
- Frontend full headless suite: 1026 SUCCESS, 0 failures, 2 skipped.
- Real browser two-season E2E: `TWO-SEASON E2E APPROVED`.

Certification report:

- `docs/qa/POST_MVP1_TWO_SEASON_SHORT_LEAGUE_E2E_CERTIFICATION.md`
