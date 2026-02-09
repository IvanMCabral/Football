package com.footballmanager.adapters.in.web.career.mappers;

import com.footballmanager.adapters.in.web.career.dto.response.AdvanceRoundResponse;
import com.footballmanager.domain.port.in.career.AdvanceRoundUseCase.AdvanceResult;

/**
 * Builder for advanceToNextRound response DTOs.
 */
public final class AdvanceRoundResponseBuilder {

    private AdvanceRoundResponseBuilder() {}

    public static AdvanceRoundResponse buildResponse(AdvanceResult result) {
        if (result.success()) {
            return new AdvanceRoundResponse(
                    true,
                    result.tournamentFinished(),
                    null,
                    result.message(),
                    result.currentRound(),
                    result.totalRounds(),
                    result.season(),
                    result.careerPhase(),
                    result.championTeamId(),
                    null,
                    null,
                    result.standings()
            );
        } else {
            return new AdvanceRoundResponse(
                    false,
                    false,
                    result.errorCode(),
                    result.message(),
                    null, null, null, null, null, null, null, null
            );
        }
    }

    public static AdvanceRoundResponse notFoundResponse(String careerId) {
        return new AdvanceRoundResponse(
                false,
                false,
                "CARRERA_NO_ENCONTRADA",
                "No se encontro la carrera: " + careerId,
                null, null, null, null, null, null, null, null
        );
    }
}
