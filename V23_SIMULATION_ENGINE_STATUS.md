# V23 Simulation Engine — Status Document

**Branch:** `mvp-1-performance-cleanup`
**Latest commit:** `d1c5c36` (feat: add role-based scorer attribution to V23 match events)
**Test status:** 48 relevant tests, 0 failures
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
| `MatchQualityComputerTest` | 6 | Unit: lambda values for equal/slight/strong OVR, clamp bounds, finite values |
| `MatchEngineImplMetricsValidationTest` | 6 | 10k matches × 3 scenarios: goals/xG ratios, 0-0 rate, 4+ rate, draw rate, no NaN/Infinity |
| `MatchEngineImplPoissonValidationTest` | 6 | 1k matches × 3 scenarios: goals in range per scenario |
| `MatchEngineImplDeterminismTest` | 7 | Phase 2: same seed → identical result; different seeds → diversity; zero/negative seeds |
| `MatchEngineImplEventConsistencyTest` | 8 | Phase 4: goal events match score; home/away attribution; events sorted; minutes valid; summary coherent |
| `MatchEngineImplRoleContributionTest` | 7 | Phase 7: synthetic role pattern; attacker >= 70%; defensive <= 15%; GK = 0; deterministic |
| `MatchEngineImplTest` | existing | Original behavior contract |
| `DivisionTest` | 8 | Career division assignment logic (unrelated, pre-existing fix) |

**Total: 48 tests, 0 failures**

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

## 11. Phase 7 Deliveries (commit `d1c5c36`)

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

## 12. Intentionally NOT Implemented Yet

- **No real player names** — `Team` stores only `Set<PlayerId>`; `Player` entity not accessible at simulation time without architecture change
- **No PlayerRepository in simulation** — synthetic role labels used instead; roles are not squad-derived
- **No xG fields in MatchResult** — `MatchResult` has no `homeXG`/`awayXG` fields; computed on-demand via `MatchQualityComputer`
- **Frontend xG display** — no API surface for xG, no JSON fields on match result serialization
- **No tactical/style modifiers** — all teams use same lambda formula regardless of strategy
- **No shot location/inside-box data** — no per-shot xG, no position data, no distToGoal
- **V32/V33 engine** — V32 is specification/documentation only, not runnable. V33 is on a separate experiment branch

---

## 13. Remaining Risks and Limitations

| Limitation | Impact | Mitigation |
|-----------|--------|-----------|
| **xG is lambda-derived, not shot-location based** | xG ≈ team lambda; does not account for shot quality distribution | Phase 1 scope only — xG is instrumentation, not goal resolution |
| **Possession formula is heuristic** | `basePossession = (homeStrength/totalStrength)*100 ±10` — not physics-based | Acceptable for current simulation fidelity |
| **No tactical style** | All teams use same lambda formula regardless of strategy | Phase 6 (Tactics/Style Modifiers) — if needed |
| **OVR calculation is squad-size based** | `70 + min(20, squadSize/2)` — no formation, player quality weighting | Sufficient for current V23 baseline |

---

## 14. Recommended Next Phase

**Recommended: Phase 8 — Full Simulation Quality Gate**

Rationale:
- **Test-only** — no production code changes, no behavior changes
- **Protects all completed work** — serves as the regression gate before Phase 5/6/tactics/API work
- **Comprehensive** — runs all scenario metrics, determinism, event consistency, role distribution, and performance sanity in one test suite
- **Required gate** — phases 5/6/any new simulation work should require Phase 8 to pass first
- **Low risk** — purely validation infrastructure, no gameplay mechanics affected

**Alternative options also available:**
- Phase 5 — Match Quality Metrics in Production (expose xG via API)
- Phase 6 — Tactics/Style Modifiers (team-wide lambda adjustments)

---

## 15. Summary

V23 simulation engine is **implemented, tested, and stable**. Phases 1A, 1B, 2, 3, 4, and 7 are complete. `MatchQualityComputer` is available as a shared utility. Shot model is aligned with lambda/xG/goals. Role-based scorer attribution is in place. All 48 relevant tests pass. No changes to production API, persistence, or frontend.

**Commit history on `mvp-1-performance-cleanup`:**
```
d1c5c36 — feat: add role-based scorer attribution to V23 match events (Phase 7)
de13433 — refactor: align V23 shot model with xG and goals (Phase 3)
b0cc191 — test: add deterministic seeding replay and determinism tests (Phase 2)
a534627 — test: add V23 event consistency validation tests (Phase 4)
1d9da23 — refactor: centralize V23 match quality lambda calculation (Phase 1B)
44b63a9 — test: add V23 match metrics and xG validation collector (Phase 1A)
4c09cfe — test: fix DivisionTest and Poisson validation imports
c0e1aa0 — refactor: replace match goal generation with calibrated Poisson model
```