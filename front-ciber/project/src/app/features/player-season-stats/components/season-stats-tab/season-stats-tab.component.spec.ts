/**
 * Contract tests for SeasonStatsTabComponent scope input.
 *
 * The Squad → Stats tab in browser E2E regressed because the component
 * silently fell back to the all-player endpoint when `teamId` was empty.
 * The fix introduces a `scope: 'team' | 'global'` input:
 *
 *   - scope='team'   + teamId='xyz' → /teams/{teamId}/player-stats
 *   - scope='team'   + teamId=''    → NO FETCH (waiting-team state)
 *   - scope='global' + teamId='xyz' → /teams/{teamId}/player-stats (legacy)
 *   - scope='global' + teamId=''    → /player-stats (legacy fallback)
 *
 * These tests pin the contract so the regression cannot return.
 */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { SimpleChange } from '@angular/core';

import { SeasonStatsTabComponent } from './season-stats-tab.component';
import { PlayerSeasonStatsService } from '../../services/player-season-stats.service';
import {
  PlayerSeasonStatsResponse,
  DEFAULT_STATS_PARAMS
} from '../../models/player-season-stats.model';

describe('SeasonStatsTabComponent — scope contract', () => {
  let fixture: ComponentFixture<SeasonStatsTabComponent>;
  let component: SeasonStatsTabComponent;

  // Spies on the service. Each test asserts which one was called.
  let teamSpy: jasmine.Spy;
  let allSpy: jasmine.Spy;

  const FAKE_RESPONSE: PlayerSeasonStatsResponse = {
    careerId: 'career-1',
    season: 1,
    playerStats: [],
    totals: { totalGoals: 0, totalAssists: 0, totalAppearances: 0, averageRating: 0 },
    incomplete: false,
    message: 'ok',
    metadata: {
      limit: 25,
      offset: 0,
      hasMore: false,
      totalPlayers: 0,
      returnedPlayers: 0,
      dataSource: 'V24_DETAIL',
      dataCompleteness: 'COMPLETE',
      filters: { season: 1 }
    },
    warnings: []
  };

  beforeEach(async () => {
    teamSpy = jasmine.createSpy('getTeamPlayerSeasonStats').and.returnValue(of(FAKE_RESPONSE));
    allSpy = jasmine.createSpy('getPlayerSeasonStats').and.returnValue(of(FAKE_RESPONSE));

    await TestBed.configureTestingModule({
      imports: [SeasonStatsTabComponent],
      providers: [
        {
          provide: PlayerSeasonStatsService,
          useValue: {
            getTeamPlayerSeasonStats: teamSpy,
            getPlayerSeasonStats: allSpy
          }
        }
      ]
    })
      .overrideComponent(SeasonStatsTabComponent, {
        // Stub out the child UI components so we don't need their full templates.
        set: { template: '<div></div>', styleUrls: [] }
      })
      .compileComponents();

    fixture = TestBed.createComponent(SeasonStatsTabComponent);
    component = fixture.componentInstance;
  });

  /**
   * Helper: set inputs, call ngOnChanges, and trigger initial detect.
   * Mirrors the lifecycle Angular would drive from a real parent template.
   */
  function setInputs(opts: {
    careerId?: string;
    season?: number;
    teamId?: string;
    scope?: 'team' | 'global';
  }) {
    component.careerId = opts.careerId ?? 'career-1';
    component.season = opts.season ?? 1;
    component.teamId = opts.teamId ?? '';
    component.scope = opts.scope ?? 'global';

    // Manually drive ngOnChanges for the inputs we set, so the component
    // sees a change detection cycle identical to a real parent binding.
    const changes: { [k: string]: SimpleChange } = {};
    if (opts.careerId !== undefined) changes['careerId'] = new SimpleChange('', component.careerId, true);
    if (opts.season !== undefined) changes['season'] = new SimpleChange(1, component.season, true);
    if (opts.teamId !== undefined) changes['teamId'] = new SimpleChange('', component.teamId, true);
    if (opts.scope !== undefined) changes['scope'] = new SimpleChange('global', component.scope, true);
    component.ngOnChanges(changes);
  }

  it('scope=team + teamId=empty → does NOT call any service method, enters waiting-team', () => {
    setInputs({ careerId: 'career-1', teamId: '', scope: 'team' });

    expect(allSpy).not.toHaveBeenCalled();
    expect(teamSpy).not.toHaveBeenCalled();
    expect(component.loadingState).toBe('waiting-team');
  });

  it('scope=team + teamId=non-empty → calls /teams/{teamId}/player-stats ONLY', () => {
    setInputs({ careerId: 'career-1', teamId: 'team-A', scope: 'team' });

    expect(teamSpy).toHaveBeenCalledTimes(1);
    expect(teamSpy.calls.mostRecent().args[0]).toBe('career-1');
    expect(teamSpy.calls.mostRecent().args[1]).toBe(1);
    expect(teamSpy.calls.mostRecent().args[2]).toBe('team-A');
    expect(allSpy).not.toHaveBeenCalled();
    expect(component.loadingState).toBe('loaded');
  });

  it('scope=global + teamId=non-empty → calls /teams/{teamId}/player-stats (legacy precedence)', () => {
    setInputs({ careerId: 'career-1', teamId: 'team-A', scope: 'global' });

    expect(teamSpy).toHaveBeenCalledTimes(1);
    expect(allSpy).not.toHaveBeenCalled();
  });

  it('scope=global + teamId=empty → calls /player-stats (legacy fallback)', () => {
    setInputs({ careerId: 'career-1', teamId: '', scope: 'global' });

    expect(allSpy).toHaveBeenCalledTimes(1);
    expect(allSpy.calls.mostRecent().args[0]).toBe('career-1');
    expect(allSpy.calls.mostRecent().args[1]).toBe(1);
    expect(teamSpy).not.toHaveBeenCalled();
  });

  it('switching scope from global to team with empty teamId does NOT fire fetch', () => {
    // Start in global mode, no teamId — should call all-player endpoint
    setInputs({ careerId: 'career-1', teamId: '', scope: 'global' });
    expect(allSpy).toHaveBeenCalledTimes(1);
    expect(teamSpy).not.toHaveBeenCalled();

    // Parent flips to team scope. teamId is still empty.
    component.scope = 'team';
    component.ngOnChanges({ scope: new SimpleChange('global', 'team', false) });

    // No new fetch should have been triggered.
    expect(allSpy).toHaveBeenCalledTimes(1);   // unchanged
    expect(teamSpy).toHaveBeenCalledTimes(0);  // never
    expect(component.loadingState).toBe('waiting-team');
  });

  it('teamId arriving after careerId in team scope triggers a refetch', () => {
    // First render: careerId set, teamId still empty, scope=team
    setInputs({ careerId: 'career-1', teamId: '', scope: 'team' });
    expect(teamSpy).not.toHaveBeenCalled();
    expect(component.loadingState).toBe('waiting-team');

    // Parent updates teamId — same component instance, new change cycle
    component.teamId = 'team-A';
    component.ngOnChanges({ teamId: new SimpleChange('', 'team-A', false) });

    expect(teamSpy).toHaveBeenCalledTimes(1);
    expect(teamSpy.calls.mostRecent().args[2]).toBe('team-A');
    expect(allSpy).not.toHaveBeenCalled();
  });

  it('careerId empty → fetchStats is a no-op regardless of scope/teamId', () => {
    setInputs({ careerId: '', teamId: 'team-A', scope: 'team' });
    expect(teamSpy).not.toHaveBeenCalled();
    expect(allSpy).not.toHaveBeenCalled();
  });
});
