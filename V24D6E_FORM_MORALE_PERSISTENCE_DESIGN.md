# V24D6E — Form/Morale Persistence Design

**Status:** V24D6E IMPLEMENTATION COMPLETE — E1 (0388a57) + E2 (9c101d1) + E3 (f801299) + E4 (e65cb03) + E5 (this doc) complete
**Branch:** `mvp-1-performance-cleanup`
**Latest implementation commit:** `e65cb03` (V24D6E4 — form mutation integration)
**Latest docs commit:** (this update — V24D6E5)
**Tests:** 651 full suite, 0 failures; focused mutation/lifecycle/form gate 234, 0 failures
**Created:** 2026-05-17
**Updated:** 2026-05-17

---

## 1. Executive Summary

V24D6E targets form persistence — updating `SessionPlayer.form` from V24 match ratings after each match.

**Audit conclusion:** The infrastructure is almost entirely in place. `SessionPlayer.form` exists (Integer, default 50), is exposed in the API DTO, does not affect OVR calculations, and is independent from the team-level `SessionTeam.morale`. `V24CareerMutationPolicy` already has `persistForm` flag and `isFormPersistenceEnabled()`. `V24CareerMutationResult` already has `formApplied`. `V24PlayerRatingModel` produces deterministic [1.0–10.0] ratings for starting XI players from the match timeline. `V24PlayerRatingsAssembler` already assembles per-player ratings after each V24 match.

V24D6E is implemented through E4. V24FormMutationApplier was added in 9c101d1, V24CareerMutationService orchestration was wired in f801299, and LeagueSimulator integration coverage was completed in e65cb03. Form persistence now updates SessionPlayer.form from V24 player ratings behind mutate-career-state + persist-form.

**MVP recommendation:** Use existing `SessionPlayer.form` (Integer, [1,99], default 50), bounded conservative deltas from player match rating only (no team result modifier), clamp to [1, 99], only mutate starting XI players who appeared in the match timeline.

Form does not affect `calculateOverall()` or direct V24 match strength. It is referenced by `V24PlayerSelector`, so V24D6E must treat form as potentially affecting AI/player selection if that selector is used in active flows.

---

## 2. Current Form/Morale State Audit

### 2.1 SessionPlayer Form Field

| Property | Value |
|----------|-------|
| Field | `private Integer form` on `SessionPlayer` |
| Type | `Integer` |
| Default value | `50` (set in `initDefaults()`, line 124) |
| Null-safe getter | `public Integer getForm()` — returns null if not set |
| Setter | `public void setForm(Integer form)` |
| Persisted in CareerSave | Yes — `SessionPlayer` is the Redis entity stored per career |
| API exposure | `SessionPlayerDTO` (record, line 23) via `SessionEntityMapper.toDTO()` |
| Used in OVR calculation | **No** — `calculateOverall()` is purely attribute-based (attack/defense/technique/speed/stamina/mentality weighted by position) |
| Used anywhere in simulation | `V24PlayerSelector.java:131` — used for AI selection logic, not OVR |
| Frontend model | `team.model.ts:51` — `morale: number` on `TeamModel`, not player form |

**Conclusion:** `SessionPlayer.form` is not part of OVR or direct match strength calculation. It is currently referenced by `V24PlayerSelector`, so the MVP should treat form as low-risk but not purely cosmetic. Form mutation may influence AI/player selection behavior if that selector participates in active flows.

### 2.2 Team Morale vs Player Form

| Entity | Field | Type | Default | Used in simulation? |
|--------|-------|------|---------|---------------------|
| `SessionTeam` | `morale` | `Integer` | 40–70 random, or 50 | No — exists but unused in V24 engine |
| `SessionPlayer` | `form` | `Integer` | 50 | No — exists but unused in V24 engine |

**Note:** These are two separate fields. This design targets `SessionPlayer.form` only. `SessionTeam.morale` is out of scope for V24D6E.

### 2.3 Form in OVR / Performance Calculation

