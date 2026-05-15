# V24D6D6 — Suspension Lifecycle/Decrement Audit + Design

**Status:** V24D6D6 AUDIT + DESIGN COMPLETE — no implementation code written
**Branch:** `mvp-1-performance-cleanup`
**Latest backend implementation commit:** `0f4ab39` (V24D6D5 discipline wiring complete)
**Latest docs commit:** `7b4486b` (docs: update V24D6D discipline persistence status)
**Tests:** 558, 0 failures (unchanged — design-only phase)
**Mutation focused gate:** 144, 0 failures
**No production code changes in this phase**

---

## 1. Executive Summary

V24D6D2-D5 implemented discipline persistence: RED_CARD events set `SessionPlayer.suspended=true` and `suspensionRemainingMatches=1`. The suspension lifecycle — decrementing the counter and clearing `suspended` after a player serves the ban — is not yet implemented.

This document audits the complete round simulation flow, existing injury lifecycle (which does not exist), availability/lineup blocking logic, and proposes a concrete suspension lifecycle design for V24D6D6 implementation.

**Key finding:** No automatic lifecycle exists for injuries, and none should be assumed for suspensions. Suspended players are NOT currently blocked from lineup selection — `LineupHelper.validatePlayerFitness()` checks energy and injured status but does NOT check the `suspended` flag. Therefore, V24D6D6 lifecycle MUST verify non-participation before decrementing, or defer the decrement until lineup blocking exists.

**Recommended MVP lifecycle:** Decrement only pre-round suspended players who did NOT participate in any fixture of the completed round. Snapshot suspended players before the round loop. After the round loop, exclude newly red-carded players AND exclude any pre-round suspended player who appeared in starting XI, match timeline events, or any available participation source. Only players who were suspended before the round and did not participate are considered to have served one suspension match.

---

## 2. Current Implemented Discipline State

### SessionPlayer Discipline Fields (V24D6D2)

| Field | Type | Default | Null-safe getter |
|-------|------|---------|------------------|
| `yellowCards` | `Integer` | `0` | `yellowCards != null ? yellowCards : 0` |
| `redCards` | `Integer` | `0` | `redCards != null ? redCards : 0` |
| `suspended` | `Boolean` | `false` | `suspended != null ? suspended : false` |
| `suspensionRemainingMatches` | `Integer` | `0` | `suspensionRemainingMatches != null ? suspensionRemainingMatches : 0` |

### V24DisciplineMutationApplier Behavior (V24D6D3)

On RED_CARD event:
```java
player.setRedCards(player.getRedCards() + 1);        // increment redCards
player.setSuspended(true);                            // set suspended = true
player.setSuspensionRemainingMatches(1);             // set remaining = 1
```

On YELLOW_CARD event:
```java
player.setYellowCards(player.getYellowCards() + 1);  // increment yellowCards
// No suspension effect in MVP
```

**No decrement logic exists anywhere in the mutation applier or service.**

### V24CareerMutationService Mutation Order (V24D6D4)

```java
// LeagueSimulator.applyV24CareerMutation() calls:
v24MutationService.applyMutations(career, v24Result, v24MutationPolicy);

// V24CareerMutationService.applyMutations() order:
1. injury applier   (if policy.isInjuryPersistenceEnabled())
2. fatigue applier (if policy.isFatiguePersistenceEnabled())
3. discipline applier (if policy.isDisciplinePersistenceEnabled())
```

No suspension lifecycle phase exists in the mutation pipeline.

---

## 3. Current Round/Mutation Flow Audit

### LeagueSimulator.simulateLeagueRound() — Full Loop

**File:** `src/main/java/com/footballmanager/application/service/simulation/LeagueSimulator.java:139-159`

```java
public void simulateLeagueRound(CareerSave career, int round) {
    TournamentState tournamentState = career.getTournamentState();
    List<MatchFixture> allFixtures = tournamentState.getFixtures();

    for (MatchFixture fixture : allFixtures) {
        if (fixture.getRound() != round) continue;
        if (!fixture.canBeSimulated()) continue;

        int homeOvr = calculateTeamOVR(career, fixture.getHomeTeamId());
        int awayOvr = calculateTeamOVR(career, fixture.getAwayTeamId());

        if (useV24DetailedEngine) {
            simulateWithV24Engine(career, fixture, homeOvr, awayOvr, tournamentState);
        } else if (useV23LeagueEngine) {
            simulateWithV23Engine(fixture, homeOvr, awayOvr, tournamentState);
        } else {
            simulateWithDefaultEngine(fixture, homeOvr, awayOvr, tournamentState);
        }
    }
}
```

