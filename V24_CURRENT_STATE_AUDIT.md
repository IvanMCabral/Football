# V24 Current State Audit

**Purpose:** Document existing simulation/domain state before designing V24 Detailed Match Engine.
**Branch:** `mvp-1-performance-cleanup`
**Status:** V24D3C+V24D5E5+V24D5E6+V24D6A+V24D6B1+V24D6B2+V24D6B3+V24D6C1+V24D6C2+V24D6C3+V24D6D2+V24D6D3+V24D6D4+V24D6D5 COMPLETED — V24A/V24B/V24C/V24D1/V24D2/V24D3A/V24D3B/V24D3C/V24D4A/V24D4B/V24D4C/V24D5A/V24D5B/V24D5C/V24D5D/V24D5F complete. V24D5E1/V24D5E2/V24D5E3/V24D5E3B/V24D5E4/V24D5E5/V24D5E6 complete in separate frontend repo. V24D6A design + V24D6B1/B2/B3 injury mutation + V24D6C1/C2/C3 fatigue mutation pipeline wired behind default-false flags. V24D6D2/D3/D4/D5 discipline persistence pipeline wired behind default-false flags. V24D6G3 (commit `3675431`), V24D6G4A (commit `362c647`), V24D6G4B (commit `c4681e2`), V24D6G5A (commit `18543dc`), V24D6G6A (commit `80ad1ed`), and V24D6G7 (audit — no code changes needed) implemented in separate frontend repo. V24D6F1/F2/F3 mutation regression tests committed (`9e52b08`/`5933d1c`/`6250f11`; +15 tests, no production code changes). Form mutation deferred.
**Latest commit:** `0f4ab39` (feat: wire V24D6D5 discipline mutation behind flags)
**Tests:** 558 total (112 V23 + 8 V24A + 22 V24B + 58 V24C + 15 V24D1 + 22 V24D2 + 17 V24D3A + 31 V24D3B + 8 V24D3C + 24 V24D4A + 13 V24D4B + 12 V24D4C + 20 V24D5A + 11 V24D5B + 9 V24D5C + 12 V24D5D + 12 V24D5F + 21 V24D6B1 + 33 V24D6B2/C2 + 19 V24D6B3/C3 + 27 V24D6C1 + 7 V24D6F1 + 2 V24D6F2 + 6 V24D6F3 + 8 V24D6D2 + 16 V24D6D3 + 7 V24D6D4 + 6 V24D6D5), 0 failures; regression gate 558 tests, 0 failures
**Date:** 2026-05-15

---

## 1. V23 Simulation Engine

### MatchEngineImpl (application/service/domain/)

- Core simulation: Poisson lambdas → goals, possession, shots
- `simulate(Team, Team)` — random seed
- `simulate(Team, Team, long seed)` — deterministic
- `simulateWithStyle(...)` — TeamStyle-aware experimental path
- `simulateWithStrength(...)` — explicit OVR experimental path
- Produces `MatchResult`: goals, possession, shots, events (synthetic labels), summary
- **V24 reuse:** Lambdas, possession formula, shot model, deterministic seed pattern — all reusable
- **Do NOT modify:** Core simulate() paths must stay stable for V23

### MatchQualityComputer (application/service/domain/)

- Computes style-aware lambdas: `computeLambdas(int, int, TeamStyle, TeamStyle)`
- Computes shot lambda from goal lambda: `computeShotLambda(double)`
- **V24 reuse:** Style-aware lambda logic, shot volume formula
- **Do NOT modify:** Existing computeLambdas() signature

### TeamStyle (application/service/domain/)

- Enum: BALANCED, ATTACKING, DEFENSIVE, COUNTER, POSSESSION
- Modifiers: totalLambda adjustment, homeShare adjustment
- **V24 reuse:** Can use TeamStyle directly — no changes needed
- **Do NOT modify:** Enum values affect V23; V24 can consume but not alter

### TeamOverallCalculator (application/service/domain/)

- Pure utility: calculates team OVR from SessionPlayer data
- `calculateFromStartingXI(CareerSave)` — uses `CareerSave.teamStarting11`
- `calculateFromPlayerIds(List<String>, Function)` — flexible player lookup
- **V24 reuse:** Starting XI lookup, player OVR from SessionPlayer.calculateOverall()
- **Do NOT modify:** Utility is stable

