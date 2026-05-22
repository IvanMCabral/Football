# V24D6I — Injury Recovery Lifecycle Design

**Status:** V24D6I1-I5 COMPLETE
**Branch:** `mvp-1-performance-cleanup`
**Latest implementation commit:** `7886308` (V24D6I3 — injury recovery lifecycle wiring)
**Latest docs commit:** `4ad4210` (V24D6I1 — design document)
**Tests:** 681 full suite total (651 pre-I + 30 I2/I3), 0 failures
**Date:** 2026-05-22

---

## Executive Summary

V24D6I injury recovery lifecycle is **COMPLETE** — implemented and validated (681 tests, 0 failures).

V24D6B injury persistence is complete — INJURY events from the V24 timeline update `SessionPlayer.injured=true`, `SessionPlayer.injuryType`, and `SessionPlayer.injuryRemainingMatches=2`. V24D6I adds the **recovery lifecycle** (automatic decrement of `injuryRemainingMatches` after each round, and clearing of injury state when it reaches 0).

V24D6I follows the same pattern as V24D6D6 (suspension lifecycle): capture pre-round state before the fixture loop, apply mutations after the full round, exclude newly-injured players from the same-round decrement, and gate by `mutate-career-state=true` + `persist-injuries=true`.

**No new schema fields needed.** Uses existing `SessionPlayer.injured`, `SessionPlayer.injuryType`, and `SessionPlayer.injuryRemainingMatches`.

---

## 1. Audit Findings

### 1.1 SessionPlayer Injury Fields

Location: `src/main/java/com/footballmanager/domain/model/entity/SessionPlayer.java`

| Field | Type | Null-safe Getter | Setter | Init Default |
|-------|------|-----------------|--------|-------------|
| `injured` | `Boolean` | `getInjured()` (returns null if unset) | `setInjured(Boolean)` | `false` via `initDefaults()` |
| `injuryType` | `String` | `getInjuryType()` | `setInjuryType(String)` | `null` via `initDefaults()` |
| `injuryRemainingMatches` | `Integer` | `getInjuryRemainingMatches()` | `setInjuryRemainingMatches(Integer)` | `0` via `initDefaults()` |

**Null handling note:** `getInjured()` returns raw Boolean (may be null). Callers use `Boolean.TRUE.equals(player.getInjured())` pattern (as seen in `V24InjuryMutationApplier` line 49 and `V24SuspensionLifecycleApplier` line 94). This is the established null-safe pattern.

**injuryType convention:** When injury is set, `V24InjuryMutationApplier` uses `DEFAULT_INJURY_TYPE = "MATCH_INJURY"`. Clearing injury should probably clear `injuryType` to null, unless convention prefers retaining it for historical audit. **Open question — see Section 6.**

### 1.2 V24InjuryMutationApplier Behavior

Location: `src/main/java/com/footballmanager/application/service/simulation/v24/V24InjuryMutationApplier.java`

```java
public static final String DEFAULT_INJURY_TYPE = "MATCH_INJURY";
public static final int DEFAULT_INJURY_DURATION_MATCHES = 2;

public int applyInjuries(CareerSave career, V24DetailedMatchResult result, V24CareerMutationPolicy policy) {
    // ... null guards ...
    for (V24MatchEvent event : result.timeline().events()) {
        if (event.type() != V24MatchEventType.INJURY) continue;
        // ...
        // Do NOT overwrite existing injury
        if (Boolean.TRUE.equals(player.getInjured())) continue;
        player.setInjured(true);
        player.setInjuryType(DEFAULT_INJURY_TYPE);
        player.setInjuryRemainingMatches(DEFAULT_INJURY_DURATION_MATCHES);
        appliedCount++;
    }
    return appliedCount;
}
```

Key behavior: **Does not overwrite existing injury** — if `injured=true` already, skip. This means a player who is already injured does NOT get a fresh 2-match injury on a new INJURY event.

