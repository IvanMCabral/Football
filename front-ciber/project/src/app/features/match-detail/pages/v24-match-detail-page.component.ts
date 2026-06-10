import { Component, OnInit, inject, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, ActivatedRoute } from '@angular/router';
import { MatchDetailApiService } from '../services/match-detail-api.service';
import { MatchDetail } from '../models/match-detail.model';
import { MatchShotMapComponent } from '../components/shot-map/match-shot-map.component';
import { ShotInput } from '../components/shot-map/match-shot-map.model';

/**
 * V24D5E4: Add Player Ratings UI
 *
 * Shows per-player ratings when playerRatings is non-empty.
 * Empty state for old matches or missing data.
 * Sorted by rating descending, top-rated player highlighted.
 * Grouped by team (home first, then away).
 *
 * No mutations. No career-state changes.
 */
@Component({
  selector: 'app-v24-match-detail-page',
  standalone: true,
  imports: [CommonModule, RouterLink, MatchShotMapComponent],
  template: `
    <div class="v24-match-detail-page">

      <!-- Loading state -->
      <div *ngIf="loading" class="state-container">
        <div class="state-spinner"></div>
        <p class="loading-text">Loading match detail...</p>
      </div>

      <!-- Error state -->
      <div *ngIf="!loading && error" class="state-container">
        <div class="state-icon error-icon">!</div>
        <p class="error-text">Failed to load match detail.</p>
        <button (click)="retry()" class="btn btn-primary">Retry</button>
        <a routerLink="/matches" class="link">Back to matches</a>
      </div>

      <!-- Detail unavailable state (404 / null) -->
      <div *ngIf="!loading && !error && detail === null" class="state-container">
        <div class="state-icon info-icon">?</div>
        <h2 class="state-title">Match Detail</h2>
        <p class="unavailable-text">Detailed match data is not available for this match.</p>
        <div class="info-box">
          <p class="info-box-title">Possible reasons:</p>
          <ul class="info-list">
            <li>Match was played before V24 detail persistence was enabled</li>
            <li>Detail persistence was disabled during simulation</li>
            <li>Endpoint is currently disabled</li>
          </ul>
        </div>
        <a routerLink="/matches" class="link">Back to matches</a>
      </div>

      <!-- Detail success state -->
      <div *ngIf="!loading && !error && detail !== null" class="detail-content">

        <!-- Header -->
        <div class="match-header">
          <a routerLink="/matches" class="link link-back">&#8592; Back to matches</a>
          <div class="scoreboard">
            <div class="team home-team">
              <span class="team-name">{{ detail.homeTeamName }}</span>
            </div>
            <div class="score-container">
              <span class="score">{{ detail.homeGoals }}</span>
              <span class="score-sep">–</span>
              <span class="score">{{ detail.awayGoals }}</span>
            </div>
            <div class="team away-team">
              <span class="team-name">{{ detail.awayTeamName }}</span>
            </div>
          </div>
          <div class="match-meta">
            <span class="meta-item">Round {{ detail.round }}</span>
            <span class="meta-dot">·</span>
            <span class="meta-item">Season {{ detail.seasonNumber }}</span>
            <span class="meta-dot">·</span>
            <span class="badge badge-v24">V24 Engine</span>
          </div>
        </div>

        <!-- Summary Cards -->
        <div class="summary-cards">
          <div class="stat-card">
            <span class="stat-card-label">xG</span>
            <div class="stat-card-values">
              <span class="stat-home">{{ detail.homeXg.toFixed(2) }}</span>
              <span class="stat-divider">–</span>
              <span class="stat-away">{{ detail.awayXg.toFixed(2) }}</span>
            </div>
          </div>
          <div class="stat-card">
            <span class="stat-card-label">Shots</span>
            <div class="stat-card-values">
              <span class="stat-home">{{ detail.homeShots }}</span>
              <span class="stat-divider">–</span>
              <span class="stat-away">{{ detail.awayShots }}</span>
            </div>
          </div>
          <div class="stat-card">
            <span class="stat-card-label">Possession</span>
            <div class="stat-card-values">
              <span class="stat-home">{{ detail.homePossession }}%</span>
              <span class="stat-divider">–</span>
              <span class="stat-away">{{ detail.awayPossession }}%</span>
            </div>
          </div>
          <div class="stat-card">
            <span class="stat-card-label">Goals</span>
            <div class="stat-card-values">
              <span class="stat-home">{{ detail.homeGoals }}</span>
              <span class="stat-divider">–</span>
              <span class="stat-away">{{ detail.awayGoals }}</span>
            </div>
          </div>
        </div>

        <!-- Post-Match Condition Summary (V24D6G6A) -->
        <div class="section post-match-summary" *ngIf="!loading && !error && detail !== null">
          <h3 class="section-title">Post-Match Condition</h3>
          <div class="condition-summary">
            <div class="condition-row" *ngIf="hasInjuryEvents()">
              <span class="condition-badge injury-badge">🤕</span>
              <span class="condition-text">{{ postMatchConditionLabel() }}</span>
            </div>
            <div class="condition-row" *ngIf="!hasInjuryEvents()">
              <span class="condition-badge neutral-badge">✅</span>
              <span class="condition-text">{{ postMatchConditionLabel() }}</span>
            </div>
            <ul class="injury-list" *ngIf="hasInjuryEvents()">
              <li *ngFor="let p of injuredPlayerSummary()" class="injury-item">
                <span class="injury-player">{{ p.playerName }}</span>
                <span class="injury-minute">{{ p.minute }}'</span>
              </li>
            </ul>
            <div class="condition-row discipline-row" *ngIf="yellowCardCount() > 0 || redCardCount() > 0">
              <span class="discipline-summary">
                <span *ngIf="yellowCardCount() > 0" class="card-count">
                  <span class="card-badge yellow-badge">🟨</span> {{ yellowCardCount() }} yellow card{{ yellowCardCount() > 1 ? 's' : '' }}
                </span>
                <span *ngIf="redCardCount() > 0" class="card-count">
                  <span class="card-badge red-badge">🟥</span> {{ redCardCount() }} red card{{ redCardCount() > 1 ? 's' : '' }}
                </span>
              </span>
            </div>
          </div>
        </div>

        <!-- Timeline Section -->
        <div class="section">
          <h3 class="section-title">Timeline</h3>
          <div *ngIf="detail.timeline.length === 0" class="empty-state">
            No timeline events available.
          </div>
          <ul class="timeline-list" *ngIf="detail.timeline.length > 0">
            <li *ngFor="let event of detail.timeline" class="timeline-item">
              <span class="event-minute">{{ event.minute }}'</span>
              <span class="event-badge" [ngClass]="eventClass(event.type)">{{ event.type }}</span>
              <div class="event-body">
                <span class="event-player">{{ event.playerName }}</span>
                <span class="event-assist" *ngIf="event.relatedPlayerName">assist: {{ event.relatedPlayerName }}</span>
              </div>
              <div class="event-meta">
                <span class="event-xg" *ngIf="event.xg != null">xG {{ event.xg.toFixed(2) }}</span>
                <span class="event-desc">{{ event.description }}</span>
              </div>
            </li>
          </ul>
        </div>

        <!-- Stats Comparison Section -->
        <div class="section">
          <h3 class="section-title">Stats</h3>
          <div class="stats-table">
            <div class="stats-row" *ngFor="let row of statsComparison()">
              <span class="stat-label">{{ row.label }}</span>
              <span class="stat-home">{{ row.home }}</span>
              <span class="stat-away">{{ row.away }}</span>
            </div>
          </div>
        </div>

        <!-- Players Section -->
        <div class="section">
          <h3 class="section-title">Players</h3>
          <div *ngIf="!hasPlayerRatings()" class="empty-state">
            Player ratings are not available for this match.
          </div>
          <div *ngIf="hasPlayerRatings()" class="players-container">
            <div class="team-players" *ngFor="let team of playerRatingsByTeam()">
              <div class="team-header">
                <span class="team-label">{{ team.label }}</span>
                <span class="team-rating" *ngIf="team.topPlayer">Top: {{ team.topPlayer.playerName }} ({{ team.topPlayer.rating.toFixed(1) }})</span>
              </div>
              <div class="table-responsive">
                <table class="players-table">
                  <thead>
                    <tr>
                      <th>Player</th>
                      <th>Pos</th>
                      <th>Rat</th>
                      <th>G</th>
                      <th>A</th>
                      <th>KP</th>
                      <th>Sh</th>
                      <th>Cards</th>
                      <th>Inj</th>
                      <th>Subs</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr *ngFor="let p of team.players; let i = index"
                        [class.row-top]="i === 0">
                      <td class="player-name">{{ p.playerName }}</td>
                      <td class="player-pos">{{ p.position }}</td>
                      <td class="player-rating" [class.rat-high]="p.rating >= 7.0" [class.rat-low]="p.rating < 6.0">{{ p.rating.toFixed(1) }}</td>
                      <td>{{ p.goals }}</td>
                      <td>{{ p.assists }}</td>
                      <td>{{ p.keyPasses }}</td>
                      <td>{{ p.shots }}</td>
                      <td class="text-center">
                        <span *ngIf="p.cards > 0" class="badge-card" [class.yellow]="p.cards === 1" [class.red]="p.cards >= 2">{{ p.cards }}</span>
                        <span *ngIf="p.cards === 0" class="text-muted">–</span>
                      </td>
                      <td class="text-center">
                        <span *ngIf="p.injuries > 0" class="badge-inj">{{ p.injuries }}</span>
                        <span *ngIf="p.injuries === 0" class="text-muted">–</span>
                      </td>
                      <td class="text-center">
                        <span *ngIf="p.substitutions > 0">{{ p.substitutions }}</span>
                        <span *ngIf="p.substitutions === 0" class="text-muted">–</span>
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        </div>

        <!-- Shots Section (Shot Map) -->
        <div class="section">
          <h3 class="section-title">Shot Map</h3>
          <div *ngIf="!hasShotMap()" class="empty-state">
            Shot map is not available for this match.
          </div>
          <app-match-shot-map
            *ngIf="hasShotMap()"
            [shotsInput]="shotInputs()"
            [homeTeamId]="detail?.homeTeamId ?? null"
            [awayTeamId]="detail?.awayTeamId ?? null">
          </app-match-shot-map>
        </div>

      </div>
    </div>
  `,
  styles: [`
    /* === Layout === */
    .v24-match-detail-page {
      padding: 16px;
      max-width: 900px;
      margin: 0 auto;
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
      color: #1a1a2e;
      background: #fafbfc;
    }
    .detail-content {
      display: flex;
      flex-direction: column;
      gap: 24px;
    }

    /* === State containers === */
    .state-container {
      display: flex;
      flex-direction: column;
      align-items: center;
      padding: 48px 16px;
      gap: 12px;
    }
    .state-spinner {
      width: 36px;
      height: 36px;
      border: 3px solid #e0e0e0;
      border-top-color: #1976d2;
      border-radius: 50%;
      animation: spin 0.8s linear infinite;
    }
    @keyframes spin { to { transform: rotate(360deg); } }
    .state-icon {
      width: 48px;
      height: 48px;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 22px;
      font-weight: bold;
    }
    .state-icon.error-icon { background: #ffebee; color: #c62828; }
    .state-icon.info-icon { background: #e3f2fd; color: #1976d2; }
    .state-title { margin: 0; font-size: 20px; color: #333; }
    .loading-text { color: #666; font-size: 15px; margin: 0; }
    .error-text { color: #c62828; font-size: 15px; margin: 0; }
    .unavailable-text { color: #555; font-size: 15px; margin: 0; }

    /* === Buttons & Links === */
    .btn {
      padding: 8px 20px;
      border: none;
      border-radius: 6px;
      font-size: 14px;
      cursor: pointer;
      transition: opacity 0.15s;
    }
    .btn:hover { opacity: 0.85; }
    .btn-primary { background: #1976d2; color: #fff; }
    .link {
      color: #1976d2;
      text-decoration: none;
      font-size: 14px;
    }
    .link:hover { text-decoration: underline; }
    .link-back {
      font-size: 13px;
      display: inline-block;
      margin-bottom: 8px;
    }

    /* === Info box === */
    .info-box {
      background: #f5f5f5;
      border-radius: 8px;
      padding: 12px 16px;
      max-width: 400px;
      width: 100%;
      text-align: left;
    }
    .info-box-title { font-size: 13px; color: #555; margin: 0 0 6px; }
    .info-list {
      margin: 0;
      padding-left: 18px;
      font-size: 13px;
      color: #777;
    }
    .info-list li { margin-bottom: 4px; }

    /* === Header / Scoreboard === */
    .match-header {
      background: #fff;
      border-radius: 12px;
      padding: 20px;
      box-shadow: 0 1px 4px rgba(0,0,0,0.08);
      display: flex;
      flex-direction: column;
      gap: 12px;
    }
    .scoreboard {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 16px;
    }
    .team { flex: 1; }
    .team-name {
      font-size: 16px;
      font-weight: 600;
      color: #1a1a2e;
      display: block;
    }
    .away-team .team-name { text-align: right; }
    .score-container {
      display: flex;
      align-items: center;
      gap: 8px;
    }
    .score {
      font-size: 36px;
      font-weight: 700;
      color: #1976d2;
      line-height: 1;
    }
    .score-sep { font-size: 24px; color: #aaa; font-weight: 300; }
    .match-meta {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 6px;
      font-size: 13px;
      color: #888;
    }
    .meta-item { }
    .meta-dot { color: #ccc; }
    .badge {
      display: inline-block;
      padding: 2px 8px;
      border-radius: 10px;
      font-size: 11px;
      font-weight: 600;
    }
    .badge-v24 { background: #e3f2fd; color: #1976d2; }

    /* === Summary Cards === */
    .summary-cards {
      display: grid;
      grid-template-columns: repeat(4, 1fr);
      gap: 10px;
    }
    .stat-card {
      background: #fff;
      border-radius: 10px;
      padding: 14px 12px;
      box-shadow: 0 1px 3px rgba(0,0,0,0.07);
      display: flex;
      flex-direction: column;
      gap: 6px;
      text-align: center;
    }
    .stat-card-label {
      font-size: 11px;
      font-weight: 600;
      color: #888;
      text-transform: uppercase;
      letter-spacing: 0.5px;
    }
    .stat-card-values {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 6px;
    }
    .stat-home { font-size: 16px; font-weight: 700; color: #1a1a2e; }
    .stat-away { font-size: 16px; font-weight: 700; color: #1a1a2e; }
    .stat-divider { font-size: 13px; color: #bbb; }

    /* === Sections === */
    .section {
      background: #fff;
      border-radius: 12px;
      padding: 20px;
      box-shadow: 0 1px 4px rgba(0,0,0,0.08);
    }
    .section-title {
      font-size: 15px;
      font-weight: 700;
      color: #1a1a2e;
      margin: 0 0 14px;
      padding-bottom: 10px;
      border-bottom: 2px solid #f0f0f0;
    }

    /* === V24D6G6A Post-Match Condition Summary === */
    .post-match-summary {
      background: #fff;
      border-left: 4px solid #1976d2;
    }
    .condition-summary {
      display: flex;
      flex-direction: column;
      gap: 8px;
    }
    .condition-row {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 14px;
    }
    .condition-badge {
      font-size: 16px;
      width: 24px;
      text-align: center;
    }
    .injury-badge { color: #c62828; }
    .neutral-badge { color: #2e7d32; }
    .condition-text {
      font-weight: 600;
      color: #333;
    }
    .injury-list {
      list-style: none;
      margin: 4px 0 0 32px;
      padding: 0;
      display: flex;
      flex-direction: column;
      gap: 4px;
    }
    .injury-item {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 13px;
    }
    .injury-player {
      color: #1a1a2e;
      font-weight: 500;
    }
    .injury-minute {
      color: #888;
      font-size: 12px;
    }
    .discipline-row {
      margin-top: 4px;
      padding-top: 8px;
      border-top: 1px solid #f0f0f0;
    }
    .discipline-summary {
      display: flex;
      align-items: center;
      gap: 12px;
    }
    .card-count {
      display: flex;
      align-items: center;
      gap: 4px;
      font-size: 13px;
      color: #555;
    }

    /* === Empty state === */
    .empty-state {
      color: #aaa;
      font-size: 14px;
      font-style: italic;
      padding: 8px 0;
    }

    /* === Timeline === */
    .timeline-list {
      list-style: none;
      margin: 0;
      padding: 0;
      display: flex;
      flex-direction: column;
      gap: 0;
    }
    .timeline-item {
      display: flex;
      align-items: flex-start;
      gap: 10px;
      padding: 10px 0;
      border-bottom: 1px solid #f5f5f5;
      font-size: 13px;
    }
    .timeline-item:last-child { border-bottom: none; }
    .event-minute {
      font-weight: 700;
      color: #1976d2;
      min-width: 28px;
      font-size: 13px;
      padding-top: 1px;
    }
    .event-badge {
      font-weight: 700;
      padding: 2px 7px;
      border-radius: 4px;
      font-size: 10px;
      text-transform: uppercase;
      letter-spacing: 0.3px;
      white-space: nowrap;
    }
    .event-body {
      flex: 1;
      min-width: 0;
    }
    .event-player {
      font-weight: 600;
      color: #1a1a2e;
      display: block;
    }
    .event-assist {
      font-size: 11px;
      color: #999;
      display: block;
    }
    .event-meta {
      display: flex;
      flex-direction: column;
      align-items: flex-end;
      gap: 2px;
    }
    .event-xg { font-size: 11px; color: #888; }
    .event-desc { font-size: 11px; color: #bbb; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; max-width: 140px; }

    /* Event type badge colors */
    .event-goal    { background: #e8f5e9; color: #2e7d32; }
    .event-shot    { background: #fff3e0; color: #e65100; }
    .event-foul    { background: #ffebee; color: #c62828; }
    .event-yellow_card { background: #fff9c4; color: #f9a825; }
    .event-red_card { background: #ffcdd2; color: #c62828; }
    .event-injury  { background: #e3f2fd; color: #0288d1; }
    .event-substitution { background: #f3e5f5; color: #7b1fa2; }
    .event-offside { background: #fafafa; color: #757575; }
    .event-corner  { background: #e0f7fa; color: #00838f; }
    .event-chance_created { background: #fafafa; color: #757575; }
    .event-shot_on_target { background: #fff3e0; color: #e65100; }
    .event-block { background: #fff3e0; color: #e65100; }
    .event-miss { background: #f5f5f5; color: #757575; }

    /* === Stats Comparison === */
    .stats-table {
      display: flex;
      flex-direction: column;
      gap: 0;
    }
    .stats-row {
      display: grid;
      grid-template-columns: 80px 1fr 1fr;
      align-items: center;
      padding: 10px 0;
      border-bottom: 1px solid #f5f5f5;
    }
    .stats-row:last-child { border-bottom: none; }
    .stat-label { font-size: 13px; color: #666; text-transform: uppercase; font-size: 11px; letter-spacing: 0.3px; }
    .stats-row .stat-home { text-align: right; font-weight: 700; font-size: 15px; color: #1a1a2e; }
    .stats-row .stat-away { text-align: left; font-weight: 700; font-size: 15px; color: #1a1a2e; }

    /* === Players === */
    .players-container { display: flex; flex-direction: column; gap: 20px; }
    .team-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 8px;
    }
    .team-label {
      font-size: 12px;
      font-weight: 700;
      color: #555;
      text-transform: uppercase;
      letter-spacing: 0.5px;
    }
    .team-rating {
      font-size: 12px;
      color: #2e7d32;
      font-weight: 600;
    }
    .table-responsive { overflow-x: auto; }
    .players-table {
      width: 100%;
      border-collapse: collapse;
      font-size: 13px;
      min-width: 560px;
    }
    .players-table th {
      text-align: left;
      padding: 7px 8px;
      background: #f5f5f5;
      color: #777;
      font-weight: 700;
      font-size: 10px;
      text-transform: uppercase;
      letter-spacing: 0.4px;
      border-bottom: 2px solid #e0e0e0;
      white-space: nowrap;
    }
    .players-table td {
      padding: 7px 8px;
      border-bottom: 1px solid #f5f5f5;
      color: #1a1a2e;
      white-space: nowrap;
    }
    .players-table tr:last-child td { border-bottom: none; }
    .players-table tr.row-top td { background: #f8fff8; }
    .players-table tr:hover td { background: #fafafa; }
    .player-name { font-weight: 600; }
    .player-pos { color: #888; font-size: 12px; }
    .player-rating { font-weight: 700; }
    .rat-high { color: #1565c0; }
    .rat-low { color: #c62828; }
    .text-center { text-align: center; }
    .text-muted { color: #ccc; }
    .badge-card {
      display: inline-block;
      padding: 2px 6px;
      border-radius: 8px;
      font-size: 11px;
      font-weight: 700;
    }
    .badge-card.yellow { background: #fff9c4; color: #f9a825; }
    .badge-card.red { background: #ffcdd2; color: #c62828; }
    .badge-inj { color: #0288d1; font-weight: 700; font-size: 12px; }

    /* === Shot Map moved to standalone MatchShotMapComponent === */

    /* === Responsive === */
    @media (max-width: 600px) {
      .summary-cards { grid-template-columns: repeat(2, 1fr); }
      .score { font-size: 28px; }
      .team-name { font-size: 14px; }
      .scoreboard { gap: 10px; }
    }
  `]
})
export class V24MatchDetailPageComponent implements OnInit {
  private api = inject(MatchDetailApiService);
  private route = inject(ActivatedRoute);
  private cdr = inject(ChangeDetectorRef);

