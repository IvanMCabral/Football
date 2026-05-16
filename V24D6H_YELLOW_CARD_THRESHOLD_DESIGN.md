# V24D6H — Yellow Card Suspension Threshold Design

**Status:** V24D6H1 DESIGN COMPLETE — audit of current implementation done; V24D6H2 implementation not started
**Branch:** `mvp-1-performance-cleanup`
**Created:** 2026-05-16
**Audited from:** V24D6D7 complete state (commits `6aadcd5`, `8097ca9`, `69bf879`)

---

## 1. Executive Summary

V24D6D2-D5 implemented discipline persistence: YELLOW_CARD events increment `SessionPlayer.yellowCards`, RED_CARD events set `suspended=true` and `suspensionRemainingMatches=1`. V24D6D6 implemented suspension lifecycle/decrement. V24D6D7 completed DTO/API exposure, lineup blocking, and frontend suspension visibility.

**Remaining gap:** Accumulated yellow cards have no career consequence. A player can accumulate 10 yellow cards across a season with no gameplay effect. V24D6H addresses this by implementing a yellow-card suspension threshold.

**Recommended MVP rule:** 5 accumulated yellow cards → 1-match suspension. Evaluated at discipline mutation time after each match. Threshold-suspended players are treated as newly suspended (excluded from immediate lifecycle decrement). No frontend changes required for MVP because the existing suspended badge and lineup blocking already handle threshold-suspended players.

---

## 2. Current Discipline State

### 2.1 Discipline Pipeline (as of V24D6D7)

**V24DisciplineMutationApplier.applyDiscipline()** — processes match timeline events:
- `YELLOW_CARD`: `player.yellowCards++` (counts individually, no threshold)
- `RED_CARD`: `player.redCards++`, `player.suspended=true`, `player.suspensionRemainingMatches=1` (counted once per player per match via `HashSet`)

**V24CareerMutationResult** — tracks only aggregate counts:
- `disciplineApplied` — total card events processed (yellow + red, no breakdown)
- No per-player detail, no suspension-type distinction

**V24CareerMutationService** — orchestrates mutation:
- Calls `disciplineApplier.applyDiscipline()` if `isDisciplinePersistenceEnabled()`
- Returns `V24CareerMutationResult` with disciplineApplied count
- No per-player suspension detail propagated

**LeagueSimulator.V24RoundMutationTracking** — tracks per-round state:
- `newlySuspendedPlayerIds` — **populated ONLY from RED_CARD events** (line 476-479 of LeagueSimulator: `if event.type() == RED_CARD → add playerId`)
- `participatedPlayerIds` — all player IDs from timeline events

**V24SuspensionLifecycleApplier.applyServedSuspensions()** — excludes from decrement:
- Players in `newlySuspendedPlayerIds` are skipped (they just got a fresh suspension, should be served next round)
- Players in `participatedPlayerIds` are skipped (served their suspension by playing)

**Key finding:** `newlySuspendedPlayerIds` is only populated from RED_CARD events. YELLOW_CARD events do NOT populate any tracking set. There is no yellow-card accumulation → suspension bridge in the current pipeline.

### 2.2 DTO/API Exposure (V24D6D7A)

- `SessionPlayerDTO`: `yellowCards`, `redCards`, `suspended`, `suspensionRemainingMatches` — all exposed
- `PlayerLineupDTO`: same four discipline fields — all exposed
- `SessionEntityMapper`: maps from SessionPlayer to DTOs

### 2.3 Lineup Blocking (V24D6D7A)

- `LineupHelper.validatePlayerFitness()` — checks `player.getSuspended()`
- `LineupCommandUseCaseImpl.performAutoSelect()` — filters `!p.getSuspended()`

### 2.4 Current Discipline Field State on SessionPlayer

```java
private Integer yellowCards;  // default 0
private Integer redCards;    // default 0
private Boolean suspended;   // default false
private Integer suspensionRemainingMatches;  // default 0
```

All fields have null-safe getters. No threshold field exists anywhere in the codebase. The threshold is a constant (not per-player configurable).

---

## 3. Problem Statement

A player who accumulates 20 yellow cards across a season has no career consequence under the current system. Yellow cards are counted and persisted but never trigger a suspension. This creates:

1. **No gameplay consequence for repeated fouling** — players can accumulate yellows indefinitely with no penalty
2. **Inconsistency with real football** — most professional leagues have yellow-card suspension rules (e.g., 5 yellows = 1-match ban)
3. **Undermined discipline system** — RED_CARD creates suspension but accumulated yellows do not

V24D6H introduces a yellow-card suspension threshold that converts accumulated yellow cards into a suspension, creating career consequences for repeated fouling behavior.

