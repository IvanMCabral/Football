# V24D6M — Player Season Stats Design

**Status:** V24D6M1-M4 COMPLETE — player season stats design, source audit, pure aggregator, read-only query service/API complete. M5 docs/status pending.
**Branch:** `mvp-1-performance-cleanup`
**Date:** 2026-05-26
**Based on:** V24D6L complete (`22b650c` L1 / `38c80f8` L2 / `9c2e6ad` L3), full suite 760 tests

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

## 4. Source Data Audit (M2 Findings)

All findings below are from direct source code inspection. No implementation changes made.

### 4.1 V24PlayerRatingsAssembler

**File:** `src/main/java/com/footballmanager/application/service/simulation/v24/V24PlayerRatingsAssembler.java`

**Key finding — CRITICAL GAP: Only starting XI players are included.**

```java
private List<V24PlayerMatchState> resolveStartingPlayers(CareerSave career, String teamId, String teamIdForState) {
    List<String> starterIds = career.getTeamStarting11().get(teamId);
    // ...
    for (String playerId : starterIds) {
        SessionPlayer sp = career.getPlayerManager().getSessionPlayer(playerId);
        if (sp != null) {
            players.add(V24PlayerMatchState.fromSessionPlayer(sp, teamIdForState));
        }
    }
    return players;
}
```

- Only `career.getTeamStarting11()` entries are processed — i.e., the starting XI only
- **Substitute players who come on during the match are NOT included in playerRatings**
- A substitute who plays 30 minutes will NOT have a `playerRatings` entry
- This means `appearances` in season stats will undercount — bench players who played will appear as 0 appearances

**Consequence for M3:** `appearances` = count of starting XI matches where the player started and was not substituted out at minute 0. This is an approximation. A player who came on as sub in 10 matches will show 0 appearances. See Section 9 for resolution.

### 4.2 V24PlayerMatchStatsModel

**File:** `src/main/java/com/footballmanager/application/service/simulation/v24/V24PlayerMatchStatsModel.java`

**Computes the following per player per match:**

```java
int goals = 0;
int assists = 0;
int keyPasses = 0;
int shots = 0;
int yellowCards = 0;
int redCards = 0;
int injuries = 0;
int fouls = 0;
boolean substitutedIn = false;
boolean substitutedOut = false;
```

**Event type handling in timeline scan:**

| Event Type (playerId match) | Tracked As |
|----------------------------|-----------|
| `GOAL` | goals++ |
| `SHOT` | shots++ |
| `YELLOW_CARD` | yellowCards++ |
| `RED_CARD` | redCards++ |
| `INJURY` | injuries++ |
| `FOUL` | fouls++ |
| `SUBSTITUTION` | substitutedOut = true |

**Event type handling for relatedPlayerId (assists/key passes):**

| Event Type (relatedPlayerId match) | Tracked As |
|------------------------------------|-----------|
| `GOAL` | assists++ |
| `SHOT` | keyPasses++ |
| `SUBSTITUTION` | substitutedIn = true |

**MISSING from V24PlayerMatchStatsModel (not computed):**
- `shotsOnTarget` — `SHOT_ON_TARGET` event type exists in `V24MatchEventType` enum but is NOT handled in stats model
- `minutesPlayed` — not computed, no minute tracking per event
- `started` — not explicit; derived from `!substitutedIn`

### 4.3 V24PlayerMatchRatingDto

**File:** `src/main/java/com/footballmanager/application/service/simulation/v24/V24PlayerMatchRatingDto.java`

**Persisted fields (all in `playerRatings` list in V24DetailedMatchData):**

```
playerId, playerName, teamId, position, rating,
goals, assists, keyPasses, shots,
yellowCards, redCards, injuries, fouls,
substitutedIn, substitutedOut
```

**Confirmed present:** goals, assists, keyPasses, shots, yellowCards, redCards, injuries, fouls, substitutedIn, substitutedOut, rating, playerId, playerName, teamId, position

**Confirmed MISSING:** `shotsOnTarget`, `minutesPlayed`, `started` flag

