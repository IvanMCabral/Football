# V24D6K5 — Tuning Decision Record

**Status:** V24D6K5 COMPLETE — committed at `2bf30d8`. K6/K7 subsequently completed; K8 docs/status close pending this update. No tuning applied.
**Branch:** `mvp-1-performance-cleanup`
**Date:** 2026-05-22
**Based on:** K2 diagnostic (957ce7f), K4 rotation-aware diagnostic (cd48379), K3 tuning design (445e550)

---

## Purpose

This is a decision record, not a code change or tuning commit. It documents what was decided after K4 diagnostic results were available, and why no production constants are being changed at this time.

---

## 1. Executive Decision

**DO NOT TUNE V24 career mutation constants at this time.**

Decision: Hold all mutation constants at current values. Do not modify `BASE_INJURY_PROB`, `FULL_MATCH_DRAIN`, `SUBSTITUTE_DRAIN`, `DEFAULT_RECOVERY_PER_NON_PARTICIPANT`, `YELLOW_CARD_SUSPENSION_THRESHOLD`, or form deltas.

Rationale: The diagnostic evidence is insufficient to justify production changes. The current diagnostic uses a 22-player synthetic squad running 50 consecutive matches without a realistic season shape (no league fixtures, no opponent variation, no tactical substitution triggers beyond energy-based rotation). Before any constant tuning, a season-shaped diagnostic must confirm the same patterns persist under realistic conditions.

---

## 2. Evidence Comparison

### 2.1 Key Metrics Comparison

| Metric | FixedXI Diagnostic (K2) | Rotation-Aware Diagnostic (K4) | K3 Provisional Target | Assessment |
|--------|------------------------|------------------------------|-----------------------|------------|
| Newly injured (50 matches) | 22 | 37 | 3–8 / season | Exceeds target; diagnostic shape may be the cause |
| Unique unavailable at round 50 | 22/22 (100%) | 11/22 (50%) | <12 / season | Better with rotation; still above provisional |
| Avg energy at round 10 | 10.5 | 15.8 | 55–80 | Rotation helps but far from target |
| Energy at round 50 | — | 15.8 | 45–75 | Sustained gap remains |
| Recoveries triggered | 0 | 26 | TBD | Recovery IS working correctly |
| Form avg at end | 55.7 | 60.8 | 45–65 | Within provisional range |
| Yellow cards | 42 | — | 3–10 / season | Insufficient data |
| Suspensions | 2 | — | 3–10 / season | Insufficient data |

### 2.2 What the Comparison Shows

**Recovery lifecycle is functioning correctly.** The rotation-aware diagnostic shows 26 injury recoveries over 50 matches. Injured players who sit out a round do recover. This rules out a bug in the recovery applier.

**Injury pressure is real but shape-dependent.** FixedXI (no rotation) produced 22 injuries over 50 matches. Rotation-aware produced 37 injuries because rotation exposes more players to match participation, which increases total injury opportunities. The 22 injuries in fixedXI undercount actual injury events because the same 11 players keep getting injured repeatedly (compounding without recovery).

**Energy drain is severe regardless of rotation.** Starting XI players drain -12/round. Even with rotation swapping exhausted starters, average energy plateaus around 15.8. This suggests drain is slightly faster than recovery can compensate at the squad level, not that recovery is broken.

**Form drift is moderate and acceptable.** Average form rose from 50 to 60.8 over 50 matches. This is within the K3 provisional target range of 45–65. Max form was 65, well below the saturation threshold of 80. Form is not a concern at this time.

---

## 3. Decision Per Domain

### 3.1 Injury — NO TUNING

**Constant:** `BASE_INJURY_PROB = 0.003`

**Decision:** Hold. Do not reduce.

**Reasoning:** The elevated injury count (37 over 50 matches in rotation-aware) is plausibly explained by the diagnostic's 22-player squad playing 50 consecutive matches without league structure. In a real season, a squad would face bye weeks, Europa/Champions League fixtures, cup matches, and pre-season. The diagnostic runs 50 meaningful matches consecutively with the same squad of 22 — it is an accelerated stress test, not a season analog. Reducing `BASE_INJURY_PROB` based on this shape would risk under-injuring in real gameplay.

