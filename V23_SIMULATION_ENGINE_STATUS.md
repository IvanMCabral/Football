# V23 Simulation Engine — Status Document

**Branch:** `mvp-1-performance-cleanup`
**Latest commit:** `e65cb03` (V24D6E4 — form mutation integration; E1-E4 complete; 651 tests)
**Status:** V24D6H COMPLETE — yellow-card threshold (5 → 1-match suspension), RED precedence, subtract-5 reset, LeagueSimulator snapshot tracking, no frontend changes required. V24D6E COMPLETE — form persistence from V24 player ratings. Injury recovery lifecycle deferred.
**Test status:** 651 total (602 baseline + 49 V24D6H+V24D6E), 0 failures; mutation/lifecycle/form gate 234, 0 failures
**Test table:** V24FormMutationApplierTest 18 | V24CareerMutationServiceTest 58 | V24CareerMutationIntegrationTest 44 | V24SuspensionLifecycleApplierTest 19 | V24InjuryMutationApplierTest 24 | V24FatigueMutationApplierTest 30 | V24DisciplineMutationApplierTest 27 | focused gate 234
**Date:** 2026-05-17

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
| `MatchResultDataAdapterTest` | 6 | Phase 10C2: maps V23 MatchResult to MatchFixture.MatchResultData; discards events/summary; null handling |
| `LeagueSimulatorTest` | 7 | Phase 10C4: dual-path league simulation coverage; default path, V23 path, deterministic seed, skipped fixtures, all fixtures in round |
| `V24DetailedMatchEngineDeterminismTest` | 1 | V24A: deterministic skeleton engine; same seed produces identical detailed result |
| `V24TimelineOrderingTest` | 1 | V24A: timeline events ordered and within valid match minutes |
| `V24DetailedMatchResultAdapterTest` | 2 | V24A: maps detailed result to MatchResultData; validates null handling |
| `V24MatchContextValidationTest` | 4 | V24A: validates match context, starting XI size, blank/null input |