**Key behaviors:**
- Simulates ALL fixtures for the round, not just the user's team.
- Three engine paths — all round-level, not per-match.
- V24 path calls `applyV24CareerMutation()` after each fixture result is recorded.

### Per-Fixture V24 Path

**File:** `LeagueSimulator.java:226-240`
```java
private void simulateWithV24Engine(CareerSave career, MatchFixture fixture, ...) {
    // ... OVR computation and context building ...
    V24DetailedMatchResult v24Result = v24Engine.simulate(context, seed);
    tournamentState.recordMatchResult(fixture.getMatchId(), resultData);  // records in-memory
    // V24D6B3: apply career mutation if enabled
    applyV24CareerMutation(career, v24Result);  // mutates in-memory CareerSave
    if (persistDetail) persistV24Detail(...);     // Redis snapshot
}
```

### applyV24CareerMutation()

**File:** `LeagueSimulator.java:299-328`
```java
private void applyV24CareerMutation(CareerSave career, V24DetailedMatchResult v24Result) {
    try {
        V24CareerMutationResult mutationResult =
                v24MutationService.applyMutations(career, v24Result, v24MutationPolicy);
        // ... logging of injuryApplied / fatigueApplied / disciplineApplied counts
    } catch (Exception e) {
        log.warn(...);  // best-effort, does not fail the round
    }
}
```

### CareerSave Persistence After Round

**File:** `MatchSimulationOrchestrator.java:156-157`
```java
careerSessionService.saveCareer(career).block(java.time.Duration.ofSeconds(10));
```

Called after ALL fixtures in the round have been simulated and mutated. This is the **only persistence point** for the round. The `CareerSave` object passed into `simulateLeagueRound()` is the same object mutated in-memory throughout the loop — it is not re-fetched between fixtures.

### Round Advancement

**File:** `MatchSimulationOrchestrator.java:138-154`

```java
career.getTournamentState().finishMatchDay();       // IN_MATCH → POST_MATCH
career.getTournamentState().enterWaitingUserPhase(); // may throw if already done
// ...
career.getTournamentState().setCurrentRound(currentRound + 1);  // round incremented here
career.getTournamentState().setCareerPhase(CareerPhase.WAITING_USER);
```

### Critical Observation: Lifecycle Opportunities

There are two points where lifecycle logic could run:

1. **Per-fixture inside the loop** — `applyV24CareerMutation()` runs after each fixture's result is recorded. However, since ALL fixtures are simulated in the same `CareerSave` object before any are persisted, decrementing suspensions after only some fixtures have been simulated could lead to inconsistent state if a suspended player plays for a team whose fixture has already been processed but other fixtures remain.

2. **After the full round loop** — Before `MatchSimulationOrchestrator.processResultsInternal()` calls `careerSessionService.saveCareer()`, the full round loop is complete. This is the safest point to apply lifecycle logic: all fixtures processed, all mutations applied, just before persistence.

**Recommendation: Lifecycle decrement must run after the full round loop, before CareerSave persistence.**

---

## 4. Injury Lifecycle Audit

### Is injuryRemainingMatches ever decremented?

**No.** The field is set but never decremented anywhere in production code.

| File | Line | Usage |
|------|------|-------|
| `SessionPlayer.java` | 39 | Field declaration |
| `SessionPlayer.java` | 127 | Initialized to `0` in `initDefaults()` |
| `SessionPlayer.java` | 184 | Getter |
| `SessionPlayer.java` | 211 | Setter |
| `V24InjuryMutationApplier.java` | 53 | Sets to `DEFAULT_INJURY_DURATION_MATCHES` (2) on injury applied |
| `SessionPlayerDTO.java` | 26 | DTO field |
| Test files | various | Assertions only |

### Is injured=false ever set automatically?

**No.** Exactly one production code path sets `injured=true` (`V24InjuryMutationApplier.java:51`). No production code path ever sets `injured=false`. The only `setInjured(false)` calls are in test setup.

### Is there any round-completion injury recovery hook?

**No.** `TournamentState.advanceRound()` at line 161 of `TournamentState.java` only increments the round counter. It has no injury processing.

