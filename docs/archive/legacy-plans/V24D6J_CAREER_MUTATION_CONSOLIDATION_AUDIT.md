# V24D6J — Career Mutation Consolidation Audit

**Purpose:** Consolidate and verify the complete V24D6 career mutation pipeline end-to-end, identify any gaps or risks, and recommend next implementation phase.
**Branch:** `mvp-1-performance-cleanup`
**Status:** V24D6J — VALIDATION COMPLETE
**Created:** 2026-05-22
**Latest implementation commit:** `cb4574e` (V24D6J5 — energy recovery lifecycle wiring)
**Latest docs commit:** pending — V24D6J6 docs/status update
**Tests:** 716 full suite, 0 failures

---

## Executive Summary

V24D6 career mutation system spans injury persistence, fatigue persistence, discipline persistence, suspension lifecycle, yellow-card threshold, form persistence, and injury recovery lifecycle. All components are implemented, tested, and wired behind default-false flags. The system is architecturally consistent with no conflicting interactions identified. No schema changes were required. The V23/default path is completely unaffected when V24 mutation flags are disabled.

**Key finding:** The V24D6 mutation pipeline is complete and internally consistent. Energy recovery and injured lineup blocking (V24D6J3-J5) have been implemented since this audit. The primary remaining work is frontend visibility polish, balancing audit, and advanced optional features.

---

## 1. Current V24D6 Mutation Pipeline End-to-End

### 1.1 Entry Point — LeagueSimulator.simulateLeagueRound()

```
simulateLeagueRound(career, round):
  1. Create V24RoundMutationTracking (lines 170-171)
  2. Capture pre-round injured: preRoundInjured = capturePreRoundInjuredPlayerIds(career) (line 174)
  3. For each fixture in round:
     a. If useV24DetailedEngine:
        - simulateWithV24Engine(career, fixture, ..., tracking) → V24DetailedMatchResult
        - collectStartingXIParticipation(context, tracking)
        - collectV24ResultParticipation(v24Result, tracking)
        - applyV24CareerMutation(career, v24Result, tracking)  ← per-match mutation
  4. applyV24SuspensionLifecycle(career, round, allFixtures, tracking)   ← post-round lifecycle
  5. applyV24InjuryRecoveryLifecycle(career, round, allFixtures, tracking, preRoundInjured)  ← post-round lifecycle
```

**V24 path only:** Mutation only occurs when `use-v24-detailed-engine=true`. V23 and default paths never touch mutation code.

### 1.2 Per-Match Mutation — applyV24CareerMutation()

For each V24 fixture result:

```
applyV24CareerMutation(career, v24Result, tracking):
  1. Capture pre-mutation suspended: preMutationSuspended = capturePreRoundSuspendedPlayerIds(career)
  2. v24MutationService.applyMutations(career, v24Result, v24MutationPolicy)
     → applies INJURY (V24D6B), FATIGUE (V24D6C), CARDS (V24D6D5), FORM (V24D6E)
  3. Snapshot comparison: postMutationSuspended - preMutationSuspended → tracking.newlySuspendedPlayerIds
  4. Snapshot comparison: postMutationInjured - preMutationInjured → tracking.newlyInjuredPlayerIds
```

**Mutation is best-effort:** Exceptions are logged and do not fail the round.

### 1.3 Lifecycle Ordering (Post-Fixture Loop)

After ALL fixtures in the round are processed:

```
applyV24SuspensionLifecycle():
  - Only runs if tracking.v24RoundProcessed=true AND disciplinePersistenceEnabled=true
  - Capture pre-round suspended (preRoundSuspended)
  - Call v24SuspensionLifecycleApplier.applyServedSuspensions(..., preRoundSuspended, tracking.newlySuspendedPlayerIds, tracking.participatedPlayerIds, ...)

applyV24InjuryRecoveryLifecycle():
  - Only runs if tracking.v24RoundProcessed=true AND injuryPersistenceEnabled=true
  - Call v24InjuryRecoveryLifecycleApplier.applyRecovery(..., preRoundInjured, tracking.newlyInjuredPlayerIds, tracking.participatedPlayerIds, ...)
```

