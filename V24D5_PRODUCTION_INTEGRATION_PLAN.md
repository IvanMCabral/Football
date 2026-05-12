# V24D5 — Production Integration Plan

**Status:** V24D5F+V24D3C COMPLETED — playerRatings populated in V24DetailedMatchData; player ratings UI complete in separate frontend repo; shot coordinates now attached to events (V24D3C); V24D5E5 shot map now possible
**Branch:** `mvp-1-performance-cleanup`
**Latest implementation commit:** `94b4962` (feat: attach shotCoordinates to V24 match events)
**Latest docs commit:** `58d35c6` (previous docs baseline before V24D5E4 documentation update)
**Tests:** 406 full suite total; 406 regression gate, 0 failures
**Created:** 2026-05-09
**Updated:** 2026-05-11

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
| Latest implementation commit | `94b4962` (V24D3C complete, shot coordinates attached to events) |
| Latest docs commit | `58d35c6` |
| Tests | 406 full suite total; 406 regression gate, 0 failures |
| V24 engine | `V24DetailedMatchEngine` — V24 path now wired in LeagueSimulator |
| Context factory | `V24MatchContextFactory` — now wired to production via V24 path |
| Redis adapter | `V24DetailedMatchRedisAdapter` — used through `V24DetailedMatchStoragePort.save(...)` only when V24 path succeeds and `app.simulation.v24.persist-detail=true` |
| Storage port | `V24DetailedMatchStoragePort` — injected into `LeagueSimulator` for best-effort V24 detail persistence |
| Query service | `V24DetailedMatchQueryService` — read-only, feature-gated |
| Query endpoint | `GET /api/careers/{careerId}/matches/{matchId}/detail` — exists |
| Endpoint flag | `app.simulation.v24.expose-detail-api=false` (default false) |
| Redis key format | `career:{careerId}:match-detail:{matchId}` |
| LeagueSimulator V24 path | Exists behind `app.simulation.league.use-v24-detailed-engine=false` |
| Flag precedence | V24 > V23 > default |
| Detail persistence | Implemented in V24D5C behind `app.simulation.v24.persist-detail=false`; default false — saves only when V24 path succeeds and flag is true |
| Production wiring | V24 simulation path and optional detail persistence are wired behind default-false flags; read-only frontend match detail page, fixture modal entry point, and player ratings UI all exist in the separate frontend repo; shot map and career-state mutation remain deferred |

**Persistence behavior (V24D5F):**
- V24 detail persistence is wired to `LeagueSimulator.persistV24Detail()`
- `V24PlayerRatingsAssembler` builds `playerRatings` from `CareerSave.teamStarting11` starting XI + `V24MatchTimeline`
- `V24DetailedMatchData.fromResult(...)` now receives populated `playerRatings` when detail is persisted
- Persistence **only** happens when **all three** conditions are true:
  1. `app.simulation.league.use-v24-detailed-engine=true`
  2. `app.simulation.v24.persist-detail=true`
  3. V24 simulation succeeds (context build succeeds and `v24Engine.simulate()` returns)
- Save failure is **best-effort**: `catch (Exception)` logs warning and the round still completes
- Context build failure **skips persistence** and falls back to default engine — round completes normally
- Flags are **independent**: `persist-detail=true` without V24 flag does not persist; `expose-detail-api=true` does not trigger persistence

**V24D3C (Shot Coordinate Event Attachment — Completed):**
- `V24MatchEvent` now carries optional `shotCoordinate` field (via `withShotCoordinate()` builder)
- GOAL/SHOT/SHOT_ON_TARGET/BLOCK/MISS events attach `V24ShotCoordinate`; non-shot events remain null
- `V24MatchEventDto.fromEvent()` maps coordinates through `V24ShotCoordinateDto.fromCoordinate()`
- Generated in `V24DetailedMatchEngine.attemptShot()` before goal resolution using passed Random (deterministic)
- No xG formula change, no Redis key format change, no MatchFixture schema change, no CareerSave mutation
- V24D3C is the final prerequisite for V24D5E5 shot map UI
- 8 new tests in `V24ShotCoordinateAttachmentTest`; regression gate: 406 tests, 0 failures
- **Commit:** `94b4962`

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

**Note on shot coordinates:** `V24MatchEvent.shotCoordinate` is now populated for GOAL/SHOT/SHOT_ON_TARGET/BLOCK/MISS events after V24D3C. Non-shot events, old persisted details, or missing detail may still have null shotCoordinate. V24D5E5 shot map UI is now unblocked from backend side but remains unimplemented.

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
| Frontend depends on null shotCoordinate | MEDIUM | LOW | V24D3C now attaches shotCoordinate to shot-result events; frontend must still handle null for non-shot events, old persisted details, or missing data |

---

