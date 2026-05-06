# V23 Phase 10C — Real OVR Integration Plan

**Status:** COMPLETED — Phase 10C1 implemented. Phase 10C2 pending decision.
**Branch:** `mvp-1-performance-cleanup`
**Latest commit:** `05597ab` (refactor: delegate league OVR calculation to TeamOverallCalculator)
**Created:** 2026-05-05

---

## Executive Summary

Phase 10A added `simulateWithStrength()` to `MatchEngineImpl`. Phase 10B added `TeamOverallCalculator`. Phase 10C targets integrating the calculator into an existing production path and optionally using `simulateWithStrength()` instead of the current simulation engine.

This audit inspects where and how to integrate real OVR computation into the league simulation flow, and whether to replace `DefaultMatchSimulator.simulateQuick()` with `MatchEngineImpl.simulateWithStrength()`.

---

## Audit Findings

### 1. LeagueSimulator — Current Integration Path

**Location:** `src/main/java/com/footballmanager/application/service/simulation/LeagueSimulator.java`

```java
public void simulateLeagueRound(CareerSave career, int round) {
    for (MatchFixture fixture : allFixtures) {
        if (fixture.getRound() != round) continue;
        if (!fixture.canBeSimulated()) continue;

        int homeOvr = calculateTeamOVR(career, fixture.getHomeTeamId());
        int awayOvr = calculateTeamOVR(career, fixture.getAwayTeamId());

        MatchSimulator.MatchResult result = matchSimulator.simulateQuick(
                fixture.getHomeTeamId(),
                fixture.getAwayTeamId(),
                homeOvr,
                awayOvr
        );

        MatchFixture.MatchResultData resultData = new MatchFixture.MatchResultData(
                result.homeGoals(), result.awayGoals(), 50, 50, 5, 5
        );

        tournamentState.recordMatchResult(fixture.getMatchId(), resultData);
    }
}
```

**Key observations:**
- Uses `MatchSimulator.simulateQuick()` (Domain service, NOT MatchEngineImpl)
- Has its own private `calculateTeamOVR()` — **duplicated logic** from Phase 10B
- `MatchResultData` is constructed with hardcoded possession (50/50) and shots (5/5)
- No `CareerSave` context passed to simulation — only team IDs and OVRs
- Only called for **non-user divisions** — user's division uses different path (likely V23 real-time)
- **No `Team` aggregate involved** — just string IDs

### 2. DefaultMatchSimulator — What LeagueSimulator Actually Calls

**Location:** `src/main/java/com/footballmanager/domain/service/DefaultMatchSimulator.java`

```java
public MatchResult simulateQuick(String homeTeamId, String awayTeamId, int homeOvr, int awayOvr) {
    int ovrDiff = homeOvr - awayOvr;

    double homeGoalProb = 0.8 + (ovrDiff * 0.02);  // 0.8 + bonus
    double awayGoalProb = 0.8 - (ovrDiff * 0.02); // 0.8 - penalty

    homeGoalProb = Math.max(0.3, Math.min(1.5, homeGoalProb));
    awayGoalProb = Math.max(0.3, Math.min(1.5, awayGoalProb));

    int homeGoals = calculateGoals(homeGoalProb);  // 5 iterations, Poisson-approximate
    int awayGoals = calculateGoals(awayGoalProb);

    // Small noise to break draws
    if (homeGoals == awayGoals && random.nextDouble() < 0.1) {
        if (random.nextBoolean()) homeGoals++;
        else awayGoals++;
    }

    return new MatchResult(homeGoals, awayGoals);
}
```

