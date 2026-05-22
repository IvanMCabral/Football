# V24D6K — Career Mutation Balancing Audit

**Status:** V24D6K1 — AUDIT COMPLETE / AWAITING COMMIT
**Branch:** `mvp-1-performance-cleanup`
**Last Updated:** 2026-05-22

---

## Purpose

This audit documents the balancing parameters of all V24 career mutations (injury, fatigue, form, discipline) and identifies recommended target ranges, risk thresholds, and a diagnostic harness for ongoing calibration. It follows the same audit-as-design-freeze pattern as V24D6J1.

---

## Source of Truth

Constants extracted directly from production source on branch `mvp-1-performance-cleanup`:

| File | Constant | Value |
|------|----------|-------|
| `V24InjuryModel.java` | `BASE_INJURY_PROB` | `0.003` |
| `V24InjuryModel.java` | `MIN_INJURY_PROB` | `0.0005` |
| `V24InjuryModel.java` | `MAX_INJURY_PROB` | `0.02` |
| `V24FatigueMutationApplier.java` | `FULL_MATCH_DRAIN` | `12` |
| `V24FatigueMutationApplier.java` | `SUBSTITUTE_DRAIN` | `6` |
| `V24FormMutationApplier.java` | (no named constants — discrete table) | see §3.3 |
| `V24DisciplineMutationApplier.java` | `YELLOW_CARD_SUSPENSION_THRESHOLD` | `5` |

---

## 1. Injury Model — V24InjuryModel

### 1.1 Probability Formula

```
injuryProb = BASE_INJURY_PROB
           + staminaModifier        // +0.008 if stamina < 20, +0.004 if stamina < 40
           + highIntensityModifier   // +0.002 if high-intensity situation
           + playingStyleModifier    // BRUTAL +0.01, ROUGH +0.005, NORMAL 0, CAREFUL -0.003, CAUTIOUS -0.006
clamped to [MIN_INJURY_PROB, MAX_INJURY_PROB]
```

### 1.2 Current Constants

| Constant | Value | Note |
|----------|-------|------|
| `BASE_INJURY_PROB` | 0.003 | Per-player per-match baseline (~0.3%) |
| `MIN_INJURY_PROB` | 0.0005 | Floor after all modifiers |
| `MAX_INJURY_PROB` | 0.02 | Ceiling (~2% — matches Premier League data ceiling) |
| Stamina < 20 modifier | +0.008 | Significant penalty for exhausted players |
| Stamina < 40 modifier | +0.004 | Moderate penalty for tired players |
| High-intensity modifier | +0.002 | Slightly increases risk during critical moments |
| BRUTAL style modifier | +0.01 | |
| ROUGH style modifier | +0.005 | |
| NORMAL style modifier | 0 | |
| CAREFUL style modifier | -0.003 | |
| CAUTIOUS style modifier | -0.006 | |

### 1.3 Injury Duration

Injuries persist for a fixed number of matches (`injuryRemainingMatches`), set at time of injury. No per-tick healing during a match — duration is a post-round decrement via `V24InjuryRecoveryLifecycleApplier` for players who did not participate that round.

### 1.4 CRITICAL — Injury Probability Call Frequency Unknown

**The mathematical rate depends entirely on how many times `V24InjuryModel` is evaluated per player per match. This is currently unknown from source audit alone.**

#### Scenario A — Evaluated once per player per match (per-match roll)

If the model is called exactly once per player at match start:
- 18-player squad × 0.003 = 0.054 expected injuries per squad per match
- 0.054 × 38 matches = **~2.05 expected injuries per squad per season**
- 25-player squad: 0.075 × 38 = **~2.85 per season**

This would be a **LOW** risk profile — very few injuries, low attrition.

#### Scenario B — Evaluated multiple times per player per match (per-action or per-tick)

If the model is called per action type (shot, dribble, pass, tackle) or per tick:
- A player generates ~30-60 actions per match × multiple tick evaluations
- Expected injuries per season could reach 40-100+ depending on call frequency
- **Risk level becomes UNKNOWN without diagnostic measurement**

#### Required Action