**Required evidence before tuning:** Season-shaped diagnostic showing >8 unique unavailable players at any round in a 38-match league season with realistic fixture distribution.

### 3.2 Energy — NO TUNING

**Constants:** `FULL_MATCH_DRAIN = 12`, `SUBSTITUTE_DRAIN = 6`, `DEFAULT_RECOVERY_PER_NON_PARTICIPANT = 8`

**Decision:** Hold. Do not adjust drain or recovery.

**Reasoning:** Average energy of 15.8 at round 10 in the rotation-aware diagnostic is well below the K3 provisional target of 55–80. However, the diagnostic applies energy-based rotation only when a starter falls below 30 energy and a bench player is at least 15 energy higher. This is a simple heuristic. Real gameplay may have more nuanced substitution triggers (tactical, injury, fatigue state). The low average may reflect the diagnostic's limited substitution model, not a constant calibration problem.

**Required evidence before tuning:** Season-shaped diagnostic with realistic substitution triggers (injury, tactical, fatigue) showing sustained energy collapse below 40 average across the squad at round 15.

### 3.3 Discipline — NO TUNING

**Constant:** `YELLOW_CARD_SUSPENSION_THRESHOLD = 5`

**Decision:** Hold. Do not adjust.

**Reasoning:** K2 diagnostic showed 42 yellows over 50 matches (0.84/match per squad) and 2 suspensions. This is below the K3 provisional target of 3–10 suspensions per season. Discipline is not causing problems. Reducing the threshold would make suspensions more frequent without gameplay justification.

**Required evidence before tuning:** Season-shaped diagnostic showing suspension rates consistently below 3 per 38-match season.

### 3.4 Form — NO TUNING

**Constants:** Discrete delta table (+3/+2/+1/0/-1/-2), clamp [1,99]

**Decision:** Hold. No changes to form deltas or clamp range.

**Reasoning:** Form average of 60.8 after 50 matches is within the provisional target range of 45–65. Max form was 65, well below the saturation threshold of 80. Form movement is moderate and self-regulating via the discrete delta table.

**Required evidence before tuning:** Season-shaped diagnostic showing form saturation (>80 average or <20 average) at season end across a 38-match season.

---

## 4. Why K5 Is a Decision Record, Not a Tuning Commit

K5 completes the measurement loop that started at K1. The sequence is:

```
K1 (audit) → K2 (diagnostic) → K3 (tuning design) → K4 (diagnostic upgrade) → K5 (decision)
```

K5's output is a documented decision not to change constants. This is a valid and intentional outcome. The alternative — tuning constants based on insufficient diagnostic evidence — risks miscalibration in the opposite direction.

The diagnostic evidence collected in K2 and K4 is valuable: it confirms recovery works, confirms rotation helps, and establishes baseline event rates under stress-test conditions. But it is not sufficient to justify production changes because the diagnostic shape (50 consecutive matches, fixed 22-player squad, simplified substitution) does not match the shape of a real season.

---

## 5. Recommended Next Phase: V24D6K6

### 5.1 Season-Shaped Diagnostic Harness

**Priority:** HIGH — required before any tuning decision can be revisited

A season-shaped diagnostic must be built before constants can be tuned. The K4 diagnostic was a 22-player squad running 50 consecutive matches. A season-shaped diagnostic should:

1. **Simulate a 38-match league season** with a full 20-team league
2. **Use realistic squad sizes** (25 players, not 22)
3. **Apply realistic fixture distribution** (home/away, European competition slots, cup runs)
4. **Use full auto-select with injury exclusion** (J3 `isPlayerAvailable()`)
5. **Apply energy-based rotation** (energy < 30 triggers substitution if fresher bench available)
6. **Track all K3 provisional target metrics** at round 10, 20, 30, 38
7. **Distinguish injury type** if severity tiers are implemented before K6

