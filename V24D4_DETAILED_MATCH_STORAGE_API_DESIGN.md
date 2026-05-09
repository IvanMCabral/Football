# V24D4 — Detailed Match Storage/API Design

**Status:** PLANNING ONLY — No code implementation until this document is reviewed and approved
**Branch:** `mvp-1-performance-cleanup`
**Latest implementation commit:** `09b89b2` (feat: add V24 player rating model — V24D3B)
**Tests:** 285 total, 0 failures (regression gate unchanged — V24D4 is docs only)
**Created:** 2026-05-08

---

## 1. Executive Summary

V24 detailed match data (timeline, xG, player ratings, shot coordinates) should **NOT** be embedded into `MatchFixture.MatchResultData`. That object has 6 fields and is stable production包袱. Stuffing V24 detail into it risks bloating fixtures, breaking backward compatibility, and creating migration headaches.

**Recommendation:** Keep `MatchResultData` stable. Add a separate detailed-match object stored in Redis, keyed by `matchId`. No API/frontend implementation in this phase.

**Key decisions deferred to this document:**
1. Separate Redis object vs. embedded in career save
2. DTO design for timeline events and player ratings
3. API endpoint shape
4. Feature flag strategy
5. Migration phases

---

## 2. Current State Audit

### Production persistence (stable — do not break)
```
MatchFixture.MatchResultData:
  homeGoals, awayGoals, homePossession, awayPossession, homeShots, awayShots (6 fields)
```

### V24DetailedMatchResult (13 fields, not persisted)
```
matchId, homeTeamId, awayTeamId, homeGoals, awayGoals,
homeXg, awayXg, homeShots, awayShots, homePossession, awayPossession,
timeline (List<V24MatchEvent>), summary
```

### V24MatchEvent (9 fields, no shotCoordinate)
```
minute, type, playerId, playerName, relatedPlayerId, relatedPlayerName, xg, description, teamId
// V24D3A: shotCoordinate helper exists but is NOT attached to events
// V24D3B: V24PlayerRatingModel exists but V24DetailedMatchResult has NO playerRatings field
```

### V24DetailedMatchResultAdapter
Maps V24DetailedMatchResult → MatchResultData (discards all detail, keeps 6 fields only).

### Current regression gate: 285 tests, 0 failures — unchanged by V24D4 (docs-only).

---

## 3. Storage Options

### Option A — Discard details (status quo)
`V24DetailedMatchResultAdapter` continues to map only 6 fields. All V24 detail is lost after simulation.
**Pros:** Safest, no schema/API/frontend changes.
**Cons:** V24 value is research-only, no persistence path.
**Decision:** V24 detail should be preserved.

### Option B — Embed into MatchResultData
Add xG, timeline, ratings, events to existing nested result.
**Pros:** Single object, no extra reads.
**Cons:** Breaks existing fixtures, migration complex, bloats career saves.
**Decision:** NO. MatchResultData must stay stable.

### Option C — Separate Redis object (RECOMMENDED)
New `V24DetailedMatchData` snapshot stored at `career:{careerId}:match-detail:{matchId}`.
**Pros:** Backward-compatible, isolated, easy cleanup, no career save bloat.
**Cons:** Extra read path if detail is requested.
**Decision:** YES — preferred storage model.

### Option D — Separate collection/table (future only)
**Decision:** Only if app moves beyond Redis-key-per-match model.

---

## 4. Recommended Storage Model

```
V24DetailedMatchData (snapshot/DTO, not internal domain object)
├── matchId: String
├── careerId: String
├── seasonNumber: Integer
├── round: Integer
├── homeTeamId: String
├── awayTeamId: String
├── homeTeamName: String
├── awayTeamName: String
├── homeGoals: Integer
├── awayGoals: Integer
├── homeXg: Double
├── awayXg: Double
├── homeShots: Integer
├── awayShots: Integer
├── homePossession: Integer
├── awayPossession: Integer
├── timeline: List<V24MatchEventDto>
├── playerRatings: List<PlayerMatchRatingDto>
├── createdAt: Instant
├── engineVersion: String ("V24")
└── schemaVersion: Integer (1)
```

**Key pattern:** `career:{careerId}:match-detail:{matchId}`

**Design principles:**
- DTO/snapshot objects only — not internal domain objects (V24MatchEvent, V24DetailedMatchResult)
- Immutable once written (no partial updates)
- Idempotent writes (same matchId = overwrite, not merge)
- schemaVersion for future migrations

---

## 5. Event DTO Design

```java
V24MatchEventDto:
  minute: int
  type: String (V24MatchEventType.name())
  teamId: String
  playerId: String
  playerName: String
  relatedPlayerId: String (nullable)
  relatedPlayerName: String (nullable)
  xg: double
  description: String
  shotCoordinate: V24ShotCoordinateDto (nullable)
```

```java
V24ShotCoordinateDto:
  x: double        // 0–100
  y: double        // 0–100
  location: String // V24ShotLocation.name()
  distanceToGoal: double
  angleToGoal: double
  insideBox: boolean
```