`SessionPlayer.calculateOverall()` (lines 137–154):
- Purely attribute-based: attack, defense, technique, speed, stamina, mentality
- Position-dependent weights (GK/DEF/MID/WINGER/ATT)
- Form is **not** a factor

`V24MatchContextFactory` / `V24TeamMatchState`:
- Strength calculation based on OVR and fatigue
- Form is **not** a factor in V24 match simulation

**Conclusion:** Form does not affect `calculateOverall()` or direct V24 match strength. However, it is referenced by `V24PlayerSelector` in AI selection logic. V24D6E should treat form as low-risk for simulation balance but not purely cosmetic — any mutation may influence player selection behavior if that selector is used in active flows. Validate in V24D6E4.

---

## 3. Current Rating and OVR Impact Audit

### 3.1 V24PlayerRatingModel

`V24PlayerRatingModel` computes deterministic ratings [1.0–10.0] from match timeline events:

| Event | Contribution |
|-------|-------------|
| Base rating | 6.0 |
| GOAL as scorer | +0.8 |
| GOAL as assist provider | +0.5 |
| SHOT as shooter | +0.10 per shot |
| SHOT as key-pass provider | +0.30 per key pass |
| SHOT with xG >= 0.30 (shooter) | +0.05 bonus |
| YELLOW_CARD | -0.3 per card |
| RED_CARD | -1.5 per red |
| INJURY | -0.2 per injury |
| FOUL | -0.05 per foul |
| SUBSTITUTION as incoming | +0.05 (appearance bonus) |

**Rating clamping:** [1.0, 10.0]

**Determinism:** Same timeline → same rating. No randomness.

### 3.2 V24PlayerRatingsAssembler

Assembles ratings for **starting XI only** (from `CareerSave.getTeamStarting11()`):
- Home team starters → `V24PlayerMatchState` → `statsModel.computeRatings()`
- Away team starters → `V24PlayerMatchState` → `statsModel.computeRatings()`
- Returns `List<V24PlayerMatchRatingDto>` — one DTO per starting player
- Unused substitutes are **not** included

**Limitation for form mutation:** Only starting XI players get ratings. Substitutes who appeared (substituted-in) also get a +0.05 bonus but may not be in the starting XI set returned by `getTeamStarting11()`. This is a data gap — the assembler only processes starters from the lineup map, not all players who participated.

**V24D6E MVP workaround:** Only mutate players who appear in both the starting XI (from `getTeamStarting11()`) AND have a `V24PlayerMatchRatingDto` entry. This is the safe set of players with confirmed ratings.

### 3.3 V24PlayerMatchRatingDto

Immutable DTO available after each V24 match:
```
playerId, playerName, teamId, position
rating (double, 1.0-10.0)
goals, assists, keyPasses, shots
yellowCards, redCards, injuries, fouls
substitutedIn, substitutedOut
```

---

## 4. Current Mutation Architecture

### 4.1 V24CareerMutationPolicy — Already Has persistForm

```java
// V24CareerMutationPolicy.java — already implemented
private final boolean persistForm;

public boolean isFormPersistenceEnabled() {
    return mutateCareerState && persistForm;
}
```

All mutation appliers follow the same gate pattern: `isXxxPersistenceEnabled()` checks master AND specific flag.

### 4.2 V24CareerMutationResult — Already Has formApplied

```java
// V24CareerMutationResult.java — already implemented
private final int formApplied;  // line 19
public int formApplied() { return formApplied; }  // line 112

// Factory methods support form:
static V24CareerMutationResult success(int injuries, int fatigue, int discipline, int form)
static V24CareerMutationResult partial(int injuries, int fatigue, int discipline, int form, List<String> failures)
```

All existing factory methods have been retrofitted with `form` parameter. **No result changes needed for V24D6E.**

### 4.3 V24CareerMutationService — Form Wired

Current service orchestration:
```java
// V24CareerMutationService.java — form applier wired in E3
if (policy.isInjuryPersistenceEnabled())  → applyInjuries()
if (policy.isFatiguePersistenceEnabled()) → applyFatigue()
if (policy.isDisciplinePersistenceEnabled()) → applyDiscipline()
if (policy.isFormPersistenceEnabled()) → applyForm()
```

