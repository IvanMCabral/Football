# V24D6K3 — Constants Tuning Design Based on Diagnostic Harness Results

**Status:** K3 COMPLETE — K4/K5/K6/K7/K8 complete; no tuning applied. See V24D6K7 and V24D6K8 for final decisions.
**Branch:** `mvp-1-performance-cleanup`
**Based on:** V24D6K2 diagnostic harness results (`957ce7f`), K4 rotation-aware results (`cd48379`), K6 season-shaped results (`8502b5d`)
**Last Updated:** 2026-05-22

---

## Purpose

This document analyzes K2 diagnostic output and proposes a tuning strategy for V24 career mutation constants. It is **design only** — no constant changes are committed here. The goal is to establish which constants might need tuning, in what order, and based on what evidence.

---

## 1. Diagnostic Interpretation

### 1.1 K2 Real-Engine Results Summary

50 matches, V24DetailedMatchEngine, all mutation flags enabled:

| Metric | Value |
|--------|-------|
| Newly injured transitions | 22 |
| Injuries per match | 0.44 |
| Currently injured at end | 22 (all squad) |
| Unique unavailable at round 5 | 4 |
| Unique unavailable at round 10 | 8 |
| Unique unavailable at round 20 | 21 |
| Unique unavailable at round 30 | 22 (squad size) |
| Avg energy at round 10 | 10.5 |
| Yellow cards | 42 |
| Red cards | 0 |
| New suspensions | 2 |
| Form avg at end | 55.7 |

### 1.2 Severity Assessment

| Area | Severity | Notes |
|------|----------|-------|
| Injury rate | **HIGH** — critical | 0.44/match is likely too aggressive for MVP; all 22 players injured by round 30 |
| Energy collapse | **HIGH** — critical | Avg 10.5 by round 10 indicates drain exceeds recovery even with recovery |
| Injury recovery | **HIGH** — critical | All 22 still injured at end — no recovery clearing injured players |
| Discipline | LOW | 42 yellows/50 matches = 0.84/match; 2 suspensions — likely acceptable |
| Form movement | LOW-MEDIUM | 55.7 avg after 50 matches — moderate, not alarming |

### 1.3 Why the 50-Match Diagnostic Shows Severe Attrition

The diagnostic runs 50 consecutive matches with the same 22-player squad. Key dynamics:

1. **Starting XI plays every match** — no rotation in this diagnostic
2. **All 22 players eventually become injured** — injury model emits events frequently
3. **Injured players may still be selected in starting XI** — diagnostic does not enforce `isPlayerAvailable()` exclusion on lineup selection
4. **Injury recovery applies only to non-participating players** — if injured players keep playing, recovery never triggers for them
5. **Energy collapses** because starting XI drains -12/round but no bench rotation means same players keep draining

This means the **attrition is not necessarily a constant calibration problem** — it may be a diagnostic realism problem (no rotation) combined with a possible injury call frequency issue.

### 1.4 Separating Diagnostic Artifact from Real Problem

**Two distinct questions must be answered before tuning:**

1. **Is the injury model emitting events too frequently?** (constant or call-frequency problem)
2. **Are injured players correctly excluded from selection in real gameplay?** (lifecycle wiring problem)

If injured players ARE correctly excluded (J3 `isPlayerAvailable()` works), then a real season with rotation would see recovery applied to non-participating injured players, and the injury rate would be less catastrophic. The K2 diagnostic may overstate the problem by not enforcing rotation.

If injured players are NOT correctly excluded (auto-select still picks injured players), then every round adds more injuries without recovery, and the problem compounds — but this is a J3 bug, not a constant calibration issue.

**The diagnostic must be upgraded (K4) to differentiate these cases before constants are changed.**

---

## 2. Root Cause Analysis by Domain

### 2A. Injury Rate Problem

**Observed:** 22 injuries over 50 matches = 0.44 per match. If evaluated once per player per match, expected rate with BASE_INJURY_PROB=0.003:
- 22 players × 0.003 × 50 matches = 3.3 expected injuries

**Actual observed: 22** — roughly 7× the expected rate.

**Possible explanations (ranked by likelihood):**

| Hypothesis | Likelihood | Explanation |
|-----------|------------|-------------|
| H1: Injury model called multiple times per player per match | **HIGH** | V24DetailedMatchEngine may call V24InjuryModel per action/event, not once per player per match. 7× multiplier suggests ~7 calls per player per match. |
| H2: Injured players not excluded from lineup, compounding effect | **HIGH** | Each injured player keeps accumulating injuries each match because they keep playing. Recovery never triggers. |
| H3: Constant is genuinely too high | **MEDIUM** | If H1 and H2 are ruled out, BASE_INJURY_PROB may need reduction. |

