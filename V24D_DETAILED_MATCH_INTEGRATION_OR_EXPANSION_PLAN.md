# V24D — Detailed Match Integration or Expansion Plan

**Status:** V24D5D COMPLETED — V24A/V24B/V24C/V24D1/V24D2/V24D3A/V24D3B/V24D4A/V24D4B/V24D4C/V24D5A/V24D5B/V24D5C/V24D5D all delivered; frontend still deferred
**Branch:** `mvp-1-performance-cleanup`
**Latest implementation commit:** `3995d3d` (test: add V24D5D end-to-end flag integration tests)
**Tests:** 389 total (112 V23 + 8 V24A + 22 V24B + 58 V24C + 15 V24D1 + 22 V24D2 + 17 V24D3A + 31 V24D3B + 24 V24D4A + 13 V24D4B + 12 V24D4C + 20 V24D5A + 11 V24D5B + 9 V24D5C + 12 V24D5D), 0 failures

---

## 1. Constraints (Non-Negotiable)

- **No production integration without separate approval**
- **No Redis schema changes without a migration plan**
- **No API/frontend changes without separate approval**
- **V23 remains production-stable** — V24 is parallel
- **V24 remains isolated** under `application/service/simulation/v24/` until explicitly wired
- **386 tests are the regression gate; full suite is 389 tests total** — all must pass after any V24D change
- Red-carded players remain non-substitutable (V24C invariant, never removed)

---

## 2. V24 Package Audit

### Complete (V24A)
`V24MatchEventType`, `V24ShotLocation`, `V24ShotQuality`, `V24MatchEvent`, `V24MatchTimeline`, `V24MatchClock`, `V24MatchContext`, `V24DetailedMatchResult`, `V24DetailedMatchResultAdapter` — deterministic engine, context validation, timeline ordering, event consistency.

### Complete (V24B)
`V24ShotXgCalculator` (multi-factor xG), `V24PlayerSelector` (position-weighted real player selection), real player attribution on all events, stats consistency enforced.

### Complete (V24C)
`V24FatigueModel` (per-minute drain + action drain + fatigue factor), `V24DisciplineModel` (modulated foul/yellow/red), `V24InjuryModel` (modulated injury probability), `V24SubstitutionEngine` (priority-based, max 5, same-position preference, duplicate prevention).

### Complete (V24D1)
`V24FormationParser` (formation parsing, safe fallback to 4-4-2), `V24PlayerSelector` formation-aware selection, `V24FormationParserTest` (15 tests). All 285 regression tests pass.

### Complete (V24D2)
`V24AssistModel` — pure function assist/key-pass provider selection with formation/style/stamina modifiers.

### Complete (V24D3A)
`V24ShotCoordinate` (immutable value object), `V24ShotCoordinateGenerator` (deterministic, uses passed Random), `V24ShotCoordinateTest` (17 tests). Coordinate system with pitch bounds, distance/angle to goal, insideBox flag. No event/result schema change. No xG recalibration.

### Complete (V24D3B)
`V24PlayerRatingModel` (pure helper), `V24PlayerRatingModelTest` (31 tests). Rating from timeline: base 6.0, goal +0.8, assist +0.5, key-pass +0.30, shot +0.10 (high-xg +0.05), cards -0.3/-1.5, injury -0.2, foul -0.05, substitution-in +0.05. Clamped [1.0, 10.0]. Helper-only, no result schema change.

### Complete (V24D4A)
`V24DetailedMatchData` (immutable snapshot DTO with `fromResult()` factory), `V24MatchEventDto` (event DTO with `fromEvent()` converter, shotCoordinate nullable), `V24ShotCoordinateDto` (coordinate DTO), `V24PlayerMatchRatingDto` (player rating/stat bundle DTO), `V24DetailedMatchStoragePort` (interface only, no implementation), `V24PlayerMatchStatsModel` (pure helper deriving stat bundles from timeline), `V24DetailedMatchDataTest` (10 tests), `V24PlayerMatchStatsModelTest` (14 tests). DTO design ready for Redis adapter. No Redis adapter yet, no API endpoint, no frontend. MatchFixture.MatchResultData unchanged. V24MatchEvent schema unchanged.

