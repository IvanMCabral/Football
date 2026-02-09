package com.footballmanager.adapters.in.web.career.dto.response;

import java.util.List;

/**
 * Response DTO for advance/continue round operations.
 * Uses a generic structure to accommodate both AdvanceResult and ContinueResult.
 */
public record AdvanceRoundResponse(
        boolean success,
        boolean tournamentFinished,
        String error,
        String message,
        Integer currentRound,
        Integer totalRounds,
        Integer season,
        String careerPhase,
        String championTeamId,
        String userTeamId,
        String userTeamName,
        List<?> standings
) {}