**Injury duration is always 2 matches** (hard-coded constant). No randomness, no severity scaling.

### 1.3 V24SuspensionLifecycleApplier Pattern (Reference Implementation)

Location: `src/main/java/com/footballmanager/application/service/simulation/v24/V24SuspensionLifecycleApplier.java`

This is the reference pattern for V24D6I. Key design decisions:

1. **Captures pre-round state** before fixture loop via `capturePreRoundSuspendedPlayerIds(career)` in `LeagueSimulator`
2. **Applies after full round** via `applyV24SuspensionLifecycle()` called once per round after all fixtures
3. **Excludes newly suspended** via `tracking.newlySuspendedPlayerIds` (players who got RED_CARD in this round)
4. **Uses participation tracking** — `participatedPlayerIds` collected from starting XI + timeline events
5. **Checks team eligibility** — only decrements if player's team had a fixture in the round
6. **Clears state at zero** — sets `suspended=false`, `suspensionRemainingMatches=0`
7. **Gated by policy** — `policy.isDisciplinePersistenceEnabled()`

### 1.4 LeagueSimulator Round/Fixture Loop

Location: `src/main/java/com/footballmanager/application/service/simulation/LeagueSimulator.java`

Relevant flow:
```
simulateLeagueRound(career, allFixtures, ...):
  tracking = V24RoundMutationTracking()  // line 169 — captured BEFORE fixture loop
  for each fixture:
    if V24 path:
      simulateWithV24Engine()  // lines 244-296
        collectStartingXIParticipation()  // adds to tracking.participatedPlayerIds
        collectV24ResultParticipation()  // adds to tracking.participatedPlayerIds
        applyV24CareerMutation()  // applies injuries/fatigue/discipline/form
  applyV24SuspensionLifecycle()  // line ~430 — called AFTER full fixture loop
```

**V24RoundMutationTracking** (lines 505-509):
```java
private static class V24RoundMutationTracking {
    final Set<String> newlySuspendedPlayerIds = new HashSet<>();
    final Set<String> participatedPlayerIds = new HashSet<>();
    boolean v24RoundProcessed = false;
}
```

**Missing:** `newlyInjuredPlayerIds` tracking. Currently no tracking of which players got new injuries during the round.

### 1.5 Participation Tracking for Suspension Lifecycle

`participatedPlayerIds` is populated from:
- Starting XI for both teams (via `collectStartingXIParticipation`)
- All players referenced in timeline events (via `collectV24ResultParticipation` — includes playerId and relatedPlayerId from all event types)

This means ANY player who appears in any match event (INJURY, GOAL, CARD, SUBSTITUTION, etc.) is counted as having participated.

### 1.6 Preventing Newly Injured Players from Decrementing

The suspension lifecycle uses `newlySuspendedPlayerIds` to exclude players who received a RED_CARD during the round from being decremented in the same round (they serve their new suspension instead).

For injury recovery, a similar mechanism is needed: `newlyInjuredPlayerIds` — players who received an INJURY event during the round should NOT decrement in the same round. They start their injury count from the next round.

**How to identify newly injured:**
- Before `applyV24CareerMutation()`: capture pre-round injured player IDs (already needed for recovery eligibility)
- After `applyV24CareerMutation()`: compare pre-round vs post-round injured set — the difference is `newlyInjuredPlayerIds`
- This is analogous to how `preMatchSuspendedPlayerIds` is captured before the round and compared against `newlySuspendedPlayerIds` for suspension lifecycle

### 1.7 V24 Path Only vs. All Round Simulation Paths

**Recommendation:** Injury recovery applies only to V24 path.

Rationale:
- `persist-injuries` flag only has meaning in V24 context (V23 has no injury mutation)
- V23/default path does not modify `injuryRemainingMatches` — recovery should not apply there either (consistency: a player injured in a V23 match would never have their injury tracked)
- The `mutate-career-state=true` + `persist-injuries=true` gates already restrict this to V24-only behavior
- If a player was injured in a V24 match, their recovery should track through subsequent V24 rounds; mixing V23 and V24 paths for the same career would create inconsistent state

