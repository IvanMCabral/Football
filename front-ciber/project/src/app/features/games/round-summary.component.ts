import { Component, inject, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, ActivatedRoute } from '@angular/router';
import { CareerService } from '../../core/services/career.service';
import { Standing } from '../../core/services/career.model';
import { BehaviorSubject, Observable, combineLatest, of } from 'rxjs';
import { map, switchMap, tap, takeUntil, catchError, shareReplay } from 'rxjs/operators';
import { Subject } from 'rxjs';
import { RoundSummaryViewModel, SummaryMatchVM } from './models/round-summary.model';

@Component({
  selector: 'app-round-summary',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './round-summary.component.html',
  styleUrls: ['./round-summary.component.css']
})
export class RoundSummaryComponent implements OnInit, OnDestroy {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private careerService = inject(CareerService);

  private destroy$ = new Subject<void>();

  private vmSubject = new BehaviorSubject<RoundSummaryViewModel>({
    gameId: '',
    roundNumber: 1,
    careerId: '',
    matches: [],
    standings: [],
    teamNameMap: {},
    userTeamId: '',
    userTeamName: '',
    userPosition: 0,
    careerPhase: 'WAITING_USER',
    tournamentStatus: null,
    errorMsg: ''
  });

  vm$: Observable<RoundSummaryViewModel>;

  constructor() {
    this.vm$ = this.vmSubject.asObservable();

    const routeParams$ = this.route.paramMap.pipe(
      map(params => ({
        gameId: params.get('gameId') || '',
        roundNumber: params.get('round') ? parseInt(params.get('round')!) : 1
      })),
      shareReplay(1)
    );

    const teams$ = routeParams$.pipe(
      switchMap(params => this.careerService.getCareerTeams(params.gameId)),
      map(teams => {
        const teamMap: { [id: string]: string } = {};
        teams.forEach(team => {
          const teamId = team.sessionTeamId || String(team.id);
          teamMap[teamId] = team.name;
        });
        return teamMap;
      }),
      shareReplay(1)
    );

    const careerStatus$ = routeParams$.pipe(
      switchMap(params => this.careerService.getCareerStatus())
    );

    combineLatest([routeParams$, teams$, careerStatus$]).pipe(
      takeUntil(this.destroy$),
      tap(([params, teamMap, careerStatus]) => {
        const userSessionTeamId = careerStatus.userSessionTeamId || '';
        const userTeamName = teamMap[userSessionTeamId] || '';

        this.careerService.getFixturesByRound(params.roundNumber).subscribe({
          next: (fixtures) => {
            const matches: SummaryMatchVM[] = fixtures.map(fixture => ({
              matchId: fixture.matchId,
              homeTeamId: fixture.homeTeamId,
              awayTeamId: fixture.awayTeamId,
              homeGoals: fixture.homeGoals ?? 0,
              awayGoals: fixture.awayGoals ?? 0,
              status: fixture.status,
              isUserMatch: String(fixture.homeTeamId) === String(userSessionTeamId) ||
                           String(fixture.awayTeamId) === String(userSessionTeamId)
            }));

            const canPlayNextRound = careerStatus.canAdvanceRound &&
                                      careerStatus.currentRound <= careerStatus.totalRounds;
            const hasNextRound = params.roundNumber < careerStatus.totalRounds;
            const isTournamentEnded = careerStatus.careerPhase === 'FINISHED' ||
                                       careerStatus.currentRound > careerStatus.totalRounds;

            this.updateVm({
              gameId: params.gameId,
              roundNumber: params.roundNumber,
              careerId: careerStatus.careerId || '',
              matches,
              standings: [],
              teamNameMap: teamMap,
              userTeamId: userSessionTeamId,
              userTeamName: userTeamName,
              userPosition: 0,
              careerPhase: careerStatus.careerPhase || 'WAITING_USER',
              tournamentStatus: {
                currentRound: careerStatus.currentRound,
                totalRounds: careerStatus.totalRounds,
                hasNextRound,
                isFinished: isTournamentEnded,
                canPlayCurrentRound: canPlayNextRound,
                season: careerStatus.season
              },
              errorMsg: ''
            });

            this.loadStandings();
            setTimeout(() => this.reloadCareerStatus(), 100);
          },
          error: (err) => {
            console.error('[SUMMARY] Error loading fixtures:', err);
            this.updateVm({
              ...this.vmSubject.value,
              errorMsg: 'Error al cargar los partidos'
            });
          }
        });
      }),
      catchError(err => {
        console.error('[SUMMARY] Error:', err);
        return of(null);
      })
    ).subscribe();
  }

  ngOnInit() {
  }