  loading = false;
  error = '';
  detail: MatchDetail | null = null;

  ngOnInit(): void {
    const careerId = this.route.snapshot.paramMap.get('careerId');
    const matchId = this.route.snapshot.paramMap.get('matchId');
    if (!careerId || !matchId) {
      this.error = 'Missing career or match ID.';
      this.cdr.detectChanges();
      return;
    }
    this.loading = true;
    this.error = '';
    this.cdr.detectChanges();

    this.api.getMatchDetail(careerId, matchId).subscribe({
      next: (data) => {
        try {
          console.log('[V24-DETAIL] received match detail', { careerId, matchId, hasData: !!data, hasTimeline: !!(data && data.timeline), timelineSize: data?.timeline?.length });
          this.detail = data ?? null;
          this.loading = false;
        } catch (err) {
          console.error('[V24-DETAIL] error processing response', err);
          this.error = 'Failed to process match detail response.';
          this.loading = false;
        } finally {
          this.cdr.detectChanges();
        }
      },
      error: (err) => {
        console.error('[V24-DETAIL] HTTP error', { careerId, matchId, status: err?.status, url: err?.url, message: err?.message });
        this.error = 'Failed to load match detail.';
        this.loading = false;
        this.cdr.detectChanges();
      }
    });
  }

  retry(): void { this.ngOnInit(); }

