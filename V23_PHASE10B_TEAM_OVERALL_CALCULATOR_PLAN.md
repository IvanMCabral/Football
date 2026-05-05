# V23 Phase 10B — TeamOverallCalculator Plan

**Status:** COMPLETED — Phase 10B implemented as Option A+B hybrid
**Branch:** `mvp-1-performance-cleanup`
**Latest commit:** `8530935` (feat: add team overall calculator for real OVR computation)
**Created:** 2026-05-05

---

## Executive Summary

Phase 10A added `simulateWithStrength(Team, Team, int, int, long seed)` to `MatchEngineImpl`. Phase 10B targets creating a reusable OVR calculator that computes real team OVR from `SessionPlayer` data, which can feed into `simulateWithStrength()` in a future Phase 10C.

This audit inspects all existing OVR calculation code, assesses whether a new calculator is needed or if existing methods can be reused, and defines the path forward.

---

## Audit Findings

### 1. SessionPlayer.calculateOverall()

**Location:** `src/main/java/com/footballmanager/domain/model/entity/SessionPlayer.java:129-146`

```java
public Integer calculateOverall() {
    if (hasNullAttributes()) return 50;

    double overall = switch (position) {
        case "GK" -> defense * 0.40 + technique * 0.20 + mentality * 0.20 +
                   stamina * 0.10 + speed * 0.05 + attack * 0.05;
        case "DEF" -> defense * 0.35 + technique * 0.15 + mentality * 0.15 +
                   stamina * 0.15 + speed * 0.10 + attack * 0.10;
        case "MID" -> technique * 0.30 + stamina * 0.20 + mentality * 0.15 +
                   defense * 0.15 + speed * 0.10 + attack * 0.10;
        case "WINGER" -> speed * 0.30 + attack * 0.25 + technique * 0.20 +
                   stamina * 0.15 + mentality * 0.05 + defense * 0.05;
        case "ATT" -> attack * 0.40 + technique * 0.20 + speed * 0.15 +
                   mentality * 0.10 + stamina * 0.10 + defense * 0.05;
        default -> getAverageAttributes();
    };
    return (int) Math.round(overall);
}
```

- Position-weighted per-player OVR
- GK, DEF, MID, WINGER, ATT supported (default = simple average)
- Returns 50 if any attribute is null
- **No energy/form/injury modifiers** — raw attribute OVR only
- Used by `CareerTeamManager.calculateTeamOVR()` and `LeagueSimulator.calculateTeamOVR()`

### 2. CareerTeamManager.calculateTeamOVR()

**Location:** `src/main/java/com/footballmanager/domain/model/entity/career/CareerTeamManager.java:147-157`

```java
public int calculateTeamOVR(String sessionTeamId,
        java.util.function.Function<String, SessionPlayer> playerProvider) {
    List<String> playerIds = teamSquads().getOrDefault(sessionTeamId, Collections.emptyList());
    if (playerIds.isEmpty()) return 0;
    return (int) playerIds.stream()
        .mapToInt(pid -> {
            SessionPlayer p = playerProvider.apply(pid);
            return p != null ? p.calculateOverall() : 0;
        })
        .average()
        .orElse(0);
}
```

- Full squad average OVR
- Uses `playerProvider: Function<String, SessionPlayer>` — caller passes lookup
- **Returns 0 for empty squad** (not 50)
- Called by `CareerSave.assignTeamsToDivisions()` via lambda
- **No starting XI support** — uses all squad players
- **No energy/form/injury modifiers**

### 3. CareerTeamManager.getSquadPlayerIds()

**Location:** `src/main/java/com/footballmanager/domain/model/entity/career/CareerTeamManager.java:124-126`

```java
public List<String> getSquadPlayerIds(String sessionTeamId) {
    return teamSquads().getOrDefault(sessionTeamId, Collections.emptyList());
}
```

- Returns full squad as `List<String>` of player IDs
- Order-preserving (ArrayList)
- Used by `CareerSave.calculateTeamOVR()` and `LeagueSimulator.calculateTeamOVR()`

### 4. CareerPlayerManager.getSessionPlayer()

**Location:** `src/main/java/com/footballmanager/domain/model/entity/career/CareerPlayerManager.java:80-82`

```java
public SessionPlayer getSessionPlayer(String sessionPlayerId) {
    return sessionPlayers().get(sessionPlayerId);
}
```

- Simple map lookup
- Returns `null` if not found

### 5. CareerSave.getTeamStarting11()

**Location:** `src/main/java/com/footballmanager/domain/model/entity/CareerSave.java:52`

```java
public Map<String, List<String>> getTeamStarting11() { return teamStarting11; }
```

- Returns `Map<String, List<String>>` — sessionTeamId → list of sessionPlayerIds (starting XI)
- Starting XI IDs are stored and available
- **Not currently used by any OVR calculation**