A diagnostic harness (V24D6K2) must measure actual injury event output before any constant tuning can be justified. Do not assume Scenario A or Scenario B without source confirmation.

### 1.5 Balancing Assessment

**Current risk level: UNKNOWN — cannot assess until diagnostic harness establishes actual event frequency**

### 1.6 Target Ranges

**ALL TARGET RANGES BELOW ARE PROVISIONAL. Do not use as hard test assertions until diagnostic harness establishes baseline.**

| Metric | Provisional Target | Alert Range | Notes |
|--------|-------------------|-------------|-------|
| Injuries per squad per season | TBD by harness | <1 or >12 | Initial gameplay range; depends on call frequency |
| Injuries per match (avg) | TBD by harness | TBD | Squad-level; varies by style |
| High-risk player (BRUTAL + <20 stamina) hit rate | TBD by harness | TBD | Cap at MAX_INJURY_PROB per call |
| Zero-injury season probability | TBD by harness | TBD | CAUTIOUS squads should occasionally go injury-free |

---

## 2. Fatigue Model — V24FatigueMutationApplier

### 2.1 Drain Rates

| Scenario | Drain | Notes |
|----------|-------|-------|
| Full match (start + complete 90 min) | -12 energy | Severe — full-match starters fatigued |
| Substitute (any participation) | -6 energy | Half drain — partial contribution |
| Did not participate | 0 during match | Receives recovery post-round via `V24EnergyRecoveryLifecycleApplier` |

### 2.2 Recovery Rates

| Scenario | Recovery | Notes |
|----------|----------|-------|
| Non-participating player | +8 per round | Via `V24EnergyRecoveryLifecycleApplier` |
| Participating player (full match) | 0 | Only drain, no in-round recovery |
| Energy floor | 0 | Cannot go negative |
| Energy cap | 100 | Cannot exceed 100 |

### 2.3 Balancing Assessment

**Current risk level: MEDIUM (logical projection — not yet measured)**

- Starting XI (11 players × -12 = -132 total drain per match across squad)
- 7 substitutes on bench: at least some participation likely
- Net per round: -132 (drain) + recovery pool (7 × +8 = +56) = ~-76 net squad energy per round
- Over 38 rounds: -2,888 cumulative energy drain across squad
- If squad average starting energy is 85, players will be at ~10-20 energy by round 20-25 without rotation
- This is intentional: encourages squad rotation and substitution strategy

### 2.4 Target Ranges

**ALL TARGET RANGES BELOW ARE PROVISIONAL.**

| Metric | Provisional Target | Alert Range | Notes |
|--------|-------------------|-------------|-------|
| Starting XI energy at round 15 | ~40-50 (est.) | TBD by harness | Should feel fatigued but not dead |
| Starting XI energy at round 25 | ~15-30 (est.) | TBD by harness | Crisis territory — rotation needed |
| Bench player energy at round 15 | ~60-70 | TBD by harness | Bench should be fresher |
| Energy-drained starter performance | N/A | TBD | Not yet modeled — future feature |
| Recovery per non-participant | +8 | TBD | Tune to keep bench relevant |

---

## 3. Form Model — V24FormMutationApplier

### 3.1 Discrete Delta Table

| Performance Threshold | Delta | Notes |
|-----------------------|-------|-------|
| ≥ 8.0 rating | +3 | Exceptional — significantly improved |
| ≥ 7.0 rating | +2 | Good — moderately improved |
| ≥ 6.5 rating | +1 | Acceptable — slightly improved |
| ≥ 5.5 rating | 0 | Neutral — no change |
| ≥ 5.0 rating | -1 | Poor — slightly declined |
| < 5.0 rating | -2 | Bad — significantly declined |

Clamped to range [1, 99]. Null form defaults to 50 at initialization.

### 3.2 Season Arc Projection

Assuming 38 matches per season, average rating 6.5 (neutral delta = 0):
- A player averaging 7.5 per season (good): +2 per 2-3 matches → +25 to +38 form over season
- A player averaging 5.0 per season (poor): -1 per match → -38 form over season → likely rotation/sell candidate
- Form range [1,99] should accommodate 20+ season arcs without saturation

### 3.3 Balancing Assessment

**Current risk level: LOW**

