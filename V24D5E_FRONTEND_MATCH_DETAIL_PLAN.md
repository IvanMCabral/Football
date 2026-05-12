# V24D5E — Frontend Match Detail Planning/Design

**Status:** V24D5F COMPLETED — playerRatings backend persistence now delivered; V24D5E1/V24D5E2/V24D5E3/V24D5E3B completed in separate frontend repo; V24D5E4 (player ratings UI) now unblocked from backend side; V24D5E5 (shot map) deferred
**Frontend repo:** `front-ciber/project` / Football-angular
**Frontend branch:** `mvp-1`
**Frontend commits:**
- `050ab57` — feat: add V24D5E2 match detail API client and TypeScript types
- `0ba2305` — feat: add V24D5E3 read-only match detail page
- `d244097` — feat: add match detail entry point from fixture modal
**Type:** Frontend design and integration planning
**Date:** 2026-05-11

---

## 1. Executive Summary

V24D5E is **planning and design only**. No frontend implementation begins until this document is approved.

V24 detailed match data (stored in Redis, queryable via existing endpoint) is **additive enrichment** — it must never be required for career progress. The existing aggregate match result remains the source of truth for standings. If detail is unavailable (older matches, disabled flags, or missing data), the UI must gracefully fall back to the existing fixture display.

Key principles:
- V24 detail is **optional enrichment**, not a replacement
- Graceful degradation when detail is missing
- Progressive disclosure to avoid overwhelming users
- No career progression blocked on V24 detail availability
- MatchFixture.MatchResultData (6 aggregate fields) is untouchable

---

## 2. Current Backend Capabilities

The backend already exposes V24 detailed match data. Frontend only needs to consume it.

| Item | Value |
|------|-------|
| Endpoint | `GET /api/careers/{careerId}/matches/{matchId}/detail` |
| Endpoint flag | `app.simulation.v24.expose-detail-api=false` (default false) |
| Persistence flag | `app.simulation.v24.persist-detail=false` (default false) |
| Simulation flag | `app.simulation.league.use-v24-detailed-engine=false` (default false) |
| Redis key | `career:{careerId}:match-detail:{matchId}` |
| 404 when | endpoint disabled OR detail not persisted |
| All flags default | false — detail is fully opt-in |
| playerRatings backend | **Populated since V24D5F** (commit `0c4d62b`) — `V24PlayerRatingsAssembler` resolves starting XI + timeline → ratings |

Full suite: 398 tests | Regression gate: 398 tests | 0 failures

---

## 3. Current Detail DTO Shape

The endpoint returns `V24DetailedMatchData`. Key fields:

```
V24DetailedMatchData:
  matchId          String
  careerId         String
  seasonNumber     Integer
  round            Integer
  homeTeamId       String
  awayTeamId       String
  homeTeamName     String
  awayTeamName     String
  homeGoals         Integer
  awayGoals         Integer
  homeXg           Double
  awayXg           Double
  homeShots        Integer
  awayShots        Integer
  homePossession   Integer
  awayPossession   Integer
  timeline         List<V24MatchEventDto>
  playerRatings    List<PlayerMatchRatingDto>  ← populated for newly persisted V24 details after V24D5F; may be empty for old matches or missing data
  schemaVersion    String
  engineVersion    String
  createdAt        Instant
```

```
V24MatchEventDto:
  minute           Integer
  type             String (GOAL, SHOT, FOUL, YELLOW_CARD, RED_CARD, INJURY, SUBSTITUTION, OFFSIDE, CORNER)
  teamId           String
  playerId         String
  playerName       String
  relatedPlayerId  String   ← nullable (for ASSIST on GOAL)
  relatedPlayerName String  ← nullable
  xg               Double   ← nullable (for SHOT/GOAL)
  description      String
  shotCoordinate   V24ShotCoordinateDto  ← nullable currently
```

