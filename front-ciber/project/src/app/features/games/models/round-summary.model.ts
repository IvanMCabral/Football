import { Standing } from '../../../core/services/career.model';

/**
 * Estado del torneo dentro del ViewModel
 */
export interface TournamentStatus {
  currentRound: number;
  totalRounds: number;
  hasNextRound: boolean;
  isFinished: boolean;
  canPlayCurrentRound: boolean;
  season?: number;
}

/**
 * ViewModel para el partido individual en el resumen
 */
export interface SummaryMatchVM {
  matchId: string;
  homeTeamId: string;
  awayTeamId: string;
  homeGoals: number;
  awayGoals: number;
  status: string;
  isUserMatch: boolean;
}

/**
 * ViewModel completo para la pantalla de resumen de jornada
 *
 * V24D6O: Added `careerId` so completed matches can link to the
 * V24 match detail page at /careers/:careerId/matches/:matchId/detail.
 */
export interface RoundSummaryViewModel {
  gameId: string;
  roundNumber: number;
  careerId: string;
  matches: SummaryMatchVM[];
  standings: Standing[];
  teamNameMap: { [id: string]: string };
  userTeamId: string;
  userTeamName: string;
  userPosition: number;
  careerPhase: string; // PRE_MATCH, IN_MATCH, POST_MATCH, WAITING_USER, FINISHED
  tournamentStatus: TournamentStatus | null;
  errorMsg: string;
}