### MatchResultDataAdapter (application/service/simulation/)

- Maps `MatchResult` → `MatchFixture.MatchResultData` (6 fields only)
- Discards events and summary
- **V24 reuse:** Adapter pattern is reference for V24→MatchResultData mapping
- **Do NOT modify:** Phase 10C2 contract

### LeagueSimulator (application/service/simulation/)

- Simulates league round: iterates fixtures, calls matchSimulator
- Two paths: `useV23LeagueEngine=false` (DefaultMatchSimulator), `true` (MatchEngineImpl)
- `SimulationConfig` injects `useV23LeagueEngine` property
- **V24 integration point:** V24 would be a third path, same pattern as V23 flag
- **Do NOT modify:** Flag architecture is Phase 10C2/10C3 contract

---

## 2. Current Match/Result Model

### MatchResult (domain/model/entity/)

- Fields: homeGoals, awayGoals, homePossession, awayPossession, homeShots, awayShots, events (List<MatchEvent>), summary
- Synthetic event labels: "HOME_DEF_1", "AWAY_ST_1" — not real player names
- **V24 missing:** Real player attribution, per-shot xG, timeline, cards/injuries

### MatchEvent (domain/model/valueobject/)

- Fields: type (MatchEventType), minute, playerId, description
- Types: GOAL, SHOT, SAVE, MISS, FOUL, CARD, INJURY, OFFSIDE, CORNER, SUBSTITUTION
- **V24 reuse:** Enum types are complete for MVP; extend if needed
- **Do NOT modify:** V23 depends on existing types

### MatchEventType (domain/model/valueobject/)

- Enum with all event types already defined
- **V24 reuse:** Can use directly

### MatchFixture (domain/model/valueobject/)

- Fields: matchId, homeTeamId, awayTeamId, round, status, result (MatchResultData)
- Nested `MatchResultData`: 6 fields (goals, possession, shots)
- **V24 missing:** No event list, no timeline, no per-shot xG, no narrative

---

## 3. Career/Team/Player Data

### CareerSave (domain/model/entity/)

- Contains: teamManager, playerManager, seasonManager, teamStarting11 (Map<String, List<String>>), tournamentState
- Persisted in Redis as JSON

### SessionTeam (domain/model/entity/)

- Fields: sessionTeamId, name, country, worldTeamId, formation (string like "4-3-3"), overall
- No tactical style field currently — TeamStyle is computed/simulator-only
- **V24 reuse:** Team identity, formation string, overall

### SessionPlayer (domain/model/entity/)

- Identity: sessionPlayerId, basePlayerId, worldPlayerId, name, age, position (GK/DEF/MID/WINGER/ATT)
- Attributes: attack, defense, technique, speed, stamina, mentality
- Dynamic: energy (starts 100), form (starts 50), injured, injuryType, injuryRemainingMatches
- Method: `calculateOverall()` — position-weighted average of 6 attributes
- **V24 reuse:** Full player data (names, positions, attributes, energy, form) — already available
- **V24 missing:** Match-specific state (stamina drain, yellow cards, match fitness)

### Formation (domain/model/valueobject/)

- String field on SessionTeam: "4-3-3", "4-4-2", etc.
- **V24 reuse:** Formation string for tactical analysis; could be parsed into positions

### Starting XI

- `CareerSave.teamStarting11: Map<String, List<String>>` — teamId → list of sessionPlayerIds
- Populated via LineupCommandUseCaseImpl from frontend
- **V24 reuse:** Real starting XI player IDs — all SessionPlayer data accessible via playerManager

---

## 4. Integration Points

### MatchSimulationOrchestrator

- Injects `LeagueSimulator` (via SimulationConfig bean)
- Calls `leagueSimulator.simulateLeagueRound(career, round)`
- **V24 insertion point:** Could inject a separate V24LeagueSimulator or add V24 flag to SimulationConfig

### SimulationConfig (application/config/)

- Creates `LeagueSimulator` bean with `useV23LeagueEngine` property
- **V24 insertion point:** Add `useV24DetailedEngine` property alongside existing flag