### Complete (V24D4B)
`V24DetailedMatchRedisAdapter` (implements `V24DetailedMatchStoragePort`), `V24DetailedMatchRedisAdapterTest` (13 tests). Redis adapter at `infrastructure/persistence/redis/V24DetailedMatchRedisAdapter.java`. Storage key: `career:{careerId}:match-detail:{matchId}`. Serialization: Jackson2Json via ReactiveRedisTemplate (same pattern as existing RedisEntityConfig adapters). `deleteByCareerId` implemented via KEYS pattern + bulk DELETE. `RedisEntityConfig` updated with `v24DetailedMatchDataRedisTemplate` bean. No API endpoint, no frontend, no production simulation wiring — adapter exists as a bean but no production flow calls it. No LeagueSimulator/SimulationConfig/MatchEngineImpl/MatchFixture changes. V24DetailedMatchResult unchanged, V24MatchEvent unchanged, V24DetailedMatchStoragePort interface unchanged.

### Complete (V24D5C)
`LeagueSimulator.persistV24Detail()` — best-effort detail persistence behind `app.simulation.v24.persist-detail=false`.
Uses `V24DetailedMatchData.fromResult(...)` and `V24DetailedMatchStoragePort.save(...)` only when V24 simulation succeeds and `persist-detail=true`.
Save failure logs and the round still completes.
Context build failure skips persistence.
`V24LeagueDetailPersistenceTest` added 9 tests.
No API/controller/frontend changes.
No Redis key format change.
`MatchFixture.MatchResultData` unchanged.
`CareerSave` schema unchanged.
`SessionPlayer`/`SessionTeam` not mutated.
`V24DetailedMatchResult`/`V24MatchEvent` unchanged.
Player ratings are currently persisted as an empty list; per-player rating persistence is deferred.

### Complete (V24D5D)
`V24EndToEndFlagIntegrationTest` — end-to-end flag integration tests for all flag combinations.
12 tests covering: all flags false (default path), V24 enabled + persist disabled (aggregate only), V24 enabled + persist enabled (saves detail), V24 disabled + persist enabled (no persistence — flags independent), expose-detail-api alone (no simulation/persistence effect), all flags true (round completes + saves detail), context build failure (fallback + no persistence), save failure (best-effort, round completes), V24 > V23 precedence, default flags safe, MatchResultData schema (6 fields), no career state mutation.
`V24EndToEndFlagIntegrationTest` added 12 tests.
No production code changes.
No API/controller/frontend changes.
No Redis key format change.
Regression gate: 386 tests, 0 failures; full suite: 389 tests.

### Still Limited
- Formation parsing and tactical role weighting are now available from V24D1; assist/key-pass selection is now available from V24D2; shot coordinates are now available from V24D3A (helper only, no event attachment); player ratings are now available from V24D3B (helper only, no result field); DTO/snapshot classes and storage port interface now available from V24D4A; Redis adapter exists from V24D4B and query endpoint exists from V24D4C but no frontend and no production simulation wiring; remaining realism gaps are event-level coordinate attachment, result-level ratings attachment, frontend match detail, set pieces, stoppage time, and full persistence/API integration.
- ~~No assist/key-pass as first-class event logic~~
- Shot coordinate helper exists (V24D3A) but no V24MatchEvent attachment and no UI shot map yet
- Player rating helper exists (V24D3B) but no V24DetailedMatchResult field and no UI/frontend yet
- DTO/snapshot classes (V24D4A), Redis adapter (V24D4B), query endpoint (V24D4C), V24MatchContextFactory (V24D5A), LeagueSimulator V24 branch (V24D5B), V24 detail persistence (V24D5C), and end-to-end flag tests (V24D5D) all exist and are tested. playerRatings currently uses empty list (per-player rating persistence deferred). V24D3C optional shot coordinate attachment still deferred. Frontend match detail UI still missing.
- No goalkeeper save quality detail beyond xG
- No corner/free kick/penalty model beyond existing chance creation
- No stoppage time or extra time
- No team morale or player form post-match
- No ball possession chains, pass/cross/dribble/turnover
- No weather/home advantage/referee profile
- `V24PlayerMatchState` stores only OVR-derived fields; no actual SessionPlayer mutation (correct for isolated design)