| `V24TimelineConsistencyTest` | 7 | V24B: validates timeline ordering, goal/shot/xG/stat consistency and possession totals |
| `V24ShotXgModelTest` | 7 | V24B: validates xG clamp, location hierarchy, style/pressure/goalkeeper modifiers |
| `V24PlayerAttributionTest` | 8 | V24B: validates real player IDs/names, no synthetic labels, goal/scorer attribution |
| `V24FatigueModelTest` | 13 | V24C1: stamina drain, action drain, fatigue factor, quality penalty |
| `V24DisciplineModelTest` | 19 | V24C2: foul/yellow/red probabilities, second-yellow red, red-card state |
| `V24InjuryModelTest` | 10 | V24C3: injury probability, stamina/action/style modifiers, injured state |
| `V24SubstitutionEngineTest` | 14 | V24C4: substitution priority, max 5, position preference, duplicate prevention, red-card exclusion |
| `V24FormationParserTest` | 15 | V24D1: formation parsing, safe fallback, formation-aware shooter/assist selection |
| `V24AssistModelTest` | 22 | V24D2: assist/key-pass selection, formation/style/stamina modifiers, eligibility, clamping, determinism |
| `V24ShotCoordinateTest` | 17 | V24D3A: shot coordinate value object, deterministic generator, pitch bounds, distance/angle, insideBox |
| `V24PlayerRatingModelTest` | 31 | V24D3B: rating helper, goals/assists/key passes/shots/cards/injuries/fouls/subs, clamping, determinism |
| `V24ShotCoordinateAttachmentTest` | 8 | V24D3C: shotCoordinate attached to GOAL/SHOT_ON_TARGET/BLOCK/MISS, null for non-shot events, deterministic, DTO/snapshot mapping |
| `V24DetailedMatchDataTest` | 10 | V24D4A: detailed match snapshot DTO, fromResult mapping, defensive copies, validation |
| `V24PlayerMatchStatsModelTest` | 14 | V24D4A: player stat bundle derivation from timeline, ratings, substitutions, determinism |
| `V24DetailedMatchRedisAdapterTest` | 13 | V24D4B: Redis adapter save/find/delete, career isolation, serialization, key format, deletion |
| `V24DetailedMatchQueryServiceTest` | 12 | V24D4C: feature-gated detail query service/controller, 404 disabled/missing, storage-only read |
| `V24MatchContextFactoryTest` | 20 | V24D5A: context factory, starting XI resolution, bench derivation, style defaults, validation, canBuild |
| `V24LeagueSimulationPathTest` | 11 | V24D5B: V24 LeagueSimulator path, flag precedence, result mapping, fallback, no persistence |
| `V24LeagueDetailPersistenceTest` | 9 | V24D5C: detail persistence behind persist-detail flag, no-save when disabled, best-effort failure, context fallback skip |
| `V24EndToEndFlagIntegrationTest` | 12 | V24D5D: end-to-end flag combinations, precedence, persistence/no-persistence, fallback, best-effort save, schema safety |
| `V24PlayerRatingsPersistenceTest` | 12 | V24D5F: playerRatings persistence in V24DetailedMatchData, assembler, no mutation, best-effort persistence |
| `V24InjuryMutationApplierTest` | 24 | V24D6B1: injury mutation applier, policy flags, null guards, flag-disabled, unknown player, already injured, duplicate events, empty/null playerId, edge cases |
| `V24CareerMutationServiceTest` | 47 | V24D6B2/C2 + V24D6F1 + V24D6D4: mutation service orchestration, null guards, flag combinations, exception handling, result object behavior, fatigue orchestration, policy edge cases, result factory semantics, discipline wiring |
| `V24CareerMutationIntegrationTest` | 35 | V24D6B3/C3 + V24D6F2 + V24D6D5 + V24D6D6B: LeagueSimulator wiring, allFlagsFalse, masterFlagFalse, specificFlagFalse, V24DisabledWithMutationFlags, defaultPathNoMutation, roundCompletion, fatigue flag combinations, storage failure best-effort, wiring-level master-gate, discipline flag combinations, suspension lifecycle wiring |
| `V24DisciplineMutationApplierTest` | 16 | V24D6D3: YELLOW_CARD increments yellowCards, RED_CARD increments redCards+sets suspended, duplicate RED same match counted once via HashSet, no suspension decrement, best-effort partial mutation |
| `SessionPlayerDTODisciplineFieldsTest` | 6 | V24D6D7A: SessionPlayer discipline fields traverse DTOs, null-safe exposure, suspended/injury/energy hierarchy |
| `LineupBlockingTest` | 6 | V24D6D7A: LineupHelper.validatePlayerFitness() blocks suspended players, LineupCommandUseCaseImpl.performAutoSelect() excludes suspended |
| `LineupCommandUseCaseImplAutoSelectTest` | 2 | V24D6D7A: auto-select skips suspended players, suspended player exception message |
| `SessionPlayerDisciplineFieldsTest` | 8 | V24D6D2: SessionPlayer discipline fields, null-safe getters, default values, initDefaults, field initialization |
| `V24FatigueMutationApplierTest` | 30 | V24D6C1 + V24D6F3: energy drain, null guards, flag combinations, floor at 0, unknown player skip, injured skip, substitute-only drain, custom drain values, null energy default, all-injured skip, relatedPlayerId substitute drain, null playerId graceful handling |

**Total: 651 tests, 0 failures** (602 baseline + 49 V24D6H+V24D6E: V24D6H 21 + V24D6E 28)

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

**Phase 10A, 10B, 10C1, 10C2, 10C3, 10C4, V24A, V24B, V24C, V24D1, V24D2, V24D3A, V24D3B, V24D4A, V24D4B, V24D4C, V24D5A, V24D5B, V24D5C, V24D5D, and V24D5E3 are complete. Recommended next: Add fixture-list entry point to match detail route, V24D3C optional schema enrichment, Phase 6C, or Phase 11.**

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