## 11. Testing Strategy

**V24D5A test suite:** `V24MatchContextFactoryTest` — 20 tests covering context factory, starting XI resolution, bench derivation, style defaults, validation, canBuild.

**V24D5B test suite:** `V24LeagueSimulationPathTest` — 11 tests covering flag behavior, result mapping, fallback, no persistence, round completion.

**V24D5C tests:** `V24LeagueDetailPersistenceTest` — 9 tests covering save/no-save/failure/fallback/no-API behavior.

**V24D5D tests:** `V24EndToEndFlagIntegrationTest` — 12 tests completed for all flag combinations.

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

### V24D5C Tests (Completed)
- `persist-detail=true` → Redis save occurs
- `persist-detail=false` → Redis save does NOT occur
- Redis save failure does not fail round — best-effort
- detail save is best-effort — failure logs warning and round completes
- context build failure skips persistence and falls back safely
- `persist-detail=true` without V24 flag does not persist
- `expose-detail-api=true` does not trigger persistence
- aggregate MatchResultData still written to fixture regardless of persistence
- MatchFixture.MatchResultData schema unchanged (6 fields)
- these tests are in `V24LeagueDetailPersistenceTest` (9 tests)

### V24EndToEndFlagIntegrationTest (V24D5D — Complete)
- all flags false (default path)
- V24 enabled + persist disabled (aggregate only)
- V24 enabled + persist enabled (saves detail)
- V24 disabled + persist enabled (no persistence — flags independent)
- expose-detail-api alone does not trigger simulation/persistence
- all flags true saves detail and completes round
- context failure falls back and skips persistence
- save failure is best-effort
- V24 > V23 > default precedence
- MatchResultData (6 fields) unchanged
- no career state mutation

---

## 12. Implementation Phases

| Phase | Content | Risk | Dependency |
|-------|---------|------|------------|
| V24D5A | `V24MatchContextFactory` only — no LeagueSimulator wiring | LOW | V24D4C complete | **Completed** |
| V24D5B | Third LeagueSimulator path behind `use-v24-detailed-engine=false` | MEDIUM | V24D5A complete | **Completed** |
| V24D5C | Detail persistence write behind `persist-detail=false` | MEDIUM | V24D5B complete | **Completed** |
| V24D5D | End-to-end integration tests for all flag combinations | MEDIUM | V24D5C complete | **Completed** |
| V24D5F | Player ratings persistence in V24DetailedMatchData | LOW | V24D5C complete | **Completed** |
| V24D3C | Shot coordinate event attachment (V24MatchEvent schema) | LOW | V24D3A complete | **Completed** |
| V24D5E | Frontend match detail implementation in separate frontend repo: E1/E2/E3/E3B/E4 complete; E5 shot map pending | MEDIUM | V24D4C + V24D5F; V24D5E5 unblocked by V24D3C | E1/E2/E3/E3B/E4 Completed; E5 Pending |

**V24D5A is the recommended first step** — no runtime behavior change, fully testable in isolation, prepares V24D5B without activating it.

---

## 13. Non-Goals

- No additional frontend implementation in this backend/root repo; frontend V24D5E1/V24D5E2/V24D5E3/V24D5E3B/V24D5E4 are complete in the separate frontend repo, and V24D5E5 shot map UI remains pending.
- No further V24 schema enrichment in V24D5; V24D3C shotCoordinate attachment is complete, and future schema work requires separate approval.
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

## V24D5C Completion Record

**Commit:** `d6b3661` — feat: persist V24 detailed match data behind feature flag
**Date:** 2026-05-09
**Tests:** 9 new (`V24LeagueDetailPersistenceTest`), 377 total full suite, 374 regression gate, 0 failures
**V24D5C delivered:**
- `LeagueSimulator` — added `persistDetail` flag and `V24DetailedMatchStoragePort` dependency; `persistV24Detail()` method called after V24 result when `persistDetail=true` and `storagePort != null`
- `SimulationConfig` — wires `app.simulation.v24.persist-detail` property to `LeagueSimulator` constructor alongside `V24DetailedMatchStoragePort` injection
- `application.yaml` — `app.simulation.v24.persist-detail: false` remains default false
- `V24LeagueDetailPersistenceTest` — 9 tests covering: save not called when `persistDetail=false`, save called when `persistDetail=true`, snapshot has expected fields, save failure does not fail round, context build failure skips persistence, `persistDetail=true` without V24 flag does not persist, `exposeDetailApi` flag does not trigger persistence, aggregate MatchResultData still written to fixture, MatchResultData schema still 6 fields
- `V24DetailedMatchData.fromResult(careerId, seasonNumber, round, homeName, awayName, v24Result, playerRatings)` used to build immutable snapshot
- `V24DetailedMatchStoragePort.save(careerId, detail)` called only when: `use-v24-detailed-engine=true` AND `persist-detail=true` AND V24 simulation succeeds
- save failure is best-effort: `catch (Exception)` logs warning and round completes
- context build failure skips persistence and falls back safely to default engine
- `persist-detail=true` without V24 flag does not persist — flags are independent
- `expose-detail-api=true` does not trigger persistence — flags are independent
- empty `playerRatings` list currently passed — per-player rating persistence deferred to future phase
- **No API/controller/frontend changes**
- **No Redis key format change** — `career:{careerId}:match-detail:{matchId}` unchanged
- **MatchFixture.MatchResultData schema unchanged** — 6 fields only
- **CareerSave schema unchanged**
- **SessionPlayer/SessionTeam not mutated**
- **V24DetailedMatchResult/V24MatchEvent unchanged**
- Regression gate: 386 tests, 0 failures; 389 full suite total

