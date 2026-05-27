# V24D6M6 — Player Season Stats API Polish Design

**Status:** V24D6M6+M7 COMPLETE — API polish design implemented by V24D6M7 (`92669fb`). M8 docs/status pending.
**Branch:** `mvp-1-performance-cleanup`
**Date:** 2026-05-26
**Based on:** V24D6M4 complete (`45c78c6`), full suite 768 tests (after M7)

---

## 1. Executive Summary

V24D6M4 delivered three working read-only player season stats endpoints behind the `expose-detail-api` feature flag. The API was functional but unadorned: no pagination, no response metadata, no sort validation, no warning signals.

V24D6M7 (commit `92669fb`) implemented all M6 design goals:

**M7 implementation complete:**
1. Pagination — `limit` / `offset` query parameters with validation
2. Response metadata — counts, flags, and provenance at response level
3. Sort contract — explicit supported fields, order, and tie-breaking
4. Warning signals — approximate/deferred field disclosure
5. Backward compatibility — existing M4 responses remain valid

**What changed:** Adding `metadata` and `warnings` fields to `PlayerSeasonStatsResponse`. The existing M4 endpoint contracts and behaviors are preserved.

---

## 2. Current API Baseline

### 2.1 Existing M4 Endpoints

All three endpoints live under `/api/careers` and are feature-gated by `app.simulation.v24.expose-detail-api=false`.

#### Endpoint 1 — All Players
```
GET /api/careers/{careerId}/seasons/{season}/player-stats
```

| Condition | Response |
|-----------|----------|
| Feature disabled (`expose-detail-api=false`) | `404 Not Found` |
| No detail data for career/season | `200 OK` — `playerStats: []` |
| Detail data exists | `200 OK` — full response |

#### Endpoint 2 — Team Filtered
```
GET /api/careers/{careerId}/seasons/{season}/teams/{teamId}/player-stats
```

| Condition | Response |
|-----------|----------|
| Feature disabled | `404 Not Found` |
| No data for team/season | `200 OK` — `playerStats: []` |
| Data exists | `200 OK` — filtered response |

#### Endpoint 3 — Single Player
```
GET /api/careers/{careerId}/seasons/{season}/players/{playerId}/stats
```

| Condition | Response |
|-----------|----------|
| Feature disabled | `404 Not Found` |
| Player has no stats (empty result) | `404 Not Found` |
| Player has data | `200 OK` — single-player response |

### 2.2 Current Response Shape

```json
{
  "careerId": "career-123",
  "season": 1,
  "playerStats": [
    {
      "playerId": "p1",
      "playerName": "John Doe",
      "teamId": "team-A",
      "position": "ST",
      "appearances": 20,
      "starts": 18,
      "goals": 12,
      "assists": 5,
      "keyPasses": 30,
      "shots": 45,
      "yellowCards": 2,
      "redCards": 0,
      "injuries": 1,
      "fouls": 15,
      "matchesMissedInjuredApprox": 3,
      "matchesMissedSuspendedApprox": 0,
      "averageRating": 7.5,
      "bestRating": 9.0,
      "worstRating": 6.0,
      "lastUpdatedRound": 30
    }
  ],
  "totalGoals": 42,
  "totalAssists": 18,
  "totalAppearances": 20,
  "averageRating": 7.2,
  "incomplete": false,
  "message": "ok"
}
```

### 2.3 Storage / Data Source

- Redis key pattern: `career:{careerId}:match-detail:{matchId}`
- Read-only scan via `V24DetailedMatchStoragePort.findByCareerId(careerId)`
- No Redis writes from the query service
- No new Redis keys introduced

### 2.4 What Is Not in M4

- No pagination (`limit`/`offset`)
- No response-level metadata
- No `warnings` array
- No sort validation
- No cache hints
- No incomplete-data signal beyond `incomplete: boolean`

### 2.5 What's in M7 (Implemented in Commit `92669fb`)