**Phase 10C2 COMPLETED — V23 engine path available behind feature flag**
Option D selected — feature flag / strategy switch:
- `LeagueSimulator` has `useV23LeagueEngine` flag (default: `false`)
- When flag is `false`: `DefaultMatchSimulator.simulateQuick()` unchanged — hardcoded 50/50 possession, 5/5 shots
- When flag is `true`: `MatchEngineImpl.simulateWithStrength()` with computed OVRs from TeamOverallCalculator
- V23 path: in-memory `Team` construction (no DB), deterministic seed from `fixture.getMatchId().hashCode()`
- `MatchResultDataAdapter` maps `MatchResult` to `MatchResultData` — events and summary discarded
- No behavior change unless flag is explicitly enabled
- No API/frontend/persistence changes
- 112 tests pass

**Phase 10C3 COMPLETED — External configuration for V23 league engine flag**
Expose `useV23LeagueEngine` through Spring configuration:
- `app.simulation.league.use-v23-engine: false` in `application.yaml`
- `SimulationConfig` creates `LeagueSimulator` bean via `@Bean` factory
- `LeagueSimulator` no longer annotated `@Service`
- Default remains `false` — `DefaultMatchSimulator` path unchanged
- V23 path only activates when property is explicitly `true`
- No API/frontend/persistence changes
- 112 tests pass

**Phase 10C4 COMPLETED — LeagueSimulator dual-path tests**
Add tests for default path and V23 path:
- Validate DefaultMatchSimulator path remains default
- Validate V23 path maps possession/shots correctly
- Validate deterministic seed for same fixture
- Validate completed/wrong-round fixtures are skipped
- All 7 tests pass — no production behavior change

**V24D5C COMPLETED — Detail persistence write (commit `d6b3661`):**
- `LeagueSimulator` — added `persistDetail` flag and `V24DetailedMatchStoragePort` dependency; `persistV24Detail()` called after V24 result when `persistDetail=true`
- `SimulationConfig` — wires `app.simulation.v24.persist-detail` property to `LeagueSimulator`
- `V24LeagueDetailPersistenceTest` — 9 tests covering save/no-save/failure/fallback/no-API behavior
- `V24DetailedMatchData.fromResult(...)` used to build snapshot
- `V24DetailedMatchStoragePort.save(...)` called only when: `use-v24-detailed-engine=true` AND `persist-detail=true` AND V24 simulation succeeds
- save failure is best-effort: `catch (Exception)` logs warning and round completes
- context build failure skips persistence and falls back safely
- empty `playerRatings` list passed — per-player rating persistence deferred
- No API/controller/frontend changes, no schema changes
- V24 remains isolated — no frontend
- Recommended next: V24D5E3 (now complete), V24D5E3B (now complete), playerRatings persistence backend, V24D3C, or Phase 11

**V24D5F COMPLETED — Player Ratings Persistence (commit `0c4d62b`):**
- `V24PlayerRatingsAssembler` — pure helper resolving starting XI from `CareerSave.teamStarting11`, converting `SessionPlayer` → `V24PlayerMatchState` via `fromSessionPlayer()`, delegating to `V24PlayerMatchStatsModel.computeRatings()`
- `LeagueSimulator.persistV24Detail()` now calls `v24PlayerRatingsAssembler.assemblePlayerRatings()` instead of `List.of()`
- `V24DetailedMatchData.fromResult(...)` now receives populated `playerRatings` (not empty list)
- Ratings derived deterministically from match timeline (goals, assists, shots, cards, fouls, injuries, substitutions)
- `playerRatings` populated only when: `use-v24-detailed-engine=true` AND `persist-detail=true` AND V24 simulation succeeds
- `persist-detail=false` does NOT compute or persist ratings
- Save failure remains best-effort
- **No API/Redis/frontend/schema changes**
- **No CareerSave/SessionPlayer/SessionTeam mutation**
- 12 new tests (`V24PlayerRatingsPersistenceTest`)
- Recommended next: V24D5E5 shot map UI (frontend), frontend QA/polish, or backend realism/career-state follow-ups (Phase 11)

**V24D5E COMPLETED (E1+E2+E3+E3B+E4+E5 all done) — Frontend planning, API client, match detail page, entry point, player ratings UI, and shot map UI all complete in separate frontend repo**