**Critical findings:**
- **Different goal model** from MatchEngineImpl — uses goal probability per opportunity (not Poisson lambda)
- OVR differential drives goal probability, not lambda splitting
- No possession calculation — hardcoded to 50/50 in `MatchResultData`
- No shot generation — hardcoded to 5/5 in `MatchResultData`
- No match events generated
- **This is a much simpler simulation than V23 MatchEngineImpl**
- Deterministic if `Random` is seeded (but it's not — `new Random()` each time)

### 3. Result Type Comparison

**MatchSimulator.MatchResult** (what LeagueSimulator gets):
```java
record MatchResult(int homeGoals, int awayGoals) {}
```
Minimal — only goals.

**MatchFixture.MatchResultData** (what gets persisted):
```java
record MatchResultData(
    int homeGoals, int awayGoals,
    int homePossession, int awayPossession,
    int homeShots, int awayShots
)
```
Six fields — goals, possession, shots.

**MatchResult** (from MatchEngineImpl):
```java
record MatchResult(
    int homeGoals, int awayGoals,
    int homePossession, int awayPossession,
    int homeShots, int awayShots,
    List<MatchEvent> events,
    String summary
)
```
Eight fields — adds events and summary.

**Compatibility issue:** To use `MatchEngineImpl.simulateWithStrength()` in LeagueSimulator, we need an adapter from `MatchResult` → `MatchResultData`. The events and summary fields are discarded.

### 4. TeamOverallCalculator — Where It Fits

**TeamOverallCalculator** has:
- `calculateFromSessionTeam(sessionTeamId, teamManager, playerManager)` → delegates to `CareerTeamManager.calculateTeamOVR()`
- `calculateFromStartingXI(sessionTeamId, career)` → uses starting XI, falls back to squad
- `calculateFromPlayerIds(playerIds, playerProvider)` → raw player ID list
- `calculateFallbackFromSquadSize(squadSize)` → matches `MatchEngineImpl.calculateTeamOverall()`

**LeagueSimulator** has its own private `calculateTeamOVR()`:
```java
private int calculateTeamOVR(CareerSave career, String sessionTeamId) {
    List<String> squadPlayerIds = career.getTeamManager().getTeamSquads()
            .getOrDefault(sessionTeamId, List.of());
    if (squadPlayerIds.isEmpty()) return 50;
    int totalOVR = 0, count = 0;
    for (String playerId : squadPlayerIds) {
        var player = career.getSessionPlayer(playerId);
        if (player != null) {
            totalOVR += player.calculateOverall();
            count++;
        }
    }
    return count > 0 ? totalOVR / count : 50;
}
```

**Duplication confirmed:** This is the same logic that `TeamOverallCalculator` now centralizes. Phase 10C should refactor this to use `TeamOverallCalculator`.

### 5. CareerSave Private OVR Method

**Location:** `src/main/java/com/footballmanager/domain/model/entity/CareerSave.java:143-158`

```java
private int calculateTeamOVR(String teamId) {
    List<String> playerIds = teamManager.getSquadPlayerIds(teamId);
    if (playerIds == null || playerIds.isEmpty()) {
        return 0;  // Note: returns 0, not 50
    }
    int totalOVR = 0, count = 0;
    for (String playerId : playerIds) {
        SessionPlayer p = playerManager.getSessionPlayer(playerId);
        if (p != null) {
            totalOVR += p.calculateOverall();
            count++;
        }
    }
    return count > 0 ? totalOVR / count : 0;  // Note: returns 0, not 50
}
```

**Observation:** Same logic as LeagueSimulator, but returns **0 for empty squad** vs 50 in LeagueSimulator. This is used for division assignment sorting only — not persisted. **Do NOT refactor unless clearly safe.**

### 6. Services That Trigger League Simulation

**LeagueSimulator.simulateLeagueRound()** is called from:
- `CareerSeasonManager` or similar service that processes match days
- Likely triggered by round advancement in career flow

**User division match simulation** is separate — uses `V23RealTimeAdapter` / `MatchEngineImpl` for user's own matches.

This means LeagueSimulator only handles **AI vs AI matches** in non-user divisions. Risk of breaking user-facing content is lower.

### 7. Integration Options — What Changes

**Option A (Refactor OVR only):**
- Replace LeagueSimulator's private `calculateTeamOVR()` with `TeamOverallCalculator`
- Keep `MatchSimulator.simulateQuick()` as-is
- OVR values identical → no simulation behavior change
- **Result type unchanged** — `MatchResultData` still from `MatchSimulator.MatchResult`
- **Lowest risk** — pure refactor, same result

**Option B (TeamOverallCalculator + MatchSimulator):**
- Replace private `calculateTeamOVR()` with `TeamOverallCalculator`
- Keep `MatchSimulator.simulateQuick()` unchanged
- Same OVR values as Option A
- **Low risk** — same simulation engine

**Option C (Replace simulation engine with MatchEngineImpl.simulateWithStrength()):**
- Use `TeamOverallCalculator` for OVR
- Call `MatchEngineImpl.simulateWithStrength()` instead of `MatchSimulator.simulateQuick()`
- Adapter needed: `MatchResult` → `MatchResultData`
- **Medium risk** — different simulation engine (Poisson vs probability-based)
- Goals/possession/shots will differ from current `DefaultMatchSimulator`
- Need to validate whether this is acceptable for non-user division simulation
- V23 MatchEngineImpl is reactive (`Mono<MatchResult>`) — async vs sync
- **More complex integration**

**Option D (Narrow integration in only one controlled endpoint):**
- Choose a narrower service to integrate
- Avoid touching `LeagueSimulator` if it's stable
- Harder to audit without codebase context

**Option E (Change MatchEngine port):**
- Add `simulateWithStrength()` to `MatchEngine` port interface
- **Not allowed** — Phase 10C constraint: do NOT change MatchEngine port
- Higher architecture impact

---

## Option Comparison

### Option A — Refactor LeagueSimulator OVR to TeamOverallCalculator

| Dimension | Impact |
|-----------|--------|
| Files affected | `LeagueSimulator.java` — replace private method body with `TeamOverallCalculator.calculateFromSessionTeam()` |
| API impact | None |
| Persistence impact | None |
| Frontend impact | None |
| Simulation behavior | None — same OVR values, same `MatchSimulator.simulateQuick()` engine |
| Test impact | None — OVR values unchanged |
| Risk level | **LOWEST** — pure refactor |
| Rollback plan | Revert method body — full regression not required |

### Option B — TeamOverallCalculator + keep DefaultMatchSimulator

| Dimension | Impact |
|-----------|--------|
| Files affected | `LeagueSimulator.java` — same as Option A |
| API impact | None |
| Persistence impact | None |
| Frontend impact | None |
| Simulation behavior | None — same simulation engine |
| Test impact | None |
| Risk level | **LOW** — same as Option A |
| Rollback plan | Revert method body |

### Option C — Replace DefaultMatchSimulator with MatchEngineImpl.simulateWithStrength()

| Dimension | Impact |
|-----------|--------|
| Files affected | `LeagueSimulator.java` — change `matchSimulator.simulateQuick()` call to `teamOverallCalculator` + `matchEngine.simulateWithStrength()`; add `MatchResult`→`MatchResultData` adapter |
| API impact | None |
| Persistence impact | None — `MatchResultData` structure unchanged (adapter maps fields) |
| Frontend impact | None |
| Simulation behavior | **Changes** — Poisson lambda vs probability-based goal model; V23 goals/match may differ from current; possession computed differently |
| Test impact | Medium — league simulation results will change; need regression validation |
| Risk level | **MEDIUM** — simulation engine swap with different math model |
| Rollback plan | Revert to `matchSimulator.simulateQuick()` — full regression required |

### Option D — Narrow Integration Only

| Dimension | Impact |
|-----------|--------|
| Files affected | Depends on chosen endpoint — requires codebase exploration |
| API impact | Unknown |
| Persistence impact | Unknown |
| Frontend impact | None |
| Simulation behavior | Partially changed |
| Test impact | Unknown |
| Risk level | **MEDIUM** — unknown scope without more exploration |
| Rollback plan | Unknown |

### Option E — Change MatchEngine Port

| Dimension | Impact |
|-----------|--------|
| Files affected | `MatchEngine.java` interface, all implementations |
| API impact | Changes port contract |
| Persistence impact | None |
| Frontend impact | None |
| Simulation behavior | Depends on implementation |
| Test impact | High — all MatchEngine implementations affected |
| Risk level | **HIGH** — violates Phase 10C constraints |
| Rollback plan | Complex |

---

## Recommended Direction

**Phase 10C1 (immediate, lowest risk): Option A**

Refactor `LeagueSimulator.calculateTeamOVR()` to delegate to `TeamOverallCalculator`. Keep `MatchSimulator.simulateQuick()` unchanged. No simulation behavior change. No API/frontend/persistence changes.

```java
// In LeagueSimulator.java — replace private method:
private int calculateTeamOVR(CareerSave career, String sessionTeamId) {
    return TeamOverallCalculator.calculateFromSessionTeam(
            sessionTeamId,
            career.getTeamManager(),
            career.getPlayerManager()
    );
}
```

**Why Option A first:**
1. No behavior change — only OVR source changes
2. Eliminates duplication — LeagueSimulator no longer has its own OVR copy
3. Easy to validate — goals/possession/shots unchanged from existing behavior
4. Teams up `TeamOverallCalculator` in a real production path without touching simulation engine
5. 99 existing tests still pass

**Then Phase 10C2 (future, separate approval): Option C**

After Phase 10C1 stability is validated, evaluate replacing `MatchSimulator.simulateQuick()` with `MatchEngineImpl.simulateWithStrength()`. This requires:
- An adapter from `MatchResult` → `MatchResultData`
- Validation that V23 MatchEngineImpl metrics are acceptable for non-user division simulation
- Separate audit and approval before proceeding

**Do NOT attempt Option C in Phase 10C1 without explicit approval.**

---

## Hard Constraints (Non-Negotiable)

1. **Existing 99 tests must pass** — no test modifications allowed
2. **Do NOT change `MatchEngine` port interface**
3. **Do NOT change `MatchEngineImpl.simulate()` behavior**
4. **Do NOT change Redis/PostgreSQL schema**
5. **Do NOT change API/frontend**
6. **Do NOT replace simulation engine without explicit Phase 10C2 approval**
7. **If OVR values differ from previous LeagueSimulator method, document and pause**

---

## Decision Required

**Phase 10C1 APPROVED and IMPLEMENTED as Option A.**

Option A selected and committed as `05597ab`. LeagueSimulator.calculateTeamOVR() now delegates to TeamOverallCalculator. Legacy empty-squad behavior preserved. Phase 10C2 (V23 engine swap) pending separate decision.

- **Option A** — Refactor LeagueSimulator OVR to TeamOverallCalculator only (no simulation engine change)
- **Option B** — Same as A but add comment/cleanup of simulation engine path
- **Option C** — Full integration: TeamOverallCalculator + replace MatchSimulator with simulateWithStrength() (higher risk, needs justification)
- **Delay Phase 10C** — Keep current state, do not integrate yet

---

## Files Reference

| File | Role |
|------|------|
| `LeagueSimulator.java:53-67` | Private `calculateTeamOVR()` — **target for refactor** |
| `LeagueSimulator.java:35-43` | Calls `matchSimulator.simulateQuick()` — simulation trigger |
| `DefaultMatchSimulator.java:56-79` | `simulateQuick()` — current simulation engine for league |
| `MatchSimulator.java:33` | Interface: `simulateQuick(homeTeamId, awayTeamId, homeOvr, awayOvr)` |
| `MatchFixture.java:138-166` | `MatchResultData` — persisted result (homeGoals, awayGoals, possession, shots) |
| `MatchResult` (MatchEngineImpl) | V23 result with 8 fields including events and summary |
| `TeamOverallCalculator.java` | Phase 10B utility — ready for production integration |

---

## Validation for Phase 10C1 Implementation

```
mvn test -Dtest=TeamOverallCalculatorTest,MatchEngineImplStrengthSimulationTest,MatchEngineImplStyleSimulationTest,MatchQualityMetricsTest,V23SimulationQualityGateTest,MatchEngineImplRoleContributionTest,MatchEngineImplEventConsistencyTest,MatchEngineImplDeterminismTest,MatchEngineImplMetricsValidationTest,MatchEngineImplPoissonValidationTest,MatchQualityComputerTest,MatchEngineImplTest,DivisionTest
```

All 99 tests must pass after Phase 10C1 changes. If league simulation results change (they shouldn't in Option A), pause and document.

---

*This document is the authoritative Phase 10C audit. No code implementation should begin until this document is approved and the decision is recorded.*