### 5.2 K6 Deliverables

- Season-shaped diagnostic test (K6 test commit)
- Updated metrics comparison table
- Decision record: which constants (if any) need tuning based on season-shaped evidence
- If tuning needed: specific constant change proposal with expected effect and rollback criteria

### 5.3 K6 Non-Deliverables

- No production code changes in src/main
- No frontend changes
- No package/config changes
- No commit to main/master until K6 results are reviewed

---

## 6. Acceptance Criteria Before Tuning

The following conditions must ALL be met before any V24 career mutation constant is changed in production:

| # | Criterion | Current Status | How to Verify |
|---|-----------|---------------|---------------|
| AC1 | Season-shaped diagnostic completed (38-match league, realistic squad) | NOT MET | K6 harness run |
| AC2 | Unique unavailable due to injury sustained >12 at any round in real-season shape | NOT MET | K6 harness run |
| AC3 | Avg squad energy <45 at round 15 in real-season shape | NOT MET | K6 harness run |
| AC4 | Avg squad energy <35 at round 25 in real-season shape | NOT MET | K6 harness run |
| AC5 | Suspension rate consistently <3 per 38-match season | UNKNOWN | K6 harness run |
| AC6 | Form saturation (>80 avg or <20 avg) observed at season end | NOT MET | K6 harness run |
| AC7 | Recovery IS triggering for injured non-participants (confirmed in K4) | MET | K4 diagnostic |

Only after ALL of AC1–AC7 are evaluated with season-shaped diagnostic data can a tuning decision be made. If AC2–AC4 are still problematic after K6, that is the evidence base for a tuning proposal. If AC5–AC6 show saturation, that is separate evidence. Each constant change must be justified by specific AC failures.

---

## 7. Risks and Open Questions

| Risk | Severity | Mitigation |
|------|----------|------------|
| Constants are genuinely wrong but K6 diagnostic still doesn't reveal it | Medium | If K6 shows acceptable ranges but production feels wrong, add player-facing telemetry before tuning |
| K6 diagnostic shape still doesn't match real gameplay | High | Review with user before proceeding; may need actual user career save data |
| Injury model call frequency is still >1 per player per match | Unknown | Add call-frequency logging to K6 harness |
| Energy recovery too slow for real season with tactical substitution | Medium | K6 must track starter/bench split and substitution events per round |

| Open Question | Priority | Required Action |
|---------------|----------|----------------|
| Does real gameplay produce the same injury rate as K6 harness? | HIGH | Compare K6 output against actual user career save data if available |
| Is 50 consecutive matches a valid stress test, or does it over-injure? | MEDIUM | K6 with realistic fixture distribution answers this |
| Should there be a per-team per-match injury cap? | MEDIUM | Defer to K6 results; if mass-injury rounds occur, cap is justified |
| Does fatigue affect injury probability? | LOW | Not modeled in V24; defer to future phase |

---

## 8. Constants Reference (Current — No Changes)

| Constant | Value | Status |
|----------|-------|--------|
| `BASE_INJURY_PROB` | 0.003 | NO CHANGE |
| `MIN_INJURY_PROB` | 0.0005 | NO CHANGE |
| `MAX_INJURY_PROB` | 0.02 | NO CHANGE |
| `FULL_MATCH_DRAIN` | 12 | NO CHANGE |
| `SUBSTITUTE_DRAIN` | 6 | NO CHANGE |
| `DEFAULT_RECOVERY_PER_NON_PARTICIPANT` | 8 | NO CHANGE |
| `YELLOW_CARD_SUSPENSION_THRESHOLD` | 5 | NO CHANGE |
| Form deltas | +3/+2/+1/0/-1/-2 | NO CHANGE |
| Form clamp | [1, 99] | NO CHANGE |

---

*V24D6K5 decision record complete. Awaiting user signal to proceed to K6 or commit K5 document.*