---

## 3. Production Simulation Path Gap Analysis

### Where V24 Could Plug In

Current production path:
```
LeagueSimulator.simulateRound()
  → DefaultMatchSimulator.simulateQuick()  OR  MatchEngineImpl.simulateWithStrength()
  → MatchResult.of(homeGoals, awayGoals, possession, shots, events, summary)
  → MatchResultDataAdapter.toMatchResultData()
  → stored in Redis as MatchFixture.MatchResultData (6 fields)
```

V24 could replace `MatchEngineImpl.simulateWithStrength()` for detailed matches:
- Input: `V24MatchContext` built from `SessionTeam`, `SessionPlayer` starting XI
- Output: `V24DetailedMatchResult` → mapped via `V24DetailedMatchResultAdapter`

### Why V24 Is Not Wired

1. **Storage gap** — `MatchFixture.MatchResultData` has only 6 fields. V24 has 13+ fields (xG, timeline, cards, injuries, subs, summary). No storage shape exists.
2. **API gap** — No frontend UI for detailed match data. No `MatchDetail` DTO.
3. **Career impact gap** — V24C does not mutate `SessionPlayer`. Injuries/fatigue/cards have no career persistence path.
4. **Feature flag missing** — No `useV24DetailedEngine` flag analogous to `useV23LeagueEngine`.

### Data Available at Simulation Time

| Data | Available? | Location |
|------|-----------|----------|
| Starting XI player IDs | Yes | `CareerSave.teamStarting11` |
| Player OVR/position/energy | Yes | `SessionPlayer` |
| Team style | Not persisted in SessionTeam | `TeamStyle` exists in simulation/domain layer; V24MatchContext carries style, but production team style requires Phase 6C/API/Redis decision |
| Formation string | Yes | `SessionTeam.formation` / `V24MatchContext.homeFormation`/`awayFormation` |
| Bench players | Derivable | `SessionPlayer` pool minus starting XI, or future lineup/bench model; not a dedicated `SessionTeam.bench` field unless separately implemented |
| SessionTeam ID | Yes | `SessionTeam.getSessionTeamId()` |

### Data Missing at Simulation Time

| Data | Gap |
|------|-----|
| Dedicated persisted match-detail key | V24MatchContext has matchId, but no Redis/API storage key/shape exists for detailed timeline data |
| Opposition tactical instructions | No tactical instruction model |
| Match weather/venue/referee | Not in simulation context |
| Player form/chemistry/morale | Not in SessionPlayer |
| Detailed injury severity | Binary injured/not-injured only |

---

## 4. Detailed Result Storage Gap

`MatchFixture.MatchResultData` has 6 fields: `homeGoals`, `awayGoals`, `homePossession`, `awayPossession`, `homeShots`, `awayShots`.

`V24DetailedMatchResult` has: `matchId`, `homeTeamId`, `awayTeamId`, `homeGoals`, `awayGoals`, `homeXg`, `awayXg`, `homeShots`, `awayShots`, `homePossession`, `awayPossession`, `timeline` (list of V24MatchEvent), `summary`.

**Gap: No storage shape for timeline, xG, event-level attribution, cards, injuries, substitutions.**

### Decision Points Before Integration

