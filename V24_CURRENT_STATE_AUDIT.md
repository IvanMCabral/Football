# V24 Current State Audit

**Purpose:** Document existing simulation/domain state before designing V24 Detailed Match Engine.
**Branch:** `mvp-1-performance-cleanup`
**Status:** V24D2 COMPLETED — V24A/V24B/V24C/V24D1/V24D2 all delivered
**Latest commit:** `1149c0b` (feat: add V24 assist model — V24D2)
**Tests:** 237 total (112 V23 + 8 V24A + 22 V24B + 58 V24C + 15 V24D1 + 22 V24D2), 0 failures
**Date:** 2026-05-08

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
- Recommended next: V24D3 — shot coordinates, player ratings, or storage/API design