### CareerSimulator / RoundController

- Triggers league simulation per round
- CareerSave passed through — has all player/team data needed for V24

---

## 5. Audit Summary

| Category | Reusable | Missing for V24 |
|----------|----------|-----------------|
| Deterministic seed | Yes — MatchEngineImpl pattern | None |
| Goal/shot/xG model | Yes — lambdas, possession | Per-shot xG, real player attribution |
| Team OVR | Yes — TeamOverallCalculator | None |
| Player data | Yes — SessionPlayer has all attributes | Match-state per player (stamina drain, cards) |
| Starting XI | Yes — teamStarting11 | None |
| Formation | Yes — string field | Parsed position map |
| TeamStyle | Yes — enum | None |
| Events | Yes — MatchEventType enum | Real player labels, per-event xG |
| Timeline | **No** | Entire timeline model needed |
| Cards/injuries | Partial — SessionPlayer has fields | Match-event model, substitution rules |
| Persistence | Yes — Redis schema stable | MatchResultData too narrow for V24 detail |

### Risks
- V23 must remain stable: do not modify MatchEngineImpl core paths
- MatchFixture.MatchResultData has only 6 fields — V24 detail needs separate storage path
- SessionPlayer.energy/form exist but not updated during match simulation

### Safest Next Step
**V24A:** Create isolated `application/service/simulation/v24/` package with domain models only (no production wiring). Use existing SessionPlayer/TeamStyle/TeamOverallCalculator data. Seed from same deterministic pattern. No Redis changes, no API changes, no V23 modification.

**V24A is now COMPLETED** (commits `fd35398`, `0214a74`, `4e2901a`). V24A delivered: 10 model classes, `V24DetailedMatchEngine` skeleton, `V24DetailedMatchResultAdapter`, 8 tests. V24 remains isolated from production flow.

**V24B is now COMPLETED** (commit `b4735a8`). V24B extended the skeleton with minute-by-minute simulation: real shot/xG (multi-factor model), possession per minute (TeamStyle-weighted), real player attribution from SessionPlayer IDs, deterministic seed, stats consistency (goals=goalEvents, shots>=goals, possession=100). 22 new tests added. V24 remains isolated — no production wiring, no Redis/API/frontend changes.

**V24D1 is now COMPLETED** (commit `55f7638`). V24D1 added formation parser and formation-aware shooter/assist selection:
- `V24FormationParser` — parses formation strings, safe fallback to 4-4-2, rejects != 10 outfield players
- `V24PlayerSelector` — new `selectShooter(List, String)` and `selectShooter(List, V24Formation)` overloads
- V24D1 did NOT modify: `V24MatchContext`, `SessionTeam`, `LeagueSimulator`, or any production flow
- V24D1 tests: 15 tests, all passing
- V24 remains isolated — no production wiring, no Redis/API/frontend changes
- Recommended next: V24D2 — assist/key-pass model + event richness

**V24D2 is now COMPLETED** (commit `1149c0b`). V24D2 added assist/key-pass model and event richness:
- `V24AssistModel` — pure function assist/key-pass provider selection
- `selectAssistProvider(candidates, shooter, formation, style, random)` — formation-aware weighted selection
- `assistProbability(shooter, candidate, formation, style)` — clamped [0.10, 0.85]
- Formation modifiers: 4-3-3 boosts WINGER, 4-2-3-1 boosts MID/WINGER, 3-5-2 boosts MID
- Style modifiers: POSSESSION +0.08, ATTACKING +0.05, DEFENSIVE -0.05
- Stamina penalty: currentStamina < 30 = -0.05
- Real `relatedPlayerId`/`relatedPlayerName` attribution on GOAL events
- GOAL description: "Goal by {shooter} assisted by {assist} {minute}'"
- V24D2 did NOT modify: `V24MatchEvent`, `V24PlayerSelector`, `V24MatchContext`
- V24D2 tests: 22 tests, all passing
- V24 remains isolated — no production wiring, no Redis/API/frontend changes
- Recommended next: V24D3A/V24D3B — shot coordinates and player ratings helpers