---

## 4. Rule Options

### 4A. Threshold Value

| Option | Value | Notes |
|--------|-------|-------|
| A1 | 3 yellows → 1-match suspension | Aggressive; may suspend too many players |
| **A2 (Recommended)** | **5 yellows → 1-match suspension** | Standard football threshold; moderate impact |
| A3 | 7 yellows → 1-match suspension | Conservative; fewer suspensions |

### 4B. Evaluation Timing

| Option | Timing | Notes |
|--------|--------|-------|
| B1 | Pre-round snapshot (before match) | Wrong — player could earn suspension mid-match then play it |
| **B2 (Recommended)** | **Post-match discipline mutation (after processing all events)** | Correct — threshold triggered after match is complete |
| B3 | Pre-round with lineup blocking | Requires lineup integration before enabling |

### 4C. Yellow Reset Behavior

| Option | Behavior | Notes |
|--------|----------|-------|
| C1 | Reset to 0 after threshold hit | Clean slate; simple |
| **C2 (Recommended)** | **Subtract threshold once, e.g., 5 → 0, 6 → 1, 7 → 2** | Partial carry-over; prevents immediate re-suspension |
| C3 | Keep lifetime total + separate activeYellowCards field | Overkill for MVP |

**C2 worked example:**
- Player has 4 yellows, receives 1 yellow → threshold reached (5 total), suspension applied, yellowCards = 0
- Player has 5 yellows, receives 2 yellows in same match → threshold reached (7 total), suspension applied once, yellowCards = 2
- Player has 9 yellows, receives 1 yellow → threshold reached (10 total), suspension applied once, yellowCards = 5 (not 0 or -1)

### 4D. Multiple Threshold Hits in Same Match

| Option | Behavior | Notes |
|--------|----------|-------|
| D1 | Apply multiple suspensions | Could result in 2-match ban from one match; complex |
| **D2 (Recommended)** | **Apply maximum 1 threshold suspension per match** | MVP simplicity; one suspension per match regardless of how many yellows over threshold |

### 4E. Interaction with RED_CARD in Same Match

| Option | Behavior | Notes |
|--------|----------|-------|
| **E1 (Recommended)** | **RED_CARD suspension takes precedence; do NOT also apply yellow-threshold suspension in same match** | Avoid double-suspension in one match; RED_CARD already sets suspension |
| E2 | Both apply | Could create 2-match suspension from single match; too aggressive |

### 4F. Already Suspended Player Reaching Threshold

| Option | Behavior | Notes |
|--------|----------|-------|
| F1 | Override existing suspension | Wrong — player already suspended, don't reset countdown |
| **F2 (Recommended)** | **Do NOT apply yellow-threshold suspension if player is already suspended** | Accumulate yellowCards but don't override active suspension |
| F3 | Extend existing suspension | Requires adding to remaining count; out of MVP scope |

### 4G. Suspension Duration

| Option | Duration | Notes |
|--------|-----------|-------|
| **G1 (Recommended)** | **1 match** | Same as RED_CARD MVP behavior; consistent |
| G2 | 2 matches | Requires `suspensionRemainingMatches > 1`; out of MVP scope |

---

## 5. Recommended MVP Rules

### 5.1 Rule Summary

1. **Threshold:** 5 accumulated yellow cards → 1-match suspension
2. **Evaluation:** After processing all YELLOW_CARD events in discipline mutation
3. **Reset:** Subtract threshold (5) once from yellowCards when suspension is applied
4. **Per-match cap:** Maximum 1 threshold suspension per match (no stacking)
5. **RED_CARD precedence:** If player receives RED_CARD in the same match, do NOT also apply yellow-threshold suspension
6. **Already suspended:** Do NOT apply yellow-threshold suspension if player is already suspended (accumulate yellows only)
7. **Duration:** 1 match — `suspended=true`, `suspensionRemainingMatches=1`
8. **Lifecycle exclusion:** Threshold-suspended players must be added to `newlySuspendedPlayerIds` so they are NOT decremented in the same round
9. **Frontend:** No changes required for MVP (suspended badge and lineup blocking already work)

### 5.2 Implementation Injection Point

V24D6H threshold logic belongs in **V24DisciplineMutationApplier** — same class that handles YELLOW_CARD counting and RED_CARD suspension. This keeps all discipline mutation logic in one place.

### 5.3 State Changes Required