### V24InjuryMutationApplier Full Body

```java
public int applyInjuries(CareerSave career, V24DetailedMatchResult result, V24CareerMutationPolicy policy) {
    int appliedCount = 0;
    for (V24MatchEvent event : result.timeline().events()) {
        if (event.type() != V24MatchEventType.INJURY) continue;
        String playerId = event.playerId();
        if (playerId == null || playerId.isBlank()) continue;
        SessionPlayer player = career.getSessionPlayer(playerId);
        if (player == null) continue;
        if (Boolean.TRUE.equals(player.getInjured())) continue;  // do not overwrite
        player.setInjured(true);
        player.setInjuryType(DEFAULT_INJURY_TYPE);  // "MATCH_INJURY"
        player.setInjuryRemainingMatches(DEFAULT_INJURY_DURATION_MATCHES);  // 2
        appliedCount++;
    }
    return appliedCount;
}
```

### Conclusion

**Injury lifecycle was designed but never implemented.** The `injuryRemainingMatches` countdown that appears in design documents is not present in code. This means:

1. V24D6D6 suspension lifecycle must be **fully self-contained** — it cannot reuse any existing injury decrement infrastructure that does not exist.
2. Both injury recovery and suspension lifecycle are **new lifecycle mechanisms** that must be designed and built together if both are to be implemented.
3. For V24D6D6 MVP, suspension lifecycle should be designed as a standalone `V24SuspensionLifecycleApplier` that:
   - Reads `suspended` and `suspensionRemainingMatches`
   - Decrements counters for pre-match suspended players after round completion
   - Clears `suspended` when counter reaches 0
   - Does NOT depend on any injury lifecycle that does not exist

---

## 5. Availability/Lineup Audit

### Injured Players — BLOCKED

**`LineupCommandUseCaseImpl.java:124`** — `performAutoSelect()` filters out injured players:
```java
.filter(p -> !p.getInjured())
```

**`LineupHelper.java:113`** — `validatePlayerFitness()` throws exception for injured players in manual XI:
```java
if (player.getInjured()) {
    throw new IllegalArgumentException("Player " + player.getName() + " is injured");
}
```

### Exhausted Players (energy ≤ 20) — BLOCKED

**`LineupHelper.java:109`** — `validatePlayerFitness()` blocks energy ≤ 20:
```java
if (player.getEnergy() <= 20) {
    throw new IllegalArgumentException("Player " + player.getName() + " has low fitness (" + player.getEnergy() + "%)");
}
```

**`LineupCommandUseCaseImpl.java:123`** — `performAutoSelect()` also filters energy ≤ 20:
```java
.filter(p -> p.getEnergy() > 20)
```

### Suspended Players — NOT BLOCKED (Known Gap)

**`LineupHelper.java:107-116`** — `validatePlayerFitness()` checks neither `suspended` nor `suspensionRemainingMatches`:
```java
public void validatePlayerFitness(List<SessionPlayer> players) {
    for (SessionPlayer player : players) {
        if (player.getEnergy() <= 20) { /* throw */ }
        if (player.getInjured()) { /* throw */ }
        // NO check for player.getSuspended()
    }
}
```

### Conclusion

- Injured and exhausted players are blocked from both auto-select and manual lineup confirmation.
- Suspended players are **not blocked** — this is a documented gap from V24D6D.
- Adding a `suspended` check to `LineupHelper.validatePlayerFitness()` is a trivial one-line addition, but it is **out of scope for V24D6D6** (deferred to V24D6D7 DTO/UI audit, or explicitly scoped as a separate change).
- **V24D6D6 must not assume suspended players are blocked from lineup.** The lifecycle decrements the counter, but until lineup blocking exists, a suspended player could be selected — this is a known gap that must be documented in V24D6D6 non-goals.

---

## 6. Lifecycle Timing Options

### Option A — Decrement at start of next simulateLeagueRound()

**Mechanism:** At the start of `simulateLeagueRound()`, before processing any fixtures, snapshot all currently suspended players and decrement their counters.

**Problem:** If the suspended player was NOT blocked from lineup (which they are not today), they could be selected and play in the match. Decrementing before the match means the suspension is cleared before the player actually misses a game.

**Verdict: Reject for MVP.** Not safe without lineup blocking.

