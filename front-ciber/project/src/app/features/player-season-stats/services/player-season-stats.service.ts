import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  PlayerSeasonStatsResponse,
  PlayerStatsParams,
  StatsApiError
} from '../models/player-season-stats.model';

/**
 * Player Season Stats API Service
 *
 * Service for fetching player season stats from the V24D6M7 API.
 * Follows the same patterns as CareerService and other existing services.
 *
 * Backend endpoints:
 * - GET /api/careers/{careerId}/seasons/{season}/player-stats
 * - GET /api/careers/{careerId}/seasons/{season}/teams/{teamId}/player-stats
 * - GET /api/careers/{careerId}/seasons/{season}/players/{playerId}/stats
 *
 * Note: The backend uses /api/careers (plural) which differs from
 * the /api/v1/career pattern used by CareerService. This service
 * uses the correct /api/careers base path.
 */
@Injectable({
  providedIn: 'root'
})
export class PlayerSeasonStatsService {
  private http = inject(HttpClient);
  private apiUrl = `${environment.apiUrl}/careers`;

  /**
   * Get all player stats for a career and season.
   *
   * @param careerId The career ID
   * @param season The season number
   * @param params Optional query parameters (limit, offset, sortBy, order)
   * @returns Observable<PlayerSeasonStatsResponse>
   */
  getPlayerSeasonStats(
    careerId: string,
    season: number,
    params?: PlayerStatsParams
  ): Observable<PlayerSeasonStatsResponse> {
    const httpParams = this.buildParams(params);
    const url = `${this.apiUrl}/${careerId}/seasons/${season}/player-stats`;
    return this.http.get<PlayerSeasonStatsResponse>(url, { params: httpParams });
  }

  /**
   * Get player stats filtered by team.
   *
   * @param careerId The career ID
   * @param season The season number
   * @param teamId The team ID to filter by
   * @param params Optional query parameters (limit, offset, sortBy, order)
   * @returns Observable<PlayerSeasonStatsResponse>
   */
  getTeamPlayerSeasonStats(
    careerId: string,
    season: number,
    teamId: string,
    params?: PlayerStatsParams
  ): Observable<PlayerSeasonStatsResponse> {
    const httpParams = this.buildParams(params);
    const url = `${this.apiUrl}/${careerId}/seasons/${season}/teams/${teamId}/player-stats`;
    return this.http.get<PlayerSeasonStatsResponse>(url, { params: httpParams });
  }

  /**
   * Get stats for a single player.
   *
   * @param careerId The career ID
   * @param season The season number
   * @param playerId The player ID
   * @returns Observable<PlayerSeasonStatsResponse> with single player in playerStats array
   */
  getSinglePlayerSeasonStats(
    careerId: string,
    season: number,
    playerId: string
  ): Observable<PlayerSeasonStatsResponse> {
    const url = `${this.apiUrl}/${careerId}/seasons/${season}/players/${playerId}/stats`;
    return this.http.get<PlayerSeasonStatsResponse>(url);
  }

  /**
   * Build HttpParams from optional query parameters.
   * Only includes defined parameters to avoid sending undefined values.
   *
   * @param params Optional query parameters
   * @returns HttpParams with only defined values set
   */
  private buildParams(params?: PlayerStatsParams): HttpParams {
    let httpParams = new HttpParams();

    if (params === undefined) {
      return httpParams;
    }

    if (params.limit !== undefined) {
      httpParams = httpParams.set('limit', String(params.limit));
    }

    if (params.offset !== undefined) {
      httpParams = httpParams.set('offset', String(params.offset));
    }

    if (params.sortBy !== undefined) {
      httpParams = httpParams.set('sortBy', params.sortBy);
    }

    if (params.order !== undefined) {
      httpParams = httpParams.set('order', params.order);
    }

    return httpParams;
  }

  /**
   * Handle API error and convert to StatsApiError.
   * This preserves the HTTP status for caller decision-making.
   *
   * @param error Error response from HTTP request
   * @returns Observable that errors with StatsApiError
   */
  handleError(error: { status: number; error?: { message?: string } | string }): Observable<never> {
    let message = 'An error occurred';

    if (typeof error.error === 'string') {
      message = error.error;
    } else if (error.error?.message) {
      message = error.error.message;
    }

    return throwError(() => new StatsApiError(error.status, message));
  }
}