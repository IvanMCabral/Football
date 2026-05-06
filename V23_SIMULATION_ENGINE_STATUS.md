# V23 Simulation Engine — Status Document

**Branch:** `mvp-1-performance-cleanup`
**Latest commit:** `e4d0856` (docs: update V23 engine docs after Phase 10C1)
**Status:** Phases 5A, 5B, 6A, 6B, 7, 8, 10A, 10B, and 10C1 complete
**Test status:** 99 tests, 0 failures
**Date:** 2026-05-05

---

## 1. Current Production Simulation Path

```
MatchEngineImpl.simulate(Team homeTeam, Team awayTeam)
  → performSimulation()
      → calculateTeamOverall(home/away)    // OVR: base 70 + squadSize/2, capped at +20
      → calculatePossession()              // OVR-based, ±10 variance, clamped to 30-70
      → MatchQualityComputer.computeLambdas() // produces homeLambda, awayLambda
      → poissonSample(homeLambda)           // Knuth for lambda<30, Normal approx for lambda>=30
      → poissonSample(awayLambda)
      → poissonSample((homeLambda+awayLambda)/0.20) // Phase 3: total shots from lambda
      → homeShots = totalShots * homePossession/100, awayShots = totalShots - homeShots
      → homeShots = max(homeGoals, homeShots)        // Phase 3: goal floor
      → generateEvents()           // Phase 7: goal events with synthetic role labels (HOME_ST_1, AWAY_RW_2, etc.), sorted by minute
      → MatchResult.of(homeGoals, awayGoals, possession, shots, events, summary)
```

**Port interface (unchanged — seed is additive overload):**
```java
public interface MatchEngine {
    Mono<MatchResult> simulate(Team homeTeam, Team awayTeam);
    Mono<MatchResult> simulate(Team homeTeam, Team awayTeam, long seed);  // Phase 2
}
```

---

## 2. Current Goal Model Formula

**Poisson lambda splitting (V23, merged at commit `c0e1aa0`):**

```
ovrDiff       = homeOverall - awayOverall
totalLambda   = clamp(2.60 + |ovrDiff| * 0.012, 2.3, 3.05)
homeShare     = clamp(0.52 + ovrDiff / 220.0, 0.25, 0.75)
homeLambda   = totalLambda * homeShare
awayLambda   = totalLambda * (1.0 - homeShare)
```

Goal sampling: `poissonSample(lambda)` — Knuth inverse-transform (lambda < 30) or Normal approximation (lambda >= 30).

---

## 3. Current MatchQualityComputer API

**Location:** `src/main/java/com/footballmanager/application/service/domain/MatchQualityComputer.java`

```java
public final class MatchQualityComputer {

    // From OVR integers
    public static MatchQualityLambdas computeLambdas(int homeOvr, int awayOvr)

    // From Team domain objects
    public static MatchQualityLambdas fromTeams(Team homeTeam, Team awayTeam)

    public record MatchQualityLambdas(
        double homeLambda,    // team xG for home team
        double awayLambda,    // team xG for away team
        double totalLambda,   // combined expected goals
        double homeShare     // home's fraction of totalLambda
    ) {
        public double totalXg() { return homeLambda + awayLambda; }
        public double homeXg()  { return homeLambda; }
        public double awayXg()  { return awayLambda; }
    }
}
```

**No randomness. No side effects. Deterministic from OVR inputs.**

---

## 4. Current Validation Tests

| Test class | Tests | What it validates |
|------------|-------|-------------------|
| `MatchQualityComputerTest` | 14 | Unit: 6 baseline + 8 style-aware lambda computation |
| `MatchEngineImplMetricsValidationTest` | 6 | 10k matches × 3 scenarios: goals/xG ratios, 0-0 rate, 4+ rate, draw rate, no NaN/Infinity |
| `MatchEngineImplPoissonValidationTest` | 6 | 1k matches × 3 scenarios: goals in range per scenario |
| `MatchEngineImplDeterminismTest` | 7 | Phase 2: same seed → identical result; different seeds → diversity; zero/negative seeds |
| `MatchEngineImplEventConsistencyTest` | 8 | Phase 4: goal events match score; home/away attribution; events sorted; minutes valid; summary coherent |
| `MatchEngineImplRoleContributionTest` | 7 | Phase 7: synthetic role pattern; attacker >= 70%; defensive <= 15%; GK = 0; deterministic |
| `MatchEngineImplStrengthSimulationTest` | 8 | Phase 10A: explicit OVR equivalence, determinism, invalid fallback, bounded metrics, existing path preserved |
| `V23SimulationQualityGateTest` | 8 | Phase 8: full regression gate; all phase metrics; determinism; event consistency; role distribution; performance |
| `TeamOverallCalculatorTest` | 10 | Phase 10B: real OVR calculator; full squad, starting XI, fallback, missing players, clamping, deterministic |