**Order:** Suspension lifecycle → Injury recovery lifecycle. Both run exactly once per `simulateLeagueRound` call.

### 1.4 V24RoundMutationTracking (inner class)

```java
private static class V24RoundMutationTracking {
    final Set<String> newlySuspendedPlayerIds = new HashSet<>();
    final Set<String> newlyInjuredPlayerIds = new HashSet<>();
    final Set<String> participatedPlayerIds = new HashSet<>();
    boolean v24RoundProcessed = false;
}
```

Participation is collected from:
- Starting XI for both teams (`collectStartingXIParticipation`)
- All player IDs referenced in V24 timeline events (`collectV24ResultParticipation`)

---

## 2. Feature Flags

### 2.1 Flag Hierarchy

| Flag | Default | Scope | Effect when true |
|------|---------|-------|-----------------|
| `use-v24-detailed-engine` | `false` | Per-simulator instance (Spring bean) | Routes simulation to V24DetailedMatchEngine |
| `mutate-career-state` | `false` | Per-simulator instance | Master gate for ALL career state mutation |
| `persist-injuries` | `false` | Per-simulator instance | Enables INJURY event → SessionPlayer mutation + injury recovery lifecycle |
| `persist-fatigue` | `false` | Per-simulator instance | Enables FATIGUE event → SessionPlayer.energy drain |
| `persist-discipline` | `false` | Per-simulator instance | Enables CARD events → SessionPlayer.yellowCards/redCards/suspended + suspension lifecycle |
| `persist-form` | `false` | Per-simulator instance | Enables V24 player rating → SessionPlayer.form persistence |

**All flags default false.** Normal production behavior is no mutation.

### 2.2 V24CareerMutationPolicy Gate Logic

```java
isCareerMutationEnabled()     = mutateCareerState
isInjuryPersistenceEnabled()   = mutateCareerState AND persistInjuries
isFatiguePersistenceEnabled() = mutateCareerState AND persistFatigue
isDisciplinePersistenceEnabled() = mutateCareerState AND persistDiscipline
isFormPersistenceEnabled()    = mutateCareerState AND persistForm
```

**All individual effects require BOTH the master gate AND their specific flag.**

### 2.3 V24 Path vs. V23/Default

| Engine | mutate-career-state | persist-* | Career mutated? |
|--------|---------------------|-----------|-----------------|
| V24DetailedEngine | true | true | Yes |
| V24DetailedEngine | true | false | No |
| V24DetailedEngine | false | true | No (master gate off) |
| V24DetailedEngine | false | false | No |
| V23 Engine | any | any | No — V23 has no mutation |
| Default | any | any | No |

**V23 and default paths are completely unaffected by V24 mutation flags.**

---

## 3. Lifecycle Interactions

### 3.1 Pre-Round Capture

**Pre-round suspended capture** (`capturePreRoundSuspendedPlayerIds`):
- Iterates all session teams and squad player IDs
- Includes player if `suspended=true` AND `suspensionRemainingMatches > 0`
- Used by suspension lifecycle to determine who is eligible to serve

**Pre-round injured capture** (`capturePreRoundInjuredPlayerIds`):
- Iterates all session teams and squad player IDs
- Includes player if `injured=true` AND `injuryRemainingMatches > 0`
- Used by injury recovery lifecycle to determine who is eligible for recovery

Both captures are done **before** the fixture loop starts.

### 3.2 Per-Match Mutation (applyV24CareerMutation)

| Effect | Applier | SessionPlayer fields | Notes |
|--------|---------|----------------------|-------|
| INJURY event | V24InjuryMutationApplier | `injured=true`, `injuryType="MATCH_INJURY"`, `injuryRemainingMatches=2` | Does NOT overwrite existing injury |
| FATIGUE event | V24FatigueMutationApplier | `energy` decremented by match drain | Energy drains per V24PlayerMatchState match-local |
| CARD events | V24DisciplineMutationApplier | `yellowCards++`, `redCards++`, `suspended=true` (RED), `suspensionRemainingMatches=1` | Includes yellow-threshold suspension via V24D6H |
| FORM | V24FormMutationApplier | `form` updated from V24 player rating | Clamped [1, 99] |

