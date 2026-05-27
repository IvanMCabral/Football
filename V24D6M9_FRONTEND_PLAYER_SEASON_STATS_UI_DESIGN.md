# V24D6M9 — Frontend Player Season Stats UI Design/Prompt

**Status:** M9 DESIGN — Implementation not authorized yet
**Branch:** `mvp-1-performance-cleanup` (backend complete: M7 `92669fb`, M8 `9be63b5`)
**Date:** 2026-05-26
**Backend:** 768 tests, 0 failures. API ready for frontend consumption.

---

## 1. Executive Summary

V24D6M7 (`92669fb`) delivered a complete player season stats API with pagination, metadata, and warnings. V24D6M9 is the frontend counterpart: a design/prompt document that specifies how the frontend should consume and display the stats API.

This document covers:
- MVP placement in the existing frontend
- Sortable table design with all 13 player stat columns
- Pagination and sorting controls
- Metadata and warning UX
- Empty/error state handling
- TypeScript interfaces
- Service/client design
- Component architecture
- Implementation phases
- Acceptance criteria

**Note:** Implementation is NOT authorized in this document. The design is ready for review and approval before M9B begins.

---

## 2. Recommended MVP Placement

### 2.1 Placement Decision

| Option | Location | Pros | Cons |
|--------|----------|------|------|
| A | Career squad page — "Season Stats" tab | Primary use case, natural fit | Requires tab infrastructure |
| B | Team detail page — section | Shows per-team stats | Doesn't cover career-level view |
| C | Player detail modal/card | Targeted view | Not useful for comparing players |
| D | Standalone leaderboard page | Full-screen stats | Extra navigation step |

**Recommendation: Option A — "Season Stats" tab on Career Squad Page**

Rationale: The primary use case is a manager viewing their squad's season performance. A tab on the squad page is the most natural entry point. Team-filtered view is accessible via a team dropdown within the same tab.

### 2.2 MVP Scope

MVP (M9C) delivers:
- Season Stats tab on squad page
- All-player stats table (default sort: goals desc)
- Sort by any of 12 fields
- Pagination with limit selector
- Metadata info bar
- Warning hints (non-blocking)
- Empty state for no data
- Feature-disabled 404 handled gracefully

Out of scope for MVP:
- Team filter dropdown (M9F)
- Expanded row details (M9F)
- Export functionality (future)
- Comparative charts (future)
- Animated transitions (future)

### 2.3 Existing Frontend Pages (Reference)

Based on the backend API structure, the frontend likely has:

- `/careers/{careerId}` — Career overview
  - Squad list (current players)
  - Season selector
- `/careers/{careerId}/teams/{teamId}` — Team detail
  - Roster
  - Tactics
  - Stats (proposed here)

The "Season Stats" tab should live in the career-level view, with a team filter dropdown to narrow results.

---

## 3. Main Stats Table Design

### 3.1 Column Specification

| Column | Field | Type | Sortable | Align |
|--------|-------|------|----------|-------|
| Player | `playerName` | string | ✅ (playerName) | left |
| Pos | `position` | string | ❌ | center |
| Apps | `appearances` | integer | ✅ | right |
| Starts | `starts` | integer | ✅ | right |
| Goals | `goals` | integer | ✅ | right |
| Assists | `assists` | integer | ✅ | right |
| Avg Rtg | `averageRating` | decimal (1 dp) | ✅ | right |
| Shots | `shots` | integer | ✅ | right |
| KP | `keyPasses` | integer | ✅ | right |
| YC | `yellowCards` | integer | ✅ | right |
| RC | `redCards` | integer | ✅ | right |
| Inj | `injuries` | integer | ✅ | right |
| Fouls | `fouls` | integer | ✅ | right |

**Deferred fields (do NOT show):** `minutesPlayed`, `shotsOnTarget`, `currentForm`, `formDeltaSeason`, `averageEnergy`, `lowestEnergy`

### 3.2 Table Behavior

