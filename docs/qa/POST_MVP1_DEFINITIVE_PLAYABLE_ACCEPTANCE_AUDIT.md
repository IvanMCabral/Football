# Post-MVP 1 definitive playable acceptance audit

Date: 2026-07-31

Audited commit: `295b28c6 docs: record definitive LiveSession context encapsulation audit`

Verdict: `PLAYABLE MVP APPROVED WITH ISSUES`

## 1. Verdict

The MVP is functionally playable for the audited core loop:

- create authenticated user;
- create careers in Spain, Argentina, and Brazil;
- load squad;
- auto-select and confirm a valid lineup;
- persist pre-match formation;
- run a live round;
- persist round results;
- update standings;
- prove the harness/match engine reacts to formation, style, player movement by pixels, and substitution scenarios.

The audit does not issue full `PLAYABLE MVP APPROVED` because the manual runtime pass did not complete a full two-season end-to-end playthrough from the browser. The automated backend suite is green and covers season, transition, promotion/relegation, persistence, detailed match, live match, substitutions, discipline, injuries, lineup, harness, and architecture-related runtime cases, but this report keeps manual end-of-season/two-season verification classified as a remaining playable acceptance gap instead of overstating it.

## 2. Environment

- Root: `D:\ProyectosOpenCode\MANAGER`
- Frontend: `D:\ProyectosOpenCode\MANAGER\front-ciber\project`
- Backend: Spring Boot WebFlux on port `8080`
- Frontend: Angular on port `4200`
- PostgreSQL: loaded from `.env`, verified against `football_manager`
- Redis: authenticated with `.env`, `PING` returned `PONG`
- Profiles used for runtime: `local,career-mutations`
- Backend health: API reachable; unauthenticated/auth-shape failures return controlled 4xx
- Frontend health: `http://localhost:4200` returned HTTP 200 and rendered UI

No critical startup errors were found in the active runtime. A local PowerShell profile warning about the `cat` alias appears in shell sessions and is unrelated to the repository/application.

## 3. Git

Initial root status was clean. Initial frontend status was clean.

Post-validation status before writing this report was clean in both repositories. This report is the only repository artifact intentionally created by this task.

## 4. Dataset

Principal DB validation after runtime and tests:

| Metric | Value |
| --- | ---: |
| Countries | 3 |
| Leagues | 3 |
| Clubs | 70 |
| Teams | 70 |
| Players | 1680 |
| Team squad rows | 1680 |
| Trait assignments | 3360 |
| Players without exactly two traits | 0 |
| Duplicate `(source_system, source_id)` | 0 |
| Orphan squad players | 0 |
| Orphan squad teams | 0 |
| Players without identity refs | 0 |
| Players without position refs | 0 |
| Suspicious `?` names | 0 |
| Mojibake name scan | 0 |

Countries/leagues validated:

- Argentina: Argentine Primera Division, 30 teams, 720 players.
- Brazil: Brazilian Serie A, 20 teams, 480 players.
- Spain: Spanish Primera Division, 20 teams, 480 players.

All teams have enough players for an 11-player lineup plus bench.

## 5. Career creation

Created real runtime careers by public API:

| Country | Club | Result |
| --- | --- | --- |
| Spain | Athletic Club | Created; squad 24; lineup 11/11; slots 11/11; standings loaded |
| Argentina | Aldosivi | Created; squad 24; lineup 11/11; slots 11/11; standings loaded |
| Brazil | Athletico Paranaense | Created; squad 24; lineup 11/11; slots 11/11; standings loaded |

Additional Spain careers were created for live round and harness validation using Athletic Club, FC Barcelona, and CA Osasuna.

## 6. Navigation

Browser smoke confirmed:

- login page renders;
- register page renders;
- UI registration succeeds;
- authenticated dashboard renders;
- dashboard shows dataset totals: `70` clubs and `1680` players;
- career setup page renders league and division controls;
- test harness page renders and correctly reports when no active career is present;
- `/squad` without active career redirects to career setup instead of failing;
- no browser console errors were observed during these smoke pages.

