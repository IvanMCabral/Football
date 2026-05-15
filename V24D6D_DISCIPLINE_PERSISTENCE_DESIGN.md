# V24D6D — Discipline/Cards Persistence Design Document

**Status:** V24D6D1 DESIGN ONLY
**Branch:** `mvp-1-performance-cleanup`
**Latest backend implementation commit:** `0dc184a`
**Latest docs commit:** `22c8668`
**Tests:** 521, 0 failures
**Mutation focused gate:** 115, 0 failures
**No production code changes in V24D6D1**

---

## 1. Executive Summary

V24 already generates match-local card events. During a match:

- `V24DisciplineModel` evaluates foul probability and decides when a player receives a yellow or red card.
- `V24PlayerMatchState` tracks `yellowCards` and `redCard` per player for the duration of that match.
- `V24DetailedMatchEngine` wires foul → yellow card → second-yellow → red card event generation.
- `V24PlayerRatingModel` applies performance penalties for yellow (-0.3) and red (-1.5) cards post-match.
- `V24PlayerMatchStatsModel` records card counts per match.

V24 detailed match data and player ratings can therefore display per-match card statistics. However, **no persistent career discipline or suspension state exists yet**. Yellow cards are not accumulated across matches. Red cards do not trigger suspension in the career model. Players who should be suspended can still be selected in the starting XI.

V24D6D designs how red cards/suspensions and optional yellow accumulation persist across matches, so that:

1. A player sent off in match N is unavailable for match N+1.
2. Yellow card counts accumulate for future audit/threshold logic.
3. Career mutations correctly persist discipline outcomes.
4. Frontend can surface suspension status in squad/lineup views.

---

## 2. Current Model Audit

| Concept | Exists? | Location | Notes |
|---------|---------|----------|-------|
| `V24DisciplineModel` | YES | `v24/V24DisciplineModel.java` | Pure function foul/yellow/red probability. Used by `V24DetailedMatchEngine`. |
| `V24PlayerMatchState.yellowCards` | YES | `v24/V24PlayerMatchState.java:25` | Per-match int field, incremented by `addYellowCard()` |
| `V24PlayerMatchState.redCard` | YES | `v24/V24PlayerMatchState.java:26` | Per-match boolean, set by `giveRedCard()` |
| `V24MatchEventType.YELLOW_CARD` | YES | `v24/V24MatchEventType.java:16` | Defined in V24 event type enum |
| `V24MatchEventType.RED_CARD` | YES | `v24/V24MatchEventType.java:17` | Defined in V24 event type enum |
| V24 card event generation | YES | `V24DetailedMatchEngine.java:109-158` | Foul → `shouldCommitFoul()` → FOUL event → yellow check → YELLOW_CARD → second-yellow → RED_CARD |
| `V24PlayerRatingModel` card penalties | YES | `v24/V24PlayerRatingModel.java` | Yellow (-0.3), Red (-1.5) applied post-match |
| `V24PlayerMatchStatsModel` card counts | YES | `v24/V24PlayerMatchStatsModel.java` | Records card counts per match in stats DTO |
| `V24DetailedMatchData` card persistence | YES | Card stats embedded in match result DTO | Match result DTO carries card event data; not career-persistent |
| `SessionPlayer` persistent `yellowCards` | **NO** | `domain/model/entity/SessionPlayer.java` | No discipline fields exist |
| `SessionPlayer` persistent `redCards` | **NO** | `domain/model/entity/SessionPlayer.java` | No discipline fields exist |
| `SessionPlayer` persistent `suspended` | **NO** | `domain/model/entity/SessionPlayer.java` | No discipline fields exist |
| `SessionPlayer` `suspensionRemainingMatches` | **NO** | `domain/model/entity/SessionPlayer.java` | No discipline fields exist |
| `CareerSave` discipline registry | **NO** | `domain/model/entity/CareerSave.java` | No discipline map or state |
| DTO/API suspension exposure | **NO** | No current DTO surface | Backend persistence first; API exposure deferred to V24D6D7 |
| Frontend suspension indicators | **NO** | No current UI surface | Deferred to V24D6D7 DTO/UI audit |

**Key finding from audit:** All discipline machinery is match-local. The gap is persistence — `SessionPlayer` has no discipline fields, and no applier reads V24 card events to update career state.

---

## 3. Design Options

