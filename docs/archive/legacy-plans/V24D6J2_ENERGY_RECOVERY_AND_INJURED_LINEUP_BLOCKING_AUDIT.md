# V24D6J2 — Energy Recovery and Injured Lineup Blocking Audit

**Purpose:** Design MVP solutions for two high-priority gaps identified in V24D6J1: (A) energy recovery mechanism and (B) injured player lineup blocking.
**Branch:** `mvp-1-performance-cleanup`
**Status:** V24D6J2 — AUDIT COMPLETE / DESIGN DRAFT
**Created:** 2026-05-22
**Latest implementation commit:** `7886308` (V24D6I3)
**Latest docs commit:** `96b9ac1` (V24D6J1)
**Tests:** 681 full suite, 0 failures (no new tests in this phase — docs-only)

---

## Executive Summary

V24D6J1 identified two high-priority gaps: **energy recovery absence** and **injured player lineup blocking**. This audit inspected the current implementation and found:

**Finding A (Energy Recovery):** V24D6C fatigue mutation drains `SessionPlayer.energy` after each V24 match (12 points for full participation, 6 for substitutes). No recovery mechanism exists — players who are low-energy stay low-energy until manually rotated. Four recovery options are evaluated; Option C (rest-based recovery) is recommended.

**Finding B (Injured Lineup Blocking):** The backend already implements injured player blocking in `performAutoSelect()` and `validatePlayerFitness()`. Auto-select filters `!p.getInjured()` and manual validation throws on `player.getInjured()`. The primary gap is the stale-data edge case: `injured=false` but `injuryRemainingMatches > 0`. A targeted fix is recommended.

**Recommendation:** Implement injured lineup blocking first (V24D6J3), then energy recovery (V24D6J4-J5), since injured blocking is already mostly implemented and only needs a small edge-case fix.

---

## Part A — Energy Recovery

### A.1 Current Energy Behavior Audit

**SessionPlayer.energy field:**
- `energy` (Integer, default 100 via `initDefaults()`)
- Getter: `getEnergy()`, setter: `setEnergy(Integer)`
- No upper bound enforcement in setter; callers must cap at 100

**V24FatigueMutationApplier behavior (`src/main/java/.../v24/V24FatigueMutationApplier.java`):**
- Drain rules:
  - Players who appeared in any non-substitution event: `DEFAULT_FULL_MATCH_DRAIN = 12`
  - Players who appeared ONLY in SUBSTITUTION events: `DEFAULT_SUBSTITUTE_DRAIN = 6`
  - Each player drains at most once per match (line 95: iterates `allParticipatingPlayerIds`, deduped)
- Skips injured players (`if (Boolean.TRUE.equals(player.getInjured())) continue;` — line 98)
- Energy clamped at 0 (`Math.max(0, currentEnergy - drain)`)
- Null energy treated as 100 (`if (currentEnergy == null) currentEnergy = 100;`)

**V24CareerMutationPolicy:**
```java
isFatiguePersistenceEnabled() = mutateCareerState AND persistFatigue
```

**V24CareerMutationService orchestration:**
- Calls `v24FatigueMutationApplier.applyFatigue(career, v24Result, v24MutationPolicy)` inside `applyMutations()`
- No recovery call exists anywhere in the service or LeagueSimulator

**LeagueSimulator mutation/lifecycle ordering:**
- `applyV24CareerMutation()` runs per-fixture during the fixture loop
- Fatigue drain happens here (line 369-376 in LeagueSimulator)
- No post-round recovery call exists after the fixture loop
- Injury recovery lifecycle runs after fixture loop but operates on `injured`/`injuryRemainingMatches`, not energy

**Existing fatigue mutation tests:**
- `V24FatigueMutationApplierTest` (30 tests) — covers drain, null guards, flag disabled, injured skip, substitute drain, energy floor at 0