```
V24ShotCoordinateDto:
  x, y             Double (pitch coords 0–100)
  location         String (SIX_YARD_BOX, PENALTY_AREA_CENTER, PENALTY_AREA_WIDE, OUTSIDE_BOX, LONG_RANGE)
  distanceToGoal   Double (meters)
  angleToGoal      Double (radians)
  insideBox        Boolean
```

```
PlayerMatchRatingDto:
  playerId         String
  playerName        String
  teamId            String
  position          String
  rating            Double (1.0–10.0)
  goals             Integer
  assists           Integer
  keyPasses         Integer
  shots             Integer
  cards             Integer
  injuries          Integer
  substitutions     Integer
```

**Current known limitations:**
- `shotCoordinate` is **nullable** — shot coordinates require V24D3C event attachment
- UI must handle both fields gracefully (empty/hidden, not error)
- `playerRatings` are now populated for newly persisted V24 details after V24D5F (backend complete); may be empty for older matches or missing data; full player ratings UI still deferred until V24D5E4

---

## 4. UX Goals

1. Show richer post-match report **without breaking** the existing match list/standings
2. Keep aggregate score visible and **primary**
3. **Progressive disclosure** — start with summary, allow drilling into detail
4. **Graceful fallback** — if detail is unavailable, show existing fixture result cleanly
5. **Mobile-friendly** and desktop-friendly
6. **Never block** round simulation or career progress

---

## 5. Proposed UI Entry Point

**Recommended: Dedicated route**
```
/careers/:careerId/matches/:matchId/detail
```

**Not recommended:** Inline expansion of fixture list row or modal overlay (too complex for timeline/shots depth)

Why dedicated route:
- Clean deep-linking and bookmarking
- Independent refresh without reloading career page
- Straightforward loading/error states
- Less risk to existing fixture list component
- Easier to progressively enhance tab by tab

Later: optional modal overlay for quick summary (after V24D5E3 baseline)

---

## 6. Proposed Page Layout

```
Route: /careers/:careerId/matches/:matchId/detail

┌─────────────────────────────────────────────────────┐
│  [← Back to Career]                                 │
│                                                     │
│  Home Team    2 – 1    Away Team                   │
│  Round 5 • Season 1                                │
│  [V24 Engine Badge]                                │
├─────────────────────────────────────────────────────┤
│  [Summary] [Timeline] [Stats] [Players] [Shots]    │
├─────────────────────────────────────────────────────┤
│                                                     │
│  SUMMARY TAB (default):                            │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐           │
│  │   xG     │ │  Shots   │ │Possession│           │
│  │  1.8–1.2 │ │   12–8   │ │  55–45  │           │
│  └──────────┘ └──────────┘ └──────────┘           │
│                                                     │
│  TIMELINE TAB:                                      │
│  12'  ⚽ GOAL — Home ST (assist: CM) xG 0.35      │
│  28'  🟨 YELLOW — Away DF                         │
│  45'  🔄 SUB — Home CM in, ST out                 │
│  67'  ⚽ GOAL — Away RW (assist: AM) xG 0.22      │
│  81'  ⚽ GOAL — Home ST (unassisted) xG 0.51      │
│                                                     │
│  STATS TAB:                                        │
│  Possession    55% ─── 45%                        │
│  Shots          12 ─── 8                          │
│  xG            1.80 ─ 1.20                        │
│  Goals           2 ─── 1                           │
│                                                     │
│  PLAYERS TAB (hidden until playerRatings populated)│
│  "Player ratings are not available yet."          │
│                                                     │
│  SHOTS TAB (hidden until shotCoordinate exists)    │
│  "Shot map will be available in a future update." │
└─────────────────────────────────────────────────────┘
```

**Tab behavior:**
- Summary, Timeline, Stats always visible (data exists from V24 simulation)
- Players tab: shows empty state until `playerRatings` list is non-empty
- Shots tab: shows empty state until `shotCoordinate` is non-null on events

---

## 7. Loading, Empty, and Error States