**Approximation for appearances:** `appearances = startingMatches + substituteMatches`
- startingMatches: count of matches where `!substitutedIn`
- substituteMatches: count of matches where `substitutedIn == true`
- A substitute who never entered (on bench, unused) will NOT appear in playerRatings at all

### 4.4 V24DetailedMatchData

**File:** `src/main/java/com/footballmanager/application/service/simulation/v24/V24DetailedMatchData.java`

**Redis key:** `career:{careerId}:match-detail:{matchId}`

**Persisted fields relevant to season aggregation:**

| Field | Type | Aggregatable? | Notes |
|-------|------|---------------|-------|
| `matchId` | String | yes | match identity |
| `careerId` | String | yes | career identity |
| `seasonNumber` | Integer | **yes** | **season filtering available** |
| `round` | Integer | yes | round number |
| `homeTeamId` | String | yes | team identity |
| `awayTeamId` | String | yes | team identity |
| `homeGoals` / `awayGoals` | int | yes | match result |
| `homeXg` / `awayXg` | double | yes | xG |
| `homeShots` / `awayShots` | int | yes | team-level shots |
| `timeline` | `List<V24MatchEventDto>` | yes | all events including INJURY, YELLOW_CARD, etc. |
| `playerRatings` | `List<V24PlayerMatchRatingDto>` | **yes** | per-player stats, goals, cards, etc. |

**Confirmed:** `seasonNumber` IS available. Season aggregation is possible.

### 4.5 V24MatchEventType Enum

**File:** `src/main/java/com/footballmanager/application/service/simulation/v24/V24MatchEventType.java`

```java
public enum V24MatchEventType {
    GOAL, SHOT, SHOT_ON_TARGET, SAVE, MISS, BLOCK,
    CHANCE_CREATED, FOUL, YELLOW_CARD, RED_CARD,
    INJURY, CORNER, OFFSIDE, SUBSTITUTION
}
```

**SHIFT_ON_TARGET exists as event type but is NOT processed in V24PlayerMatchStatsModel.** Only SHOT events are counted as shots.

### 4.6 V24MatchEvent / V24MatchEventDto

**Files:**
- `src/main/java/com/footballmanager/application/service/simulation/v24/V24MatchEvent.java`
- `src/main/java/com/footballmanager/application/service/simulation/v24/V24MatchEventDto.java`

**Fields:**
```
minute, type, teamId, playerId, playerName,
relatedPlayerId, relatedPlayerName, xg, description, shotCoordinate
```

**Key for aggregation:**
- ASSIST: `relatedPlayerId` on `GOAL` events — assist attribution works
- INJURY: `INJURY` events have `playerId` of injured player — count works
- YELLOW/RED CARD: `playerId` on card events — count works
- SUBSTITUTION: `playerId` = player coming OFF, `relatedPlayerId` = player coming ON — both substitutions tracked via `substitutedOut`/`substitutedIn`

### 4.7 V24DetailedMatchRedisAdapter

**File:** `src/main/java/com/footballmanager/infrastructure/persistence/redis/V24DetailedMatchRedisAdapter.java`

**Key methods available:**

```java
void save(String careerId, V24DetailedMatchData detail);
Optional<V24DetailedMatchData> findByMatchId(String careerId, String matchId);
void deleteByCareerId(String careerId);
```

**Key pattern:** `career:{careerId}:match-detail:{matchId}`

**CRITICAL GAP for season aggregation:** No `findByCareerId(careerId)` or `findByCareerIdAndSeason(careerId, seasonNumber)` method exists.

**Workaround options for M3:**
1. Use `redisTemplate.keys("career:" + careerId + ":match-detail:*")` — scans all keys for a career (expensive but functional)
2. The `V24DetailedMatchData.seasonNumber` field exists — filter in-memory after fetching

**Estimated cost:** For a career with 38 rounds × 20 teams / 2 = 380 matches, a keys scan + 380 individual `get` calls is ~200-500ms. Acceptable for MVP if limited to a few concurrent users.

### 4.8 SessionPlayer State Fields (for context)

