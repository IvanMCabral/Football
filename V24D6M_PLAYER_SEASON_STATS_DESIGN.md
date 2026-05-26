# V24D6M — Player Season Stats Design

**Status:** V24D6M1 DRAFT — design doc only, no implementation
**Branch:** `mvp-1-performance-cleanup`
**Date:** 2026-05-26
**Based on:** V24D6L complete (`22b650c` L1 / `38c80f8` L2 / `9c2e6ad` L3), full suite 723/0 failures

---

## Purpose

V24 already stores detailed match data and player ratings per match when detail persistence is enabled. The missing piece is season-level aggregation per player. This document designs the player season stats feature — covering source data audit, aggregate model, storage strategy, API shape, UX use cases, feature flag approach, backfill strategy, testing, and phased delivery.

**Constraint:** This is a design doc only. No implementation in M1. No code changes. No flag defaults changed.

---

## 1. Executive Summary

V24 stores rich per-match data:

- **Match detail** — shot locations, xG values, minute-by-minute timeline, event types
- **Player ratings** — per-player match rating, goals, assists, passes, shots, cards, etc.
- **SessionPlayer state** — energy, form, injury/suspension tracking per round

These exist independently per match. A user's career question — "who is my top scorer this season?" or "which player has been most injured?" — cannot be answered without scanning every match.

**The missing piece:** season-level aggregation per player.

Season stats serve multiple stakeholders:

| Use Case | Description |
|----------|-------------|
| UX / UI | Squad stats table, player profile, leaderboards |
| Scouting | Compare player performance across matches |
| Awards | Top scorer, top assister, golden boot |
| Form analysis | Track form trends over time |
| Mutation understanding | Context for injury/discipline/form — "why is this player's form declining?" |

This is especially relevant before broad mutation rollout (Tier 2/3): users need visibility into how injuries, fatigue, and form change over a season, not just per-match.

**Design goal:** additive and backward-compatible. V24 detail storage remains unchanged. Season stats are a read-only aggregation layer.

---

## 2. Goals

- Aggregate player match stats across a full season within a career
- Support: goals, assists, appearances, starts, minutes played, ratings, cards, injuries, form trends
- Allow querying stats by career, season, team, and player
- Avoid mutating existing match detail data
- Keep V24 detail storage additive and backward-compatible
- Support both V24 detail-enabled careers and legacy V23 careers (where possible)
- Provide a foundation for future UX: leaderboards, player cards with season context, award tracking

---

## 3. Non-Goals

- **No production implementation in M1** — this document is the design only
- **No frontend implementation in M1** — UX wireframes and component design are out of scope
- **No immediate Redis migration** — storage strategy is designed but not committed
- **No change to match simulation** — V24 simulation engine is unaffected
- **No change to V24 mutation constants** — injury/fatigue/discipline/form values are unchanged
- **No change to feature flag defaults** — all V24/mutation flags remain `false`

---

## 4. Source Data Audit

### 4.1 V24DetailedMatchData

Stored in Redis under `career:{careerId}:match-detail:{matchId}`. Contains:

| Field | Type | Aggregatable? | Notes |
|-------|------|---------------|-------|
| `matchId` | long | yes | match identity |
| `careerId` | long | yes | career identity |
| `round` | int | yes | round number |
| `homeTeamId` | long | yes | team identity |
| `awayTeamId` | long | yes | team identity |
| `homeScore` / `awayScore` | int | yes | match result |
| `timeline` | `List<V24MatchEvent>` | yes | goals, cards, injuries, substitutions |
| `shots` | `List<V24ShotEvent>` | yes | shot locations, xG, shooter |

**Timeline event types to aggregate:**

| Event Type | Aggregatable As |
|------------|-----------------|
| `GOAL` | goals (and assists via preceding pass event) |
| `ASSIST` | assists |
| `YELLOW_CARD` | yellowCards |
| `RED_CARD` | redCards |
| `INJURY` | injuries (count) |
| `SUBSTITUTION` | minutesPlayed (via replacement) |

### 4.2 V24PlayerMatchRatingDto / playerRatings

Populated via `V24PlayerRatingsAssembler` into match detail. Per-player per-match:

| Field | Aggregatable As |
|-------|-----------------|
| `playerId` | identity |
| `rating` | averageRating, bestRating, worstRating |
| `goals` | goals |
| `assists` | assists |
| `shots` | shots, shotsOnTarget |
| `passes` | keyPasses (approximate) |
| `minutesPlayed` | minutesPlayed, appearances, starts |
| `possession` | not directly aggregated |
| `aerialsWon` | not directly aggregated |

**Key assumption to verify:** `minutesPlayed` field — is it reliably populated for all players who appeared? If a player starts and is substituted at 60min, does the assembler record 60 or 90? This must be confirmed in the source data audit phase (M2).

### 4.3 V24PlayerMatchStatsModel

Output of the per-match stats assembly. Used to produce `V24PlayerMatchRatingDto`. May contain additional fields not currently exposed in the DTO. M2 should audit this model precisely.

### 4.4 SessionPlayer (Redis, per career round state)

Not per-match — updated each round. Fields relevant to season stats:

| Field | Aggregatable As |
|-------|-----------------|
| `injuryRemainingMatches` | derived: injury events + recovery tracking |
| `injured` | current injury flag |
| `suspensionRemainingMatches` | derived: suspension events |
| `suspended` | current suspension flag |
| `yellowCards` | cumulative yellowCards |
| `redCards` | cumulative redCards (reset on season?) |
| `energy` | averageEnergy, lowestEnergy |
| `form` | currentForm, formDeltaSeason |

**Important:** SessionPlayer is a *current state* snapshot, not a historical log. To derive "matches missed due to injury," you need per-round snapshots or a derived event log. This is a design risk — see Section 13.

### 4.5 Summary: Immediately Aggregatable Fields

| Stat | Source | Confidence |
|------|--------|------------|
| Goals | `playerRatings.goals` | HIGH |
| Assists | `playerRatings.assists` | HIGH |
| Appearances | count of `playerRatings` entries | HIGH |
| Minutes | `playerRatings.minutesPlayed` | MEDIUM — needs verification |
| Shots | `playerRatings.shots` | HIGH |
| Shots on target | `playerRatings.shotsOnTarget` | MEDIUM — needs verification in stats model |
| Yellow cards | `playerRatings.yellowCards` | HIGH |
| Red cards | `playerRatings.redCards` | HIGH |
| Average/best/worst rating | `playerRatings.rating` | HIGH |
| Injuries (count) | `timeline.INJURY` events | HIGH |
| Energy | `SessionPlayer.energy` | MEDIUM — current state only, not per-match |
| Form | `SessionPlayer.form` | MEDIUM — current state only |
| Matches missed injured | derived from SessionPlayer snapshots | **LOW** — needs lifecycle tracking |

---

## 5. Proposed Aggregate Model

### 5.1 Core Aggregate DTO: `PlayerSeasonStatsDto`

```java
public class PlayerSeasonStatsDto {
    Long careerId;
    Integer season;
    Long teamId;
    Long playerId;
    String playerName;
    String position;

    // Appearance
    Integer appearances;
    Integer starts;
    Integer minutesPlayed;

    // Attack
    Integer goals;
    Integer assists;
    Integer keyPasses;
    Integer shots;
    Integer shotsOnTarget;

    // Discipline
    Integer yellowCards;
    Integer redCards;

    // Health
    Integer injuries;                    // count of injury events
    Integer matchesMissedInjured;       // rounds unavailable due to injury
    Integer matchesMissedSuspended;    // rounds unavailable due to suspension

    // Performance
    Double averageRating;
    Double bestRating;
    Double worstRating;

    // Form and energy
    Double currentForm;                // SessionPlayer.form at last round
    Double formDeltaSeason;            // form at round N minus form at round 1
    Double averageEnergy;              // mean of SessionPlayer.energy across rounds
    Double lowestEnergy;               // lowest SessionPlayer.energy observed

    // Metadata
    Integer lastUpdatedRound;
}
```

### 5.2 Response DTO: `PlayerSeasonStatsResponse`

```java
public class PlayerSeasonStatsResponse {
    Long careerId;
    Integer season;
    List<PlayerSeasonStatsDto> playerStats;

    // Optional computed summaries
    Integer totalGoals;
    Integer totalAssists;
    Integer totalAppearances;
    Double averageTeamRating;
}
```