All items above are now implemented:
- `limit`/`offset` pagination with validation (limit ≤ 0 → 400, limit > 200 → clamp + warning, offset < 0 → 400)
- Response-level `metadata` block with limit/offset/hasMore/totalPlayers/returnedPlayers/totalMatchesProcessed/lastUpdatedRound/dataSource/dataCompleteness/generatedAt/versionHash
- `warnings` array with APPROXIMATE_APPEARANCES, APPROXIMATE_MATCHES_MISSED, NO_DETAIL_DATA, LARGE_LIMIT_CLAMPED
- Sort validation for 12 fields with stable tie-breaking
- Cache hints via `generatedAt` and `versionHash` (no full HTTP ETag)

---

## 3. Pagination Contract

### 3.1 Approach Decision

| Approach | Pros | Cons |
|----------|------|------|
| **A: `limit` + `offset`** (recommended) | Simple, cursor-agnostic, good for tables and leaderboards | No total count without extra computation |
| B: `page` + `size` | Familiar to web devs, natural page numbers | Dual semantics if `offset` also supported; page 0 vs page 1 confusion |
| C: `cursor` | Best for infinite scroll | Complex for leaderboard use case; requires stable sort |

**Recommendation: Approach A — `limit` + `offset`**

MVP use cases (squad stats table, top scorers leaderboard) are well-served by `limit`/`offset`. Cursor-based pagination adds complexity without benefit for season-bounded data.

### 3.2 Query Parameters

| Parameter | Type | Default | Max | Description |
|-----------|------|---------|-----|-------------|
| `limit` | integer | `50` | `200` | Max player records per response |
| `offset` | integer | `0` | — | Skip first N players (for pagination) |

### 3.3 Validation Rules

| Input | Recommended Behavior | Alternative |
|-------|----------------------|-------------|
| `limit <= 0` | `400 Bad Request` | `400 Bad Request` |
| `limit > 200` | **Clamp to 200** + add warning | `400 Bad Request` |
| `limit` non-integer | `400 Bad Request` | `400 Bad Request` |
| `offset < 0` | `400 Bad Request` | `400 Bad Request` |
| `offset >= total` | `200 OK` — `playerStats: []`, `hasMore: false` | Same |

**Justification for clamping `limit > max`:** A client accidentally requesting `limit=999` should not receive a `400` error. Clamping + warning is forgiving behavior that does not break existing clients. Return `400` only for **semantically invalid** inputs (negative values, wrong types). Excessively large `limit` is a performance guard, not a semantic error.

### 3.4 Pagination Metadata Fields

Added to `PlayerSeasonStatsResponse.metadata`:

```json
{
  "metadata": {
    "limit": 50,
    "offset": 0,
    "hasMore": true,
    "totalPlayers": 87,
    "returnedPlayers": 50
  }
}
```

- `limit` — effective limit used (may differ from requested if clamped)
- `offset` — effective offset used
- `hasMore` — `true` if `offset + returnedPlayers < totalPlayers`
- `totalPlayers` — total players matching the query (all seasons / team / player filters applied)
- `returnedPlayers` — count of players in this response page

### 3.5 Tie-Breaking for Stable Pagination

When `limit`/`offset` pagination is used, results must be deterministic across pages. The sort order must be **stable unique**.

**Recommended tie-break chain:**
1. Primary sort field (e.g., `goals desc`)
2. `assists desc`
3. `averageRating desc`
4. `playerName asc`
5. `playerId asc` (guaranteed unique)

This ensures that two players with identical primary+secondary sort values never produce unstable ordering across pagination requests.

### 3.6 Open Question

**Should `totalPlayers` count all players in the career/season, or only those with at least one appearance?**

Recommendation: Count all players with at least one entry in `playerRatings` (i.e., `appearances > 0 OR substitutedIn == true`). This matches what users expect from a leaderboard. A player who never played should not appear in the count.

---

## 4. Sort Contract

### 4.1 Supported Sort Fields

| Field | Type | Notes |
|-------|------|-------|
| `goals` | integer | Primary sort for golden boot |
| `assists` | integer | Secondary / top assister |
| `averageRating` | double | Best performers |
| `appearances` | integer | Most-used players |
| `starts` | integer | Starter consistency |
| `shots` | integer | Shooting volume |
| `keyPasses` | integer | Chance creation |
| `yellowCards` | integer | Discipline (desc = most bad) |
| `redCards` | integer | Discipline (desc = most bad) |
| `injuries` | integer | Availability concerns |
| `fouls` | integer | Aggression metric |
| `playerName` | string | Alphabetical (stable tie-break) |