- **Discard details?** — `V24DetailedMatchResultAdapter` already discards everything but 6 aggregate fields. Could remain permanently discarded.
- **New Redis object?** — `V24DetailedMatchData` or `V24MatchSnapshot` stored separately with matchId key. Requires Redis schema migration plan.
- **Expose via API without persistence?** — `V24DetailedMatchResult` returned as API response DTO. Frontend decides what to display.
- **Separate `MatchDetail` DTO?** — Convert V24DetailedMatchResult to a new DTO for API exposure, separate from storage.
- **In-memory only?** — V24 remains a research/analysis tool, results never persisted or exposed.

---

## 5. API/Frontend Gap

No UI exists for:
- Shot map / xG chart
- Match event timeline
- Player performance details
- Card/injury/sub history
- Possession timeline graph

Exposing V24 data requires: new API endpoint, new DTO, frontend component design, and separate approval for all three layers.

---

## 6. Career Impact Gap

V24C mutates only `V24PlayerMatchState` during a match. `SessionPlayer` is **never mutated**. This means:

| Aspect | V24C Behavior | Production Expectation |
|--------|---------------|----------------------|
| Injuries | Set `injured=true` on V24PlayerMatchState only | Would need SessionPlayer injury flag update |
| Fatigue | Drains `currentStamina` on V24PlayerMatchState only | Would need SessionPlayer.energy update |
| Yellow cards | Counted on V24PlayerMatchState only | No competition disciplinary impact |
| Substitutions | V24PlayerMatchState on/off pitch only | No SessionTeam starting XI change |

**This is correct and intentional for isolated V24.** Before production wiring, must decide: does V24 drive actual career state, or is it a separate analysis engine?

---

## 7. Formation/Tactics Gap

- `TeamStyle` is consumed (ATTACKING, DEFENSIVE, COUNTER, POSSESSION, BALANCED)
- Formation strings are now parsed by `V24FormationParser` (V24D1).
- `V24PlayerSelector` now has formation-aware shooter/assist selection.
- V24D1 does not persist formation or team style; it only consumes existing formation strings.
- No pressing intensity, build-up speed, risk appetite, width/height/line settings.
- Formation affects selection weights, but not yet full tactical behavior or possession-chain logic.

---

## 8. Match Realism Gaps Remaining After V24C

| Gap | Impact | V24D Candidate? |
|-----|--------|-----------------|
| No assist/key-pass first-class logic | Missing chance creation chain | Yes |
| No shot coordinates | No shot map UI possible | Yes |
| No goalkeeper save quality detail | Saves are binary | Yes |
| No corner/free kick model | Set pieces not modeled | Yes |
| No stoppage time | 90 minutes is exactly 90 events | Yes |
| No extra time/penalties | No knockout stage support | Future |
| No team morale/chemistry | Form doesn't carry across matches | Future |
| No player post-match form update | Form is static per SessionPlayer | Future |
| No detailed player ratings | No per-match player scores | Yes |
| No possession chains/pass sequences | Possession is aggregate only | Yes |
| No weather/home advantage/referee | No situational modifiers | Future |

---

## 9. Options

### Option A — Keep V24 Isolated, Improve Realism

**Description:** Continue isolated V24 realism after V24D1: assist/key-pass events, shot coordinates, player ratings, set-piece detail, event richness, and possession-chain improvements — all inside the isolated package.

**Files:** `V24AssistModel.java`, `V24ShotCoordinates.java`, `V24PlayerRatings.java`

**Risk:** LOW — no production touch

**Pros:** Improves V24 quality without any integration risk; can be validated with 334-test regression gate; no career/API/Redis decisions needed.

**Cons:** Results remain accessible only via test or direct engine call.

**Tests:** New unit tests for each helper; existing 334 remain regression gate.

**Rollback:** `git checkout HEAD~1 -- src/main/java/.../simulation/v24/`