### 3.3 Newly Suspended/Injured Tracking (Snapshot Comparison)

After `applyV24CareerMutation` for each fixture:
```
newlySuspendedPlayerIds = postMutationSuspended - preMutationSuspended
newlyInjuredPlayerIds = postMutationInjured - preMutationInjured
```

**Why snapshot comparison:**
- RED_CARD events set `suspended=true` in the same mutation call
- Yellow-threshold events set `suspended=true` in the same mutation call
- INJURY events set `injured=true` in the same mutation call
- These newly suspended/injured players must NOT have their lifecycle (suspension served / injury recovery) applied in the same round

### 3.4 Suspension Lifecycle (V24D6D6A/B)

**Trigger:** `applyV24SuspensionLifecycle` after fixture loop

**Eligibility rules for serving a suspension:**
1. Player was suspended (`suspended=true`) BEFORE the round started
2. `suspensionRemainingMatches > 0`
3. Player's team had an eligible fixture in the current round
4. Player did NOT participate in the current round (not in `participatedPlayerIds`)
5. Player was NOT newly suspended in this round (not in `newlySuspendedPlayerIds`)
6. `persist-discipline=true` AND `mutate-career-state=true`

**Action:**
- `suspensionRemainingMatches > 1`: decrement by 1, `suspended=true`
- `suspensionRemainingMatches == 1`: `suspended=false`, `suspensionRemainingMatches=0`

**Participation exclusion:** Pre-suspended players who participated (e.g., played through a suspension) do not have their suspension served that round. Their suspension persists to the next round they don't play.

### 3.5 Injury Recovery Lifecycle (V24D6I)

**Trigger:** `applyV24InjuryRecoveryLifecycle` after suspension lifecycle

**Eligibility rules for recovery decrement:**
1. Player was injured (`injured=true`) BEFORE the round started
2. `injuryRemainingMatches > 0`
3. Player's team had an eligible fixture in the current round
4. Player did NOT participate in the current round (not in `participatedPlayerIds`)
5. Player was NOT newly injured in this round (not in `newlyInjuredPlayerIds`)
6. `persist-injuries=true` AND `mutate-career-state=true`

**Action:**
- `injuryRemainingMatches > 1`: decrement by 1, `injured=true`, `injuryType` unchanged
- `injuryRemainingMatches == 1`: `injured=false`, `injuryRemainingMatches=0`, `injuryType=null` (cleared)

**Participation exclusion:** Pre-injured players who participated (e.g., played while injured) do not have their recovery applied that round.

### 3.6 Interaction Matrix

| Interaction | Behavior |
|------------|----------|
| INJURY event → already injured | V24InjuryMutationApplier skips (do NOT overwrite existing injury) |
| RED_CARD → pre-suspended | V24DisciplineMutationApplier sets suspended=true; player has newlySuspended tracking; suspension lifecycle skips for same round |
| Yellow-threshold → pre-suspended | Same as RED_CARD: newlySuspended exclusion prevents double-servicing |
| Suspension lifecycle → injury player | No interaction — operates on different SessionPlayer fields |
| Injury recovery → suspended player | No interaction — operates on different SessionPlayer fields |
| Form persistence → availability | Form does not affect match availability; separate from injury/suspension |

---

## 4. Interactions Between Mutation Systems

### 4.1 Injury Persistence and Injury Recovery Lifecycle

**Injury mutation** sets `injured=true`, `injuryType="MATCH_INJURY"`, `injuryRemainingMatches=2` when V24 INJURY event occurs.

**Injury recovery** decrements or clears injury state for pre-round injured players who did not participate and have no fixture conflict.

**Interaction rules:**
- Newly injured players (injury applied this round) are excluded from recovery decrement in the same round via `newlyInjuredPlayerIds`
- Recovery only applies to players who were injured BEFORE the round started
- Injury mutation does not affect recovery-eligible pool until the NEXT round

**No conflict identified.** The snapshot comparison ensures newly injured players start their countdown from the next round.

### 4.2 Red-Card Suspension and Suspension Lifecycle

**Red-card mutation** sets `suspended=true`, `suspensionRemainingMatches=1` when V24 RED_CARD event occurs.