### 5.3 Three-Layer Architecture

| Layer | Type | Description |
|-------|------|-------------|
| **Source** | Read-only | V24DetailedMatchData (Redis), SessionPlayer state (Redis) |
| **Aggregate** | Computed | `PlayerSeasonStatsAggregator` — pure function, no side effects |
| **Response** | DTO | `PlayerSeasonStatsDto` / `PlayerSeasonStatsResponse` — API output |

**Persistence note:** The aggregate is computed on demand (Option A) or stored as a snapshot (Option B, see Section 6). The response DTO never mutates source data.

### 5.4 Computed-Only Projection

For MVP (Option A), aggregation is computed on demand:

```
Query → PlayerSeasonStatsAggregator
  → scan all matches for careerId + season
  → for each match: load V24DetailedMatchData from Redis
  → for each player in match: extract ratings + events
  → accumulate into PlayerSeasonStatsDto per player
  → return List<PlayerSeasonStatsDto>
```

No new Redis keys written. No new persistence. Backward compatible.

---

## 6. Storage Strategy Options

### Option A — Compute on Demand from V24DetailedMatchData

| Aspect | Detail |
|--------|--------|
| Persistence | None — computed each query |
| Schema risk | None |
| Backward compatibility | Full — only reads existing detail data |
| Career growth | O(matches × players) — acceptable for careers up to ~500 matches |
| Migration | None needed |
| Leaderboard UI | Slow — must recompute each request |

**Best for:** MVP, low-traffic careers, initial development. Acceptable if match detail exists for all relevant matches.

### Option B — Persist Rolling Aggregate After Each Round

| Aspect | Detail |
|--------|--------|
| Persistence | New Redis key written after each round |
| Schema risk | Medium — new key structure, needs versioning |
| Backward compatibility | Partial — new keys for new rounds only |
| Career growth | O(players × rounds) — bounded |
| Migration | Backfill from existing V24DetailedMatchData |
| Leaderboard UI | Fast — read from persisted aggregate |

**Best for:** Production UX with leaderboards, frequent stat queries, larger careers.

### Option C — Hybrid (Recommended for MVP)

1. **Phase 1 (M3-M4):** Option A — compute on demand. No new persistence.
2. **Phase 2 (M5):** If query performance becomes a problem, add Option B snapshot persistence. Design supports this transition without breaking existing queries.

### 6.1 Recommendation

**Design for Option A, with Option B as a future migration path.** The aggregator in M3 should be a pure function that could later write to a Redis snapshot. Start with the simpler approach.

---

## 7. Redis Key Proposal (For Future Option B)

If/when persistence is added (M5), propose:

### Primary Aggregate Key

```
career:{careerId}:season:{season}:player-stats
```

Value: JSON array of `PlayerSeasonStatsDto` for all players in that season.

### Per-Player Key (alternative, more granular)

```
career:{careerId}:season:{season}:player:{playerId}:stats
```

Value: Single `PlayerSeasonStatsDto`.

### Design Considerations

| Concern | Approach |
|---------|---------|
| **Career isolation** | All keys prefixed `career:{careerId}:` — easy to delete by career |
| **Season isolation** | Key includes `season:{season}` — supports multi-season careers |
| **Delete by career** | `career:{careerId}:*` — single pattern delete |
| **Migration / backfill** | Run aggregator over existing V24DetailedMatchData, write to new keys |
| **Serialization** | JSON via Jackson (same as existing V24DetailedMatchData) |
| **Schema versioning** | Add `version: 1` field to DTO — enables future migrations |
| **TTL** | Optional: no TTL for MVP; consider 90-day TTL for inactive seasons later |

### Backward Compatibility

If Option B keys do not exist for a season, fall back to Option A computation. This means the system degrades gracefully — existing careers without new keys still return correct data via computation.

---

## 8. API Design Proposal

### 8.1 Endpoints

```
GET /api/careers/{careerId}/seasons/{season}/player-stats
GET /api/careers/{careerId}/seasons/{season}/teams/{teamId}/player-stats
GET /api/careers/{careerId}/seasons/{season}/players/{playerId}/stats
```