### 4.2 Order

| Value | Meaning |
|-------|---------|
| `asc` | Ascending (lowest first) |
| `desc` | Descending (highest first) |

**Default:** `sortBy=goals`, `order=desc`

### 4.3 Unknown Sort Field

If `sortBy` value is not in the supported list above → `400 Bad Request` with message listing valid values.

Do **not** silently fall back to default. Returning `400` with explicit valid options prevents client confusion.

### 4.4 Example Request

```
GET /api/careers/career-123/seasons/1/player-stats?sortBy=averageRating&order=desc&limit=10&offset=0
```

---

## 5. Response Metadata Design

### 5.1 Metadata Block

Add to `PlayerSeasonStatsResponse.metadata`:

```json
{
  "metadata": {
    "limit": 50,
    "offset": 0,
    "hasMore": true,
    "totalPlayers": 87,
    "returnedPlayers": 50,
    "totalMatchesProcessed": 38,
    "lastUpdatedRound": 38,
    "dataSource": "V24_DETAIL",
    "dataCompleteness": "COMPLETE"
  }
}
```

### 5.2 Field Definitions

| Field | Type | Description |
|-------|------|-------------|
| `limit` | integer | Effective limit (may differ from requested if clamped) |
| `offset` | integer | Effective offset |
| `hasMore` | boolean | `true` if more players exist beyond this page |
| `totalPlayers` | integer | Total players matching all filters |
| `returnedPlayers` | integer | Count of players in this response |
| `totalMatchesProcessed` | integer | Number of V24DetailedMatchData records scanned for this response |
| `lastUpdatedRound` | integer | Highest `round` value found in scanned match data |
| `dataSource` | string | Fixed: `"V24_DETAIL"` — indicates source of data |
| `dataCompleteness` | enum | `"COMPLETE"` \| `"PARTIAL"` \| `"EMPTY"` \| `"UNKNOWN"` |

### 5.3 `dataCompleteness` Semantics

| Value | Meaning | When |
|-------|---------|-------|
| `COMPLETE` | All known matches for this season have detail data | Expected for normal careers |
| `PARTIAL` | Some matches have detail data but some rounds appear missing | First/last round of active season, or gaps |
| `EMPTY` | No detail data found for this career/season | Feature disabled or new career |
| `UNKNOWN` | Cannot determine completeness | Not enough information to assess |

### 5.4 `totalMatchesProcessed` Clarification

This is the count of `V24DetailedMatchData` records scanned from Redis (via `findByCareerId`). It is **not** the total rounds in the season. It reflects what was actually available for aggregation.

- A complete season with 38 rounds and 20 teams playing would produce up to 380 match detail records
- `totalMatchesProcessed` = number of those records found in Redis

### 5.5 Metadata Backward Compatibility

Adding `metadata` to the response does **not** break existing clients. Clients that deserialize with `PlayerSeasonStatsResponse` as a Java bean or a JSON library will receive `null` for unknown fields unless they explicitly ignore unknown fields.

**Constraint:** Do not remove existing fields. Do not change types of existing fields.

---

## 6. Warning Model

### 6.1 Design

Add optional `warnings` array to `PlayerSeasonStatsResponse`:

```json
{
  "warnings": [
    {
      "code": "PARTIAL_DETAIL_DATA",
      "message": "Season stats are incomplete because detail data is missing for some rounds (rounds 3, 7, 12).",
      "field": null
    },
    {
      "code": "APPROXIMATE_APPEARANCES",
      "message": "'appearances' is approximate because substitute players who never started are not fully counted.",
      "field": "appearances"
    }
  ]
}
```

### 6.2 Warning Object Shape

| Field | Type | Description |
|-------|------|-------------|
| `code` | string | Machine-readable warning identifier (upper_snake_case) |
| `message` | string | Human-readable explanation |
| `field` | string or null | Field this warning relates to, if applicable |

### 6.7 Potential Warning Codes