### 6. LeagueSimulator.calculateTeamOVR() (DUPLICATE LOGIC)

**Location:** `src/main/java/com/footballmanager/application/service/simulation/LeagueSimulator.java:53-67`

```java
private int calculateTeamOVR(CareerSave career, String sessionTeamId) {
    List<String> squadPlayerIds = career.getTeamManager().getTeamSquads()
            .getOrDefault(sessionTeamId, List.of());
    if (squadPlayerIds.isEmpty()) return 50;

    int totalOVR = 0;
    int count = 0;
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

**Critical finding:** This is a copy of the same logic as `CareerTeamManager.calculateTeamOVR()` but:
- Returns 50 for empty squad (vs 0 in CareerTeamManager)
- Uses `career.getSessionPlayer()` directly vs `playerProvider.apply()`
- **Not reusable** — private method inside `LeagueSimulator`
- **DUPLICATE** — should be refactored to use `CareerTeamManager.calculateTeamOVR()`

### 7. TeamOVRQueryService.calculateTeamOVR(List<WorldPlayer>)

**Location:** `src/main/java/com/footballmanager/application/service/query/TeamOVRQueryService.java:73-85`

```java
public int calculateTeamOVR(List<WorldPlayer> players) {
    if (players == null || players.isEmpty()) return 50;
    int totalOVR = 0;
    int count = 0;
    for (WorldPlayer player : players) {
        int ovr = player.calculateOverall();
        totalOVR += ovr;
        count++;
    }
    return count > 0 ? totalOVR / count : 50;
}
```

- Uses `WorldPlayer.calculateOverall()` (different from `SessionPlayer.calculateOverall()`)
- **WorldView/WorldPlayer context** — not usable in career simulation flow
- Used for world team display, not career simulation

### 8. Where MatchEngine / MatchEngineImpl is Called

**MatchEngine port interface** (unchanged, only has `simulate(Team, Team)`):
```java
public interface MatchEngine {
    Mono<MatchResult> simulate(Team homeTeam, Team awayTeam);
}
```

**MatchEngineImpl** (`application/service/domain/MatchEngineImpl.java`):
- `simulate(Team, Team)` — normal production path, uses `calculateTeamOverall()` squad-size formula
- `simulate(Team, Team, long seed)` — seeded overload
- `simulateWithStyle(Team, Team, TeamStyle, TeamStyle, long seed)` — Phase 6B style overload
- `simulateWithStrength(Team, Team, int, int, long seed)` — Phase 10A explicit OVR overload (NOT in port interface)

**LeagueSimulator** (`application/service/simulation/LeagueSimulator.java:35-43`):
- Calls `MatchSimulator.simulateQuick(homeTeamId, awayTeamId, homeOvr, awayOvr)` — different simulation path
- Uses its own private `calculateTeamOVR()` to get OVRs from `CareerSave`
- **Does NOT use MatchEngineImpl or simulateWithStrength()**

**CareerSave** (`entity/CareerSave.java:143-158`):
- Has private `calculateTeamOVR(String teamId)` method used for division assignment
- Duplicates the same logic as `CareerTeamManager.calculateTeamOVR()`

### 9. Existing OVR Calculation Patterns

**Pattern A — CareerTeamManager.calculateTeamOVR(provider):**
```java
// Used by CareerSave for division assignment
teamManager.calculateTeamOVR(sessionTeamId, pid -> career.getSessionPlayer(pid))
```

**Pattern B — Inline private method (LeagueSimulator):**
```java
// Duplicated logic, not reusable
```

**Pattern C — CareerSave private method:**
```java
// Another duplicate, used internally
```

**Key finding:** OVR calculation logic is triplicated across:
1. `CareerTeamManager.calculateTeamOVR(Function)` — the canonical one
2. `LeagueSimulator.calculateTeamOVR(CareerSave, String)` — private duplicate
3. `CareerSave.calculateTeamOVR(String)` — private duplicate

### 10. What Would simulateWithStrength() Need?

For Phase 10C integration, a future service would compute:
```java
int homeOvr = teamOverallCalculator.calculate(sessionTeamId, pid -> career.getSessionPlayer(pid));
int awayOvr = teamOverallCalculator.calculate(awaySessionTeamId, pid -> career.getSessionPlayer(pid));
engine.simulateWithStrength(homeTeam, awayTeam, homeOvr, awayOvr, seed);
```

The calculator must:
- Accept `sessionTeamId` + player lookup function
- Return `int` in range [1, 100] (valid for `simulateWithStrength`)
- Work with both full squad and starting XI

---

## Option Comparison

### Option A — Reuse CareerTeamManager.calculateTeamOVR() (No New Calculator)

**Description:** No new class. Use existing `CareerTeamManager.calculateTeamOVR(sessionTeamId, playerProvider)` everywhere OVR is needed. Refactor `LeagueSimulator` and `CareerSave` private methods to use it.

| Dimension | Impact |
|-----------|--------|
| Files affected | `LeagueSimulator.java` (refactor private method → use CareerTeamManager), `CareerSave.java` (remove duplicate private method) |
| API impact | None |
| Persistence impact | None |
| Frontend impact | None |
| Simulation behavior | None — same OVR values, just from shared source |
| Test impact | None — same calculation logic, test assertions unchanged |
| Risk level | **LOWEST** — pure refactor, no new code |
| Rollback plan | Revert refactor — full regression not required |

**Assessment:** Cleanest option. `CareerTeamManager.calculateTeamOVR()` already exists and works. Only downside: does not support starting XI (only full squad). But this may be sufficient for Phase 10C.

---

### Option B — New TeamOverallCalculator Utility (application/service/domain)

**Description:** Create a new `TeamOverallCalculator` utility in `application/service/domain/` that wraps OVR computation and can handle:
- Full squad average
- Starting XI average
- Optional energy/form/injury modifiers (future)
- Fallback to squad-size formula when no player data available

```java
public final class TeamOverallCalculator {