V24D6E3 added `V24FormMutationApplier` field, 4-arg constructor, and `applyForm()` call after discipline. No further service changes needed.

### 4.4 V24CareerMutationService Constructors

V24CareerMutationService now includes V24FormMutationApplier in the constructor chain. E3 added the form applier field, 4-arg constructor, and default constructor delegation. Existing constructors remain preserved and delegate to the full constructor with default appliers.

### 4.5 Configuration / LeagueSimulator Wiring

V24FormMutationApplier is created in E2 and wired into V24CareerMutationService in E3. LeagueSimulator already threads persistForm through policy construction, so no LeagueSimulator production change was required.

### 4.6 Production Files Changed (V24D6E2–E4)

| File | Change |
|------|--------|
| `V24FormMutationApplier.java` | **NEW** — pure applier class (E2, commit `9c101d1`) |
| `V24CareerMutationService.java` | Added form applier field + wiring (E3, commit `f801299`) |
| `V24CareerMutationServiceTest.java` | Added form applier tests (E3, +5 tests) |
| `V24FormMutationApplierTest.java` | **NEW** — unit tests (E2, 18 tests) |
| `V24CareerMutationIntegrationTest.java` | Added form integration tests (E4, +5 tests) |
| `V24CareerMutationIntegrationTest.java` | Add form integration tests |

**No changes to:**
- `SessionPlayer.java` — field already exists
- `SessionPlayerDTO.java` — form already exposed
- `SessionEntityMapper.java` — form already mapped
- `V24CareerMutationPolicy.java` — flag already implemented
- `V24CareerMutationResult.java` — formApplied already exists
- `SimulationConfig.java` — persistForm already wired
- `LeagueSimulator.java` — persistForm already threaded
- `V24PlayerRatingModel.java` — no changes
- `V24PlayerRatingsAssembler.java` — no changes
- Redis schema — no new fields

---

## 5. Problem Statement

`SessionPlayer.form` exists but is never updated after a match. Players maintain the default form value (50) indefinitely. This creates:

1. **No visible career consequence** — good performances have no reward, poor performances have no penalty
2. **Stale player conditions** — form reflects nothing about recent match history
3. **Missing progression layer** — injury/fatigue/discipline are persisted but form is not, despite having a field already
4. **Dormant field** — `form` is stored and exposed but never mutated, wasting the existing infrastructure

---

## 6. Rule Options

### 6A. Update Formula — Discrete Step (Recommended for MVP)

| Rating range | Form delta |
|--------------|------------|
| >= 8.0 | +3 |
| >= 7.0 | +2 |
| >= 6.5 | +1 |
| 5.5–6.49 | 0 |
| 5.0–5.49 | -1 |
| < 5.0 | -2 |

**Pros:** Simple, predictable, easy to test, hard to snowball
**Cons:** Coarse — treats 6.5 and 6.9 the same

### 6B. Update Formula — Smooth Linear (Alternative)

```
delta = round((rating - 6.0) * 1.5)
```

| Rating | Raw delta | Clamped |
|--------|-----------|---------|
| 8.0 | +3.0 | +3 |
| 7.5 | +2.25 | +2 |
| 7.0 | +1.5 | +2 |
| 6.5 | +0.75 | +1 |
| 6.0 | 0 | 0 |
| 5.5 | -0.75 | -1 |
| 5.0 | -1.5 | -2 |
| 4.5 | -2.25 | -2 |

**Pros:** Granular, continuous
**Cons:** More complex to reason about, easier to snowball

### 6C. Team Result Modifier

| Team result | Additional form delta |
|-------------|---------------------|
| Win | +1 |
| Draw | 0 |
| Loss | -1 |

**Risk:** Can snowball strong teams (winning teams get better form → win more). Not recommended for MVP.

### 6D. Combined (Rating + Red Card penalty) — NOT SELECTED

- Base delta from rating using discrete step (6A)
- Red card: no extra form penalty in MVP; rating already includes -1.5 penalty and discipline applies suspension separately
- Injury: no additional form penalty beyond rating impact (injury already a consequence)

