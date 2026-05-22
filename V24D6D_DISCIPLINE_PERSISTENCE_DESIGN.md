# V24D6D — Discipline/Cards Persistence Design Document

**Status:** V24D6D1–D7+V24D6H IMPLEMENTATION COMPLETE — V24D6D6 suspension lifecycle wired in LeagueSimulator; V24D6D7A DTO/API suspension exposure and lineup blocking complete; V24D6D7B1/B2 frontend suspension warnings/badges complete; V24D6H yellow-card threshold (5 → 1-match suspension) implemented via V24DisciplineMutationApplier; threshold-suspended players tracked via LeagueSimulator snapshot comparison; V24DetailedMatchEngineProvider interface enables deterministic test injection; V24D6G3/G4A/G4B/G5A/G6A and V24D6G7 (audit — no code changes) in separate frontend repo
**Branch:** `mvp-1-performance-cleanup`
**Latest backend implementation commits:** `0f4ab39` (V24D6D5 discipline wiring), `219628d` (V24D6D6A suspension lifecycle applier), `b4291d9` (V24D6D6B suspension lifecycle wiring), `6aadcd5` (V24D6D7A DTO/API suspension exposure), `8b747bd` (V24D6H1 design), `6a07173` (V24D6H2 applier), `ab1f7b5` (V24D6H3 service tests), `980be03` (V24D6H4 lifecycle integration)
**Latest docs commit:** this file (V24D6D7C+V24D6H docs update)
**Tests:** 623, 0 failures (602 baseline + 21 V24D6H); mutation/lifecycle focused gate 206, 0 failures
**Remaining deferred (post-V24D6I):** Advanced/competition-specific discipline rules, optional frontend yellow counter display. V24D6I injury recovery lifecycle is now complete. Form/morale mutation (V24D6E) is complete.
**No production code changes in V24D6D1 (design only); V24D6D2/D3/D4/D5/D6A/D6B/D7A production code committed separately; V24D6H production code committed with respective H1-H4 commits**

---

## 1. Executive Summary

V24 already generates match-local card events. During a match:

- `V24DisciplineModel` evaluates foul probability and decides when a player receives a yellow or red card.
- `V24PlayerMatchState` tracks `yellowCards` and `redCard` per player for the duration of that match.
- `V24DetailedMatchEngine` wires foul → yellow card → second-yellow → red card event generation.
- `V24PlayerRatingModel` applies performance penalties for yellow (-0.3) and red (-1.5) cards post-match.
- `V24PlayerMatchStatsModel` records card counts per match.

V24 detailed match data and player ratings can therefore display per-match card statistics. **V24D6D2-D7 now implement persistent career discipline/suspension state.** Yellow cards accumulate on `SessionPlayer.yellowCards`, red cards accumulate on `SessionPlayer.redCards`, and red cards set `SessionPlayer.suspended=true` with `suspensionRemainingMatches=1`. V24D6D6A/B implements suspension lifecycle/decrement (commits `219628d`/`b4291d9`). V24D6D7A implements DTO/API suspension exposure and lineup blocking (backend commit `6aadcd5`). V24D6D7B1/B2 implements frontend suspension warnings and badges (frontend commits `8097ca9`+`69bf879`). V24D6H implements yellow-card suspension threshold (5 → 1-match suspension) via V24DisciplineMutationApplier with LeagueSimulator snapshot tracking (commits `8b747bd`/`6a07173`/`ab1f7b5`/`980be03`). DTO/API/frontend suspension visibility, backend lineup blocking, and yellow threshold are complete. Remaining deferred (post-V24D6I): advanced/competition-specific discipline rules, optional frontend yellow counter display. V24D6E form persistence and V24D6I injury recovery lifecycle are complete.

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
| `SessionPlayer` persistent `yellowCards` | **YES** — implemented V24D6D2 | `domain/model/entity/SessionPlayer.java:47-50` | Integer, null-safe getter `(yellowCards != null ? yellowCards : 0)` |
| `SessionPlayer` persistent `redCards` | **YES** — implemented V24D6D2 | `domain/model/entity/SessionPlayer.java:47-50` | Integer, null-safe getter `(redCards != null ? redCards : 0)` |
| `SessionPlayer` persistent `suspended` | **YES** — implemented V24D6D2 | `domain/model/entity/SessionPlayer.java:47-50` | Boolean, null-safe getter `(suspended != null ? suspended : false)` |
| `SessionPlayer` `suspensionRemainingMatches` | **YES** — implemented V24D6D2 | `domain/model/entity/SessionPlayer.java:47-50` | Integer, null-safe getter `(suspensionRemainingMatches != null ? suspensionRemainingMatches : 0)` |
| `CareerSave` discipline registry | **NO** | `domain/model/entity/CareerSave.java` | No discipline map or state |
| DTO/API suspension exposure | **YES** | `CareerPlayerDto`, `CareerSquadDto`, `PlayerLineupDTO` | Implemented V24D6D7 (backend `6aadcd5`) |
| Frontend suspension indicators | **YES** | `PlayerCardComponent`, `LineupPlayerCardComponent` | Implemented V24D6D7 (frontend `8097ca9`+`69bf879`) |
| Lineup blocking for suspended players | **YES** | `LineupHelper.validatePlayerFitness()`, `LineupCommandUseCaseImpl.performAutoSelect()` | Implemented V24D6D7A (backend `6aadcd5`) |

