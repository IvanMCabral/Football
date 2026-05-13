# V24D6 — Career State Mutation Design

**Status:** V24D6A DESIGN COMPLETE; V24D6B1/B2/B3 IMPLEMENTATION COMPLETE; V24D6C1/C2/C3 FATIGUE MUTATION COMPLETE; cards/form deferred
**Branch:** `mvp-1-performance-cleanup`
**Created:** 2026-05-12
**Latest implementation commit:** `0dc184a` (feat: add fatigue mutation applier, service orchestration, and LeagueSimulator wiring)
**Tests:** 506 regression gate (459 baseline + 27 V24D6C1 + 14 V24D6C2 + 6 V24D6C3), 0 failures

---

## 1. Executive Summary

V24D5E completed the read-only match detail flow — from V24 simulation through Redis persistence, API endpoint, and frontend display of timeline, player ratings, and shot map. V24 is now a functioning, visible simulation layer.

The next logical step is to move from **visualization to consequence**: V24 match outcomes should affect persistent career state so that simulation results matter beyond the match itself.

**Goal:** Design how V24 match outcomes (injuries, fatigue, cards, form) can safely mutate `CareerSave` persistent state after a simulated round — without breaking existing careers, without forcing adoption, and without compromising the V23/stable path.

V24D6A began as design-only and is now complete. V24D6B1/B2/B3 and V24D6C1/C2/C3 are also complete: injury mutation applier, mutation service orchestration, and LeagueSimulator wiring behind default-false flags, PLUS fatigue mutation applier, fatigue service orchestration, and fatigue LeagueSimulator wiring behind default-false flags. Injury and fatigue are complete through V24D6C3; cards/form remain deferred.

---

## 2. Current State

### V24 Simulation Path

| Component | Status | Location |
|-----------|--------|----------|
| V24DetailedMatchEngine | Complete | `application/service/simulation/v24/` |
| V24MatchContextFactory | Complete | `application/service/simulation/v24/` |
| V24 path in LeagueSimulator | Complete, behind `use-v24-detailed-engine=false` | `LeagueSimulator` |
| V24 detail persistence | Complete, behind `persist-detail=false` | `V24DetailedMatchRedisAdapter` |
| V24 detail query endpoint | Complete, behind `expose-detail-api=false` | `V24DetailedMatchQueryService` |
| Player ratings persistence | Complete (V24D5F) | `V24DetailedMatchData.playerRatings` |
| Shot coordinate attachment | Complete (V24D3C) | `V24MatchEvent.shotCoordinate` |
| Frontend detail page | Complete (V24D5E6, commit `12d203d`) | `front-ciber/project` / `mvp-1` |
| Frontend shot map UI | Complete (V24D5E5, commit `9b88739`) | `front-ciber/project` / `mvp-1` |
| Frontend player ratings UI | Complete (V24D5E4, commit `958af1e`) | `front-ciber/project` / `mvp-1` |

### Mutation State

| Aspect | Current Behavior |
|--------|-----------------|
| V24PlayerMatchState | Match-local only, reset each match |
| SessionPlayer | Mutated only when V24 path succeeds and `mutate-career-state=true` + `persist-injuries=true` (injury) or `persist-fatigue=true` (fatigue); default false, so normal behavior remains unchanged |
| SessionTeam | **Not mutated** by V24 |
| CareerSave schema | Unchanged |
| MatchFixture.MatchResultData | Unchanged (6 aggregate fields) |
| V23/default path | Unaffected by V24 |
| Backend tests | 506, 0 failures |

**Key observation:** V24 produces rich match-local state (injuries, stamina drain, cards, ratings). As of V24D6C3, there is now a persistent career-state path for injuries AND fatigue: INJURY events can update existing SessionPlayer injury fields, and energy drain can update SessionPlayer.energy, when the V24 path succeeds and both `mutate-career-state=true` and the respective effect flag is true. Cards/suspensions and form/morale remain match-local/deferred.

---

## 3. Design Principles

These principles govern all V24 career mutation work:

1. **Additive and reversible** — mutation is a new optional path; existing career data is not migrated or altered. Flags off = no mutation = same behavior as today.

2. **Feature-flagged at every level** — master flag + per-effect flags; all default false; no silent mutations.

3. **No mutation unless explicitly enabled** — even when flags are on, mutation only occurs if the specific effect flag is on.

