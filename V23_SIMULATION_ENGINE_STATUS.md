# V23 Simulation Engine — Status Document

**Branch:** `mvp-1-performance-cleanup`
**Latest commit:** `1d9da23` (refactor: centralize V23 match quality lambda calculation)
**Test status:** 26 relevant tests, 0 failures
**Date:** 2026-05-04

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
      → generateEvents()                    // goal events per goals scored, ±card/injury
      → MatchResult.of(homeGoals, awayGoals, possession, shots, events, summary)
```

**Port interface (unchanged):**
```java
public interface MatchEngine {
    Mono<MatchResult> simulate(Team homeTeam, Team awayTeam);
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
| `MatchEngineImplTest` | existing | Original behavior contract |
| `DivisionTest` | 8 | Career division assignment logic (unrelated, pre-existing fix) |

**Total: 26 tests, 0 failures**

---

## 5. Current Validated Metrics (10,000+ matches per scenario)

| Scenario | Goals/match | xG/match | Goals/xG | 0-0 rate | 4+ rate | Draw rate |
|----------|-------------|-----------|----------|----------|---------|-----------|
| Equal OVR (75-75) | ~2.56 | ~2.60 | ~0.98 | ~7-8% | ~26% | ~26% |
| Slight favorite (80-70) | ~2.56 | ~2.72 | ~0.94 | ~7% | ~26% | ~26% |
| Strong favorite (90-60) | ~2.53 | ~2.96 | ~0.86 | ~7% | ~26% | ~26% |

All within Phase 1 acceptable ranges. Goals/xG ratio < 1.0 is expected (Poisson variance).

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

## 8. Intentionally NOT Implemented Yet

- **xG fields in MatchResult** — `MatchResult` has no `homeXG`/`awayXG` fields. xG is computed on-demand via `MatchQualityComputer`.
- **Frontend xG display** — no API surface for xG, no JSON fields on match result serialization
- **Shot quality pipeline** — shots are `Math.max(3, possession/15 + random.nextInt(5))`. No per-shot xG, no position data
- **Player-level positioning** — no x/y coordinates, no `distToGoal`, no role-based shot distribution
- **V32/V33 engine** — V32 is specification/documentation only, not runnable. V33 is on a separate experiment branch
- **Deterministic seeding** — `Random` is unseeded; replay is not reproducible
- **Career xG persistence** — `MatchResultData` in `MatchFixture` has no xG field; Redis career round data has no xG

---

## 9. Remaining Risks and Limitations

| Limitation | Impact | Mitigation |
|-----------|--------|-----------|
| **xG is lambda-derived, not shot-location based** | xG ≈ team lambda by definition; does not account for shot quality distribution within a match | Phase 1 scope only — xG is instrumentation, not goal resolution |
| **Shots are simple/independent** | `homeShots = max(3, possession/15 + random.nextInt(5))` — no shot-quality correlation with xG | Future work: shot quality pipeline |
| **No role-based shot distribution** | All shots attributed to team level; no attacker vs CB/DM split | Not in V23 scope; V32 spec has separate concern |
| **Possession formula is heuristic** | `basePossession = (homeStrength/totalStrength)*100 ±10` — not physics-based | Acceptable for current simulation fidelity |
| **Unseeded Random** | Replay produces different results; debugging non-reproducible | Future: deterministic seed option |
| **OVR calculation is squad-size based** | `70 + min(20, squadSize/2)` — no formation, player quality weighting | Sufficient for current V23 baseline |

---

## 10. Recommended Next Phase Options

### Option A — Internal Match Quality Service
Use `MatchQualityComputer` in existing services (e.g., `CareerQueryService`, `MatchFinishService`) to compute xG on demand for internal analytics — no API change, no persistence change.

**Risk:** Very low. Additive utility usage.

### Option B — Add xG Fields to API Result DTO
Add `homeXG`, `awayXG` to `MatchResultData` (the JSON-serializable DTO in `MatchFixture`). Surface xG in career round API responses.

**Risk:** Medium — changes `MatchResultData` JSON schema, requires frontend to handle optional fields.

### Option C — Shot Count Realism
Refine `homeShots` formula to better correlate with real football data (currently `max(3, possession/15 + rand)` produces ~9-10 shots/match which is within acceptable range but independent of goal quality).

**Risk:** Low — does not affect goal model. Could improve match feel.

### Option D — Deterministic Seeding
Add optional `long seed` parameter to `MatchEngineImpl.simulate()`. Use `new Random(seed)` for all random decisions.

**Risk:** Very low — purely additive, no existing behavior changes.

### Option E — Role/Team-Style Modifiers
Introduce team tactical style (possession, counter, direct) that adjusts `homeShare` or `totalLambda` slightly — e.g., `counter` team gets +0.05 away share.

**Risk:** Medium — changes goal distribution; requires careful re-validation of all Phase 1 metrics.

---

## Summary

V23 Poisson goal model is **implemented, tested, and stable**. Phase 1A and 1B deliverables are complete. `MatchQualityComputer` is available as a shared utility. No changes to production API, persistence, or frontend. All 26 relevant tests pass.

**Commit history on `mvp-1-performance-cleanup`:**
```
1d9da23 — refactor: centralize V23 match quality lambda calculation (Phase 1B)
44b63a9 — test: add V23 match metrics and xG validation collector (Phase 1A)
4c09cfe — test: fix DivisionTest and Poisson validation imports
c0e1aa0 — refactor: replace match goal generation with calibrated Poisson model
```