**V24D5E status:**
- V24D5E1 Design Document — COMPLETED (commit `e64c2d9` in root repo)
- V24D5E2 Frontend API Client + Types — COMPLETED (frontend repo `050ab57` on `mvp-1` branch)
- V24D5E3 Read-only Match Detail Page — COMPLETED (frontend repo `0ba2305` on `mvp-1` branch)
- V24D5E3B Fixture/List Entry Point — COMPLETED (frontend repo `d244097` on `mvp-1` branch)
- V24D5E4 Player Ratings UI — COMPLETED (frontend repo `958af1e` on `mvp-1` branch)
- V24D5E5 Shot Map UI — COMPLETED (frontend repo `9b88739` on `mvp-1` branch)

Frontend repo: `front-ciber/project` / Football-angular / `mvp-1`
Frontend route: `/careers/:careerId/matches/:matchId/detail` → `V24MatchDetailPageComponent`
Dashboard fixture modal links completed matches to detail page via "📊 Detalle" button.
Frontend validation: `npx tsc --noEmit` OK, `npx ng build` BUILD SUCCESS
Root/backend repo (`mvp-1-performance-cleanup`) unchanged by V24D5E frontend implementation.
Backend tests: 558 full suite, 558 regression gate, 0 failures (V24D6F1/F2/F3 added +15 mutation regression tests; V24D6D2/D3/D4/D5 added +37 discipline tests, no production code changes in V24D6F).

**Phase 11 — Frontend xG and Tactic Display**
Integrate xG fields from `MatchInfo`/`LeagueMatchInfo` DTOs into UI:
- Separate approval required for frontend work
- Low technical risk — xG fields are already in API DTOs (nullable)

**V24B COMPLETED — Minute-by-minute event timeline with shots/goals/xG**
Built on isolated V24A skeleton (commit `b4735a8`):
- Real shot events with xG per shot
- Possession per minute from TeamStyle
- Real player attribution from V24PlayerMatchState
- Tactical modifiers using TeamStyle
- No production wiring
- No Redis/API/frontend changes

**V24C — Fatigue, cards, injuries, and substitutions** ✅ COMPLETED (commits `03148d5`, `c4aba73`, `ad72536`, `23d1806`)

See Section 15 for the completed V24C delivery record. V24C is not future work — it has been implemented and tested.

**Phase 9 — Future Advanced Engine (V32/V33)**
V32 is specification-only. V33 is on a separate experiment branch.
Only after sustained V23 stability (>30 days, quality gate passes consistently).

**Required regression gate for any simulation change:**
```
mvn test -Dtest=V24ShotCoordinateAttachmentTest,V24ShotCoordinateTest,V24PlayerRatingModelTest,V24AssistModelTest,V24FormationParserTest,V24SubstitutionEngineTest,V24InjuryModelTest,V24DisciplineModelTest,V24FatigueModelTest,V24DetailedMatchEngineDeterminismTest,V24TimelineOrderingTest,V24DetailedMatchResultAdapterTest,V24MatchContextValidationTest,V24TimelineConsistencyTest,V24ShotXgModelTest,V24PlayerAttributionTest,LeagueSimulatorTest,MatchResultDataAdapterTest,TeamOverallCalculatorTest,MatchEngineImplStrengthSimulationTest,V24LeagueSimulationPathTest,MatchEngineImplStyleSimulationTest,MatchQualityMetricsTest,V23SimulationQualityGateTest,MatchEngineImplRoleContributionTest,MatchEngineImplEventConsistencyTest,MatchEngineImplDeterminismTest,V24MetricsValidationTest,V24PoissonValidationTest,MatchEngineImplTest,MatchQualityComputerTest,MatchEngineImplTest,DivisionTest,V24DetailedMatchQueryServiceTest,V24DetailedMatchRedisAdapterTest,V24DetailedMatchDataTest,V24PlayerMatchStatsModelTest,V24PlayerRatingsPersistenceTest,V24LeagueDetailPersistenceTest,V24EndToEndFlagIntegrationTest,V24MatchContextFactoryTest,V24InjuryMutationApplierTest,V24FatigueMutationApplierTest,V24CareerMutationServiceTest,V24CareerMutationIntegrationTest,SessionPlayerDTODisciplineFieldsTest,LineupBlockingTest,LineupCommandUseCaseImplAutoSelectTest
```
Expected: 651 tests (regression gate), 0 failures; 651 full suite total (602 baseline + 49 V24D6H+V24D6E).