**Total: 99 tests, 0 failures**

---

## 5. Current Validated Metrics (10,000+ matches per scenario)

| Scenario | Goals/match | xG/match | Goals/xG | Shots/match | Goals/shot | 0-0 rate | 4+ rate | Draw rate |
|----------|-------------|-----------|----------|-------------|------------|----------|---------|-----------|
| Equal OVR (75-75) | ~2.61 | ~2.60 | ~1.00 | ~12.9 | ~0.20 | ~7.5% | ~27% | ~27% |
| Slight favorite (80-70) | ~2.61 | ~2.72 | ~0.95 | ~12.9 | ~0.20 | ~7.8% | ~26% | ~27% |
| Strong favorite (90-60) | ~2.58 | ~2.96 | ~0.87 | ~12.9 | ~0.20 | ~7.5% | ~26% | ~26% |

All within Phase 1/3 acceptable ranges. Goals/xG ratio ≈ 1.00 (improved from ~0.95). Shots now correlated with lambda.

**Phase 3 shot model improvements:**
- Goals > shots bug: eliminated (was 13-29 per 10k matches)
- Goals per shot: 0.27 → 0.20 (more realistic conversion rate)
- Shots range: 6-14 → 6-19 (wider, more realistic)

---

## 6. Phase 1A Deliveries (commit `44b63a9`)

- **`MatchMetricsCollector`** — test utility aggregating goals, shots, xG, win/draw rates across 10k+ matches
- **`MatchEngineImplMetricsValidationTest`** — 10k-match per scenario validation asserting metrics within ranges
- Duplicated lambda formula in test collector (subsequently fixed in Phase 1B)
- Proven: Poisson model produces stable, explainable metrics

---

## 7. Phase 1B Deliveries (commit `1d9da23`)

- **`MatchQualityComputer`** — production utility, single source of truth for lambda formula
  - `computeLambdas(int, int)` — from OVR values
  - `fromTeams(Team, Team)` — from domain Team objects
  - `MatchQualityLambdas` record with `homeLambda`, `awayLambda`, `totalLambda`, `homeShare`
  - Helper methods: `totalXg()`, `homeXg()`, `awayXg()`
- **`MatchEngineImpl`** refactored — delegates to `MatchQualityComputer`, formula no longer duplicated in production
- **`MatchMetricsCollector`** refactored — delegates to `MatchQualityComputer`, test duplication removed
- **`MatchQualityComputerTest`** — 6 unit tests for the computer (edge cases: clamp bounds, finite values, all three OVR scenarios)
- **No API changes** — `MatchEngine` port unchanged, `MatchResult` unchanged, no persistence changes

---

## 8. Phase 2 Deliveries (commit `b0cc191`)

- **`simulate(Team, Team, long seed)`** — deterministic seeded overload using `new Random(seed)`
- **`MatchEngineImplDeterminismTest`** — 7 tests validating:
  - Same seed → byte-for-byte identical `MatchResult` (goals, possession, shots, events, summary)
  - Different seeds → statistically different results
  - Zero seed and negative seed work correctly
  - Unseeded simulation unchanged
- **No production behavior changes** — unseeded `simulate(Team, Team)` unchanged
- Enables: replay debugging, deterministic CI, test reproducibility

---

## 9. Phase 3 Deliveries (commit `de13433`)

- **Shot model aligned with lambda/xG** — shots derived from same Poisson lambda as goals:
  ```java
  double avgXgPerShot = 0.20;
  double expectedTotalShots = (homeLambda + awayLambda) / avgXgPerShot;
  int totalShots = poissonSample(expectedTotalShots, random);
  totalShots = Math.max(6, Math.min(totalShots, 18));
  int homeShots = (int) (totalShots * homePossession / 100.0);
  int awayShots = totalShots - homeShots;
  homeShots = Math.max(homeGoals, homeShots);  // goal floor
  awayShots = Math.max(awayGoals, awayShots);
  ```