**V24D3A is now COMPLETED** (commit `9a632c4`). V24D3A added shot coordinate helpers:
- `V24ShotCoordinate` — immutable value object (x, y, location, distanceToGoal, angleToGoal, insideBox)
- `V24ShotCoordinateGenerator` — deterministic generator using passed Random
- Coordinate ranges per V24ShotLocation: SIX_YARD_BOX, PENALTY_AREA_CENTER, PENALTY_AREA_WIDE, OUTSIDE_BOX, LONG_RANGE
- Pitch coordinate system: x ∈ [0,100], y ∈ [0,100], goal center at (100, 50)
- `penalty(Random)` method for penalty kick coordinates
- V24D3A did NOT modify: `V24MatchEvent`, `V24DetailedMatchResult`, or xG formula
- V24D3A tests: 17 tests, all passing
- V24 remains isolated — no production wiring, no Redis/API/frontend changes

**V24D3B is now COMPLETED** (commit `09b89b2`). V24D3B added player rating helper:
- `V24PlayerRatingModel` — pure helper, no mutable state, no Random
- `computePlayerRating(playerId, timeline)` → double; `computeRatings(Collection, timeline)` → Map
- `clampRating(double)` → [1.0, 10.0]
- Rating rules: base 6.0, goal +0.8, assist +0.5, key-pass +0.30, shot +0.10 (high-xg +0.05), yellow -0.3, red -1.5, injury -0.2, foul -0.05, substitution-in +0.05
- V24D3B did NOT modify: `V24DetailedMatchResult`, `V24PlayerMatchState`, `V24MatchEvent`, or SessionPlayer
- V24D3B tests: 31 tests, all passing
- V24 remains isolated — no production wiring, no Redis/API/frontend changes
- Recommended next: V24D4A DTO/storage design classes or V24D3C optional schema enrichment

**V24D4A is now COMPLETED** (commit `3c653f1`). V24D4A added detailed match DTOs and storage port interface:
- `V24DetailedMatchData` — immutable snapshot DTO with `fromResult()` factory, timeline DTOs, playerRatings DTOs, engineVersion/schemaVersion
- `V24MatchEventDto` — event DTO with `fromEvent(V24MatchEvent)` converter; shotCoordinate nullable (V24MatchEvent schema unchanged)
- `V24ShotCoordinateDto` — coordinate DTO with `fromCoordinate(V24ShotCoordinate)`
- `V24PlayerMatchRatingDto` — player rating/stat bundle DTO, rating clamped [1.0, 10.0]
- `V24DetailedMatchStoragePort` — interface only (`save`, `findByMatchId`, `deleteByCareerId`); no Redis adapter
- `V24PlayerMatchStatsModel` — pure helper deriving stat bundles from timeline (goals, assists, keyPasses, shots, cards, injuries, fouls, subs) + rating integration
- V24D4A did NOT modify: `V24MatchEvent`, `V24DetailedMatchResult`, `V24DetailedMatchEngine`, or any production wiring
- V24D4A tests: 24 tests (10 `V24DetailedMatchDataTest` + 14 `V24PlayerMatchStatsModelTest`), all passing
- V24 remains isolated — no Redis adapter, no API endpoint, no frontend
- Recommended next: V24D4B Redis adapter behind feature flag, V24D4C API endpoint, or V24D3C optional schema enrichment

**V24D4B is now COMPLETED** (commit `ecea7d5`). V24D4B added Redis adapter for detailed match storage:
- `V24DetailedMatchRedisAdapter` — implements `V24DetailedMatchStoragePort`, stores at `career:{careerId}:match-detail:{matchId}`
- Storage key: `career:{careerId}:match-detail:{matchId}`
- Serialization: Jackson2Json via ReactiveRedisTemplate (same pattern as existing RedisEntityConfig adapters)
- `deleteByCareerId` implemented via KEYS pattern + bulk DELETE
- `RedisEntityConfig` updated with `v24DetailedMatchDataRedisTemplate` bean
- V24D4B did NOT modify: `V24DetailedMatchResult`, `V24MatchEvent`, `V24DetailedMatchEngine`, LeagueSimulator, SimulationConfig, MatchEngineImpl, or any production wiring
- V24D4B tests: 13 tests (`V24DetailedMatchRedisAdapterTest`), all passing
- V24 remains isolated — no API endpoint, no frontend, no production simulation wiring
- Recommended next: V24D4C query endpoint behind feature flag, or V24D3C optional schema enrichment, or Phase 6C / Phase 11