**Confirmed current behavior:**
- Energy drains after V24 match when `mutate-career-state=true` AND `persist-fatigue=true`
- No automatic recovery exists anywhere in the career simulation
- No-fixture/rest players do NOT recover energy automatically
- Bench/non-participating players do NOT recover energy automatically
- Newly injured players are skipped by fatigue applier (injured players don't drain)
- Energy affects V24 match performance (V24PlayerRatingModel applies penalties) but not selection eligibility
- Energy does NOT affect V23/default simulation (V23 engine doesn't read SessionPlayer.energy)

### A.2 Energy Recovery Design Options

**Option A — Flat per-round recovery for non-participating players**
- After each round, all players who did NOT participate (not in `participatedPlayerIds`) recover +N energy
- Participating players do not recover (or recover less)
- Teams without a fixture: all squad players recover
- Simple implementation: add `applyEnergyRecovery()` after fixture loop in LeagueSimulator

**Option B — Universal per-round recovery for all players**
- All players in the career recover +N each simulated round regardless of participation
- Simple but less realistic — players who played full matches still recover same amount
- May over-recover in long seasons

**Option C — Rest-based recovery for non-participating players (RECOMMENDED)**
- After each round, non-participating players (not in `participatedPlayerIds`) recover +N energy
- Participating players drain through V24FatigueMutationApplier and do not get additional recovery in the same round
- Teams without a fixture: all squad players recover
- Aligns with real football: players who don't play in a match recover more fully
- Uses existing `participatedPlayerIds` tracking from V24RoundMutationTracking

**Option D — No automatic recovery**
- Not recommended — energy would become permanently low for players who aren't rotated
- Would require manual intervention or separate "rest" action per player

### A.3 Recommended Energy Recovery MVP

**Rule:** Rest-based per-round recovery for non-participating players.

```
applyEnergyRecovery(career, round, allFixtures, tracking):
  if !v24RoundProcessed return
  if !isFatiguePersistenceEnabled() return
  for each player in career who was in pre-round squad:
    if playerId NOT in tracking.participatedPlayerIds:
      // Player did not participate this round — recover energy
      currentEnergy = player.getEnergy() ?? 100
      newEnergy = min(100, currentEnergy + ENERGY_RECOVERY_RATE)
      player.setEnergy(newEnergy)
```

**Parameters:**
- `ENERGY_RECOVERY_RATE = 8` (players who don't play recover 8 energy per round)
- Participating players: energy stays as drained (no additional recovery that round)
- Cap at 100 (no overflow)
- Null energy treated as 100
- Injured players ARE eligible for energy recovery (they were skipped from drain but should still recover if they didn't play)
- Newly injured players (this round): eligible for recovery if they didn't participate
- Suspended players: eligible for recovery if they didn't participate
- Teams without fixture: all squad players recover (not in participatedPlayerIds because they had no fixture)

**No new schema fields.** Uses existing `SessionPlayer.energy`.

**Lifecycle position:** After `applyV24InjuryRecoveryLifecycle` in LeagueSimulator, same pattern as suspension/injury recovery.

**Gate:** `mutate-career-state=true` AND `persist-fatigue=true`

### A.4 Energy Recovery Edge Cases

| Case | Behavior |
|------|----------|
| `energy = null` | Treat as 100, apply recovery |
| `energy = 100` | No change (already at cap) |
| Player participated but energy still high | Drain applies, no recovery |
| Player did not participate but energy at 0 | Recovery to 8 |
| Injured player who didn't participate | Recovery applies |
| Suspended player who didn't participate | Recovery applies |
| Player with no fixture | Recovery applies to all squad |
| All players at 100 | No changes |
| Multiple rounds without participation | Accumulates (+8 per round) until cap |

---

## Part B — Injured Lineup Blocking

### B.1 Current Injured Lineup Blocking Audit

**Inspected files:**
- `LineupCommandUseCaseImpl.java` — `autoSelectLineup()` and `performAutoSelect()`
- `LineupHelper.java` — `validatePlayerFitness()`

**performAutoSelect() filtering (lines 120-128):**
```java
List<SessionPlayer> availablePlayers = squadIds.stream()
    .map(id -> career.getSessionPlayers().get(id))
    .filter(Objects::nonNull)
    .filter(p -> p.getEnergy() > 20)
    .filter(p -> !p.getInjured())
    .filter(p -> !Boolean.TRUE.equals(p.getSuspended()))
    .filter(p -> p.getSuspensionRemainingMatches() <= 0)
    .sorted(Comparator.comparing(SessionPlayer::calculateOverall).reversed())
    .toList();
```

**Auto-select already blocks:**
- `!p.getInjured()` — all players with `injured=true` are excluded
- `!Boolean.TRUE.equals(p.getSuspended())` — suspended players excluded
- `p.getSuspensionRemainingMatches() <= 0` — suspension remaining check
- `p.getEnergy() > 20` — low-energy players excluded

**validatePlayerFitness() (lines 107-121):**
```java
public void validateLineupBasic(List<SessionPlayer> players) {
    for (SessionPlayer player : players) {
        if (player.getEnergy() <= 20) {
            throw new IllegalArgumentException(
                "Player " + player.getName() + " has low fitness (" + player.getEnergy() + "%)");
        }
        if (player.getInjured()) {
            throw new IllegalArgumentException("Player " + player.getName() + " is injured");
        }
        if (Boolean.TRUE.equals(player.getSuspended()) || player.getSuspensionRemainingMatches() > 0) {
            throw new IllegalArgumentException(
                "Player " + player.getName() + " is suspended for " + player.getSuspensionRemainingMatches() + " match(es)");
        }
    }
}
```

**Manual lineup validation already throws on:**
- `player.getInjured()` — exact boolean check (not `Boolean.TRUE.equals`)
- `Boolean.TRUE.equals(player.getSuspended())` OR `suspensionRemainingMatches > 0`

**Confirmed current behavior:**
- Auto-select already excludes injured players via `!p.getInjured()` (line 124)
- Manual lineup save already rejects injured players via `validatePlayerFitness()`
- No specific `injuryRemainingMatches` check in auto-select or validation
- Frontend blocking: not audited here (separate frontend repo)

### B.2 Injured Lineup Blocking Design Options

**Option A — Add injuryRemainingMatches guard to auto-select and validation (RECOMMENDED)**
- In `performAutoSelect()`: change `.filter(p -> !p.getInjured())` to also check `p.getInjuryRemainingMatches() == null || p.getInjuryRemainingMatches() <= 0`
- In `validatePlayerFitness()`: add check `player.getInjuryRemainingMatches() != null && player.getInjuryRemainingMatches() > 0` as additional injury condition
- Ensures players with `injured=false` but stale `injuryRemainingMatches > 0` are also blocked
- Aligns with suspension pattern: `suspended=true` OR `suspensionRemainingMatches > 0`

**Option B — Add null-safe Boolean.TRUE.equals pattern to validation**
- Change `player.getInjured()` to `Boolean.TRUE.equals(player.getInjured())` in `validatePlayerFitness()` for null-safe consistency
- This is a consistency fix (suspension check uses `Boolean.TRUE.equals()`)

**Option C — No change needed**
- Backend already blocks injured players; the stale-data edge case is rare
- Not recommended — the V24D6J1 audit identified this as a gap

### B.3 Recommended Injured Lineup Blocking MVP

**Primary fix — stale data guard in auto-select (LineupCommandUseCaseImpl.java line 124):**

Change:
```java
.filter(p -> !p.getInjured())
```
To:
```java
.filter(p -> !Boolean.TRUE.equals(p.getInjured()) &&
             (p.getInjuryRemainingMatches() == null || p.getInjuryRemainingMatches() <= 0))
```

**Secondary fix — null-safe consistency in validatePlayerFitness() (LineupHelper.java):**

Change:
```java
if (player.getInjured()) {
```
To:
```java
if (Boolean.TRUE.equals(player.getInjured()) ||
    (player.getInjuryRemainingMatches() != null && player.getInjuryRemainingMatches() > 0)) {
```

And add injury duration to error message:
```java
"Player " + player.getName() + " is injured for " + player.getInjuryRemainingMatches() + " match(es)"
```

**Rationale:** This follows the same pattern as suspension blocking: check boolean flag OR remaining count. Players are blocked if either `injured=true` OR `injuryRemainingMatches > 0`.

**No new schema fields.** Uses existing `SessionPlayer.injured` and `SessionPlayer.injuryRemainingMatches`.

### B.4 Injured Lineup Blocking Edge Cases

| Case | Behavior |
|------|----------|
| `injured=true`, `injuryRemainingMatches=2` | Blocked — injured flag |
| `injured=false`, `injuryRemainingMatches=0` | Not blocked — not injured, no remaining |
| `injured=false`, `injuryRemainingMatches>0` (stale) | Blocked — remaining check catches this |
| `injured=null`, `injuryRemainingMatches=2` | Blocked — remaining check catches this |
| `injured=true`, `injuryRemainingMatches=null` | Blocked — injured flag |
| `injured=null`, `injuryRemainingMatches=0` | Not blocked — null injured treated false, no remaining |
| Player not in squad | N/A — auto-select uses squad stream |
| Already saved lineup with injured player | validatePlayerFitness rejects on next save |

---

## Part C — Combined Test Plan

### C.1 Energy Recovery Tests (if implemented — V24D6J4)

| Test Case | Setup | Expected |
|-----------|-------|----------|
| Non-participating player recovers | Player energy=70, not in participatedPlayerIds | energy → 78 (70+8, capped at 100) |
| Participating player does not recover | Player energy=70, in participatedPlayerIds | energy stays 70 |
| Energy capped at 100 | Player energy=95, not participated | energy → 100 |
| Null energy treated as 100 | Player energy=null, not participated | energy → 100 |
| Injured player recovers if not participated | injured=true, energy=60, not participated | energy → 68 |
| Suspended player recovers if not participated | suspended=true, energy=60, not participated | energy → 68 |
| No fixture: all squad recover | Team has no fixture this round | All squad players recover |
| Energy at 0 recovers to 8 | energy=0, not participated | energy → 8 |

### C.2 Injured Lineup Blocking Tests

| Test Case | Setup | Expected |
|-----------|-------|----------|
| injured=true blocked in auto-select | injured=true, remaining=2 | Not in availablePlayers |
| injured=false, remaining>0 blocked | injured=false, remaining=2 | Not in availablePlayers |
| injured=false, remaining=0 allowed | injured=false, remaining=0 | In availablePlayers |
| Null injured, remaining>0 blocked | injured=null, remaining=2 | Not in availablePlayers |
| validatePlayerFitness throws on injured | Manual save with injured player | IllegalArgumentException |
| validatePlayerFitness throws on stale injured | Manual save with injured=false, remaining=2 | IllegalArgumentException |
| Null-safe: validatePlayerFitness null injured | Manual save with injured=null, remaining=0 | No exception |
| Suspended player blocked (existing) | suspended=true | Not in availablePlayers |

---

## Part D — Proposed Implementation Phases

| Phase | Content | Deliverable | Tests |
|-------|---------|-------------|-------|
| **V24D6J3** | Injured lineup blocking MVP — stale-data guard in `performAutoSelect()` + `validatePlayerFitness()` | LineupCommandUseCaseImpl + LineupHelper fixes | ~8 new unit tests |
| **V24D6J4** | Energy recovery applier + unit tests (Option C: rest-based recovery, +8/round for non-participating players) | V24EnergyRecoveryLifecycleApplier + 8 tests | +8 tests |
| **V24D6J5** | LeagueSimulator wiring for energy recovery + integration tests | LeagueSimulator changes + integration tests | +5 tests |
| **V24D6J6** | Full suite validation + docs/status update | 681+ tests pass, docs updated | 0 new tests |
| **V24D6J7** | Optional frontend follow-up if UI doesn't show backend rejection clearly | Frontend changes (separate repo) | — |

---

## Part E — Risks and Open Questions

### Energy Recovery

| Risk | Mitigation |
|------|-----------|
| Recovery rate too fast — players never get tired | Start conservative (8/round), increase if needed |
| Injured players recover while injured — unrealistic | Injured players recover but injury status unchanged; separate concern |
| Teams without fixtures over-recover | Monitor team energy distribution; cap per team if needed |
| Energy recovery interacts with fatigue drain in same round | Recovery runs AFTER all fixtures; drain already applied during fixture loop |

**Open question:** Should `ENERGY_RECOVERY_RATE` be configurable via application.yaml? Recommendation: yes, default 8, allow tuning.

**Open question:** Should players who are suspended also recover energy? Current recommendation: yes — suspension doesn't prevent physical recovery.

### Injured Lineup Blocking

| Risk | Mitigation |
|------|-----------|
| `player.getInjured()` returns null — inconsistent with `Boolean.TRUE.equals()` pattern | Fix to null-safe in both auto-select and validation |
| Existing saved lineups with stale injured=false / remaining>0 | validatePlayerFitness catches this on next save; no automatic migration needed |
| Frontend already blocks but backend doesn't — inconsistent UX | Fix backend to match frontend; both should block same players |

**Open question:** Should `injuryRemainingMatches` be cleared on full recovery? V24D6I already clears: `injured=false, remaining=0, injuryType=null` at remaining==1. This audit confirms that behavior.

---

## Part F — Recommendation

**Priority order:**

1. **V24D6J3 first** — Injured lineup blocking is already mostly implemented; only needs a small edge-case fix (add `injuryRemainingMatches > 0` guard). Low implementation risk, high gameplay integrity.

2. **V24D6J4-J5 second** — Energy recovery requires new applier + LeagueSimulator wiring. Medium implementation risk. Improves long-season gameplay realism.

**Why J3 first:**
- Injured lineup blocking is a correctness fix — prevents selecting unavailable players
- Energy recovery is a balance improvement — not a correctness issue
- J3 is smaller and safer to implement
- J3 directly addresses the stale-data gap identified in V24D6J1

---

*V24D6J2 — Energy Recovery and Injured Lineup Blocking Audit. No code changes.*