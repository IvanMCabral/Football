package com.footballmanager.domain.port.in.career;

import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ContinueSeasonUseCase {
    Mono<ContinueResult> continueToNewSeason(UUID userId);

    record ContinueResult(
        boolean success,
        Object career,
        String message,
        int newSeason,
        int currentRound,
        int totalRounds,
        String phase,
        String userSessionTeamId,
        String userTeamName
    ) {
        public static ContinueResult error(String code, String msg) {
            return new ContinueResult(false, null, msg, 0, 0, 0, null, null, null);
        }
    }
}