### Option B — Decrement after user-team match simulation, only if player did not participate

**Mechanism:** After the user's team match is simulated, check which of the user's suspended players were in the starting XI, and only decrement those who did NOT play.

**Problem:** The current `simulateLeagueRound()` processes ALL fixtures in a round, not just the user's team. The user's team match is one of many. Determining "did this player participate" requires comparing starting XI against match events — complex and fragile. Also, CPU teams' suspended players would not have their suspensions served.

**Verdict: Reject for MVP.** Over-complicated and incomplete for round-level simulation.

### Option C — Decrement after full round completion for all suspended players on teams that had a fixture

**Mechanism:** After all fixtures in the round are processed, snapshot the set of suspended players before the round started, then decrement all of them. If lineup blocking does not prevent selection, a player who was suspended AND played would still have their suspension served incorrectly.

**Verdict: Acceptable only if lineup blocking is in place before V24D6D6 ships. Otherwise, add a precondition check.**

### Option D — Two-phase lifecycle: backend counter + future lineup blocking

**Mechanism:** V24D6D6 implements only the counter decrement (backend-only). Lineup blocking comes in V24D6D7 or later. Until lineup blocking exists, suspended players who are selected and play will have their suspension served anyway (counter decrements) — which may or may not be the intended behavior.

**Verdict: Acceptable with explicit non-goal documentation.**

### Option E — Defer decrement entirely until lineup blocking exists

**Mechanism:** Keep current V24D6D5 behavior — `suspended` and `suspensionRemainingMatches` are sticky. A player red-carded in match N remains suspended until V24D6D6 (or later) with lineup blocking.

**Problem:** If `persist-discipline=true` is enabled in production without lineup blocking, a red-carded player could be selected every round but never actually serve the suspension — they play through it. This is game-breaking.

**Verdict: Reject.** Cannot enable `persist-discipline=true` in production without a lifecycle mechanism.

### Recommended: Option C with participation verification

Implement Option C only with a mandatory participation check.

Lifecycle may run after the full round loop, but it must NOT blindly decrement every pre-round suspended player. A player serves a suspension only if:
1. the player was suspended before the round started;
2. `suspensionRemainingMatches > 0`;
3. the player's team had an eligible fixture in the round;
4. the player did not participate in the round, based on reliable participation evidence;
5. the player was not newly red-carded in the same round.

If participation cannot be reliably derived, the implementation must skip decrement and log/report the limitation. It must not assume that the pre-match snapshot alone proves that the player missed the match.

Lineup blocking remains deferred, but the lifecycle remains safe before lineup blocking exists because it excludes players who participated.

---

## 7. Recommended MVP Lifecycle Semantics

### Suspension Lifecycle Invariant

A suspension is served only when ALL of the following are true:
- `player.suspended == true` before the round started
- `suspensionRemainingMatches > 0`
- The player's team had an eligible fixture in the round
- The player did NOT participate in that fixture (not in starting XI, not in match events)
- The player was NOT newly red-carded in the same round

**This invariant is mandatory.** If participation cannot be verified, V24D6D6 implementation must stop and report instead of decrementing blindly.

### Warning

> **Until lineup blocking is implemented, decrementing blindly after a round is unsafe.** A suspended player who is selected and plays would have their suspension incorrectly served. V24D6D6 implementation must either verify non-participation or defer the decrement until lineup blocking exists.

### Rule 1: Snapshot Before Round

Before `simulateLeagueRound()` begins iterating fixtures, capture the set of all player IDs where `suspended == true` at that moment. This is the "pre-match snapshot."

```java
Set<String> preMatchSuspendedPlayerIds = career.getAllSessionPlayerIds().stream()
    .filter(id -> {
        SessionPlayer p = career.getSessionPlayer(id);
        return p != null && Boolean.TRUE.equals(p.getSuspended());
    })
    .collect(Collectors.toSet());
```

### Rule 2: Process All Fixtures (No Changes to Existing Mutation Order)

The existing mutation pipeline (injury → fatigue → discipline) runs as normal after each fixture. Discipline mutation sets `suspended=true` and `suspensionRemainingMatches=1` for any player who received a RED_CARD in this round.

### Rule 3: Collect Participation Evidence After Round Loop