### Option A — Add Fields Directly to SessionPlayer (Recommended for MVP)

Add four new fields to `SessionPlayer`:

```java
private Integer yellowCards;           // default 0
private Integer redCards;            // default 0
private Boolean suspended;            // default false
private Integer suspensionRemainingMatches;  // default 0
```

With getters/setters following the existing pattern.

| Aspect | Detail |
|--------|--------|
| Schema impact | SessionPlayer widened by 4 fields. All existing callers unaffected. |
| Redis/Jackson compatibility | Primitives with safe defaults (0, false) — old JSON without these fields deserializes with defaults. No Redis key migration needed. |
| API impact | Fields added to SessionPlayer; if CareerPlayerDto or CareerSquadDto traverses SessionPlayer fields, API surface grows automatically. No explicit DTO change required to persist. |
| Complexity | Low — field addition + mutation applier write path only. |
| Testability | High — unit tests for serialization/deserialization, unit tests for mutation applier, integration tests for wiring. |
| Recommendation | **Option A for MVP** — mirrors existing `injured`, `energy`, `form` fields which use the same "accumulate and track remaining" pattern. Simplest serialization story. |

### Option B — New PlayerDisciplineState Object Nested in SessionPlayer

```java
private PlayerDisciplineState discipline = new PlayerDisciplineState();
```

Where `PlayerDisciplineState` encapsulates yellowCards, redCards, suspended, suspensionRemainingMatches.

| Aspect | Detail |
|--------|--------|
| Schema impact | New nested object; old JSON still works if Jackson handles null/nested defaults. |
| Redis/Jackson compatibility | Nested object requires null-initialization logic; more complex than Option A. |
| API impact | Slightly more ceremony to access (`player.getDiscipline().getYellowCards()` vs `player.getYellowCards()`). |
| Complexity | Medium — adds indirection without meaningful benefit at MVP scale. |
| Testability | Good — discipline state can be tested in isolation. |
| Recommendation | Consider for future if discipline rules grow complex (e.g., competition-specific bans, appeals). Overkill for MVP. |

### Option C — Career-Level Discipline Registry

New `Map<String, DisciplineState>` at `CareerSave` level keyed by `sessionPlayerId`. `CareerSave` does not change SessionPlayer.

| Aspect | Detail |
|--------|--------|
| Schema impact | New top-level map in CareerSave; SessionPlayer unchanged. |
| Redis/Jackson compatibility | CareerSave JSON structure changes; old saves lack the map but Jackson handles gracefully with empty default. |
| API impact | Discipline state not on SessionPlayer — requires separate lookup in services. |
| Complexity | High — discipline lookup requires map traversal on every mutation and lineup selection. |
| Testability | Medium — registry tested separately from player. |
| Recommendation | Useful if discipline data must survive player transfers/removals (archival view). Not needed for MVP where player and discipline are tightly coupled. |

### Option D — Read-Only Cards Only, No Persistence

Only surface per-match card stats in match results and ratings. No career-level accumulation.

| Aspect | Detail |
|--------|--------|
| Schema impact | None. |
| Redis/Jackson compatibility | N/A |
| API impact | Card stats visible per-match only. |
| Complexity | None. |
| Testability | N/A |
| Recommendation | Rejected. A player sent off must be unavailable in the next match. This requires persistent state. |

---

## 4. MVP Rules Proposal

**V24D6D MVP** implements the minimal discipline persistence rules needed for red-card suspension to work:

### Card Accumulation

- Each `YELLOW_CARD` event in the V24 match timeline increments `SessionPlayer.yellowCards` by 1.
- Each `RED_CARD` event increments `SessionPlayer.redCards` by 1.

### Red Card Suspension

- `RED_CARD` event sets `SessionPlayer.suspended = true`.
- `RED_CARD` event sets `SessionPlayer.suspensionRemainingMatches = 1`.

### Yellow Accumulation

- **No automatic suspension threshold in MVP.** Yellow accumulation is informational (audit/career record) and for future threshold logic.
- **No yellow reset rules in MVP.** Yellow counts grow for the career lifetime. A future phase can define end-of-season or N-match reset.
- **No yellow-to-red promotion in persistence.** The second-yellow → red transition is already handled inside `V24PlayerMatchState.addYellowCard()` during the match. The timeline `RED_CARD` event fires once. Persistence should rely on timeline events only — do not independently promote yellows to red in the applier.