- **Goal floor enforced** — `shots >= goals` per team (eliminates goals>shots bug)
- **Shots now correlated with match quality** — total lambda drives total shots
- **Goals per shot: 0.27 → 0.20** — more realistic conversion rate
- **Commit:** `de13433`

---

## 10. Phase 4 Deliveries (commit `a534627`)

- **`MatchEngineImplEventConsistencyTest`** — 8 tests, 1k matches each:
  - Goal event count matches score (home + away)
  - Home goal events attributed to HOME team, away goal events to AWAY team
  - Events sorted by minute
  - Event minutes within valid range [0, 120]
  - Summary matches final score
  - Seeded event list deterministic across 3 calls
  - Event consistency across 6 OVR combinations
- **Production code: already correct** — no production changes needed
- **Validates existing `generateEvents()` logic** — already generates correct counts and attributions

---

## 11. Phase 5A Deliveries (commit `b5d286f`)

- **`MatchQualityMetrics`** — immutable value object record in `domain/model/valueobject/`:
  - Fields: `homeXg`, `awayXg`, `totalXg`, `goalsToXgRatio`, `homeShare`
  - `fromLambdas(MatchQualityLambdas)`: from pre-computed lambda values
  - `fromTeams(Team, Team)`: delegates to `MatchQualityComputer.fromTeams()`
  - `withGoals(int, int)`: computes goals/xG ratio from actual goals
  - Validation: rejects NaN/Infinity, rejects negative xG values
- **`MatchQualityMetricsTest`** — 8 unit tests covering factory methods and validation
- **Internal use only** — not persisted, not exposed via API

---

## 12. Phase 7 Deliveries (commit `d1c5c36`)

- **Synthetic role-based scorer labels** — goal events now use `HOME_ST_1`, `HOME_RW_2`, `AWAY_AM_1`, etc. instead of generic `PlayerHome1` / `PlayerAway1`
- **`selectScorer()` helper method** — weighted random role selection using injected `Random` for determinism
- **Role weights:** ST 35%, RW/LW 25%, AM 20%, CM 12%, DM 5%, DF 3%, GK 0%
- **`MatchEngineImplRoleContributionTest`** — 7 tests validating:
  - All scorer names match `^(HOME|AWAY)_(ST|RW|LW|AM|CM|DM|DF)_\\d+$`
  - Goal event count equals score (unchanged)
  - HOME/AWAY attribution correct (unchanged)
  - Attacker roles (ST/RW/LW/AM) >= 70% of goals across 30k matches
  - Defensive roles (DM/DF) <= 15% of goals across 30k matches
  - GK goals == 0
  - Seeded simulation produces identical scorer names
- **No MatchEvent schema change** — role encoded in `playerName` field
- **No API/persistence/frontend changes**
- **Determinism preserved** — same seed produces identical scorer names

---

## 13. Phase 8 Deliveries (commit `0abc001`)

- **`V23SimulationQualityGateTest`** — 8 comprehensive regression tests combining all Phase 1–7 guarantees:
  - `qualityGate_equalOvrMetrics` — 10k seeded matches, all metrics within ranges
  - `qualityGate_slightFavoriteMetrics` — 10k seeded matches, same assertions
  - `qualityGate_strongFavoriteMetrics` — 10k seeded matches, same assertions
  - `qualityGate_determinism` — seeded replay produces byte-identical MatchResult
  - `qualityGate_eventConsistency` — 1k matches, event count/attribution/sort/minutes/summary
  - `qualityGate_roleDistribution` — scorer pattern, attacker>=70%, defensive<=15%, GK=0
  - `qualityGate_noImpossibleStats` — goals<=shots, possession=100, non-negative values
  - `qualityGate_performanceSanity` — 10k matches under 30s threshold
- **Required regression gate** — any future simulation change must pass this test suite
- **Production code unchanged** — test-only addition

**Quality gate metrics (validated ranges):**

| Scenario | Goals/match | xG/match | Goals/xG | Shots/match | 0-0 rate | 4+ rate |
|----------|------------|-----------|----------|-------------|----------|---------|
| Equal (75-75) | ~2.58 | 2.60 | ~0.99 | ~12.9 | ~7.3% | ~25.4% |
| Slight (80-70) | ~2.59 | 2.72 | ~0.95 | ~12.9 | ~7.5% | ~25.3% |
| Strong (90-60) | ~2.60 | 2.96 | ~0.88 | ~12.9 | ~7.5% | ~25.9% |

