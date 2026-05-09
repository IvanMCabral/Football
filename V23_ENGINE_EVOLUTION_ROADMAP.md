# V23 Engine Evolution Roadmap

**Status:** ACTIVE — Phases 1A, 1B, 2, 3, 4, 5A, 5B, 6A, 6B, 7, 8, 10A, 10B, 10C1, 10C2, 10C3, and 10C4 completed. V24A/V24B/V24C/V24D1/V24D2/V24D3A/V24D3B/V24D4A/V24D4B/V24D4C completed.
**Current baseline commit:** `ab3c5fd` (V24D4C complete)
**Tests:** 334 relevant tests, 0 failures
**Date:** 2026-05-09

---

## Executive Summary

V23 currently implements a simple Poisson goal model with OVR-differential lambda splitting. The engine produces stable, validated match metrics (goals/match ≈ 2.6, 0-0 rate ≈ 7%, draw rate ≈ 26%) but lacks reproducibility, coherent shot modeling, role attribution, and tactical depth.

This roadmap defines 9 phases to evolve V23 incrementally without big rewrites. Each phase is small, testable, and committed separately. V32 remains specification-only. V33 is a separate experiment branch.

---

## Phase 0 — Current Completed Baseline

**Commit:** `ab3c5fd`
**Tests:** 334 relevant tests, 0 failures (112 V23 + 8 V24A + 22 V24B + 58 V24C + 15 V24D1 + 22 V24D2 + 17 V24D3A + 31 V24D3B + 24 V24D4A + 13 V24D4B + 12 V24D4C)

### What exists

| Component | Location | Description |
|-----------|----------|-------------|
| Poisson goal model | `MatchEngineImpl.performSimulation()` | OVR-differential lambda splitting, `totalLambda ∈ [2.3, 3.05]`, `homeShare ∈ [0.25, 0.75]` |
| `MatchQualityComputer` | `application/service/domain/MatchQualityComputer.java` | Static `computeLambdas(homeOvr, awayOvr)` and `fromTeams(Team, Team)` returning `MatchQualityLambdas` record |
| `MatchQualityMetrics` | `domain/model/valueobject/MatchQualityMetrics.java` | Immutable record: homeXg, awayXg, totalXg, goalsToXgRatio, homeShare; factory methods fromLambdas, fromTeams, withGoals |
| `MatchMetricsCollector` | `test/.../MatchMetricsCollector.java` | Test-only aggregate collector: goals, shots, xG, win/draw rates across 10k matches |
| `MatchQualityComputerTest` | `test/.../MatchQualityComputerTest.java` | 14 unit tests (6 baseline + 8 style-aware) |
| `MatchQualityMetricsTest` | `test/.../MatchQualityMetricsTest.java` | 8 unit tests for MatchQualityMetrics factories and validation |
| `MatchEngineImplMetricsValidationTest` | `test/.../MatchEngineImplMetricsValidationTest.java` | 10k-match validation asserting goals/xG/0-0/4+ within ranges |
| `MatchEngineImplPoissonValidationTest` | `test/.../MatchEngineImplPoissonValidationTest.java` | 1k-match per scenario goal range validation |
| `MatchEngineImplDeterminismTest` | `test/.../MatchEngineImplDeterminismTest.java` | 7 tests: same seed = identical result; different seeds = diversity |
| `MatchEngineImplEventConsistencyTest` | `test/.../MatchEngineImplEventConsistencyTest.java` | 8 tests: goal events match score; events sorted; summary coherent |
| `MatchEngineImplRoleContributionTest` | `test/.../MatchEngineImplRoleContributionTest.java` | 7 tests: synthetic role pattern; attacker >= 70%; defensive <= 15%; GK = 0; deterministic |
| `V23SimulationQualityGateTest` | `test/.../V23SimulationQualityGateTest.java` | 8 tests: full regression gate |
| Status document | `V23_SIMULATION_ENGINE_STATUS.md` | Full engine documentation (Phase 6B: simulateWithStyle) |

### Current simulation path

```
simulate(Team home, Team away)
  → calculateTeamOverall()     // 70 + squadSize/2, capped at +20
  → calculatePossession()       // OVR-based ±10 variance, clamped 30-70
  → computeLambdas()           // MatchQualityComputer — produces homeLambda, awayLambda
  → poissonSample(homeLambda)  // Knuth (lambda<30) or Normal approx (lambda>=30)
  → poissonSample(awayLambda)
  → poissonSample((homeLambda+awayLambda)/0.20)  // Phase 3: total shots from lambda
  → homeShots = totalShots * homePossession/100; // Phase 3: possession split
  → homeShots = max(homeGoals, homeShots)        // Phase 3: goal floor
  → generateEvents()           // Phase 7: goal events with synthetic role labels (HOME_ST_1, AWAY_RW_2), sorted by minute
  → MatchResult.of(homeGoals, awayGoals, possession, shots, events, summary)
```