**Suspension lifecycle** decrements or clears suspension for pre-round suspended players who did not participate.

**Interaction rules:**
- Newly suspended players (RED_CARD this round) are excluded from suspension decrement in the same round via `newlySuspendedPlayerIds`
- RED_CARD sets `suspensionRemainingMatches=1`, so the player misses at least 1 match
- If the player was already suspended before the RED_CARD, the new suspension replaces the old one (not additive)

**No conflict identified.** The snapshot comparison ensures newly suspended players serve their new suspension.

### 4.3 Yellow-Card Threshold and Suspension Lifecycle

**Yellow-card threshold (V24D6H)** sets `suspended=true`, `suspensionRemainingMatches=1` when a player's `yellowCards >= 5` after a match.

**Suspension lifecycle** serves suspensions for pre-round suspended players.

**Interaction rules:**
- When yellow-count threshold triggers a suspension, the player is added to `newlySuspendedPlayerIds`
- Suspension lifecycle excludes `newlySuspendedPlayerIds` from serving in the same round
- Yellow-card accumulation uses `subtract-5` reset after threshold suspension (documented in V24D6H design)

**No conflict identified.** Threshold-triggered suspensions follow the same lifecycle rules as RED_CARD suspensions.

### 4.4 Form Mutation and V24PlayerSelector

**Form mutation (V24D6E)** updates `SessionPlayer.form` from V24 player ratings at the end of each match.

**V24PlayerSelector** uses `SessionPlayer.form` for AI squad selection decisions.

**Interaction:**
- Form is read by AI when selecting starting XI and substitutes
- Form does NOT affect match simulation directly (no match-strength modifier)
- Form does NOT affect injury/suspension/fatigue recovery eligibility

**No conflict identified.** Form flows: V24 match → V24FormMutationApplier → SessionPlayer.form → V24PlayerSelector reads for AI decisions.

### 4.5 Fatigue Mutation and Availability/Performance

**Fatigue mutation (V24D6C)** drains `SessionPlayer.energy` based on V24 match stamina consumption.

**Effect on availability:**
- Energy does NOT affect match eligibility (no hard blocks)
- Energy affects player performance rating (V24PlayerRatingModel applies penalties)
- No lifecycle recovery mechanism for energy — energy persists across matches

**Recovery mechanism:** Energy is expected to be restored between rounds (no automatic drain recovery implemented; V24D6C only handles the drain, not recovery). **This is a gap — see Section 7.**

---

## 5. Known Safe Defaults

| Default | Value | Rationale |
|---------|-------|-----------|
| `use-v24-detailed-engine` | `false` | V24 detailed engine is opt-in; V23/default path unaffected |
| `mutate-career-state` | `false` | Career mutation is opt-in master gate |
| `persist-injuries` | `false` | Injury mutation off by default |
| `persist-fatigue` | `false` | Fatigue mutation off by default |
| `persist-discipline` | `false` | Discipline mutation off by default |
| `persist-form` | `false` | Form mutation off by default |
| V23/default path | unaffected | V23 engine has no mutation code; default path is original behavior |

**No schema changes from V24D6.** All mutation uses existing SessionPlayer fields:
- `injured`, `injuryType`, `injuryRemainingMatches`
- `energy`
- `yellowCards`, `redCards`, `suspended`, `suspensionRemainingMatches`
- `form`

---

## 6. Test Coverage Summary

### 6.1 Full Suite

| Metric | Value |
|--------|-------|
| Full suite | 681 tests, 0 failures |
| Regression gate | 681, 0 failures |
| Baseline (pre-V24D6) | 602 tests |
| V24D6 additions | +79 tests across V24D6B through V24D6I |

### 6.2 Focused Mutation/Lifecycle Gate

