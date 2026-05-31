/**
 * Player Season Stats - TypeScript Type Definitions
 *
 * Type definitions for the V24D6M7 Player Season Stats API.
 * These types mirror the backend response shapes exactly.
 *
 * Deferred fields (NOT included - not available in backend):
 * - minutesPlayed
 * - shotsOnTarget
 * - currentForm
 * - formDeltaSeason
 * - averageEnergy
 * - lowestEnergy
 */

// =============================================================================
// Enums and Unions
// =============================================================================

/** Sortable fields for the player stats API */
export type SortField =
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

/** Sort order */
export type SortOrder = 'asc' | 'desc';

/** Data completeness indicator */
export type DataCompleteness = 'COMPLETE' | 'PARTIAL' | 'EMPTY' | 'UNKNOWN';

/** Warning severity type for UI display */
export type WarningType = 'info' | 'warning';

/** Limit options for pagination */
export const LIMIT_OPTIONS = [25, 50, 100, 200] as const;
export type LimitOption = typeof LIMIT_OPTIONS[number];

// =============================================================================
// Core DTOs
// =============================================================================

/**
 * Individual player stat record.
 * Each player in the playerStats array has these fields.
 */
export interface PlayerSeasonStatsDto {
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

/**
 * Totals aggregate for the entire response.
 */
export interface PlayerSeasonStatsTotals {
  totalGoals: number;
  totalAssists: number;
  totalAppearances: number;
  averageRating: number;
}

/**
 * Response metadata block.
 * Contains pagination info and data provenance.
 */
export interface PlayerSeasonStatsMetadata {
  limit: number;
  offset: number;
  hasMore: boolean;
  totalPlayers: number;
  returnedPlayers: number;
  totalMatchesProcessed: number;
  lastUpdatedRound: number;
  dataSource: 'V24_DETAIL';
  dataCompleteness: DataCompleteness;
  generatedAt: string; // ISO-8601 timestamp
  versionHash: string;
}

/**
 * Raw warning object from the backend API.
 */
export interface PlayerSeasonStatsWarning {
  code: string;
  message: string;
  field: string | null;
}

/**
 * Main response envelope for the player season stats API.
 * This is the root type returned by all three endpoints.
 */
export interface PlayerSeasonStatsResponse {
  careerId: string;
  season: number;
  playerStats: PlayerSeasonStatsDto[];
  totals: PlayerSeasonStatsTotals;
  incomplete: boolean;
  message: string;
  /** Optional for backward compatibility - metadata may not exist on older responses */
  metadata?: PlayerSeasonStatsMetadata;
  /** Optional for backward compatibility - warnings may not exist on older responses */
  warnings?: PlayerSeasonStatsWarning[];
}

// =============================================================================
// API Query Parameters
// =============================================================================

/**
 * Query parameters for the all-players and team-filtered endpoints.
 * All fields are optional - backend defaults apply when not specified.
 */
export interface PlayerStatsParams {
  /** Max player records per response. Default: 50, Max: 200 */
  limit?: number;
  /** Skip first N players. Default: 0 */
  offset?: number;
  /** Sort field. Default: 'goals' */
  sortBy?: SortField;
  /** Sort order. Default: 'desc' */
  order?: SortOrder;
}

/**
 * Query parameters for the team-filtered endpoint.
 */
export interface TeamPlayerStatsParams extends PlayerStatsParams {
  teamId: string;
}

// =============================================================================
// UI Display Types
// =============================================================================

/**
 * Translated warning for UI display.
 * Converts backend warning codes to user-friendly messages.
 */
export interface WarningDisplay {
  code: string;
  message: string;
  type: WarningType;
}

/**
 * Toolbar state for managing sort/pagination UI.
 */
export interface StatsToolbarState {
  teamId?: string;
  limit: LimitOption;
  sortBy: SortField;
  order: SortOrder;
  /** Current page (1-indexed), computed from offset / limit */
  page: number;
}

/**
 * Pagination state derived from metadata.
 */
export interface PaginationState {
  offset: number;
  limit: number;
  hasMore: boolean;
  totalPlayers: number;
  returnedPlayers: number;
  totalPages: number;
  currentPage: number;
}

// =============================================================================
// Error Types
// =============================================================================

/**
 * Error thrown by the player season stats API client.
 * Preserves the HTTP status code for caller handling.
 */
export class StatsApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly body: string
  ) {
    super(`Stats API error: ${status}`);
    this.name = 'StatsApiError';
  }
}

// =============================================================================
// Utility Functions
// =============================================================================

/**
 * Default query parameters for initial fetch.
 */
export const DEFAULT_STATS_PARAMS: Required<PlayerStatsParams> = {
  limit: 25,
  offset: 0,
  sortBy: 'goals',
  order: 'desc',
};

/**
 * Default sort field and order.
 */
export const DEFAULT_SORT: Pick<PlayerStatsParams, 'sortBy' | 'order'> = {
  sortBy: 'goals',
  order: 'desc',
};