### Known limitations

1. **No real player names** — `Team` stores only `Set<PlayerId>`; `Player` entity not accessible at simulation time
2. **No tactical style in simulation** — `MatchEngineImpl` uses baseline lambdas; `TeamStyle` enum exists but is not consumed
3. **No shot location/inside-box** — no per-shot xG, no position data
4. **`calculateTeamOverall` is squad-size based only** — no formation or player quality weighting

---

## Phase 2 — Deterministic Seeding / Replayability

### Objective

Enable reproducible match simulations. Given the same teams and the same seed, the engine produces the exact same `MatchResult` (goals, possession, shots, events, summary) every time. Different seeds produce statistically different results within the existing Poisson distribution.

### Non-goals

- Do NOT change the `MatchEngine` port interface or its `simulate()` signature
- Do NOT change goal distribution (goals/match, 0-0 rate, draw rate must remain within Phase 1 validated ranges)
- Do NOT add deterministic mode as default — seeded simulation is opt-in
- Do NOT refactor the internal tick engine or change possession calculation

### Files likely affected

| File | Change | Risk |
|------|--------|------|
| `MatchEngineImpl.java` | Add `simulate(Team, Team, long seed)` overload that uses `new Random(seed)` | Low — additive, existing method unchanged |
| `MatchEngineImplTest.java` | Add test: same inputs → same output across 3 consecutive calls | Low — test only |
| `MatchQualityComputerTest.java` | No change | None |

### Implementation approach

**Step 1 — Add seed-overloaded simulate method**

```java
// In MatchEngineImpl.java
public Mono<MatchResult> simulate(Team homeTeam, Team awayTeam, long seed) {
    return Mono.fromCallable(() -> performSimulation(homeTeam, awayTeam, new Random(seed)))
            .subscribeOn(Schedulers.boundedElastic());
}

private MatchResult performSimulation(Team homeTeam, Team awayTeam, Random random) {
    // same logic as existing performSimulation() but uses passed Random
    // extract the current Random random = new Random() line and replace
}
```

**Step 2 — Extract existing performSimulation to use injected Random**

```java
private MatchResult performSimulation(Team homeTeam, Team awayTeam, Random random) {
    // existing logic, just change: Random random = new Random() → use injected
    int homeOverall = calculateTeamOverall(homeTeam);
    // ... rest unchanged
}
```

**Step 3 — Existing simulate() calls new private method with `new Random()`**

```java
private MatchResult performSimulation(Team homeTeam, Team awayTeam) {
    return performSimulation(homeTeam, awayTeam, new Random());
}
```

### Tests required

1. **Determinism test** — call `simulate(team, team, 12345L)` 3 times, assert all return identical `homeGoals`, `awayGoals`, `homeShots`, `awayShots`, `events` (same size and content)
2. **Seed diversity test** — two different seeds produce different results (not guaranteed to be different — use very different seeds e.g., 1L vs System.nanoTime())
3. **No regression test** — existing `simulate(Team, Team)` (no seed) still produces results within Phase 1 ranges
4. **Seed 0 and negative seeds** — handle gracefully (Random accepts any long)

### Success criteria

- Same seed + same teams → byte-for-byte identical `MatchResult`
- Existing no-seed simulation unchanged behavior
- 26 existing tests + new deterministic tests all pass
- goals/match, 0-0 rate, draw rate within Phase 1 validated ranges

### Failure criteria

- Any existing test fails
- goals/match shifts outside 2.0–3.5 range
- Regression in 0-0 or draw rates

### Rollback plan

```bash
git checkout -- src/main/java/com/footballmanager/application/service/domain/MatchEngineImpl.java
git rm --cached src/test/java/com/footballmanager/application/service/domain/MatchEngineImplDeterminismTest.java
```
Result: `MatchEngineImpl` back to `1d9da23` state. No other files affected.

### Risk level

**LOW** — additive overload, existing code path unchanged, purely deterministic test validation.

---

## Phase 3 — Shot Model Alignment

### Objective

Make shot count coherent with goals, xG, and team strength. Currently shots are independent of lambda/goals: `max(3, possession/15 + random.nextInt(5))`. After this phase, shot generation is driven by expected goals distribution, producing realistic shot counts per match (real football: ~12-16 shots/match total).