After all fixtures have been processed, collect the set of all player IDs who appeared in any match this round. `participatedPlayerIds` can be built from:
- Starting XI from the V24 match context for each fixture
- `V24DetailedMatchResult.timeline()` — collect all `playerId` and `relatedPlayerId` values from all events
- Substitution events
- Any existing match participation source

```java
Set<String> participatedPlayerIds = new HashSet<>();
for (V24DetailedMatchResult result : roundResults) {
    for (V24MatchEvent event : result.timeline().events()) {
        if (event.playerId() != null) participatedPlayerIds.add(event.playerId());
        if (event.relatedPlayerId() != null) participatedPlayerIds.add(event.relatedPlayerId());
    }
    // Also add starting XI from context if accessible
}
```

If participation cannot be reliably derived for a given implementation, V24D6D6 implementation **must stop and report** (log a warning and skip decrement) rather than decrementing blindly.

### Rule 4: Decrement Only Eligible Pre-Match Suspended Players

For each player in `preMatchSuspendedPlayerIds`:
1. Player is NOT in `newlySuspendedPlayerIds` (not red-carded this round)
2. Player is NOT in `participatedPlayerIds` (did not play)
3. Player's team had a fixture in this round (if BYE weeks are modeled)
4. `suspensionRemainingMatches > 0`

```java
for (String playerId : preMatchSuspendedPlayerIds) {
    // Skip if player was newly red-carded this round (already handled by discipline)
    if (newlySuspendedPlayerIds.contains(playerId)) continue;

    // Skip if player participated in any fixture this round
    if (participatedPlayerIds.contains(playerId)) continue;

    SessionPlayer player = career.getSessionPlayer(playerId);
    if (player == null) continue;

    Integer remaining = player.getSuspensionRemainingMatches();
    if (remaining != null && remaining > 0) {
        int newRemaining = remaining - 1;
        player.setSuspensionRemainingMatches(newRemaining);
        if (newRemaining <= 0) {
            player.setSuspended(false);
            player.setSuspensionRemainingMatches(0);
        }
    }
}
```

### Rule 5: BYE Week / No Fixture Handling

A player on a team with no fixture in the current round should NOT have their suspension served. If the data model supports BYE weeks, the lifecycle applier must check team fixture participation before decrementing. If BYE weeks are not modeled, document the limitation — all pre-match suspended players are decremented regardless of fixture presence.

### Rule 6: Flag Gating

Lifecycle decrement is gated by `policy.isDisciplinePersistenceEnabled()` — the same flag that gates discipline mutation. No separate flag is needed.

### V24D6D7 Relation

V24D6D7 remains the DTO/API/frontend visibility audit. Lineup hard-blocking (suspending players from selection) is a separate phase and is not required for V24D6D6 to function, provided the participation check in Rule 4 is implemented. The lifecycle is safe even before hard-blocking exists, as long as the participation verification is in place.

---

## 8. Proposed Implementation Design for V24D6D6

### Class: V24SuspensionLifecycleApplier

Location: `src/main/java/com/footballmanager/application/service/simulation/v24/V24SuspensionLifecycleApplier.java`

### API

```java
public class V24SuspensionLifecycleApplier {

    /**
     * Applies suspension lifecycle decrement for pre-match suspended players
     * after a full round of fixtures has been processed.
     *
     * Decrement is ONLY applied when participation can be verified:
     * - Player was suspended before the round started (preMatchSuspendedPlayerIds)
     * - Player did NOT participate in any fixture this round (participatedPlayerIds)
     * - Player was NOT newly red-carded this round (newlySuspendedPlayerIds)
     * - Player's team had a fixture this round (checked via roundFixtures)
     *
     * If participation cannot be verified, no decrement occurs.
     *
     * @param career Current CareerSave (mutated in-place)
     * @param currentRound The round number that just completed
     * @param roundFixtures All fixtures for the current round (to check team participation)
     * @param preMatchSuspendedPlayerIds Player IDs where suspended=true BEFORE the round started
     * @param newlySuspendedPlayerIds Player IDs that received a RED_CARD in this round
     *        (excluded from decrement to avoid clearing new suspensions immediately)
     * @param participatedPlayerIds Player IDs who appeared in any fixture this round
     *        (from V24DetailedMatchResult timeline events, starting XI, substitution events)
     * @param policy Mutation policy (gated by isDisciplinePersistenceEnabled)
     * @return Number of players whose suspension status changed (for logging/auditing)
     */
    public int applyServedSuspensions(
            CareerSave career,
            int currentRound,
            List<MatchFixture> roundFixtures,
            Set<String> preMatchSuspendedPlayerIds,
            Set<String> newlySuspendedPlayerIds,
            Set<String> participatedPlayerIds,
            V24CareerMutationPolicy policy) {
        // ...
    }
}
```