| Code | Meaning | Severity | Field |
|------|---------|----------|-------|
| `V24_DETAIL_DISABLED` | Feature flag is off — no detail data available | Error | — |
| `NO_DETAIL_DATA` | No V24DetailedMatchData found for this career/season | Info | — |
| `PARTIAL_DETAIL_DATA` | Some rounds have no detail data — stats may be incomplete | Warning | — |
| `APPROXIMATE_APPEARANCES` | `appearances` is approximate — bench players may be undercounted | Info | `appearances` |
| `APPROXIMATE_MATCHES_MISSED` | `matchesMissedInjuredApprox` / `matchesMissedSuspendedApprox` are estimates | Info | `matchesMissedInjuredApprox`, `matchesMissedSuspendedApprox` |
| `DEFERRED_MINUTES_PLAYED` | `minutesPlayed` field not available — not in source data | Info | `minutesPlayed` (not in DTO) |
| `DEFERRED_SHOTS_ON_TARGET` | `shotsOnTarget` not available — `SHOT_ON_TARGET` events not counted | Info | `shotsOnTarget` (not in DTO) |
| `DEFERRED_FORM_ENERGY` | `averageEnergy`/`lowestEnergy`/`currentForm` not available — no history | Info | `averageRating` (approx) |
| `LARGE_LIMIT_CLAMPED` | Requested `limit` > 200 was clamped to 200 | Info | `limit` |

### 6.8 Implementation Note for M7

Warnings are ** informational only**. The HTTP response code is still `200` unless the feature is disabled (404). Warnings do not change HTTP status.

`V24_DETAIL_DISABLED` warning should only appear if `expose-detail-api` is `false`. Currently this returns `404`, but M7 may change it to `200` with warning + empty `playerStats`. Discuss with frontend before making that change.

---

## 7. Incomplete Data Semantics

### 7.1 Definitions

The `dataCompleteness` field communicates the quality of the underlying data:

| Value | Definition | User-Facing Message |
|-------|------------|---------------------|
| `COMPLETE` | All completed rounds in the season have a corresponding `V24DetailedMatchData` record | "Complete data" |
| `PARTIAL` | At least one expected round's detail data is missing, but some data exists | "Incomplete data — some rounds missing" |
| `EMPTY` | No `V24DetailedMatchData` records found for this career/season | "No data — V24 detail may be disabled" |
| `UNKNOWN` | Cannot determine completeness (e.g., season number not found in any record) | "Data completeness unknown" |

### 7.2 How to Compute Completeness

For MVP, a simple heuristic is sufficient:

1. Count distinct `round` values present in scanned `V24DetailedMatchData` records
2. Compare to expected number of rounds for the season

**For `PARTIAL` detection:** If `totalMatchesProcessed > 0` but `lastUpdatedRound` is less than the expected final round of the season, mark as `PARTIAL`.

**For `COMPLETE` detection:** If `lastUpdatedRound` >= expected_final_round AND no gaps detected (future: compare expected round set to actual round set), mark as `COMPLETE`.

**Simplicity note:** For MVP, only detect `EMPTY` vs `NON-EMPTY`. `PARTIAL` detection adds complexity and can be added in a later iteration. Mark all non-empty responses as `COMPLETE` for now, and add `PARTIAL` detection in a future phase.

---

## 8. Cache Hints

### 8.1 Designed Fields

| Field | Type | Description |
|-------|------|-------------|
| `lastUpdatedRound` | integer | Highest round number in the scanned data |
| `generatedAt` | ISO-8601 timestamp | When this response was computed |
| `versionHash` | string | Hash of the raw data used to compute stats (optional, for ETag) |

### 8.2 Metadata Block Extension

```json
{
  "metadata": {
    "lastUpdatedRound": 38,
    "generatedAt": "2026-05-26T15:30:00Z",
    "versionHash": "a3f2b8c1"
  }
}
```

### 8.3 `versionHash` Purpose

`versionHash` is computed from the set of `matchId` values of all scanned `V24DetailedMatchData` records. If the set of matches hasn't changed, the hash is identical. If new match detail data was persisted, the hash changes.

This enables **conditional re-fetch**: clients can store the last `versionHash` and pass it as a request header (`If-None-Match: "a3f2b8c1"`) — if the data hasn't changed, the server returns `304 Not Modified`.

### 8.4 ETag Recommendation

**Do not implement HTTP ETag in M7.** Adding proper ETag support requires:
- Server-side `ETag` header generation
- `If-None-Match` request header handling
- `304 Not Modified` response logic
- Coordination with frontend HTTP client configuration

