# V24D5 — Production Integration Plan

**Status:** V24D5B COMPLETED — LeagueSimulator V24 path added behind default-false flag; detail persistence still deferred
**Branch:** `mvp-1-performance-cleanup`
**Latest implementation commit:** `cca2f6e` (feat: add V24 LeagueSimulator path behind feature flag — V24D5B)
**Latest docs commit:** `f1f5549` (V24D5 planning updated)
**Tests:** 368 total (354 before V24D5B + 11 V24D5B + 3 extra), 0 failures
**Created:** 2026-05-09

---

## 1. Executive Summary

V24 should **NOT** replace V23 immediately. It should be introduced as a third simulation path behind a default-false feature flag. The existing V23 and default simulator paths remain stable and unchanged.

**Key decisions:**
- `MatchFixture.MatchResultData` remains the source of truth for league standings (6 fields only).
- V24 detailed data is **additive** and stored separately in Redis at `career:{careerId}:match-detail:{matchId}`.
- The query endpoint (`GET /api/careers/{careerId}/matches/{matchId}/detail`) already exists behind `app.simulation.v24.expose-detail-api=false`.
- Production integration must be **phased and reversible**.
- V24 context invalidity should fall back to V23/default simulator, not fail the round.

---

## 2. Current Stable Baseline

| Item | Value |
|------|-------|
| Latest implementation commit | `cca2f6e` (V24D5B complete) |
| Latest docs commit | `f1f5549` |
| Tests | 368 full suite total; 365 regression gate, 0 failures |
| V24 engine | `V24DetailedMatchEngine` — V24 path now wired in LeagueSimulator |
| Context factory | `V24MatchContextFactory` — now wired to production via V24 path |
| Redis adapter | `V24DetailedMatchRedisAdapter` — exists, not called by simulation |
| Storage port | `V24DetailedMatchStoragePort` — interface only |
| Query service | `V24DetailedMatchQueryService` — read-only, feature-gated |
| Query endpoint | `GET /api/careers/{careerId}/matches/{matchId}/detail` — exists |
| Endpoint flag | `app.simulation.v24.expose-detail-api=false` (default false) |
| Redis key format | `career:{careerId}:match-detail:{matchId}` |
| LeagueSimulator V24 path | Exists behind `app.simulation.league.use-v24-detailed-engine=false` |
| Flag precedence | V24 > V23 > default |
| Detail persistence | Still deferred; no Redis save in simulation flow |
| No production wiring | V24 simulation path wired but detail persistence not yet implemented |

---

## 3. Existing Production Simulation Flow

```
CareerSessionService / RoundController
  → LeagueSimulator.simulateRound(career, round)
      → DefaultMatchSimulator.simulateQuick()   [default path]
        OR MatchEngineImpl.simulateWithStrength()  [V23 flag path]
      → MatchResult (homeGoals, awayGoals, possession, shots, events, summary)
      → MatchResultDataAdapter.toMatchResultData()  → MatchFixture.MatchResultData (6 fields)
      → CareerSave persisted to Redis
```

**Existing flags (from application.yaml):**
- `app.simulation.league.use-v23-engine=false` — enables V23 MatchEngineImpl path

**V23 path remains completely unchanged by V24D5.**

---

## 4. Proposed V24 Production Flow

```
RoundController
  → LeagueSimulator.simulateRound(career, round)
      → IF use-v24-detailed-engine:
          → V24MatchContextFactory.build(career, fixture, homeTeam, awayTeam, seed)
              → V24MatchContext (home/away team ID, formation, starting XI, style, seed)
          → V24DetailedMatchEngine.simulate(context)
              → V24DetailedMatchResult (goals, xG, possession, shots, timeline, summary)
          → V24DetailedMatchResultAdapter.toMatchResultData()
              → MatchResultData (6 fields, same as V23 path — for standings)
          → IF persist-detail=true:
              → V24DetailedMatchData.fromResult(careerId, season, round, homeName, awayName, result, playerRatings)
              → V24DetailedMatchStoragePort.save(careerId, detail)
          → CareerSave persisted (MatchResultData unchanged)
      → ELSE: existing V23/default path unchanged
```

**Critical invariant:** Even when V24 is enabled, `MatchResultData` stores only 6 aggregate fields. V24 detail is **additive and best-effort** — a failed detail save does not fail the match or standings update.

---

## 5. Required Feature Flags

```yaml
app:
  simulation:
    league:
      use-v23-engine: false      # existing V23 path flag
      use-v24-detailed-engine: false  # V24D5: enable V24 simulation path
    v24:
      persist-detail: false        # V24D4B: store V24DetailedMatchData in Redis
      expose-detail-api: false    # V24D4C: GET /detail endpoint
```