### Location in V24CareerMutationService

Option A — Add as 4th phase inside `applyMutations()`:
```java
public V24CareerMutationResult applyMutations(CareerSave career, V24DetailedMatchResult result, V24CareerMutationPolicy policy) {
    int injuriesApplied = injuryApplier.applyInjuries(...);    // phase 1
    int fatigueApplied = fatigueApplier.applyFatigue(...);     // phase 2
    int disciplineApplied = disciplineApplier.applyDiscipline(...); // phase 3

    // NEW: phase 4 — suspension lifecycle
    // NOTE: This is per-fixture, but lifecycle needs round-level snapshot.
    // The snapshot must be captured BEFORE the round loop in LeagueSimulator,
    // and passed through. This requires changing applyMutations signature.
    // See option B for preferred approach.
}
```

Option B — Add in `LeagueSimulator` after the full round loop (recommended):
```java
// In LeagueSimulator.simulateLeagueRound(), after the for-loop over all fixtures:
// 1. Capture pre-match snapshot
Set<String> preMatchSuspendedPlayerIds = capturePreRoundSuspendedPlayers(career);
// 2. Collect participated player IDs from all V24DetailedMatchResults this round
Set<String> participatedPlayerIds = collectParticipatedPlayerIds(roundResults);
// 3. Existing mutation calls per fixture inside the loop (discipline sets newlySuspendedPlayerIds)
// 4. After loop completes:
if (v24MutationPolicy.isDisciplinePersistenceEnabled()) {
    v24SuspensionLifecycleApplier.applyServedSuspensions(
        career, round, allFixturesThisRound,
        preMatchSuspendedPlayerIds,
        newlySuspendedPlayerIds,
        participatedPlayerIds,
        v24MutationPolicy);
}
```

**Option B is preferred** because:
1. Lifecycle operates at round level, not per-fixture
2. Pre-match snapshot is captured once before the loop
3. Does not change `V24CareerMutationService.applyMutations()` signature
4. Keeps lifecycle logic in the orchestration layer (LeagueSimulator) where round-level context exists

### Flag Behavior

| mutate-career-state | persist-discipline | Lifecycle behavior |
|---------------------|--------------------|--------------------|
| false | false | No discipline mutation, no lifecycle |
| true | false | No discipline mutation, no lifecycle |
| false | true | No discipline mutation, no lifecycle (master gate required) |
| true | true | Discipline mutation applies, lifecycle decrements |

Lifecycle requires both `mutate-career-state=true` AND `persist-discipline=true` — same as discipline mutation. No separate flag needed.

### Ordering Guarantee

1. Capture `preMatchSuspendedPlayerIds` snapshot BEFORE the round loop
2. Inside loop: each fixture's `applyV24CareerMutation()` adds new RED_CARDs; collect `participatedPlayerIds` from each V24DetailedMatchResult timeline
3. After loop: lifecycle applier runs on snapshot players, excluding newly suspended AND excluding participants
4. `careerSessionService.saveCareer()` persists the final state

---

## 9. Proposed Tests

### V24D6D6 Unit Tests: V24SuspensionLifecycleApplierTest

**Constructor and Policy:**
1. `policyDisabled_noLifecycleChange()` — when `isDisciplinePersistenceEnabled()=false`, no changes made

**Core Lifecycle Logic:**
2. `suspendedBeforeMatch_remainingOne_clearsAfterServed()` — player suspended with `remaining=1`, not participated, team had fixture → after lifecycle → `suspended=false, remaining=0`
3. `suspendedBeforeMatch_remainingTwo_decrementsToOneStillSuspended()` — `remaining=2`, not participated, team had fixture → after → `remaining=1, suspended=true`
4. `notSuspended_noChange()` — player with `suspended=false` is untouched
5. `nullFields_defaultSafely()` — null `suspensionRemainingMatches` treated as 0, no change

