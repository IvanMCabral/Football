package com.footballmanager.application.service.career;

import com.footballmanager.domain.port.in.career.AdvanceRoundUseCase;
import com.footballmanager.domain.port.in.career.ContinueSeasonUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * SeasonAdvancementService - Facade para avance de temporadas.
 *
 * REFACTORED: Delega a UseCases especializados:
 * - AdvanceRoundUseCase: Avanzar fecha
 * - ContinueSeasonUseCase: Nueva temporada
 *
 * Mantiene compatibilidad hacia atrás.
 */
@Service
@RequiredArgsConstructor
public class SeasonAdvancementService {

    private final AdvanceRoundUseCase advanceRoundUseCase;
    private final ContinueSeasonUseCase continueSeasonUseCase;

    /**
     * Avanza a la siguiente fecha.
     *
     * @deprecated Usar AdvanceRoundUseCase directamente
     */
    @Deprecated
    public Mono<AdvanceRoundUseCase.AdvanceResult> advanceToNextRound(java.util.UUID userId, String careerId) {
        return advanceRoundUseCase.advanceToNextRound(userId, careerId);
    }

    /**
     * Inicia una nueva temporada.
     *
     * @deprecated Usar ContinueSeasonUseCase directamente
     */
    @Deprecated
    public Mono<ContinueSeasonUseCase.ContinueResult> continueToNewSeason(java.util.UUID userId) {
        return continueSeasonUseCase.continueToNewSeason(userId);
    }
}