4. **Never block round completion because of mutation failure** — if mutation fails, the round result is still returned; CareerSave is still persisted with its current data; an error is logged.

   **Mutation failure policy has two levels:**
   - **Service-level/fatal failure** (e.g., null argument, unexpected exception): no mutation is applied; round still completes; CareerSave is persisted without mutation changes.
   - **Per-player/per-effect failure** (e.g., player not found, field not writable): that player or effect is skipped, other mutation appliers continue; round still completes; failures are recorded in `V24CareerMutationResult.failures`.

5. **V23/default path completely unaffected** — career mutation only applies to V24 simulation path; V23 and default simulators never trigger mutation.

6. **Old saves remain compatible** — new mutation data (injury flags, energy drain) is applied to existing CareerSave without schema migration; if the new fields are absent, assume defaults.

7. **Deterministic where possible** — mutation outcomes should be deterministic for a given match result and seed, enabling reliable regression testing.

8. **Mutation effects must be explainable** — users must be able to understand why a player is injured/tired/suspended; frontend must surface this information clearly, or mutation behavior must be documented.

---

## 4. Candidate Career Effects

### 4.1 Injuries

**Source:** `V24MatchEvent` with `type=INJURY`, or `V24PlayerMatchState.injured` flag set during simulation.

**Target fields on `SessionPlayer`:**
```java
private boolean injured;
private String injuryType;       // e.g., "MUSCLE", "JOINT", "ILLNESS"
private int injuryRemainingMatches;  // decremented each round
```

**Trigger logic:** When V24 emits an INJURY event for player X, and `persist-injuries=true` and `mutate-career-state=true`, set `injured=true`, record type and remaining matches.

**Risk:** Medium/High. Injuries affect player availability in lineup selection. If many players accumulate injuries, lineup construction becomes difficult. Mitigation: cap maximum injured in squad, require substitution.

**Recommendation:** V24D6B — Injury persistence only. Use existing `SessionPlayer.injured` / `injuryType` / `injuryRemainingMatches` fields. No new schema needed.

---

### 4.2 Fatigue / Energy

**Source:** `V24PlayerMatchState.minutesPlayed`, `V24FatigueModel` stamina drain, substitution timing.

**Target field on `SessionPlayer`:**
```java
private int energy;  // 0-100, default 100
```

**Trigger logic:** After match, compute energy drain based on minutes played and fatigue factor. Apply drain when `persist-fatigue=true` and `mutate-career-state=true`.

**Risk:** Medium. If energy drains too fast, users cannot field competitive teams. If drain is too slow, fatigue has no gameplay effect. Requires careful calibration.

**Recommendation:** V24D6C — Fatigue persistence (after V24D6B injury pipeline is proven). Use existing `SessionPlayer.energy` field. No new schema needed for V24D6B/V24D6C.

---

### 4.3 Cards / Suspensions

**Source:** `V24MatchEvent` with `type=YELLOW_CARD` or `RED_CARD`.

**Target:** No existing suspension model in `SessionPlayer`. New discipline/suspension state would be needed.

**Decision required:** How many yellow cards trigger a suspension? Are red cards a 1-match ban? Is there a competition-level disciplinary table?

**Risk:** High. Without an existing suspension model in `SessionTeam` or `SessionPlayer`, implementing discipline requires a new data model. This affects lineup selection, career progression, and user experience significantly.

**Recommendation:** V24D6D — Design cards/suspensions separately before implementation. Do not mix with V24D6B injury/fatigue baseline. Requires UI indicator for suspended players (V24D6G).

---

### 4.4 Form / Morale

**Source:** `V24PlayerRatingModel` per-match rating, match result (win/draw/loss), goals/assists.

**Target:** `SessionPlayer.form` (if it exists) or new morale/confidence field.

**Current state:** `SessionPlayer.form` may exist as a field. If not, a new field would be needed.

**Risk:** Medium/High. Form affects player performance in subsequent matches. If not carefully balanced, high-OVR teams get even higher form, low-OVR teams spiral downward. Dynamic range must be limited.

**Recommendation:** Defer V24D6E until injury/fatigue/cards pipeline is stable. Form/morale is the most game-sensitive effect and should not be the first mutation baseline.

---

### 4.5 Player Stats / Season Stats

**Source:** Goals, assists, shots, cards, injury events in `V24MatchEvent` timeline.

**Target:** Possible future `PlayerSeasonStats` model (not yet designed).

