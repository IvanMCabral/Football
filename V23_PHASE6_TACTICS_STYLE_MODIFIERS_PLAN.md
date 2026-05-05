# V23 Phase 6: Tactics/Style Modifiers — Audit & Implementation Plan

**Status:** PHASE 6A COMPLETED -- Phase 6B pending (simulation integration undecided)
**Branch:** `mvp-1-performance-cleanup`
**Created:** 2026-05-05
**Phase 6A implemented:** 2026-05-05 (`abbcb53`)

---

## 1. Audit: Current State

### 1.1 Team Model

`src/main/java/com/footballmanager/domain/model/aggregate/Team.java`

| Field | Type | Notes |
|-------|------|-------|
| `id` | `TeamId` | identity |
| `managerId` | `UserId` | owner |
| `name` | `String` | |
| `country` | `String` | |
| `budget` | `BigDecimal` | mutable |
| `formation` | `Formation` | mutable via `updateFormation()` |
| `squadPlayerIds` | `Set<PlayerId>` | |
| `createdAt` | `Instant` | immutable |
| `updatedAt` | `Instant` | |

**No style/tactical field exists.** `Team` has `formation` but no tactical style enum or attribute.

### 1.2 Formation Model

`src/main/java/com/footballmanager/domain/model/valueobject/Formation.java`

```java
public enum Formation {
    FORMATION_4_4_2("4-4-2", 4, 4, 2),
    FORMATION_4_3_3("4-3-3", 4, 3, 3),
    FORMATION_4_2_3_1("4-2-3-1", 4, 5, 1),
    FORMATION_3_5_2("3-5-2", 3, 5, 2),
    FORMATION_5_3_2("5-3-2", 5, 3, 2),
    FORMATION_4_1_4_1("4-1-4-1", 4, 5, 1),
    FORMATION_3_4_3("3-4-3", 3, 4, 3);
}
```

**Formation does NOT encode tactical style directly.** A formation like `4-3-3` could be used for attacking or defensive play depending on team instructions. Deriving style from formation is unreliable without additional semantic knowledge.

### 1.3 Team Entities (Career/World)

- **`SessionTeam`** — lives in Redis only. Has `formation` as `String`. **No style field.**
- **`WorldTeam`** — in WorldSnapshot. Has `baseFormation` as `String`. **No style field.**

### 1.4 Where Tactical Style Could Exist

| Entity | Has style field? | Mutable? |
|--------|-------------------|---------|
| `Team.aggregate.Team` | No | — |
| `SessionTeam` | No | — |
| `WorldTeam` | No | — |
| `CareerSave` | No | — |
| `Division` | No | — |

**No tactical style field exists anywhere in the domain model.**

### 1.5 MatchQualityComputer — Lambda Flow

```java
public static MatchQualityLambdas computeLambdas(int homeOvr, int awayOvr) {
    int ovrDiff = homeOvr - awayOvr;

    double baseTotalLambda = 2.60;
    double imbalanceBoost = Math.abs(ovrDiff) * 0.012;
    double totalLambda = clamp(baseTotalLambda + imbalanceBoost, 2.3, 3.05);

    double homeBaseShare = 0.52;
    double strengthShift = ovrDiff / 220.0;
    double homeShare = clamp(homeBaseShare + strengthShift, 0.25, 0.75);

    double homeLambda = totalLambda * homeShare;
    double awayLambda = totalLambda * (1.0 - homeShare);

    return new MatchQualityLambdas(homeLambda, awayLambda, totalLambda, homeShare);
}
```

**Entry points where style could be injected:**

1. `MatchQualityComputer.computeLambdas(int, int)` — add overloaded `computeLambdas(int homeOvr, int awayOvr, TeamStyle homeStyle, TeamStyle awayStyle)`
2. `MatchEngineImpl.performSimulation()` calls `computeLambdas()` at line 41-42 — could pass style from team if available

### 1.6 MatchEngineImpl — Current Call Flow