**V24D4C is now COMPLETED** (commit `ab3c5fd`). V24D4C added query endpoint for detailed match data:
- `V24DetailedMatchQueryService` — reads from `V24DetailedMatchStoragePort`, feature-gated
- `V24DetailedMatchController` — REST controller at `GET /api/careers/{careerId}/matches/{matchId}/detail`
- `V24SimulationConfig` — `@ConfigurationProperties` for `app.simulation.v24.*`
- Feature flag: `app.simulation.v24.expose-detail-api=false` (default false)
- Disabled/missing detail: returns 404
- Reads only from storage port — no V24 engine call, no production simulation wiring, no writes
- V24D4C did NOT modify: `V24DetailedMatchResult`, `V24MatchEvent`, `V24DetailedMatchEngine`, LeagueSimulator, SimulationConfig, MatchEngineImpl, MatchFixture, or any production wiring
- V24D4C tests: 12 tests (`V24DetailedMatchQueryServiceTest`), all passing
- V24 remains isolated — no frontend, no production simulation wiring
- Recommended next: V24D5 production integration planning, or V24D3C optional schema enrichment, or frontend match detail design, or Phase 6C / Phase 11

**V24D5A is now COMPLETED** (commit `8470779`). V24D5A added V24MatchContextFactory:
- `V24MatchContextFactory` — factory building `V24MatchContext` from `CareerSave`, `MatchFixture`, `SessionTeam` home/away, seed
- `build(...)` — primary API, styles default to `TeamStyle.BALANCED`
- `buildWithStyles(...)` — explicit TeamStyle overload
- `canBuild(...)` — returns false on validation failure, never throws
- Starting XI resolved from `CareerSave.teamStarting11` keyed by `MatchFixture.homeTeamId`/`awayTeamId`
- Bench derived from `CareerSave.getTeamSquad(teamId)` minus starter IDs
- V24D5A did NOT modify: LeagueSimulator, SimulationConfig, MatchEngineImpl, MatchFixture, CareerSave, SessionPlayer, SessionTeam, or any production wiring
- V24D5A tests: 20 tests (`V24MatchContextFactoryTest`), all passing
- V24 remains isolated — no production simulation wiring, no frontend
- Recommended next: V24D5B third LeagueSimulator path behind default-false flag, or V24D3C optional schema enrichment, or frontend match detail design, or Phase 6C / Phase 11

**V24D5B is now COMPLETED** (commit `cca2f6e`). V24D5B added third LeagueSimulator path behind `use-v24-detailed-engine=false`:
- `LeagueSimulator.simulateWithV24Engine()` — V24 path using `V24DetailedMatchEngine` when flag is true
- `SimulationConfig` — injected `use-v24-detailed-engine` property alongside existing `use-v23-engine`
- `application.yaml` — added `app.simulation.league.use-v24-detailed-engine: false` (default false)
- Flag precedence: V24 > V23 > default
- `V24MatchContextFactory.build()` called to build context from CareerSave + MatchFixture + SessionTeam home/away + seed
- `V24DetailedMatchResultAdapter.toMatchResultData()` maps V24DetailedMatchResult to MatchFixture.MatchResultData (6 fields)
- Context build failure falls back to default engine, round completes
- V24D5B did NOT modify: V24DetailedMatchResult, V24MatchEvent, V24DetailedMatchEngine, or any production wiring
- V24D5B tests: 11 tests (`V24LeagueSimulationPathTest`), all passing
- V24 remains isolated — no Redis detail persistence, no API/controller/frontend changes
- Recommended next: V24D5C detail persistence behind persist-detail flag, V24D3C optional schema enrichment, frontend match detail design, Phase 6C, or Phase 11