**Production touch:** No. **Redis/API/Frontend:** No.

**Recommendation:** YES — recommended path for V24D3C optional schema enrichment and later isolated V24 expansion.

---

### Option B — Detailed Result Storage Design (Docs Only)

**Description:** Create `V24D_STORAGEDESIGN.md` with proposed Redis shape, DTOs, and migration plan. No implementation.

**Files:** New design doc only.

**Risk:** LOW — documentation only.

**Pros:** Clarifies integration requirements before any code; enables parallel frontend/API design work.

**Cons:** No immediate value; planning exercise only.

**Tests:** None.

**Rollback:** Delete the doc file.

**Production touch:** No (docs only). **Redis/API/Frontend:** No.

**Recommendation:** YES — should run in parallel with Option A work.

---

### Option C — Feature-Flagged V24 Path in LeagueSimulator (Implementation)

**Description:** Add `useV24DetailedEngine` flag to `SimulationConfig` and `LeagueSimulator`. When enabled, use `V24DetailedMatchEngine` instead of `MatchEngineImpl` for league simulation.

**Risk:** MEDIUM/HIGH — touches `LeagueSimulator`, `SimulationConfig`, career data flow.

**Files:** `LeagueSimulator.java`, `SimulationConfig.java`, potentially `V24MatchContextFactory`.

**Pros:** Enables live testing of V24 with real career data; analogous to V23 flag pattern.

**Cons:** Requires V24 match context from career data; storage gap remains unresolved; career impact of V24 injuries/fatigue undefined; this is integration without a plan for results.

**Tests:** `LeagueSimulatorV24PathTest` (new).

**Rollback:** Remove flag, restore previous LeagueSimulator behavior.

**Production touch:** Yes. **Redis:** Only if V24 results stored. **API/Frontend:** No.

**Recommendation:** NOT YET — requires Option B storage design first.

---

### Option D — API/DTO Exposure Design (Docs Only)

**Description:** Design `MatchDetailDto`, `PlayerMatchPerformanceDto`, `ShotMapDto` and the endpoints that would expose V24 data. No implementation.

**Files:** New design doc only.

**Risk:** LOW — documentation only.

**Pros:** Frontend team can begin design work in parallel; surfaces API decisions early.

**Cons:** No immediate value; requires Option B storage decision first.

**Tests:** None.

**Rollback:** Delete the doc file.

**Production touch:** No (docs only). **Redis/API/Frontend:** No.

**Recommendation:** YES — should follow Option B.

---

### Option E — Full Production Integration

**Description:** Wire V24 into LeagueSimulator, add Redis storage, add API endpoints, add frontend components — all at once.

**Risk:** HIGH — too many concurrent decisions; V23 stability at risk.

**Recommendation:** NO.

---

## 10. Recommended V24D Path

### Recommended Sequence

**V24D1 (Isolated Expansion — Completed)**
Formation parser + tactical role weighting inside isolated V24.
- Add `V24FormationParser` — parses formation strings into position slots
- Add role-weighted shooter selection by formation slot
- Tests: `V24FormationParserTest` (15 tests)
- Risk: LOW
- Files: `V24FormationParser.java`, `V24PlayerSelector` update
- **Status: COMPLETED** — commit `55f7638`

**V24D2 (Isolated Expansion — Completed)**
Assist and key-pass model + event richness.
- Add `V24AssistModel` — first-class assist probability after shot
- Formation-aware weighted provider selection (4-3-3 boosts WINGER, 4-2-3-1 boosts MID/WINGER, 3-5-2 boosts MID)
- Style modifiers (POSSESSION +0.08, ATTACKING +0.05, DEFENSIVE -0.05)
- Stamina penalty (currentStamina < 30 = -0.05)
- Real `relatedPlayerId`/`relatedPlayerName` attribution on GOAL events
- GOAL description: "Goal by {shooter} assisted by {assist} {minute}'"
- Tests: `V24AssistModelTest` (22 tests)
- Risk: LOW
- **Status: COMPLETED** — commit `1149c0b`