### Loading
- Skeleton cards for summary numbers
- Skeleton list for timeline events
- Do not block page layout — show structure immediately

### 404 — Detail Unavailable
```
┌─────────────────────────────────────┐
│  Match Detail                        │
│  Home 2 – 1 Away                   │
│                                     │
│  ⚠  Detailed match data is not      │
│     available for this match.       │
│                                     │
│  Possible reasons:                  │
│  • Match was played before V24      │
│    detail persistence was enabled  │
│  • Detail persistence was disabled │
│  • Endpoint was disabled           │
│                                     │
│  [View Aggregate Result]            │
└─────────────────────────────────────┘
```
- Show existing fixture result if available from CareerSave
- Never show a scary error — this is expected for older matches
- Endpoint returns 404 by design when detail is missing

### 500 — Server Error
- Show: "Failed to load match detail. [Retry]"
- Do not crash the career page
- Retry button calls API again

### Empty playerRatings
- Show: "Player ratings are not available yet." (backend playerRatings persistence is now complete in V24D5F; this state only appears for older matches without persisted data or if flags are disabled)
- Do not show empty table with headers — too confusing
- Players tab becomes visible once `playerRatings` is non-empty (now the case for newly persisted matches after V24D5F)

### Null shotCoordinate
- Hide shots tab entirely
- Or show: "Shot map will be available in a future update."

---

## 8. API Client Design

### Recommended TypeScript interfaces

```typescript
interface MatchDetail {
  matchId: string;
  careerId: string;
  seasonNumber: number;
  round: number;
  homeTeamId: string;
  awayTeamId: string;
  homeTeamName: string;
  awayTeamName: string;
  homeGoals: number;
  awayGoals: number;
  homeXg: number;
  awayXg: number;
  homeShots: number;
  awayShots: number;
  homePossession: number;
  awayPossession: number;
  timeline: MatchEvent[];
  playerRatings: PlayerMatchRating[];  // may be empty for old matches; populated for newly persisted V24 details after V24D5F
  schemaVersion: string;
  engineVersion: string;
  createdAt: string;  // ISO instant
}

interface MatchEvent {
  minute: number;
  type: 'GOAL' | 'SHOT' | 'FOUL' | 'YELLOW_CARD' | 'RED_CARD' | 'INJURY' | 'SUBSTITUTION' | 'OFFSIDE' | 'CORNER';
  teamId: string;
  playerId: string;
  playerName: string;
  relatedPlayerId?: string;   // nullable
  relatedPlayerName?: string; // nullable
  xg?: number;                // nullable
  description: string;
  shotCoordinate?: ShotCoordinate;  // nullable currently
}

interface ShotCoordinate {
  x: number;
  y: number;
  location: 'SIX_YARD_BOX' | 'PENALTY_AREA_CENTER' | 'PENALTY_AREA_WIDE' | 'OUTSIDE_BOX' | 'LONG_RANGE';
  distanceToGoal: number;
  angleToGoal: number;
  insideBox: boolean;
}

interface PlayerMatchRating {
  playerId: string;
  playerName: string;
  teamId: string;
  position: string;
  rating: number;
  goals: number;
  assists: number;
  keyPasses: number;
  shots: number;
  cards: number;
  injuries: number;
  substitutions: number;
}
```

### API client method

```typescript
// Recommended: returns null on 404, throws on 500
async function getMatchDetail(careerId: string, matchId: string): Promise<MatchDetail | null> {
  const response = await fetch(`/api/careers/${careerId}/matches/${matchId}/detail`);
  if (response.status === 404) return null;  // detail not available
  if (!response.ok) throw new Error(`Failed to load detail: ${response.status}`);
  return response.json() as Promise<MatchDetail>;
}
```

**Key behavior:**
- 200 → return parsed `MatchDetail`
- 404 → return `null` (detail unavailable, not an error)
- 500 → throw, let caller show retry UI

---

## 9. Feature Flag Handling

The backend owns three independent flags. Frontend should **not** replicate them.