  ngOnDestroy() {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private updateVm(vm: RoundSummaryViewModel) {
    this.vmSubject.next(vm);
  }

  private loadStandings() {
    this.careerService.getStandings().subscribe({
      next: (standings) => {
        const vm = this.vmSubject.value;
        const userPosition = standings.findIndex(s => s.teamId === vm.userTeamId) + 1;

        const frontendStandings: Standing[] = standings.map(s => ({
          ...s,
          wins: s.won,
          draws: s.drawn,
          losses: s.lost
        }));

        this.updateVm({
          ...vm,
          standings: frontendStandings,
          userPosition
        });
      },
      error: (err) => {
        console.error('[SUMMARY] Error loading standings:', err);
        this.calculateStandingsFromMatches();
      }
    });
  }

  public reloadCareerStatus() {
    let attempts = 0;
    const maxAttempts = 5;
    const delayMs = 200;

    const fetchStatus = () => {
      this.careerService.getCareerStatus().subscribe({
        next: (status) => {
          const vm = this.vmSubject.value;
          const canPlayNextRound = status.canAdvanceRound && status.currentRound <= status.totalRounds;
          const hasNextRound = vm.roundNumber < status.totalRounds;
          const isTournamentEnded = status.careerPhase === 'FINISHED' || status.currentRound > status.totalRounds;
          const isStaleData = vm.roundNumber >= 21 && status.currentRound === vm.roundNumber && status.canAdvanceRound && status.careerPhase !== 'FINISHED';

          if (isStaleData && attempts < maxAttempts) {
            attempts++;
            setTimeout(fetchStatus, delayMs);
            return;
          }

          const newTournamentStatus = {
            currentRound: status.currentRound,
            totalRounds: status.totalRounds,
            hasNextRound,
            isFinished: isTournamentEnded,
            canPlayCurrentRound: canPlayNextRound,
            season: status.season
          };

          this.updateVm({
            ...vm,
            careerPhase: status.careerPhase || 'WAITING_USER',
            tournamentStatus: newTournamentStatus
          });
        },
        error: (err) => {
          if (attempts < maxAttempts) {
            attempts++;
            setTimeout(fetchStatus, delayMs);
          }
        }
      });
    };

    fetchStatus();
  }

  private calculateStandingsFromMatches() {
    const vm = this.vmSubject.value;

    const standingsMap = new Map<string, Standing>();

    vm.matches.forEach(match => {
      [match.homeTeamId, match.awayTeamId].forEach(teamId => {
        if (!standingsMap.has(teamId)) {
          standingsMap.set(teamId, {
            teamId,
            teamName: vm.teamNameMap[teamId] || '',
            played: 0,
            won: 0,
            drawn: 0,
            lost: 0,
            goalsFor: 0,
            goalsAgainst: 0,
            goalDifference: 0,
            points: 0
          });
        }
      });
    });

    vm.matches.forEach(match => {
      const home = standingsMap.get(match.homeTeamId);
      const away = standingsMap.get(match.awayTeamId);

      if (home && away) {
        home.played++;
        away.played++;
        home.goalsFor += match.homeGoals;
        away.goalsFor += match.awayGoals;
        home.goalsAgainst += match.awayGoals;
        away.goalsAgainst += match.homeGoals;
        home.goalDifference = home.goalsFor - home.goalsAgainst;
        away.goalDifference = away.goalsFor - away.goalsAgainst;

        if (match.homeGoals > match.awayGoals) {
          home.won++;
          home.points += 3;
          away.lost++;
        } else if (match.homeGoals < match.awayGoals) {
          away.won++;
          away.points += 3;
          home.lost++;
        } else {
          home.drawn++;
          away.drawn++;
          home.points++;
          away.points++;
        }
      }
    });

    const standings = Array.from(standingsMap.values())
      .sort((a, b) => b.points - a.points || b.goalDifference - a.goalDifference);

    const userPosition = standings.findIndex(s => s.teamId === vm.userTeamId) + 1;

    this.updateVm({
      ...vm,
      standings,
      userPosition
    });
  }

  getTeamName(teamId: string): string {
    const vm = this.vmSubject.value;
    return vm.teamNameMap[teamId] || teamId.substring(0, 8);
  }

  /**
   * V24D6O: Navigate to the V24 match detail page for a completed match.
   * The view consumes GET /api/v1/careers/{careerId}/matches/{matchId}/detail
   * and shows score, xG, timeline, playerRatings and shot map.
   */
  goToMatchDetail(matchId: string) {
    const vm = this.vmSubject.value;
    if (!vm.careerId || !matchId) {
      console.warn('[SUMMARY] Missing careerId or matchId for V24 detail navigation', vm.careerId, matchId);
      return;
    }
    this.router.navigate(['/careers', vm.careerId, 'matches', matchId, 'detail']);
  }

  playNextRound() {
    const vm = this.vmSubject.value;

    if (vm.tournamentStatus?.isFinished) {
      this.router.navigate([`/games/${vm.gameId}/champion`]);
      return;
    }

    const nextRound = vm.roundNumber + 1;

    if (vm.tournamentStatus && nextRound > vm.tournamentStatus.totalRounds) {
      this.router.navigate([`/games/${vm.gameId}/champion`]);
      return;
    }

    this.router.navigate([`/games/${vm.gameId}/round/${nextRound}/live`]);
  }

  goToChampion() {
    const vm = this.vmSubject.value;
    this.router.navigate([`/games/${vm.gameId}/champion`]);
  }

  backToGame() {
    this.router.navigate(['/squad']);
  }

  continueToNewSeason() {
    if (!confirm('¿Iniciar una nueva temporada con tu equipo actual?')) {
      return;
    }

    this.careerService.continueToNewSeason().subscribe({
      next: (response) => {
        if (response.success) {
          alert('¡Nueva temporada ' + response.season + ' iniciada!');
          this.router.navigate(['/squad']);
        } else {
          alert('Error: ' + response.message);
        }
      },
      error: (err) => {
        console.error('[SUMMARY] Error iniciando nueva temporada:', err);
        alert(err.error?.message || 'Error al iniciar nueva temporada');
      }
    });
  }
}