**V24D3 (Isolated Expansion — ✅ COMPLETED)**
Shot coordinates (V24D3A) and player ratings helper (V24D3B) delivered. Both remain helper-only — no event/result schema change. V24D3C (optional schema enrichment) deferred. Recommended next: V24D4 (Storage/API design) or Phase 6C.

**V24D4A (DTO/Storage Design — Completed)**
DTO/snapshot classes and storage port interface — no Redis adapter yet.
- `V24DetailedMatchData`, `V24MatchEventDto`, `V24ShotCoordinateDto`, `V24PlayerMatchRatingDto`
- `V24DetailedMatchStoragePort` (interface only)
- `V24PlayerMatchStatsModel` (pure helper deriving stat bundles from timeline)
- Tests: `V24DetailedMatchDataTest` (10) + `V24PlayerMatchStatsModelTest` (14)
- Risk: LOW
- **Status: COMPLETED** — commit `3c653f1`

**V24D4B (Redis Adapter — Completed)**
Implements `V24DetailedMatchStoragePort` behind feature flag.
- `V24DetailedMatchRedisAdapter` at `infrastructure/persistence/redis/`
- Storage key: `career:{careerId}:match-detail:{matchId}`
- Serialization: Jackson2Json via ReactiveRedisTemplate
- `deleteByCareerId` via KEYS pattern + bulk DELETE
- `RedisEntityConfig` updated with `v24DetailedMatchDataRedisTemplate` bean
- Tests: `V24DetailedMatchRedisAdapterTest` (13 tests)
- No API endpoint, no frontend, no production simulation wiring
- **Status: COMPLETED** — commit `ecea7d5`

**V24D4C (Query Endpoint — Completed)**
GET `/api/careers/{careerId}/matches/{matchId}/detail` behind feature flag.
- `V24DetailedMatchQueryService` — reads from `V24DetailedMatchStoragePort`, feature-gated
- `V24DetailedMatchController` — REST controller at the endpoint
- `V24SimulationConfig` — `@ConfigurationProperties` for `app.simulation.v24.*`
- Feature flag: `app.simulation.v24.expose-detail-api=false` (default false)
- Disabled/missing detail: returns 404
- Reads only from storage port — no V24 engine call, no production simulation wiring
- Tests: `V24DetailedMatchQueryServiceTest` (12 tests)
- **Status: COMPLETED** — commit `ab3c5fd`

**V24D5A (Context Factory — Completed)**
- `V24MatchContextFactory` — factory building V24MatchContext from CareerSave, MatchFixture, SessionTeam, seed
- `V24MatchContextFactoryTest` (20 tests)
- No runtime behavior change — isolated, not wired to production
- **Status: COMPLETED** — commit `8470779`

**V24D5B (LeagueSimulator V24 Path — Completed)**
- Third LeagueSimulator path behind `app.simulation.league.use-v24-detailed-engine=false` (default false)
- Flag precedence: V24 > V23 > default
- `simulateWithV24Engine()` calls `V24DetailedMatchEngine` via `V24MatchContextFactory`
- `V24DetailedMatchResultAdapter.toMatchResultData()` maps to `MatchFixture.MatchResultData` (6 fields)
- Context build failure falls back to default — round completes
- `V24LeagueSimulationPathTest` (11 tests)
- No Redis detail persistence — `V24DetailedMatchStoragePort.save(...)` not called
- **Status: COMPLETED** — commit `cca2f6e`

**V24D5C (Detail Persistence — Completed)**
- Save `V24DetailedMatchData` to Redis behind `app.simulation.v24.persist-detail=false` (default false)
- `LeagueSimulator.persistV24Detail()` called only when: `use-v24-detailed-engine=true` AND `persist-detail=true` AND V24 simulation succeeds
- Best-effort Redis write — failure logs and round still completes
- `V24DetailedMatchData.fromResult(...)` used to build snapshot
- Empty `playerRatings` list passed — per-player rating persistence deferred
- No frontend, no API/controller changes
- **Status: COMPLETED** — commit `d6b3661`