Instead, **include `versionHash` in the JSON response** as a plain field. Frontend can read it and optionally use it for local caching. This is simpler and more portable than full ETag support.

When API consumers exist and need conditional re-fetch, full ETag support can be added in a separate design doc.

### 8.5 Cache Hint Backward Compatibility

Adding these fields to `metadata` is backward-compatible. Existing clients ignore unknown fields.

---

## 9. Backward Compatibility

### 9.1 Principles

1. **Existing fields never removed** — all M4 response fields remain
2. **New fields are optional** — clients that don't expect them should not break
3. **Types never changed** — `playerStats` remains an array, not null
4. **Behavior preserved** — feature-disabled returns `404`, single-player-missing returns `404`

### 9.2 What Is Backward Compatible

| Change | Compatible? | Reason |
|--------|-------------|--------|
| Adding `metadata` object | ✅ Yes | Unknown JSON fields ignored by most deserializers |
| Adding `warnings` array | ✅ Yes | Unknown JSON array ignored |
| Adding new fields to `metadata` | ✅ Yes | Unknown fields ignored |
| Adding `versionHash` to metadata | ✅ Yes | New string field |
| Adding `dataCompleteness` to metadata | ✅ Yes | New enum field |

### 9.3 What Is NOT Backward Compatible

| Change | Breaking? | Reason |
|--------|----------|--------|
| Removing `playerStats` field | ❌ Yes | Clients expect it |
| Changing `playerStats` from array to object | ❌ Yes | Type change breaks deserializers |
| Returning `200` instead of `404` for disabled feature | ❌ Yes | Behavior change |
| Returning `200` instead of `404` for missing player | ❌ Yes | Behavior change (existing M4 single-player endpoint returns 404) |

### 9.4 Version Strategy

The API does not currently have a versioned path (e.g., `/v2/`). Adding `metadata` does not require a version bump. If a future change is truly breaking, a `/v2/` path would be added alongside the existing `/v1/` path.

**For M7:** No version bump needed. The changes are purely additive.

---

## 10. Proposed M7 Implementation Scope

### 10.1 In Scope for M7

**Implementation:**
- Add `limit` (default 50, max 200) and `offset` (default 0) query parameters to all-player and team-filtered endpoints
- Clamp `limit > 200` to 200 + add `LARGE_LIMIT_CLAMPED` warning
- Validate `limit <= 0` → `400`
- Validate `offset < 0` → `400`
- Validate `sortBy` value — reject unknown fields with `400`
- Add `metadata` block to `PlayerSeasonStatsResponse` with `limit`, `offset`, `hasMore`, `totalPlayers`, `returnedPlayers`, `totalMatchesProcessed`, `lastUpdatedRound`, `dataSource`, `dataCompleteness`
- Add `warnings` array (optional field, empty when no warnings)
- Add `generatedAt` to metadata (current server time)
- Add `versionHash` to metadata (hash of match IDs scanned)
- Apply tie-break chain for stable ordering: `goals DESC, assists DESC, averageRating DESC, playerName ASC, playerId ASC`
- Add `sortBy` and `order` params to team-filtered endpoint
- Add sort validation tests
- Add pagination validation tests
- Add metadata/warnings tests

**Tests:**
- `defaultPagination_returnsFirst50`
- `limitOffsetApplied_playersReturnedCorrectRange`
- `limitTooLarge_clampedTo200_withWarning`
- `offsetNegative_returns400`
- `invalidSortBy_returns400`
- `metadata_totalsCorrect`
- `metadata_hasMoreTrueWhenMoreRows`
- `metadata_hasMoreFalseAtEnd`
- `warnings_includeApproximateFields`
- `existingM4EndpointsStillWork_unchangedBehavior`

### 10.2 Out of Scope for M7

- **Redis snapshot persistence** — Option B from V24D6M1 design doc; deferred
- **Frontend stats UI** — separate frontend repo
- **`minutesPlayed` field** — no source data in `V24PlayerMatchRatingDto`
- **`shotsOnTarget` field** — `SHOT_ON_TARGET` events not counted in stats model
- **`currentForm` / `formDeltaSeason`** — no historical baseline in source data
- **`averageEnergy` / `lowestEnergy`** — no per-round energy history
- **HTTP ETag support** — `versionHash` field only, not full conditional GET
- **`page` / `size` pagination** — not supported; `limit`/`offset` only