### Bans and Appeals

- **No multi-match bans in MVP.** All red card suspensions are exactly 1 match.
- **No appeal/reduction logic in MVP.**
- **No competition-specific discipline rules.**

### Suspension Lifecycle — Critical Timing Rule

> **IMPORTANT:** Do NOT decrement `suspensionRemainingMatches` at "match start" in this design. A 1-match ban must not be accidentally cleared before the player actually misses the next match.

**Recommended suspension lifecycle for V24D6D MVP:**

1. Red card in match N → `suspensionRemainingMatches = 1`, `suspended = true`
2. Player is unavailable for next eligible match (N+1).
3. After match N+1 is processed (the match the player sat out), suspension is served.
4. **Suspension decrement** is implemented as a separate follow-up phase **V24D6D6**.
5. **Until V24D6D6 exists:** V24D6D persistence correctly sets suspension fields but does not claim to have completed the full lifecycle.

This separates the persistence of discipline events (what V24D6D does) from the lifecycle management of serving suspensions (what V24D6D6 adds).

---

## 5. Mutation Mechanics

### New Applier: V24DisciplineMutationApplier

Follows the same pattern as `V24InjuryMutationApplier` and `V24FatigueMutationApplier`:

```java
public class V24DisciplineMutationApplier {

    private final V24CareerMutationPolicy policy;

    public V24DisciplineMutationApplier(V24CareerMutationPolicy policy) {
        this.policy = policy;
    }

    public int applyDiscipline(CareerSave careerSave, V24DetailedMatchResult result,
                                V24CareerMutationPolicy policy) {
        // Gated by: isCareerMutationEnabled() && isDisciplinePersistenceEnabled()
        // Iterates result.timeline() for YELLOW_CARD / RED_CARD events
        // For each event: look up SessionPlayer, increment yellowCards or redCards, set suspension
        // Skips null/missing playerIds
        // On exception: logs, records failure, does NOT fail the round
        // Returns count of players/cards to which discipline was applied
    }
}
```

### Gate Logic

```
mutate-career-state = true  AND  persist-discipline = true  → discipline applier called
mutate-career-state = true  AND  persist-discipline = false → discipline applier skipped
mutate-career-state = false                              → no discipline mutation (regardless of persist-discipline)
```

### Best-Effort Partial Mutation

Same semantics as injury and fatigue mutation:

- If discipline applier throws, injury and fatigue mutations already applied **are not rolled back**.
- Discipline failures are added to `result.getFailures()`.
- `result.isPartialFailure()` set to true when `injuriesApplied > 0 || fatigueApplied > 0 || disciplineApplied > 0`.

### Null Handling

- Events with null `sessionPlayerId` are skipped (logged at debug level).
- SessionPlayers not found in `careerSave.getSessionPlayer(playerId)` are skipped.
- Null-safe defaults on SessionPlayer fields prevent NPE on old career saves.

---

## 6. V24CareerMutationPolicy Impact

`V24CareerMutationPolicy` already has `isDisciplinePersistenceEnabled()` (confirmed from V24D6D-0 audit):

```java
public boolean isDisciplinePersistenceEnabled() {
    return mutateCareerState && persistDiscipline;
}
```

**Existing behavior to preserve:**

- `isDisciplinePersistenceEnabled()` returns true only when **both** `mutateCareerState` AND `persistDiscipline` are true.
- `persistDiscipline` flag alone must not enable discipline mutation.
- Injury, fatigue, and discipline persistence flags are independent — enabling one does not enable others.
- `persist-detail` and `expose-detail-api` remain independent of discipline flag.

**If `isDisciplinePersistenceEnabled()` does not yet exist** in the current `V24CareerMutationPolicy` at `0dc184a`, it must be added as part of V24D6D implementation. The design assumes it exists or will be added.

---

## 7. V24CareerMutationResult Impact

`V24CareerMutationResult` already has a `disciplineApplied` field (confirmed from V24D6D-0 audit):

```java
private int disciplineApplied;
// and corresponding setter/getter
```

**Expected usage after V24D6D:**

- `disciplineApplied` is incremented for each player who receives a card event mutation (one count per player, not per card).
- `injuriesApplied`, `fatigueApplied`, `disciplineApplied` remain independent counters.
- `failures` list remains a defensive copy.
- `partialFailure` semantics unchanged.