---

## 23. Summary

V23 simulation engine is **implemented, tested, and stable**. Phases 1A, 1B, 2, 3, 4, 5A, 5B, 6A, 6B, 7, 8, 10A, 10B, 10C1, 10C2, 10C3, and 10C4 are complete. `MatchQualityComputer` and `MatchQualityMetrics` are available as shared utilities. `TeamStyle` enum exists for tactical style computation. Shot model is aligned with lambda/xG/goals. Role-based scorer attribution is in place. xG is now exposed in fixture API DTOs (MatchInfo, LeagueMatchInfo) as nullable fields. Comprehensive quality gate is established. All 285 relevant tests pass (112 V23 + 8 V24A + 22 V24B + 58 V24C + 15 V24D1 + 22 V24D2 + 17 V24D3A + 31 V24D3B). Experimental `simulateWithStyle()` and `simulateWithStrength()` methods exist in `MatchEngineImpl` for style-aware and strength-aware simulation; normal simulation path unchanged. No changes to production API, persistence, or frontend. Phase 10C3 complete — `useV23LeagueEngine` externalized via `SimulationConfig`. Phase 10C4 complete — LeagueSimulator dual-path tests validate default and V23 paths. V24D3A/V24D3B complete — shot coordinates (V24D3A) and player ratings helper (V24D3B) delivered. V24D3C (optional schema enrichment) and V24D4 (storage/API design) are the recommended next phases. V24A is also available as a parallel evolution line (see Section 24).

## 24. V24 — Parallel Detailed Match Engine (Isolated)

V24 is a **parallel evolution line** to V23. It is **not yet part of production simulation flow**.

V24A (complete) provides an isolated detailed match engine skeleton:
- **Package:** `application/service/simulation/v24/`
- **Models:** V24MatchEventType, V24ShotLocation, V24ShotQuality, V24MatchClock, V24PlayerMatchState, V24TeamMatchState, V24MatchEvent, V24MatchTimeline, V24MatchContext, V24DetailedMatchResult
- **Engine:** `V24DetailedMatchEngine` — deterministic, accepts `V24MatchContext + seed`, returns `V24DetailedMatchResult` with placeholder events
- **Adapter:** `V24DetailedMatchResultAdapter` — maps to `MatchFixture.MatchResultData` (6 fields, discards timeline/xG/summary)
- **Tests:** 8 tests covering determinism, timeline ordering, adapter, and context validation
- **No production wiring** — V24 does not integrate with `LeagueSimulator`, `MatchEngineImpl`, or any production flow
- **No persistence** — V24A has no Redis or database dependencies

V24 is **NOT a replacement for V23**. V23 remains the production simulation engine. V24 will be developed in parallel and integrated only after separate approval.

**V24B completed** — minute-by-minute event timeline with real xG and player attribution (commit `b4735a8`):
- `V24DetailedMatchEngine` now replaces placeholder events with minute-by-minute simulation
- `V24ShotXgCalculator` — multi-factor xG (location + shooter + assist + defensive pressure + GK quality + style), clamped [0.01, 0.80]
- `V24PlayerSelector` — position-weighted shooter/assist selection from real SessionPlayer IDs
- Real player attribution: goals/shots/fouls/yellows/injuries/corners/offsides/substitutions use actual SessionPlayer IDs/names
- Deterministic seed: same context + same seed = identical result
- Stats consistency: goals = goalEvents count, shots >= goals, possession sums to 100, xG > 0 when shots > 0
- V24B tests: V24PlayerAttributionTest (8), V24ShotXgModelTest (7), V24TimelineConsistencyTest (7) — 22 new tests, all pass
- V24 remains isolated — no production wiring, no LeagueSimulator integration, no Redis/API/frontend changes

