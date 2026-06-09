import { Component, Input, OnInit, OnChanges, inject, ChangeDetectionStrategy, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { PlayerSeasonStatsService } from '../../services/player-season-stats.service';
import {
  PlayerSeasonStatsResponse,
  PlayerSeasonStatsDto,
  PlayerSeasonStatsMetadata,
  PlayerSeasonStatsWarning,
  PlayerStatsParams,
  StatsApiError,
  SortField,
  SortOrder,
  LimitOption,
  DEFAULT_STATS_PARAMS
} from '../../models/player-season-stats.model';
import { PlayerSeasonStatsTableComponent } from '../player-season-stats-table/player-season-stats-table.component';
import { PlayerSeasonStatsToolbarComponent } from '../player-season-stats-toolbar/player-season-stats-toolbar.component';
import { PlayerSeasonStatsPaginationComponent } from '../player-season-stats-pagination/player-season-stats-pagination.component';
import { PlayerSeasonStatsEmptyStateComponent, EmptyStateReason } from '../player-season-stats-empty-state/player-season-stats-empty-state.component';
import { PlayerSeasonStatsInfoBarComponent } from '../player-season-stats-info-bar/player-season-stats-info-bar.component';
import { PlayerSeasonStatsWarningsComponent } from '../player-season-stats-warnings/player-season-stats-warnings.component';

type LoadingState = 'loading' | 'loaded' | 'error' | 'empty' | 'waiting-team';

/**
 * Scope of the stats tab.
 *
 * - 'team'   : caller REQUIRES team-scoped data. The component will ONLY call
 *              the team endpoint (/teams/{teamId}/player-stats). If `teamId`
 *              is empty, the component stays in a 'waiting-team' state and
 *              NEVER falls back to the all-player endpoint.
 *
 * - 'global' : default. The component may call either endpoint based on
 *              whether `teamId` is provided (backwards compatible).
 */
export type StatsScope = 'team' | 'global';

@Component({
  selector: 'app-season-stats-tab',
  standalone: true,
  imports: [
    CommonModule,
    PlayerSeasonStatsTableComponent,
    PlayerSeasonStatsToolbarComponent,
    PlayerSeasonStatsPaginationComponent,
    PlayerSeasonStatsEmptyStateComponent,
    PlayerSeasonStatsInfoBarComponent,
    PlayerSeasonStatsWarningsComponent
  ],
  templateUrl: './season-stats-tab.component.html',
  styleUrls: ['./season-stats-tab.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class SeasonStatsTabComponent implements OnInit, OnChanges {
  private statsService = inject(PlayerSeasonStatsService);
  private cdr = inject(ChangeDetectorRef);

  /** Career ID input */
  @Input() careerId: string = '';
  /** Season number input */
  @Input() season: number = 1;
  /** Team ID for team-filtered stats (optional - when provided, calls /teams/{teamId}/player-stats) */
  @Input() teamId: string = '';
  /**
   * Stats scope. 'team' forces team-scoped endpoint and disables all-player
   * fallback. 'global' preserves legacy behavior (default).
   */
  @Input() scope: StatsScope = 'global';

  // State
  players: PlayerSeasonStatsDto[] = [];
  metadata: PlayerSeasonStatsMetadata | null = null;
  warnings: PlayerSeasonStatsWarning[] = [];
  loadingState: LoadingState = 'loading';
  errorMessage: string = '';

  // Toolbar/pagination params
  params: PlayerStatsParams = { ...DEFAULT_STATS_PARAMS };

  // Track previous teamId to detect when it changes from empty to populated
  private previousTeamId: string = '';

  ngOnInit(): void {
    this.previousTeamId = this.teamId;
    console.log('[STATS-TAB] ngOnInit careerId=' + this.careerId + ', teamId=' + this.teamId + ', scope=' + this.scope);
    if (this.careerId) {
      this.fetchStats();
    }
  }

  ngOnChanges(changes: { careerId?: any; season?: any; teamId?: any; scope?: any }): void {
    console.log('[STATS-TAB] ngOnChanges careerId=' + this.careerId + ', teamId=' + this.teamId + ', scope=' + this.scope + ', previousTeamId=' + this.previousTeamId);
    // Detect when teamId changes from empty to non-empty → trigger refetch
    if (changes.teamId && this.teamId && !this.previousTeamId && this.careerId) {
      console.log('[STATS-TAB] teamId populated from empty → refetch with team endpoint');
      this.previousTeamId = this.teamId;
      this.fetchStats();
    } else if (this.careerId) {
      this.fetchStats();
    }
    if (changes.teamId) {
      this.previousTeamId = this.teamId;
    }
    // If scope flips to 'team' but teamId is still empty, settle into waiting-team
    // without firing a fetch. (This can happen if the parent re-renders.)
    if (changes.scope && this.scope === 'team' && !this.teamId) {
      this.loadingState = 'waiting-team';
      this.players = [];
      this.metadata = null;
      this.warnings = [];
      this.cdr.markForCheck();
    }
  }

  fetchStats(): void {
    // Guard 1: careerId is the only hard requirement for any fetch.
    if (!this.careerId) {
      console.log('[STATS-TAB] fetchStats skipped: careerId is empty');
      return;
    }

    // Guard 2: team-scope contract — when scope='team', teamId is mandatory.
    // We must NOT call the all-player endpoint, because that would show
    // 132 players from every team instead of the 11 from the user's squad.
    if (this.scope === 'team' && !this.teamId) {
      console.log('[STATS-TAB] scope=team but teamId is empty — waiting (no fetch)');
      this.loadingState = 'waiting-team';
      this.errorMessage = '';
      this.players = [];
      this.metadata = null;
      this.warnings = [];
      this.cdr.markForCheck();
      return;
    }

    this.loadingState = 'loading';
    this.errorMessage = '';

    const endpoint = this.teamId ? 'team' : 'ALL-PLAYER';
    console.log('[STATS-TAB] fetchStats endpoint=' + endpoint + ' scope=' + this.scope + ' careerId=' + this.careerId + ' teamId=' + this.teamId);

    // Team scope ALWAYS uses the team endpoint; global scope keeps the
    // legacy precedence (teamId wins, all-player as fallback).
    const stats$ = this.resolveEndpoint();

    stats$.subscribe({
      next: (response: PlayerSeasonStatsResponse) => {
        this.handleResponse(response);
      },
      error: (error: StatsApiError) => {
        this.handleError(error);
      }
    });
  }

  /**
   * Decide which service method to call based on `scope` and `teamId`.
   *
   * - scope='team'   + teamId       → team endpoint (only legal combo)
   * - scope='team'   + no teamId    → unreachable (Guard 2 returns early)
   * - scope='global' + teamId       → team endpoint (precedence)
   * - scope='global' + no teamId    → all-player endpoint
   */
  private resolveEndpoint() {
    if (this.teamId) {
      return this.statsService.getTeamPlayerSeasonStats(
        this.careerId, this.season, this.teamId, this.params
      );
    }
    return this.statsService.getPlayerSeasonStats(
      this.careerId, this.season, this.params
    );
  }

  private handleResponse(response: PlayerSeasonStatsResponse): void {
    this.players = response.playerStats || [];
    this.metadata = response.metadata || null;
    this.warnings = response.warnings || [];

    if (this.players.length === 0) {
      this.loadingState = 'empty';
    } else {
      this.loadingState = 'loaded';
    }

    this.cdr.markForCheck();
  }

  private handleError(error: StatsApiError): void {
    if (error.status === 404) {
      this.loadingState = 'empty';
    } else {
      this.loadingState = 'error';
      this.errorMessage = error.body || 'An error occurred';
    }
    this.cdr.markForCheck();
  }

  onLimitChange(newLimit: LimitOption): void {
    this.params = {
      ...this.params,
      limit: newLimit,
      offset: 0 // Reset offset when limit changes
    };
    this.fetchStats();
  }

  onSortChange(sort: { sortBy: SortField; order: SortOrder }): void {
    this.params = {
      ...this.params,
      sortBy: sort.sortBy,
      order: sort.order,
      offset: 0 // Reset offset when sort changes
    };
    this.fetchStats();
  }

  onPrevious(): void {
    const newOffset = Math.max(0, this.params.offset! - this.params.limit!);
    this.params = {
      ...this.params,
      offset: newOffset
    };
    this.fetchStats();
  }

  onNext(): void {
    const newOffset = (this.params.offset || 0) + this.params.limit!;
    this.params = {
      ...this.params,
      offset: newOffset
    };
    this.fetchStats();
  }

  onRetry(): void {
    this.fetchStats();
  }

  get offset(): number {
    return this.params.offset || 0;
  }

  get limit(): number {
    return this.params.limit || 50;
  }

  get hasMore(): boolean {
    return this.metadata?.hasMore || false;
  }

  get totalPlayers(): number {
    return this.metadata?.totalPlayers || this.players.length;
  }

  get returnedPlayers(): number {
    return this.metadata?.returnedPlayers || this.players.length;
  }

  get sortBy(): SortField {
    return (this.params.sortBy as SortField) || 'goals';
  }

  get order(): SortOrder {
    return (this.params.order as SortOrder) || 'desc';
  }

  get currentLimit(): LimitOption {
    return (this.params.limit as LimitOption) || 50;
  }

  get emptyReason(): EmptyStateReason {
    if (this.loadingState === 'error') {
      return 'api-error';
    }
    if (this.metadata?.dataCompleteness === 'EMPTY') {
      return 'feature-disabled';
    }
    return 'no-data';
  }

  get showRetry(): boolean {
    return this.loadingState === 'error';
  }
}
