package com.footballmanager.application.service.career;

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
                            freePlayersCount
                    );

                    log.debug("[STATUS] userId={}, currentRound={}, careerPhase={}, totalRounds={}",
                        userId, dto.currentRound(), dto.careerPhase(), dto.totalRounds());

                    return dto;
                })
                .switchIfEmpty(Mono.just(new CareerStatusDto(
                        null, 0, 0, 0, null, null, null, false, null, "IDLE", true, "NO_CAREER", 0, 0
                )));
    }
}