**V24D5D (End-to-End Integration Tests — Completed)**
- All flag combinations validated end-to-end (commit `3995d3d`, 12 tests)

**V24D5E (Frontend Planning/Design — Partial: E1+E2 done, E3+ pending)**

V24D5E1 Design Document — COMPLETED (commit `e64c2d9` in root repo)
V24D5E2 Frontend API Client + Types — COMPLETED (frontend repo `050ab57` on `mvp-1`)
V24D5E3 Read-only Match Detail Page — PENDING
V24D5E4 Player Ratings UI — Deferred (playerRatings backend persistence needed)
V24D5E5 Shot Map UI — Deferred (V24D3C shot coordinate attachment needed)

Frontend repo: `front-ciber/project` / Football-angular / `mvp-1`

---

## 11. V24D1 Completion Record

**Commit:** `55f7638` — feat: add V24 formation parser (V24D1)
**Date:** 2026-05-08
**Tests:** 15 new (`V24FormationParserTest`), 215 total, 0 failures
**V24D1 delivered:**
- `V24FormationParser` — parses "4-4-2", "4-3-3", "4-2-3-1", "3-5-2", "3-4-3", "5-3-2", "5-4-1"
- Safe fallback to "4-4-2" for null/blank/invalid input
- Rejects formations with != 10 outfield players
- `V24PlayerSelector` — formation-aware `selectShooter(List, String)` and `selectShooter(List, V24Formation)` overloads
- Original `selectShooter(List)` preserved for backward compatibility
- **No V24MatchContext modification**
- **No persisted team style added**
- **No SessionTeam modification**
- **No production wiring**
- **No Redis/API/frontend changes**
- Regression gate: 215 tests, 0 failures

**V24D1 did NOT add:**
- No production integration (LeagueSimulator unchanged)
- No Redis schema changes
- No V24MatchContext modification
- No SessionTeam.style field
- No API/frontend changes

---

## 12. V24D2 Completion Record

**Commit:** `1149c0b` — feat: add V24 assist model (V24D2)
**Date:** 2026-05-08
**Tests:** 22 new (`V24AssistModelTest`), 237 total at V24D2 completion, 0 failures
**V24D2 delivered:**
- `V24AssistModel` — pure function assist/key-pass provider selection
- `selectAssistProvider(candidates, shooter, formation, style, random)` — formation-aware weighted selection
- `assistProbability(shooter, candidate, formation, style)` — clamped [0.10, 0.85]
- `shouldCreditAssist(...)` — deterministic assist credit decision
- Formation modifiers: 4-3-3 boosts WINGER (2.0), 4-2-3-1 boosts MID (2.2)/WINGER (2.0), 3-5-2 boosts MID (2.2)
- Style modifiers: POSSESSION +0.08, ATTACKING +0.05, COUNTER +0.03, DEFENSIVE -0.05
- Stamina penalty: currentStamina < 30 = -0.05
- Real `relatedPlayerId`/`relatedPlayerName` on GOAL events via `V24DetailedMatchEngine`
- GOAL description: "Goal by {shooter} assisted by {assist} {minute}'" when assist credited
- Integration: `V24DetailedMatchEngine` uses `assistModel.selectAssistProvider()` instead of `selector.selectAssistProvider()`
- **V24MatchEvent unchanged** (no schema change)
- **V24PlayerSelector unchanged** (original method preserved)
- **V24MatchContext unchanged**
- **No production wiring**
- **No Redis/API/frontend changes**
- Regression gate at V24D2 completion: 237 tests, 0 failures