| Location | Change | Type |
|---------|--------|------|
| `V24DisciplineMutationApplier` | Add threshold constant `YELLOW_THRESHOLD = 5` | New constant |
| `V24DisciplineMutationApplier` | After yellowCards increment, check `if (yellowCards >= threshold && !player.isSuspended())` | New logic |
| `V24DisciplineMutationApplier` | Return count includes threshold suspensions (no result type change needed for MVP) | Uses existing int return |
| `LeagueSimulator` | Detect newly threshold-suspended players via pre/post mutation suspended snapshot comparison (Option B) | New tracking |
| `V24CareerMutationResult` | **No change in MVP** — threshold suspensions observable via LeagueSimulator snapshot comparison and SessionPlayer state | Deferred

### 5.4 Threshold Logic Pseudocode

```java
// Inside V24DisciplineMutationApplier.applyDiscipline() after processing YELLOW_CARD:

if (type == YELLOW_CARD) {
    // ... existing: yellowCards++ ...
    // After increment:
    if (yellowCards >= YELLOW_THRESHOLD 
            && !Boolean.TRUE.equals(player.getSuspended())
            && !alreadyThresholdSuspendedThisMatch.contains(playerId)) {
        // Apply suspension
        player.setSuspended(true);
        player.setSuspensionRemainingMatches(1);
        // Subtract threshold once
        player.setYellowCards(player.getYellowCards() - YELLOW_THRESHOLD);
        // Track for lifecycle exclusion
        thresholdSuspendedPlayers.add(playerId);
        appliedCount++; // counts as discipline applied
        // Note: RED_CARD already sets suspended, so skip if already suspended
    }
}
```

---

## 6. Lifecycle Integration

### 6.1 The Exclusion Problem

V24SuspensionLifecycleApplier skips decrement for players in `newlySuspendedPlayerIds`. Currently only RED_CARD players are added to this set. If yellow-threshold suspensions are not tracked, they would be incorrectly decremented in the SAME ROUND they are applied.

**Example of bug without tracking:**
1. Player has 4 yellows going into match
2. Receives 1 yellow in match → now 5 → threshold suspension applied
3. Lifecycle runs post-round → player is NOT in `newlySuspendedPlayerIds` → incorrectly decremented
4. Player's `suspensionRemainingMatches` goes from 1 to 0 → suspension served immediately without missing a match

### 6.2 Solution

LeagueSimulator needs to know which players were threshold-suspended in the current round. Options:

**Option A — Enhance V24CareerMutationResult to return per-player detail**
- Change `applyDiscipline()` to return a struct/object with `disciplineApplied` count AND `Set<String> newlySuspendedPlayerIds` (including threshold)
- Requires: result type change, service return value change, LeagueSimulator capture

**Option B — Snapshot before/after comparison in LeagueSimulator**
- Before `applyV24CareerMutation()`: capture current `suspended=true` player IDs
- After `applyV24CareerMutation()`: compare — any player who just became suspended is newly threshold-suspended
- Requires: no result type change, just LeagueSimulator comparison logic

**Option C — V24DisciplineMutationApplier as service with callback**
- Applier calls back into LeagueSimulator with threshold-suspended player IDs
- Tight coupling; not recommended

**Recommended: Option B** — snapshot comparison in LeagueSimulator. It requires no changes to `V24CareerMutationResult` or the mutation applier interface. The logic is:
1. Before `applyV24CareerMutation()`, capture `preMutationSuspendedIds = getSuspendedPlayerIds()`
2. Call `applyV24CareerMutation()`
3. After mutation, capture `postMutationSuspendedIds = getSuspendedPlayerIds()`
4. New threshold-suspended = `postMutationSuspendedIds - preMutationSuspendedIds` minus RED_CARD players already tracked
5. Add to `newlySuspendedPlayerIds`

This is similar to how `preMatchSuspendedPlayerIds` is already captured for the lifecycle decrement.

### 6.3 Lifecycle Flow With V24D6H

```
Match completes
  ↓
V24DisciplineMutationApplier.applyDiscipline()
  → YELLOW_CARD events: yellowCards++
  → Threshold reached: suspended=true, remainingMatches=1, yellowCards -= 5
  → RED_CARD events: redCards++, suspended=true, remainingMatches=1
  ↓
LeagueSimulator captures threshold-suspended players (snapshot comparison)
  ↓
V24SuspensionLifecycleApplier.applyServedSuspensions()
  → pre-round suspended players who did NOT participate AND
    are NOT in newlySuspendedPlayerIds (RED_CARD + threshold) → decrement
  → threshold-suspended players are NOT decremented this round
  ↓
Player serves suspension next round (misses match, not in newlySuspended next round)
```

---

## 7. API/DTO/Frontend Impact

### 7.1 Backend Changes