---

## 7. Recommended MVP Rules

### Field
- Use existing `SessionPlayer.form` (Integer, default 50)
- No new morale/confidence field in MVP

### Scale
- Form stored as Integer, range [1, 99]
- Default: 50 (neutral)
- Clamp after every update: `form = Math.max(1, Math.min(99, form + delta))`

### Update Formula — Discrete Step (6A)
```
if (rating >= 8.0) delta = +3
else if (rating >= 7.0) delta = +2
else if (rating >= 6.5) delta = +1
else if (rating >= 5.5) delta = 0
else if (rating >= 5.0) delta = -1
else delta = -2
```

### Bounds
- Never below 1, never above 99
- Null form on first mutation: treat as 50, apply delta, clamp

### Participation Requirement
- Only mutate players who have a `V24PlayerMatchRatingDto` entry (starting XI with confirmed rating)
- Unused substitutes do not change form (no rating available)
- Injured players who participated still get their rating-based form update

### Red Card Additional Penalty
- Red card: no extra form penalty in MVP. The rating model already applies a red-card penalty (-1.5), and discipline mutation already applies suspension. Avoid double-penalizing the same event.

### No Team Result Modifier
- Player form changes based solely on individual match rating
- No win/draw/loss modifier in MVP
- Rationale: avoids snowball risk; individual performance is sufficient signal

### Interaction with Injury/Fatigue/Discipline
- Form mutation runs independently — no interaction with injury, fatigue, or discipline
- A player who is injured can still gain or lose form from their rating
- Injury already has gameplay consequences; form does not compound it further

### Old Saves
- Null form: treat as 50, apply delta on first mutation
- Existing form values: preserved, only updated on next match
- Clamp only on mutation: old saves with form > 99 clamped to 99 on first mutation

### Frontend/API Impact
- Form is already exposed in `SessionPlayerDTO` via `SessionEntityMapper.toDTO()`
- No new API endpoints required
- No frontend changes in V24D6E MVP (backend-only)
- If form is not displayed in UI, implementation is still valid — backend mutation is complete

---

## 8. Implemented Design

### V24D6E2 — V24FormMutationApplier (implemented in 9c101d1)

```java
package com.footballmanager.application.service.simulation.v24;

/**
 * Applies form updates to SessionPlayer.form from V24 player match ratings.
 *
 * <p>Reads V24PlayerMatchRatingDto list from V24DetailedMatchResult.
 * Mutates SessionPlayer.form field with bounded deltas.
 *
 * <p>Rules (V24D6E MVP):
 * - Discrete step: >=8.0 → +3, >=7.0 → +2, >=6.5 → +1, >=5.5 → 0, >=5.0 → -1, <5.0 → -2
 * - Clamp: [1, 99]
 * - Red card: no extra penalty (rating already reflects red-card penalty; suspension handled separately by discipline)
 * - Null form: treat as 50
 * - Starting XI players only (from V24PlayerRatingsAssembler output)
 */
public final class V24FormMutationApplier {

    public int applyForm(CareerSave career, V24DetailedMatchResult result, V24CareerMutationPolicy policy) {
        if (career == null || result == null || policy == null) return 0;
        if (!policy.isFormPersistenceEnabled()) return 0;

        List<V24PlayerMatchRatingDto> ratings = getPlayerRatings(result);
        if (ratings == null || ratings.isEmpty()) return 0;

        int count = 0;
        for (V24PlayerMatchRatingDto dto : ratings) {
            String playerId = dto.playerId();
            SessionPlayer player = career.getPlayerManager().getSessionPlayer(playerId);
            if (player == null) continue;  // unknown player skip

            double rating = dto.rating();
            int delta = computeDelta(rating);

            // Red-card impact is already reflected in dto.rating(); do not apply an additional red-card form penalty in MVP.

            // Apply with clamp
            Integer current = player.getForm();
            int base = (current != null) ? current : 50;
            int updated = Math.max(1, Math.min(99, base + delta));
            player.setForm(updated);
            count++;
        }
        return count;
    }

    int computeDelta(double rating) {
        if (rating >= 8.0) return 3;
        if (rating >= 7.0) return 2;
        if (rating >= 6.5) return 1;
        if (rating >= 5.5) return 0;
        if (rating >= 5.0) return -1;
        return -2;
    }

    List<V24PlayerMatchRatingDto> getPlayerRatings(V24DetailedMatchResult result) {
        // V24DetailedMatchResult has getPlayerRatings() or similar accessor
        // Fall back to V24PlayerRatingsAssembler if needed
    }
}
```

