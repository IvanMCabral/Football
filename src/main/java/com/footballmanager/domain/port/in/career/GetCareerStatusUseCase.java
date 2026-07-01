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
     *
     * <p><b>V25D78-C55.2 phase 4 UI (front consume)</b>: added
     * {@code userDivision} (PRIMERA/SEGUNDA/TERCERA) so the dashboard
     * can render the user's tier prominent without a second round-trip.
     *
     * <p><b>V25D78-C55.2 phase 4 UI (auto-trigger d2)</b>: added
     * {@code promotionsAvailable} (true when CareerSave.promotions is
     * non-empty — i.e. a season just ended and the engine computed
     * promotion/relegation movements but the user hasn't seen them yet).
     * The frontend uses localStorage to mark promotions as viewed so the
     * dialog doesn't re-pop on every reload. Lazy computed from existing
     * CareerSave state, no engine change required.
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
            int freePlayersCount,
            String userDivision,
            boolean promotionsAvailable
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