| Test Class | Count | Coverage |
|-----------|-------|----------|
| V24FormMutationApplierTest | 18 | V24D6E form mutation |
| V24CareerMutationServiceTest | 58 | V24D6B/C/D/E service orchestration |
| V24CareerMutationIntegrationTest | 52 | V24D6D6B/D6I LeagueSimulator wiring |
| V24SuspensionLifecycleApplierTest | 19 | V24D6D6A suspension lifecycle |
| V24InjuryMutationApplierTest | 24 | V24D6B injury mutation |
| V24InjuryRecoveryLifecycleApplierTest | 22 | V24D6I2 injury recovery lifecycle |
| V24FatigueMutationApplierTest | 30 | V24D6C fatigue mutation |
| V24DisciplineMutationApplierTest | 27 | V24D6D5 discipline mutation |
| **Focused gate** | **250** | |

### 6.3 Key Test Classes

| Class | What it tests |
|-------|---------------|
| `V24CareerMutationServiceTest` | Applier orchestration, flag combinations, null guards |
| `V24CareerMutationIntegrationTest` | LeagueSimulator wiring, end-to-end mutation flow |
| `V24SuspensionLifecycleApplierTest` | Suspension lifecycle rules: pre-round capture, newly suspended exclusion, participation exclusion, fixture eligibility, full recovery at 1 |
| `V24InjuryRecoveryLifecycleApplierTest` | Injury recovery rules: pre-round capture, newly injured exclusion, participation exclusion, fixture eligibility, full recovery at 1, injuryType clearing |
| `V24InjuryMutationApplierTest` | Injury mutation: flag guards, null guards, do-not-overwrite-existing-injury |
| `V24FatigueMutationApplierTest` | Fatigue mutation: energy drain, flag guards |
| `V24DisciplineMutationApplierTest` | Discipline mutation: yellow/red cards, threshold suspension |

---

## 7. Remaining Real Gaps

### 7.1 Confirmed Gaps — Post-V24D6J Status

The following gaps were identified in this audit. After V24D6J3-J5 implementation:

| Gap | Status | Resolution |
|-----|--------|------------|
| **Energy recovery mechanism** | ✅ IMPLEMENTED (V24D6J4-J5) | `V24EnergyRecoveryLifecycleApplier` — rest-based +8/round for non-participating players, wired after injury recovery in `LeagueSimulator`. 716 tests, 0 failures. |
| **Backend lineup blocking for injured players** | ✅ IMPLEMENTED (V24D6J3) | `performAutoSelect()` and `validatePlayerFitness()` now cover `injured=true` AND `injuryRemainingMatches > 0` (stale-data edge case). No injured players can be auto-selected or manually saved. |
| **Optional frontend yellow-card counter display** | Open | MVP relies on suspended badge only; no visible yellow card count per player |
| **Optional frontend form display/polish** | Open | Form exists on SessionPlayer but may not be displayed in frontend UI |
| **Advanced injury severity model** | Open | All injuries are 2-match duration; no severity scaling |
| **Team morale/chemistry** | Open | Not modeled in V24 simulation context |
| **Player season stats** | Open | No per-player season cumulative stats beyond career total |
| **Advanced/competition-specific discipline rules** | Open | Only standard league rules implemented |
| **Balancing audit for mutation frequency** | Medium | No systematic audit of whether injury/fatigue/card rates are well-balanced for gameplay |

### 7.2 Lineup Blocking for Injured Players — Audit Required

The V24D6I injury recovery lifecycle does NOT enforce that injured players cannot be selected in the starting XI. The LeagueSimulator `simulateLeagueRound` method iterates fixtures and simulates matches but does not validate lineup eligibility based on `injured` or `injuryRemainingMatches`.

**This is a known gameplay gap.** If `persist-injuries=true` is enabled and a player is injured, they can still be put in the starting XI through the lineup controller. The match will still simulate with that player, and the injury may affect their performance rating via V24PlayerRatingModel, but the system does not hard-block the selection.

**Recommendation:** Audit whether lineup blocking for injured players should be implemented (V24D6J2), or if it is the frontend's responsibility to enforce this.

### 7.3 Energy Recovery — Audit Required

V24D6C fatigue mutation drains `SessionPlayer.energy` but there is no recovery mechanism:
- No rest/recovery between rounds
- No energy regeneration based on matches skipped
- Players with low energy continue with low energy until manually substituted

**Recommendation:** Determine if energy recovery is in scope for V24D6 or a future phase.

---

## 8. Risks

