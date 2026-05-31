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

type LoadingState = 'loading' | 'loaded' | 'error' | 'empty';

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

  // State
  players: PlayerSeasonStatsDto[] = [];
  metadata: PlayerSeasonStatsMetadata | null = null;
  warnings: PlayerSeasonStatsWarning[] = [];
  loadingState: LoadingState = 'loading';
  errorMessage: string = '';

  // Toolbar/pagination params
  params: PlayerStatsParams = { ...DEFAULT_STATS_PARAMS };

  ngOnInit(): void {
    if (this.careerId) {
      this.fetchStats();
    }
  }

  ngOnChanges(): void {
    if (this.careerId) {
      this.fetchStats();
    }
  }

  fetchStats(): void {
    this.loadingState = 'loading';
    this.errorMessage = '';

    // Use team-filtered endpoint when teamId is provided, otherwise use all-player endpoint
    const stats$ = this.teamId
      ? this.statsService.getTeamPlayerSeasonStats(this.careerId, this.season, this.teamId, this.params)
      : this.statsService.getPlayerSeasonStats(this.careerId, this.season, this.params);

    stats$.subscribe({
      next: (response: PlayerSeasonStatsResponse) => {
        this.handleResponse(response);
      },
      error: (error: StatsApiError) => {
        this.handleError(error);
      }
    });
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