**Backend availability is the source of truth:**
- If endpoint returns 404 → detail not available → show graceful fallback
- If endpoint returns 200 → detail available → show detail UI

**Optional frontend staging flag:**
```
frontend.features.matchDetailV24=false
```
- Use only if menu/routing needs staged rollout before full enable
- Default to `true` once backend flags are production-enabled
- Do not gate the graceful fallback — even with frontend flag off, users see aggregate result

---

## 10. Compatibility Rules

**Must NOT:**
- Replace existing match result display on career/fixtures page
- Require V24 detail for career progress or standings
- Block round simulation
- Assume `playerRatings` is non-empty
- Assume `shotCoordinate` is non-null on any event
- Assume `timeline` is non-empty
- Mutate any backend state
- Change fixture result model

**Must:**
- Fall back to existing aggregate `MatchFixture.MatchResultData` when detail is 404
- Handle 404 gracefully (never an error for the user)
- Treat V24 detail as **optional enrichment**
- Handle nullable/empty fields without showing error states

---

## 11. Implementation Phases

### V24D5E1 — Design Document — COMPLETED
- No code
- Establishes UI/UX direction
- **Status: COMPLETED** — committed as `e64c2d9`

### V24D5E2 — Frontend API Client + Types — COMPLETED
**Commit:** `050ab57` (frontend repo `mvp-1`)
- TypeScript interfaces from Section 8
- `getMatchDetail(careerId, matchId): Observable<MatchDetail | null>` API client
- Error handling (200/404/500)
- URL-encoded `careerId` and `matchId` via `encodeURIComponent()`
- 200 → returns `MatchDetail`; 404 → returns `null`; 500+ → propagates
- Empty `playerRatings` list handled
- Nullable `shotCoordinate` and `relatedPlayerId/relatedPlayerName` handled
- No UI route yet
- Validation: `npx tsc --noEmit` OK, `npx ng build` BUILD SUCCESS
- **Status: COMPLETED**

### V24D5E3 — Read-only Match Detail Page — COMPLETED
**Commit:** `0ba2305` (frontend repo `mvp-1`)
- Route: `/careers/:careerId/matches/:matchId/detail`
- `V24MatchDetailPageComponent` — standalone Angular component with inline template/styles
- Uses `MatchDetailApiService.getMatchDetail(careerId, matchId)` from V24D5E2
- Header + score + round/season + V24 badge
- Summary cards: xG, shots, possession, goals
- Timeline tab: event list sorted by minute ascending
- Stats comparison table
- Loading state, 404/null unavailable state, 500/error retry state
- Empty `playerRatings` state: "Player ratings are not available yet."
- Shot map deferred state: "Shot map will be available in a future update."
- No player ratings full UI, no shot map implementation
- No fixture/list UI modified, no backend/API/Redis changes
- Validation: `npx tsc --noEmit` OK, `npx ng build` BUILD SUCCESS
- **Status: COMPLETED**

### V24D5E3B — Fixture/List Entry Point — COMPLETED
**Commit:** `d244097` (frontend repo `mvp-1`)
- Dashboard fixture modal (`DashboardFixtureModalComponent`) updated with "📊 Detalle" link
- Link visible only for matches with `status === 'COMPLETED'`
- Link hidden when `careerId` is unavailable (fetched via `CareerService.getCareerStatus()`)
- Link hidden for pending/in-progress matches
- Route target: `/careers/:careerId/matches/:matchId/detail`
- Fixture modal does NOT call detail endpoint (no pre-fetch, no polling)
- Match detail page (`V24MatchDetailPageComponent`) unchanged
- No backend/API/Redis changes, no player ratings UI, no shot map
- Validation: `npx tsc --noEmit` OK, `npx ng build` BUILD SUCCESS
- **Status: COMPLETED**

