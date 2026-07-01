package com.footballmanager.application.service.career;

import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.Division;
import com.footballmanager.domain.port.in.career.GetCareerStatusUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Implementación del UseCase para consultar estado de carrera.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GetCareerStatusUseCaseImpl implements GetCareerStatusUseCase {

    private final CareerSessionService sessionService;

    @Override
    public Mono<CareerStatusDto> execute(UUID userId) {
        return sessionService.getCareerFromCache(userId)
                .map(career -> {
                    String sessionTeamId = career.getUserSessionTeamId();
                    int squadSize = sessionTeamId != null ? career.getTeamSquad(sessionTeamId).size() : 0;
                    int freePlayersCount = career.getPlayerManager().getFreePlayerIds().size();

                    // V25D78-C55.2 phase 4 UI (c): expose user's division tier so
                    // the dashboard can render it prominent without a 2nd round-trip.
                    // CareerSave.getUserDivision() returns the entity Division; we
                    // map divisionNumber → tier name (PRIMERA/SEGUNDA/TERCERA/...)
                    // so the frontend can branch on a stable string without parsing
                    // localized display names.
                    //
                    // V25D78-C55.9: extend to all 12 sub-divisions per league (C55.6
                    // distribution: 60 teams / teamsPerDivision=5 → 12 divisions).
                    // The previous switch only handled 1-3 and returned null for
                    // divisionNumber 4-12, leaving the field null for the bottom 75%
                    // of teams (per C55.7.3 smoke gap #8). For 4-12 we map to Spanish
                    // ordinal tier names so the frontend gets a non-null label
                    // regardless of tier. Post-promotion, the user's division may
                    // shift up or down (see PromotionExecutor.execute → DivisionManager
                    // .moveTeam), and the next /career/status call will reflect the new
                    // division because getUserDivision() is computed lazily from
                    // seasonManager.findDivisionByTeamId(userSessionTeamId).
                    //
                    // Returns null for legacy careers (pre-C55.2 with no divisions
                    // assigned) — seasonManager.divisions is empty in that case.
                    String userDivision = null;
                    Division division = career.getUserDivision();
                    if (division != null) {
                        userDivision = switch (division.getDivisionNumber()) {
                            case 1 -> "PRIMERA";
                            case 2 -> "SEGUNDA";
                            case 3 -> "TERCERA";
                            case 4 -> "CUARTA";
                            case 5 -> "QUINTA";
                            case 6 -> "SEXTA";
                            case 7 -> "SEPTIMA";
                            case 8 -> "OCTAVA";
                            case 9 -> "NOVENA";
                            case 10 -> "DECIMA";
                            case 11 -> "UNDECIMA";
                            case 12 -> "DUODECIMA";
                            default -> null;
                        };
                    }

                    // V25D78-C55.9 (A9 fix): expose the human-readable team name so
                    // the dashboard / squad / any consumer can show it without a
                    // 2nd round-trip to /career/continue or a WorldTeam lookup.
                    // Lazy computed from the in-memory sessionTeam the user chose at
                    // career-start (CareerSave.getSessionTeam(userSessionTeamId)).
                    // Matches ContinueSeasonUseCaseImpl.userTeamName derivation so the
                    // two endpoints stay consistent (per C55.7.3 gap #9 / #10).
                    // Returns null when the session team cannot be resolved so the
                    // frontend can show a "no team" placeholder if it ever appears
                    // (defensive — in practice getSessionTeamId is always set once
                    // career-start completes).
                    String userTeamName = null;
                    if (sessionTeamId != null) {
                        var sessionTeam = career.getSessionTeam(sessionTeamId);
                        if (sessionTeam != null) {
                            userTeamName = sessionTeam.getName();
                        }
                    }

                    // V25D78-C55.2 phase 4 UI (d2): auto-trigger promotions dialog.
                    // PromotionRelegationService.calculatePromotionsAndRelegations() (called
                    // inside MatchSimulationOrchestrator.finishTournament) populates
                    // seasonManager.promotions when a season ends. We surface that as a
                    // boolean so the frontend can decide when to open the dialog.
                    boolean promotionsAvailable = !career.getPromotions().isEmpty();

                    CareerStatusDto dto = new CareerStatusDto(
                            career.getCareerId().toString(),
                            career.getCurrentSeason(),
                            career.getTournamentState().getCurrentRound(),
                            career.getTournamentState().getTotalRounds(),
                            career.getUserTeamId() != null ? career.getUserTeamId().toString() : null,
                            sessionTeamId,
                            userTeamName,
                            false,
                            null,
                            "IDLE",
                            true,
                            career.getTournamentState().getCareerPhase().name(),
                            squadSize,
                            freePlayersCount,
                            userDivision,
                            promotionsAvailable
                    );

                    log.debug("[STATUS] userId={}, currentRound={}, careerPhase={}, totalRounds={}, userDivision={}, userTeamName={}, promotionsAvailable={}",
                        userId, dto.currentRound(), dto.careerPhase(), dto.totalRounds(), dto.userDivision(), dto.userTeamName(), dto.promotionsAvailable());

                    return dto;
                })
                .switchIfEmpty(Mono.just(new CareerStatusDto(
                        null, 0, 0, 0, null, null, null, false, null, "IDLE", true, "NO_CAREER", 0, 0, null, false
                )));
    }
}