- `V24DisciplineMutationApplier` — threshold logic (no API change)
- `V24CareerMutationResult` — no MVP change; threshold suspensions observed through SessionPlayer state and LeagueSimulator snapshot comparison; optional explicit count deferred to future phase
- `LeagueSimulator` — snapshot comparison for threshold-suspended tracking (no API change)
- No new endpoints
- No Redis/schema changes

### 7.2 Frontend Changes

**None required for MVP.**

The existing suspended badge (`PlayerCardComponent` and `LineupPlayerCardComponent`) already displays "Suspended" when `player.suspended === true`. Once V24D6H applies a yellow-threshold suspension, `suspended=true` is set on the SessionPlayer and the existing badge renders automatically.

No DTO changes, no component changes, no API contract changes.

### 7.3 Future Frontend Enhancement (Not V24D6H Scope)

Display yellow card count on player cards:
- Add `yellowCards` display to `PlayerCardComponent`
- Add yellow card badge with count (e.g., "3/5" threshold indicator)
- Tooltip: "X yellow cards — 1 match suspension at 5"
- This is optional and deferred to a future phase

---

## 8. Testing Plan

### V24D6H2 — Unit Tests for V24DisciplineMutationApplier (Threshold Logic)

Tests for threshold behavior in applier:

1. `belowThreshold_noSuspension()` — 4 yellows, no threshold applied
2. `exactlyThreshold_setsSuspendedAndRemainingOne()` — 5 yellows, suspended=true, remaining=1
3. `aboveThreshold_subtractsThresholdOnce()` — 6 yellows → suspended, yellowCards becomes 1
4. `exactlyThresholdAtMatchEnd_subtractsToZero()` — 5 exactly → yellowCards becomes 0
5. `redCardSameMatch_doesNotApplyAdditionalYellowThresholdSuspension()` — player gets RED_CARD and enough YELLOWs → only RED suspension, not double
6. `alreadySuspended_reachesThreshold_doesNotOverrideSuspension()` — player already suspended, yellow threshold reached → suspended stays true, remaining stays 1
7. `thresholdWithMultipleYellowsInSameMatch_appliesOnce()` — player gets 2 yellows (was 4), reaches threshold → only 1 suspension applied, not 2
8. `disabledPolicy_noThreshold()` — policy with discipline disabled → no threshold applied
9. `nullPlayerId_yellowThresholdSkipped()` — event with null playerId → no NPE
10. `unknownPlayer_yellowThresholdSkipped()` — playerId not in career → skipped gracefully
11. `yellowCardsAccumulatesAcrossMatches()` — test accumulation over multiple match calls (implicit in state machine)

### V24D6H3 — Service/Result Regression Tests

1. `thresholdContributesToDisciplineAppliedCount_orDocumentedCountSemantics()` — threshold suspension counts toward disciplineApplied OR semantics documented
2. `thresholdAndRedCard_bothTrackedCorrectly()` — both RED and yellow-threshold suspensions appear correctly
3. `disabledPolicy_noDisciplineMutationAndNoThresholdEffect()` — policy off → no discipline mutation and no threshold effect
4. `thresholdFailure_doesNotEraseOtherDiscipline()` — if threshold throws, yellows still counted, red still applies

### V24D6H4 — LeagueSimulator Integration Tests

1. `thresholdSuspendedPlayer_notDecrementedSameRound()` — player threshold-suspended in round N → NOT decremented in round N's lifecycle
2. `thresholdSuspendedPlayer_decrementedNextRound()` — player threshold-suspended in round N, does not play in round N+1 → suspended=false after lifecycle
3. `thresholdSuspendedPlayer_playsNextRound_notDecremented()` — player threshold-suspended, then plays in round N+1 → NOT decremented (participation check)
4. `bothRedAndThreshold_newlySuspendedExcludesBoth()` — player gets RED and player gets threshold → both in newlySuspended set
5. `persist-disciplineFalse_noThresholdEffect()` — flag off → no discipline mutation and no threshold effect

### V24D6H5 — Full Validation

Run full regression gate:
```
mvn test -Dtest=... (602 tests + V24D6H tests)
```
Expected: 602 + V24D6H2-H4 tests, all pass.

---

## 9. Risks and Mitigations

### Risk 1: Double suspension (RED + yellow threshold same match)

**Severity:** HIGH
**Likelihood:** MEDIUM (player who gets a second yellow leading to RED could simultaneously cross threshold from earlier yellows in the same match)

**Mitigation:** RED_CARD suspension takes precedence. Applier must check `!Boolean.TRUE.equals(player.getSuspended())` before applying yellow-threshold suspension.

### Risk 2: Immediate decrement without lifecycle exclusion