**Independence rules:**
- `use-v24-detailed-engine=true` does NOT auto-enable `persist-detail` or `expose-detail-api`
- `persist-detail=true` does NOT auto-enable `expose-detail-api`
- `expose-detail-api=true` does NOT auto-enable simulation
- All three default to **false**

**Recommended production rollout sequence:**
1. All flags false — V23/default path, no V24
2. Enable `use-v24` in local/dev only — validate V24 engine behavior
3. Enable `persist-detail=true` in dev — validate Redis writes
4. Enable `expose-detail-api=true` in dev — validate query endpoint
5. Staging soak — 1-2 weeks with all flags on
6. Limited production rollout — small %, monitor
7. Full production rollout — only after staging stability confirmed

---

## 6. V24MatchContextFactory Design

**New class:** `V24MatchContextFactory`

**Responsibilities:**
- Build `V24MatchContext` from `CareerSave`, `MatchFixture`, `SessionTeam` home/away, deterministic seed
- Resolve `matchId` from `MatchFixture.matchId`
- Resolve team IDs and names
- Parse formations from `SessionTeam.formation`
- Resolve starting XI from `CareerSave.teamStarting11` → `List<SessionPlayer>`
- Derive bench from player pool minus starting XI
- Derive or default `TeamStyle` (default: `BALANCED`)
- Validate 11 starters per team
- Validate player IDs exist in `CareerSave.playerManager`

**Fallback policy (critical):**
- Invalid V24 context (missing lineup, unknown players) → **fall back to V23/default path**, log warning
- Do NOT fail the entire round simulation due to V24 context build failure
- V24 context validation errors are logged but do not block match completion

**Design questions:**
| Question | Recommendation |
|----------|----------------|
| Missing lineup fallback? | V24D5A: missing lineup is invalid — build() throws IllegalArgumentException, canBuild() returns false. V24D5B: LeagueSimulator catches invalid context and falls back to V23/default path, log warning. Optional top-11 fallback considered later but not implemented in V24D5A. |
| Missing bench? | Empty bench is acceptable for V24 |
| Team style default? | `BALANCED` if not persisted |
| Invalid context behavior? | V24D5A: build() throws IllegalArgumentException; canBuild() returns false. V24D5B: LeagueSimulator fallback to V23/default path, log warning. |

---

## 7. Detail Persistence Policy

| Rule | Detail |
|------|--------|
| When to persist | After successful V24 result generation, only if `persist-detail=true` |
| Persistence failure | Log error, continue match — detail is best-effort |
| Idempotency | Same `matchId` overwrites existing detail |
| Re-simulation | Overwrites previous detail snapshot |
| Cleanup | `deleteByCareerId` called when career deletion flow integrates (future) |
| Endpoint missing data | Returns 404 regardless of cause (flag off, no data, Redis error) |

---

## 8. Adapter Policy

| Adapter | Purpose |
|---------|---------|
| `V24DetailedMatchResultAdapter` | Maps `V24DetailedMatchResult` → `MatchResultData` (6 fields only) |
| `V24DetailedMatchData.fromResult(...)` | Builds immutable detail snapshot for Redis |
| `V24PlayerMatchStatsModel` | Derives `playerRatings` stat bundle from timeline |
| `V24PlayerRatingModel` | Computes per-player rating from timeline |

**Note on shot coordinates:** `V24MatchEvent.shotCoordinate` remains null until V24D3C optional schema enrichment attaches coordinates to events. Without V24D3C, shot map tab cannot be fully populated but no critical functionality is blocked.

---

## 9. Rollback Strategy

**Flag rollback (safe):**
- Turn off `use-v24-detailed-engine` → V23/default path resumes immediately
- Persisted detail keys remain in Redis, untouched
- Endpoint can remain enabled or disabled independently

**Hard rollback (if needed):**
- Revert V24D5 integration commit only
- Redis detail keys are **additive** — no migration rollback needed
- `MatchResultData` schema is unchanged — no fixture migration needed

**No rollback needed for:**
- V24D5A context factory (no runtime effect until wired)
- V24D5B LeagueSimulator branch (default false, no effect until enabled)

---

## 10. Risk Table

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|-----------|
| V24 context build failure (missing lineup) | MEDIUM | MEDIUM | Fall back to V23, log warning — round completes |
| Player lookup mismatch | LOW | MEDIUM | Validate IDs at context build, fallback on error |
| Redis save failure | MEDIUM | LOW | Log error, continue — detail is best-effort |
| Large Redis payload (long timeline) | MEDIUM | MEDIUM | Snapshot is immutable; pagination deferred to future |
| Endpoint exposed without data | LOW | LOW | 404 is correct behavior; flag is off by default |
| V24 result diverges from V23 stability | MEDIUM | HIGH | Extensive regression testing before production enable |
| V24 performance slower than quick sim | MEDIUM | MEDIUM | Benchmark in dev before staging; async option for future |
| Accidental production enablement | LOW | HIGH | All flags default false; manual opt-in required |
| Career state side effects (injuries/fatigue) | LOW | MEDIUM | V24C does not mutate SessionPlayer — no side effects yet |
| Frontend depends on null shotCoordinate | MEDIUM | LOW | V24D3C can attach later; frontend handles null gracefully |