**Risk:** Medium. Affects career record-keeping. Mostly cosmetic if the data is not displayed to the user.

**Recommendation:** Separate phase (V24D6X). Do not mix season stats with mutation baseline. V24D6B/C/D should focus on gameplay-affecting mutations (injury, fatigue, cards) before stat tracking.

---

## 5. Recommended Phase Breakdown

| Phase | Description | Priority |
|-------|-------------|----------|
| **V24D6A** | Design — this document | DONE |
| **V24D6B** | Injury persistence — B1 applier, B2 service orchestration, B3 LeagueSimulator wiring behind flags | DONE |
| **V24D6C** | Fatigue/energy persistence — C1 applier, C2 service orchestration, C3 LeagueSimulator wiring | DONE |
| **V24D6D** | Cards/suspensions design + persistence — new discipline model if approved | MEDIUM |
| **V24D6E** | Form/morale updates — SessionPlayer.form or new field | LOW (defer) |
| **V24D6F** | Career mutation integration tests + rollback tests | HIGH |
| **V24D6G** | UI indicators — show unavailable/tired/suspended players in lineup | MEDIUM |

**Rationale:** Injury and fatigue are the least reversible effects (a player cannot play if injured or exhausted). Starting with these creates the most immediate gameplay consequence. Cards/suspensions require a new model. Form is the most sensitive to balance errors.

---

## 6. Proposed Feature Flags

### Proposed Flag Hierarchy

```yaml
# Master gate for all V24 career mutation
app.simulation.v24.mutate-career-state=false

# Per-effect gates (only meaningful when mutate-career-state=true)
app.simulation.v24.persist-injuries=false
app.simulation.v24.persist-fatigue=false
app.simulation.v24.persist-discipline=false
app.simulation.v24.persist-form=false
```

### Flag Independence

| Flag | Requires |
|------|----------|
| `mutate-career-state=false` | All mutation disabled regardless of other flags |
| `persist-injuries=true` | `mutate-career-state=true` |
| `persist-fatigue=true` | `mutate-career-state=true` |
| `persist-discipline=true` | `mutate-career-state=true` |
| `persist-form=true` | `mutate-career-state=true` |

### Relationship to Existing Flags

| Existing Flag | Relationship to mutation | Notes |
|---------------|--------------------------|-------|
| `use-v24-detailed-engine=false` | Required precondition | V24 path disabled → no mutation possible because no V24 result exists |
| `persist-detail=false` | Independent | Detail persistence does NOT imply mutation; mutation may be enabled or disabled separately |
| `expose-detail-api=false` | Independent | API exposure does NOT imply mutation; mutation may be enabled or disabled separately |

**Key insight:** `persist-detail` and `expose-detail-api` are additive read-only features. `mutate-career-state` is a write operation that requires explicit enablement.

---

## 7. Proposed Architecture

### New Components

```
application/service/simulation/v24/
├── V24CareerMutationService.java       # orchestrates mutation
├── V24CareerMutationPolicy.java         # evaluates flags + decides what to mutate
├── V24CareerMutationResult.java          # outcome record: what was mutated, what failed
├── V24InjuryMutationApplier.java        # applies injury events to SessionPlayer
├── V24FatigueMutationApplier.java       # applies energy drain to SessionPlayer
├── V24DisciplineMutationApplier.java    # applies card/suspension logic
└── V24FormMutationApplier.java          # applies form/morale updates
```

### Where Mutation Plugs In

```
LeagueSimulator.simulateRound()
├── build V24MatchContext
├── simulate V24DetailedMatchEngine
├── map V24DetailedMatchResult → MatchResultData     ← existing
├── persist V24Detail (if persist-detail=true)        ← existing
├── applyV24CareerMutation(v24Result, careerSave)    ← NEW
└── persist CareerSave                               ← existing
```

`V24CareerMutationService`, `V24CareerMutationPolicy`, `V24CareerMutationResult`, `V24InjuryMutationApplier` are implemented through V24D6B1/B2/B3. `V24FatigueMutationApplier` is implemented in V24D6C1, with service orchestration in V24D6C2 and runtime wiring in V24D6C3. `V24DisciplineMutationApplier` and `V24FormMutationApplier` remain future work.

### V24CareerMutationService — Implemented in V24D6B2