---

## V24D5F Completion Record

**Commit:** `0c4d62b` — `feat: persist V24 player ratings in detailed match data`
**Date:** 2026-05-11
**Tests:** 12 new (`V24PlayerRatingsPersistenceTest`), 406 full suite total (now includes V24D3C), 406 regression gate, 0 failures

**V24D5F delivered:**
- `V24PlayerRatingsAssembler` — pure helper that resolves starting XI from `CareerSave.teamStarting11`, converts `SessionPlayer` → `V24PlayerMatchState` via `fromSessionPlayer()`, and delegates to `V24PlayerMatchStatsModel.computeRatings()`
- `LeagueSimulator.persistV24Detail()` now calls `v24PlayerRatingsAssembler.assemblePlayerRatings()` instead of passing `List.of()`
- `V24DetailedMatchData.fromResult(...)` now receives populated `playerRatings` when detail is persisted (not empty list)
- Ratings are derived deterministically from match timeline events (goals, assists, shots, cards, substitutions)
- `playerRatings` populated only when: `use-v24-detailed-engine=true` AND `persist-detail=true` AND V24 simulation succeeds
- `persist-detail=false` does NOT compute or persist ratings — flags are independent
- Save failure is **best-effort**: `catch (Exception)` logs warning and the round still completes
- **No API schema changes**
- **No Redis key format changes**
- **No frontend changes**
- **No SessionPlayer/SessionTeam mutation**
- **No MatchFixture.MatchResultData change**
- **No CareerSave schema change**
- Regression gate: 406 tests, 0 failures

---

## V24D3C Completion Record

**Commit:** `94b4962` — `feat: attach shotCoordinates to V24 match events`
**Date:** 2026-05-11
**Tests:** 8 new (`V24ShotCoordinateAttachmentTest`), 406 full suite total, 406 regression gate, 0 failures

**V24D3C delivered:**
- `V24MatchEvent` now carries optional `shotCoordinate` field (via `withShotCoordinate()` immutable builder pattern)
- Constructor overload preserves backward compatibility — existing code that calls the 9-arg constructor gets null shotCoordinate
- GOAL/SHOT/SHOT_ON_TARGET/BLOCK/MISS events attach `V24ShotCoordinate`; non-shot events (FOUL/YELLOW_CARD/RED_CARD/INJURY/SUBSTITUTION/OFFSIDE/CORNER/CHANCE_CREATED) have null
- `V24MatchEventDto.fromEvent()` maps `shotCoordinate` through `V24ShotCoordinateDto.fromCoordinate()` when non-null
- Generated in `V24DetailedMatchEngine.attemptShot()` before goal resolution using `V24ShotCoordinateGenerator.generate(location, random)` with passed Random for determinism
- Coordinates attached before goal resolution — same seed always produces same coordinates
- No xG formula change — coordinates generated after xG is computed and stored on the event
- No Redis key format change
- No MatchFixture.MatchResultData change
- No CareerSave/SessionPlayer/SessionTeam mutation
- **V24D3C is the final prerequisite for V24D5E5 shot map UI**
- Regression gate: 406 tests, 0 failures

**Status:** V24D5E1 + V24D5E2 + V24D5E3 + V24D5E3B + V24D5E4 COMPLETED — all frontend planning, API client, page, entry point, and player ratings UI complete in separate frontend repo

**Frontend V24D5E commits:**
- `050ab57` — feat: add V24D5E2 match detail API client and TypeScript types
- `0ba2305` — feat: add V24D5E3 read-only match detail page
- `d244097` — feat: add match detail entry point from fixture modal
- `958af1e` — feat: add V24D5E4 player ratings UI

