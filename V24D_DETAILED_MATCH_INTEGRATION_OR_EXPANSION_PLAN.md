# V24D — Detailed Match Integration or Expansion Plan

**Status:** V24D1 COMPLETED — V24A/V24B/V24C/V24D1 all delivered
**Branch:** `mvp-1-performance-cleanup`
**Latest commit:** `55f7638` (feat: add V24 formation parser — V24D1)
**Tests:** 215 total (112 V23 + 8 V24A + 22 V24B + 58 V24C + 15 V24D1), 0 failures

---

## 1. Constraints (Non-Negotiable)

- **No production integration without separate approval**
- **No Redis schema changes without a migration plan**
- **No API/frontend changes without separate approval**
- **V23 remains production-stable** — V24 is parallel
- **V24 remains isolated** under `application/service/simulation/v24/` until explicitly wired
- **215 tests are the regression gate** — all must pass after any V24D change
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
`V24FormationParser` (formation parsing, safe fallback to 4-4-2), `V24PlayerSelector` formation-aware selection, `V24FormationParserTest` (15 tests). All 215 regression tests pass.

### Still Limited
- Formation parsing and tactical role weighting are now available from V24D1; remaining realism gaps are assist/key-pass logic, shot coordinates, richer event chains, player ratings, set pieces, stoppage time, and persistence/API integration.
- No assist/key-pass as first-class event logic
- No shot coordinates/shot map
- No goalkeeper save quality detail beyond xG
- No corner/free kick/penalty model beyond existing chance creation
- No stoppage time or extra time
- No team morale or player form post-match
- No detailed player ratings
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

**Pros:** Improves V24 quality without any integration risk; can be validated with 215-test regression gate; no career/API/Redis decisions needed.

**Cons:** Results remain accessible only via test or direct engine call.

**Tests:** New unit tests for each helper; existing 215 remain regression gate.

**Rollback:** `git checkout HEAD~1 -- src/main/java/.../simulation/v24/`

**Production touch:** No. **Redis/API/Frontend:** No.

**Recommendation:** YES — recommended path for V24D2 and later isolated V24 expansion.

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

**V24D2 (Isolated Expansion — Next)**
Assist and key-pass model + event richness.
- Add `V24AssistModel` — first-class assist probability after shot
- Add shot coordinates to V24MatchEvent
- Add player ratings (per-match score) to V24PlayerMatchState
- Tests: `V24AssistModelTest`, `V24PlayerRatingsTest`
- Risk: LOW
- Recommended next step

**V24D3 (Storage/API Design — Docs Only)**
Detailed result storage design and API/frontend DTO design.
- Document `V24DetailedMatchData` Redis shape
- Document `MatchDetailDto`, `ShotMapDto`, `PlayerPerformanceDto`
- No implementation
- Risk: LOW

**V24D4 (Production Integration — Only After V24D3)**
Feature-flagged V24 path in LeagueSimulator, only after storage design is approved.

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

## 12. V24D1 Specification (Archive)

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

## 13. Regression Command

After any V24D change, the full regression gate:

```
mvn test -Dtest=LeagueSimulatorTest,MatchResultDataAdapterTest,TeamOverallCalculatorTest,MatchEngineImplStrengthSimulationTest,MatchEngineImplStyleSimulationTest,MatchQualityMetricsTest,V23SimulationQualityGateTest,MatchEngineImplRoleContributionTest,MatchEngineImplEventConsistencyTest,MatchEngineImplDeterminismTest,MatchEngineImplMetricsValidationTest,MatchEngineImplPoissonValidationTest,MatchQualityComputerTest,MatchEngineImplTest,DivisionTest,V24DetailedMatchEngineDeterminismTest,V24TimelineOrderingTest,V24DetailedMatchResultAdapterTest,V24MatchContextValidationTest,V24TimelineConsistencyTest,V24ShotXgModelTest,V24PlayerAttributionTest,V24FatigueModelTest,V24DisciplineModelTest,V24InjuryModelTest,V24SubstitutionEngineTest,V24FormationParserTest
```

Expected: **215 tests, 0 failures**.

---

*This document is the authoritative V24D planning specification. No code implementation begins until this document is reviewed and a specific V24D1 phase is approved.*