**Participation Verification:**
6. `preExistingSuspendedPlayerAccidentallySelected_doesNotDecrement()` — player was suspended before round, was selected and played in starting XI this round → NOT decremented (participation check prevents incorrect clear)
7. `preExistingSuspendedPlayerNotSelected_decrements()` — player was suspended before round, was NOT selected/played → decremented and cleared if remaining=1
8. `newlyRedCardedThisMatch_notDecrementedSameMatch()` — player just RED_CARD'd with `remaining=1` is NOT in preMatchSnapshot → not decremented
9. `preExistingSuspendedAndNewRed_sameRound_notCleared()` — player was in snapshot with `remaining=1`, also received RED_CARD in this round (reset to `remaining=1`) → lifecycle excludes via newlySuspendedPlayerIds → `suspended=true, remaining=1` (not cleared)

**BYE Week / No Fixture:**
10. `playerOnTeamWithNoFixture_noDecrement()` — if team had no fixture this round, player not decremented (requires fixture list check)

**Independence:**
11. `lifecycleFailure_doesNotAffectInjuryFatigueApplied()` — if lifecycle throws, injury/fatigue already in career are unchanged

### V24D6D6 Service Orchestration Tests: add to V24CareerMutationServiceTest

12. `lifecycleApplierReceivesCorrectPreMatchSnapshot()` — mock or verify lifecycle applier is called with correct player set
13. `lifecycleFailureDoesNotEraseDisciplineSuccess()` — if lifecycle throws, discipline mutations already applied are preserved

### V24D6D6 Integration Tests: add to V24CareerMutationIntegrationTest

14. `redCardInRoundN_playerStillSuspendedAfterRoundN()` — RED_CARD in round N → `suspended=true, remaining=1` after the round; lifecycle must NOT decrement newly red-carded players in the same round. The suspension can only be served in a later eligible round where the player was pre-round suspended and did not participate.
15. `preExistingSuspension_decrementsAfterNextRound()` — player suspended going into round N (remaining=1), not participated → after round N lifecycle → `suspended=false` (served)
16. `preExistingSuspension_clearsWhenRemainingReachesZero()` — `remaining=2`, not participated → after round N lifecycle → `remaining=1, suspended=true`; after round N+1 lifecycle → `remaining=0, suspended=false`
17. `preExistingSuspendedPlayerPlayedInRound_notDecremented()` — player suspended going into round N, was selected and played in starting XI this round → after lifecycle → NOT decremented, `remaining=1, suspended=true`
18. `persistDisciplineFalse_noLifecycleChange()` — `persist-discipline=false`, master=true → lifecycle skipped
19. `masterFalse_noLifecycleChange()` — `mutate-career-state=false` → no lifecycle regardless of persist-discipline
20. `v24Disabled_noLifecycleChange()` — V24 engine disabled → no lifecycle (no V24 result, no lifecycle)
21. `newlyRedCardedAndPreSuspended_sameRound_notClearedPrematurely()` — player was suspended `remaining=1` going into round N; also received RED_CARD in round N → after lifecycle → `suspended=true, remaining=1` (not cleared)

---

## 10. Risks / Open Questions

### Risk 1: Lineup Blocking Not Implemented

Until `LineupHelper.validatePlayerFitness()` checks `player.getSuspended()`, a suspended player can be selected in the starting XI. If `persist-discipline=true` is enabled without lineup blocking, a suspended player could play and have their suspension incorrectly served.

**Mitigation:** Document as a critical non-goal. Do not recommend enabling `persist-discipline=true` in production until lineup blocking (V24D6D7) is implemented. Add a warning in the design doc.

### Risk 2: BYE Week / No Fixture Handling

If a team has no fixture in a round (BYE week or schedule gap), a suspended player on that team should NOT have their suspension served that round. The current design does not handle this — all pre-match suspended players are decremented regardless.

**Mitigation:** Open question for V24D6D6 implementation. If BYE weeks exist in the data model, the lifecycle applier must check team fixture participation. If BYE weeks are not modeled, document the limitation.

### Risk 3: Interaction with Injury Lifecycle

Both injury recovery and suspension lifecycle are missing. If V24D6D6 implements suspension lifecycle, and a future V24D6E (or later) implements injury recovery using the same pattern, the two lifecycles must not interfere.

**Mitigation:** Keep suspension lifecycle in a dedicated `V24SuspensionLifecycleApplier`. Do not mix with injury recovery logic.

### Risk 4: Round Loop Snapshot vs. Per-Fixture Mutation