---

## 14. Phase 5B Deliveries (commit `69b8e0e`)

- **`MatchInfo`** DTO � `homeXG`, `awayXG`, `totalXG` as nullable `Double` fields
- **`LeagueMatchInfo`** DTO � same xG fields
- **`UserDivisionFixtureQueryService.getByRound()`** � xG computed on-demand via `MatchQualityComputer.computeLambdas()` + squad OVR from `CareerSave`
- **`LeagueFixtureQueryService.buildLeagueDivisionFixtures()`** � same pattern for all divisions
- **`FixtureQueryHelper`** � `toMatchInfo(MatchFixture, Map, CareerSave)` overload computes xG when career context is available
- API change is additive only � all xG fields are nullable for backward compatibility
- No Redis/schema changes, no MatchResult/MatchResultData changes

## 15. Phase 6A Deliveries (commit `abbcb53`)

- **`TeamStyle`** enum � BALANCED, ATTACKING, DEFENSIVE, COUNTER, POSSESSION
- **`MatchQualityComputer.computeLambdas(int, int, TeamStyle, TeamStyle)`** � style-aware lambda computation overload
- **`MatchQualityComputerTest`** � 8 new tests for style-aware computation
- BALANCED+BALANCED produces exactly the same result as existing `computeLambdas(int, int)`
- All 25 style combinations clamped to [2.3, 3.05] totalLambda and [0.25, 0.75] homeShare
- Style effects small: <10% totalLambda change, <15% per-team change vs baseline
- `MatchEngineImpl` unchanged � no simulation behavior change
- No persistence, API, or frontend changes

## 16. Phase 6B Deliveries (commit `2eaa41a`)

- **`simulateWithStyle(Team, Team, TeamStyle, TeamStyle, long seed)`** — experimental style-aware overload in `MatchEngineImpl`
- **`MatchEngineImplStyleSimulationTest`** — 9 tests validating style simulation
- **Option B selected** — experimental overload only, no persistence/API/frontend changes
- **BALANCED+BALANCED path** delegates to baseline `computeLambdas(int, int)` for guaranteed equivalence
- **Null handling**: null style defaults to `TeamStyle.BALANCED`
- **Port interface unchanged** — `MatchEngine` interface has only `simulate(Team, Team)`
- **Existing `simulate(Team, Team, long seed)` unchanged** — same deterministic behavior
- No Team, SessionTeam, WorldTeam, API, persistence, or frontend changes

## 17. Phase 10A Deliveries (commit `f75afe1`)

- **`simulateWithStrength(Team, Team, int, int, long seed)`** — experimental explicit OVR overload in `MatchEngineImpl`
- **`MatchEngineImplStrengthSimulationTest`** — 8 tests validating explicit OVR simulation
- **Option D selected** — explicit OVR passing without TeamOverallCalculator
- Invalid OVRs (outside 1-100) fall back to `calculateTeamOverall(team)` baseline
- **Port interface unchanged** — `MatchEngine` interface unchanged
- **`performSimulationWithStrength()`** — private method using resolved OVRs for possession and lambdas
- **`isValidOvr(int ovr)`** — `ovr >= 1 && ovr <= 100`
- **Existing `simulate(Team, Team)` unchanged** — uses `calculateTeamOverall()` squad-size formula
- **Existing `simulate(Team, Team, long seed)` unchanged**
- **Existing `simulateWithStyle(...)` unchanged**
- `calculateTeamOverall()` unchanged — still `70 + min(20, squadSize/2)`
- No Team, SessionTeam, SessionPlayer, CareerSave, CareerTeamManager, CareerPlayerManager changes
- No API, persistence, Redis, PostgreSQL, or frontend changes

### Phase 10A Validation Results