### Non-goals

- Do NOT change goal generation (Poisson lambda model is frozen)
- Do NOT add per-shot xG or position data
- Do NOT change possession calculation
- Do NOT add shot quality differentiation for this phase
- Do NOT implement xG-based goal resolution

### Files likely affected

| File | Change | Risk |
|------|--------|------|
| `MatchEngineImpl.java` | Refine `homeShots` formula to use lambda and possession | Low — internal only |
| `MatchEngineImplMetricsValidationTest.java` | Update shot assertions to new realistic range | Low — test only |
| New: `ShotModelCalibrationTest.java` | Validate shots/match in range 12-16, correlation with xG | None — new test |

### Implementation approach

**Option B — Preferred: Generate shots from expected goals and average xG/shot**

```java
// In performSimulation(), replace shot formula:
// Average xG per shot in real football ≈ 0.12–0.18
// We use 0.20 as a slightly optimistic estimate (lower quality = fewer goals per shot)
// totalExpectedShots = totalLambda / avgXgPerShot
// homeShots = totalExpectedShots * homeShare (with Poisson variance)

double avgXgPerShot = 0.20;
double expectedTotalShots = totalLambda / avgXgPerShot;
// Add variance: Poisson-like shot count
int totalShots = poissonSample(expectedTotalShots, random);
int homeShots = Math.max(3, (int) Math.round(totalShots * possession / 100.0));
int awayShots = Math.max(3, totalShots - homeShots);
```

**Side effect:** This also makes `homeShots + awayShots` correlate with `totalLambda` (and therefore with `totalGoals`), making the simulation more coherent.

### Alternative Option A — Keep simple but calibrate

```java
// Current: max(3, possession/15 + random)
// Better calibrated: use possession and totalLambda to estimate realistic range
// Real football: ~10-14 total shots per team per match in simulation terms
int homeShots = Math.max(3, (int) (totalLambda * 2.5 * (homePossession / 100.0)) + random.nextInt(3) - 1);
```

This is simpler but less coherent with xG.

### Tests required

1. **Shot range test** — assert shots/match (total) is within 10–20 range across 1k matches
2. **Shot-goal correlation test** — matches with more shots should have slightly more goals (trend, not deterministic)
3. **No regression test** — goals/match must stay in Phase 1 validated range
4. **Possession shots coherence** — higher possession → more shots (trend, not deterministic)

### Success criteria

- Total shots/match: 10–20 (acceptable simulation range)
- Goals/match: still ~2.5–2.7 (Phase 1 range)
- Goals/xG ratio: still 0.80–1.20
- No existing test regression

### Failure criteria

- goals/match shifts outside 2.0–3.5
- shots/match drops below 6 or exceeds 25
- 0-0 rate changes significantly (>2 percentage points)

### Rollback plan

Revert `homeShots`/`awayShots` calculation in `MatchEngineImpl` to previous formula. Update or remove new shot tests.

### Risk level

**MEDIUM** — shot formula change can affect goals correlation. Requires careful re-validation.

---

## Phase 4 — Event Consistency

### Objective

Validate and ensure that `MatchResult.getEvents()` is perfectly consistent with the match score and team identifiers. Goals, cards, injuries must be plausible and internally consistent.

### Non-goals

- Do NOT change goal generation or event generation logic (yet)
- Do NOT add new event types
- Do NOT implement event-based goal resolution

### Files likely affected

| File | Change | Risk |
|------|--------|------|
| `MatchEngineImpl.java` | Ensure `generateEvents()` count matches `homeGoals` + `awayGoals` | Low |
| `MatchEngineImplMetricsValidationTest.java` | Add event consistency assertions | None — test only |
| New: `EventConsistencyTest.java` | 100% event validation test | None — new test |

### Implementation approach

**In `MatchEngineImpl.generateEvents()`:**