  // === Helpers ===
  private typeMap: Record<string, string> = {
    'GOAL': 'event-goal', 'SHOT': 'event-shot', 'SHOT_ON_TARGET': 'event-shot_on_target',
    'BLOCK': 'event-block', 'MISS': 'event-miss', 'FOUL': 'event-foul',
    'YELLOW_CARD': 'event-yellow_card', 'RED_CARD': 'event-red_card',
    'INJURY': 'event-injury', 'SUBSTITUTION': 'event-substitution',
    'OFFSIDE': 'event-offside', 'CORNER': 'event-corner', 'CHANCE_CREATED': 'event-chance_created'
  };
  eventClass(type: string): string { return this.typeMap[type] ?? 'event-shot'; }

  statsComparison(): { label: string; home: string; away: string }[] {
    if (!this.detail) return [];
    return [
      { label: 'Goals', home: String(this.detail.homeGoals), away: String(this.detail.awayGoals) },
      { label: 'xG', home: this.detail.homeXg.toFixed(2), away: this.detail.awayXg.toFixed(2) },
      { label: 'Shots', home: String(this.detail.homeShots), away: String(this.detail.awayShots) },
      { label: 'Possession', home: `${this.detail.homePossession}%`, away: `${this.detail.awayPossession}%` }
    ];
  }

