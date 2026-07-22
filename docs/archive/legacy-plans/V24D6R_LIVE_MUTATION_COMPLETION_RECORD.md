# V24D6R — Live Match Career Mutation Pipeline

**Status:** COMPLETED
**Branch:** `mvp-1-performance-cleanup`
**Commits:** `b9220c4`, `f6f90ed`, `88e2cab`
**Date:** 2026-06-12
**Test suite:** 814/814 PASS (full suite), 271/271 (V24 focused gate)
**Smoke E2E:** PASSED — Player 11 MAD RED_CARD scenario

---

## 1. What Was Delivered

V24D6R wired the career mutation pipeline into the **live match path** (UI/SSE flow via `RoundController.handleMatchFinished`), completing the mutation story for all three simulation paths:

| Path | Mutation pipeline called? | Before V24D6R | After V24D6R |
|------|---------------------------|--------------|-------------|
| Batch league simulation (`simulateLeagueRound`) | ✅ Always | Already worked | No change |
| Live match path (`persistV24DetailForLiveMatch`) | ❌ Silent | No mutation | Now mutates |
| Single match SSE (`RoundController`) | ❌ Silent | No mutation | Now mutates |

### Root Cause Fixed

`LeagueSimulator.persistV24DetailForLiveMatch()` was only persisting V24 detail to Redis — it never called the mutation pipeline. The appliers existed and were correct, but were only connected to the batch path.

**Fix:** Added `applyLiveMatchCareerMutations(career, v24Result)` helper called after `storagePort.save(detail)` in `persistV24DetailForLiveMatch()`, invoking `v24MutationService.applyMutations(career, v24Result, v24MutationPolicy)`.

---

## 2. Files Changed

### Productive (1 file, +82 lines)
- `src/main/java/com/footballmanager/application/service/simulation/LeagueSimulator.java` — hotfix in `persistV24DetailForLiveMatch()`

### Tests (3 files, +944 lines)
- `src/test/java/com/footballmanager/application/service/simulation/V24LivePathCareerMutationIntegrationTest.java` — 5 tests
- `src/test/java/com/footballmanager/application/service/simulation/V24CareerMutationAvailabilityLifecycleIntegrationTest.java` — 3 tests
- `src/test/java/com/footballmanager/application/service/lineup/V24ManualSelectLineupAvailabilityBlockingTest.java` — 1 test

### New Profile (1 file, +13 lines)
- `src/main/resources/application-v24-mutations.yml`

---

## 3. Reversible Profile — `v24-mutations`

```yaml
# Activated via: --spring.profiles.active=local,v24-mutations
spring:
  config:
    activate:
      on-profile: v24-mutations

app:
  simulation:
    v24:
      mutate-career-state: true
      persist-discipline: true
      persist-injuries: true
      persist-fatigue: false
      persist-form: false
```

**Usage:**
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local,v24-mutations
# or
SPRING_PROFILES_ACTIVE=local,v24-mutations ./start_manager_full.sh
```

---

## 4. Test Results

| Suite | Tests | Result |
|-------|-------|--------|
| V24D6R focused (V24LivePath*, V24SecondYellow*, V24CareerMutationAvailability*, V24ManualSelect*) | 14 | ✅ PASS |
| Broad V24 gate (*V24*Discipline*, *V24*Injury*, *V24*Suspension*, *V24*CareerMutation*, LineupBlockingTest, LineupCommandUseCaseImplAutoSelectTest, V24DetailedMatchEngineSecondYellowTest) | 271 | ✅ PASS |
| Full suite | 814 | ✅ PASS |

**Evolution:** 800 (V24D6Q) → 809 (Phase 1 tests) → 814 (V24D6R hotfix + 14 new tests)

---

## 5. Smoke E2E — Validated Manually

**Career:** `db279d9e-c1d5-4173-82cc-c3b751f1b20a`
**Match:** `0079a4fe-96a5-4b81-a41d-71b310a33e71`
**Player:** Player 11 MAD (`2d7838a0-61cf-42c9-8615-c8632858c9ea`)

**Event timeline:**
- 23' YELLOW_CARD
- 34' YELLOW_CARD
- 34' RED_CARD (second yellow)

**Backend log observed:**
```
[V24D6R-LIVE-MUTATION] careerId=db279d9e-c1d5-4173-82cc-c3b751f1b20a, matchId=0079a4fe-96a5-4b81-a41d-71b310a33e71,
injuriesApplied=0, fatigueApplied=0, disciplineApplied=5, formApplied=0, totalMutations=5
```

**Observed behavior:**
- ✅ Squad UI showed Player 11 MAD: ⛔ Suspended / Unavailable for 1 match
- ✅ Auto Seleccionar excluded the suspended player, showed "Not enough attackers"
- ✅ Stats endpoint showed `yellowCards=2`, `redCards=1`, `matchesMissedSuspendedApprox=1`
- ✅ Stats endpoint returned 200 OK with accumulated data

---

## 6. Pending — V24D6R2

Lifecycle **decrement** (suspended/injured recovery across rounds) is NOT implemented in this phase.

**Reason:** Requires end-of-round participation tracking (`preMatchSuspended`, `newlySuspended`, `participatedPlayerIds`, `preMatchInjured`, `newlyInjured`) through all 6 matches of the round — data the current live architecture does not collect. The `MatchSimulationOrchestrator.processResultsInternal` end-of-round hook exists but is not safe without tracking data.

**V24D6R2 scope:**
- Thread-safe tracking sets in `RoundController` → `processMatchDayResults` → `processResultsInternal` → `LeagueSimulator`
- Pre-round capture of pre-match suspended/injured state before first match
- End-of-round hook in `MatchSimulationOrchestrator.processResultsInternal` calling `LeagueSimulator.applyEndOfRoundLiveLifecycle(...)`
- Decrement logic: `suspensionRemainingMatches--`, `injuryRemainingMatches--` when player did NOT participate
- Tests T5–T8 of original V24D6R plan

---

## 7. Commit History

```
88e2cab fix(V24D6R): apply career mutations in live match path   ← hotfix
f6f90ed chore(V24D6R): add v24 mutations smoke profile          ← profile YAML
b9220c4 test(V24D6R): add career mutation availability lifecycle coverage  ← tests
d7cc800 fix(V24D6Q): emit red card on second yellow
9819f0f chore: stop tracking frontend sub-repo files
```

---

## 8. Rules Compliance

- ✅ No push during work — only after smoke passed
- ✅ No code changes outside the 3 authorized test files + 1 hotfix
- ✅ `application.yaml` untouched
- ✅ `application-local.yml` untouched
- ✅ Frontend sub-repo untouched
- ✅ `squad-editor-modal/` untouched
- ✅ No reset, no clean
- ✅ Profile YAML created, not modifying existing configs