```java
public interface V24CareerMutationService {
    /**
     * Apply career mutations from V24 match result to CareerSave.
     * Best-effort: failures are logged but do not block round completion.
     * @param v24Result the completed V24 match result
     * @param careerSave the career save to mutate
     * @param policy the mutation policy evaluated from current flags
     * @return mutation result describing what was applied and what failed
     */
    V24CareerMutationResult apply(V24DetailedMatchResult v24Result,
                                   CareerSave careerSave,
                                   V24CareerMutationPolicy policy);
}
```

### V24CareerMutationResult — Implemented in V24D6B2

```java
public record V24CareerMutationResult(
    List<PlayerMutation> injuriesApplied,
    List<PlayerMutation> fatigueApplied,
    List<PlayerMutation> disciplineApplied,
    List<PlayerMutation> formApplied,
    List<String> failures,              // logged, not thrown
    boolean partialFailure             // some effects applied, some failed
) {}
```

### Mutation Failure Behavior

**Question:** If mutation fails (e.g., player not found, field not writable), should the round still complete?

**Recommended behavior:** Round completes. Mutation is best-effort.

| Scenario | Round Outcome | Mutation Outcome | CareerSave |
|----------|---------------|-----------------|------------|
| Mutation service throws | Round completes | No mutation applied | Unchanged |
| Player not found in CareerSave | Round completes | Skip that player's mutation | Unchanged |
| Partial mutation (some players fail) | Round completes | Partial applied | Partially mutated |
| All flags off | Round completes | No mutation | Unchanged |
| V24 fallback to V23 | Round completes via V23 | No mutation | Unchanged |

**Rationale:** Blocking a round because of a career mutation failure would be a worse user experience than completing the round without mutation. Log the failure clearly so operators can detect and investigate.

---

## 8. Data Model Impact

### Existing SessionPlayer Fields

| Field | Type | Current Usage | Mutation Target? |
|-------|------|---------------|------------------|
| `energy` | int | Set at career creation; not modified by simulation | Yes — drain on V24 match |
| `form` | int? | May exist; not modified by simulation | Yes — update on V24 match |
| `injured` | boolean | Set manually or by career events | Yes — set from V24 INJURY event |
| `injuryType` | String | Set manually or by career events | Yes — set from V24 INJURY event |
| `injuryRemainingMatches` | int | Decremented by career admin logic | Yes — set from V24 INJURY event |

### Missing Data (Not in SessionPlayer Today)

| Gap | Needed For | Decision Required |
|-----|-----------|-------------------|
| Yellow card accumulation | Discipline tracking | V24D6D |
| Suspension remaining matches | Discipline tracking | V24D6D |
| Season stats (goals/assists/shots) | Stat tracking | V24D6X |
| Morale/confidence | Form updates | V24D6E |
| Last N match ratings | Form trend calculation | V24D6E |

### V24D6B/V24D6C Minimal Data Model

For V24D6B (injury) and V24D6C (fatigue), **no new SessionPlayer fields are required**:
- Use existing `injured`, `injuryType`, `injuryRemainingMatches` for injury persistence
- Use existing `energy` for fatigue persistence

V24D6D (cards/suspensions) would require a new discipline model if approved separately.

---

## 9. Mutation Ordering

### Recommended Order

```
1. Simulate V24 match → V24DetailedMatchResult
2. Map V24DetailedMatchResult → MatchResultData (aggregate)   ← existing
3. Persist V24 detail snapshot (if persist-detail=true)        ← existing
4. Apply V24 career mutations (if mutate-career-state=true)    ← NEW
5. Persist CareerSave                                         ← existing
6. Return RoundResult (aggregate + detail reference)
```

### Rationale

Mutation should occur **after** detail persistence and **before** CareerSave persistence, because:
- Mutation applies to SessionPlayer/SessionTeam which are part of CareerSave
- CareerSave is the final state that gets persisted
- V24 detail snapshot should reflect the match events themselves, not post-mutation state

### Alternative Order Considered

**Apply mutation after CareerSave persistence:**
- Risk: if mutation fails, CareerSave may already be persisted with partial state
- Mitigation: use transaction-like pattern (but CareerSave has no transaction model)
- Rejected: best-effort is simpler

**Apply mutation before detail persistence:**
- No significant advantage
- Detail snapshot should reflect match state at conclusion, not post-mutation
- Rejected: maintains clean separation

---

## 10. Failure and Rollback Policy

### Scenario Analysis