- **Default sort:** `goals desc`
- **Clickable column headers:** clicking a sortable column header sorts by that field (toggle order between asc/desc)
- **Active sort indicator:** show ▲/▼ on the currently sorted column
- **Row hover:** highlight row on hover
- **Position badge:** show position abbreviation (ST, LW, RW, CAM, CM, CDM, CB, LB, RB, GK) with color coding:
  - Attack (ST/LW/RW/CAM): red tint
  - Midfield (CM/CDM): blue tint
  - Defense (CB/LB/RB): green tint
  - GK: yellow tint

### 3.3 Table Skeleton (ASCII Mockup)

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ SEASON STATS                        [Team: All ▾]              [50 ▾] players/page │
├─────────────────────────────────────────────────────────────────────────────────────┤
│ Stats updated through round 28 · Showing 50 of 87 players            ⚠ 2 warnings  │
├────────┬────┬─────┬───────┬──────┬────────┬──────┬─────┬─────┬────┬────┬────┬─────┤
│ Player│Pos │Apps │Starts │Goals │Assists │AvgRtg│Shots│ KP  │ YC │ RC │Inj │Fouls│
├────────┼────┼─────┼───────┼──────┼────────┼──────┼─────┼─────┼────┼────┼────┼─────┤
│ ▲ DESC │    │     │       │      │        │      │     │     │    │    │    │     │
├────────┼────┼─────┼───────┼──────┼────────┼──────┼─────┼─────┼────┼────┼────┼─────┤
│ Doe, J │ ST │  30 │    28 │   18 │      7 │  7.5 │  52 │  31 │  2 │  0 │  1 │  18 │
│ Smith, A│LW  │  29 │    25 │   12 │      9 │  7.3 │  44 │  28 │  1 │  0 │  0 │  12 │
│ ...    │    │     │       │      │        │      │     │     │    │    │    │     │
├────────┴────┴─────┴───────┴──────┴────────┴──────┴─────┴─────┴────┴────┴────┴─────┤
│ ◄ Previous    Page 1 of 2    Next ►                                               │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

### 3.4 Expanded Row (M9F — Post-MVP)

Not in MVP scope. Future expanded row content:
- `bestRating`
- `worstRating`
- `matchesMissedInjuredApprox`
- `matchesMissedSuspendedApprox`
- `lastUpdatedRound`

Implementation: Click row to expand OR separate "Details" button. Expand under the row with additional stat fields.

---

## 4. Sorting and Pagination UI

### 4.1 Sort Controls

**Option A — Clickable column headers (recommended):**
- Each sortable column header is clickable
- Click once: sort desc
- Click again: sort asc
- Third click: return to default (goals desc)
- Show current sort field and order indicator (▲/▼) on active column

**Option B — Sort dropdown + order toggle:**
- Dropdown: list of 12 sort fields
- Order toggle: asc/desc buttons
- Less discoverable than header clicks

**Recommendation: Option A** — Header clicks are the industry-standard pattern for sortable tables (e.g., GitHub, Jira, Excel). More intuitive and requires less UI surface area.

### 4.2 Pagination Controls

```
┌──────────────────────────────────────────────────────────────┐
│ ◄ Previous    Page 1 of 2    Next ►                         │
└──────────────────────────────────────────────────────────────┘
```

- **Previous/Next:** navigate offset by current limit
- **Page indicator:** "Page X of Y" where Y = ceil(totalPlayers / limit)
- **Disabled state:** "Previous" disabled on page 1; "Next" disabled on last page

### 4.3 Limit Selector

```
[50 ▾] players/page
```

Options: 25, 50, 100, 200 (powers of 2 for variety).

- Default: 50
- Changing limit resets offset to 0
- Show in toolbar next to team filter

### 4.4 "Showing X of Y Players" Bar

Displayed above the table, below the toolbar:

```
Stats updated through round 28 · Showing 50 of 87 players
```

