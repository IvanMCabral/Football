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
                    // CareerSave.getUserDivision() returns the entity Division (1/2/3);
                    // map divisionNumber → tier name (PRIMERA/SEGUNDA/TERCERA) so the
                    // frontend can branch on a stable string without parsing localized
                    // display names. Returns null for legacy careers (pre-C55.2 with no
                    // divisions assigned).
                    String userDivision = null;
                    Division division = career.getUserDivision();
                    if (division != null) {
                        userDivision = switch (division.getDivisionNumber()) {
                            case 1 -> "PRIMERA";
                            case 2 -> "SEGUNDA";
                            case 3 -> "TERCERA";
                            default -> null;
                        };
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
                            null,
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

                    log.debug("[STATUS] userId={}, currentRound={}, careerPhase={}, totalRounds={}, userDivision={}, promotionsAvailable={}",
                        userId, dto.currentRound(), dto.careerPhase(), dto.totalRounds(), dto.userDivision(), dto.promotionsAvailable());

                    return dto;
                })
                .switchIfEmpty(Mono.just(new CareerStatusDto(
                        null, 0, 0, 0, null, null, null, false, null, "IDLE", true, "NO_CAREER", 0, 0, null, false
                )));
    }
}