**V24C completed** — fatigue, cards, injuries, and substitutions (commits `03148d5`, `c4aba73`, `ad72536`, `23d1806`):
- `V24FatigueModel` — per-minute drain (style-based 4-6/min), action drain (shot+8, foul+5, chance+3), fatigue factor bands [0.50–1.00]
- `V24DisciplineModel` — modulated foul [0.005–0.12], modulated yellow card [0.10–0.80], second-yellow-red, red-carded off pitch, never substituted
- `V24InjuryModel` — modulated injury probability [0.0005–0.02], stamina/style/high-intensity modifiers
- `V24SubstitutionEngine` — max 5 subs/team, priority: injured > very tired (stamina<30) > tired+yellow (stamina<50, yellowCards>=1), same-position preference, duplicate prevention
- V24C tests: 56 tests across 4 test classes, all passing
- V24 remains isolated — no production wiring, no LeagueSimulator integration, no Redis/API/frontend changes

**V24D1 COMPLETED — Formation parser and tactical role weighting (commit `55f7638`):**
- `V24FormationParser` — parses formation strings; safe fallback to "4-4-2"; rejects != 10 outfield players
- `V24PlayerSelector` — formation-aware `selectShooter(List, String)` and `selectShooter(List, V24Formation)` overloads
- Original `selectShooter(List)` preserved for backward compatibility
- V24D1 tests: 15 tests, all passing
- V24 remains isolated — no production wiring, no Redis/API/frontend changes
- V24D1 did NOT modify: `V24MatchContext`, `SessionTeam`, `LeagueSimulator`, or any production flow

**V24D2 COMPLETED — Assist and key-pass model + event richness (commit `1149c0b`):**
- `V24AssistModel` — pure function assist/key-pass provider selection
- `selectAssistProvider(candidates, shooter, formation, style, random)` — formation-aware weighted selection
- `assistProbability(shooter, candidate, formation, style)` — clamped [0.10, 0.85]
- Formation modifiers: 4-3-3 boosts WINGER, 4-2-3-1 boosts MID/WINGER, 3-5-2 boosts MID
- Style modifiers: POSSESSION +0.08, ATTACKING +0.05, DEFENSIVE -0.05
- Stamina penalty: currentStamina < 30 = -0.05
- Real `relatedPlayerId`/`relatedPlayerName` on GOAL events
- Integration: `V24DetailedMatchEngine` uses `assistModel.selectAssistProvider()` instead of `selector.selectAssistProvider()`
- V24D2 did NOT modify: `V24MatchEvent`, `V24PlayerSelector`, `V24MatchContext`
- V24D2 tests: 22 tests, all passing
- V24 remains isolated — no production wiring, no Redis/API/frontend changes

**V24D3A is now COMPLETED** (commit `9a632c4`). V24D3A added shot coordinate helpers:
- `V24ShotCoordinate` — immutable value object (x, y, location, distanceToGoal, angleToGoal, insideBox)
- `V24ShotCoordinateGenerator` — deterministic generator using passed Random
- Coordinate ranges per V24ShotLocation: SIX_YARD_BOX, PENALTY_AREA_CENTER, PENALTY_AREA_WIDE, OUTSIDE_BOX, LONG_RANGE
- Pitch coordinate system: x ∈ [0,100], y ∈ [0,100], goal center at (100, 50)
- `penalty(Random)` method for penalty kick coordinates
- V24D3A did NOT modify: `V24MatchEvent`, `V24DetailedMatchResult`, or xG formula
- V24D3A tests: 17 tests, all passing
- V24 remains isolated — no production wiring, no Redis/API/frontend changes