---

## 8. API/DTO Impact

Adding discipline fields to `SessionPlayer` automatically exposes them through any DTO that traverses `SessionPlayer` fields. However:

- **Backend persistence can be done first.**
- **No new API endpoint is required for backend persistence.** However, adding fields to `SessionPlayer` does not guarantee that suspension/card fields are exposed through existing DTOs. API contract exposure must be audited explicitly in V24D6D7 before frontend work.
- Frontend/API exposure for suspended players is a **later explicit phase (V24D6D7)**.
- **Do not assume UI can show suspended status until DTO/frontend audit confirms fields traverse the API boundary.**

**Future UI indicators needed** (V24D6D7 scope):
- Squad view: suspended badge on player card
- Lineup selection: suspended players unavailable/grayed out
- Dashboard: suspensions upcoming in next match
- Match detail: red/suspended player annotation

---

## 9. Backward Compatibility

**Option A (SessionPlayer fields)** provides the best backward compatibility story:

- Adding primitive fields (`int yellowCards = 0`, `boolean suspended = false`) to `SessionPlayer` does not break existing Redis JSON.
- Old career saves without discipline fields deserialize with Java defaults (0, false, 0) — safe values.
- No Redis key migration needed.
- No database schema migration needed.
- CareerSave JSON structure is unchanged.

**Serialization/deserialization tests required** when fields are added (V24D6D2 scope):
- New SessionPlayer instance has correct defaults.
- JSON serialized without discipline fields deserializes with defaults.
- JSON serialized with discipline fields round-trips correctly.

---

## 10. Testing Plan

### V24D6D2 — SessionPlayer Discipline Fields + Serialization/Unit Tests

- `newSessionPlayer_defaultsDisciplineFields()`
- `oldJsonWithoutDisciplineFields_deserializesWithDefaults()` — if practical with existing JSON round-trip tests
- `gettersSetters_disciplineFields_work()` — yellowCards, redCards, suspended, suspensionRemainingMatches
- `sessionPlayerWithDiscipline_serializesAndDeserializesCorrectly()`

### V24D6D3 — V24DisciplineMutationApplier Tests

- `disabledPolicy_noMutation()`
- `yellowCard_incrementsYellowCards()`
- `redCard_setsSuspendedAndOneMatchSuspension()`
- `yellowAndRed_samePlayer_countsCorrectly()`
- `multiplePlayers_cardsAppliedIndependently()`
- `unknownPlayer_skipped()`
- `nullPlayerId_skipped()`
- `duplicateRedCard_sameMatch_countsOnceOrDocumentedBehavior()` — RED_CARD fires once per match even on second-yellow; verify no double-count
- `alreadySuspended_redCard_preservesOrExtendsSuspension()` — document whether a second red card resets or extends suspension; MVP rule: redCards++, suspensionRemainingMatches=1 (reset)

### V24D6D4 — V24CareerMutationService Discipline Orchestration

- `persistDisciplineEnabled_callsDisciplineApplier()`
- `persistDisciplineDisabled_doesNotCallDisciplineApplier()`
- `injuryFatigueDiscipline_independentCounts()`
- `disciplineFailure_doesNotEraseInjuryFatigueSuccess()`
- `disciplineFailure_preservesFailureMessage()`

### V24D6D5 — LeagueSimulator Wiring

- `V24_path_mutateCareerState_persistDiscipline_appliesDiscipline()`
- `V24_disabled_persistDisciplineTrue_noMutation()`
- `V23_defaultPath_noMutation()`
- `masterFalse_persistDisciplineTrue_noMutation()`
- `disciplineMutationFailure_roundCompletes()` — only if `v24MutationService` is injectable; if not injectable, document as deferred limitation

### V24D6D6 — Suspension Lifecycle/Decrement

- `redCardSuspension_notClearedBeforeNextMatch()` — verify suspension is set but not prematurely decremented
- `suspensionDecrementsAfterServedMatch()` — only after V24D6D6 decrement logic is implemented
- `clearsSuspendedWhenRemainingReachesZero()`
- `doesNotDecrementPlayerWhoDidNotServeSuspension()` if relevant to implementation

### V24D6D7 — DTO/API/Frontend Audit for Suspension Visibility