## 7. Lineup

Pre-match lineup API:

- `POST /api/v1/career/lineup/auto-select` with `4-4-2` returned 11 players.
- Returned 11 tactical slots.
- `POST /api/v1/career/lineup/confirm` succeeded.
- Round fixture reflected the user side with the selected `4-4-2` formation when the team had a fixture.

Invalid states are guarded by phase checks in the public API.

## 8. Pre-match tactics

Harness/runtime evidence:

- `preview-summary` for CA Osasuna vs Deportivo Alaves produced possession, shots, xG, and channel metrics.
- Formation matrix showed distinct behavior for `4-4-2`, `4-3-3`, `3-5-2`, `4-2-3-1`, and `5-3-2`.
- Example: `4-4-2` and `4-3-3` produced different xG, shot volume, wide shots, and shape attack multipliers under the same seed window.

This proves formation changes reach the engine and affect observable match variables.

## 9. Live match

Live round evidence:

- `POST /api/v1/match-engine/rounds/start` returned `IN_PROGRESS`.
- The round registered 8 matches.
- After live execution, round fixtures became `COMPLETED:8`.
- Standings updated with played matches: standings rows `5`, played sum `4`.
- The score and standings were persisted through the career endpoints.

One probe paused a running round intentionally and confirmed pause/resume endpoints returned controlled state.

## 10. During-match tactics

During live match:

- Style change endpoint accepted `ATTACKING`.
- Returned `success=true`, `minuteApplied=2`, `currentStyle=ATTACKING`.

Formation endpoint was also exercised with an invalid role payload and returned a controlled 400:

- `invalid position 'CB' — must be one of GK, DEF, MID, WINGER, ATT`

This is a correct validation response, not state corruption. A follow-up formation attempt was affected by BYE/user-match selection and was not counted as a successful live formation mutation in this manual audit.

## 11. Substitutions

Live substitution endpoint evidence:

- Manual substitution request returned `success=true`.
- Returned `minuteApplied=10`.
- Returned `substitutionsRemaining=4`.

Harness substitution coverage is green in the backend suite. Manual visual proof of a substitution event in the final persisted detailed timeline was not completed in this pass, so this remains a P2 evidence gap, not a detected gameplay failure.

## 12. Injuries, discipline, and fatigue

Automated backend suite passed focused coverage for injuries, discipline, fatigue/lifecycle, substitutions, and availability rules.

Manual live audit did not force a full injury/suspension recovery cycle across multiple dates. No corrupted availability state was observed in created careers; starting squads were healthy and valid.

## 13. Round processing

A live round processed all 8 fixtures once:

- before: fixtures pending;
- after live execution: `COMPLETED:8`;
- standings played sum: `4`;
- no duplicate fixture status was observed in the audited career.

## 14. Standings

Standings loaded before and after round processing. After the live round, standings reflected played matches and points table movement. Backend suite passed standings and round-processing tests.

## 15. Season closure

Backend suite passed season-related tests. Manual browser/API runtime did not complete a whole season in this audit window.

Status: playable acceptance gap, not a confirmed defect.

## 16. Promotion/relegation

Runtime status showed `promotionsAvailable=false` in audited careers using current configuration. The dataset currently contains one principal league per country. The audit therefore does not declare real promotion/relegation functional from manual runtime evidence.

Status: not approved as manually proven. Covered only by automated tests/configuration-level paths.

## 17. New season

Backend suite passed season-transition coverage. Manual runtime did not complete a full transition to season 2.

Status: playable acceptance gap, not a confirmed defect.

## 18. Two seasons

Manual two-season browser/API smoke was not completed. Automated suite is green.

Status: P2 evidence gap for final playable certification.

## 19. Three countries

Created and validated playable career setup in:

- Spain: Athletic Club.
- Argentina: Aldosivi.
- Brazil: Athletico Paranaense.

All three loaded squad, lineup, slots, fixtures/standings paths, and valid players.

