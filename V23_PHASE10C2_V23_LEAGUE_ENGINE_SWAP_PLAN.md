# V23 Phase 10C2 — V23 Engine Swap for League Simulation

**Status:** AUDIT — No code implementation yet
**Branch:** `mvp-1-performance-cleanup`
**Latest commit:** `a5c0da4` (docs: align V23 docs with Phase 10C1 doc commit)
**Created:** 2026-05-05

---

## Executive Summary

Phase 10C1 integrated `TeamOverallCalculator` into `LeagueSimulator` for OVR computation without changing the simulation engine. Phase 10C2 evaluates replacing `DefaultMatchSimulator.simulateQuick()` with `MatchEngineImpl.simulateWithStrength()` using those OVRs.

This audit inspects all affected components, compares options, and recommends a direction.

---

## Audit Findings

### 1. LeagueSimulator — Current Flow

**Location:** `src/main/java/com/footballmanager/application/service/simulation/LeagueSimulator.java`

```java
public void simulateLeagueRound(CareerSave career, int round) {
    // Phase 10C1: OVR now comes from TeamOverallCalculator
    int homeOvr = calculateTeamOVR(career, fixture.getHomeTeamId()); // delegates to TeamOverallCalculator
    int awayOvr = calculateTeamOVR(career, fixture.getAwayTeamId());

    // Uses domain service MatchSimulator (NOT MatchEngineImpl)
    MatchSimulator.MatchResult result = matchSimulator.simulateQuick(
            fixture.getHomeTeamId(), fixture.getAwayTeamId(), homeOvr, awayOvr);

    // Maps to 6-field MatchResultData — goals, possession (50/50), shots (5/5)
    MatchFixture.MatchResultData resultData = new MatchFixture.MatchResultData(
            result.homeGoals(), result.awayGoals(), 50, 50, 5, 5);
    tournamentState.recordMatchResult(fixture.getMatchId(), resultData);
}
```

**Called by:** `MatchSimulationOrchestrator` (application service, async via Mono)
**Scope:** AI vs AI matches in all non-user divisions
**Key:** Only string team IDs and computed OVRs are used — no Team aggregate

### 2. DefaultMatchSimulator — What LeagueSimulator Uses

**Location:** `src/main/java/com/footballmanager/domain/service/DefaultMatchSimulator.java:56-79`

```java
public MatchResult simulateQuick(String homeTeamId, String awayTeamId, int homeOvr, int awayOvr) {
    int ovrDiff = homeOvr - awayOvr;
    // OVR diff → goal probability
    double homeGoalProb = 0.8 + (ovrDiff * 0.02);
    double awayGoalProb = 0.8 - (ovrDiff * 0.02);
    homeGoalProb = Math.max(0.3, Math.min(1.5, homeGoalProb));
    awayGoalProb = Math.max(0.3, Math.min(1.5, awayGoalProb));

    int homeGoals = calculateGoals(homeGoalProb);  // 5 iterations, random chance per opportunity
    int awayGoals = calculateGoals(awayGoalProb);

    // Small noise to break draws
    if (homeGoals == awayGoals && random.nextDouble() < 0.1) {
        if (random.nextBoolean()) homeGoals++; else awayGoals++;
    }
    return new MatchResult(homeGoals, awayGoals); // 2-field record
}
```

**Model:** Probability-based goal generation (not Poisson lambda)
**Possession:** Hardcoded 50/50 in `MatchResultData` construction
**Shots:** Hardcoded 5/5 in `MatchResultData` construction
**Events:** None generated
**Random:** `new Random()` per simulation (non-deterministic)

### 3. MatchSimulator Interface and Implementations

**Location:** `src/main/java/com/footballmanager/domain/service/MatchSimulator.java`

```java
public interface MatchSimulator {
    MatchState simulateReal(MatchState state, int toMinute);
    MatchResult simulateQuick(String homeTeamId, String awayTeamId, int homeOvr, int awayOvr);
    record MatchResult(int homeGoals, int awayGoals) {}
}
```