### 8.2 Response Shape

**GET /api/careers/{careerId}/seasons/{season}/player-stats**

```json
{
  "careerId": 1,
  "season": 1,
  "playerStats": [
    {
      "playerId": 101,
      "playerName": "John Doe",
      "position": "ST",
      "teamId": 1,
      "appearances": 28,
      "starts": 25,
      "minutesPlayed": 2140,
      "goals": 15,
      "assists": 7,
      "keyPasses": 43,
      "shots": 67,
      "shotsOnTarget": 31,
      "yellowCards": 3,
      "redCards": 0,
      "injuries": 2,
      "matchesMissedInjured": 4,
      "matchesMissedSuspended": 0,
      "averageRating": 7.2,
      "bestRating": 8.5,
      "worstRating": 5.8,
      "currentForm": 6.5,
      "formDeltaSeason": -0.5,
      "averageEnergy": 72.3,
      "lowestEnergy": 58.0,
      "lastUpdatedRound": 30
    }
  ],
  "totalGoals": 42,
  "totalAssists": 18,
  "totalAppearances": 28,
  "averageTeamRating": 6.9
}
```

**GET /api/careers/{careerId}/seasons/{season}/teams/{teamId}/player-stats**

Same shape as above, filtered to one team.

**GET /api/careers/{careerId}/seasons/{season}/players/{playerId}/stats**

Returns single `PlayerSeasonStatsDto` object (not wrapped in list).

### 8.3 Query Parameters

| Parameter | Default | Description |
|-----------|---------|-------------|
| `sortBy` | `goals` | Sort field: goals, assists, rating, appearances, etc. |
| `order` | `desc` | `asc` or `desc` |
| `limit` | `null` (all) | Limit results — useful for leaderboards |

### 8.4 Error Responses

| Status | Condition |
|--------|-----------|
| `404 Not Found` | Career or season does not exist |
| `404 Not Found` | No V24 detail data for this career (feature not enabled) |
| `400 Bad Request` | Invalid season number |
| `503 Service Unavailable` | Redis unavailable (detail data inaccessible) |

### 8.5 Feature Flag Gate

If `expose-detail-api=false` (default), these endpoints return `404` with message indicating the feature requires V24 detail to be enabled.

```
GET /api/careers/{careerId}/seasons/{season}/player-stats
```

Response when flags off:

```json
{
  "error": "Player season stats require V24 detail persistence to be enabled",
  "requiredFlags": ["app.simulation.v24.expose-detail-api"],
  "currentFlags": {
    "expose-detail-api": false
  }
}
```

---

## 9. Frontend UX Use Cases

### 9.1 Squad Stats Table

- Sortable columns: goals, assists, rating, appearances, cards, injuries
- Filter by team
- Exportable (future)

### 9.2 Player Profile — Season Stats Tab

- Appearances, starts, minutes
- Goals, assists, shots
- Average/best/worst rating
- Discipline: yellow/red cards, suspensions
- Injury history: injuries this season, matches missed
- Form trend: current form, delta from season start
- Energy: average energy, lowest energy

### 9.3 Top Scorers / Golden Boot

- League-wide leaderboard
- Sort by: goals, assists, rating
- Filter by season

### 9.4 Discipline Table

- Most yellow cards
- Players approaching suspension threshold (4+ yellows)
- Red card count

### 9.5 Injury History Summary

- Players with most matches missed
- Injury count per player
- Combined with form: injured players often show declining form

### 9.6 Form Trend Indicator

- Per-player: `formDeltaSeason` shown as +/- indicator
- Color coded: green (improving), red (declining), gray (stable)
- Useful context for mutation beta users: "this player's form dropped because he was injured for 3 matches"

### 9.7 Energy / Fatigue Trend

- Average energy across season
- Notable troughs (player was overplayed)
- Recovery periods (player sat out a match)

---

## 10. Feature Flag Strategy

### 10.1 Recommended Approach

Season stats are a **read-only aggregation** over existing V24 detail data. No mutation dependency. Therefore:

| Flag | Required? | Reason |
|------|-----------|--------|
| `use-v24-detailed-engine` | No | Stats work with persisted detail regardless of engine |
| `persist-detail` | **Yes** | Without detail persistence, there is no source data to aggregate |
| `expose-detail-api` | No | Stats can be computed even if detail API is not exposed |
| `mutate-career-state` | No | Stats are read-only; mutation has no effect on aggregation |

### 10.2 Minimum Flag Requirements

```
app.simulation.v24.persist-detail: true   # required — source data must exist
app.simulation.v24.expose-detail-api: false  # not required for stats computation
```

### 10.3 Behavior When Flags Off

| Scenario | Behavior |
|----------|----------|
| `persist-detail=false` | No V24DetailedMatchData exists → stats return 404 with helpful message |
| Detail data partially exists | Return available stats; document incomplete data in response |
| Old career, no detail data | Return empty with message that stats require V24 detail enabled |

### 10.4 Optional Dedicated Flag

If a dedicated flag is desired for future granularity:

```
app.simulation.v24.persist-season-stats: false
```

**Pros:** Independent control of stats feature
**Cons:** Another flag to manage; stats are harmless read-only
**Recommendation:** Do not add a dedicated flag at MVP stage. Rely on `persist-detail` as the gate.

---

## 11. Backfill Strategy

### 11.1 For Careers Without V24 Detail Persistence

These careers have no V24DetailedMatchData in Redis. Options:

| Option | Approach | Limitation |
|--------|----------|------------|
| **A: Skip** | Return empty/404 for these careers | Users cannot see older stats |
| **B: Approximate from MatchFixture** | If round results (score, top scorer) are stored elsewhere, approximate | Incomplete — no per-player detail |
| **C: Require re-simulation** | Not recommended — simulation is non-deterministic | Would produce different results |

**Recommendation:** Option A for MVP. Document the limitation. Users who want season stats must play with V24 detail enabled.

### 11.2 For Careers With V24 Detail Persistence

Existing detail data can be aggregated at any time. The aggregator is a pure function — it produces the same result regardless of when it runs (assuming detail data is unchanged).

### 11.3 Backfill Process (When Option B Persistence Is Added)

```
1. For each career with V24DetailedMatchData:
   a. For each season:
      i.  Run PlayerSeasonStatsAggregator over all match details
      ii. Write result to career:{careerId}:season:{season}:player-stats
2. Validate: total goals/assists match expected values
3. Set version field to 1
```

**Note:** Backfill is a one-time migration operation, not an application feature. It should be documented but not implemented as part of M3-M5.

### 11.4 Incomplete Seasons

If a career has partial detail data (e.g., first 10 rounds with detail, earlier rounds without), the aggregator should:

1. Process available matches
2. Set `lastUpdatedRound` to the last round with detail data
3. Include a warning or flag in the response if data is incomplete:
   ```json
   {
     "incomplete": true,
     "missingRounds": [1, 2, 3, 4, 5],
     "message": "Season stats are incomplete — detail data is missing for rounds 1–5"
   }
   ```

---

## 12. Testing Strategy

### 12.1 Unit Tests for PlayerSeasonStatsAggregator

| Test | Description |
|------|-------------|
| `goals_aggregated_correctly` | Sum of goals from multiple matches equals total |
| `assists_aggregated_correctly` | Sum of assists from multiple matches equals total |
| `average_rating_computed_correctly` | Mean of [7.0, 8.0, 6.5] = 7.17 |
| `best_worst_rating_tracked` | Best 8.5, worst 5.8 |
| `yellow_cards_summed` | Sum across matches |
| `red_cards_summed` | Sum across matches |
| `injuries_counted` | Count of INJURY timeline events |
| `appearances_counted` | Count of matches where player has an entry |
| `starts_vs_substitute_distinguished` | If `minutesPlayed > 0` and `isStarting = true` → start |
| `missing_detail_skipped_safely` | If one match has no detail, others still processed |
| `duplicate_match_detail_not_double_counted` | Deduplicate by matchId before aggregating |
| `career_isolation` | Stats for career 1 do not include matches from career 2 |
| `season_isolation` | Season 1 stats do not include season 2 matches |
| `team_isolation` | Team A stats do not include Team B players |
| `empty_season_returns_empty_list` | No matches → empty list, no error |
| `null_rating_field_handled` | Player with null rating does not crash average |
| `player_not_in_match_returns_zero` | Player with no entry in a match shows 0 for all stats |