- Use `metadata.lastUpdatedRound` for "round N"
- Use `metadata.returnedPlayers` for "X"
- Use `metadata.totalPlayers` for "Y"
- If `metadata.dataCompleteness === 'PARTIAL'`, add info icon: "Data incomplete — some rounds missing"

### 4.5 Default Fetch Behavior

```typescript
// Initial load defaults
const params: PlayerStatsParams = {
  limit: 50,
  offset: 0,
  sortBy: 'goals',
  order: 'desc',
};
```

---

## 5. Metadata Display

### 5.1 Info Bar Design

Above the table, in a subdued style (lighter background, smaller font):

```
Stats updated through round {lastUpdatedRound} · Showing {returnedPlayers} of {totalPlayers} players
```

### 5.2 Completeness Indicator

When `metadata.dataCompleteness !== 'COMPLETE'`:

| Completeness | Display | Icon |
|--------------|---------|------|
| `COMPLETE` | Hidden (no indicator needed) | — |
| `PARTIAL` | "Incomplete data — some rounds missing" | ℹ info icon |
| `EMPTY` | Handled by empty state (see Section 7) | — |
| `UNKNOWN` | "Data completeness unknown" | ℹ info icon |

### 5.3 Generated Timestamp

Use `metadata.generatedAt` for display if available:
- "Generated: 2026-05-26T15:30:00Z" — show in tooltip on the info bar

---

## 6. Warning Display

### 6.1 Warning UX Philosophy

Warnings should be:
- **Non-blocking** — never interrupt the user's workflow
- **Informative** — explain what the user is seeing without technical jargon
- **Subtle** — visible but not alarming

### 6.2 Warning Translation Map

| Backend Code | User-Facing Message | Style | Icon |
|-------------|---------------------|-------|------|
| `LARGE_LIMIT_CLAMPED` | "Showing maximum of 200 players per page" | Warning (yellow) | ⚠ |
| `APPROXIMATE_APPEARANCES` | "Appearance counts are approximate" | Info (blue) | ℹ |
| `APPROXIMATE_MATCHES_MISSED` | "Injury/suspension absences are estimates" | Info (blue) | ℹ |
| `NO_DETAIL_DATA` | (Handled by empty state — see Section 7) | — | — |

### 6.3 Warning Layout

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ SEASON STATS                        [Team: All ▾]              [50 ▾] players/page │
├─────────────────────────────────────────────────────────────────────────────────────┤
│ ⚠ Showing maximum of 200 players per page   ℹ Appearance counts are approximate     │
├─────────────────────────────────────────────────────────────────────────────────────┤
```

Warnings appear in a slim bar below the toolbar, above the table.

### 6.4 What NOT to Show

- Do NOT show `dataSource` (V24_DETAIL is meaningless to users)
- Do NOT show `versionHash` (technical field)
- Do NOT show any warning code directly to users
- Do NOT use red/error styling for any backend warning (none are errors in the UX sense)

---

## 7. Empty and Error States

### 7.1 Feature Disabled (404)

When `GET /api/.../player-stats` returns `404`:

```
┌─────────────────────────────────────────────────────────────┐
│                                                             │
│         ⚠  Season stats are not available yet               │
│                                                             │
│    Season stats require detailed match recording to be       │
│    enabled. Play matches with V24 detail enabled to        │
│    start collecting data.                                   │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

No retry button. No error code. Explain what the user needs to do.

### 7.2 No Data (200, empty playerStats)

When API returns `200` with `playerStats: []` (no detail data for this career/season):

```
┌─────────────────────────────────────────────────────────────┐
│                                                             │
│         📊  No season stats available yet                   │
│                                                             │
│    Season stats will appear here once you play matches      │
│    with V24 detail recording enabled.                       │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

No spinner. No loading state after data loads (empty is a valid state).

### 7.3 Single Player Not Found (404)

When `GET /api/.../players/{playerId}/stats` returns `404`:

```
┌─────────────────────────────────────────────────────────────┐
│                                                             │
│         Player stats not found                              │
│                                                             │
│    This player has no recorded stats for this season.        │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### 7.4 API Error (5xx)