### V24D5E4 — Player Ratings UI — Pending
- Backend `playerRatings` persistence is now complete (V24D5F, commit `0c4d62b`); V24D5E4 frontend UI is now unblocked from backend side
- Players tab with per-player stat cards
- Empty state only for older matches or missing data

### V24D5E5 — Shot Map UI — Deferred
- Only after V24D3C attaches shot coordinates to events (separate backend phase)
- Shot map visualization
- Empty/hidden state until then

---

## 12. Backend Dependencies / Blockers

Before V24D5E3 (basic page) can ship:
1. **Need real example JSON** from backend with timeline populated — test with flags enabled
2. Confirm endpoint returns proper 404 when detail not persisted

Before V24D5E4 (player ratings):
1. Backend playerRatings persistence is now complete (**V24D5F committed as `0c4d62b`**) — `playerRatings` list is now populated in persisted V24 details
2. V24D5E4 frontend player ratings UI is now unblocked from backend side

Before V24D5E5 (shot map):
1. V24D3C must attach shot coordinates to events (separate approval)
2. `shotCoordinate` must be non-null on SHOT/GOAL events

**Dev/test enablement (requires separate ops action):**
```
app.simulation.league.use-v24-detailed-engine=true
app.simulation.v24.persist-detail=true
app.simulation.v24.expose-detail-api=true
```

---

## 13. Example API Response

```json
{
  "matchId": "match-r1-h1-a2",
  "careerId": "career-001",
  "seasonNumber": 1,
  "round": 1,
  "homeTeamId": "team-home-id",
  "awayTeamId": "team-away-id",
  "homeTeamName": "FC Barcelona",
  "awayTeamName": "Real Madrid",
  "homeGoals": 2,
  "awayGoals": 1,
  "homeXg": 1.80,
  "awayXg": 1.20,
  "homeShots": 12,
  "awayShots": 8,
  "homePossession": 55,
  "awayPossession": 45,
  "timeline": [
    {
      "minute": 12,
      "type": "GOAL",
      "teamId": "team-home-id",
      "playerId": "p-h-9",
      "playerName": "L. Yamal",
      "relatedPlayerId": "p-h-8",
      "relatedPlayerName": "Pedri",
      "xg": 0.35,
      "description": "GOAL — L. Yamal (assist: Pedri)"
    },
    {
      "minute": 28,
      "type": "YELLOW_CARD",
      "teamId": "team-away-id",
      "playerId": "p-a-4",
      "playerName": "R.vard",
      "xg": null,
      "description": "YELLOW CARD — R.vard"
    },
    {
      "minute": 45,
      "type": "SUBSTITUTION",
      "teamId": "team-home-id",
      "playerId": "p-h-6",
      "playerName": "Gavi",
      "relatedPlayerId": "p-h-15",
      "relatedPlayerName": "Ferran Torres",
      "xg": null,
      "description": "SUB — Gavi replaced by Ferran Torres"
    },
    {
      "minute": 67,
      "type": "GOAL",
      "teamId": "team-away-id",
      "playerId": "p-a-11",
      "playerName": "Vinicius Jr",
      "relatedPlayerId": "p-a-10",
      "relatedPlayerName": "Bellingham",
      "xg": 0.22,
      "description": "GOAL — Vinicius Jr (assist: Bellingham)"
    },
    {
      "minute": 81,
      "type": "GOAL",
      "teamId": "team-home-id",
      "playerId": "p-h-9",
      "playerName": "L. Yamal",
      "relatedPlayerId": null,
      "relatedPlayerName": null,
      "xg": 0.51,
      "description": "GOAL — L. Yamal (unassisted)"
    }
  ],
  "playerRatings": [],
  "schemaVersion": "1.0",
  "engineVersion": "V24",
  "createdAt": "2026-05-11T12:00:00Z"
}
```

**Note:** `playerRatings` may be empty for older matches (before V24D5F) but is populated for newly persisted V24 details after V24D5F. `shotCoordinate` is absent from all events until V24D3C. Both must be handled as empty/nullable.

---