**Not used directly in season stats aggregation** — season stats are derived from V24DetailedMatchData only (per-match, not per-round state). SessionPlayer fields like `injuryRemainingMatches`, `suspensionRemainingMatches`, `energy`, `form` are CURRENT STATE at the time of the match, not historical. They are NOT part of the season stats MVP computation.

---

## 5. Confirmed Field Mapping (M2 Audit)

After source code audit, the following PlayerSeasonStatsDto fields are confirmed available or unavailable:

| PlayerSeasonStatsDto Field | Source | Confidence | Implementation Notes |
|----------------------------|--------|------------|----------------------|
| `careerId` | V24DetailedMatchData.careerId | HIGH | Direct from detail data |
| `season` | V24DetailedMatchData.seasonNumber | HIGH | Direct from detail data |
| `teamId` | V24PlayerMatchRatingDto.teamId | HIGH | Direct from playerRatings |
| `playerId` | V24PlayerMatchRatingDto.playerId | HIGH | Direct from playerRatings |
| `playerName` | V24PlayerMatchRatingDto.playerName | HIGH | Direct from playerRatings |
| `position` | V24PlayerMatchRatingDto.position | HIGH | Direct from playerRatings |
| `goals` | V24PlayerMatchRatingDto.goals | HIGH | Direct sum |
| `assists` | V24PlayerMatchRatingDto.assists | HIGH | Direct sum |
| `keyPasses` | V24PlayerMatchRatingDto.keyPasses | HIGH | Direct sum |
| `shots` | V24PlayerMatchRatingDto.shots | HIGH | Direct sum |
| `shotsOnTarget` | — | **NOT AVAILABLE** | SHOT_ON_TARGET event exists but is not counted in stats model |
| `yellowCards` | V24PlayerMatchRatingDto.yellowCards | HIGH | Direct sum |
| `redCards` | V24PlayerMatchRatingDto.redCards | HIGH | Direct sum |
| `injuries` | V24PlayerMatchRatingDto.injuries | HIGH | Direct sum (INJURY event count) |
| `fouls` | V24PlayerMatchRatingDto.fouls | HIGH | Direct sum (available in DTO) |
| `substitutedIn` | V24PlayerMatchRatingDto.substitutedIn | HIGH | Available in DTO |
| `substitutedOut` | V24PlayerMatchRatingDto.substitutedOut | HIGH | Available in DTO |
| `rating` | V24PlayerMatchRatingDto.rating | HIGH | Per-match rating available |
| `averageRating` | derived from rating | HIGH | Mean of per-match ratings |
| `bestRating` | derived from rating | HIGH | Max of per-match ratings |
| `worstRating` | derived from rating | HIGH | Min of per-match ratings |
| `appearances` | derived | **APPROXIMATE** | See 5.1 resolution |
| `starts` | derived | **APPROXIMATE** | starts = !substitutedIn per match |
| `minutesPlayed` | — | **NOT AVAILABLE** | No per-minute tracking in DTO or stats model |
| `currentForm` | — | **NOT AVAILABLE** | SessionPlayer.form is current state only |
| `formDeltaSeason` | — | **NOT AVAILABLE** | No form-at-round-1 baseline stored |
| `averageEnergy` | — | **NOT AVAILABLE** | SessionPlayer.energy is current state only |
| `lowestEnergy` | — | **NOT AVAILABLE** | No per-round energy snapshot |
| `matchesMissedInjured` | derived | **PARTIAL** | See Q3 in Section 13 |
| `matchesMissedSuspended` | derived | **PARTIAL** | See Q3 in Section 13 |

### 5.1 Appearances Approximation (Critical Gap — Resolved for MVP)

V24PlayerRatingsAssembler only processes starting XI. Bench players who came on as substitutes are NOT included in playerRatings.

**M3 MVP resolution:**
- `startingAppearances` = count of matches where `!substitutedIn` (player started)
- `substituteAppearances` = count of matches where `substitutedIn == true`
- `totalAppearances = startingAppearances + substituteAppearances`