**V24D2 did NOT add:**
- No production integration (LeagueSimulator unchanged)
- No Redis schema changes
- No V24MatchEvent schema modification
- No V24PlayerSelector modification
- No V24MatchContext modification
- No API/frontend changes

---

## 13. V24D1 Specification (Archive)

**Goal:** Formation parser + tactical role weighting for isolated V24.

**Files Likely Affected:**
- New: `V24FormationParser.java`
- Modify: `V24PlayerSelector.java` (role-weighted selection)
- New: `V24FormationParserTest.java`

**Non-Goals:** No production wiring, no Redis, no API, no frontend. V24D1 must not add a persisted team style field or modify `SessionTeam.style`.

**Risk:** LOW — isolated, additive, test-covered.

**Tests:** `V24FormationParserTest` (15 tests), 215 total regression gate.

**Rollback:**
```bash
git checkout HEAD~1 -- src/main/java/.../simulation/v24/V24FormationParser.java
git checkout HEAD~1 -- src/main/java/.../simulation/v24/V24PlayerSelector.java
```

---

## 14. Regression Command

After any V24D change, the full regression gate:

```
mvn test -Dtest=V24MatchContextFactoryTest,V24DetailedMatchQueryServiceTest,V24DetailedMatchRedisAdapterTest,V24DetailedMatchDataTest,V24PlayerMatchStatsModelTest,V24PlayerRatingModelTest,V24ShotCoordinateTest,V24AssistModelTest,V24FormationParserTest,V24SubstitutionEngineTest,V24InjuryModelTest,V24DisciplineModelTest,V24FatigueModelTest,V24DetailedMatchEngineDeterminismTest,V24TimelineOrderingTest,V24DetailedMatchResultAdapterTest,V24MatchContextValidationTest,V24TimelineConsistencyTest,V24ShotXgModelTest,V24PlayerAttributionTest,LeagueSimulatorTest,MatchResultDataAdapterTest,TeamOverallCalculatorTest,MatchEngineImplStrengthSimulationTest,V24LeagueSimulationPathTest,MatchEngineImplStyleSimulationTest,MatchQualityMetricsTest,V23SimulationQualityGateTest,MatchEngineImplRoleContributionTest,MatchEngineImplEventConsistencyTest,MatchEngineImplDeterminismTest,MatchEngineImplMetricsValidationTest,MatchEngineImplPoissonValidationTest,MatchQualityComputerTest,MatchEngineImplTest,DivisionTest,V24LeagueDetailPersistenceTest
```

Expected: **374 tests (regression gate), 0 failures; 377 full suite total (112 V23 + 8 V24A + 22 V24B + 58 V24C + 15 V24D1 + 22 V24D2 + 17 V24D3A + 31 V24D3B + 24 V24D4A + 13 V24D4B + 12 V24D4C + 20 V24D5A + 11 V24D5B + 9 V24D5C)**.

---

*This document is the authoritative V24D planning specification. No code implementation begins until this document is reviewed and a specific V24D phase is approved.*

---

## 15. V24D2 Specification (Archive)

**Goal:** Assist/key-pass model + event richness for isolated V24.

**Files Likely Affected:**
- New: `V24AssistModel.java`
- Modify: `V24DetailedMatchEngine.java` (assist integration, GOAL description)
- New: `V24AssistModelTest.java`

**Non-Goals:** No production wiring, no Redis, no API, no frontend. V24D2 must not modify V24MatchEvent schema, V24PlayerSelector, or V24MatchContext.

**Risk:** LOW — isolated, additive, test-covered.

**Tests:** `V24AssistModelTest` (22 tests), 237 total regression gate at V24D2 completion.

**Rollback:**
```bash
git checkout HEAD~1 -- src/main/java/.../simulation/v24/V24DetailedMatchEngine.java
git rm --cached src/main/java/.../simulation/v24/V24AssistModel.java
git rm --cached src/test/java/.../simulation/v24/V24AssistModelTest.java
```