| Scenario | Round Behavior | Mutation Behavior | CareerSave |
|----------|---------------|-------------------|------------|
| V24 context build fails | Fallback to V23/default | No mutation | Unchanged |
| V24 simulation fails | Fallback to V23/default | No mutation | Unchanged |
| Detail persistence fails | Round completes | Mutation still proceeds (independent) | Mutated |
| Mutation service throws (fatal) | Round completes | No mutation applied | Unchanged |
| Injury mutation fails (per-player) | Round completes | Skip injury, other mutations proceed | Partial |
| Fatigue mutation fails (per-player) | Round completes | Skip fatigue, other mutations proceed | Partial |
| All mutation flags false | Round completes | No mutation | Unchanged |
| mutate-career-state=false | Round completes | No mutation | Unchanged |

### Rollback Mechanism

**Flag rollback:** Set `app.simulation.v24.mutate-career-state=false` → all mutation stops immediately. Next round is unaffected.

**No data rollback:** CareerSave mutated data is not automatically reverted. If a bad mutation occurs:
1. Disable flags immediately
2. Investigate mutation applier logic
3. Fix and redeploy
4. Future rounds are unaffected
5. Historical mutation effects remain (manual correction via career admin if needed)

**No automatic revert:** V24 career mutation is not designed to self-heal old career state. Accept this limitation for V24D6B.

### Persistence Compatibility

- Old CareerSave files without new mutation fields load with defaults
- `injured=false`, `energy=100`, `injuryRemainingMatches=0` for fields not present
- No migration required

---

## 11. Testing Strategy

### Implemented and Future Test Classes

| Test Class | Status | Tests | Notes |
|------------|--------|-------|-------|
| `V24InjuryMutationApplierTest` | Implemented | 21 | V24D6B1 — policy flags, null guards, flag-disabled, unknown player, already injured, duplicate events |
| `V24CareerMutationServiceTest` | Implemented | 33 | V24D6B2/C2 — mutation service orchestration, null guards, flag combinations, exception handling, result object behavior, fatigue orchestration |
| `V24CareerMutationIntegrationTest` | Implemented | 19 | V24D6B3/C3 — LeagueSimulator wiring, allFlagsFalse, masterFlagFalse, specificFlagFalse, V24DisabledWithMutationFlags, defaultPathNoMutation, roundCompletion, fatigue flag combinations |
| `V24FatigueMutationApplierTest` | Implemented | 27 | V24D6C1 — energy drain, null guards, flag combinations, floor at 0, unknown player skip, injured skip, substitute-only drain, custom drain values, null energy default |
| `V24CareerMutationPolicyTest` | Optional | — | Future — only needed if policy grows complex |
| `V24CareerMutationRollbackTest` | Future | — | Only needed if rollback behavior expands beyond current best-effort |

### Test Scenarios

**Flag combination tests:**
- All flags false → no mutation
- `mutate-career-state=true` + `persist-injuries=true` + others false → only injuries mutate
- `mutate-career-state=false` + individual flags true → no mutation (master gate off)
- All flags true → full mutation

**Injury tests:**
- Player with no injury events → SessionPlayer unchanged
- Player with INJURY event → `injured=true`, `injuryRemainingMatches` set
- Multiple injury events in same match → apply once (first event wins)
- Player not in CareerSave → skip, log warning

**Fatigue tests:**
- Player with 90 minutes → energy drain proportional to stamina cost
- Player with 0 minutes (unused) → no energy drain
- Player already at low energy → drain applies, can go to 0
- Energy floors at 0 (cannot go negative)

**Fallback tests:**
- V24 context build fails → V23 path, no mutation
- V24 simulation fails → V23 path, no mutation
- LeagueSimulator with flags off → standard round, no mutation

**V23 path tests:**
- V23 or default simulator path → never triggers V24CareerMutationService
- CareerSave unchanged after V23 round

**Compatibility tests:**
- Old CareerSave without energy/injury fields → loads with defaults
- Mutation applied to old save → fields populated correctly

---

## 12. Risks

### Risk 1: Over-mutating players

**Description:** If injury probability is too high, large portions of a squad become unavailable. After several rounds, lineup selection becomes impossible.

**Mitigation:** Cap maximum injuries per match (e.g., max 2 players injured per team per match). Implement a minimum squad availability requirement before allowing simulation to proceed.

### Risk 2: Impossible lineups

**Description:** Accumulated injuries + suspensions + tired players may make it impossible to field a valid starting XI.