**Recommended verification (K4):**
- Add per-round injury event emission logging to diagnostic
- Verify injured player exclusion in diagnostic's auto-select path
- Count how many times V24InjuryModel.evaluate() is called per match per player

**Do NOT reduce BASE_INJURY_PROB until H1 and H2 are verified.**

---

### 2B. Injury Recovery Problem

**Observed:** 22 currently injured at end, 0 recovery visible.

**Mechanism:** `V24InjuryRecoveryLifecycleApplier` applies recovery **only to non-participating players** post-round.

**Why it fails in diagnostic:**
1. Starting XI (11 players) participates every match
2. Injured players are NOT excluded from starting XI in diagnostic
3. Therefore injured players continue to participate every round
4. Therefore recovery never applies to them
5. `injuryRemainingMatches` counts down only for players who don't participate
6. Since injured players always participate, their `injuryRemainingMatches` never decrements

**The diagnostic is not exercising the recovery lifecycle correctly** because it lacks rotation-aware auto-select.

**Fix (K4):** Upgrade diagnostic to use realistic auto-select that excludes injured players (J3 `isPlayerAvailable()`). This should show recovery applying to bench players who are injured and don't participate.

**If after K4 diagnostic upgrade injury still accumulates at high rate**, then constants may need tuning:
- Reduce `BASE_INJURY_PROB`
- OR add per-team per-match injury cap
- OR increase default `injuryRemainingMatches` duration (shorter seasons = less impact)

---

### 2C. Energy Balance Problem

**Observed:** Avg energy 10.5 by round 10, min 0.0, max 73.0.

**Mechanism:**
- Starting XI drains -12 energy per match
- Recovery applies +8 only to non-participating bench players
- With 11 starters playing every match, only bench (non-participating) gets recovery
- After ~7 rounds: starting XI energy = 85 - (7 × 12) = 1; bench energy = 85 + (7 × 8) = 141 → capped at 100
- By round 10, starting XI is near 0

**Diagnostic realism issue:** No rotation means starting XI hits 0 energy. In real gameplay, auto-select would substitute exhausted players with fresher bench players. Energy recovery per non-participant (+8) would then apply to starters who got substituted.

**However**, if injured players are NOT excluded from selection (H2 above), they keep playing and draining even at 0 energy. Energy below 0 is clamped to 0 (per `V24FatigueMutationApplier`). So energy can't go negative — but at 0, the player's performance would ideally degrade (not yet modeled).

**Energy constants assessment:**
- `FULL_MATCH_DRAIN = 12`: Likely correct — feels severe but intentional
- `SUBSTITUTE_DRAIN = 6`: Likely correct
- `DEFAULT_RECOVERY_PER_NON_PARTICIPANT = 8`: May be slightly low

**Key question:** Does auto-select substitute exhausted players in real gameplay? If yes, energy drain is managed by rotation. If no, energy is a serious balance problem regardless of constants.

**Recommended action (K4):** Verify auto-select substitutes players with low energy (<20) in the diagnostic. If it does, energy drain is self-correcting. If it doesn't, K5 should tune drain/recovery constants.

---

### 2D. Discipline Balance

**Observed:** 42 yellows over 50 matches = 0.84/match. 2 suspensions. 0 reds.

**Assessment:** Discipline rate appears **LOW-MEDIUM** — not alarming.

- Premier League average: ~2.5 yellow cards per match per team ≈ 5 per match total ≈ 0.10-0.15 per player per match
- Our diagnostic: 42 / 50 = 0.84 yellows per squad per match, spread across 22 players ≈ 0.038 per player per match
- This is well within acceptable bounds.

**Do not tune discipline constants at this time.**

---

### 2E. Form Balance

**Observed:** Average form rose from 50 to 55.7 over 50 matches. Max 65. Min 50.

**Assessment:** Form movement is **LOW** severity — acceptable for MVP.

- A player averaging good performance (+2 delta per match) would gain ~100 form over 50 matches — which is clamped at 99
- Our observed avg 55.7 suggests average performance is neutral-to-good
- Max 65 suggests no saturation issues yet
- Form delta table seems appropriately calibrated (coarse steps, no wild swings)

**Do not tune form deltas at this time.**

---

## 3. Recommended Tuning Candidates

### 3A. Injury Rate — Candidate for Tuning (if H1/H2 ruled out)