**Key finding from audit:** All discipline machinery is in place. Persistence gap is now closed — `SessionPlayer` has discipline fields (V24D6D2), `V24DisciplineMutationApplier` reads V24 card events and updates career state (V24D6D3), and `V24CareerMutationService` orchestrates mutation behind flags (V24D6D4).

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
| API impact | **Historical design-time note:** field persistence did not guarantee API exposure. V24D6D7 later audited and completed DTO/API/frontend traversal: backend DTO exposure and lineup blocking in `6aadcd5`, frontend suspension warnings in `8097ca9`, and suspended card badges in `69bf879`. No explicit DTO change required to persist. |
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

    public int applyDiscipline(
            CareerSave career,
            V24DetailedMatchResult result,
            V24CareerMutationPolicy policy) {
        // Gated by policy.isDisciplinePersistenceEnabled()
        // Iterates result.timeline() for YELLOW_CARD / RED_CARD events
        // Returns applied discipline mutation count
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

- `disciplineApplied` is incremented for each applied discipline mutation event. YELLOW_CARD events count individually; RED_CARD events count once per player per match when duplicate RED_CARD events appear for the same player.
- `injuriesApplied`, `fatigueApplied`, `disciplineApplied` remain independent counters.
- `failures` list remains a defensive copy.
- `partialFailure` semantics unchanged.

---

## 8. API/DTO Impact

Adding discipline fields to `SessionPlayer` automatically exposes them through any DTO that traverses `SessionPlayer` fields. However:

- **Backend persistence can be done first.**
- **Historical design note:** adding fields to `SessionPlayer` did not guarantee API exposure. V24D6D7 later audited and completed DTO/API/frontend traversal: backend DTO exposure and lineup blocking in `6aadcd5`, frontend suspension warnings in `8097ca9`, and suspended card badges in `69bf879`.
- V24D6D7 DTO/API/frontend suspension visibility is now complete.
- **UI can now show suspended status** — fields traverse the API boundary confirmed through V24D6D7.

**V24D6D7 completion:**
- Backend DTO/API suspension exposure and lineup blocking are complete in `6aadcd5`.
- Frontend dashboard/squad warnings are complete in `8097ca9`.
- Frontend PlayerCard and LineupPlayerCard suspended badges are complete in `69bf879`.
- V24D6D7B3 polish/accessibility audit found no additional code changes required.
- Match detail remains event-based and does not display career suspension state unless a future UX phase explicitly requires it.

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

### V24D6D7 — DTO/API/Frontend Visibility + Lineup Blocking

- `SessionPlayerDTODisciplineFieldsTest` — 6 tests: DTO exposes yellowCards, redCards, suspended, suspensionRemainingMatches and mapper uses null-safe getters.
- `LineupBlockingTest` — 6 tests: `LineupHelper.validatePlayerFitness()` blocks suspended players and positive `suspensionRemainingMatches`.
- `LineupCommandUseCaseImplAutoSelectTest` — 2 tests: auto-select excludes suspended players and players with positive `suspensionRemainingMatches`.
- Frontend V24D6D7B1/B2 validated with `npx tsc --noEmit` and `npx ng build --configuration production`.
- V24D6D7B3 accessibility/polish audit passed with no code changes required.

---

## 11. Risks and Mitigations

| Risk | Severity | Likelihood | Mitigation |
|------|----------|------------|------------|
| **Schema widening** — adding 4 fields to SessionPlayer increases its surface area permanently | Low | High (by design) | Option A chosen because it mirrors existing fields; fields are low-cost primitives |
| **UI confusion** — suspension persists in backend but frontend cannot display it | Medium | Medium | **Closed by V24D6D7A/B:** backend DTO/API exposure and lineup blocking are complete in `6aadcd5`; frontend warnings and badges are complete in `8097ca9` and `69bf879` |
| **Lineup selection** — suspended players can still be picked in starting XI | Medium | Medium (if not guarded) | **Closed by V24D6D7A:** `LineupHelper.validatePlayerFitness()` and `LineupCommandUseCaseImpl.performAutoSelect()` now check and reject suspended players (backend `6aadcd5`) |
| **Suspension decrement timing** — `suspensionRemainingMatches` cleared before player actually misses a match | Medium | Medium | **Implemented:** V24D6D6A/B suspension lifecycle now wired in LeagueSimulator with participation verification; lifecycle no longer deferred |
| **Yellow accumulation unbounded** — yellowCards grows forever over long careers | Low | Low | Future phase can define reset threshold; MVP is informational only |
| **Best-effort partial mutation** — discipline mutation fails but injury/fatigue already applied and persist | Low | Low | This is existing semantics confirmed by V24D6F tests; not a regression |
| **Rollout with default-false** — `persist-discipline` defaults to false; enabling it activates discipline mutation silently | Medium | Low | Both master gate AND discipline-specific flag must be true; tests cover both flag combinations |
| **Duplicate RED_CARD counting** — second-yellow already fires RED_CARD in match, but RED_CARD event also fires from the direct red flow | Medium | Low | Test `duplicateRedCard_sameMatch_countsOnceOrDocumentedBehavior()` verifies single count per player per match |

---

## 12. Non-Goals

V24D6D1 was design-only. V24D6D2/D3/D4/D5 implemented discipline fields, applier, service orchestration, and LeagueSimulator wiring. V24D6D6 implemented suspension lifecycle/decrement. V24D6D7 implemented backend DTO/API exposure, lineup blocking, and frontend suspension indicators. The following items were originally non-goals or deferred after V24D6D5; items now completed by V24D6D6/D7 are struck through, while remaining deferred work is listed without strikethrough:

- ~~Implement discipline fields on SessionPlayer~~ ✓ V24D6D2 done
- ~~Implement V24DisciplineMutationApplier~~ ✓ V24D6D3 done
- ~~Wire discipline applier into V24CareerMutationService~~ ✓ V24D6D4 done
- ~~Change LeagueSimulator construction or wiring~~ ✓ V24D6D5 done
- ~~Implement API endpoints or DTOs for suspension visibility~~ ✓ V24D6D7 done (backend `6aadcd5`)
- ~~Change frontend components for suspension indicators~~ ✓ V24D6D7 done (frontend `8097ca9`+`69bf879`)
- ~~Implement suspension decrement lifecycle~~ ✓ V24D6D6A/B done
- Implement yellow accumulation threshold (e.g., 5 yellows → 1-match suspension)
- Implement form, morale, or reputation effects
- ~~Implement lineup blocking for suspended players~~ ✓ V24D6D7 done (backend `6aadcd5`)
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

- [x] Design document created (V24D6D1)
- [x] Current state audited (all 17 concepts in audit table)
- [x] Option A selected for SessionPlayer field extension
- [x] MVP rules defined with explicit conservative scope
- [x] Suspension lifecycle timing rule documented — implemented through V24D6D6A/B
- [x] Testing plan covers all phases (D2 through D7)
- [x] Risks documented with mitigations
- [x] Non-goals explicitly listed
- [x] V24D6D1: no src/main or src/test changes (design-only)
- [x] V24D6D2: SessionPlayer fields implemented, tests committed
- [x] V24D6D3: DisciplineMutationApplier implemented, tests committed
- [x] V24D6D4: Service orchestration wired, tests committed
- [x] V24D6D5: LeagueSimulator wiring complete, tests committed
- [x] V24D6D6: Suspension lifecycle implemented and wired (commits `219628d`/`b4291d9`)
- [x] V24D6D7A: Backend DTO/API suspension exposure and lineup blocking implemented (commit `6aadcd5`)
- [x] V24D6D7B1/B2: Frontend suspension warnings and badges implemented (commits `8097ca9`/`69bf879`)
- [x] Backend regression gate: 623 tests, 0 failures
- [x] Frontend V24D6D7B1/B2: `npx tsc --noEmit` + `npx ng build --configuration production` passed
- [x] V24D6D7B3: accessibility/polish audit found no additional code changes required
- [x] V24D6D1-D6 had no API/frontend changes; V24D6D7 intentionally added backend DTO/API exposure, lineup blocking, and frontend suspension indicators
- [x] No target/ staging

---

## 15. Implementation Summary (V24D6D2–D7)

| Phase | Commit | Files | Tests added |
|-------|--------|-------|-------------|
| V24D6D2 | `ecab588` | `SessionPlayer.java` | +8 |
| V24D6D3 | `7bf350a` | `V24DisciplineMutationApplier.java` | +16 |
| V24D6D4 | `f1ef4df` | `V24CareerMutationService.java`, `V24CareerMutationResult.java` | +7 |
| V24D6D5 | `0f4ab39` | `LeagueSimulator.java` | +6 |
| V24D6D6A | `219628d` | `V24SuspensionLifecycleApplier.java` | +19 |
| V24D6D6B | `b4291d9` | `LeagueSimulator.java`, `V24DetailedMatchEngineProvider.java`, `V24CareerMutationIntegrationTest.java` | +8 |
| V24D6D7A | `6aadcd5` | `SessionPlayerDTO.java`, `PlayerLineupDTO.java`, `SessionEntityMapper.java`, `LineupHelper.java`, `LineupCommandUseCaseImpl.java`, `LineupQueryUseCaseImpl.java` | +14 |

Total new tests: **80** | Regression gate: **602** | Mutation + lifecycle focused gate: **171**

**Frontend validation:**
- V24D6D7B1 (`8097ca9`): dashboard/squad warnings — `npx tsc --noEmit` passed, `npx ng build --configuration production` passed
- V24D6D7B2 (`69bf879`): PlayerCard/LineupPlayerCard suspended badges — `npx tsc --noEmit` passed, `npx ng build --configuration production` passed
- V24D6D7B3: accessibility/polish audit — no code changes required

**Deferred to future phases:**
- V24D6E: Form/morale effects
- Yellow accumulation threshold (e.g., 5 yellows → 1-match suspension)
- Injury recovery lifecycle
- Advanced/competition-specific discipline rules

---

## Validation Commands

```bash
git status --short
git diff --stat
```

**Historical D1 validation expectation:**
- Only `V24D6D_DISCIPLINE_PERSISTENCE_DESIGN.md` in output of `git status --short`
- `git diff --stat` shows only this new file
- No changes to src/main or src/test

**Current V24D6D7C documentation update expectation:**
- Docs only
- No src/main or src/test changes
- No frontend source changes
- No target/dist artifacts staged

---

*This document is the authoritative V24D6D design specification. All implementation must conform to this document. Update this document before making any implementation changes.*