**V24D5C is now COMPLETED** (commit `d6b3661`). V24D5C added V24 detail persistence during simulation:
- `LeagueSimulator` — added `persistDetail` flag and `V24DetailedMatchStoragePort` dependency; `persistV24Detail()` called after V24 result when `persistDetail=true` and `storagePort != null`
- `SimulationConfig` — wires `app.simulation.v24.persist-detail` property to `LeagueSimulator`; `V24DetailedMatchStoragePort` bean injected
- `application.yaml` — `app.simulation.v24.persist-detail: false` remains default false
- `V24LeagueDetailPersistenceTest` — 9 tests covering: save not called when `persistDetail=false`, save called when `persistDetail=true`, snapshot has expected fields, save failure does not fail round, context build failure skips persistence, `persistDetail=true` without V24 flag does not persist, `exposeDetailApi` flag does not trigger persistence, aggregate MatchResultData still written to fixture, MatchResultData schema still 6 fields
- `V24DetailedMatchData.fromResult(careerId, seasonNumber, round, homeName, awayName, v24Result, playerRatings)` used to build immutable snapshot
- `V24DetailedMatchStoragePort.save(careerId, detail)` called only when: `use-v24-detailed-engine=true` AND `persist-detail=true` AND V24 simulation succeeds
- save failure is best-effort: `catch (Exception)` logs warning and round completes
- context build failure skips persistence and falls back safely to default engine
- `persist-detail=true` without V24 flag does not persist — flags are independent
- `expose-detail-api=true` does not trigger persistence — flags are independent
- empty `playerRatings` list currently passed — per-player rating persistence deferred to future phase
- V24D5C did NOT modify: V24DetailedMatchResult, V24MatchEvent, V24DetailedMatchEngine, MatchFixture.MatchResultData, CareerSave, SessionPlayer, SessionTeam, or any production wiring
- V24D5C tests: 9 tests (`V24LeagueDetailPersistenceTest`), all passing
- V24 remains isolated — no frontend
- Recommended next: V24D5E frontend match detail planning/design, V24D3C optional schema enrichment, Phase 6C, or Phase 11

**V24D5D is now COMPLETED** (commit `3995d3d`). V24D5D added end-to-end flag integration tests:
- `V24EndToEndFlagIntegrationTest` — 12 tests covering all flag combination scenarios
- allFlagsFalseUsesDefaultPath — default path used, no V24, no persistence
- v24EnabledPersistDisabledProducesAggregateOnly — V24 path without persistence
- v24EnabledPersistEnabledSavesDetail — V24 path with persistence via storagePort.save
- v24DisabledPersistEnabledDoesNotPersist — flags independent, no V24 = no persistence
- exposeDetailApiEnabledDoesNotTriggerSimulationOrPersistence — read-only flag has no simulation effect
- allFlagsTrueCompletesRoundAndPersistsDetail — all three flags together, round completes
- v24ContextFailureFallsBackAndDoesNotPersist — missing starting XI fallback, no save
- detailSaveFailureDoesNotFailRound — storagePort throws, round completes
- v24TakesPrecedenceOverV23WhenBothEnabled — V24 wins over V23 flag
- defaultFlagsRemainSafe — all flags default false, safe
- matchResultDataSchemaStillSixFields — MatchResultData unchanged
- noCareerStateMutationAfterV24Simulation — no energy/formation mutation
- V24D5D did NOT modify: V24DetailedMatchResult, V24MatchEvent, V24DetailedMatchEngine, MatchFixture.MatchResultData, CareerSave, SessionPlayer, SessionTeam, or any production wiring
- V24D5D tests: 12 tests (`V24EndToEndFlagIntegrationTest`), all passing
- Only tests changed — no production code
- Regression gate: 406 tests, 0 failures; 406 full suite total
- Recommended next: V24D5E5 shot map UI (now unblocked by V24D3C), Phase 6C, or Phase 11

**V24D5F — Player Ratings Persistence — COMPLETED (commit `0c4d62b`):**