```
performSimulation(Team home, Team away, Random random)
  â calculateTeamOverall(home/away)    // OVR: 70 + squadSize/2, capped at +20
  â calculatePossession()              // OVR-based, Â±10 variance, clamped to 30-70
  â MatchQualityComputer.computeLambdas(homeOverall, awayOverall)
  â poissonSample(homeLambda) / poissonSample(awayLambda)
  â generateEvents()
  â MatchResult.of(...)
```

**Style could be added:**
- At lambda computation time (if style available from Team)
- As a separate parameter to `computeLambdas()` overload
- NOT in `calculatePossession()` directly (possession already heuristic-based)

### 1.7 API/DTOs for Team Creation/Update

- `CareerTeamController` handles `/api/v1/career/teams/*` endpoints
- Team creation: `cloneTeamToSession()`, `createRandomTeam()` — formation is set at creation time
- No API DTO for setting team tactical style (because it doesn't exist)
- `TeamCommandUseCaseImpl` handles business logic for team operations

### 1.8 Formation â Style Derivation (Risk Analysis)

| Formation | Attackers | Could imply | Risk |
|-----------|-----------|-------------|------|
| `4-3-3` | 3 | ATTACKING | HIGH — 4-3-3 used for both attacking and defensive setups |
| `4-4-2` | 2 | DEFENSIVE/BALANCED | HIGH — 4-4-2 is balanced |
| `5-3-2` | 2 | DEFENSIVE | MEDIUM — 5 atb is usually defensive |
| `3-5-2` | 2 | BALANCED/ATTACKING | MEDIUM — 3 atb can be attacking |
| `4-2-3-1` | 1 | DEFENSIVE | MEDIUM — 1 striker, 2 midfielders |

**Conclusion:** Deriving style from formation is unreliable. Formation defines structure, not intent. **Option C (derive from formation) is not recommended.**

### 1.9 Team Persistence/Serialization Impact

| Entity | Persistence | Style Change Impact |
|--------|-------------|---------------------|
| `Team` | PostgreSQL `teams_table` | Adding field requires schema migration |
| `SessionTeam` | Redis (serialized JSON) | Adding field is additive, backward-compatible if nullable |
| `WorldTeam` | WorldSnapshot (in-memory, Redis-cached) | Adding field requires snapshot rebuild |

**Key finding:** `SessionTeam` is in Redis (not PostgreSQL). Adding a style field there is lower risk than adding to `Team`.

---

## 2. Options Analysis

### Option A — Style-Aware computeLambdas Overload (Test/Analytics Only)

**Description:** Add `computeLambdas(int homeOvr, int awayOvr, TeamStyle homeStyle, TeamStyle awayStyle)` as a new static overload in `MatchQualityComputer`. `TeamStyle` is a new enum in `application/service/domain/` or `domain/model/valueobject/`. `MatchEngineImpl` remains unchanged.

**Files affected:**
- New: `TeamStyle.java` — enum (BALANCED, ATTACKING, DEFENSIVE, COUNTER, POSSESSION)
- `MatchQualityComputer.java` — new overloaded `computeLambdas()` method
- `MatchQualityComputerTest.java` — tests for style-aware computation
- `V23SimulationQualityGateTest.java` — optional style validation tests

**API impact:** None (internal utility only)

**Persistence impact:** None

**Frontend impact:** None

**Simulation behavior impact:** None in Phase 6A — `MatchEngineImpl` does not call the new overload

**Tests required:**
- Unit test: `computeLambdas(75, 75, BALANCED, BALANCED)` equals baseline `computeLambdas(75, 75)`
- Unit test: style modifiers produce measurable but small lambda changes
- Unit test: totalLambda clamps to [2.3, 3.05] for all style combinations
- Unit test: homeShare clamps to [0.25, 0.75] for all style combinations
- Regression test: existing computeLambdas behavior unchanged

**Risk level:** **LOW** — additive only, existing code path untouched

**Rollback plan:**
```bash
git rm --cached src/main/java/com/footballmanager/application/service/domain/TeamStyle.java
git checkout -- src/main/java/com/footballmanager/application/service/domain/MatchQualityComputer.java
```

---

### Option B — Add TeamStyle Field to Team Aggregate

**Description:** Add `TeamStyle style` field to `Team` aggregate with getter/setter. Add to `SessionTeam` in Redis. `MatchQualityComputer` and `MatchEngineImpl` updated to consume style.

**Files affected:**
- `Team.java` — add `style` field and `updateStyle()` method
- `SessionTeam.java` — add `style` field
- `CareerTeamMapper` / `SessionEntityMapper` — map style field
- `MatchQualityComputer.java` — new style-aware overload
- `MatchEngineImpl.java` — pass team style to `computeLambdas()`
- `CareerTeamController.java` or `TeamCommandUseCase` — add `UpdateTeamStyleRequest` DTO
- Database migration if Team is in PostgreSQL

**API impact:** New DTO for style update, new field in team responses

**Persistence impact:** **MEDIUM** — `Team` is in PostgreSQL, schema change required; `SessionTeam` in Redis is lower risk

**Frontend impact:** Optional — can display/edit team style when available

**Simulation behavior impact:** YES — `MatchEngineImpl` now uses style to adjust lambdas

**Tests required:**
- All Option A tests
- Unit test: style persists through reconstruct
- Unit test: style round-trips through Redis
- Regression test: existing simulation unchanged (when style is BALANCED)

**Risk level:** **MEDIUM** — domain model change + persistence change + simulation change

**Rollback plan:**
```bash
git checkout -- src/main/java/com/footballmanager/domain/model/aggregate/Team.java
git checkout -- src/main/java/com/footballmanager/application/service/domain/MatchQualityComputer.java
git checkout -- src/main/java/com/footballmanager/application/service/domain/MatchEngineImpl.java
```
Risk: Schema migration for Team table required if committed.

---

### Option C — Derive Style from Formation (Not Recommended)

**Description:** No explicit style field. Instead, map formation to a derived tactical style:
- `5-3-2` or `4-5-1` â DEFENSIVE
- `4-4-2` â BALANCED
- `4-3-3` or `3-5-2` â ATTACKING
- etc.

**Files affected:**
- `MatchQualityComputer.java` — new `computeLambdas(int, int, Formation, Formation)` overload
- Logic in `MatchEngineImpl` to extract formation and pass it

**API impact:** None (formation already exists)

**Persistence impact:** None

**Frontend impact:** None

**Simulation behavior impact:** Hidden coupling between formation and tactics — changing formation silently changes simulation behavior

**Tests required:**
- Validate all 7 formations map to plausible styles
- Validate style effects are small and within clamps

**Risk level:** **HIGH** — hidden behavior, formation semantics are ambiguous, no clear ownership of style logic

**Rollback plan:**
```bash
git checkout -- src/main/java/com/footballmanager/application/service/domain/MatchQualityComputer.java
```

**Not recommended.**

---

### Option D — MatchEngineImpl Optional Seeded Overload with Style

**Description:** Keep `Team` and `MatchEngineImpl` unchanged. Add a new `simulateWithStyle(Team, Team, TeamStyle, TeamStyle, long seed)` method that computes style-adjusted lambdas without affecting the normal `simulate()` path. Used for experiments/analytics only.

**Files affected:**
- `TeamStyle.java` — new enum
- `MatchQualityComputer.java` — style-aware overload
- `MatchEngineImpl.java` — new `simulateWithStyle()` method
- New test class for style experiments

**API impact:** None

**Persistence impact:** None

**Simulation behavior impact:** None for normal simulation path

**Tests required:**
- Same as Option A
- Determinism test: same style + same seed â identical result
- Diversity test: different styles â statistically different lambdas

**Risk level:** **LOW** — completely separate code path

**Rollback plan:**
```bash
git rm --cached src/main/java/com/footballmanager/application/service/domain/TeamStyle.java
git checkout -- src/main/java/com/footballmanager/application/service/domain/MatchEngineImpl.java
```

---

## 3. Recommended Direction

**Option A — Style-Aware computeLambdas Overload (Phase 6A)**

Rationale:
1. **Lowest-risk incremental path** — additive only, no existing code paths affected
2. No `Team` model changes needed
3. No persistence changes
4. No API changes
5. Creates reusable `TeamStyle` enum for future phases
6. Establishes style modifier formula with unit tests before touching simulation
7. `MatchEngineImpl` remains unchanged during Phase 6A — simulation behavior guaranteed stable

**Phase 6A scope:**
1. Create `TeamStyle` enum in `application/service/domain/`:
   ```java
   public enum TeamStyle {
       BALANCED,    // no modifier (baseline)
       ATTACKING,   // +totalLambda, +homeShare if home
       DEFENSIVE,   // -totalLambda, +awayShare if home
       COUNTER,    // -totalLambda, +awayShare when weaker
       POSSESSION  // -totalLambda, +homeShare
   }
   ```
2. Add `computeLambdas(int homeOvr, int awayOvr, TeamStyle homeStyle, TeamStyle awayStyle)` to `MatchQualityComputer`
3. Style effects applied as small additive adjustments before clamping:
   - `totalLambda += styleLambdaAdjust(homeStyle, awayStyle)` (clamped to [2.3, 3.05])
   - `homeShare += styleShareAdjust(homeStyle, awayStyle)` (clamped to [0.25, 0.75])
4. Add `MatchQualityComputerTest` tests validating:
   - BALANCED+BALANCED â exact same result as existing `computeLambdas()`
   - All style combinations â totalLambda within [2.3, 3.05]
   - All style combinations â homeShare within [0.25, 0.75]
   - Style effects are small (<10% change vs baseline)
5. Do NOT change `MatchEngineImpl` in Phase 6A

**Phase 6B (future, separate approval):**
- Decide whether `MatchEngineImpl` should consume style
- If yes: Option B (add to Team) or Option D (optional seeded overload)
- Would require full quality gate re-validation

**What NOT to implement in Phase 6A:**
- No `Team` domain model changes
- No `SessionTeam` changes
- No `MatchEngineImpl` changes
- No API DTOs for style
- No Redis/PostgreSQL schema changes
- No Formation-to-style derivation (Option C)
- No frontend changes

---

## 4. Phase 6A Implementation Sketch

### 4.1 TeamStyle.java — New File

```java
package com.footballmanager.application.service.domain;

/**
 * Tactical style for match simulation.
 * Phase 6A: Used only in MatchQualityComputer style-aware computeLambdas overload.
 * Phase 6B (future): May be consumed by MatchEngineImpl via Team.getStyle().
 */
public enum TeamStyle {
    /** No modifier — produces exactly the same result as computeLambdas(int, int) */
    BALANCED,

    /** Slightly higher totalLambda, slightly higher own share */
    ATTACKING,

    /** Slightly lower totalLambda, slightly higher defensive share */
    DEFENSIVE,

    /** Lower totalLambda, better chance share when weaker than opponent */
    COUNTER,

    /** Slightly lower totalLambda, slightly higher possession share */
    POSSESSION
}
```

### 4.2 MatchQualityComputer — Style-Aware Overload

```java
/**
 * Style-aware lambda computation.
 * Applies small additive adjustments before clamping.
 * BALANCED+BALANCED is guaranteed to equal computeLambdas(homeOvr, awayOvr).
 */
public static MatchQualityLambdas computeLambdas(
        int homeOvr, int awayOvr,
        TeamStyle homeStyle, TeamStyle awayStyle) {

    int ovrDiff = homeOvr - awayOvr;

    double baseTotalLambda = 2.60;
    double imbalanceBoost = Math.abs(ovrDiff) * 0.012;
    double totalLambda = baseTotalLambda + imbalanceBoost;

    // Style adjustments (applied before clamping)
    totalLambda += styleLambdaAdjust(homeStyle, awayStyle);
    totalLambda = clamp(totalLambda, 2.3, 3.05);

    double homeBaseShare = 0.52;
    double strengthShift = ovrDiff / 220.0;
    double homeShare = homeBaseShare + strengthShift;

    // Style adjustments (applied before clamping)
    homeShare += styleShareAdjust(homeStyle, awayStyle);
    homeShare = clamp(homeShare, 0.25, 0.75);

    double homeLambda = totalLambda * homeShare;
    double awayLambda = totalLambda * (1.0 - homeShare);

    return new MatchQualityLambdas(homeLambda, awayLambda, totalLambda, homeShare);
}

private static double styleLambdaAdjust(TeamStyle home, TeamStyle away) {
    // Net effect: small adjustments, net zero in BALANCED vs BALANCED
    double homeAdj = lambdaStyleBonus(home);
    double awayAdj = lambdaStyleBonus(away);
    // Combined style effect: average of both teams' tendencies
    return (homeAdj + awayAdj) / 2.0;
}

private static double styleShareAdjust(TeamStyle home, TeamStyle away) {
    // Home benefits from attacking/possession, loses from defensive/counter
    double homeAdj = switch (home) {
        case ATTACKING -> +0.03;
        case DEFENSIVE -> -0.02;
        case POSSESSION -> +0.02;
        case COUNTER -> -0.01;
        case BALANCED -> 0.0;
    };
    double awayAdj = switch (away) {
        case ATTACKING -> -0.03;  // away benefits less from attacking
        case DEFENSIVE -> +0.02;
        case POSSESSION -> -0.02;
        case COUNTER -> +0.01;
        case BALANCED -> 0.0;
    };
    return homeAdj + awayAdj;
}

private static double lambdaStyleBonus(TeamStyle style) {
    return switch (style) {
        case ATTACKING -> +0.10;  // slightly more total chances
        case DEFENSIVE -> -0.10;  // slightly fewer total chances
        case COUNTER -> -0.08;    // fewer but more efficient
        case POSSESSION -> -0.05; // fewer but higher share
        case BALANCED -> 0.0;
    };
}
```

### 4.3 Invariant Guarantee

```java
// Must hold for all OVR combinations:
var balanced = computeLambdas(75, 75);
var balancedStyle = computeLambdas(75, 75, TeamStyle.BALANCED, TeamStyle.BALANCED);
assertEquals(balanced.homeLambda(), balancedStyle.homeLambda(), 0.0001);
assertEquals(balanced.awayLambda(), balancedStyle.awayLambda(), 0.0001);
assertEquals(balanced.totalLambda(), balancedStyle.totalLambda(), 0.0001);
```

---

## 5. Files Affected (Phase 6A)

| File | Change | Risk |
|------|--------|------|
| `TeamStyle.java` | New enum in `application/service/domain/` | None — new type |
| `MatchQualityComputer.java` | Add style-aware overload, helper methods | Low — existing method unchanged |
| `MatchQualityComputerTest.java` | Add style validation tests | None — test only |

**No changes to:** `Team`, `SessionTeam`, `WorldTeam`, `MatchEngineImpl`, `CareerSave`, `TournamentState`, `MatchResult`, persistence, API, frontend.

---

## 6. Tests Required (Phase 6A)

| Test | What it validates |
|------|-------------------|
| `computeLambdas_BALANCED_vs_BALANCED_equalsBaseline` | Style-aware with BALANCED equals baseline for all 3 OVR scenarios |
| `computeLambdas_styleStaysWithinLambdaClamp` | totalLambda â [2.3, 3.05] for all 25 style combinations |
| `computeLambdas_styleStaysWithinShareClamp` | homeShare â [0.25, 0.75] for all 25 style combinations |
| `computeLambdas_styleEffectsAreSmall` | Style change < 10% vs baseline for all combinations |
| `computeLambdas_allCombinationsFinite` | No NaN/Infinity for all 125 test cases (5 styles Ã— 5 styles Ã— 5 OVR combos) |
| `V23SimulationQualityGateTest` | Full regression gate still passes |
| Full quality gate command | All 64 tests pass |

---

## 7. Success Criteria (Phase 6A)

| Metric | Target | How measured |
|--------|--------|--------------|
| `computeLambdas(ovr, ovr, BALANCED, BALANCED)` | Identical to `computeLambdas(ovr, ovr)` | Unit test assertion |
| totalLambda range | [2.3, 3.05] for all style combos | Unit test loop |
| homeShare range | [0.25, 0.75] for all style combos | Unit test loop |
| Style effect magnitude | < 10% change vs BALANCED baseline | Computed ratio |
| Existing tests | All 64 tests pass | Full quality gate |
| No production code changes | `MatchEngineImpl` unchanged | Visual inspection |
| No persistence changes | Redis/PostgreSQL schema unchanged | Visual inspection |

---

## 8. Failure Criteria

- Any existing test regression
- `MatchEngineImpl` behavior changed
- BALANCED+BALANCED not exactly equal to baseline `computeLambdas()`
- totalLambda or homeShare outside clamp ranges
- Style effect exceeding 15% change vs baseline
- NaN or Infinity produced
- New non-nullable fields in any entity/DTO

---

## 9. Rollback Plan (Phase 6A)

```bash
git rm --cached src/main/java/com/footballmanager/application/service/domain/TeamStyle.java
git checkout -- src/main/java/com/footballmanager/application/service/domain/MatchQualityComputer.java
```

**Result:** `TeamStyle.java` deleted, `MatchQualityComputer` back to `69b8e0e` state. No other files affected.

**Verification:**
```
mvn test -Dtest=V23SimulationQualityGateTest,...
```
Must pass.

---

## 10. Non-Goals

- Do **NOT** change `Team` domain entity
- Do **NOT** change `SessionTeam` or `WorldTeam`
- Do **NOT** change `MatchEngineImpl` behavior
- Do **NOT** change `MatchQualityComputer` baseline formula (existing method unchanged)
- Do **NOT** derive style from Formation (Option C — not reliable)
- Do **NOT** add style to API DTOs or persistence schema
- Do **NOT** touch V32/V33
- Do **NOT** require frontend changes
- Do **NOT** implement Phase 6B style consumption in `MatchEngineImpl`

---

## 11. Validation Command

After implementation:
```
mvn test -Dtest=MatchQualityMetricsTest,V23SimulationQualityGateTest,MatchEngineImplRoleContributionTest,MatchEngineImplEventConsistencyTest,MatchEngineImplDeterminismTest,MatchEngineImplMetricsValidationTest,MatchEngineImplPoissonValidationTest,MatchQualityComputerTest,MatchEngineImplTest,DivisionTest
```

All 64+ tests must pass.

---

## Phase 6A Delivered (commit `abbcb53`)

Phase 6A implemented as specified:
- `TeamStyle` enum created
- `computeLambdas(int, int, TeamStyle, TeamStyle)` added
- `MatchQualityComputerTest` — 8 new tests, all passing
- `MatchEngineImpl` unchanged
- No API/persistence changes

## Phase 6B — Pending

**Decision required:** Should `MatchEngineImpl` consume `TeamStyle`?

Options under evaluation:
- **Keep as analytics-only** — Phase 6A utility for analysis, no simulation change
- **Add to Team aggregate** — `Team.style` field, higher risk (persistence/API changes)
- **Optional seeded overload** — `simulateWithStyle(Team, Team, TeamStyle, TeamStyle, seed)`
- **Derive from Formation** — not recommended (risky hidden coupling)

*This document is the authoritative Phase 6 specification. Phase 6A implemented. Phase 6B requires separate approval.*