When API returns an error:

```
┌─────────────────────────────────────────────────────────────┐
│                                                             │
│         ⚠  Unable to load season stats                     │
│                                                             │
│    Something went wrong loading the stats.                  │
│    [Try Again]                                              │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

Include a "Try Again" button. Do not show technical error details.

### 7.5 Invalid Sort/Offset (400)

Frontend should never send invalid params (validate before request). If a 400 is received:

```
┌─────────────────────────────────────────────────────────────┐
│         ⚠  Invalid request — please refresh                 │
└─────────────────────────────────────────────────────────────┘
```

Refresh resets to defaults.

---

## 8. TypeScript Interfaces

### 8.1 Core Response Type

```typescript
// Main response envelope
interface PlayerSeasonStatsResponse {
  careerId: string;
  season: number;
  playerStats: PlayerSeasonStatsDto[];
  totals: {
    totalGoals: number;
    totalAssists: number;
    totalAppearances: number;
    averageRating: number;
  };
  incomplete: boolean;
  message: string;
  metadata: PlayerSeasonStatsMetadata;
  warnings: PlayerSeasonStatsWarning[];
}
```

### 8.2 Player DTO

```typescript
// Individual player stat record
interface PlayerSeasonStatsDto {
  careerId: string;
  season: number;
  teamId: string;
  playerId: string;
  playerName: string;
  position: string;
  appearances: number;
  starts: number;
  goals: number;
  assists: number;
  keyPasses: number;
  shots: number;
  yellowCards: number;
  redCards: number;
  injuries: number;
  fouls: number;
  matchesMissedInjuredApprox: number;
  matchesMissedSuspendedApprox: number;
  averageRating: number;
  bestRating: number;
  worstRating: number;
  lastUpdatedRound: number;
}
```

### 8.3 Metadata

```typescript
// Response metadata
interface PlayerSeasonStatsMetadata {
  limit: number;
  offset: number;
  hasMore: boolean;
  totalPlayers: number;
  returnedPlayers: number;
  totalMatchesProcessed: number;
  lastUpdatedRound: number;
  dataSource: 'V24_DETAIL'; // always this value
  dataCompleteness: 'COMPLETE' | 'PARTIAL' | 'EMPTY' | 'UNKNOWN';
  generatedAt: string; // ISO-8601
  versionHash: string;
}
```

### 8.4 Warning

```typescript
// Warning object
interface PlayerSeasonStatsWarning {
  code: string; // e.g., 'LARGE_LIMIT_CLAMPED'
  message: string; // e.g., 'Requested limit > 200 was clamped to 200'
  field: string | null;
}
```

### 8.5 Query Parameters

```typescript
// Sort order
type SortOrder = 'asc' | 'desc';

// Sortable fields
type SortField =
  | 'goals'
  | 'assists'
  | 'averageRating'
  | 'appearances'
  | 'starts'
  | 'shots'
  | 'keyPasses'
  | 'yellowCards'
  | 'redCards'
  | 'injuries'
  | 'fouls'
  | 'playerName';

// API query params
interface PlayerStatsParams {
  limit?: number;    // default 50, max 200
  offset?: number;   // default 0
  sortBy?: SortField; // default 'goals'
  order?: SortOrder;  // default 'desc'
}

// Limit options
const LIMIT_OPTIONS = [25, 50, 100, 200] as const;
type LimitOption = typeof LIMIT_OPTIONS[number];
```

### 8.6 Utility Types

```typescript
// For toolbar state
interface StatsToolbarState {
  teamId?: string;    // undefined = all teams
  limit: LimitOption;
  sortBy: SortField;
  order: SortOrder;
  page: number;       // computed from offset / limit
}

// For pagination
interface PaginationState {
  offset: number;
  limit: number;
  hasMore: boolean;
  totalPlayers: number;
  returnedPlayers: number;
}