- Discrete steps are coarse enough to prevent wild swings
- Clamp [1,99] prevents extreme values
- No direct performance effect wired yet (form is stored but not applied to OVR/decision-making in V24D6)
- Risk: form drift accumulation over seasons could make all players max-out or min-out without reversion

### 3.4 Target Ranges

**ALL TARGET RANGES BELOW ARE PROVISIONAL.**

| Metric | Provisional Target | Alert Range | Notes |
|--------|-------------------|-------------|-------|
| Avg form after 10 rounds (neutral player) | ~50 | TBD by harness | Should hover near start |
| Form after 20 rounds (good performer) | +20 to +30 | TBD by harness | Should be noticeable |
| Form after 20 rounds (poor performer) | -20 to -30 | TBD by harness | Should be concerning |
| Season-end form max | 99 | TBD by harness | Top performers near cap |
| Season-end form min | 1 | TBD by harness | Worst performers near floor |
| Null form at initialization | 50 | 50 | Confirmed — no special handling |

---

## 4. Discipline Model — V24DisciplineMutationApplier

### 4.1 Card Rules

| Event | Consequence |
|-------|-------------|
| Yellow card received | `yellowCardCount++` |
| Yellow card count reaches `YELLOW_CARD_SUSPENSION_THRESHOLD` (5) | Player suspended for 1 match, yellow count reset to 0 |
| Direct red card received | Player suspended for 1 match immediately |
| Suspension | `suspensionRemainingMatches` set to 1, decremented post-round |

### 4.2 Recovery Rules

- `V24SuspensionLifecycleApplier` decrements `suspensionRemainingMatches` by 1 post-round
- When it reaches 0, player is eligible to play again
- No mechanism for yellow card count decay between matches (5 yellows = suspension always, regardless of timing)
- No double-suspension for accumulating multiple thresholds in same match (unlikely but not guarded)

### 4.3 Balancing Assessment

**Current risk level: MEDIUM (logical projection — not yet measured)**

- 5 yellows = suspension is aligned with most European top leagues
- No gradual decay means accumulation matters — players with 4 yellows are one bad match from suspension
- Direct reds are harsh: 1-match ban for a red in a match you might already be losing
- Yellow card rate per squad per season is unknown without diagnostic harness

### 4.4 Target Ranges

**ALL TARGET RANGES BELOW ARE PROVISIONAL. Do not use as hard test assertions until diagnostic harness establishes baseline.**

| Metric | Provisional Target | Alert Range | Notes |
|--------|-------------------|-------------|-------|
| Yellow cards per squad per season | TBD by harness | TBD | Squad-level; varies by style |
| Suspensions per squad per season | TBD by harness | TBD | Driven by yellow accumulation + reds |
| Avg yellows before first suspension | TBD by harness | TBD | Should feel earned |
| Red card suspensions per season | TBD by harness | TBD | Less frequent than yellow-based |
| Players with 4 yellows mid-season | TBD by harness | TBD | High-tension group — one match from ban |

---

## 5. Cross-Mutation Interactions

### 5.1 Known Interaction Risks

| Interaction | Risk | Severity |
|-------------|------|----------|
| Injured + suspended | Player unavailable via both flags; both must clear independently | Low — correct behavior |
| Injured + fatigued | Injured player drains 0 energy (correct); recovery still applies | Low — correct behavior |
| Form + injury | No current interaction (form not applied to injury probability) | Medium — future opportunity |
| Form + fatigue | No current interaction (form not applied to energy drain) | Medium — future opportunity |
| Suspensions + fixture rotation | If suspended player has no fixture (bye week), suspension decrements anyway | Low — correct but monitor |
| Energy + injury | Player at 0 energy does not increase injury probability directly | Low — but stamina <20 modifier may kick in |

### 5.2 Missing Interactions (Future Feature Candidates)

See §7.1 for items to be considered only after baseline balancing is established.

---

## 6. Diagnostic Harness Design

### 6.1 Purpose

A dedicated integration test that simulates career seasons with mutation flags enabled, collecting actual mutation event output to establish real baseline distributions. Used for:
- Establishing current injury/suspension/form event rates (before any constant changes)
- Detecting regression in mutation rates after any code changes
- Providing data-driven calibration targets instead of assumptions