---

## 11. Test Plan for M7

### 11.1 Pagination Tests

| Test | Description |
|------|-------------|
| `pagination_defaultLimit_returns50` | No params → first 50 players sorted by default |
| `pagination_limit10_returns10` | `limit=10` → exactly 10 players in response |
| `pagination_offset50_skipsFirst50` | `offset=50` → players 51+ |
| `pagination_limit200_maxRespected` | `limit=500` → clamped to 200, no error |
| `pagination_limit0_returns400` | `limit=0` → 400 Bad Request |
| `pagination_limitNegative_returns400` | `limit=-1` → 400 Bad Request |
| `pagination_offsetNegative_returns400` | `offset=-5` → 400 Bad Request |
| `pagination_hasMore_trueWhenMoreExist` | `limit=10, offset=0, total=87` → `hasMore: true` |
| `pagination_hasMore_falseAtEnd` | `limit=10, offset=80, total=87` → `hasMore: false` |
| `pagination_totalPlayers_stableAcrossPages` | `totalPlayers` same on page 1 and page 2 |
| `pagination_tieBreak_stableAcrossPages` | Same player never appears on two pages |

### 11.2 Sort Tests

| Test | Description |
|------|-------------|
| `sort_goalsDesc_default` | No sort param → goals DESC |
| `sort_assistsDesc` | `sortBy=assists, order=desc` → assists DESC |
| `sort_averageRatingDesc` | `sortBy=averageRating, order=desc` → rating DESC |
| `sort_playerNameAsc` | `sortBy=playerName, order=asc` → alphabetical |
| `sort_invalidField_returns400` | `sortBy=invalidField` → 400 with valid options |
| `sort_invalidOrder_returns400` | `order=invalid` → 400 |
| `sort_tieBreak_appliesSecondSort` | Two players same goals → assists DESC break |
| `sort_tieBreak_appliesThirdSort` | Two players same goals+assists → rating DESC break |

### 11.3 Metadata Tests

| Test | Description |
|------|-------------|
| `metadata_limitReflectsEffective` | Requested `limit=500`, metadata shows `limit: 200` |
| `metadata_offsetReflectsEffective` | Requested `offset=20`, metadata shows `offset: 20` |
| `metadata_totalPlayers_matchesFilter` | Team filter → `totalPlayers` is count within team |
| `metadata_returnedPlayers_matchesPageSize` | `limit=10` → `returnedPlayers: 10` |
| `metadata_lastUpdatedRound_fromScannedData` | Highest round in scanned matches |
| `metadata_dataSource_fixedValue` | Always `"V24_DETAIL"` |
| `metadata_dataCompleteness_COMPLETE_normalCareer` | Normal career with full season → `COMPLETE` |
| `metadata_dataCompleteness_EMPTY_noData` | No detail data → `EMPTY` |

### 11.4 Warning Tests

| Test | Description |
|------|-------------|
| `warnings_empty_whenNoIssues` | Normal career → `warnings: []` |
| `warnings_approximateAppearances_included` | Substituted players → `APPROXIMATE_APPEARANCES` warning |
| `warnings_largeLimitClamped_included` | `limit=500` → `LARGE_LIMIT_CLAMPED` warning |
| `warnings_partialDetail_included` | Gap in rounds → `PARTIAL_DETAIL_DATA` warning |

### 11.5 Regression Tests

| Test | Description |
|------|-------------|
| `m4_allPlayersEndpoint_unchanged` | `GET .../player-stats` with no params → same as M4 |
| `m4_teamEndpoint_unchanged` | `GET .../teams/{teamId}/player-stats` → same as M4 |
| `m4_playerEndpoint_still404missing` | `GET .../players/unknown` → 404 |
| `m4_featureDisabled_still404` | `expose-detail-api=false` → 404 for all endpoints |

---

## 12. Recommended Next Phase: V24D6M8

**V24D6M8 — Docs/Status Update**

All M6/M7 implementation work is complete. M8 is a docs-only update to close out the V24D6M phase and update all status/roadmap documents.

---

*V24D6M6 design complete. M7 implementation follows this specification.*