**Severity:** HIGH
**Likelihood:** HIGH (if threshold-suspended players are not added to `newlySuspendedPlayerIds`)

**Mitigation:** LeagueSimulator snapshot comparison (Option B) ensures threshold-suspended players are excluded from lifecycle decrement in the same round.

### Risk 3: Yellow reset semantics causing user confusion

**Severity:** MEDIUM
**Likelihood:** LOW (subtract-threshold is a known pattern)

**Mitigation:** Document clearly in UI tooltips and in-game messaging. If player has 7 yellows and receives 1 more (8 total), after threshold they have 3 yellows remaining (not 0 and not negative).

### Risk 4: Multiple threshold hits in same match causing multiple suspensions

**Severity:** MEDIUM
**Likelihood:** LOW (per-match cap prevents this)

**Mitigation:** Apply maximum 1 threshold suspension per match. Track `alreadyThresholdSuspendedThisMatch` in applier.

### Risk 5: Old saves with high yellowCards values

**Severity:** LOW
**Likelihood:** MEDIUM (if persist-discipline is enabled on existing career)

**Mitigation:** Threshold is evaluated post-match. If a player in an old career has 50 yellows, on the next match where they receive a yellow card, the threshold will be evaluated and suspension applied. This is correct behavior — accumulated yellows should trigger suspension regardless of when they were earned.

### Risk 6: Yellow threshold applied without lineup blocking

**Severity:** LOW (lineup blocking already exists from V24D6D7A)

**Mitigation:** `LineupHelper.validatePlayerFitness()` blocks suspended players. Once threshold suspension is applied, the player is blocked from lineup automatically.

### Risk 7: Mutation disabled but persist-discipline enabled

**Severity:** LOW
**Likelihood:** LOW

**Mitigation:** Threshold logic is gated by `isDisciplinePersistenceEnabled()` which requires BOTH `mutateCareerState=true` AND `persist-discipline=true`. Neither flag defaults to true.

---

## 10. Non-Goals

V24D6H does NOT include:
- **No multi-match bans** — all suspensions are 1 match
- **No appeal/reduction logic** — suspension stands as applied
- **No competition-specific rules** — single threshold for all competitions/leagues
- **No frontend yellow card counter display** — MVP relies on existing suspended badge only
- **No configurable threshold** — threshold is hardcoded as 5
- **No injury recovery lifecycle** — separate deferred phase
- **No form/morale persistence** — separate deferred phase
- **No new API endpoints** — all changes are internal mutation logic
- **No Redis/schema migration** — SessionPlayer fields already exist

---

## 11. Proposed Implementation Phases

| Phase | Content | Risk | Dependency |
|-------|---------|------|------------|
| V24D6H1 | **This design document** — audit + design | NONE | V24D6D7 complete |
| V24D6H2 | V24DisciplineMutationApplier threshold logic + unit tests | LOW | H1 complete |
| V24D6H3 | Service/result regression tests; no V24CareerMutationResult field in MVP | LOW | H2 complete |
| V24D6H4 | LeagueSimulator lifecycle integration + snapshot comparison + integration tests | MEDIUM | H2+H3 complete |
| V24D6H5 | Full regression gate validation | LOW | H4 complete |
| V24D6H6 | Docs update | NONE | H2-H5 complete |

---

## 12. Completion Criteria

- [x] Design document created (V24D6H1)
- [ ] Threshold constant defined (5)
- [ ] Threshold-applies-suspension logic implemented in V24DisciplineMutationApplier
- [ ] Per-match cap enforced (1 threshold suspension max per match)
- [ ] RED_CARD precedence implemented (no double suspension)
- [ ] Already-suspended protection implemented
- [ ] Yellow reset behavior (subtract 5 once) implemented
- [ ] V24CareerMutationResult unchanged in MVP; count semantics documented/tested
- [ ] LeagueSimulator snapshot comparison for threshold-suspended tracking implemented
- [ ] Lifecycle exclusion verified (threshold-suspended players not decremented same round)
- [ ] Unit tests: V24DisciplineMutationApplier threshold tests (11 tests)
- [ ] Service/result regression tests confirm threshold behavior does not break existing disciplineApplied semantics
- [ ] Integration tests: LeagueSimulator lifecycle with threshold (5 tests)
- [ ] Full regression gate: 602 + V24D6H tests, 0 failures
- [ ] No new API endpoints
- [ ] No Redis/schema changes
- [ ] No frontend changes (MVP)
- [ ] No target/ staging

---

*This document is the authoritative V24D6H design specification. All implementation must conform to this document. Update this document before making any code changes.*