// For warnings display
interface WarningDisplay {
  code: string;
  message: string;  // translated from backend code
  type: 'info' | 'warning';
}
```

---

## 9. Service/Client Design

### 9.1 API Client Module

```typescript
// src/services/playerSeasonStatsService.ts

import { PlayerSeasonStatsResponse } from '@/types/playerSeasonStats';

const API_BASE = '/api/careers';

export interface GetPlayerStatsOptions {
  careerId: string;
  season: number;
  teamId?: string;
  limit?: number;
  offset?: number;
  sortBy?: SortField;
  order?: SortOrder;
}

// GET /api/careers/{careerId}/seasons/{season}/player-stats
export async function getPlayerSeasonStats(
  careerId: string,
  season: number,
  options: Omit<GetPlayerStatsOptions, 'careerId' | 'season'> = {}
): Promise<PlayerSeasonStatsResponse> {
  const params = new URLSearchParams();
  if (options.limit !== undefined) params.set('limit', String(options.limit));
  if (options.offset !== undefined) params.set('offset', String(options.offset));
  if (options.sortBy) params.set('sortBy', options.sortBy);
  if (options.order) params.set('order', options.order);

  const url = `${API_BASE}/${careerId}/seasons/${season}/player-stats?${params}`;
  const response = await fetch(url);

  if (!response.ok) {
    throw new StatsApiError(response.status, await response.text());
  }

  return response.json();
}

// GET /api/careers/{careerId}/seasons/{season}/teams/{teamId}/player-stats
export async function getTeamPlayerSeasonStats(
  careerId: string,
  season: number,
  teamId: string,
  options: Omit<GetPlayerStatsOptions, 'careerId' | 'season' | 'teamId'> = {}
): Promise<PlayerSeasonStatsResponse> {
  const params = new URLSearchParams();
  if (options.limit !== undefined) params.set('limit', String(options.limit));
  if (options.offset !== undefined) params.set('offset', String(options.offset));
  if (options.sortBy) params.set('sortBy', options.sortBy);
  if (options.order) params.set('order', options.order);

  const url = `${API_BASE}/${careerId}/seasons/${season}/teams/${teamId}/player-stats?${params}`;
  const response = await fetch(url);

  if (!response.ok) {
    throw new StatsApiError(response.status, await response.text());
  }

  return response.json();
}

// GET /api/careers/{careerId}/seasons/{season}/players/{playerId}/stats
export async function getSinglePlayerSeasonStats(
  careerId: string,
  season: number,
  playerId: string
): Promise<PlayerSeasonStatsResponse> {
  const url = `${API_BASE}/${careerId}/seasons/${season}/players/${playerId}/stats`;
  const response = await fetch(url);

  if (!response.ok) {
    throw new StatsApiError(response.status, await response.text());
  }

  return response.json();
}

// Error class
export class StatsApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly body: string
  ) {
    super(`Stats API error: ${status}`);
    this.name = 'StatsApiError';
  }
}
```

### 9.2 Usage Example

```typescript
// In a React component or store

async function loadStats(careerId: string, season: number) {
  try {
    const data = await getPlayerSeasonStats(careerId, season, {
      limit: 50,
      offset: 0,
      sortBy: 'goals',
      order: 'desc',
    });

    setPlayers(data.playerStats);
    setMetadata(data.metadata);
    setWarnings(translateWarnings(data.warnings));
  } catch (err) {
    if (err instanceof StatsApiError && err.status === 404) {
      setFeatureDisabled(true);
    } else {
      setApiError(true);
    }
  }
}
```

### 9.3 Warning Translation Function

```typescript
// src/utils/warningTranslator.ts

import { PlayerSeasonStatsWarning } from '@/types/playerSeasonStats';