### 6.2 Proposed Harness: V24MutationBalancingHarness

```java
public class V24MutationBalancingHarness {
    private int matchesPerSeason = 38;
    private int squadSize = 25;
    private int seasonsToSimulate = 10;

    public List<SeasonMetrics> runSeasons();

    public static class SeasonMetrics {
        int seasonNumber;
        int totalInjuries;
        int totalSuspensions;
        int redCardSuspensions;
        int avgFormChange;
        int playersAtFormCeiling;    // >= 90
        int playersAtFormFloor;       // <= 10
        double avgEnergyOfStarters;   // at round 25
        double avgEnergyOfBench;      // at round 25
    }
}
```

### 6.3 Target Metrics

**WARNING: All target ranges below are PROVISIONAL. Do not use as hard test assertions until at least one diagnostic run establishes current baseline distributions.**

| Metric | Provisional Target | Alert Range | Notes |
|--------|-------------------|-------------|-------|
| Injuries per squad per season | TBD by harness | TBD | First run establishes baseline |
| Suspensions per squad per season | TBD by harness | TBD | First run establishes baseline |
| Red card suspensions per season | TBD by harness | TBD | First run establishes baseline |
| Avg form change per season | TBD by harness | TBD | First run establishes baseline |
| Players at form ceiling (≥90) | TBD by harness | TBD | First run establishes baseline |
| Players at form floor (≤10) | TBD by harness | TBD | First run establishes baseline |
| Avg starter energy at round 25 | TBD by harness | TBD | First run establishes baseline |
| Avg bench energy at round 25 | TBD by harness | TBD | First run establishes baseline |

---

## 7. Recommended Next Phases

### V24D6K2 — Balancing Diagnostic Harness/Test Only

**Priority: HIGHEST — must precede all constant tuning**

- No production behavior changes
- Simulate multiple seasons / many rounds with mutation flags enabled
- Collect actual mutation outputs:
  - injuries per team/season (actual event count, not probability math)
  - unavailable players per round (injury + suspension combined)
  - average squad energy at defined checkpoints (round 10, 20, 30)
  - suspensions per team/season (yellow-based vs red-based split)
  - form distribution at season start, mid-season, end
- Produce report-style output with generous ranges; assertions may be disabled or very wide initially
- Goal: answer "what does the model actually produce?" not "what should it produce"

### V24D6K3 — Constants Tuning Based on Harness Results

**Priority: AFTER K2 baseline established**

- Tune only constants whose actual output deviates from desired gameplay experience
- Do not tune blind — K2 data must justify every change
- Document what changed and why, referencing K2 measurements

### V24D6K4 — Configuration Extraction

**Priority: AFTER K3 tuning decisions**

- Make selected constants configurable (via policy or config file) only after K3 identifies which ones need runtime adjustment
- Not all constants need to be configurable — only those that game designers need to tune without code changes

### V24D6K5 — Docs/Status Update

**Priority: FINAL**

- Update V23_SIMULATION_ENGINE_STATUS.md, V24D5_PRODUCTION_INTEGRATION_PLAN.md, and V23_ENGINE_EVOLUTION_ROADMAP.md with K2-K4 results
- Mark V24D6K complete only after documentation reflects measured state

---

## 7.1 Future Feature Candidates (After Baseline Balancing Established)

The following are legitimate feature ideas but must NOT be taken up before K2-K4 complete the measurement loop. Presenting them as "K2-K5" was premature phase drift.

| Feature | Rationale | Priority After K2-K4 |
|---------|-----------|---------------------|
| Form → Performance wiring | Form stored but not applied; could affect xG or OVR | Medium |
| Fatigue → Injury interaction | High cumulative energy drain could increase injury risk | Medium |
| Energy → Performance degradation | Energy < 20 could reduce shot quality | Medium |
| Injury severity tiers | All injuries equal duration; variety missing | Low |
| Form reversion to mean | Prevent season-end form saturation | Low |
| Yellow card season decay | Prevent carry-over of 4-yellow tension into new season | Low |
| Suspension → Form preservation | Suspended players should not suffer form decay | Low |