```java
private List<MatchEvent> generateEvents(int homeGoals, int awayGoals, Random random) {
    List<MatchEvent> events = new ArrayList<>();

    // Goals — exact count matching score
    for (int i = 0; i < homeGoals; i++) {
        int minute = 10 + random.nextInt(80);
        events.add(MatchEvent.of(MatchEvent.EventType.GOAL, minute,
                                "PlayerHome" + (i + 1), "HOME"));
    }
    for (int i = 0; i < awayGoals; i++) {
        int minute = 10 + random.nextInt(80);
        events.add(MatchEvent.of(MatchEvent.EventType.GOAL, minute,
                                "PlayerAway" + (i + 1), "AWAY"));
    }

    // Cards — 0-2 per match
    int cardCount = random.nextDouble() < 0.3 ? (random.nextDouble() < 0.5 ? 2 : 1) : 0;
    for (int i = 0; i < cardCount; i++) {
        int cardMinute = 20 + random.nextInt(60);
        events.add(MatchEvent.of(MatchEvent.EventType.CARD, cardMinute,
                                "Player", random.nextBoolean() ? "HOME" : "AWAY"));
    }

    // Injuries — 0-1 per match
    if (random.nextDouble() < 0.2) {
        int injuryMinute = 30 + random.nextInt(50);
        events.add(MatchEvent.of(MatchEvent.EventType.INJURY, injuryMinute,
                                "InjuredPlayer", random.nextBoolean() ? "HOME" : "AWAY"));
    }

    // Ensure sorted by minute
    events.sort(Comparator.comparingInt(MatchEvent::getMinute));
    return events;
}
```

### Tests required

1. **Goal event count test** — `events.count(e -> GOAL) == homeGoals + awayGoals` across 1k matches
2. **Goal team attribution test** — GOAL events with team "HOME" correlate with homeGoals > 0
3. **Event minute ordering test** — `events.stream().map(e -> e.getMinute()).isSorted()` across 1k matches
4. **Summary coherence test** — summary string contains correct score
5. **Card/injury plausibility** — at most 3 cards per match, at most 1 injury per match

### Success criteria

- 100% of simulated matches have goal event count == actual goals scored
- All events sorted by minute
- No invalid team identifiers in events
- No summary that contradicts actual score

### Failure criteria

- Even one match where `events.stream().filter(GOAL).count() != homeGoals + awayGoals`
- Any unsorted event list

### Rollback plan

Event generation logic is self-contained in `generateEvents()`. Revert to previous implementation. Run existing tests.

### Risk level

**LOW** — validation and consistency improvements only.

---

## Phase 5 — Match Quality Metrics in Production

### Objective

Decide and implement whether xG/match quality metrics should be exposed in production code (services, controllers, API responses) or kept as internal utilities only.

### Non-goals

- Do NOT change goal generation or Poisson model
- Do NOT implement xG-based goal resolution
- Do NOT persist xG to Redis without a migration plan

### Files likely affected

Options A–D have different file sets. See `V23_PHASE1B_PRODUCTION_METRICS_PLAN.md` for detailed analysis.

| Option | Files | API Impact | Persistence | Risk |
|--------|-------|-----------|-------------|------|
| **A** — Internal only | `CareerQueryService` or similar | None | None | **LOWEST** |
| **B** — MatchQualityMetrics object | New `MatchQualityMetrics.java` | Low (new object) | None | Low |
| **C** — MatchResult fields | `MatchResult.java`, `MatchResultData` in `MatchFixture` | Medium (API response change) | Medium (schema) | Medium |
| **D** — Persist xG | `CareerSave`, `MatchFixture`, Redis schema | Medium | High (migration) | **HIGH** |

### Implementation approach

**Recommended: Option B first**

1. Create `MatchQualityMetrics` record
2. Add to services that process match results — compute via `MatchQualityComputer`
3. Surface in API only if clearly needed
4. Persistence deferred

### Tests required

- Unit tests for `MatchQualityMetrics` computation
- Integration test if exposed via API endpoint

### Success criteria

- Metrics computable on demand via `MatchQualityComputer`
- No changes to existing test pass rates
- API surface (if any) backward-compatible

### Rollback plan

Remove new class. Revert any service/endpoint changes.

### Risk level

**LOW** (Option A/B) to **HIGH** (Option D)

---

## Phase 6 — Tactics / Style Modifiers

### Objective

Introduce team tactical style that slightly modifies `totalLambda`, `homeShare`, or possession without breaking the validated Poisson distribution. E.g., `counter` team gets slightly higher `awayShare` or lower `totalLambda`.

> **Phase 6B complete** — Option B: experimental `simulateWithStyle()` overload in `MatchEngineImpl`. Normal simulation path unchanged. Phase 6C or Phase 10 next.

### Non-goals

- Do NOT change the base Poisson formula
- Do NOT implement full formation or player-level tactics
- Do NOT allow tactics to shift goals/match outside Phase 1 ranges
- Do NOT implement V32-style multi-tier tactical engine

### Files likely affected

