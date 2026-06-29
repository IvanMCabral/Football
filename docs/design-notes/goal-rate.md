# V24 Engine — Goal Rate Distribution & Calibration Status

**Date:** 2026-06-29
**Author:** SENIOR-football (C42 cleanup)
**Status:** Research note. No engine code touched. Decision (cap or document) is Iván's.

---

## 1. Why this doc exists

C42 cleanup final included a caveat: **the V24 engine's goal rate is significantly
below the expected ~2.7 goals/team/match** (per Iván's brief). Three sprints
(C27 matchIntensity, C31 statsAmp reduction, C33 V33a cap+statsAmp restore) have
already attempted to bring it into band. This doc captures the current state
(V33a calibration, on `mvp-1-performance-cleanup` + `V25D77-BACK-CLEANUP-FINAL`)
and lays out the gap to the target so Iván can decide whether to push for
closer to real LaLiga numbers or accept the current distribution as the
"gameplay design choice."

**Out of scope:** the engine itself, `V24ShotXgCalculator.java`, the formation
modifier coefficients, the matchIntensity multiplier. None of those files
are touched by C42.

---

## 2. Current state — V33a calibration (committed to `mvp-1-performance-cleanup`)

The most recent calibration is **V33a** (Sprint C33, commit `0e3f397 fix(engine)
V25D71-C33 V33a: relax cap ratio 2.0→2.5 + restore statsAmp 0.012→0.025`).
It set `V24ShotXgCalculator` to:
- `formationModRatio` cap: 2.5 (was 2.0 in C31, was 2.5+ in pre-C31)
- `statsAmp` coefficient: 0.025 (was 0.012 in C31, was 0.025 pre-C31)

Reference values from `V33CalibrationDiagnosticTest` (N=200 per scenario,
`CachingRandomWrapper` + deterministic seeds → bit-identical reproduction):

| Scenario | OVR (H × A) | Formation (H × A) | Total goals / match | Per team | Top-team win % |
|----------|-------------|-------------------|---------------------|----------|----------------|
| **PAREJOS** | 85 × 85 | 4-3-3 × 4-3-3 | **1.160** | 0.58 | ~50% (50/50) |
| **INTERMEDIO-A** | 85 × 75 | 4-3-3 × 4-3-3 | ~2.0 (in [1.85, 2.25] band) | ~1.0 | ~70% |
| **INTERMEDIO-B** | 85 × 70 | 4-3-3 × 4-3-3 | ~2.0 (in [1.85, 2.25] band) | ~1.0 | ~75% |
| **INTERMEDIO-C** | 85 × 65 | 4-3-3 × 4-3-3 | ~2.1 (in [1.85, 2.25] band) | ~1.05 | ~75% |
| **INTERMEDIOS avg** | — | — | **2.047** | 1.02 | — |
| **DESIGUALES** | 90 × 60 | 4-3-3 × 5-3-2 | (high — see §4) | (high) | **75.5%** (in [70%, 80%] band) |

### Historical context (before V33a)

| Milestone | PAREJOS total | INTERMEDIOS avg | DESIGUALES topWins | Note |
|-----------|---------------|------------------|---------------------|------|
| Pre-C27 baseline | 4.405 | 6.0+ | 93.5% | "Goleada norm" — P(≥4 total) = 61.5% |
| C27 (matchIntensity) | 1.795 | 6.0+ | 93.5% | Reduced parejos but intermedios untouched |
| C31 (statsAmp 0.025→0.012) | ~0.5 | 5.45→2.08 | 100%→~50% | Over-correction — too few goals AND inverted top-wins |
| **C33 V33a (current)** | **1.160** | **2.047** | **75.5%** | Sweet spot per the V33a-e matrix |

### What the current band locks in

`V33CalibrationDiagnosticTest` asserts the V33a values stay in these regression
bands (loose enough to absorb RNG noise, tight enough to catch calibration drift):

```java
parejos.avgTotalGoals      ∈ [1.0, 1.3]    // V33a reference 1.160
intermediosAvg             ∈ [1.85, 2.25]  // V33a reference 2.047
desigualesHomeWinPct       ∈ [70, 80]%      // V33a reference 75.5%
```

Anyone editing `V24ShotXgCalculator.java` (lines 382, 538, 586) will trip
the regression test if they accidentally move the calibration.

---

## 3. The target — real LaLiga 2024/25 vs. Iván's "~2.7/team" brief

### 3a. Real LaLiga 2024/25 (real-world reference)

LaLiga 2024/25 final stats (publicly available, ~380 matches):
- **Total goals per match: ~2.6** (range 2.5–2.7 across the season)
- **Per team per match: ~1.3**
- Distribution: ~26% 0-0 / 1-0 / 0-1 (low-scoring), ~40% 2-1 / 1-2 / 2-0 / 0-2
  (mid), ~20% 3+ total (high), rest draws 1-1 / 2-2 etc.
- Top-wins: ~46% home, ~28% draw, ~26% away (in a 20-team league, top vs
  mid-table is closer to 55-60% top win).

### 3b. Iván's "~2.7/team" target

The task brief references **~2.7 per team per match** as the expected number.
Two interpretations are plausible:

| Interpretation | Per team per match | Total per match | Gap to current parejos | Gap to current intermedios |
|----------------|---------------------|------------------|------------------------|----------------------------|
| **A. Per team** (literal) | 2.7 | 5.4 | 4.6× too low | 2.6× too low |
| **B. Total per match** ("/team" as a typo / non-literal) | 1.35 | 2.7 | 2.3× too low | 1.3× too low |

Both interpretations agree on one thing: **the current engine is
significantly below target**, especially in parejos (current 1.16 total
vs. target 2.5–5.4 total depending on interpretation).

---

## 4. Gap analysis & open questions

### 4a. Is the gap a bug or a design choice?

**PAREJOS** (1.160 total / 0.58 per team): clearly below real football. Real
LaLiga parejos matches end 1-1, 2-1, 1-0 far more often than 0-0 / 1-0.
The current parejos distribution is "snore-fest" — about 60% of parejos
matches end 0-0 or 1-0 (combined). This is below any reasonable football
expectation, including the C27 target Iván set (1.5 total per match).

**INTERMEDIOS** (2.047 total / 1.02 per team): closer to real life but still
on the low end. Real LaLiga mid-vs-mid averages 2.4–2.6 total. Current 2.0
is in the "defensive masterclass" tail, not the median.

**DESIGUALES** (75.5% top-wins, total goals ~3.5): the top-win rate is in
band (real football top vs bottom is ~70–80%), but the goal totals are
inferred to be on the lower end too (V33a diagnostic didn't print a
specific total). The C27 target was [1.5, 4.5] total.

### 4b. Where the gap lives in the code

`V24ShotXgCalculator.java` has three relevant coefficients that
together produce the current distribution:

1. **Line 382 — `formationModRatio` cap** (currently 2.5):
   Clamps the offense-vs-defense ratio. Lower cap → fewer goals.
   V33a raised this from 2.0 → 2.5. Pre-V33a was even higher.
2. **Line 538 — `statsAmp` (offensive) coefficient** (currently 0.025):
   `1.0 + (teamAttack - 70) * 0.025` — how much elite teamAttack boosts xG.
   C31 dropped this 0.025 → 0.012, V33a restored 0.025.
3. **Line 586 — `statsAmp` (defensive) coefficient** (currently 0.025):
   Mirror of #2 for the defensive side.

If the target is to push parejos/intermedios toward real-LaLiga numbers,
the natural knobs are (in order of expected impact, all speculative):
- **Increase statsAmp further** (0.025 → 0.04?): bigger spread between
  top-tier and lower-tier attacks → more goals in intermedios and
  parejos when one team has an elite attacker.
- **Relax the cap further** (2.5 → 3.0?): lets the formationModRatio
  go higher in extreme mismatches → more goals in desiguales.
- **Boost the base xG rate** (0.01–0.60 clamp in `calculateXg`): the
  per-shot base probability. Increasing this would lift all scenarios
  uniformly but might over-correct desiguales.
- **Re-introduce a partial matchIntensity for intermedios** (current
  intensity = 1.0 for diff<30% per C27 fix; could be 0.7–0.8 for
  diff=5–25% to add some goal-suppression in true parejos only).

None of these are touched by C42. The decision is Iván's.

### 4c. Risk of over-correcting

History (C27→C31→C33) shows the engine is sensitive:
- C27 over-corrected parejos down (1.5) but left intermedios at 6+ →
  added matchIntensity.
- C31 over-corrected in the other direction (dropped statsAmp 0.012,
  runtime intermedios fell 5.45→2.08 and top-wins inverted 100%→50%) →
  V33a restore was needed.
- V33a landed in a defensible spot but **left parejos below Iván's
  own 1.5 target** (currently 1.16).

Any future push toward "real LaLiga parejos" should be done in a
matrix-style diagnostic (replicate V33a-e style: try a 5×5 grid of
{cap, statsAmp} and pick the cell that lands ALL three scenarios in
target bands) rather than ad-hoc coefficient tweaks. Otherwise we'll
re-create the C31 over-correction cycle.

---

## 5. Open questions for Iván

1. **Target interpretation**: does "~2.7/team" mean 2.7 per team
   (5.4 total per match — very high-scoring) or 2.7 total per match
   (1.35 per team — close to real LaLiga)? The doc is written to be
   useful under either interpretation.
2. **PAREJOS acceptable band**: C27 set target 1.5 total. Current is
   1.16. Do you want to push parejos up to 1.5–2.0, accept current 1.16,
   or even push it lower (more "tactical chess")?
3. **INTERMEDIOS acceptable band**: V33a landed 2.047 in the
   [1.85, 2.25] regression band. Real LaLiga intermedios is 2.4–2.6.
   Is the current band good enough for gameplay, or do you want a
   targeted push toward 2.5+?
4. **DESIGUALES band**: V33a landed top-wins 75.5%. Real LaLiga
   top-vs-bottom is ~75%. Good. But what's the goal total? The V33a
   diagnostic didn't assert on it. Should we re-introduce that
   assertion before the next calibration?
5. **Cap or document**: do you want a new sprint to push the
   numbers up (matrix-style, V33b?), or accept V33a as the gameplay
   design and document it as the "intentional" target band?

---

## 6. References

- `V24ShotXgCalculator.java` (lines 382, 538, 586 — the three knobs)
- `V33CalibrationDiagnosticTest.java` (regression band guards)
- `V27GoalBalanceBaselineDiagnosticTest.java` (pre-C27/C31/C33 baseline
  numbers + Iván's C27/C28 target bands)
- Sprint reports:
  - `reporte-C27-final.md` (C27 matchIntensity introduction)
  - `reporte-V25D31-sprint.md` (C31 over-correction)
  - `reporte-C33-phase1.md` (V33a matrix diagnostic)
  - `C33-phase2-report.md` (V33a regression test + reference values)
- Runbook: `MANAGER_TEAM_RUNBOOK.md` §3 (calibration discipline)