**Key design decisions:**
- Pure function style — no mutable state, no external I/O
- Returns count of players mutated (for `formApplied` in result)
- Null player skip: continue silently (same as injury/fatigue discipline skip)
- Delta computation is package-visible for unit testing

### V24D6E3 — Service Wiring (implemented in f801299)

```java
private final V24FormMutationApplier formMutationApplier;

public V24CareerMutationService(...) {
    // existing constructors updated to accept form applier
}

public V24CareerMutationResult applyMutations(...) {
    // existing mutation calls...

    int form = 0;
    if (policy.isFormPersistenceEnabled()) {
        try {
            form = formMutationApplier.applyForm(career, result, policy);
        } catch (Exception e) {
            failures.add("Form mutation failed: " + e.getMessage());
        }
    }

    // update result factory call to include form
    return V24CareerMutationResult.success(injuries, fatigue, discipline, form);
}
```

### V24D6E4 — LeagueSimulator Integration (integration coverage completed in e65cb03)

No LeagueSimulator production code change was required. `persistForm` parameter already threaded through policy construction and service constructor. The wiring was already done in LeagueSimulator — only the applier class and service field were missing (added in E2/E3).

---

## 9. API/DTO/Frontend Impact

### Backend
- **No new API endpoints** — form already in `SessionPlayerDTO`
- **No Redis schema changes** — `SessionPlayer.form` already a field
- **No new DTOs** — `V24PlayerMatchRatingDto.rating()` already available
- **No configuration changes** — `persistForm` already wired

### Frontend
- **No frontend changes in E1** — backend-only MVP
- Form is exposed in squad endpoint if frontend consumes it
- If frontend displays form, mutation is immediately visible after next match
- If frontend does not display form, mutation is still valid (backend complete, UI later)

### Data Flow
```
V24DetailedMatchResult
  └── V24PlayerRatingsAssembler.assemblePlayerRatings()
        └── List<V24PlayerMatchRatingDto> (rating per starting XI player)
              └── V24FormMutationApplier.applyForm()
                    └── SessionPlayer.form updated
                          └── SessionPlayerDTO (via existing mapper)
```

---

## 10. Testing Plan

### V24FormMutationApplierTest

| Test | Scenario |
|------|----------|
| `nullCareer_returnsZero` | career=null → 0 |
| `nullResult_returnsZero` | result=null → 0 |
| `nullPolicy_returnsZero` | policy=null → 0 |
| `disabledPolicy_noMutation` | persistForm=false → 0, no form change |
| `noRatings_noMutation` | result with empty ratings → 0 |
| `unknownPlayer_skipped` | playerId not in career → skip, count 0 |
| `ratingExcellent_increasesForm` | rating 9.0 → delta +3 |
| `ratingGood_increasesForm` | rating 7.5 → delta +2 |
| `ratingAcceptable_increasesForm` | rating 6.7 → delta +1 |
| `ratingNeutral_noChange` | rating 6.0 → delta 0 |
| `ratingPoor_decreasesForm` | rating 5.2 → delta -1 |
| `ratingVeryPoor_decreasesForm` | rating 4.5 → delta -2 |
| `formClampedAtMax` | form=98, delta +3 → clamped to 99 |
| `formClampedAtMin` | form=2, delta -2 → clamped to 1 |
| `nullExistingForm_defaultsNeutral` | form=null, rating 7.0 → set to 52 |
| `redCard_noExtraPenaltyBeyondRating` | rating 6.5, redCards=1 → delta computed from rating only, no additional -1 |
| `multiplePlayers_independentChanges` | 3 players, different ratings → independent updates |