| File | Change | Risk |
|------|--------|------|
| `MatchQualityComputer.java` | Add optional `tacticsModifier(TeamStyle)` parameter to `computeLambdas()` | Low |
| `MatchEngineImpl.java` | Pass team style to `computeLambdas()` | Low |
| `Team.java` or `TeamStyle.java` | Add style field (ATTACK, DEFEND, COUNTER, POSSESSION, BALANCED) | Low — domain model |
| `MatchEngineImplTacticsTest.java` | Validate modifier effects | None — new test |

### Implementation approach

```java
public enum TeamStyle {
    ATTACKING,   // +0.10 totalLambda, +0.03 homeShare, +2 shots
    DEFENSIVE,   // -0.10 totalLambda, -0.02 homeShare, -1 shots
    COUNTER,     // -0.05 possession, +0.05 awayShare
    POSSESSION,  // +5 possession, -0.05 totalLambda
    BALANCED     // no change (baseline)
}

public static MatchQualityLambdas computeLambdas(int homeOvr, int awayOvr, TeamStyle homeStyle, TeamStyle awayStyle) {
    // base computation
    double totalLambda = clamp(baseTotalLambda + imbalanceBoost, 2.3, 3.05);
    double homeShare = clamp(homeBaseShare + strengthShift + styleModifier, 0.25, 0.75);

    // style adjustments (small, additive)
    totalLambda += styleLambdaAdjust(homeStyle);
    homeShare += styleShareAdjust(homeStyle, awayStyle);

    return new MatchQualityLambdas(totalLambda * homeShare, totalLambda * (1-homeShare), totalLambda, homeShare);
}
```

**Key constraint:** `totalLambda` must stay in `[2.3, 3.05]` after style adjustment.

### Tests required

1. **No regression test** — goals/match within Phase 1 ranges with all styles at BALANCED
2. **Style effect test** — ATTACKING style → slightly higher totalLambda/shot count (within range)
3. **Style clamp test** — style cannot push lambdas outside valid ranges
4. **Cross-style test** — 5×5 style combinations all produce valid results

### Success criteria

- goals/match still 2.0–3.5 with all style combinations
- 0-0 rate >= 5% with all styles
- Style effects measurable but small (<10% change in goals/match vs BALANCED baseline)
- All existing tests pass

### Failure criteria

- goals/match shifts outside 2.0–3.5 with any style combination
- 0-0 rate drops below 5% with any style
- Any existing test regression

### Rollback plan

Remove style parameters from `computeLambdas()`. Set all teams to BALANCED. Re-run tests.

### Risk level

**MEDIUM** — tactical modifiers can affect goal distribution. Requires extensive re-validation.

---

## Phase 7 — Player/Role Contribution

### Objective

Attribute goal events to plausible players/roles so that scorers are not generic strings. ST/RW/LW/AM are most likely scorers; CB/DM score rarely; GK almost never.

### Non-goals

- Do NOT implement full positioning system (x/y coordinates)
- Do NOT implement V32-style off-ball AI or run intelligence
- Do NOT change goal generation or xG model
- Do NOT implement player skill/overall beyond existing OVR

### Files likely affected

| File | Change | Risk |
|------|--------|------|
| `MatchEngineImpl.java` | Update `generateEvents()` to use role-weighted scorer selection | Low |
| `MatchEvent.java` | May need richer player info on goal events | Low |
| `MatchEngineImplRoleAttributionTest.java` | Validate role distribution of scorers | None — new test |

### Implementation approach

**Role-weighted scorer selection:**

```java
private String selectScorer(int teamIndex, boolean isHomeTeam, Random random) {
    // Role weights: ST=40%, RW/LW=25%, AM=20%, CM=10%, CB/DM=4%, GK=1%
    double r = random.nextDouble();
    if (r < 0.40) return roleName("ST", teamIndex);
    if (r < 0.65) return roleName("RW", teamIndex);  // or LW
    if (r < 0.85) return roleName("AM", teamIndex);
    if (r < 0.95) return roleName("CM", teamIndex);
    if (r < 0.99) return roleName("CB", teamIndex);
    return roleName("GK", teamIndex);  // rare but possible
}
```

**For teams with squad data:** Weight by actual player positions in squad.

**For simple simulation (no squad data):** Use probability weights above.

### Tests required

1. **Role distribution test** — across 1k matches, attacker roles (ST/RW/LW/AM) account for >60% of goals
2. **No impossible scorers** — CB/DM goals < 5%, GK goals < 0.5%
3. **Home/away attribution** — home goals attributed to home-role players
4. **Event count consistency** — still no regression in goal event count

### Success criteria