  hasPlayerRatings(): boolean { return !!(this.detail?.playerRatings?.length); }

  playerRatingsByTeam(): { label: string; players: import('../models/match-detail.model').PlayerMatchRating[]; topPlayer?: import('../models/match-detail.model').PlayerMatchRating }[] {
    if (!this.detail?.playerRatings?.length) return [];
    const home = this.detail.playerRatings.filter(p => p.teamId === this.detail!.homeTeamId);
    const away = this.detail.playerRatings.filter(p => p.teamId === this.detail!.awayTeamId);
    const sorted = (list: import('../models/match-detail.model').PlayerMatchRating[]) => [...list].sort((a, b) => b.rating - a.rating);
    const withTop = (list: import('../models/match-detail.model').PlayerMatchRating[], label: string) => {
      const s = sorted(list);
      return { label, players: s, topPlayer: s[0] };
    };
    return [
      withTop(home, 'Home Team'),
      withTop(away, 'Away Team')
    ].filter(t => t.players.length > 0);
  }

  // === Post-Match Condition Summary (V24D6G6A) ===
  injuryEvents(): import('../models/match-detail.model').MatchEvent[] {
    return (this.detail?.timeline ?? []).filter(e => e.type === 'INJURY');
  }

  injuryEventsCount(): number { return this.injuryEvents().length; }

