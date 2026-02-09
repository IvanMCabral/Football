package com.footballmanager.adapters.in.web.career.mappers;

import com.footballmanager.adapters.in.web.career.dto.response.AdvanceRoundResponse;
import com.footballmanager.domain.port.in.career.ContinueSeasonUseCase.ContinueResult;

/**
 * Builder for continueToNewSeason response DTOs.
 */
public final class ContinueSeasonResponseBuilder {

    private ContinueSeasonResponseBuilder() {}

    public static AdvanceRoundResponse buildResponse(ContinueResult result) {
        if (result.success()) {
            return new AdvanceRoundResponse(
                    true,
                    false,
                    null,
                    result.message(),
                    result.currentRound(),
                    result.totalRounds(),
                    result.newSeason(),
                    result.phase(),
                    null,
                    result.userSessionTeamId(),
                    result.userTeamName(),
                    null
            );
        } else {
            return new AdvanceRoundResponse(
                    false,
                    false,
                    null,
                    result.message(),
                    null, null, null, null, null, null, null, null
            );
        }
    }

    public static AdvanceRoundResponse notFoundResponse() {
        return new AdvanceRoundResponse(
                false,
                false,
                "CARRERA_NO_ENCONTRADA",
                "No se encontro la carrera",
                null, null, null, null, null, null, null, null
        );
    }
}