---

## 8. Open Questions

1. **Injury severity model**: Should there be varying injury durations (1-5 matches) rather than all injuries equal? Currently all injuries are 1-match minimum.
2. **Form reversion**: Should form naturally drift back toward 50 over time, or is the current discrete-per-match model correct?
3. **Yellow card timing**: Should a player who accumulates 5 yellows in match 38 serve suspension in round 1 of next season? Current model says yes — no off-season reset.
4. **Fixture-less suspension**: A player suspended in a bye week — does the suspension decrement anyway? Currently yes. Should it wait for next actual match?
5. **BRUTAL style cap**: At MAX_INJURY_PROB (2%) per call, if model is called per-action, a high-injury squad could accumulate many injuries per season. Acceptable only if K2 harness measures it.
6. **Injury model call frequency**: How many times per match is `V24InjuryModel` evaluated per player? Once at match start? Per action? Per tick? Source code review or harness measurement required to answer.

---

## 9. Gap Summary

| Gap | Severity | Recommended Phase |
|-----|----------|------------------|
| Unknown injury event frequency | Critical — blocks all tuning | K2 (harness) |
| Unknown actual injury rate per season | Critical — blocks target range setting | K2 (harness) |
| No form → performance wiring | Medium | After K2-K4 |
| No fatigue → injury interaction | Medium | After K2-K4 |
| No energy → performance degradation | Medium | After K2-K4 |
| No injury severity tiers | Low | After K2-K4 |
| No form reversion to mean | Low | After K2-K4 |
| No yellow card season decay | Low | After K2-K4 |

---

## 10. Constants Reference Table

| Constant | Value | File |
|----------|-------|------|
| `BASE_INJURY_PROB` | 0.003 | V24InjuryModel.java |
| `MIN_INJURY_PROB` | 0.0005 | V24InjuryModel.java |
| `MAX_INJURY_PROB` | 0.02 | V24InjuryModel.java |
| Stamina < 20 modifier | +0.008 | V24InjuryModel.java |
| Stamina < 40 modifier | +0.004 | V24InjuryModel.java |
| High-intensity modifier | +0.002 | V24InjuryModel.java |
| BRUTAL style modifier | +0.01 | V24InjuryModel.java |
| ROUGH style modifier | +0.005 | V24InjuryModel.java |
| CAREFUL style modifier | -0.003 | V24InjuryModel.java |
| CAUTIOUS style modifier | -0.006 | V24InjuryModel.java |
| `FULL_MATCH_DRAIN` | 12 | V24FatigueMutationApplier.java |
| `SUBSTITUTE_DRAIN` | 6 | V24FatigueMutationApplier.java |
| Default recovery per non-participant | +8 | V24EnergyRecoveryLifecycleApplier.java |
| Energy floor | 0 | V24FatigueMutationApplier.java |
| Energy cap | 100 | V24FatigueMutationApplier.java |
| Form delta ≥8.0 | +3 | V24FormMutationApplier.java |
| Form delta ≥7.0 | +2 | V24FormMutationApplier.java |
| Form delta ≥6.5 | +1 | V24FormMutationApplier.java |
| Form delta ≥5.5 | 0 | V24FormMutationApplier.java |
| Form delta ≥5.0 | -1 | V24FormMutationApplier.java |
| Form delta <5.0 | -2 | V24FormMutationApplier.java |
| Form clamp range | [1, 99] | V24FormMutationApplier.java |
| Default null form | 50 | V24FormMutationApplier.java |
| `YELLOW_CARD_SUSPENSION_THRESHOLD` | 5 | V24DisciplineMutationApplier.java |
| Red card suspension | +1 match | V24DisciplineMutationApplier.java |
| Yellow threshold suspension | +1 match | V24DisciplineMutationApplier.java |

---

*Audit complete. Awaiting user signal to proceed to K2 or commit K1 document.*

**Final recommendation: Proceed next with V24D6K2 — balancing diagnostic harness/test only. Do not implement new gameplay modifiers (Form→Performance, Fatigue→Injury, Energy→Performance, Season Decay) until K2 establishes measured baseline.**