---

## 11. Testing Strategy

**V24D5A test suite:** `V24MatchContextFactoryTest` — 20 tests covering context factory, starting XI resolution, bench derivation, style defaults, validation, canBuild.

**V24D5B test suite:** `V24LeagueSimulationPathTest` — 11 tests covering flag behavior, result mapping, fallback, no persistence, round completion.

**V24D5C tests (pending):** Persistence write tests for when `persist-detail=true`.

**V24D5D tests (pending):** End-to-end flag combination integration tests.

### V24MatchContextFactoryTest (V24D5A — Complete)
- Missing lineup is rejected by V24MatchContextFactory; build() throws IllegalArgumentException, canBuild() returns false
- Invalid player IDs handled gracefully
- Bench derivation excludes starters
- Team style defaults to BALANCED
- Formation parsing handles all supported formats

### V24LeagueSimulationPathTest (V24D5B — Complete)
- Flag `use-v24-detailed-engine=false` → existing path unchanged
- Flag `use-v24-detailed-engine=true` → V24 engine called
- V24 aggregate result maps to `MatchResultData` (6 fields preserved)
- `persist-detail=false` / V24D5B behavior → no Redis detail write
- V24D5B does not call `V24DetailedMatchStoragePort.save(...)`
- V24D5B does not call `V24DetailedMatchData.fromResult(...)`
- Redis save behavior is deferred to V24D5C

### V24D5C Tests (Pending)
- `persist-detail=true` → Redis save occurs
- Redis save failure does not fail round
- detail save is best-effort
- these tests are pending, not part of V24D5B

### V24ProductionIntegrationRegressionTest (V24D5D — Pending)
- All fixtures complete when V24 enabled
- Standings update correctly with V24 path
- Deterministic seed produces identical V24 result
- `MatchResultData` (6 fields) unchanged regardless of V24 enablement
- End-to-end with all flag combinations

---

## 12. Implementation Phases

| Phase | Content | Risk | Dependency |
|-------|---------|------|------------|
| V24D5A | `V24MatchContextFactory` only — no LeagueSimulator wiring | LOW | V24D4C complete ✅ Completed |
| V24D5B | Third LeagueSimulator path behind `use-v24-detailed-engine=false` | MEDIUM | V24D5A complete | **Completed** |
| V24D5C | Detail persistence write behind `persist-detail=false` | MEDIUM | V24D5B complete |
| V24D5D | End-to-end integration tests for all flag combinations | MEDIUM | V24D5C complete |
| V24D5E | Frontend planning/design — no implementation | — | Separate approval |

**V24D5A is the recommended first step** — no runtime behavior change, fully testable in isolation, prepares V24D5B without activating it.

---

## 13. Non-Goals

- No frontend implementation in V24D5
- No V24D3C schema enrichment unless separately approved
- No `MatchFixture.MatchResultData` schema change
- No `CareerSave` schema change unless separately planned
- No `SessionPlayer` mutation for injuries/fatigue (V24 isolated design)
- No automatic production enablement
- No removal of V23 path
- No replacement of `DefaultMatchSimulator`
- No V24 engine call from endpoint (already read-only)

---

## V24D5A Completion Record

**Commit:** `8470779` — `feat: add V24 match context factory`
**Date:** 2026-05-09
**Tests:** 20 new (`V24MatchContextFactoryTest`), 354 total, 0 failures
**V24D5A delivered:**
- `V24MatchContextFactory` — factory building `V24MatchContext` from `CareerSave`, `MatchFixture`, `SessionTeam` home/away, seed
- `build(CareerSave, MatchFixture, SessionTeam, SessionTeam, long seed)` — primary API, styles default to `TeamStyle.BALANCED`
- `buildWithStyles(CareerSave, MatchFixture, SessionTeam, SessionTeam, TeamStyle, TeamStyle, long seed)` — explicit styles overload
- `canBuild(CareerSave, MatchFixture, SessionTeam, SessionTeam)` — returns false on validation failure, never throws
- Starting XI resolved from `CareerSave.teamStarting11` keyed by `MatchFixture.homeTeamId`/`awayTeamId`
- Each starter ID looked up via `CareerSave.getSessionPlayer(playerId)`
- Bench derived from `CareerSave.getTeamSquad(teamId)` minus 11 starter IDs (may be empty)
- Missing lineup does NOT fallback inside factory — build() throws IllegalArgumentException, canBuild() returns false
- Invalid context throws in build()
- Invalid context returns false in canBuild()
- Production fallback to V23/default is deferred to V24D5B LeagueSimulator path
- **No LeagueSimulator/SimulationConfig/MatchEngineImpl changes** — factory is isolated
- **No API/controller/Redis adapter changes**
- **No persistence writes** — read-only factory
- **No runtime behavior change** — not called from production simulation
- **V24MatchContext unchanged** — no field additions needed
- **MatchFixture.MatchResultData unchanged**
- **CareerSave schema unchanged**
- **SessionPlayer/SessionTeam not mutated**
- Regression gate: 354 tests, 0 failures