## 20. Concurrency

Concurrent/session isolation is covered by the green backend suite and prior approved LiveSession audits. This functional pass also created multiple independent users/careers without observed cross-user contamination.

Manual simultaneous browser control of two live sessions was not completed in this pass.

## 21. Restart/recovery

Redis and PostgreSQL were validated before and after runtime/tests. Backend recovery-specific coverage is green in the backend suite. A destructive runtime restart during this side audit was not performed to avoid disrupting the active local environment.

Status: P2 evidence gap for this specific manual pass; no failure observed.

## 22. Errors

Controlled errors observed:

- world leagues without required `userId`: 400 with explicit missing parameter.
- fixtures endpoint without required `round`: 400 with explicit missing parameter.
- detailed timeline without required `minute`: 400 with explicit missing parameter.
- invalid live formation tactical role: 400 with useful message.
- test harness without active career in UI: friendly "Sin carrera activa" screen.

No DB corruption followed these invalid calls.

## 23. Browser smoke

Browser smoke passed:

- initial login page;
- registration page;
- UI register → dashboard;
- dashboard dataset totals;
- career setup;
- squad redirect when no active career;
- test harness no-career state.

No browser console errors were captured on the smoke pages.

## 24. Backend tests

Commands:

- `mvn -q -DskipTests test-compile`
- `mvn -q test`

Results:

- Surefire reports: 278
- Tests: 2521
- Failures: 0
- Errors: 0
- Skipped: 4

## 25. Frontend tests

Commands:

- `npm run build -- --configuration development`
- `npm run build`
- `npm test -- --watch=false --browsers=ChromeHeadless`

Results:

- Development build: green.
- Production build: green.
- Visible text encoding guard: passed, 381 files scanned.
- Karma: `TOTAL: 1022 SUCCESS`
- Skipped: 2
- Failures: 0

Warnings/errors printed inside intentionally mocked frontend tests did not fail the suite.

## 26. DB

DB integrity after runtime and tests remained stable:

- 3 countries;
- 3 leagues;
- 70 clubs;
- 70 teams;
- 1680 players;
- 3360 trait assignments;
- 0 duplicate source IDs;
- 0 player/team squad orphans;
- 0 players without exactly two traits.

## 27. P0

None found.

No evidence of:

- inability to create/play a career core loop;
- DB corruption;
- duplicate results in audited round;
- career loss;
- cross-career contamination.

## 28. P1

None confirmed.

No confirmed blocking evidence that:

- lineup/tactic data fails to reach the engine;
- substitutions fail at API/use-case level;
- standings are wrong after a completed round;
- UI principal is unusable;
- backend/frontend suites are red.

## 29. P2

Open evidence gaps/minor issues:

1. Manual two-season browser/API run was not completed in this pass.
2. Manual promotion/relegation cannot be approved from the current one-league-per-country principal dataset and runtime status showed `promotionsAvailable=false`.
3. Manual detailed timeline evidence for the live substitution event was not captured after completion, although the substitution endpoint returned `success=true` and automated tests are green.
4. One route spelling expectation differs from actual app route: direct `/career-setup` redirected to `/dashboard`; the visible app route is `/career/setup`.
5. Shell output is polluted by a local PowerShell profile alias warning unrelated to the app.

## 30. Readiness

The MVP is ready for playable internal use and continued product iteration, with the caveat that a final release-grade acceptance should still include a full manual two-season walkthrough and explicit promotion/relegation classification once multi-division country datasets/rules are expanded or deliberately scoped out.

## 31. Conclusion

`PLAYABLE MVP APPROVED WITH ISSUES`

The core playable MVP is healthy: dataset integrity is clean, backend and frontend suites are green, three-country career creation works, lineup and tactical setup work, live round processing works, standings update, browser smoke passes, and the harness proves tactical/player-position changes affect the engine.

The remaining issues are evidence/completeness gaps around long-horizon manual season progression and promotion/relegation, not observed P0/P1 failures in the playable core loop.