### 1.8 No Fixture / Bye Behavior

If a player's team has no fixture in a given round (bye week or not in competition):
- The player should NOT have their injury decrement that round
- This is consistent with suspension lifecycle behavior (players without fixture don't serve suspension)
- Implementation: check `teamsWithFixtureThisRound` before decrementing, same pattern as suspension lifecycle

### 1.9 Injured Player Who Participates — Should They Decrement?

The suspension lifecycle does NOT decrement a suspended player who participated in the round (line 100: `if (participatedPlayerIds.contains(playerId)) continue;`).

For injury recovery, the equivalent question: should a player who is injured but somehow participates (e.g., came on as a substitute despite being injured in the system) have their recovery deferred?

**Recommendation:** Yes — injured players who participated should NOT decrement. The `participatedPlayerIds` tracking already exists and can be reused. If a player is injured but participates (edge case), their injury recovery is deferred to the next round they don't play.

This keeps the suspension and injury recovery rules symmetric.

### 1.10 Clearing injuryType on Recovery

When `injuryRemainingMatches` reaches 0:
- `injured` → `false`
- `injuryRemainingMatches` → `0`
- `injuryType` → **null** (recommendation: clear it)

**Open question:** Does any existing code or UI rely on `injuryType` persisting after recovery? If so, clearing it could cause a gap. The safest default is to clear it (null) since the player is no longer injured and the field's purpose (describe the injury) is retrospective. If a player gets injured again later, `injuryType` gets set to "MATCH_INJURY" again.

### 1.11 Gate: persist-injuries vs. Separate Flag

**Recommendation:** Use existing `persist-injuries` flag for injury recovery.

Rationale:
- Injury recovery is a natural part of injury persistence lifecycle
- Adding a separate `persist-injury-recovery` flag would create redundancy and confusion
- When `persist-injuries=true`: injury mutation applies AND injury recovery lifecycle applies
- When `persist-injuries=false`: neither injury mutation nor recovery applies
- Consistent with how `persist-discipline` gates both discipline mutation AND suspension lifecycle

**Alternative consideration:** Could injury recovery be implemented as a phase of the injury mutation itself (immediate injury application + delayed recovery)? No — the suspension lifecycle is a separate applier called after the round, and injury recovery should follow the same pattern.

---

## 2. Recommended MVP Rules

### Recovery Conditions (ALL must be true)

1. `injured=true` BEFORE the round started
2. `injuryRemainingMatches > 0` (or null — treat null as 0, no recovery)
3. Player's team had an eligible fixture in the current round
4. Player did NOT participate in the current round (not in `participatedPlayerIds`)
5. Player was NOT newly injured in this round (not in `newlyInjuredPlayerIds`)
6. `mutate-career-state=true` AND `persist-injuries=true`

### Recovery Action

```
if injuryRemainingMatches > 1:
    injuryRemainingMatches -= 1
    // injured stays true
else (== 1 or <= 0):
    injured = false
    injuryRemainingMatches = 0
    injuryType = null  // clear on full recovery
```

### Newly Injured Player (injury applied this round)

- Does NOT decrement in the same round
- `injuryRemainingMatches` starts at 2 and first decrement happens next round they don't participate

### Player Without Fixture

- Does NOT decrement (same as suspension lifecycle)
- Only players whose team had a fixture in the round are eligible for recovery

### Injured Player Who Participates

- Does NOT decrement (same as suspension lifecycle)
- Recovery deferred to next round they don't participate

### Null/Edge Case Handling

| Case | Behavior |
|------|----------|
| `injuryRemainingMatches = null` | Treat as 0 — no recovery |
| `injured = null` (not set) | Treat as false — no recovery |
| `injured = false` but `injuryRemainingMatches > 0` | Treat as false — no recovery (stale data) |
| `injured = true` but `injuryRemainingMatches = 0` | injured=true with remaining=0 — no change needed |
| Player ID not found in CareerSave | Skip — continue silently |

---

## 3. Proposed Implementation Design

### 3.1 V24InjuryRecoveryLifecycleApplier (NEW — pure applier)

Package: `com.footballmanager.application.service.simulation.v24`

Behavior:
- Captures pre-round injured player IDs before fixture loop (via LeagueSimulator)
- Captures newly injured player IDs after injury mutation (via comparison)
- Applies decrement after full round, excluding newly injured and participating players
- Uses existing `SessionPlayer` fields — no new schema

```java
public final class V24InjuryRecoveryLifecycleApplier {

    public int applyRecovery(
            CareerSave career,
            int currentRound,
            List<MatchFixture> roundFixtures,
            Set<String> preRoundInjuredPlayerIds,
            Set<String> newlyInjuredPlayerIds,
            Set<String> participatedPlayerIds,
            V24CareerMutationPolicy policy) {

        if (career == null) return 0;
        if (policy == null) return 0;
        if (!policy.isInjuryPersistenceEnabled()) return 0;
        if (preRoundInjuredPlayerIds == null || preRoundInjuredPlayerIds.isEmpty()) return 0;
        if (roundFixtures == null) return 0;
        if (participatedPlayerIds == null) return 0;  // safest: skip if unverified

        // Build set of team IDs that had a fixture this round
        Set<String> teamsWithFixture = roundFixtures.stream()
                .filter(f -> f.getRound() == currentRound)
                .flatMap(f -> Stream.of(
                        f.getHomeTeamId(), f.getAwayTeamId()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<String, String> playerToTeam = buildPlayerToTeamMap(career);

        int recovered = 0;
        for (String playerId : preRoundInjuredPlayerIds) {
            SessionPlayer player = career.getSessionPlayer(playerId);
            if (player == null) continue;

            // Skip if not currently injured
            if (!Boolean.TRUE.equals(player.getInjured())) continue;

            // Skip if newly injured this round
            if (newlyInjuredPlayerIds.contains(playerId)) continue;

            // Skip if participated this round
            if (participatedPlayerIds.contains(playerId)) continue;

            // Check team fixture eligibility
            String teamId = playerToTeam.get(playerId);
            if (teamId == null || !teamsWithFixture.contains(teamId)) continue;

            // Apply recovery
            Integer remaining = player.getInjuryRemainingMatches();
            if (remaining == null || remaining <= 0) continue;

            if (remaining > 1) {
                player.setInjuryRemainingMatches(remaining - 1);
            } else {
                player.setInjured(false);
                player.setInjuryRemainingMatches(0);
                player.setInjuryType(null);  // clear on full recovery
            }
            recovered++;
        }
        return recovered;
    }

    private Map<String, String> buildPlayerToTeamMap(CareerSave career) { ... }
}
```

### 3.2 V24CareerMutationService — No Change Needed

`V24CareerMutationService` already handles injury mutation. Injury recovery lifecycle is a separate applier called after the round in `LeagueSimulator`, not in the per-match mutation service. This is the same pattern as `V24SuspensionLifecycleApplier`.

### 3.3 LeagueSimulator Changes

**Add to `V24RoundMutationTracking`:**
```java
final Set<String> newlyInjuredPlayerIds = new HashSet<>();
```

**New method: `capturePreRoundInjuredPlayerIds(career)`** (mirrors `capturePreRoundSuspendedPlayerIds`):
```java
private Set<String> capturePreRoundInjuredPlayerIds(CareerSave career) {
    Set<String> injured = new HashSet<>();
    for (SessionTeam team : career.getAllSessionTeams()) {
        for (String playerId : career.getSquadPlayerIds(team.getSessionTeamId())) {
            SessionPlayer player = career.getSessionPlayer(playerId);
            if (player == null) continue;
            if (Boolean.TRUE.equals(player.getInjured())) {
                Integer remaining = player.getInjuryRemainingMatches();
                if (remaining != null && remaining > 0) {
                    injured.add(playerId);
                }
            }
        }
    }
    return injured;
}
```

**In `simulateLeagueRound`** — capture pre-round injured before fixture loop:
```java
V24RoundMutationTracking tracking = new V24RoundMutationTracking();
Set<String> preRoundInjured = capturePreRoundInjuredPlayerIds(career);
```

**After `applyV24CareerMutation`** — compute newly injured and add to tracking:
```java
// newly injured = post-round injured - pre-round injured
Set<String> postRoundInjured = capturePreRoundInjuredPlayerIds(career);  // re-capture
Set<String> newlyInjured = new HashSet<>(postRoundInjured);
newlyInjured.removeAll(preRoundInjured);
tracking.newlyInjuredPlayerIds.addAll(newlyInjured);
```

**New method: `applyV24InjuryRecoveryLifecycle`** (mirrors `applyV24SuspensionLifecycle`):
```java
private void applyV24InjuryRecoveryLifecycle(CareerSave career, int round,
                                             List<MatchFixture> allFixtures,
                                             V24RoundMutationTracking tracking,
                                             Set<String> preRoundInjured) {
    try {
        if (!tracking.v24RoundProcessed) return;
        if (!v24MutationPolicy.isInjuryPersistenceEnabled()) return;

        List<MatchFixture> roundFixtures = allFixtures.stream()
                .filter(f -> f.getRound() == round)
                .collect(Collectors.toList());

        int recovered = v24InjuryRecoveryLifecycleApplier.applyRecovery(
                career, round, roundFixtures,
                preRoundInjured,
                tracking.newlyInjuredPlayerIds,
                tracking.participatedPlayerIds,
                v24MutationPolicy);

        if (recovered > 0) {
            log.debug("[V24D6I] Recovered {} injuries for career {} round {}",
                    recovered, career.getData().getCareerId(), round);
        }
    } catch (Exception e) {
        log.warn("[V24D6I] Injury recovery lifecycle failed for career {} round {}: {}, continuing round",
                career.getData().getCareerId(), round, e.getMessage());
    }
}
```

**Call order in `simulateLeagueRound`:**
1. Capture `preRoundInjured` and `preRoundSuspended` before fixture loop
2. Run fixture loop
3. Call `applyV24CareerMutation()` (injury + fatigue + discipline + form)
4. Compute `newlyInjured` and `newlySuspended`
5. Call `applyV24SuspensionLifecycle()` (existing)
6. Call `applyV24InjuryRecoveryLifecycle()` (NEW — after suspension lifecycle)

### 3.4 Configuration / Flag Behavior

No new flags. Uses existing:
- `app.simulation.v24.mutate-career-state=true`
- `app.simulation.v24.persist-injuries=true`

Default false remains safe.

---

## 4. API/DTO/Frontend Impact

| Item | Impact |
|------|--------|
| **No new API endpoints** | Injury recovery happens in LeagueSimulator post-processing |
| **No Redis schema changes** | Uses existing SessionPlayer fields |
| **No new DTOs** | Existing SessionPlayerDTO already exposes injured/injuryType/injuryRemainingMatches |
| **No configuration changes** | Uses existing persist-injuries flag |
| **No frontend changes in V24D6I MVP** | Backend-only |

---

## 5. V24D6I Does NOT Include

- Injury severity scaling (all injuries are 2 matches in V24D6B — no change here)
- Injury prevention or mitigation
- Injury mutation (already done in V24D6B)
- Fatigue/energy recovery (separate concern — V24D6C)
- Form/morale recovery (no such concept in current design)
- Automatic injury occurrence (stochastic model — would be a separate future design)

---

## 6. Open Questions

| # | Question | Recommendation | Risk |
|---|----------|---------------|------|
| OQ1 | Should `injuryType` be cleared on full recovery or retained for historical audit? | Clear to null — player is no longer injured; the field's purpose is active injury description | Low — can be changed later |
| OQ2 | Should injury recovery apply to V23/default path? | No — restrict to V24 path only; V23 has no injury mutation so no recovery makes sense there | Low |
| OQ3 | What if a player has `injuryRemainingMatches = null` (not set)? | Treat as 0 (no recovery) — consistent with null-safe pattern | Low |
| OQ4 | What if `injured=false` but `injuryRemainingMatches > 0` (stale data)? | Treat as not injured — skip recovery | Low |
| OQ5 | Should injury recovery be order-independent with suspension lifecycle? | Yes — call both after the round; order doesn't matter as they operate on different fields | Low |

---

## 7. Risks

| Risk | Mitigation |
|------|-----------|
| Newly injured players accidentally decrement in same round | Compute `newlyInjuredPlayerIds` by comparing pre/post round injured sets before calling recovery applier |
| Player without fixture still decrements | Check `teamsWithFixtureThisRound` before decrementing — same guard as suspension lifecycle |
| Null `injuryRemainingMatches` causes NPE | Null guard: `if (remaining == null \|\| remaining <= 0) continue;` |
| Mixing V23 and V24 paths for same career creates inconsistent recovery | Restrict to V24 path only via `persist-injuries` gate |
| `injuryType` clearing breaks existing UI that reads it | Confirm no UI relies on `injuryType` after `injured=false`; if so, retain it | Low |

---

## 8. Testing Plan

### 8.1 Unit Tests — V24InjuryRecoveryLifecycleApplier

**V24InjuryRecoveryLifecycleApplierTest** (NEW — ~15-18 tests):

| Test Case | Setup | Expected |
|-----------|-------|----------|
| `preExistingInjured_decrements` | player A: injured=true, remaining=2, team has fixture this round, did not participate | remaining→1, injured stays true |
| `preExistingInjured_recoversWhen1` | player A: injured=true, remaining=1, team has fixture this round, did not participate | injured=false, remaining=0, injuryType=null |
| `newlyInjured_doesNotDecrementSameRound` | player A was NOT in preRoundInjuredPlayerIds; V24 INJURY event sets injured=true, remaining=2 this round; player added to newlyInjuredPlayerIds | no recovery decrement; player was not in pre-round set so recovery applier iterates only pre-round injured; newlyInjuredPlayerIds is explicit safety guard |
| `participated_doesNotDecrement` | player A: injured=true, remaining=2, team has fixture, participated this round | no change |
| `noFixture_doesNotDecrement` | player A: injured=true, remaining=2, team has no fixture this round | no change |
| `persistInjuriesFalse_doesNothing` | injured=true, remaining=2, team has fixture, did not participate, policy.isInjuryPersistenceEnabled()=false | no change |
| `mutateCareerStateFalse_doesNothing` | LeagueSimulator calls only when policy enabled, so covered | no change |
| `nullRemaining_handled` | player A: injured=true, remaining=null, team has fixture, did not participate | no change (skip) |
| `injuredFalse_staleRemaining_handled` | player A: injured=false, remaining=2, team has fixture | no change (skip) |
| `playerNotInCareer_handled` | player ID not in career | skip, no exception |
| `multipleInjuredPlayers_decrementTogether` | players A,B: both injured, remaining=2, both teams have fixture this round, neither participated | both decremented |
| `teamEligibility_checked` | player A: injured=true, remaining=2, team has fixture, player didn't participate | decremented |
| `injuryTypeCleared_onFullRecovery` | player A: injured=true, remaining=1, team has fixture, did not participate, recovers | injured=false, remaining=0, injuryType=null |

### 8.2 Service/Orchestration Tests

No new service tests needed. Injury recovery lifecycle is applied in `LeagueSimulator`, not in `V24CareerMutationService`. The `V24CareerMutationService` already has tests for injury mutation.

### 8.3 LeagueSimulator Integration Tests

**V24CareerMutationIntegrationTest** additions (if deterministic engine available):

| Test Case | Expected |
|-----------|----------|
| `injuryRecovery_preExistingInjured_decrements` | pre-injured player decrements after round |
| `injuryRecovery_newlyInjured_doesNotDecrementSameRound` | newly injured player does not decrement same round |
| `injuryRecovery_playerRecoversAt1` | player with remaining=1 recovers to injured=false |
| `injuryRecovery_multiplePlayers_recoverTogether` | multiple injured players all handled correctly |
| `injuryRecovery_noFixture_noDecrement` | player with no fixture does not decrement |
| `injuryRecovery_participated_noDecrement` | injured player who participated does not decrement |
| `injuryRecovery_persistInjuriesFalse_noOp` | when persist-injuries=false, no recovery applied |
| `injuryRecovery_suite651_passes` | full suite still 651, 0 failures |

### 8.4 Full Suite Validation

```bash
mvn test -Dtest=V24InjuryRecoveryLifecycleApplierTest,...
# Expected: 681 full suite tests, 0 failures
```

---

## 9. Implementation Phases (All Complete)

| Phase | Content | Deliverable | Commit/Result |
|-------|---------|-------------|---------------|
| **V24D6I1** | This design document | `V24D6I_INJURY_RECOVERY_LIFECYCLE_DESIGN.md` | `4ad4210` ✓ |
| **V24D6I2** | V24InjuryRecoveryLifecycleApplier + unit tests | New applier class + 22 tests | `7208821` ✓ |
| **V24D6I3** | LeagueSimulator wiring + integration tests | LeagueSimulator changes + 8 integration tests | `7886308` ✓ |
| **V24D6I4** | Full suite validation | 681 tests, 0 failures, BUILD SUCCESS | ✓ |
| **V24D6I5** | Documentation/status update | This doc updated | pending |

---

## 10. Completion Criteria (All Met)

- [x] V24D6I1 design document created and approved (`4ad4210`)
- [x] V24InjuryRecoveryLifecycleApplier implemented with recovery rules above (`7208821`; 22 tests)
- [x] Unit tests pass (22 tests, all scenarios)
- [x] LeagueSimulator wiring complete — pre-round capture, newly injured tracking, post-round recovery call (`7886308`)
- [x] Integration tests pass (8 new tests in V24CareerMutationIntegrationTest)
- [x] Full suite: 681 tests, 0 failures (BUILD SUCCESS)
- [x] No new API/Redis/schema/frontend changes
- [x] target/dist not staged

---

## 11. Comparison with Suspension Lifecycle

| Aspect | Suspension Lifecycle (V24D6D6) | Injury Recovery (V24D6I) |
|--------|-------------------------------|-------------------------|
| Trigger | `persist-discipline=true` | `persist-injuries=true` |
| Pre-round capture | `capturePreRoundSuspendedPlayerIds` | `capturePreRoundInjuredPlayerIds` |
| Newly added exclusion | `newlySuspendedPlayerIds` (RED_CARD events) | `newlyInjuredPlayerIds` (INJURY events) |
| Participation check | Yes — participated players don't serve | Yes — participated players don't recover |
| Fixture eligibility | Yes — team must have fixture | Yes — team must have fixture |
| State clearing at zero | `suspended=false`, `remaining=0` | `injured=false`, `remaining=0`, `injuryType=null` |
| Applier class | `V24SuspensionLifecycleApplier` | `V24InjuryRecoveryLifecycleApplier` (NEW) |
| Call location | `applyV24SuspensionLifecycle()` after fixture loop | `applyV24InjuryRecoveryLifecycle()` after fixture loop |

---

*V24D6I is fully implemented. V24D6I1 design (`4ad4210`), V24D6I2 applier (`7208821`), V24D6I3 wiring (`7886308`), V24D6I4 validation (681 tests, 0 failures).*