| Parameter | Current | Proposed | Reason | Expected Effect | Risk |
|-----------|---------|----------|--------|-----------------|------|
| `BASE_INJURY_PROB` | 0.003 | 0.001-0.002 | If H1 (call frequency) is confirmed as ~1 call/player/match, reduce by 33-50% | Fewer injuries per season | Players may go entire seasons with zero injuries — too sterile |
| OR per-team per-match injury cap | none | 1-2 injuries per team per match | Prevents mass injury events in one match | May feel artificial to players |

**Note:** Do NOT tune until K4 verifies call frequency and diagnostic rotation realism.

---

### 3B. Energy Drain/Recovery — Candidate for Tuning (if K4 shows auto-select works but energy still collapses)

| Parameter | Current | Proposed | Reason | Expected Effect | Risk |
|-----------|---------|----------|--------|-----------------|------|
| `FULL_MATCH_DRAIN` | 12 | 10 | If K4 shows rotation works but starting XI still collapses too fast | Slower energy decline, bench more valuable | Bench becomes too strong if starters barely fatigue |
| `DEFAULT_RECOVERY_PER_NON_PARTICIPANT` | 8 | 10 | Slightly faster recovery for bench to incentivize rotation | Bench players return to relevance faster | Starting XI may never need rotation if recovery is too fast |
| `SUBSTITUTE_DRAIN` | 6 | 5 | Reduced penalty for substitutes to encourage usage | Managers more willing to rotate | Could reduce strategic depth |

**Note:** Energy tuning should only happen after confirming auto-select/rotation is realistic (K4).

---

### 3C. Candidates NOT Recommended for Tuning at This Time

| Candidate | Reason |
|-----------|--------|
| `YELLOW_CARD_SUSPENSION_THRESHOLD` | 42 yellows / 50 matches = 0.84/match — discipline is not problematic |
| Form deltas | Form movement is moderate and seems well-calibrated |
| Red card rules | 0 reds in 50 matches — no data to suggest change needed |

---

## 4. Recommended MVP Tuning Strategy

### Phase Order (Do NOT skip steps)

```
K2 (done) → K3 (this doc) → K4 (diagnostic realism) → K5 (conservative tuning) → K6 (suite + docs)
```

### Step 1: K4 — Diagnostic Realism Upgrade (HIGHEST PRIORITY)

Before any constant tuning, upgrade the diagnostic to be rotation-aware:

- Use `isPlayerAvailable()` from J3 to exclude injured players from auto-select
- Use energy-based auto-substitution when player energy < 20
- Add per-round snapshots showing:
  - How many injuries occurred
  - Which players recovered (injuryRemainingMatches → 0)
  - Starting XI vs bench energy split
  - Substitution events

This will reveal:
- Whether injured players are correctly excluded (H2)
- Whether energy recovery is actually applying to substituted players
- Whether the injury rate is still high after rotation is enforced

### Step 2: Analyze K4 Results

After K4 diagnostic upgrade, compare:

- Injury rate with rotation enforcement vs without
- Energy distribution with vs without substitution
- Recovery lifecycle exercise (do injured bench players recover?)

### Step 3: K5 — Conservative Tuning Based on K4 Results

Only if K4 shows:
- Injury rate still too high (after rotation enforcement) → tune BASE_INJURY_PROB
- Energy still collapses (after auto-select/substitution) → tune drain/recovery constants
- Otherwise, no tuning needed — model is working as intended

**Critical principle: Tune one thing at a time. Re-run K4 harness between changes.**

---

## 5. Proposed Target Ranges (Provisional — Not Hard Assertions)

These are **provisional MVP targets** derived from football common sense and K2 data. They should be used as guidance for K4 diagnostic review, not as pass/fail test assertions.

| Metric | Provisional Target | Alert Threshold | Notes |
|--------|-------------------|-----------------|-------|
| Injuries per 38-match season per squad | 3–8 | <2 or >12 | Depends heavily on rotation and diagnostic realism |
| Unique unavailable due to injury at any round | 0–4 usual | >6 sustained | Injured players should be rotated out and recover |
| Avg squad energy after round 10 | 55–80 | <45 | Depends on rotation enforcement |
| Avg squad energy after round 20 | 45–75 | <35 | Bench recovery should counterbalance starters |
| Players below 20 energy (sustained) | <30% squad | >50% for >5 rounds | Rotation should prevent mass exhaustion |
| Suspensions per season per squad | 3–10 | <2 or >15 | Discipline seems lower-priority to tune |
| Form avg after 38-match season | 45–60 | <40 or >65 | Acceptable range for MVP |
| Form max after 38-match season | <80 | ≥80 | Prevents form saturation |