export function translateWarning(warning: PlayerSeasonStatsWarning): WarningDisplay {
  switch (warning.code) {
    case 'LARGE_LIMIT_CLAMPED':
      return {
        code: warning.code,
        message: 'Showing maximum of 200 players per page',
        type: 'warning',
      };
    case 'APPROXIMATE_APPEARANCES':
      return {
        code: warning.code,
        message: 'Appearance counts are approximate',
        type: 'info',
      };
    case 'APPROXIMATE_MATCHES_MISSED':
      return {
        code: warning.code,
        message: 'Injury/suspension absences are estimates',
        type: 'info',
      };
    case 'NO_DETAIL_DATA':
      // Handled by empty state, not displayed as warning
      return {
        code: warning.code,
        message: 'No detail data available',
        type: 'info',
      };
    default:
      return {
        code: warning.code,
        message: warning.message,
        type: 'info',
      };
  }
}

export function translateWarnings(warnings: PlayerSeasonStatsWarning[]): WarningDisplay[] {
  return warnings
    .filter(w => w.code !== 'NO_DETAIL_DATA') // NO_DETAIL_DATA handled by empty state
    .map(translateWarning);
}
```

---

## 10. Component Design

### 10.1 Component Hierarchy

```
SeasonStatsTab
├── PlayerSeasonStatsToolbar
│   ├── TeamFilterDropdown (M9F — post-MVP)
│   └── LimitSelector
├── PlayerSeasonStatsInfoBar
├── PlayerSeasonStatsWarnings
├── PlayerSeasonStatsTable
│   └── PlayerSeasonStatsRow[]
└── PlayerSeasonStatsPagination
```

### 10.2 Component Specifications

#### `SeasonStatsTab`

**Purpose:** Container component that manages all stats state and data fetching.

**Props:**
```typescript
interface SeasonStatsTabProps {
  careerId: string;
  season: number;
}
```

**State:**
```typescript
interface SeasonStatsTabState {
  players: PlayerSeasonStatsDto[];
  metadata: PlayerSeasonStatsMetadata | null;
  warnings: WarningDisplay[];
  loading: boolean;
  error: 'feature-disabled' | 'empty' | 'api-error' | null;
  toolbar: StatsToolbarState;
}
```

**Behavior:**
- Fetch on mount with default params (limit=50, offset=0, sortBy=goals, order=desc)
- Refetch when toolbar state changes (sort, limit, page)
- Handle all error states and display appropriate UI

---

#### `PlayerSeasonStatsToolbar`

**Purpose:** Sort and filter controls above the table.

**Props:**
```typescript
interface PlayerSeasonStatsToolbarProps {
  teamId: string | undefined;    // M9F: team filter
  limit: LimitOption;
  onLimitChange: (limit: LimitOption) => void;
  onTeamChange: (teamId: string | undefined) => void; // M9F
}
```

**UI:**
- Left: "SEASON STATS" title
- Right: Team dropdown (M9F) + limit selector "[50 ▾] players/page"

---

#### `PlayerSeasonStatsInfoBar`

**Purpose:** Display metadata summary above the table.

**Props:**
```typescript
interface PlayerSeasonStatsInfoBarProps {
  metadata: PlayerSeasonStatsMetadata;
}
```

**Display:**
- "Stats updated through round {lastUpdatedRound} · Showing {returnedPlayers} of {totalPlayers} players"
- If `dataCompleteness === 'PARTIAL'`: add info icon with tooltip
- Subdued background, smaller font than table

---

#### `PlayerSeasonStatsWarnings`

**Purpose:** Display non-blocking warning messages.

**Props:**
```typescript
interface PlayerSeasonStatsWarningsProps {
  warnings: WarningDisplay[];
}
```

**Display:**
- Slim bar below toolbar, above table
- Each warning: icon + message, inline
- Warning type: yellow ⚠ icon; Info type: blue ℹ icon
- Filter out `NO_DETAIL_DATA` (handled by empty state)

---

#### `PlayerSeasonStatsTable`

**Purpose:** Sortable, paginated player stats table.

**Props:**
```typescript
interface PlayerSeasonStatsTableProps {
  players: PlayerSeasonStatsDto[];
  sortBy: SortField;
  order: SortOrder;
  onSortChange: (field: SortField) => void;
}
```

**Behavior:**
- Renders all columns per Section 3.1
- Clickable headers for sortable columns
- Active sort column shows ▲/▼ indicator
- Row hover highlight

---

#### `PlayerSeasonStatsPagination`

**Purpose:** Navigate between pages.

**Props:**
```typescript
interface PlayerSeasonStatsPaginationProps {
  offset: number;
  limit: number;
  hasMore: boolean;
  totalPlayers: number;
  onPrevious: () => void;
  onNext: () => void;
}
```

**Display:**
- "◄ Previous    Page X of Y    Next ►"
- Previous disabled on offset=0
- Next disabled when !hasMore

---

#### `PlayerSeasonStatsEmptyState`

**Purpose:** Display when no stats data is available.

**Props:**
```typescript
interface PlayerSeasonStatsEmptyStateProps {
  reason: 'feature-disabled' | 'no-data' | 'player-not-found' | 'api-error';
}
```

**Display:** Per Section 7 — appropriate message + icon for each reason.

---

#### `PlayerSeasonStatsExpandedRow` (M9F — post-MVP)

**Purpose:** Show additional per-player details.

**Props:**
```typescript
interface PlayerSeasonStatsExpandedRowProps {
  player: PlayerSeasonStatsDto;
}
```

**Display:** bestRating, worstRating, matchesMissedInjuredApprox, matchesMissedSuspendedApprox, lastUpdatedRound

---

## 11. Implementation Phases

### M9A — Design/Prompt (This Document)

- Complete frontend design specification
- TypeScript interfaces
- Component hierarchy
- API client design
- User-facing copy for all states
- This document serves as the implementation prompt for M9B-M9F

**Deliverable:** `V24D6M9_FRONTEND_PLAYER_SEASON_STATS_UI_DESIGN.md`

**Status:** ✅ Complete (this document)

---

### M9B — API Client + TypeScript Types

- Create `src/types/playerSeasonStats.ts` with all interfaces
- Create `src/services/playerSeasonStatsService.ts` with API client
- Create `src/utils/warningTranslator.ts`
- Create unit tests for warning translator
- Test API client with mock server responses

**Deliverable:** TypeScript types + API client, tested

**Entry criteria:** Backend M7/M8 committed and deployed (or available via local backend)

---

### M9C — Table UI MVP

- Implement `SeasonStatsTab` container
- Implement `PlayerSeasonStatsTable` with sortable columns
- Implement `PlayerSeasonStatsToolbar` with limit selector
- Implement `PlayerSeasonStatsPagination`
- Wire up default fetch (limit=50, offset=0, sortBy=goals, order=desc)
- Default sort indicator on "Goals" column

**Deliverable:** Working stats table with sorting and pagination

**Entry criteria:** M9B complete

---

### M9D — Warnings and Empty States

- Implement `PlayerSeasonStatsInfoBar` (metadata display)
- Implement `PlayerSeasonStatsWarnings` (translated warnings)
- Implement `PlayerSeasonStatsEmptyState` for all 4 reasons
- Wire up warning translation
- Handle 404 for feature disabled

**Deliverable:** All warning and empty states working

**Entry criteria:** M9C complete

---

### M9E — Integration Test / Manual QA

- Manual QA: load stats for a real career with data
- Manual QA: pagination through all pages
- Manual QA: all sort options work
- Manual QA: empty state for new career with no detail data
- Manual QA: feature-disabled state (if testable)
- Verify no console errors in any state

**Deliverable:** QA sign-off on all acceptance criteria

**Entry criteria:** M9D complete

---

### M9F — Polish (Post-MVP)

- Team filter dropdown (M9B mention: team-filtered endpoint exists)
- Expanded row details (bestRating, worstRating, etc.)
- Position color badges
- Smooth transitions when sorting/paginating
- Loading skeleton state (optional — only if time allows)

**Deliverable:** Polished UI with enhanced features

**Entry criteria:** M9E complete and signed off

---

## 12. Acceptance Criteria

### 12.1 Functional Criteria

| # | Criterion | Test Method |
|---|-----------|-------------|
| AC1 | Stats tab loads and displays players for a career with data | Manual: navigate to career → Stats tab → see players |
| AC2 | Default fetch uses limit=50, offset=0, sortBy=goals, order=desc | Network tab: verify first request params |
| AC3 | Clicking column header sorts by that field (toggle order) | Manual: click "Assists" header → verify sort changes |
| AC4 | Pagination uses offset correctly (page 2 = offset 50 for limit 50) | Network tab: verify offset on page 2 request |
| AC5 | Empty state shown when no stats data available | Manual: create career with no detail data → Stats tab → empty state |
| AC6 | Feature-disabled 404 shows user-friendly message | Manual: disable V24 detail → Stats tab → "not available" message |
| AC7 | Warnings rendered as user-friendly hints (not technical codes) | Manual: request limit=500 → see "Showing maximum of 200" not "LARGE_LIMIT_CLAMPED" |
| AC8 | No crash when metadata/warnings fields are missing | Code review: all metadata/warnings accesses are optional or guarded |

### 12.2 Non-Functional Criteria

| # | Criterion | Test Method |
|---|-----------|-------------|
| AC9 | No deferred fields shown in table | Code review: minutesPlayed, shotsOnTarget, etc. not in table columns |
| AC10 | Sort toggles between asc/desc correctly | Manual: click same column 3 times → desc → asc → desc |
| AC11 | Limit change resets offset to 0 | Manual: go to page 2 → change limit → verify offset resets |
| AC12 | "Showing X of Y" updates correctly after sort/page | Manual: sort by assists → verify counts still correct |

---

## 13. Design Decision Summary

| Decision | Choice | Rationale |
|----------|--------|-----------|
| MVP placement | Career squad page — "Season Stats" tab | Primary use case, natural entry point |
| Sort UI | Clickable column headers | Industry standard, intuitive |
| Pagination UI | Previous/Next + Page X of Y + limit selector | Familiar, minimal UI |
| Default sort | goals desc | Most useful for initial view |
| Warning style | Non-blocking slim bar, user-friendly copy | Visible but not alarming |
| Empty state | Friendly message + explanation | Actionable, not technical |
| Limit options | 25, 50, 100, 200 | Powers of 2 for variety |
| Deferred fields | Hidden from UI | Clearly not available; no broken UX |

---

## 14. Open Questions for Frontend Team

1. **Tab infrastructure:** Does the squad page already have a tab component? If so, which library/pattern is used?
2. **State management:** React Query / TanStack Query for API fetching, or custom hooks?
3. **Styling:** CSS modules, styled-components, Tailwind? Existing pattern in frontend?
4. **Team filter:** Is team-filtered view needed in MVP, or can it wait for M9F?
5. **Position badges:** Is color-coded position display consistent with existing UI?
6. **Loading state:** Is a skeleton loader expected, or is a simple spinner acceptable?

---

## 15. Related Documents

- [V24D6M6 — Player Season Stats API Polish Design](V24D6M6_PLAYER_SEASON_STATS_API_POLISH_DESIGN.md) — Backend design, full API reference
- [V24D6M7 — Player Season Stats API Pagination/Metadata/Polish Summary](V24D6M_PLAYER_SEASON_STATS_DESIGN.md) — Section 20, API implementation summary
- [V23_ENGINE_EVOLUTION_ROADMAP.md](V23_ENGINE_EVOLUTION_ROADMAP.md) — Project status and context

---

## 16. Next Steps

1. **Review this design** with frontend team
2. **Resolve open questions** (Section 14)
3. **Authorize M9B** — API client + TypeScript types implementation
4. After M9B: authorize M9C → M9D → M9E → M9F sequentially

---

*V24D6M9 design complete. Implementation not authorized until design is approved.*