`V24PlayerRatingsAssembler` — pure helper that resolves starting XI from `CareerSave.teamStarting11`, converts `SessionPlayer` → `V24PlayerMatchState` via `fromSessionPlayer()`, and delegates to `V24PlayerMatchStatsModel.computeRatings()` for deterministic stat/rating derivation from timeline.
- `LeagueSimulator.persistV24Detail()` now calls `v24PlayerRatingsAssembler.assemblePlayerRatings()` instead of passing `List.of()`
- `V24DetailedMatchData.fromResult(...)` now receives populated `playerRatings` when detail is persisted
- Ratings derived deterministically from match timeline (goals, assists, shots, cards, fouls, injuries, substitutions)
- `playerRatings` populated only when: `use-v24-detailed-engine=true` AND `persist-detail=true` AND V24 simulation succeeds
- `persist-detail=false` does NOT compute or persist ratings — flags are independent
- Save failure remains best-effort
- **No API schema changes, no Redis key format changes, no frontend changes**
- **No SessionPlayer/SessionTeam mutation**
- **No MatchFixture.MatchResultData change**
- **No CareerSave schema change**
- `V24PlayerRatingsPersistenceTest` — 12 new tests, all passing
- Regression gate: 406 tests, 0 failures; 406 full suite total

**V24D5E (Frontend) — Completed through V24D5E4 in separate frontend repo:**

V24D5E1 Design Document — COMPLETED (commit `e64c2d9` in root repo)

V24D5E2 Frontend API Client + Types — COMPLETED (frontend repo `050ab57` on branch `mvp-1`)
- Frontend repo: `front-ciber/project` / Football-angular
- `MatchDetailApiService.getMatchDetail(careerId, matchId): Observable<MatchDetail | null>`
- TypeScript interfaces: `MatchDetail`, `MatchEvent`, `MatchEventType`, `ShotCoordinate`, `ShotLocation`, `PlayerMatchRating`
- 200 → MatchDetail; 404 → null; 500+ → propagates
- URL-encoded `careerId` and `matchId`
- Empty `playerRatings` list handled; nullable `shotCoordinate` and `relatedPlayerId/relatedPlayerName` handled
- Validation: `npx tsc --noEmit` OK, `npx ng build` BUILD SUCCESS

V24D5E3 Read-only Match Detail Page — COMPLETED (frontend repo `0ba2305` on branch `mvp-1`)
- `V24MatchDetailPageComponent` — standalone Angular component at `src/app/features/match-detail/pages/v24-match-detail-page.component.ts`
- Route: `/careers/:careerId/matches/:matchId/detail`
- Header with score, round, season, V24 badge
- Summary cards: xG, shots, possession, goals
- Timeline (minute-sorted events), stats comparison table
- 404/null friendly unavailable state, 500/error retry state
- Empty playerRatings state, shot map deferred state
- Validation: `npx tsc --noEmit` OK, `npx ng build` BUILD SUCCESS
- No backend/API/Redis changes, no fixture/list UI modified
- V24D5E4 now complete, V24D5E5 shot map now complete, V24D5E6 page polish now complete (frontend repo commit `12d203d`)

V24D5E3B Fixture/List Entry Point — COMPLETED (frontend repo `d244097` on branch `mvp-1`)
- Dashboard fixture modal (`DashboardFixtureModalComponent`) updated with "📊 Detalle" link
- Link visible only for matches with `status === 'COMPLETED'`
- Link hidden when `careerId` unavailable or match not completed
- Route target: `/careers/:careerId/matches/:matchId/detail`
- Fixture modal does NOT call detail endpoint
- Validation: `npx tsc --noEmit` OK, `npx ng build` BUILD SUCCESS
- No backend/API/Redis changes, no player ratings UI, no shot map

V24D5E4 Player Ratings UI — COMPLETED (frontend repo `958af1e` on branch `mvp-1`)
- `V24MatchDetailPageComponent` — Players section now renders full table when `playerRatings` is non-empty
- Table columns: playerName, position, rating, goals, assists, keyPasses, shots, cards, injuries, substitutions
- Players grouped by team (Home first, Away second), sorted by rating descending
- Top-rated player highlighted green; rating color-coded (>=7.0 blue, <6.0 red)
- Uses existing `MatchDetail.playerRatings` from V24D5F backend persistence
- Empty state preserved for null/undefined/empty playerRatings
- No new endpoint calls, no route changes, no API client/type changes
- No backend/API/Redis changes, no shot map implementation
- Validation: `npx tsc --noEmit` OK, `npx ng build` BUILD SUCCESS

Root/backend repo (`mvp-1-performance-cleanup`) is unchanged by V24D5E frontend implementation.
Backend tests: 406 full suite, 406 regression gate, 0 failures.