**These are provisional and will be refined after K4 diagnostic run with rotation enforcement.**

---

## 6. Open Questions

| # | Question | Why It Matters | Priority |
|---|----------|----------------|----------|
| Q1 | Is the diagnostic's auto-select excluding injured players? | If not, H2 is active and injury accumulation is diagnostic artifact | HIGH |
| Q2 | Does V24InjuryModel get called once per player per match, or per action/tick? | Determines whether BASE_INJURY_PROB constant is the root cause or call frequency is | HIGH |
| Q3 | Does auto-select substitute players with energy < 20? | Determines whether energy drain self-corrects via rotation | HIGH |
| Q4 | Does injury recovery apply to a player whose team has a fixture but who was not selected? | Pre-injured non-participant should recover post-round | MEDIUM |
| Q5 | Should there be a per-team per-match injury cap? | Prevents mass-injury rounds from ruining match quality | MEDIUM |
| Q6 | Is 50 consecutive matches without rotation realistic for a squad? | Real seasons have rotation, tactical substitutions, bye weeks | HIGH |
| Q7 | Do the existing unit tests for V24InjuryModel test call frequency or just probability math? | May need to add call-frequency validation to unit tests | MEDIUM |

---

## 7. Recommended K4 Implementation

### Option A: Diagnostic Realism Upgrade (RECOMMENDED)

K4 should upgrade `V24MutationBalancingDiagnosticTest` to be rotation-aware:

1. **Auto-select with injury exclusion:**
   - After each match, call `isPlayerAvailable()` to filter injured players
   - Build next round's starting XI by selecting 11 available players
   - Use energy as a secondary sort key (prefer fresher players)

2. **Energy-based substitution:**
   - If a player in starting XI has energy < 20, prefer bench players with higher energy
   - This exercises the recovery lifecycle for substituted-out players

3. **Add lifecycle visibility:**
   - Track which players recovered (injuryRemainingMatches → 0)
   - Track substitution events
   - Track energy before/after substitution

4. **Result:** K4 will show whether injury/energy problems persist with realistic rotation

### Option B: Conservative Constant Tuning (ONLY IF K4 PROVES CONSTANTS ARE WRONG)

Only if K4 shows rotation is enforced but injury rate is still too high:
- Reduce `BASE_INJURY_PROB` from 0.003 to 0.001-0.002
- Re-run K4 harness and compare

### Option C: Skip K4 and Tune Blindly (NOT RECOMMENDED)

Tuning without diagnostic upgrade risks:
- Reducing injury probability when the real problem is call frequency
- Adjusting energy constants when the real problem is lack of rotation enforcement
- Making changes that cancel each other out

**Recommendation: K4 first, then decide on tuning.**

---

## 8. Proposed Next Phases

| Phase | Focus | Description |
|-------|-------|-------------|
| V24D6K4 | Diagnostic Realism Upgrade | Upgrade harness to rotation-aware auto-select, energy-based substitution, lifecycle visibility |
| V24D6K5 | Conservative Tuning (if needed) | Tune BASE_INJURY_PROB or energy constants based on K4 results |
| V24D6K6 | Full Suite + Docs/Status Update | Run full test suite, update V23_SIMULATION_ENGINE_STATUS.md, V24D5_PRODUCTION_INTEGRATION_PLAN.md, V23_ENGINE_EVOLUTION_ROADMAP.md |

---

## 9. Constants Reference (from K1)

| Constant | Current Value | Tunable? | Notes |
|----------|---------------|----------|-------|
| `BASE_INJURY_PROB` | 0.003 | Yes, if K4 proves call frequency = 1/player/match | See §2A |
| `FULL_MATCH_DRAIN` | 12 | Yes, if K4 shows energy still collapses with rotation | See §2C |
| `SUBSTITUTE_DRAIN` | 6 | Yes | See §2C |
| `DEFAULT_RECOVERY_PER_NON_PARTICIPANT` | 8 | Yes | See §2C |
| `YELLOW_CARD_SUSPENSION_THRESHOLD` | 5 | No (discipline not problematic) | |
| Form deltas | +3/+2/+1/0/-1/-2 | No (form seems well-calibrated) | |
| Red card suspension | +1 match | No (0 reds observed) | |

---

*This document is the K3 tuning design. Awaiting K4 diagnostic realism upgrade before any constant changes.*