| Test | What it validates |
|------|-------------------|
| `explicitOvrEqualsBaselineWhenMatchingCalculatedOvr` | OVR 80/80 with 20-player team produces identical results to baseline |
| `explicitOvrChangesOutcomeDeterministically` | Same OVR+seed → same result; asymmetric OVR → different outcomes |
| `invalidOvrFallsBackToBaseline` | OVR 0, -1, 101, 999, MIN/MAX_VALUE all fall back to baseline |
| `explicitOvrProducesValidResultsForRepresentativeScenarios` | 5 OVR pairs (75/75, 80/70, 90/60, 60/90, 70/70) all produce valid stats |
| `explicitOvrMetricsRemainBounded` | 1000 matches × 3 scenarios: goals 2.0-3.8, shots 8-20, 0-0 ≥3%, 4+ ≤45% |
| `existingSeededSimulationStillDeterministic` | `simulate(Team, Team, seed)` unchanged — same seed = identical result |
| `simulateWithStyleStillWorks` | `simulateWithStyle(...)` unchanged — BALANCED/BALANCED = baseline |
| `asymmetricOvrScenariosValid` | Strong favorite home/away produces expected possession imbalance |

## 18. Phase 10B Deliveries (commit `8530935`)

- **`TeamOverallCalculator`** — pure utility in `application/service/domain/` for real team OVR computation
- **`TeamOverallCalculatorTest`** — 10 tests validating OVR calculation
- **Pure utility, no side effects** — no repository injection, thread-safe
- **`calculateFromSessionTeam(sessionTeamId, teamManager, playerManager)`** — delegates to `CareerTeamManager.calculateTeamOVR()`
- **`calculateFromStartingXI(sessionTeamId, career)`** — uses starting XI, falls back to squad if empty
- **`calculateFromPlayerIds(playerIds, playerProvider)`** — raw player ID list with lookup function
- **`calculateFallbackFromSquadSize(squadSize)`** — returns `70 + min(20, squadSize/2)` matching `MatchEngineImpl.calculateTeamOverall()`
- **Clamps to [1, 100]** — all methods return valid OVR
- **No production integration** — `simulateWithStrength()` is NOT called from production flow yet
- **Ready for Phase 10C** — caller with `CareerSave` context can call `TeamOverallCalculator` then `simulateWithStrength()`

## 19. Intentionally NOT Implemented Yet

- **No real player names** — `Team` stores only `Set<PlayerId>`; `Player` entity not accessible at simulation time without architecture change
- **No PlayerRepository in simulation** — synthetic role labels used instead; roles are not squad-derived
- **No xG fields in MatchResult** — `MatchResult` has no `homeXG`/`awayXG` fields; computed on-demand via `MatchQualityComputer`
- **Frontend xG display** — xG is in API DTOs but frontend integration is a separate future task
- **No tactical style user-configurable** — `TeamStyle` enum exists but no UI/API to set style; `simulateWithStyle()` is experimental only; Phase 6C or later could add user-facing style selection
- **No shot location/inside-box data** — no per-shot xG, no position data, no distToGoal
- **V32/V33 engine** — V32 is specification/documentation only, not runnable. V33 is on a separate experiment branch

---

## 20. Remaining Risks and Limitations

| Limitation | Impact | Mitigation |
|-----------|--------|-----------|
| **xG is lambda-derived, not shot-location based** | xG ≈ team lambda; does not account for shot quality distribution | Phase 1 scope only — xG is instrumentation, not goal resolution |
| **Possession formula is heuristic** | `basePossession = (homeStrength/totalStrength)*100 ±10` — not physics-based | Acceptable for current simulation fidelity |
| **No tactical style in simulation** | MatchEngineImpl uses baseline lambdas; TeamStyle available but not consumed | Phase 6B experimental overload available; Phase 6C for user-facing |
| **OVR calculation is squad-size based for normal simulate()** | `70 + min(20, squadSize/2)` — no formation, player quality weighting | Phase 10A added explicit OVR overload; Phase 10B for real OVR integration |
| **xG not persisted to Redis** | Computed on-demand only; past matches have no xG in saved career data | Phase 5C deferred — Redis schema migration required |

---

## 21. Phase 5C — Future Work (Deferred)

- **`goalsToXgRatio`** — optional future field for over/under-performance analysis; not in current scope
- **Redis persistence of xG** — explicitly deferred due to schema migration complexity; no current plan
- **Frontend display** — separate approval required for frontend integration of xG fields

---

## 22. Recommended Next Phase

**Phase 10A, 10B, and 10C1 complete. Phase 10C2 next — evaluate V23 engine swap for league simulation.**

**Phase 6C — User-Configurable Tactical Style**
Add `TeamStyle` to `SessionTeam` (Redis), expose via career API, add frontend style selector:
- Low-medium risk — SessionTeam is Redis JSON, backward-compatible addition
- Requires: SessionTeam field, API endpoint, frontend UI
- Not yet approved — decision point for future sprint