- Audit: `CareerPlayerDto` / `CareerSquadDto` traverses `SessionPlayer.suspended` and `SessionPlayer.suspensionRemainingMatches`
- Audit: Lineup `assignPlayerToTeam` or `getAvailablePlayers` checks `suspended` flag
- Audit: Frontend `PlayerCardComponent` or equivalent renders suspended badge
- No new tests until DTO/frontend scope is confirmed

---

## 11. Risks and Mitigations

| Risk | Severity | Likelihood | Mitigation |
|------|----------|------------|------------|
| **Schema widening** — adding 4 fields to SessionPlayer increases its surface area permanently | Low | High (by design) | Option A chosen because it mirrors existing fields; fields are low-cost primitives |
| **UI confusion** — suspension persists in backend but frontend cannot display it | Medium | Medium | V24D6D explicitly defers API/UI exposure to V24D6D7; discipline persistence can proceed without UI |
| **Lineup selection** — suspended players can still be picked in starting XI | Medium | Medium (if not guarded) | Lineup selection service must check `player.suspended` before confirming XI; guard in V24D6D5 or V24D6D7 |
| **Suspension decrement timing** — `suspensionRemainingMatches` cleared before player actually misses a match | Medium | Medium | **Design decision:** decrement logic deferred to V24D6D6; V24D6D MVP only sets suspension, does not claim to manage lifecycle |
| **Yellow accumulation unbounded** — yellowCards grows forever over long careers | Low | Low | Future phase can define reset threshold; MVP is informational only |
| **Best-effort partial mutation** — discipline mutation fails but injury/fatigue already applied and persist | Low | Low | This is existing semantics confirmed by V24D6F tests; not a regression |
| **Rollout with default-false** — `persist-discipline` defaults to false; enabling it activates discipline mutation silently | Medium | Low | Both master gate AND discipline-specific flag must be true; tests cover both flag combinations |
| **Duplicate RED_CARD counting** — second-yellow already fires RED_CARD in match, but RED_CARD event also fires from the direct red flow | Medium | Low | Test `duplicateRedCard_sameMatch_countsOnceOrDocumentedBehavior()` verifies single count per player per match |

---

## 12. Non-Goals

V24D6D1 does **NOT**:

- Implement discipline fields on SessionPlayer
- Implement V24DisciplineMutationApplier
- Wire discipline applier into V24CareerMutationService
- Change LeagueSimulator construction or wiring
- Change API endpoints or DTOs
- Change frontend components
- Implement suspension decrement lifecycle (V24D6D6)
- Implement yellow accumulation threshold (e.g., 5 yellows → 1-match suspension)
- Implement form, morale, or reputation effects
- Implement lineup blocking for suspended players
- Implement competition-specific discipline rules
- Implement appeal or ban reduction logic

---

## 13. Recommended Implementation Order

1. **V24D6D1** — Design doc (this document)
2. **V24D6D2** — SessionPlayer discipline fields + serialization/unit tests
3. **V24D6D3** — V24DisciplineMutationApplier tests + applier implementation
4. **V24D6D4** — V24CareerMutationService discipline orchestration tests + wiring
5. **V24D6D5** — LeagueSimulator wiring tests + production wiring if injectable
6. **V24D6D6** — Suspension lifecycle/decrement phase (separate from initial persistence)
7. **V24D6D7** — DTO/API/frontend audit for suspension visibility

Each phase is independent enough to be reviewed and committed separately.

---

## 14. Completion Criteria

- [x] Design document created
- [x] Current state audited (all 17 concepts in audit table)
- [x] Option A selected for SessionPlayer field extension
- [x] MVP rules defined with explicit conservative scope
- [x] Suspension lifecycle timing rule documented — decrement deferred to V24D6D6
- [x] Testing plan covers all phases (D2 through D7)
- [x] Risks documented with mitigations
- [x] Non-goals explicitly listed
- [x] No src/main code changes
- [x] No src/test code changes
- [x] No target/ staging

---

## Validation Commands

```bash
git status --short
git diff --stat
```

**Expected after V24D6D1 design only:**
- Only `V24D6D_DISCIPLINE_PERSISTENCE_DESIGN.md` in output of `git status --short`
- `git diff --stat` shows only this new file
- No changes to src/main or src/test

---

*This document is the authoritative V24D6D design specification. All implementation must conform to this document. Update this document before making any implementation changes.*