The pre-match snapshot is captured once before the round loop. Each fixture's mutation runs inside the loop and may set `suspended=true` on new players. After the loop, the lifecycle applier runs on the snapshot, excluding newly suspended players.

**Edge case:** If a player was in the pre-match snapshot AND received another RED_CARD in the current round, they are in both `preMatchSuspendedPlayerIds` AND `newlySuspendedPlayerIds`. The lifecycle applier must correctly exclude them using `newlySuspendedPlayerIds`.

### Risk 5: V24CareerMutationResult Counting

The result object tracks `disciplineApplied` (discipline mutations applied) but does not track lifecycle decrements. A future audit of `V24CareerMutationResult` fields may want to add `suspensionsServed` for observability.

**Mitigation:** Not required for MVP. Can be added later.

### Open Questions

1. **BYE weeks:** Does the data model support teams with no fixture in a given round? If yes, should suspended players on those teams be skipped?
2. **Pre-match snapshot for CPU teams:** The current `simulateLeagueRound()` iterates all fixtures. Should the pre-match snapshot include suspended players on CPU teams? Yes — all teams' suspensions should be served when their matches are played. The implementation should handle all teams, not just the user's team.
3. **Yellow card accumulation threshold:** Not in V24D6D6 scope, but the lifecycle applier could be extended in the future to check `yellowCards >= N` and automatically apply suspension. Document this as a future extension point.
4. **Starting XI availability at lifecycle time:** The lifecycle applier must not rely on pre-match snapshot alone as proof that a player served a suspension. The implementation must derive `participatedPlayerIds` from reliable match participation evidence. If participation cannot be reliably derived, V24D6D6 must skip decrement for that player/round and log/report the limitation rather than clearing the suspension.

---

## 11. Non-Goals

The following are explicitly NOT in V24D6D6 scope:

- **No DTO/API/frontend exposure** — suspension visibility through CareerPlayerDto/CareerSquadDto is V24D6D7
- **No lineup blocking** — checking `player.getSuspended()` in `LineupHelper.validatePlayerFitness()` is deferred to V24D6D7
- **No yellow-card suspension threshold** — e.g., 5 yellows → 1-match ban is future work
- **No multi-match bans beyond the existing `suspensionRemainingMatches` counter** — current model supports N-match bans if `suspensionRemainingMatches > 1` is set; MVP red card sets to 1
- **No appeal/reduction logic**
- **No competition-specific discipline rules**
- **No production flag default changes** — `persist-discipline` remains `false`
- **No injury recovery lifecycle** — `injuryRemainingMatches` decrement is separate work
- **No modification of existing injury, fatigue, or discipline mutation appliers**
- **No change to V24CareerMutationService mutation order**
- **No change to LeagueSimulator constructor or initialization**
- **No new flag for suspension lifecycle** — uses `persist-discipline` + `mutate-career-state`

---

## 12. Completion Criteria

- [x] Round/mutation flow audited (LeagueSimulator, MatchSimulationOrchestrator)
- [x] Injury lifecycle audited (confirmed: does not exist)
- [x] Availability/lineup blocking audited (injured/exhausted blocked, suspended not blocked)
- [x] All five lifecycle timing options evaluated
- [x] Recommended Option C design with snapshot-before/lifecycle-after semantics documented
- [x] Pre-match snapshot mechanism defined
- [x] Newly suspended exclusion mechanism defined (avoids clearing RED_CARD set in same round)
- [x] BYE week handling documented as open question
- [x] Flag behavior documented (no new flag needed)
- [x] V24SuspensionLifecycleApplier API proposed (with participation exclusion: preMatchSuspendedPlayerIds + newlySuspendedPlayerIds + participatedPlayerIds)
- [x] Integration point in LeagueSimulator (after round loop, before save) specified
- [x] Test plan enumerated (21 tests: 11 unit + 2 service + 8 integration)
- [x] Risks documented with mitigations
- [x] Open questions listed
- [x] Non-goals explicitly listed
- [x] No src/main or src/test changes in this phase
- [x] No target/ staging

---

## Validation Commands

```bash
git status --short
git diff --stat
```

**Expected after V24D6D6 design only:**
- Only `V24D6D6_SUSPENSION_LIFECYCLE_DESIGN.md` in output of `git status --short`
- `git diff --stat` shows only this new file
- No changes to src/main or src/test