**Mitigation:** V24D6F integration tests must cover this scenario. Allow lineup reset or auto-recovery if availability drops below threshold.

### Risk 3: Fatigue calibration

**Description:** If energy drains too fast, players are exhausted after 2-3 matches. If too slow, fatigue has no gameplay effect.

**Mitigation:** Calibrate drain rate against real match frequency. Target: players need rotation after 4-6 matches if energy starts at 100.

### Risk 4: Missing suspension model

**Description:** Without a discipline model, cards accumulate but have no consequence. Users may exploit this by playing aggressively without downside.

**Mitigation:** V24D6D must design and implement suspension logic before cards have career consequences. Do not implement card mutation without suspension.

### Risk 5: Save compatibility

**Description:** Adding mutation to existing CareerSave files may cause unexpected behavior if old saves have unusual state.

**Mitigation:** All new fields default to "no effect" state. No mandatory migrations. Comprehensive fallback behavior.

### Risk 6: User confusion — why is player unavailable?

**Description:** If mutation introduces injury/fatigue/suspension but the UI does not show the reason, users will be confused and blame bugs.

**Mitigation:** V24D6G (UI indicators) is co-required with mutation. Frontend must display unavailable player status clearly. If UI is not ready, do not enable mutation flags.

---

## 13. Recommendation

### First Implementation: V24D6B (Injury Persistence Only)

1. **Scope:** Apply INJURY events from V24 match to `SessionPlayer.injured`, `injuryType`, `injuryRemainingMatches`.
2. **Flags:** `mutate-career-state=true` (master), `persist-injuries=true` (specific).
3. **No new schema:** Use existing `SessionPlayer` injury fields.
4. **No new UI:** V24D6B does not require frontend changes. Injury effects are visible in lineup selection (existing behavior).
5. **Tests first:** Write V24InjuryMutationApplierTest before wiring into LeagueSimulator.
6. **Calibration:** Cap injury probability per match to avoid squad decimation.

### Why Not V24D6B + V24D6C Together?

- Injury and fatigue are independent effects
- Separate test suites for each enable parallel development
- If injury pipeline has bugs, fatigue changes are not affected
- V24D6C calibration (energy drain rate) is separate from injury logic
- Recommend: prove V24D6B in production before adding fatigue

### Why Not V24D6D (Cards) Next?

- No existing suspension model in SessionPlayer
- Cards have no career consequence today
- Implementing cards without suspension would be game-breaking
- V24D6D requires new discipline schema and V24D6G (UI) co-design
- Recommend: defer until injury pipeline is proven stable

---

## 14. Non-Goals

V24D6A does NOT include:

- **No code implementation** — design document only
- **No frontend changes** — UI indicators are V24D6G, future phase
- **No API changes** — mutation is internal to LeagueSimulator
- **No Redis schema changes** — mutation targets CareerSave, not Redis detail keys
- **No MatchFixture.MatchResultData change** — aggregate result remains 6 fields
- **No automatic production enablement** — all flags default false, manual opt-in required
- **No mutation for V23/default paths** — V24CareerMutationService is never called for non-V24 rounds
- **No new SessionPlayer fields for V24D6B** — use existing injury/energy fields
- **No new SessionPlayer fields for V24D6C** — use existing energy field

---

## 15. Completion Criteria for V24D6A

- [x] Document created
- [x] Phase breakdown approved (V24D6B through V24D6G)
- [x] Flags defined and approved
- [x] Architecture proposed and reviewed
- [x] Risks documented and mitigations listed
- [x] V24D6B implementation scope is clear
- [x] Non-goals explicit
- [x] Recommended order: V24D6B first, then V24D6C, then V24D6D

## 16. V24D6B Implementation Completion Record

**V24D6B1 — Injury Mutation Applier** (commit `f3d863a`)
- `V24CareerMutationPolicy` — immutable policy with flag evaluation
- `V24InjuryMutationApplier` — applies INJURY events to SessionPlayer
- `V24InjuryMutationApplierTest` — 21 tests
- Default: `injuryType = "MATCH_INJURY"`, `injuryRemainingMatches = 2`
- No new schema; uses existing SessionPlayer fields

**V24D6B2 — Mutation Service Orchestration** (commit `91e2f04`)
- `V24CareerMutationResult` — immutable result with defensive copy
- `V24CareerMutationService` — orchestrates appliers, catches exceptions
- `V24CareerMutationServiceTest` — 19 tests (injury-only)
- Service-level fatal failure → no mutation; per-player failure → skip and continue
- V24InjuryMutationApplier: removed `final` for test subclassing