  hasInjuryEvents(): boolean { return this.injuryEventsCount() > 0; }

  yellowCardEvents(): import('../models/match-detail.model').MatchEvent[] {
    return (this.detail?.timeline ?? []).filter(e => e.type === 'YELLOW_CARD');
  }

  redCardEvents(): import('../models/match-detail.model').MatchEvent[] {
    return (this.detail?.timeline ?? []).filter(e => e.type === 'RED_CARD');
  }

  yellowCardCount(): number { return this.yellowCardEvents().length; }
  redCardCount(): number { return this.redCardEvents().length; }

  injuredPlayerSummary(): { playerName: string; minute: number }[] {
    return this.injuryEvents().map(e => ({
      playerName: e.playerName || 'Unknown',
      minute: e.minute
    }));
  }

  postMatchConditionLabel(): string {
    if (this.hasInjuryEvents()) {
      return `🤕 ${this.injuryEventsCount()} injury event${this.injuryEventsCount() > 1 ? 's' : ''} occurred`;
    }
    return 'No injury events recorded for this match.';
  }

  // === Shot Map (V24D6O) ===
  hasShotMap(): boolean { return !!(this.detail?.timeline?.some(e => e.shotCoordinate != null)); }
  /**
   * Build a strongly-typed ShotInput[] for the standalone MatchShotMapComponent.
   * The renderer owns projection, drawing, and tooltips.
   */
  shotInputs(): ShotInput[] {
    const tl = this.detail?.timeline ?? [];
    return tl
      .filter(e => !!e.shotCoordinate)
      .map(e => ({
        teamId: e.teamId,
        type: e.type,
        minute: e.minute,
        playerName: e.playerName,
        xg: e.xg ?? null,
        description: e.description,
        shotCoordinate: e.shotCoordinate,
      }));
  }
}