---

## V24D5B Completion Record

**Commit:** `cca2f6e` — feat: add V24 LeagueSimulator path behind feature flag
**Date:** 2026-05-09
**Tests:** 11 new (`V24LeagueSimulationPathTest`), 368 total, 0 failures
**V24D5B delivered:**
- `LeagueSimulator.simulateWithV24Engine()` — third simulation path using V24DetailedMatchEngine when flag is true
- `SimulationConfig` — injected `use-v24-detailed-engine` property alongside existing `use-v23-engine` flag
- `application.yaml` — added `app.simulation.league.use-v24-detailed-engine: false` (default false)
- `V24LeagueSimulationPathTest` — 11 tests covering: flag disabled uses default path, V23 path precedence, V24 path activated, result maps to 6 fields, no Redis persistence, context build failure falls back safely, round completes on failure, V24 flag defaults false, existing tests pass, no MatchFixture schema change, all fixtures complete
- Flag precedence: V24 > V23 > default (when respective flags are true)
- `V24MatchContextFactory.build()` called to build context from CareerSave + MatchFixture + SessionTeam home/away + seed
- `V24DetailedMatchResultAdapter.toMatchResultData()` maps V24DetailedMatchResult to MatchFixture.MatchResultData (6 fields)
- Context build failure catches `IllegalArgumentException` and falls back to default engine — round completes
- **No V24DetailedMatchStoragePort.save(...)** — no Redis detail persistence in simulation flow
- **No API/controller/frontend changes**
- **MatchFixture.MatchResultData schema unchanged** — 6 fields only
- **CareerSave schema unchanged**
- **SessionPlayer/SessionTeam not mutated**
- Regression gate: 365 tests (regression set), 0 failures; 368 full suite total

---

## 14. Recommended Next Step

**V24D5C — Detail persistence write behind `persist-detail=false` flag.**

**Why:**
- V24D5B delivered the third LeagueSimulator path behind `use-v24-detailed-engine=false`.
- The V24 path currently maps only aggregate result data into `MatchFixture.MatchResultData`.
- Detail persistence is the next additive step: save `V24DetailedMatchData` to Redis only when `app.simulation.v24.persist-detail=true`.
- The flag remains default false, so there is no accidental persistence.
- Detail save must be best-effort: failure logs and the round still completes.

**V24D5C deliverable:**
Add `V24DetailedMatchData.fromResult(...)` and `V24DetailedMatchStoragePort.save(...)` call to the V24 simulation path when `persist-detail=true`, with tests for save/no-save/failure behavior.

---

## 15. Required Regression Gate

After any V24 change, the full regression gate:

```
mvn test -Dtest=V24MatchContextFactoryTest,V24DetailedMatchQueryServiceTest,V24DetailedMatchRedisAdapterTest,V24DetailedMatchDataTest,V24PlayerMatchStatsModelTest,V24ShotCoordinateTest,V24PlayerRatingModelTest,V24AssistModelTest,V24FormationParserTest,V24SubstitutionEngineTest,V24InjuryModelTest,V24DisciplineModelTest,V24FatigueModelTest,V24DetailedMatchEngineDeterminismTest,V24TimelineOrderingTest,V24DetailedMatchResultAdapterTest,V24MatchContextValidationTest,V24TimelineConsistencyTest,V24ShotXgModelTest,V24PlayerAttributionTest,LeagueSimulatorTest,MatchResultDataAdapterTest,TeamOverallCalculatorTest,MatchEngineImplStrengthSimulationTest,MatchEngineImplStyleSimulationTest,MatchQualityMetricsTest,V23SimulationQualityGateTest,MatchEngineImplRoleContributionTest,MatchEngineImplEventConsistencyTest,MatchEngineImplDeterminismTest,MatchEngineImplMetricsValidationTest,MatchEngineImplPoissonValidationTest,MatchQualityComputerTest,MatchEngineImplTest,DivisionTest,V24LeagueSimulationPathTest
```

**Expected:** 365 tests (regression gate), 0 failures; 368 full suite total.

---

*This document is the authoritative V24D5 production integration planning specification. V24D5A and V24D5B are complete. V24D5C implementation begins after approval.*