### 8.1 Overlapping Mutation Effects

**Risk:** If multiple mutation flags are enabled together (`persist-injuries=true` AND `persist-fatigue=true` AND `persist-discipline=true` AND `persist-form=true`), the combined effect on player availability may be severe — injured, tired, suspended, and low-form players simultaneously unavailable.

**Mitigation:** All flags default false. Production deployment should audit flag combinations before enabling multiple mutations simultaneously.

### 8.2 Lifecycle Order Regression

**Risk:** If the order of `applyV24SuspensionLifecycle` and `applyV24InjuryRecoveryLifecycle` is changed, or if lifecycle appliers are called inside the fixture loop instead of after it, the snapshot comparison for `newlySuspendedPlayerIds` and `newlyInjuredPlayerIds` would produce incorrect results.

**Mitigation:** Unit tests cover lifecycle order. Full suite regression gate (681 tests) would catch order regressions.

### 8.3 Stale Documentation Risk

**Risk:** As V24D6 evolves, docs may drift from implementation. The status lines in multiple docs (V23_ENGINE_EVOLUTION_ROADMAP.md, V23_SIMULATION_ENGINE_STATUS.md, V24_CURRENT_STATE_AUDIT.md, etc.) must be updated whenever implementation changes.

**Mitigation:** V24D6I5 (commit `f49abad`) updated all status docs. Future V24D6 changes should include a docs audit phase.

### 8.4 Frontend Visibility Gaps

**Risk:** Some SessionPlayer fields mutated by V24D6 may not be exposed in the frontend API or UI:
- `energy` (fatigue) — may not be displayed
- `injuryRemainingMatches` — exposed via squad endpoint (confirmed in V24D6G2)
- `yellowCards` — exposed via squad endpoint (confirmed in V24D6D7A)
- `form` — may not be displayed

**Mitigation:** V24D6G2 DTO audit confirmed squad endpoint exposes most fields. Verify form display in frontend.

### 8.5 Injury Duration Imbalance

**Risk:** All INJURY events result in `injuryRemainingMatches=2` regardless of severity. A muscle strain and a torn ACL have the same duration. This may be unrealistic for gameplay balance.

**Mitigation:** Not addressed in current V24D6. Future V24D6K or similar phase could introduce severity scaling.

---

## 9. Recommended Next Implementation Phase

### V24D6J2 — Energy Recovery + Lineup Blocking Audit

Two high-priority gaps identified in this audit:

**A. Energy Recovery Mechanism**
- V24D6C drains energy but provides no recovery
- Recommendation: design and implement automatic energy recovery between rounds
- Simple approach: `energy = min(100, energy + recoveryRate)` applied each round, or energy tied to matches skipped
- Alternative: rest flag per player, or rotation logic

**B. Lineup Blocking for Injured Players**
- LeagueSimulator does not block injured players from starting XI
- Recommendation: audit current lineup selection flow in CareerCommandController/LineupController
- Determine if backend should enforce `injured` → `suspension`-style lineup blocking
- Frontend may already handle this — verify before implementing backend enforcement

**Recommended order:** Energy recovery (V24D6J2) first, then lineup blocking audit (V24D6J3).

---

## 10. Audit Completeness

This audit was based on:
- Source code inspection: `LeagueSimulator.java`, `V24CareerMutationService.java`, `V24CareerMutationPolicy.java`, `V24InjuryMutationApplier.java`, `V24FatigueMutationApplier.java`, `V24DisciplineMutationApplier.java`, `V24FormMutationApplier.java`, `V24SuspensionLifecycleApplier.java`, `V24InjuryRecoveryLifecycleApplier.java`
- Test code inspection: focused mutation/lifecycle gate (250 tests)
- Design doc inspection: V24D6I_INJURY_RECOVERY_LIFECYCLE_DESIGN.md, V24D6D_DISCIPLINE_PERSISTENCE_DESIGN.md, V24D6H_YELLOW_CARD_THRESHOLD_DESIGN.md, V24D6E_FORM_MORALE_PERSISTENCE_DESIGN.md

**No implementation changes were made during this audit.**

---

*V24D6J1 — Career Mutation Consolidation Audit. No code changes.*