**Implementations:**
- `DefaultMatchSimulator` — probability-based, no events
- Likely others in the domain service package

**Port vs Implementation distinction:**
- `MatchEngine` (port in `domain/ports/out/match/`) — only has `simulate(Team, Team)` returning `Mono<MatchResult>`
- `MatchSimulator` (domain service interface) — a different abstraction used by LeagueSimulator
- `MatchEngineImpl` (application service) — implements `MatchEngine` port AND has extra methods like `simulateWithStrength()`

**Critical:** `simulateWithStrength()` is NOT on the `MatchEngine` port — it is a direct method on `MatchEngineImpl`. Phase 10C2 does NOT require changing the `MatchEngine` port.

### 4. MatchEngineImpl.simulateWithStrength()

**Location:** `src/main/java/com/footballmanager/application/service/domain/MatchEngineImpl.java:60-69`

```java
public Mono<MatchResult> simulateWithStrength(
        Team homeTeam,
        Team awayTeam,
        int homeOvr,
        int awayOvr,
        long seed) {
    return Mono.fromCallable(() ->
            performSimulationWithStrength(homeTeam, awayTeam, new Random(seed), homeOvr, awayOvr))
            .subscribeOn(Schedulers.boundedElastic());
}
```

**Returns:** `Mono<MatchResult>` (reactive, async)
**Requires:** `Team` aggregate objects (homeTeam, awayTeam) with valid names/squad sizes
**Invalid OVR handling:** Falls back to `calculateTeamOverall(team)` if OVR outside [1, 100]
**Seed:** `new Random(seed)` — deterministic if seed provided
**Model:** V23 Poisson goal model with computed possession and shots

**performSimulationWithStrength** (lines 122-160):
- Computes possession from OVR diff using `calculatePossession()`
- Computes lambdas via `MatchQualityComputer.computeLambdas()`
- Poisson-sampled goals from lambdas
- Poisson-sampled total shots from `(homeLambda+awayLambda)/0.20`
- Shot split by possession
- Goal floor enforced: `max(homeGoals, homeShots)`
- Events generated via `generateEvents()` (goal events with scorer info)
- Summary generated via `generateSummary()`

### 5. Team Aggregate — Construction Requirements

**Location:** `src/main/java/com/footballmanager/domain/model/aggregate/Team.java`

**Required fields:**
- `TeamId id` (non-null)
- `UserId managerId` (non-null)
- `name` (validated: 3-100 chars)
- `country` (non-null)
- `budget` (BigDecimal, non-negative)
- `formation` (non-null, e.g. "4-3-3")
- `Set<PlayerId> squadPlayerIds` (for `getSquadSize()`)

**Problem:** `Team` is a PostgreSQL-persisted aggregate. `LeagueSimulator` works with `CareerSave` which has `SessionTeam` objects (Redis-only, not Team). To use `simulateWithStrength()`, we need valid `Team` instances.

**Possible constructions:**
1. **Build minimal in-memory Team** — no DB repository needed, but requires: `TeamId`, `UserId`, `name`, `country`, `budget`, `formation`, `squadSize` (for `getSquadSize()` used by `calculateTeamOverall` fallback)
2. **Convert SessionTeam → Team** — would need a repository to load/convert, adds coupling

**Minimum viable Team for simulateWithStrength:**
```java
Team homeTeam = Team.create(
    TeamId.of(sessionTeamId),           // use sessionTeamId as TeamId
    UserId.of("system"),                // AI teams have no user
    sessionTeamName,                    // from CareerSave or SessionTeam
    "AI",                               // placeholder country
    BigDecimal.ZERO,                    // budget irrelevant for simulation
    Formation.of("4-3-3")               // default formation
);
// squadPlayerIds is empty → calculateTeamOverall returns 70 (base)
```

**Risk:** Empty squad means `calculateTeamOverall()` returns 70 regardless of `homeOvr/awayOvr` passed to `simulateWithStrength()`. But since we pass explicit OVRs (validated to [1, 100]), this fallback won't be triggered — the passed OVRs are used directly.