**Note:** V24D3C (optional schema enrichment) is required before `shotCoordinate` can be populated — currently the helper exists but is not attached to events. Without V24D3C, `shotCoordinate` stays null in storage.

---

## 6. Player Rating DTO Design

```java
PlayerMatchRatingDto:
  playerId: String
  playerName: String
  teamId: String
  position: String
  rating: double           // computed by V24PlayerRatingModel
  goals: int
  assists: int
  keyPasses: int
  shots: int
  yellowCards: int
  redCards: int
  injuries: int
  substitutedIn: int
  substitutedOut: int
```

**Note:** V24PlayerRatingModel currently computes only `rating` from timeline. Full stat bundle (goals, assists, etc.) requires a separate `V24PlayerMatchStatsModel` helper — not yet implemented. Until then, `playerRatings` in storage will have `rating` populated but other fields may be zero or derived separately in a future step.

---

## 7. API Design Options

### Option API-A — GET /api/careers/{careerId}/matches/{matchId}/detail (RECOMMENDED)
- Returns `V24DetailedMatchData` if stored.
- Returns 404 or empty body if no detail exists.
- Handles missing detail gracefully (backward-compatible).
- **Pros:** Simple, explicit, backward-compatible.
- **Cons:** Extra request for detail.

### Option API-B — Include detail in fixture response
- **Decision:** NO. Timeline-heavy data bloats league screen. Keep summary and detail separate.

### Option API-C — Summary endpoint + timeline endpoint
- Future optimization if payload becomes large.
- **Decision:** Defer to future.

### API-A Implementation Notes:
- Feature-gated (`app.simulation.v24.expose-detail-api=false` default).
- Returns 404 if `V24DetailedMatchData` not found for matchId.
- Response should be lightweight JSON — no binary fields.

---

## 8. Frontend Consumption (Design Only)

| Screen/Tab | Data Needed | Notes |
|------------|-------------|-------|
| Match detail page | V24DetailedMatchData | Separate route/tab |
| Timeline tab | timeline (List<V24MatchEventDto>) | chronological |
| Stats/xG tab | homeXg, awayXg, homeShots, awayShots | simple |
| Shot map tab | timeline[].shotCoordinate | requires V24D3C |
| Player ratings tab | playerRatings (List<PlayerMatchRatingDto>) | sortable |
| Cards/injuries tab | timeline filtered by CARD/INJURY types | |

**Note:** Frontend work is separate. Design API first without UI implementation commitment.

---

## 9. Backward Compatibility

| Scenario | Behavior |
|----------|----------|
| Existing career without V24 detail | Loads normally — detail endpoint returns 404 |
| Existing fixture without detail | No change — MatchResultData unchanged |
| Null/empty timeline in detail | API returns empty list, not error |
| Old detail without schemaVersion | Handle with migration or skip |
| MatchResultData consumers | Unaffected — 6 fields unchanged |

**Critical:** `MatchFixture.MatchResultData` schema must never change. V24 detail is additive and isolated.

---

## 10. Consistency and Lifecycle

| Question | Answer |
|----------|--------|
| When to write detail snapshot? | Same save point as fixture result if V24 engine used |
| What if fixture saves but detail fails? | Log error, do not fail match — detail is additive |
| Idempotency? | Yes — same matchId overwrites (not merge) |
| Re-simulation behavior? | Overwrite existing detail with fresh snapshot |
| Cleanup when career deleted? | Redis key deletion via career deletion cascade |
| Immutable after match? | Yes — snapshot is read-only once written |
| Transaction with fixture? | Preferred but not required — detail is best-effort |

---

## 11. Redis Key Strategy

**Recommended:** `career:{careerId}:match-detail:{matchId}`

| Approach | Pros | Cons |
|----------|------|------|
| Individual key per match | Easy TTL/cleanup, no large maps | Many keys if many matches |
| Map under career (`match-details` hash) | Fewer keys, atomic reads | Harder to TTL per-match, large hash |
| Embed in CareerSave | No extra reads | Bloats career save, slow loads |

**Decision:** Individual keys per match. Supports per-match TTL if needed later. Cleanup is straightforward on career deletion.

---

## 12. Implementation Phases

### Phase 1 — DTO/Storage Design (V24D4A) — NO WIRING
- Add `V24MatchEventDto`, `V24ShotCoordinateDto`, `PlayerMatchRatingDto`, `V24DetailedMatchData`
- Add `V24DetailedMatchStoragePort` interface (no implementation)
- Add `V24PlayerMatchStatsModel` helper to derive stat bundle (goals, assists, etc.) from timeline
- No Redis, no API, no feature flag wiring
- Tests: new unit tests for DTOs/helpers only
- Risk: LOW — pure design classes

### Phase 2 — Redis Adapter Behind Feature Flag (V24D4B)
- Implement `V24DetailedMatchRedisAdapter` implements `V24DetailedMatchStoragePort`
- Add feature flags: `app.simulation.v24.persist-detail=false`
- Store detail only when V24 engine enabled AND flag is true
- Tests: adapter tests with embedded Redis