### V24CareerMutationServiceTest additions

| Test | Scenario |
|------|----------|
| `persistFormEnabled_callsFormApplier` | policy with form=true → formApplied > 0 |
| `persistFormDisabled_doesNotCallFormApplier` | policy with form=false → formApplied = 0 |
| `masterFalse_persistFormTrue_doesNotCallFormApplier` | mutate=false, form=true → formApplied = 0 |
| `formFailure_doesNotEraseInjuryFatigueDisciplineSuccess` | form throws → partialFailure, others preserved |
| `allMutationFlagsEnabled_includesForm` | all flags true → formApplied counted |

### V24CareerMutationIntegrationTest additions

| Test | Scenario |
|------|----------|
| `persistFormEnabled_appliesFormMutation` | full pipeline, form flag on → form updated |
| `persistFormRequiresMasterGate` | master off, form on → no form mutation |
| `persistFormSpecificFlagFalse_noFormMutation` | master on, form off → no form mutation |
| `v24DisabledWithFormFlags_noMutation` | V24 off, all flags on → no form mutation |
| `defaultPathNoFormMutation` | default (non-V24) path → no form mutation |
| `formIndependentFromInjuryFatigueDiscipline` | all mutations enabled → form updates independent |

---

## 11. Risks and Mitigations

### Risk 1: Form Snowball (Winning Teams Get Stronger)

**Description:** If winning teams get +1 form bonus per win, over a season they accumulate significantly higher form than losing teams, creating runaway strength advantage.

**Mitigation:** MVP uses no team-result modifier. Only individual match rating drives form. Win bonus not applied.

### Risk 2: Form Death Spiral (Low-Rated Players Get Worse)

**Description:** Players with consistently low ratings (e.g., rating 4.5 → -2 per match) spiral from 50 to very low values quickly, compounding poor performance.

**Mitigation:** Clamp [1, 99] prevents hitting 0. But a death spiral to 1 is still possible. Monitor in validation. Consider floor bias (e.g., minimum -1 per match) if death spiral observed.

### Risk 3: Overreaction to One Match

**Description:** A single excellent (9.0) or terrible (3.0) match causes a large form swing (+3 or -2). Overreaction to single events.

**Mitigation:** Conservative deltas (+3/-2 max) prevent extreme swings. Form changes accumulate gradually over season.

### Risk 4: Form Affects AI/Player Selection Before UI Is Ready

**Description:** Form does not affect `calculateOverall()` or direct V24 match strength, but it is referenced by `V24PlayerSelector`. If that selector participates in active lineup/AI flows, form mutation may influence selection behavior before users understand why.

**Mitigation:** Form is low-risk for simulation balance. Accept the potential selection influence as an MVP trade-off. Validate in V24D6E4 that form mutation does not produce unexpected lineup/selection anomalies.

### Risk 5: Rating Model Calibration Affects Balance

**Description:** If most ratings cluster around 6.0–6.5, form deltas are small and the system has little effect. If ratings skew high (many 8.0+), form keeps rising.

**Mitigation:** V24PlayerRatingModel is calibrated in V24D4/V24D5. Validation run after E4 will show rating distribution. Adjust thresholds if needed.

### Risk 6: Old Saves with Null/Unbounded Form

**Description:** Old career saves may have null form or form values outside [1, 99].

**Mitigation:** Null treated as 50 on first mutation. Clamp applied on first mutation. No retroactive clamp without mutation.

### Risk 7: Starting XI Only — Bench Players Unaffected

**Description:** Unused substitutes never get form updates. If a substitute plays well in one match but doesn't start the next, form doesn't reflect their performance.

**Mitigation:** Accepted limitation of V24D6E MVP. `V24PlayerRatingsAssembler` only processes starting XI. Could be extended in a future phase to include substituted-in players.

---

## 12. Non-Goals