**Validated:** `isValidOvr(ovr)` check at line 162-163 ensures explicit OVRs [1, 100] bypass the fallback. Only invalid OVRs trigger the fallback.

### 6. MatchEngine Port — Unchanged Constraint

**Location:** `src/main/java/com/footballmanager/domain/ports/out/match/MatchEngine.java`

```java
public interface MatchEngine {
    Mono<MatchResult> simulate(Team homeTeam, Team awayTeam);
}
```

**Constraint:** Phase 10C2 does NOT require modifying the `MatchEngine` port. `simulateWithStrength()` is a direct method on `MatchEngineImpl`, not part of the port interface. The port remains unchanged.

### 7. MatchResult → MatchResultData Adapter Requirements

**MatchResult** (entity, 8 fields):
```java
int homeGoals, awayGoals
int homePossession, awayPossession
int homeShots, awayShots
List<MatchEvent> events
String summary
```

**MatchFixture.MatchResultData** (value object, 6 fields):
```java
int homeGoals, awayGoals
int homePossession, awayPossession
int homeShots, awayShots
```

**Adapter (trivial):**
```java
MatchFixture.MatchResultData data = new MatchFixture.MatchResultData(
    result.getHomeGoals(), result.getAwayGoals(),
    result.getHomePossession(), result.getAwayPossession(),
    result.getHomeShots(), result.getAwayShots()
);
```

**Events and summary are discarded** — they are not stored in `MatchResultData`. This is safe for league simulation since:
- Events are only needed for user-facing match replays
- Summary is only needed for user-facing match commentary
- AI vs AI matches don't render events to users

### 8. Events/Summary Discard Safety