**Limitation:** An unused substitute (on bench, never entered) will NOT appear in playerRatings at all. This undercounts appearances for bench players who did play. Document in API response.

### 5.2 Matches Missed Derivation (Partial — Q3 Resolved)

- **Suspension:** Derive from INJURY/RED_CARD events + knowledge that 5 yellows = 1-match suspension. Count SUSPENSION events per player from timeline.
- **Injury:** Count INJURY events per player per round. Each INJURY event marks at least 1 round missed.
- **Limitation:** Precision requires SessionPlayer per-round snapshot history. MVP uses timeline event count as proxy.

---

## 6. Proposed Aggregate Model

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

## 7. Storage Strategy Options

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

## 8. Redis Key Proposal (For Future Option B)

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

## 9. API Design Proposal

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

## 10. Frontend UX Use Cases

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

## 11. Feature Flag Strategy

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

## 12. Backfill Strategy

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

## 13. Testing Strategy

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

## 14. Risks and Open Questions (M2 Answers)

### 13.1 High Priority — Answered (RESOLVED by M2 audit)

**Q1: Are minutesPlayed currently reliable from player ratings/stat model?**

**ANSWER: NOT AVAILABLE — no minutes tracking exists.**

V24PlayerMatchRatingDto does not have a `minutesPlayed` field. Neither V24PlayerRatingsAssembler nor V24PlayerMatchStatsModel computes or stores minutes played per player. The stats model only tracks `substitutedIn` and `substitutedOut` as booleans.

**Consequence for M3:** Drop `minutesPlayed` from MVP scope. Use `substitutedIn`/`substitutedOut` to derive approximate appearances instead.

---

**Q2: Does V24 detail persist ASSIST and INJURY events in the timeline?**

**ANSWER: YES (ASSIST), YES (INJURY) — both confirmed present.**

- ASSIST: Derived from `relatedPlayerId` on `GOAL` events in the timeline. `statsModel.buildRatingDto()` checks `if (pid.equals(event.relatedPlayerId()))` for `GOAL` type and increments `assists`. Confirmed working.
- INJURY: `INJURY` event type exists in `V24MatchEventType` enum. Stats model increments `injuries` counter when `event.type() == INJURY`. Confirmed present.
- YELLOW_CARD / RED_CARD: Both confirmed present and counted.
- SUBSTITUTION: Both `substitutedIn` (via relatedPlayerId) and `substitutedOut` (via playerId) confirmed tracked.

---

**Q3: Can matches missed due to injury/suspension be derived without SessionPlayer snapshots?**

**ANSWER: PARTIAL — timeline event counting as proxy, with limitations.**

- INJURY events are persisted in the timeline. Each INJURY event corresponds to a round where the player was injured.
- `matchesMissedInjured` can be approximated as: count of distinct rounds where the player has an INJURY event in the timeline.
- Limitation: If a player is injured across multiple rounds from a single injury event (e.g., 3-match injury), the current model would count it as 1 event, not 3 missed matches. Full precision requires SessionPlayer per-round snapshots.
- `matchesMissedSuspended`: Derived from accumulated yellow cards. 5 yellows = 1-match suspension. The suspension event itself may or may not be in the timeline — this needs verification during M3 implementation.

**M3 MVP approach:** Use INJURY event count as `injuries` metric. Approximate `matchesMissedInjured` as `injuries × estimated_avg_injury_duration`. Document the approximation.

---

**Q7: Does shotsOnTarget exist in the stats model?**

**ANSWER: NO — SHOT_ON_TARGET event exists but is NOT processed.**

`V24MatchEventType.SHOT_ON_TARGET` exists in the enum. However, `V24PlayerMatchStatsModel.buildRatingDto()` does NOT handle `SHOT_ON_TARGET` events — it only handles `SHOT` events. `shotsOnTarget` is not computed and not stored in `V24PlayerMatchRatingDto`.

**Consequence for M3:** Drop `shotsOnTarget` from MVP scope. Add to future phase if SHOT_ON_TARGET counting is implemented in the stats model.

---