- Attacker role (ST/RW/LW/AM) accounts for ≥60% of goals across 1k matches
- GK goals < 1% of total goals
- CB/DM goals < 10% of total goals
- All Phase 1 metrics preserved

### Failure criteria

- Goal count changes significantly (implying logic error)
- All goals attributed to single role (weight bug)
- Any Phase 1 metric regression

### Rollback plan

Revert `generateEvents()` to use generic "PlayerHome1"/"PlayerAway1" naming. Remove role-specific logic.

### Risk level

**LOW** — event generation only, no gameplay mechanics affected.

---

## Phase 8 — Full Simulation Quality Gate

### Objective

Establish a comprehensive, repeatable validation suite that can be run after any engine change to ensure no regressions. This is the gate that must pass before merging any simulation change.

### Non-goals

- Do NOT change simulation behavior as part of this phase
- Do NOT add performance benchmarking that affects production
- Do NOT implement automated alerting (CI/CD level only)

### Files likely affected

| File | Change | Risk |
|------|--------|------|
| New: `V23SimulationQualityGateTest.java` | Main test class: 10k matches × 3 scenarios, all Phase 1 metrics asserted | None |
| New: `V23SimulationRegressionSuite.java` | JUnit test suite aggregating all simulation tests | None |
| `MatchEngineImplMetricsValidationTest.java` | May be merged into quality gate | None |

### Implementation approach

```java
class V23SimulationQualityGateTest {

    @Test
    void qualityGate_EqualOvr()    { runScenario("Equal OVR", 75, 75, 10_000); }
    @Test
    void qualityGate_SlightFav()    { runScenario("Slight fav", 80, 70, 10_000); }
    @Test
    void qualityGate_StrongFav()   { runScenario("Strong fav", 90, 60, 10_000); }

    @Test
    void qualityGate_Determinism() { runDeterminismTest(1_000); }

    @Test
    void qualityGate_EventConsistency() { runEventConsistencyTest(1_000); }

    @Test
    void qualityGate_NoRegressionVsBaseline() {
        // Compare metrics to baseline from Phase 1 (stored as constants)
        // Assert: |current - baseline| < threshold for all metrics
    }

    private void runScenario(String name, int homeOvr, int awayOvr, int matches) {
        MatchMetricsCollector c = new MatchMetricsCollector();
        for (int i = 0; i < matches; i++) {
            Team home = createTeam(homeOvr);
            Team away = createTeam(awayOvr);
            MatchResult r = simulate(home, away);
            double[] lambdas = MatchQualityComputer.computeLambdas(homeOvr, awayOvr);
            c.record(r, lambdas[0], lambdas[1], homeOvr, awayOvr);
        }
        c.assertWithinRanges(name);  // Phase 1 ranges
        c.assertNoRegressionsVsBaseline();  // Phase 1 baseline comparison
    }
}
```

### Tests required

1. **10k-match quality gate** — all three scenarios, all metrics within ranges
2. **Determinism test** — seeded simulation is byte-for-byte identical
3. **Event consistency test** — 100% of events consistent with score
4. **Regression vs baseline test** — all metrics within ±5% of Phase 1 baseline values
5. **Performance test** — 10k matches complete within reasonable time (e.g., <30 seconds)

### Success criteria

- All quality gate tests pass
- No metric deviates more than 5% from Phase 1 baseline
- Determinism test passes for 1k seeded calls
- Event consistency 100%
- No test suite timeout

### Failure criteria

- Any quality gate metric outside acceptable range
- Determinism test fails
- Any event consistency violation
- Performance regression (>50% slower than baseline)

### Rollback plan

Quality gate is a test suite — it does not change production behavior. Rollback means reverting the test file itself, which has no production impact.

### Risk level

**NONE** — this phase is test-only infrastructure.

---

## Phase 9 — Future Advanced Engine

### Objective

**Only after V23 is stable and quality gate passes consistently.** Consider building a new advanced engine (V34 or similar) from scratch, using V32 documentation as design inspiration but NOT copying V32 source code.

### Non-goals

- Do NOT implement V32 source code recovered from documentation
- Do NOT rewrite V23 into V32
- Do NOT modify V33 experiment branch from this roadmap

### Preconditions for Phase 9

1. Phase 8 quality gate passes consistently (3 consecutive runs)
2. All 26 existing tests pass
3. No critical bugs in production match simulation for ≥30 days
4. Team has bandwidth to review a new engine design

### Implementation approach

If Phase 9 is approved in the future, it should start with a separate planning document similar to this one, not inheriting V32 source or V33 experiment scope.

---

## Recommended Next Phase: V24D Planning, Phase 6C, or Phase 11