**V24D6B3 — LeagueSimulator Wiring + Flags** (commit `a11bc67`)
- 5 mutation flags added to `application.yaml` (all default false):
  - `app.simulation.v24.mutate-career-state=false` (master gate)
  - `app.simulation.v24.persist-injuries=false`
  - `app.simulation.v24.persist-fatigue=false`
  - `app.simulation.v24.persist-discipline=false`
  - `app.simulation.v24.persist-form=false`
- `SimulationConfig` injects mutation properties
- `LeagueSimulator` applies mutation after V24 success and detail persistence
- `V24CareerMutationIntegrationTest` — 13 tests
- Regression gate: 459 tests, 0 failures

**V24D6B3 behavior confirmed:**
- `mutate-career-state` master gate required for all mutation
- `persist-injuries` requires master gate true
- V24 disabled → no mutation (even with flags true)
- V23/default path → no mutation
- `persist-detail` independent from mutation
- `expose-detail-api` independent from mutation
- Mutation failure best-effort, does not fail round
- No fatigue/cards/form implementation
- No schema/API/Redis/frontend changes

## 16b. V24D6C Implementation Completion Record

**V24D6C1 — Fatigue Mutation Applier** (commit `982293b`)
- `V24FatigueMutationApplier` — pure helper for energy drain
- `V24FatigueMutationApplierTest` — 27 tests
- Constants: `DEFAULT_FULL_MATCH_DRAIN = 12`, `DEFAULT_SUBSTITUTE_DRAIN = 6`
- Constructor accepts optional custom drain values
- Algorithm: first pass collects SUBSTITUTION-only players; second pass removes players who appear in non-substitution events from substituteOnly set; drain based on which set the player is in
- Uses `SessionPlayer.getEnergy()`/`setEnergy(Integer)`, skips injured players (Boolean.TRUE.equals(player.getInjured()))
- Energy floors at 0
- No new schema — uses existing SessionPlayer.energy field

**V24D6C2 — Mutation Service Fatigue Orchestration** (commit `91e2f04`)
- `V24CareerMutationService` — added two-arg constructor (injury + fatigue appliers); single-arg delegates with default fatigue applier
- `V24CareerMutationResult` — added `failure(injuries, fatigue, failures, partial)` factory for preserving mutation counts on failure
- `V24CareerMutationServiceTest` — 14 new fatigue orchestration tests (33 total now)
- `applyFatigue()` called when `policy.isFatiguePersistenceEnabled()`
- Service-level failure handling: `failure(injuries, fatigue, failures, atLeastOneSuccess)` preserves partial=true only when at least one mutation succeeded
- Failures caught independently; partial success preserved

**V24D6C3 — LeagueSimulator Fatigue Wiring** (commit `0dc184a`)
- `LeagueSimulator.applyV24CareerMutation()` — now logs `fatigueApplied()` count alongside `injuriesApplied()`
- `V24CareerMutationIntegrationTest` — 6 new integration tests (19 total now)
- Test coverage: master+persistFatigue flag combos, V24 disabled, V23 path, persist-detail independence, no cards/form
- V24D6C3 required no LeagueSimulator constructor change — V24D6C2's single-arg constructor already injected fatigue applier internally
- Regression gate: 506 tests, 0 failures

**V24D6C3 behavior confirmed:**
- `mutate-career-state` master gate required for fatigue mutation
- `persist-fatigue=true` requires `mutate-career-state=true` to have effect
- V24 disabled → no fatigue mutation (even with flags true)
- V23/default path → no fatigue mutation
- Fatigue skips injured players (Boolean.TRUE.equals(player.getInjured()))
- Substitute-only players (appear in SUBSTITUTION events, no other events) drain 6; full-match players drain 12
- Energy floors at 0
- Null energy defaults to 100 before drain
- No cards/form implementation

## 17. Recommended Next Step

**V24D6D — Cards/Suspensions Design** or **V24D6F — Extended Integration Tests**.

V24D6D (cards/suspension discipline model) requires a new data model and UI indicators (V24D6G) before implementation.

---

*This document is the authoritative V24D6 design specification. V24D6B1/B2/B3 implementation conforms to this design. Remaining phases (V24D6C/D/E/F/G) are deferred.*