### 13.2 Medium Priority — Confirmed and Updated

**Q4: Is on-demand aggregation fast enough?**

**CONFIRMED YES for MVP.** 380 Redis keys per season, each ~2-5KB, is ~2MB total data. ~200-500ms expected. Acceptable for MVP if Redis is co-located. Option B (snapshot persistence) remains available as future migration.

**Q5: Do we need stats for non-V24 (V23) matches?**

**CONFIRMED:** V23 matches do not produce V24DetailedMatchData. For mixed careers, stats will only reflect V24-enabled rounds. API should return an `incomplete` flag in this case.

**Q6: How to handle player transfers?**

**CONFIRMED.** Stats split by team via `teamId` on `PlayerSeasonStatsDto`. A transferred player appears twice — once per team — with separate `PlayerSeasonStatsDto` entries. Implementation must handle this correctly.

---

### 13.3 Low Priority — Confirmed

**Q8: formDeltaSeason — confirmed NOT AVAILABLE.** No form-at-round-1 baseline stored. No timeline form events. Drop from MVP.

**Q9: averageEnergy / lowestEnergy — confirmed NOT AVAILABLE.** SessionPlayer.energy is current-state only. No per-round snapshot. Drop from MVP.

---

## 15. M3 MVP Field Scope (Defined by M2 Audit)

Based on M2 audit, M3 implements the following PlayerSeasonStatsDto fields:

### 14.1 Safe to Implement (HIGH Confidence)

| Field | Source | Notes |
|-------|--------|-------|
| careerId | V24DetailedMatchData.careerId | Direct |
| season | V24DetailedMatchData.seasonNumber | Direct |
| teamId | V24PlayerMatchRatingDto.teamId | Direct |
| playerId | V24PlayerMatchRatingDto.playerId | Direct |
| playerName | V24PlayerMatchRatingDto.playerName | Direct |
| position | V24PlayerMatchRatingDto.position | Direct |
| goals | V24PlayerMatchRatingDto.goals | Direct sum |
| assists | V24PlayerMatchRatingDto.assists | Direct sum |
| keyPasses | V24PlayerMatchRatingDto.keyPasses | Direct sum |
| shots | V24PlayerMatchRatingDto.shots | Direct sum |
| yellowCards | V24PlayerMatchRatingDto.yellowCards | Direct sum |
| redCards | V24PlayerMatchRatingDto.redCards | Direct sum |
| injuries | V24PlayerMatchRatingDto.injuries | Direct sum |
| fouls | V24PlayerMatchRatingDto.fouls | Direct sum (bonus field) |
| averageRating | derived | Mean of per-match ratings |
| bestRating | derived | Max of per-match ratings |
| worstRating | derived | Min of per-match ratings |
| lastUpdatedRound | derived | Max round processed |

### 14.2 Approximate (Implemented with Known Limitations)

| Field | Derivation | Limitation |
|-------|-----------|------------|
| appearances | startingAppearances + substituteAppearances | Bench players who played but were never in starting XI are undercounted |
| starts | count of !substitutedIn per match | Accurate for starters |
| matchesMissedInjured | count of INJURY events × estimated duration | Approximation only; not precise |
| matchesMissedSuspended | derived from yellow card accumulation | Approximate; needs verification |

### 14.3 Deferred (NOT in M3 MVP)

| Field | Reason |
|-------|--------|
| minutesPlayed | No per-minute tracking in source data |
| shotsOnTarget | SHOT_ON_TARGET event not counted in stats model |
| currentForm | No form history in source data |
| formDeltaSeason | No form-at-round-1 baseline |
| averageEnergy | No energy history in source data |
| lowestEnergy | No energy history in source data |

---

## 16. Proposed Phases

### M1 — Design

**Status: COMPLETE** (`011ff92`)
`V24D6M_PLAYER_SEASON_STATS_DESIGN.md`

### M2 — Source Data Audit

**Status: COMPLETE** (this document update)
Code-level findings incorporated into design doc.

### M3 — Pure Aggregator Service + Unit Tests