### 12.2 Integration Tests

| Test | Description |
|------|-------------|
| `api_returns_stats_for_valid_career` | GET endpoint returns 200 + valid JSON |
| `api_returns_404_for_unknown_career` | Unknown careerId → 404 |
| `api_returns_404_when_no_detail_data` | Career without detail → 404 with helpful message |
| `api_filters_by_team` | `GET .../teams/{teamId}/player-stats` returns only that team |
| `api_returns_single_player` | `GET .../players/{playerId}/stats` returns single DTO |
| `api_sorts_correctly` | `?sortBy=goals&order=desc` returns descending goals |
| `api_respects_limit` | `?limit=10` returns 10 results |

### 12.3 Edge Cases

| Case | Expected Behavior |
|------|-------------------|
| Player transferred mid-season | Stats split by team — team field indicates which team |
| Player with 0 appearances | Included with all zeros (appeared in squad but never played) |
| Player transferred in mid-season | Appearances reflect games played for this team only |
| Season with no completed matches | Empty result, no error |
| Detail data partially missing | Incomplete flag set, available stats returned |

---

## 13. Risks and Open Questions

### 13.1 High Priority (Must Resolve Before M2)

**Q1: Are minutesPlayed currently reliable from player ratings/stat model?**

The `V24PlayerRatingsAssembler` produces `V24PlayerMatchRatingDto.minutesPlayed`. If a player starts and is substituted at 60min, does it record 60? If a player comes on at 70min, does it record 20? **This must be verified in the source code during M2.** If minutesPlayed is unreliable, appearances/minutes stats are unreliable.

**Q2: Does V24 detail persist all required event types?**

We assume ASSIST events are persisted in the timeline. We assume INJURY events are persisted. We assume shotsOnTarget is in the stats model. **M2 must audit V24DetailedMatchData schema and V24PlayerMatchStatsModel precisely.**

**Q3: Should injury/missed matches be derived from SessionPlayer state or per-round snapshots?**

SessionPlayer is a **current state** store. `injuryRemainingMatches` tells you how many rounds left, but not a history of which rounds were missed. To compute "matches missed due to injury," you need either:
- A per-round SessionPlayer snapshot log (does not currently exist)
- A derivation from INJURY events + recovery events in timeline

**Recommendation:** Derive from INJURY/RECOVERY events in timeline. This is the most reliable approach without new snapshot infrastructure.

### 13.2 Medium Priority (Design Decision Needed)

**Q4: Is on-demand aggregation fast enough?**

A career with 38 rounds × 20 teams × 25 players = 19,000 player-match entries. Computing on demand reads 760 matches (38 rounds × 20 teams / 2 = 380 matches). Reading 380 Redis keys and aggregating in memory is likely <100ms. **Likely acceptable for MVP.** If careers grow to multiple seasons, consider Option B caching.

**Q5: Do we need stats for non-V24 (V23) matches?**

V23 matches do not produce V24DetailedMatchData. For mixed careers (some rounds with V23, some with V24), stats will only reflect V24 rounds. This should be documented and handled gracefully (incomplete flag).

**Q6: How to handle player transfers later?**

A player transferred mid-season should have stats split by team. The `teamId` field on `PlayerSeasonStatsDto` handles this — a transferred player appears in stats for both teams. However, the aggregator needs to handle this correctly: a player who played 15 matches for Team A and 10 for Team B should have two separate `PlayerSeasonStatsDto` entries, one per team.

**Q7: Does shotsOnTarget exist in the stats model?**

It is referenced in V24PlayerMatchRatingDto and may be assembled in V24PlayerRatingsAssembler. M2 must confirm. If not available, shotsOnTarget can be dropped from MVP scope.

### 13.3 Low Priority (Future Consideration)

**Q8: Should formDeltaSeason be computed from SessionPlayer snapshots or from match-level form events?**

SessionPlayer.form is a current-state value. To compute `formDeltaSeason`, you need either:
- First-round form (form at round 1) stored separately
- A form-change event in the timeline