Phase 6A, Phase 6B, Phase 10C1, Phase 10C2, Phase 10C3, Phase 10C4, V24A, V24B, V24C, and V24D1 are complete:
- `TeamStyle` enum exists (BALANCED, ATTACKING, DEFENSIVE, COUNTER, POSSESSION)
- MatchQualityComputer style-aware overload exists
- `simulateWithStyle()` and `simulateWithStrength()` exist in `MatchEngineImpl`
- LeagueSimulator has optional V23 engine path behind `useV23LeagueEngine` flag (default: `false`)
- V24C completed: fatigue, cards, injuries, substitutions (commits `03148d5`/`c4aba73`/`ad72536`/`23d1806`)
- V24FatigueModel, V24DisciplineModel, V24InjuryModel, V24SubstitutionEngine all delivered and tested
- V24D1 completed: formation parser + tactical role weighting (commit `55f7638`)
- 215 tests pass (112 V23 + 8 V24A + 22 V24B + 58 V24C + 15 V24D1)

**Phase 6C — User-configurable tactical styles**
Make tactical style available to real career teams via SessionTeam/API/frontend.
- Risk: MEDIUM because it touches Redis/API/frontend and production simulation integration
- Requires: SessionTeam field, CareerSave migration, API endpoint, frontend UI
- Do not start without separate audit/plan

**Phase 11 — Frontend xG and tactic display**
Expose already available xG fields and style experiments in UI.
- Risk: LOW/MEDIUM depending on frontend scope
- xG fields already in MatchInfo/LeagueMatchInfo DTOs (nullable)

**V24C — Fatigue, cards, injuries, and substitutions (Completed)**
Continue V24 detailed engine development after V24B:
- V24FatigueModel: per-minute drain (style-based 4-6/min), action drain (shot+8, foul+5, chance+3), fatigue factor bands [0.50–1.00], xG/shooter quality penalty
- V24DisciplineModel: modulated foul [0.005–0.12], modulated yellow card [0.10–0.80], second-yellow-red, red-carded off pitch, never substituted
- V24InjuryModel: modulated injury probability [0.0005–0.02], stamina/style/high-intensity modifiers
- V24SubstitutionEngine: max 5 subs/team, priority: injured > very tired (stamina<30) > tired+yellow (stamina<50, yellowCards>=1), same-position preference, duplicate prevention
- V24C4 tests: 56 tests across 4 test classes, all passing
- Risk: LOW/MEDIUM for V24C — still isolated package, no production integration
- Commits: `03148d5`/`c4aba73`/`ad72536`/`23d1806`

**V24D1 — Formation parser and tactical role weighting (Completed)**
Formation parser + tactical role weighting for isolated V24:
- V24FormationParser: parses "4-4-2", "4-3-3", "4-2-3-1", "3-5-2", "3-4-3", "5-3-2", "5-4-1", safe fallback to "4-4-2"
- V24PlayerSelector: formation-aware `selectShooter(List, String)` and `selectShooter(List, V24Formation)` overloads
- Original `selectShooter(List)` preserved for backward compatibility
- V24FormationParserTest: 15 tests
- Risk: LOW — isolated package, no production integration
- Commit: `55f7638`
- Did NOT modify: V24MatchContext, SessionTeam, LeagueSimulator, or any production flow

**V24D2 — Assist and key-pass model + event richness (Completed)**
Assist and key-pass model + event richness for isolated V24:
- `V24AssistModel` — pure function assist/key-pass provider selection
- Formation-aware weighted provider selection (4-3-3 boosts WINGER, 4-2-3-1 boosts MID/WINGER, 3-5-2 boosts MID)
- Style modifiers: POSSESSION +0.08, ATTACKING +0.05, DEFENSIVE -0.05
- Stamina penalty: currentStamina < 30 = -0.05
- Real `relatedPlayerId`/`relatedPlayerName` on GOAL events
- Integration: `V24DetailedMatchEngine` uses `assistModel.selectAssistProvider()`
- V24D2 did NOT modify: `V24MatchEvent`, `V24PlayerSelector`, `V24MatchContext`
- V24D2 tests: 22 tests (`V24AssistModelTest`), all passing
- Risk: LOW — isolated package, no production integration
- Commit: `1149c0b`
- Recommended next: V24D3 — shot coordinates, player ratings, or storage/API design