## 14. Testing Strategy

### Frontend unit/integration tests (V24D5E2/E3)
- `getMatchDetail()` returns `MatchDetail` on 200
- `getMatchDetail()` returns `null` on 404
- `getMatchDetail()` throws on 500
- Renders loading skeleton while fetching
- Renders summary cards on success
- Renders 404 empty state when null
- Renders retry button on error
- Timeline events sorted by minute ascending
- Empty `playerRatings` shows empty state message
- Absent `shotCoordinate` hides shot map

### Backend regression gate (unchanged by V24D5E frontend work)
- Keep: 398 regression tests, 0 failures
- Full suite: 398 tests, 0 failures
- V24D5E adds **no backend tests** — no production code changes in frontend repo

---

## 15. Risks

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Detail unavailable for older matches | High | Low | Fallback to aggregate; users understand "not available" |
| Endpoint disabled in production | Low | Medium | 404 graceful fallback; user-friendly message |
| `playerRatings` empty for older matches or missing detail | Medium | Low | Empty state; V24D5E4 renders ratings once available; players tab hidden |
| `shotCoordinate` null blocks shot map | High | Low | Shots tab hidden; future update message |
| Large timeline payload (90+ events) | Low | Medium | Pagination/virtualization later; not in V24D5E3 scope |
| UI overcomplication | Medium | Medium | Progressive tabs; V24D5E3 = minimal viable detail |
| Detail/aggregate inconsistency | Low | Low | Aggregate drives standings; detail is enrichment only |
| Frontend accidentally requires V24 detail | Low | High | Compatibility rules + regression tests |

---

## 16. Recommended Next Step

**V24D5E3 — Read-only Match Detail Page** — COMPLETED in frontend repo commit `0ba2305`.

**V24D5E3 completion summary:**
- Route: `/careers/:careerId/matches/:matchId/detail` → `V24MatchDetailPageComponent`
- Uses `MatchDetailApiService.getMatchDetail(careerId, matchId)` from V24D5E2 (commit `050ab57`)
- Header with score, round, season, V24 badge
- Summary cards (xG, shots, possession, goals)
- Timeline (minute-sorted events), stats comparison table
- 404/null friendly unavailable state, 500/error retry state
- Empty playerRatings state, shot map deferred state
- Validation: `npx tsc --noEmit` OK, `npx ng build` BUILD SUCCESS
- No backend/API/Redis changes, no fixture/list UI modified

**What was explicitly NOT added (V24D5E3 only; fixture entry point added in V24D5E3B):**
- No player ratings full UI
- No shot map implementation
- No fixture/list entry point integration (added in V24D5E3B — dashboard fixture modal "📊 Detalle" link)
- No backend changes

**V24D5E2 delivered (frontend repo commit `050ab57`):**
- TypeScript interfaces: `MatchDetail`, `MatchEvent`, `MatchEventType`, `ShotCoordinate`, `ShotLocation`, `PlayerMatchRating`
- `MatchDetailApiService.getMatchDetail(careerId, matchId): Observable<MatchDetail | null>`
- 200 → MatchDetail; 404 → null; 500+ → propagates
- URL-encoded `careerId` and `matchId`
- Empty `playerRatings` list handled; nullable `shotCoordinate` and `relatedPlayerId/relatedPlayerName` handled

**Next recommended steps (in priority order):**
1. V24D5E4 — player ratings UI (now unblocked from backend side after V24D5F)
2. V24D5E5 only after V24D3C attaches shot coordinates (backend)
3. Frontend QA/polish pass on match detail page

---

## 17. Non-Goals

- No frontend implementation in this document
- No backend changes
- No API schema changes
- No Redis schema changes
- No production flag changes
- No `playerRatings` frontend UI implementation in this document; backend persistence is complete since V24D5F (V24D5E4 is unblocked)
- No `shotCoordinate` attachment (deferred to V24D3C)
- No career-state mutation
- No changes to existing fixture/standings UI