**Confirmed safe:** AI division simulation results are recorded to `TournamentState` as `MatchResultData` (goals, possession, shots only). Events generated by V23 engine are for user match replay, not for league standings. No events are persisted for non-user matches currently either (DefaultMatchSimulator doesn't generate events at all).

### 9. Mono<MatchResult> — Blocking in Synchronous Path

**Current LeagueSimulator:** Synchronous, void method
**MatchEngineImpl.simulateWithStrength():** Returns `Mono<MatchResult>` (reactive)

**To call from synchronous LeagueSimulator:**
```java
MatchResult result = matchEngine.simulateWithStrength(homeTeam, awayTeam, homeOvr, awayOvr, seed)
    .block(Duration.ofSeconds(5));
```

**Safety justification:**
- `LeagueSimulator.simulateLeagueRound()` is called from `MatchSimulationOrchestrator.processResultsInternal()` which already uses `.block()` for Redis operations (lines 110, 157)
- League simulation is a batch of AI vs AI matches — blocking is acceptable in this synchronous career processing path
- `Schedulers.boundedElastic()` in `simulateWithStrength()` means the blocking call has a dedicated thread pool
- For a typical league with ~10 fixtures per round, blocking each sequentially adds negligible latency vs. the overall round processing time

**Alternative (async):** Could refactor to return `Mono<Void>` and chain, but this is a larger architectural change not required by Phase 10C2.

### 10. Tests Around LeagueSimulator / Career Season Advancement

**Finding:** No tests exist specifically for `LeagueSimulator` or `simulateLeagueRound()`.

```bash
grep -r "LeagueSimulator\|simulateLeagueRound" src/test
# 0 results
```

**Existing tests that validate the broader system:**
- `DivisionTest` — validates division standings/promotion/relegation logic
- `CareerSaveTest` (if exists) — validates career state transitions
- All 99 V23 engine tests validate `MatchEngineImpl`, `TeamOverallCalculator`, etc.

**Risk:** If league simulation behavior changes (goals/possession/shots differ), there is no direct unit test for LeagueSimulator. The change would manifest in integration tests that exercise career round advancement.

**Mitigation:** Option C behavior change can be validated by running the full test suite (99 tests) plus manual inspection of career simulation output.

### 11. Behavior Comparison — DefaultMatchSimulator vs V23 Engine

| Dimension | DefaultMatchSimulator | V23 MatchEngineImpl |
|-----------|----------------------|---------------------|
| Goals model | Probability-based (5 iterations) | Poisson lambda |
| Possession | Hardcoded 50/50 | Computed from OVR diff |
| Shots | Hardcoded 5/5 | Computed from total lambda/0.20 |
| Events | None | Goal events with scorer |
| Summary | None | Generated string |
| Random | `new Random()` each call | `new Random(seed)` if seed provided |
| OVR role | Goal probability modifier | Lambda + possession input |
| Goals range | 0-5+ (cap from 5 iterations) | 0-10+ (Poisson) |
| Draw breaking | 10% noise | None (Poisson natural distribution) |

**What changes for AI division simulation:**
- Goals will differ: V23 Poisson vs probability-based
- Possession: computed (e.g., 60/40) vs hardcoded 50/50
- Shots: computed (e.g., 12-15 range) vs hardcoded 5/5
- Events: generated but discarded in adapter
- Summary: generated but discarded in adapter

**Is this acceptable?** For AI vs AI matches, computed possession and realistic shot counts are arguably more accurate than hardcoded 50/50 and 5/5. However, league standings, promotion/relegation will change because goals change.

### 12. Impact on Standings, Fixtures, Promotion/Relegation

- **Standings:** Will change because goals change. User's division may see different leaders.
- **Fixtures:** Unchanged — fixture generation doesn't depend on simulation results
- **Promotion/Relegation:** Will differ because standings differ. Points/goals change.
- **User-facing expectations:** User only directly sees their own match results. AI division results affect their league table position indirectly (promotion/relegation boundaries).

**Note:** User's own matches are NOT simulated by LeagueSimulator — they use the real-time V23 engine path. Only AI vs AI non-user division matches are affected.

---

## Options Comparison

### Option A — Do Nothing

Keep `DefaultMatchSimulator.simulateQuick()` as the league simulation engine. V23 engine remains unused for AI matches.

| Dimension | Impact |
|-----------|--------|
| Files affected | None |
| API impact | None |
| Persistence impact | None |
| Frontend impact | None |
| Simulation behavior | None — current model unchanged |
| Test impact | None |
| Architecture risk | **NONE** |
| Rollback plan | N/A — no changes |

**Assessment:** Lowest risk, no value added. V23 engine investment (Phase 10A/10B) remains idle for AI matches.

---

### Option B — Add Adapter Without Changing Engine

Create `MatchResultAdapter` utility for future use. No behavior change.

```java
public static MatchFixture.MatchResultData fromMatchResult(MatchResult result) {
    return new MatchFixture.MatchResultData(
        result.getHomeGoals(), result.getAwayGoals(),
        result.getHomePossession(), result.getAwayPossession(),
        result.getHomeShots(), result.getAwayShots()
    );
}
```

| Dimension | Impact |
|-----------|--------|
| Files affected | New `MatchResultAdapter.java` in application/service/simulation/ |
| API impact | None |
| Persistence impact | None |
| Frontend impact | None |
| Simulation behavior | None |
| Test impact | None |
| Architecture risk | **LOW** — pure utility, no production path change |
| Rollback plan | Delete adapter class — no migration |

**Assessment:** Low value but also low risk. Useful if Option C is planned, but could be done later.

---

### Option C — Replace DefaultMatchSimulator with MatchEngineImpl.simulateWithStrength()

**Core implementation:**

1. Add `MatchEngineImpl` dependency to `LeagueSimulator`:
   ```java
   private final MatchEngineImpl matchEngine; // application service
   ```

2. Replace `matchSimulator.simulateQuick()` with `matchEngine.simulateWithStrength()`:
   ```java
   // Build minimal Team objects (no DB, no repository)
   Team homeTeam = buildMinimalTeam(fixture.getHomeTeamId(), sessionTeamName, homeOvr);
   Team awayTeam = buildMinimalTeam(fixture.getAwayTeamId(), sessionTeamName, awayOvr);
   
   // Block on reactive — safe in this synchronous path
   MatchResult result = matchEngine.simulateWithStrength(
       homeTeam, awayTeam, homeOvr, awayOvr, seed)
       .block(Duration.ofSeconds(5));
   
   // Adapter to MatchResultData (events/summary discarded)
   MatchFixture.MatchResultData data = MatchResultAdapter.toData(result);
   ```

3. Add seed derivation (deterministic per fixture):
   ```java
   long seed = fixture.getMatchId().hashCode(); // deterministic, reproducible
   ```

4. Team building (minimal, no persistence):
   ```java
   Team buildMinimalTeam(String sessionTeamId, String name, int ovr) {
       return Team.create(
           TeamId.of(sessionTeamId),
           UserId.of("system"),
           name != null ? name : "Team " + sessionTeamId,
           "AI",
           BigDecimal.ZERO,
           Formation.of("4-3-3")
       );
   }
   ```

| Dimension | Impact |
|-----------|--------|
| Files affected | `LeagueSimulator.java` — add `MatchEngineImpl` DI, replace `matchSimulator.simulateQuick()` call, add `buildMinimalTeam()`, add `MatchResultAdapter` |
| API impact | None |
| Persistence impact | None |
| Frontend impact | None |
| Simulation behavior | **Changes** — Poisson goals, computed possession/shots, events generated (discarded) |
| Test impact | 99 tests must pass; no direct LeagueSimulator tests to fail; possible integration impact if career season validation exists |
| Architecture risk | **MEDIUM** — `LeagueSimulator` (application service) depends on `MatchEngineImpl` (application service) — same layer, acceptable; not coupling domain service to application service |
| Rollback plan | Revert `LeagueSimulator.java` to use `matchSimulator.simulateQuick()` — full regression required |

**Assessment:** Medium risk, highest value. Uses V23 engine investment for AI matches. Behavior change is significant (goals, possession, shots). Requires validation that new simulation metrics are acceptable for AI division play.

---

### Option D — Feature Flag / Strategy Switch

Default remains `DefaultMatchSimulator`. Optional V23 engine for league simulation controlled by a flag.

```java
private boolean useV23Engine = false; // default off

// In simulateLeagueRound:
if (useV23Engine) {
    // V23 path
} else {
    // Existing DefaultMatchSimulator path
}
```

| Dimension | Impact |
|-----------|--------|
| Files affected | `LeagueSimulator.java` — add flag, if/else branch |
| API impact | None (flag is internal) |
| Persistence impact | None |
| Frontend impact | None |
| Simulation behavior | Choice-based — both paths must be maintained |
| Test impact | Both paths must work; 99 tests unchanged |
| Architecture risk | **LOW-MEDIUM** — conditional logic, two paths to maintain |
| Rollback plan | Flip flag or revert flag change |

**Assessment:** Safer rollout. The old engine remains the default so behavior doesn't change until explicitly enabled. However, adds complexity and two simulation paths to maintain.

---

### Option E — Change MatchSimulator Implementation to Delegate to V23 Engine

Keeps `LeagueSimulator` calling `matchSimulator.simulateQuick()` but changes the implementation.

```java
// In DefaultMatchSimulator (or new implementation):
public MatchResult simulateQuick(...) {
    // Call MatchEngineImpl.simulateWithStrength() internally
    // But MatchEngineImpl is application service — domain shouldn't depend on it
}
```

| Dimension | Impact |
|-----------|--------|
| Files affected | `DefaultMatchSimulator.java` or new `MatchSimulator` impl |
| API impact | None — MatchSimulator interface unchanged |
| Persistence impact | None |
| Frontend impact | None |
| Simulation behavior | Changes (same as Option C) |
| Test impact | 99 tests unchanged; interface contract same |
| Architecture risk | **HIGH** — domain service (`MatchSimulator`) depends on application service (`MatchEngineImpl`) — wrong dependency direction |

**Assessment:** Wrong architecture. Domain services should not depend on application services. LeagueSimulator directly using `MatchEngineImpl` (Option C) is cleaner than coupling `DefaultMatchSimulator` to `MatchEngineImpl`.

---

## Recommended Direction

**Prefer Option D — Feature Flag / Strategy Switch** if the goal is safe rollout with ability to toggle.

**Or Option C — Direct Replacement** if the team explicitly accepts changed AI division simulation behavior (more realistic possession/shots, Poisson goals, different standings).

**Reasoning:**
1. Option A leaves V23 engine investment idle — low value
2. Option B is just scaffolding — low immediate value
3. Option C is the highest-value path but changes AI simulation behavior significantly
4. Option D adds safety via flag but adds two-paths-to-maintain complexity
5. Option E is architecturally wrong — domain shouldn't depend on application service

**If choosing Option C or D:**
- Add `MatchResultAdapter.toData(MatchResult)` utility
- Build minimal `Team` objects in `LeagueSimulator` (no DB, no repository)
- Use `fixture.getMatchId().hashCode()` as seed for reproducibility
- Block on `Mono<MatchResult>` — safe in synchronous career processing path
- Events and summary are safely discarded in the adapter

**Do NOT implement Option E.** The domain/application layer dependency direction is wrong.

**Do NOT change the MatchEngine port** — `simulateWithStrength()` is a direct method, not part of the port.

---

## Hard Constraints (Non-Negotiable)

1. **Existing 99 tests must pass** — no test modifications allowed
2. **No API/frontend changes**
3. **No Redis/PostgreSQL schema changes**
4. **No V32/V33 work**
5. **No MatchEngine port change**
6. **If blocking Mono, justify safety** — blocking is acceptable in synchronous LeagueSimulator path (already uses `.block()` for Redis operations in `MatchSimulationOrchestrator`)
7. **Team objects must be constructed in-memory** — no DB/repository needed for AI team simulation
8. **Events/summary are discarded** — not persisted for AI matches

---

## Decision Required

**Phase 10C2 APPROVAL NEEDED.**

- **Option A** — Do nothing, keep DefaultMatchSimulator
- **Option B** — Add adapter utility for future use, no behavior change
- **Option C** — Replace with MatchEngineImpl.simulateWithStrength() directly (medium risk, behavior changes)
- **Option D** — Feature flag switch, DefaultMatchSimulator remains default (lowest risk of C/D)
- **Option E** — Change MatchSimulator impl to delegate to V23 engine (architecturally wrong)

---

## Files Reference

| File | Role |
|------|------|
| `LeagueSimulator.java:27-51` | `simulateLeagueRound()` — target method to modify |
| `LeagueSimulator.java:54-66` | `calculateTeamOVR()` — Phase 10C1, already uses TeamOverallCalculator |
| `DefaultMatchSimulator.java:56-79` | `simulateQuick()` — current league engine (replaced in Option C/D) |
| `MatchSimulator.java:33` | Interface: `simulateQuick(homeTeamId, awayTeamId, homeOvr, awayOvr)` |
| `MatchEngineImpl.java:60-69` | `simulateWithStrength(Team, Team, int, int, long)` — target engine |
| `MatchEngine.java` (port) | Port interface — unchanged |
| `MatchResult.java` (entity) | 8-field result with events/summary |
| `MatchFixture.java:138-169` | `MatchResultData` — 6-field persisted result |
| `Team.java:13` | Aggregate requiring TeamId, UserId, name, country, budget, formation, squad |
| `MatchSimulationOrchestrator.java` | Caller — already uses `.block()` for sync career processing |

---

## Validation for Future Implementation

```bash
mvn test -Dtest=TeamOverallCalculatorTest,MatchEngineImplStrengthSimulationTest,MatchEngineImplStyleSimulationTest,MatchQualityMetricsTest,V23SimulationQualityGateTest,MatchEngineImplRoleContributionTest,MatchEngineImplEventConsistencyTest,MatchEngineImplDeterminismTest,MatchEngineImplMetricsValidationTest,MatchEngineImplPoissonValidationTest,MatchQualityComputerTest,MatchEngineImplTest,DivisionTest
```

All 99 tests must pass. If league simulation results change (they will in Option C/D), document the delta and validate that computed possession/shots are within acceptable ranges.

---

*This document is the authoritative Phase 10C2 audit. No code implementation should begin until this document is approved and the decision is recorded.*