**Phase 10C1 COMPLETED — LeagueSimulator OVR refactor**
Refactor LeagueSimulator.calculateTeamOVR() to delegate to TeamOverallCalculator.
- Option A selected: pure refactor, no simulation engine change
- Legacy empty-squad behavior preserved (returns 50)
- All OVR values match old implementation
- DefaultMatchSimulator.simulateQuick() unchanged
- No API/frontend/persistence changes

**Phase 10C2 — Evaluate V23 engine swap for league simulation**
Replace DefaultMatchSimulator.simulateQuick() with MatchEngineImpl.simulateWithStrength():
- Phase 10C1 already delegates LeagueSimulator OVR calculation to TeamOverallCalculator
- DefaultMatchSimulator.simulateQuick() is still used
- simulateWithStrength() is still not called by production league flow
- Phase 10C2 would evaluate replacing DefaultMatchSimulator with MatchEngineImpl.simulateWithStrength()
- Requires adapter from MatchResult to MatchFixture.MatchResultData
- Medium risk — changes simulation math/model and requires validation of all 99 tests
- Do not start without separate audit/plan
**Phase 11 — Frontend xG and Tactic Display**
Integrate xG fields from `MatchInfo`/`LeagueMatchInfo` DTOs into UI:
- Separate approval required for frontend work
- Low technical risk — xG fields are already in API DTOs (nullable)

**Phase 9 — Future Advanced Engine (V32/V33)**
V32 is specification-only. V33 is on a separate experiment branch.
Only after sustained V23 stability (>30 days, quality gate passes consistently).

**Required regression gate for any simulation change:**
```
mvn test -Dtest=MatchQualityMetricsTest,V23SimulationQualityGateTest,MatchEngineImplRoleContributionTest,MatchEngineImplEventConsistencyTest,MatchEngineImplDeterminismTest,MatchEngineImplMetricsValidationTest,MatchEngineImplPoissonValidationTest,MatchQualityComputerTest,MatchEngineImplTest,DivisionTest
```

---

## 23. Summary

V23 simulation engine is **implemented, tested, and stable**. Phases 1A, 1B, 2, 3, 4, 5A, 5B, 6A, 6B, 7, and 8 are complete. `MatchQualityComputer` and `MatchQualityMetrics` are available as shared utilities. `TeamStyle` enum exists for tactical style computation. Shot model is aligned with lambda/xG/goals. Role-based scorer attribution is in place. xG is now exposed in fixture API DTOs (MatchInfo, LeagueMatchInfo) as nullable fields. Comprehensive quality gate is established. All 99 relevant tests pass. Experimental `simulateWithStyle()` and `simulateWithStrength()` methods exist in `MatchEngineImpl` for style-aware and strength-aware simulation; normal simulation path unchanged. No changes to production API, persistence, or frontend. Phase 10C (career/league integration using TeamOverallCalculator) and Phase 6C (user-configurable style) are the recommended next phases.

**Commit history on `mvp-1-performance-cleanup`:**
```
e4d0856 — docs: update V23 engine docs after Phase 10C1
8530935 — feat: add team overall calculator for real OVR computation (Phase 10B)
05597ab — refactor: delegate league OVR calculation to TeamOverallCalculator (Phase 10C1)
f75afe1 — feat: add experimental explicit OVR match simulation overload (Phase 10A)

2eaa41a — feat: add experimental style-aware match simulation overload (Phase 6B)
abbcb53 — feat: add style-aware match quality lambda computation (Phase 6A)
69b8e0e — feat: expose xG metrics in fixture query DTOs (Phase 5B)
b5d286f — feat: add internal match quality metrics value object (Phase 5A)
0abc001 — test: add V23 full simulation quality gate (Phase 8)
d1c5c36 — feat: add role-based scorer attribution to V23 match events (Phase 7)
de13433 — refactor: align V23 shot model with xG and goals (Phase 3)
b0cc191 — test: add deterministic seeding replay and determinism tests (Phase 2)
a534627 — test: add V23 event consistency validation tests (Phase 4)
1d9da23 — refactor: centralize V23 match quality lambda calculation (Phase 1B)
44b63a9 — test: add V23 match metrics and xG validation collector (Phase 1A)
4c09cfe — test: fix DivisionTest and Poisson validation imports
c0e1aa0 — refactor: replace match goal generation with calibrated Poisson model
```