### Phase 3 — Query Endpoint (V24D4C)
- Add GET `/api/careers/{careerId}/matches/{matchId}/detail`
- Feature-gated: `app.simulation.v24.expose-detail-api=false`
- Returns detail if present, 404 if not

### Phase 4 — Frontend Match Detail Page
- Separate from this document — requires frontend approval

### Phase 5 — V24 Production Simulation Flag (V24D5)
- `app.simulation.league.use-v24-detailed-engine`
- Only after Phase 1–4 validated
- Separate from persistence flag

---

## 13. Feature Flags

```
app.simulation.league.use-v24-detailed-engine=false   # Enable V24 engine in league sim
app.simulation.v24.persist-detail=false                # Store V24DetailedMatchData in Redis
app.simulation.v24.expose-detail-api=false             # Expose detail via REST endpoint
```

**Independence:** All three flags are independent:
- `persist-detail=true` without `expose-detail-api` = internal-only storage
- `expose-detail-api=true` without `persist-detail` = 404s only
- `use-v24-detailed-engine=true` does NOT automatically enable persistence

---

## 14. Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Redis payload size (large timeline) | MEDIUM | MEDIUM | Phase 1 DTO design limits fields; pagination in future if needed |
| Schema evolution (future V25) | LOW | LOW | schemaVersion field; additive changes only |
| API payload size | MEDIUM | MEDIUM | Feature flag; timeline endpoint separate from summary |
| Fixture/detail inconsistency | LOW | LOW | Detail is best-effort; fixture is source of truth |
| Career deletion cleanup | LOW | LOW | Redis key deletion cascades from career delete |
| Accidental production activation | LOW | HIGH | Flags default false; manual opt-in required |
| Frontend unstable schema | MEDIUM | MEDIUM | DTO stability required before frontend work |

---

## 15. Recommended Path

**V24D3C vs V24D4A decision:**

| Factor | V24D3C (Schema Enrichment First) | V24D4A (DTO/Storage Design First) |
|--------|----------------------------------|------------------------------------|
| Shot map in storage | Requires V24D3C to attach coordinates to events | Coordinates null until V24D3C |
| Player ratings in storage | Works now (rating only, no stat bundle) | Requires V24PlayerMatchStatsModel |
| Production wiring risk | MEDIUM (schema changes) | LOW (design only, no wiring) |
| Depends on V24D3C? | Independent | Independent |
| Value delivered | Event schema change | Design clarity, no code risk |

**Recommendation:** V24D4A — DTO/storage design classes only, no Redis wiring. This advances the integration design without production risk. V24D3C can run in parallel or later if shot map / stat bundle are needed before storage design is validated.

**V24D4A is docs/classes-only:**
- Add DTOs: V24MatchEventDto, V24ShotCoordinateDto, PlayerMatchRatingDto, V24DetailedMatchData
- Add storage port interface: V24DetailedMatchStoragePort
- Add stats helper: V24PlayerMatchStatsModel (derives stat bundle from timeline)
- No Redis implementation, no API, no feature flag wiring
- 285 tests remain regression gate

---

## 16. Non-Goals (V24D4 is Documentation Only)

- No production wiring
- No Redis implementation
- No API implementation
- No frontend implementation
- No LeagueSimulator changes
- No MatchEngineImpl changes
- No V23 source modifications
- No V24MatchEvent schema change (V24D3C optional)
- No V24DetailedMatchResult schema change
- No SessionPlayer mutation
- No SessionTeam mutation
- No feature flag wiring in this phase

---

## 17. Required Regression Command

After any V24D4 code changes, the full regression gate:

```
mvn test -Dtest=V24ShotCoordinateTest,V24PlayerRatingModelTest,V24AssistModelTest,V24FormationParserTest,V24SubstitutionEngineTest,V24InjuryModelTest,V24DisciplineModelTest,V24FatigueModelTest,V24DetailedMatchEngineDeterminismTest,V24TimelineOrderingTest,V24DetailedMatchResultAdapterTest,V24MatchContextValidationTest,V24TimelineConsistencyTest,V24ShotXgModelTest,V24PlayerAttributionTest,LeagueSimulatorTest,MatchResultDataAdapterTest,TeamOverallCalculatorTest,MatchEngineImplStrengthSimulationTest,MatchEngineImplStyleSimulationTest,MatchQualityMetricsTest,V23SimulationQualityGateTest,MatchEngineImplRoleContributionTest,MatchEngineImplEventConsistencyTest,MatchEngineImplDeterminismTest,MatchEngineImplMetricsValidationTest,MatchEngineImplPoissonValidationTest,MatchQualityComputerTest,MatchEngineImplTest,DivisionTest
```

**Expected:** 285 tests, 0 failures (V24D4 adds tests only for new DTOs/helpers, regression gate unchanged until wiring phase).

---

*This document is the authoritative V24D4 storage and API design specification. No implementation begins until this document is reviewed and a specific V24D4 phase is approved.*