**Deliverables:**
- `PlayerSeasonStatsAggregator` — pure function, reads V24DetailedMatchData, computes aggregates
- `PlayerSeasonStatsDto` — DTO with M3 MVP fields only (Section 14)
- `PlayerSeasonStatsResponse` — list wrapper with team totals
- Unit tests for all safe fields and approximate field derivations
- Storage gap workaround: use `redisTemplate.keys()` pattern scan for career + season filtering

**Scope limits:** No API endpoints. No new Redis keys. No shotsOnTarget, minutesPlayed, form, or energy fields.

### M4 — Query Service + API DTOs + Integration Tests

**Deliverables:**
- `PlayerSeasonStatsQueryService`
- API controller with 3 endpoints (Section 8)
- Integration tests
- Feature flag check on `persist-detail`
- `incomplete` flag when detail data is partial

### M5 — Optional Redis Snapshot Persistence

Future. Only if M4 query performance is unacceptable.

### M6 — Frontend Stats UI

Separate frontend repo.

### M7 — Docs/Status Update

Status/roadmap updates.

---

## 17. Phase Summary

| Phase | Type | Status | Key Deliverable | Commit |
|-------|------|--------|-----------------|--------|
| M1 | Design | **COMPLETE** | `V24D6M_PLAYER_SEASON_STATS_DESIGN.md` | `011ff92` |
| M2 | Source audit | **COMPLETE** | Field mapping confirmed; M3 MVP scope defined | `db36055` |
| M3 | Implementation | **COMPLETE** | Pure aggregator + DTOs + 19 tests | `533f101` |
| M4 | Implementation | **COMPLETE** | Query service + API endpoints + 18 tests | `45c78c6` |
| M5 | Docs/Status | **PENDING** | This update — status docs + roadmap | — |
| M6 | Future | Future | Pagination / response metadata design | — |
| M7 | Future | Future | Frontend stats UI design | — |

---

## 18. V24D6M4 API Implementation

**Commit:** `45c78c6`

### Endpoints

| Method | Path | Behavior when no data | Feature disabled |
|--------|------|----------------------|------------------|
| GET | `.../seasons/{season}/player-stats` | 200, empty `playerStats[]` | 404 |
| GET | `.../seasons/{season}/teams/{teamId}/player-stats` | 200, empty `playerStats[]` | 404 |
| GET | `.../seasons/{season}/players/{playerId}/stats` | **404** if player not found | 404 |

### Storage

- `V24DetailedMatchStoragePort.findByCareerId(careerId)` — bulk read, no Redis writes
- KEYS scan pattern `career:{careerId}:match-detail:*` + individual GETs per match
- Existing key format unchanged: `career:{careerId}:match-detail:{matchId}`
- No new Redis keys introduced

### Feature Flag

`app.simulation.v24.expose-detail-api=false` (default) → all endpoints return 404

---

## 19. Deferred Items (Still Not Implemented)

| Item | Reason |
|------|--------|
| `minutesPlayed` | No per-minute tracking in source data |
| `shotsOnTarget` | `SHOT_ON_TARGET` event not counted in stats model |
| `currentForm` / `formDeltaSeason` | No form history baseline in source data |
| `averageEnergy` / `lowestEnergy` | No energy history in source data |
| Pagination (`limit`/`offset`) | Not in M4 scope |
| Response-level cache metadata (`lastUpdatedRound` at response level) | Player-level `lastUpdatedRound` exists in `PlayerSeasonStatsDto`; response-level metadata deferred |
| Redis snapshot persistence | Option B from Section 7 — deferred |
| Frontend stats UI | Separate frontend repo |

---

## 20. Recommended Next Phase: V24D6M6

**V24D6M6 — Pagination / Response Metadata Design**

Design-only. No implementation until API consumers exist:
- `limit` / `offset` query parameters for all-player endpoint
- Response-level `metadata` block with `lastUpdatedRound`, `totalMatches`, `totalPlayers`
- Sort options: `sortBy` (goals/assists/rating/appearances), `order` (asc/desc)
- Backward-compatible — all new fields optional
