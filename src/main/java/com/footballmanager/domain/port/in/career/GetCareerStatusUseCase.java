package com.footballmanager.domain.port.in.career;

import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Puerto de entrada para consultar el estado de una carrera.
 */
public interface GetCareerStatusUseCase {

    Mono<CareerStatusDto> execute(UUID userId);

    /**
     * DTO de respuesta para estado de carrera.
     */
    record CareerStatusDto(
            String careerId,
            int season,
            int currentRound,
            int totalRounds,
            String userTeamId,
            String userSessionTeamId,
            String userTeamName,
            boolean hasLastMatchPlayed,
            String nextMatchId,
            String engineStatus,
            boolean canAdvanceRound,
            String careerPhase,
            int squadSize,
            int freePlayersCount
    ) {}

    /**
     * DTO de respuesta para estado de carrera.
     */
    record CareerStatus(
            String careerId,
            int season,
            int currentRound,
            int totalRounds,
            String userTeamId,
            String userSessionTeamId,
            String userTeamName,
            boolean hasLastMatchPlayed,
            String nextMatchId,
            String engineStatus,
            boolean canAdvanceRound,
            String careerPhase,
            int squadSize,
            int freePlayersCount
    ) {}
}