**Approach:** Store `formAtRound1` as a separate computed value when aggregating season stats. Compare `currentForm` (at last round) against `formAtRound1`.

**Q9: Should averageEnergy use SessionPlayer.energy snapshots or match-level energy data?**

Currently SessionPlayer.energy is updated each round. To compute average energy, you need a snapshot per round. This is the same snapshot problem as injury misses. **For MVP: compute averageEnergy from SessionPlayer snapshots if available; otherwise drop from MVP scope.**

---

## 14. Proposed Phases

### M1 — This Document (Design)

**Deliverable:** `V24D6M_PLAYER_SEASON_STATS_DESIGN.md` — this document
**Non-deliverables:** No code, no tests, no implementation

### M2 — Source Data Audit (Code-Level Mapping)

**Deliverables:**

- Precise audit of `V24PlayerRatingsAssembler` output fields
- Confirm `minutesPlayed` reliability
- Confirm `shotsOnTarget` availability
- Confirm ASSIST event persistence in timeline
- Confirm INJURY event persistence in timeline
- Confirm which fields exist in `V24PlayerMatchStatsModel` vs `V24PlayerMatchRatingDto`
- Document exact Redis key structure for V24DetailedMatchData

**Non-deliverables:** No implementation, no tests

### M3 — Pure Aggregator Service + Unit Tests

**Deliverables:**

- `PlayerSeasonStatsAggregator` — pure function, no side effects
- Unit tests for all aggregation cases (Section 12.1)
- `PlayerSeasonStatsDto` and `PlayerSeasonStatsResponse` DTOs
- No API endpoints yet
- No new Redis keys

### M4 — Query Service + API DTOs + Integration Tests

**Deliverables:**

- `PlayerSeasonStatsQueryService` — orchestrates aggregation
- API controller with endpoints (Section 8)
- Integration tests (Section 12.2)
- Feature flag check in API layer
- Error responses per Section 8.4

### M5 — Optional Redis Snapshot Persistence (Future)

**Deliverables (if needed):**

- New Redis keys per Section 7
- Write path: snapshot after each round
- Read path: check snapshot first, fall back to computation
- Backfill process documented
- Migration tests

**Non-deliverable:** Do not implement M5 unless M4 query performance is unacceptable.

### M6 — Frontend Stats UI (Separate Repo)

**Deliverables (in frontend repo):**

- Squad stats table component
- Player profile season stats tab
- Leaderboard components
- Discipline table
- Form/energy trend indicators

**Coordination:** Frontend and backend versions must be compatible. API contract documented in M4.

### M7 — Docs/Status Update

**Deliverables:**

- Update V23_SIMULATION_ENGINE_STATUS.md with M1-M6 completion
- Update V23_ENGINE_EVOLUTION_ROADMAP.md
- Update V24D5_PRODUCTION_INTEGRATION_PLAN.md
- Mark V24D6M complete

---

## 15. Phase Summary

| Phase | Type | Status | Key Deliverable |
|-------|------|--------|-----------------|
| M1 | Design | **IN PROGRESS** | This document |
| M2 | Source audit | Pending | Code-level field mapping |
| M3 | Implementation | Pending | Aggregator + unit tests |
| M4 | Implementation | Pending | API + integration tests |
| M5 | Implementation | Future | Redis snapshot persistence |
| M6 | Frontend | Future | Stats UI components |
| M7 | Docs | Pending | Status/roadmap updates |

---

## 16. Recommended Next Phase: V24D6M2

### V24D6M2 — Source Data Audit

Before any code is written, M2 must answer the open questions in Section 13:

1. Audit `V24PlayerRatingsAssembler` — confirm exact output fields
2. Verify `minutesPlayed` reliability — does sub-in/out record correctly?
3. Verify `shotsOnTarget` — does it exist in the stats model?
4. Verify ASSIST and INJURY events in timeline
5. Confirm Redis key structure for V24DetailedMatchData
6. Map all source fields to target PlayerSeasonStatsDto fields

**Deliverable:** Updated design doc with confirmed field mappings, or a separate audit note with findings.

**Scope:** Read-only investigation. No code changes. No tests. No implementation.

---

*V24D6M1 design doc complete. Awaiting user signal to proceed with M2 source data audit or next phase.*