**Frontend V24D5E3 completion summary:**
- `V24MatchDetailPageComponent` — standalone Angular component at `src/app/features/match-detail/pages/v24-match-detail-page.component.ts`
- Route: `/careers/:careerId/matches/:matchId/detail`
- Uses `MatchDetailApiService.getMatchDetail(careerId, matchId)`
- Header with score, round, season, V24 badge
- Summary cards: xG, shots, possession, goals
- Timeline (minute-sorted events), stats comparison table
- 404/null friendly unavailable state, 500/error retry state
- Empty playerRatings state, shot map deferred state
- Validation: `npx tsc --noEmit` OK, `npx ng build` BUILD SUCCESS

**Frontend V24D5E3B completion summary:**
- Dashboard fixture modal (`DashboardFixtureModalComponent`) updated with "📊 Detalle" link
- Link visible only for matches with `status === 'COMPLETED'`
- Link hidden when `careerId` unavailable or match not completed
- Route target: `/careers/:careerId/matches/:matchId/detail`
- Fixture modal does NOT call detail endpoint
- Validation: `npx tsc --noEmit` OK, `npx ng build` BUILD SUCCESS

**V24D5E4 is complete. V24D5E5 shot map UI is pending and now unblocked by V24D3C shot coordinate attachment.**

**Backend unchanged by frontend implementation:** root repo branch `mvp-1-performance-cleanup` has no changes from V24D5E frontend work.

---

## 14. Recommended Next Step

**V24D5E3 — Read-only Match Detail Page — COMPLETED** in separate frontend repo (commit `0ba2305`).

**V24D5E2 frontend API client** is complete (frontend repo commit `050ab57`).

**V24D5E3 delivered:**
- Route: `/careers/:careerId/matches/:matchId/detail` → `V24MatchDetailPageComponent`
- Uses `MatchDetailApiService.getMatchDetail(careerId, matchId)`
- Header with score, round, season, V24 badge
- Summary cards (xG, shots, possession, goals)
- Timeline (minute-sorted events), stats comparison table
- 404/null friendly unavailable state, 500/error retry state
- Empty playerRatings state, shot map deferred state
- Validation: `npx tsc --noEmit` OK, `npx ng build` BUILD SUCCESS

**Backend unchanged:** root repo has no changes from V24D5E frontend work. No API schema changes, no Redis schema changes.

**Next recommended steps (in priority order):**
1. V24D5E5 — frontend shot map UI
2. Frontend QA/polish pass on match detail page
3. Optional backend realism follow-ups: set pieces, stoppage time, goalkeeper save quality, career-state mutation decisions

---

## 15. Required Regression Gate

After any V24 change, the full regression gate:

```
mvn test -Dtest=V24MatchContextFactoryTest,V24DetailedMatchQueryServiceTest,V24DetailedMatchRedisAdapterTest,V24DetailedMatchDataTest,V24PlayerMatchStatsModelTest,V24ShotCoordinateTest,V24PlayerRatingModelTest,V24AssistModelTest,V24FormationParserTest,V24SubstitutionEngineTest,V24InjuryModelTest,V24DisciplineModelTest,V24FatigueModelTest,V24DetailedMatchEngineDeterminismTest,V24TimelineOrderingTest,V24DetailedMatchResultAdapterTest,V24MatchContextValidationTest,V24TimelineConsistencyTest,V24ShotXgModelTest,V24PlayerAttributionTest,LeagueSimulatorTest,MatchResultDataAdapterTest,TeamOverallCalculatorTest,MatchEngineImplStrengthSimulationTest,MatchEngineImplStyleSimulationTest,MatchQualityMetricsTest,V23SimulationQualityGateTest,MatchEngineImplRoleContributionTest,MatchEngineImplEventConsistencyTest,MatchEngineImplDeterminismTest,MatchEngineImplMetricsValidationTest,MatchEngineImplPoissonValidationTest,MatchQualityComputerTest,MatchEngineImplTest,DivisionTest,V24LeagueSimulationPathTest,V24LeagueDetailPersistenceTest,V24EndToEndFlagIntegrationTest,V24PlayerRatingsPersistenceTest,V24ShotCoordinateAttachmentTest
```

**Expected:** 406 tests (regression gate), 0 failures; 406 full suite total (112 V23 + 8 V24A + 22 V24B + 58 V24C + 15 V24D1 + 22 V24D2 + 17 V24D3A + 31 V24D3B + 8 V24D3C + 24 V24D4A + 13 V24D4B + 12 V24D4C + 20 V24D5A + 11 V24D5B + 9 V24D5C + 12 V24D5D + 12 V24D5F).

---

*This document is the authoritative V24D5 production integration planning specification. V24D5A, V24D5B, V24D5C, V24D5D, V24D5F, and V24D3C are complete. V24D5E1/V24D5E2/V24D5E3/V24D5E3B/V24D5E4 are complete in the separate frontend repo. V24D5E5 shot map UI is the next pending frontend phase.*