V24D6E (overall) does NOT include:
- **No morale field** — using existing `SessionPlayer.form`, no new morale/confidence field
- **No team chemistry** — `SessionTeam.morale` is separate and out of scope
- **No player personality/professionalism** — not in SessionPlayer model
- **No training system** — form changes only from match ratings
- **No contract/happiness system** — not modeled
- **No new API endpoints** — form already in existing DTOs
- **No frontend changes in E1** — backend-only MVP
- **No Redis/schema migration** — form field already exists
- **No changes to V24PlayerRatingModel** — rating model unchanged
- **No production code in E1** — design/audit only

---

## 13. Proposed Implementation Phases

| Phase | Content | Deliverable | Status |
|-------|---------|-------------|--------|
| **V24D6E1** | This design document | `V24D6E_FORM_MORALE_PERSISTENCE_DESIGN.md` | DONE (`0388a57`) |
| **V24D6E2** | V24FormMutationApplier + unit tests | New applier class + 18 tests | DONE (`9c101d1`) |
| **V24D6E3** | V24CareerMutationService orchestration + service tests | Service update + 5 tests | DONE (`f801299`) |
| **V24D6E4** | LeagueSimulator integration + full suite validation | Integration tests + 651 tests | DONE (`e65cb03`) |
| **V24D6E5** | Documentation update | All docs updated | DONE (this doc) |

---

## 14. Completion Criteria

- [x] V24D6E1 design document created and approved (`0388a57`)
- [x] V24D6E2 V24FormMutationApplier implemented with discrete-step delta formula (`9c101d1`)
- [x] V24FormMutationApplier unit tests pass (18 tests, all scenarios)
- [x] V24D6E3 V24CareerMutationService updated with form applier wiring (`f801299`)
- [x] V24CareerMutationServiceTest additions pass (58 tests total, 0 failures)
- [x] V24D6E4 LeagueSimulator integration tests pass
- [x] V24CareerMutationIntegrationTest additions pass (44 tests total, 0 failures)
- [x] Full suite: 651 tests, 0 failures
- [x] No form effect on calculateOverall() or direct V24 match strength; V24PlayerSelector influence acceptable for MVP and validated in E4
- [x] persistForm flag wired: mutate-career-state + persist-form both required
- [x] Form mutation independent from injury/fatigue/discipline
- [x] V24D6E5 docs update committed (this update — `e65cb03`)
- [x] No frontend/API/schema changes
- [x] target/dist not staged

**Remaining deferred (out of V24D6E scope):**
- Optional frontend form display/polish
- Optional future inclusion of substituted-in players if ratings assembler expands
- Injury recovery lifecycle
- Advanced/competition-specific discipline rules

---

## Appendix A: Audit Evidence

### A.1 SessionPlayer.form

```java
// SessionPlayer.java line 36
private Integer form;

// initDefaults() line 124
this.form = 50;

// getter line 181
public Integer getForm() { return form; }

// setter line 208
public void setForm(Integer form) { this.form = form; }
```

### A.2 SessionPlayerDTO.form exposure

```java
// SessionPlayerDTO.java line 23
record SessionPlayerDTO(
    ...
    Integer form,   // line 23
    ...
) {}

// SessionEntityMapper.toDTO() line 44
player.getForm(),  // mapped in toDTO()
```

### A.3 Form not in OVR

```java
// SessionPlayer.calculateOverall() lines 137-154
// Purely: attack, defense, technique, speed, stamina, mentality
// No form reference in entire method
```

### A.4 V24CareerMutationPolicy already has persistForm

```java
// V24CareerMutationPolicy.java lines 23, 30, 35, 70-72, 80
private final boolean persistForm;
isFormPersistenceEnabled() { return mutateCareerState && persistForm; }
```

### A.5 V24CareerMutationResult already has formApplied

```java
// V24CareerMutationResult.java lines 19, 27, 109-112
private final int formApplied;
public int formApplied() { return formApplied; }
```

### A.6 persistForm already in Configuration

```java
// SimulationConfig.java lines 53-54
@Value("${app.simulation.v24.persist-form:false}")
private boolean persistForm;
```

---

*This document is the authoritative V24D6E design specification. V24D6E1 is complete. V24D6E2–E4 are implementation phases to follow after approval.*