    public static int calculateFromSessionTeam(
            String sessionTeamId,
            CareerTeamManager teamManager,
            CareerPlayerManager playerManager) {
        return calculateFromPlayerIds(
            teamManager.getSquadPlayerIds(sessionTeamId),
            pid -> playerManager.getSessionPlayer(pid)
        );
    }

    public static int calculateFromStartingXI(
            String sessionTeamId,
            CareerSave career,
            CareerPlayerManager playerManager) {
        List<String> startingIds = career.getTeamStarting11()
            .getOrDefault(sessionTeamId, List.of());
        return calculateFromPlayerIds(startingIds, pid -> playerManager.getSessionPlayer(pid));
    }

    private static int calculateFromPlayerIds(
            List<String> playerIds,
            Function<String, SessionPlayer> lookup) {
        if (playerIds.isEmpty()) return 70; // fallback to baseline
        int total = 0, count = 0;
        for (String pid : playerIds) {
            SessionPlayer p = lookup.apply(pid);
            if (p != null) {
                total += p.calculateOverall();
                count++;
            }
        }
        return count > 0 ? total / count : 70;
    }
}
```

| Dimension | Impact |
|-----------|--------|
| Files affected | New `TeamOverallCalculator.java` in `application/service/domain/` |
| API impact | None |
| Persistence impact | None |
| Frontend impact | None |
| Simulation behavior | None in Phase 10B — calculator only |
| Test impact | New unit tests for calculator; existing tests unchanged |
| Risk level | **LOW** — pure utility, no production simulation path change |
| Rollback plan | Delete new class — no migration needed |

**Assessment:** Good if starting XI support is needed now or in near future. Adds a reusable, testable component. Slightly more code than Option A.

---

### Option C — TeamOVRQueryService Extension

**Description:** Extend `TeamOVRQueryService` to also handle career OVR calculations.

| Dimension | Impact |
|-----------|--------|
| Files affected | `TeamOVRQueryService.java` — add career OVR method |
| API impact | None |
| Persistence impact | None |
| Frontend impact | None |
| Simulation behavior | None |
| Test impact | New tests for service method |
| Risk level | **LOW** — additive only |
| Rollback plan | Remove new method from service |

**Assessment:** Query service is for WorldView queries. Adding career simulation logic here couples query service to simulation flow — wrong layer. **Not recommended.**

---

### Option D — Integrate Directly Into LeagueSimulator Only

**Description:** Refactor `LeagueSimulator.calculateTeamOVR()` to call `CareerTeamManager.calculateTeamOVR()`. No new calculator. No changes to other services.

| Dimension | Impact |
|-----------|--------|
| Files affected | `LeagueSimulator.java` — refactor private method |
| API impact | None |
| Persistence impact | None |
| Frontend impact | None |
| Simulation behavior | None |
| Test impact | None |
| Risk level | **LOW** — refactor only |
| Rollback plan | Revert method — full regression not required |

**Assessment:** Narrow scope. Only fixes duplication in `LeagueSimulator`. `CareerSave` private method still duplicated. Sufficient if Phase 10C integration is limited to league simulation only.

---

### Option E — Change MatchEngineImpl.calculateTeamOverall()

**Description:** Change the formula inside `MatchEngineImpl.calculateTeamOverall()` to use real player OVR.

**Not allowed in Phase 10B.** This would change `simulate(Team, Team)` behavior and break all 89 existing tests. Violates hard constraints.

---

## Recommended Direction

**Prefer Option A + Option B hybrid — Create TeamOverallCalculator utility that delegates to CareerTeamManager.**

Rationale:
1. `CareerTeamManager.calculateTeamOVR()` is the canonical implementation — use it
2. Option A alone doesn't add starting XI support
3. `LeagueSimulator` and `CareerSave` private methods are duplicate logic that should be eliminated
4. A `TeamOverallCalculator` utility provides a single entry point for future OVR needs (starting XI, energy modifiers, etc.)
5. Lowest risk: pure addition, no existing behavior change

**Phase 10B implementation (if Option A+B approved):**

1. Create `TeamOverallCalculator` in `application/service/domain/`
2. Method: `calculateFromSessionTeam(String, CareerTeamManager, CareerPlayerManager)` → delegates to `CareerTeamManager.calculateTeamOVR()`
3. Method: `calculateFromStartingXI(String, CareerSave, CareerPlayerManager)` → uses `CareerSave.getTeamStarting11()` + `CareerPlayerManager.getSessionPlayer()`
4. Method: `calculateFallback(int squadSize)` → returns `70 + min(20, squadSize/2)` (same as `MatchEngineImpl.calculateTeamOverall()`)
5. Refactor `LeagueSimulator.calculateTeamOVR()` to use `TeamOverallCalculator` (eliminates duplicate)
6. Add unit tests for `TeamOverallCalculator`
7. **Do NOT change `MatchEngineImpl.simulate()` or `calculateTeamOverall()`**
8. **Do NOT change MatchEngine port interface**
9. **Do NOT add simulateWithStrength() calls in Phase 10B**

**Do NOT implement Phase 10C integration in Phase 10B.** Phase 10C is separate work.

**Phase 10C (future, separate approval):**
- Integrate `TeamOverallCalculator` into career/league service
- Call `simulateWithStrength()` with computed OVRs instead of normal `simulate()`
- Only change that specific simulation path, not all of MatchEngineImpl
- **Current state:** Phase 10B complete, TeamOverallCalculator ready for Phase 10C integration

---

## Hard Constraints (Non-Negotiable)

1. **Existing 89 tests must pass** — no test modifications allowed
2. **No repository injection into MatchEngineImpl** — violates separation of concerns
3. **No Redis schema changes**
4. **No API/frontend changes**
5. **No change to normal `simulate()` behavior** — `calculateTeamOverall()` unchanged
6. **No change to MatchEngine port interface**
7. **`simulateWithStrength()` is NOT called in Phase 10B** — Phase 10B only creates the calculator

---

## Decision Required

**Phase 10B APPROVED and IMPLEMENTED as Option A+B hybrid.**

Option A+B hybrid selected and committed as `8530935`. TeamOverallCalculator utility created. Phase 10C (integration) next.

- **Option A** — Reuse CareerTeamManager.calculateTeamOVR() only (refactor duplicates, no new class)
- **Option B** — New TeamOverallCalculator utility (starting XI support, fallback, future-proof)
- **Option A+B** — Hybrid: TeamOverallCalculator that delegates to CareerTeamManager + starting XI method
- **Option D** — Fix LeagueSimulator only (narrow scope, no new class)

---

## Files Reference

| File | Role |
|------|------|
| `SessionPlayer.java` | Player entity with `calculateOverall()` position-weighted OVR |
| `CareerTeamManager.java:147` | `calculateTeamOVR(sessionTeamId, playerProvider)` — canonical squad OVR |
| `CareerTeamManager.java:124` | `getSquadPlayerIds(sessionTeamId)` — squad player ID list |
| `CareerPlayerManager.java:80` | `getSessionPlayer(sessionPlayerId)` — player lookup |
| `CareerSave.java:52` | `getTeamStarting11()` — starting XI map |
| `LeagueSimulator.java:53` | Private `calculateTeamOVR()` — **duplicate, to be refactored** |
| `CareerSave.java:143` | Private `calculateTeamOVR(String)` — **duplicate, to be removed** |
| `TeamOVRQueryService.java:73` | `calculateTeamOVR(List<WorldPlayer>)` — world view only, not for career |
| `MatchEngine.java` | Port interface — only `simulate(Team, Team)`, unchanged |
| `MatchEngineImpl.java:60` | `simulateWithStrength()` — explicit OVR overload, Phase 10A complete |

---

## Validation for Phase 10B Implementation

```
mvn test -Dtest=MatchEngineImplStrengthSimulationTest,MatchEngineImplStyleSimulationTest,MatchQualityMetricsTest,V23SimulationQualityGateTest,MatchEngineImplRoleContributionTest,MatchEngineImplEventConsistencyTest,MatchEngineImplDeterminismTest,MatchEngineImplMetricsValidationTest,MatchEngineImplPoissonValidationTest,MatchQualityComputerTest,MatchEngineImplTest,DivisionTest
```

All 89 tests must pass. New calculator unit tests are additive only.

---

*This document is the authoritative Phase 10B audit. No code implementation should begin until this document is approved and the decision is recorded.*