**Required regression gate for any simulation change:**
```
mvn test -Dtest=V24DetailedMatchQueryServiceTest,V24DetailedMatchRedisAdapterTest,V24DetailedMatchDataTest,V24PlayerMatchStatsModelTest,V24ShotCoordinateTest,V24PlayerRatingModelTest,V24AssistModelTest,V24FormationParserTest,V24SubstitutionEngineTest,V24InjuryModelTest,V24DisciplineModelTest,V24FatigueModelTest,V24DetailedMatchEngineDeterminismTest,V24TimelineOrderingTest,V24DetailedMatchResultAdapterTest,V24MatchContextValidationTest,V24TimelineConsistencyTest,V24ShotXgModelTest,V24PlayerAttributionTest,LeagueSimulatorTest,MatchResultDataAdapterTest,TeamOverallCalculatorTest,MatchEngineImplStrengthSimulationTest,MatchEngineImplStyleSimulationTest,MatchQualityMetricsTest,V23SimulationQualityGateTest,MatchEngineImplRoleContributionTest,MatchEngineImplEventConsistencyTest,MatchEngineImplDeterminismTest,MatchEngineImplMetricsValidationTest,MatchEngineImplPoissonValidationTest,MatchQualityComputerTest,MatchEngineImplTest,DivisionTest
```
Expected: 334 tests, 0 failures.
---

## Phase Implementation Order

| Phase | Name | Risk | Priority | Status |
|-------|------|------|----------|--------|
| **Phase 1A** | Metrics/xG Collector | NONE | Done | Completed |
| **Phase 1B** | MatchQualityComputer | LOW | Done | Completed |
| **Phase 2** | Deterministic Seeding | LOW | Done | Completed |
| **Phase 3** | Shot Model Alignment | MEDIUM | Done | Completed |
| **Phase 4** | Event Consistency | LOW | Done | Completed |
| **Phase 5A** | MatchQualityMetrics Value Object | NONE | Done | Completed |
| **Phase 7** | Player/Role Contribution | LOW | Done | Completed |
| **Phase 8** | Full Simulation Quality Gate | NONE | Done | Completed |
| Phase 5B | MatchQualityMetrics API Exposure | LOW | 1 — Done | Completed |
| Phase 6A | Style-aware computeLambdas | NONE | Done | Completed |
| Phase 6B | Experimental simulateWithStyle overload (Option B) | LOW | Done | Completed |
| Phase 10A | Experimental simulateWithStrength overload (Option D) | LOW | Done | Completed |
| Phase 10B | TeamOverallCalculator utility + Starting XI support | LOW | Done | Completed |
| Phase 10C1 | LeagueSimulator OVR refactor to TeamOverallCalculator | LOW | Done | Completed |
| Phase 10C2 | V23 engine path behind useV23LeagueEngine flag (Option D) | LOW | Done | Completed |
| Phase 10C3 | External configuration for useV23LeagueEngine via SimulationConfig | LOW | Done | Completed |
| Phase 10C4 | LeagueSimulator dual-path integration tests | LOW | Done | Completed |
| Phase 9 | Future Advanced Engine | HIGH | 3 | Deferred until V23 stable |

---

## V24 — Parallel Detailed Match Engine

V24 is a parallel evolution line to V23. It is **not** a replacement for the V23 quick simulation engine.

| Phase | Name | Risk | Priority | Status |
|-------|------|------|----------|--------|
| V24A | Detailed engine model skeleton + deterministic engine | LOW | 1 | Completed |
| V24B | Minute-by-minute event timeline with real xG and player attribution | LOW | 2 | Completed |
| V24C | Fatigue, cards, injuries, and substitutions | LOW/MEDIUM | 3 | Completed |
| V24D1 | Formation parser and tactical role weighting | LOW | 4 | Completed |
| V24D2 | Assist/key-pass model and event richness | LOW | 2 | Completed |
| V24D3A | Shot coordinates helper and generator | LOW | 1 | Completed |
| V24D3B | Player ratings helper from timeline | LOW | 1 | Completed |
| V24D3C | Optional event/result schema enrichment | MEDIUM | 2 | Deferred |
| V24D4A | Detailed match DTOs + storage port interface | LOW | 1 | Completed |
| V24D4B | Redis adapter behind feature flag | MEDIUM | 2 | Completed |
| V24D4C | Detailed match query endpoint | MEDIUM | 2 | Completed |
| V24D5 | Production integration | HIGH | 3+ | Deferred |

*This document is the authoritative V23 evolution roadmap. V24 is documented separately in V24A_DETAILED_ENGINE_SKELETON_PLAN.md.*

---

*This document is the authoritative V23 evolution roadmap. No implementation begins until this document is approved and a specific phase plan is reviewed.*