**V24D3B is now COMPLETED** (commit `09b89b2`). V24D3B added player rating helper:
- `V24PlayerRatingModel` — pure helper, no mutable state, no Random
- `computePlayerRating(playerId, timeline)` → double; `computeRatings(Collection, timeline)` → Map
- `clampRating(double)` → [1.0, 10.0]
- Rating rules: base 6.0, goal +0.8, assist +0.5, key-pass +0.30, shot +0.10 (high-xg +0.05), yellow -0.3, red -1.5, injury -0.2, foul -0.05, substitution-in +0.05
- V24D3B did NOT modify: `V24DetailedMatchResult`, `V24PlayerMatchState`, `V24MatchEvent`, or SessionPlayer
- V24D3B tests: 31 tests, all passing
- V24 remains isolated — no production wiring, no Redis/API/frontend changes
- Recommended next: V24D3C optional schema enrichment, V24D4 storage/API design, Phase 6C, or Phase 11

**Commit history on `mvp-1-performance-cleanup` for V24:**
```
0c4d62b — feat: persist V24 player ratings in detailed match data (V24D5F)
3995d3d — test: add V24D5D end-to-end flag integration tests (V24D5D)
d6b3661 — feat: persist V24 detailed match data behind feature flag (V24D5C)
cca2f6e — feat: add V24 LeagueSimulator path behind feature flag (V24D5B)
8470779 — feat: add V24 match context factory (V24D5A)
ab3c5fd — feat: add V24 detailed match query endpoint (V24D4C)
ecea7d5 — feat: add V24 detailed match Redis adapter (V24D4B)
3c653f1 — feat: add V24 detailed match DTOs (V24D4A)
09b89b2 — feat: add V24 player rating model (V24D3B)
9a632c4 — feat: add V24 shot coordinates (V24D3A)
1149c0b — feat: add V24 assist model (V24D2)
55f7638 — feat: add V24 formation parser (V24D1)
23d1806 — feat: add V24 substitution engine (V24C4)
ad72536 — feat: add V24 injury model (V24C3)
c4aba73 — feat: add V24 discipline model (V24C2)
03148d5 — feat: add V24 fatigue model (V24C1)
b4735a8 — feat: add V24 minute-by-minute event timeline (V24B)
4e2901a — test: add V24 detailed engine skeleton coverage (V24A3)
0214a74 — feat: add V24 detailed match engine skeleton (V24A2)
fd35398 — feat: add V24 detailed match model skeleton (V24A1)
```

**Commit history on `mvp-1-performance-cleanup` (full):**
```
55f7638 — feat: add V24 formation parser (V24D1)
23d1806 — feat: add V24 substitution engine (V24C4)
ad72536 — feat: add V24 injury model (V24C3)
c4aba73 — feat: add V24 discipline model (V24C2)
03148d5 — feat: add V24 fatigue model (V24C1)
b4735a8 — feat: add V24 minute-by-minute event timeline (V24B)
4e2901a — test: add V24 detailed engine skeleton coverage (V24A3)
0214a74 — feat: add V24 detailed match engine skeleton (V24A2)
fd35398 — feat: add V24 detailed match model skeleton (V24A1)
b4735a8 — feat: add V24 minute-by-minute event timeline (V24B)
4e2901a — test: add V24 detailed engine skeleton coverage (V24A3)
0214a74 — feat: add V24 detailed match engine skeleton (V24A2)
fd35398 — feat: add V24 detailed match model skeleton (V24A1)
```

**Commit history on `mvp-1-performance-cleanup`:**
```
23d1806 — feat: add V24 substitution engine (V24C4)
ad72536 — feat: add V24 injury model (V24C3)
c4aba73 — feat: add V24 discipline model (V24C2)
03148d5 — feat: add V24 fatigue model (V24C1)
b4735a8 — feat: add V24 minute-by-minute event timeline (V24B)
4e2901a — test: add V24 detailed engine skeleton coverage (V24A3)
0214a74 — feat: add V24 detailed match engine skeleton (V24A2)
fd35398 — feat: add V24 detailed match model skeleton (V24A1)
268188f — feat: externalize V23 league engine flag (Phase 10C3)
b290ca6 — test: add LeagueSimulator dual-path coverage (Phase 10C4)
a430e96 — feat: add feature-flagged V23 league simulation path (Phase 10C2)
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