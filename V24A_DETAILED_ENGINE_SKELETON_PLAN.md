# V24A — Detailed Engine Skeleton Plan

**Status:** COMPLETED
**Branch:** `mvp-1-performance-cleanup`
**Created:** 2026-05-06
**Completed:** 2026-05-06

**V24B completed later** in commit `b4735a8` — the V24A skeleton was extended with real minute-by-minute simulation, xG per shot, and player attribution. The V24A foundation (models, context, clock, timeline, adapter) remains unchanged.

---

## Goal

Create isolated `application/service/simulation/v24/` with domain models + deterministic skeleton. No production wiring, no Redis, no API, no V23 changes.

**Package:** `com.footballmanager.application.service.simulation.v24`

---

## Classes to Create

### Domain Models

**V24MatchEventType** — Enum: GOAL, SHOT, SHOT_ON_TARGET, SAVE, MISS, BLOCK, CHANCE_CREATED, FOUL, YELLOW_CARD, RED_CARD, INJURY, CORNER, OFFSIDE, SUBSTITUTION. No state, no tests needed.

**V24ShotLocation** — Enum zones: SIX_YARD_BOX, PENALTY_AREA_CENTER, PENALTY_AREA_WIDE, OUTSIDE_BOX, LONG_RANGE. Immutable, no tests.

**V24ShotQuality** — Record: location, shooterQuality, assistQuality, defensivePressure, goalkeeperQuality, tactic. Immutable xG input bundle.

**V24MatchClock** — Tracks minute (1-90). Mutable: `advance()`, `isRunning()`, `halftime()`. Deterministic seed-based.

**V24PlayerMatchState** — Mutable per-player state built from SessionPlayer. Fields: sessionPlayerId, name, position, attack/defense/technique/speed/stamina/mentality, currentStamina, form, yellowCards, redCard, injured, onPitch, teamId. Factory: `fromSessionPlayer(SessionPlayer, teamId)`. Mutators: `drainStamina(int)`, `addYellowCard()`, `substitute()`, `injure()`.

**V24TeamMatchState** — Mutable team aggregate. Fields: teamId, name, formation, style, startingPlayers(11), benchPlayers, goals, xg, shots, shotsOnTarget, possessionTicks. Factory: `create(SessionTeam, List<SessionPlayer> starting, List<SessionPlayer> bench)`. Validates 11 starting players.

**V24MatchEvent** — Immutable record: minute, type, teamId, playerId, playerName, relatedPlayerId, relatedPlayerName, xg, description. playerId/playerName are real from SessionPlayer.

**V24MatchTimeline** — Mutable event list. Methods: `addEvent(V24MatchEvent)`, `eventsByMinute(int)`, `goalEvents()`, `shotEvents()`, `homeGoals()`, `awayGoals()`.

**V24MatchContext** — Immutable input record: matchId, homeTeamId, awayTeamId, homeTeam, awayTeam, homeStartingPlayers(11), awayStartingPlayers(11), homeBenchPlayers, awayBenchPlayers, homeFormation, awayFormation, homeStyle, awayStyle. Factory validates 11 players per side.

**V24DetailedMatchResult** — Immutable output record: matchId, homeTeamId, awayTeamId, homeGoals, awayGoals, homeXg, awayXg, homeShots, awayShots, homePossession, awayPossession, timeline, summary.

### Engine

**V24DetailedMatchEngine** — Deterministic entry point:

```java
public class V24DetailedMatchEngine {
    public V24DetailedMatchResult simulate(V24MatchContext ctx, long seed) {
        Random random = new Random(seed);
        // Initialize team/player states from ctx
        V24MatchClock clock = new V24MatchClock(90);
        V24MatchTimeline timeline = new V24MatchTimeline();
        // Minute loop (placeholder in V24A): 1-3 events per minute
        while (clock.isRunning()) {
            simulateMinute(homeState, awayState, clock, timeline, random);
            clock.advance();
        }
        return finalizeResult(homeState, awayState, timeline, ctx);
    }
    // V24A produces placeholder events only — no real simulation
}
```

- Deterministic: `new Random(seed)` propagated throughout
- No Redis, no LeagueSimulator, no API
- Returns V24DetailedMatchResult with placeholder timeline

### Adapter (isolated, not wired in production)

**V24DetailedMatchResultAdapter** — Maps V24DetailedMatchResult → MatchFixture.MatchResultData (6 fields). Not integrated into any production flow in V24A.

---

## V24A Tests

| Test | Purpose | Key Assertions |
|------|---------|----------------|
| `V24DetailedMatchEngineDeterminismTest` | Same seed = same result | Two calls with same seed: identical goals, possession, timeline size |
| `V24TimelineOrderingTest` | Events ordered by minute | All events minute 1-90, sorted ascending |
| `V24DetailedMatchResultAdapterTest` | Adapter maps 6 fields | goals/possession/shots mapped to MatchResultData |
| `V24MatchContextValidationTest` | Rejects invalid input | Throws on != 11 starting players, null matchId |

---

## Validation Command

```
mvn test -Dtest=V24DetailedMatchEngineDeterminismTest,V24TimelineOrderingTest,V24DetailedMatchResultAdapterTest,V24MatchContextValidationTest,LeagueSimulatorTest,MatchResultDataAdapterTest,TeamOverallCalculatorTest,MatchEngineImplStrengthSimulationTest,MatchEngineImplStyleSimulationTest,MatchQualityMetricsTest,V23SimulationQualityGateTest,MatchEngineImplRoleContributionTest,MatchEngineImplEventConsistencyTest,MatchEngineImplDeterminismTest,MatchEngineImplMetricsValidationTest,MatchEngineImplPoissonValidationTest,MatchQualityComputerTest,MatchEngineImplTest,DivisionTest
```

Current baseline: 112 tests, 0 failures. V24A adds 8 tests. Actual total after V24A: 120 tests, 0 failures.

---

## Implementation Order

**V24A1 (models):** V24MatchEventType, V24ShotLocation, V24ShotQuality, V24MatchClock, V24PlayerMatchState, V24TeamMatchState, V24MatchEvent, V24MatchTimeline, V24MatchContext, V24DetailedMatchResult. Lowest risk — pure domain, no logic.

**V24A2 (engine):** V24DetailedMatchEngine + V24DetailedMatchResultAdapter. Deterministic skeleton with placeholder events.

**V24A3 (tests):** 4 test classes. Validates determinism, ordering, adapter, context validation.

**Recommended: V24A1 → V24A2 → V24A3 as sequential commits** — each reviewable/testable independently.

**V24MatchClock bug fixed during V24A3:** `isRunning()` originally used `currentMinute <= maxMinutes`, causing an infinite loop at minute 90 (when `currentMinute == maxMinutes == 90`, `isRunning()` was `true` but `advance()` did not increment). Fixed to `currentMinute < maxMinutes`.

**Next: V24B** — detailed event timeline with real shot xG, possession per minute, player attribution, tactical modifiers, fatigue/cards/injuries/substitutions.

---

## Design Compliance

| Rule | Compliant |
|------|-----------|
| No LeagueSimulator integration | Yes — isolated package |
| No persistence (Redis/API) | Yes — pure Java objects |
| MatchEngineImpl untouched | Yes — new class only |
| MatchResult/MatchFixture untouched | Yes — separate result type |
| SessionPlayer read-only | Yes — factory copy only |
| TeamStyle referenced | Yes — import allowed |
| Adapter isolated | Yes — not wired in V24A |