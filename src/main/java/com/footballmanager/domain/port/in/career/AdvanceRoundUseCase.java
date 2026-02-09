package com.footballmanager.domain.port.in.career;

import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

public interface AdvanceRoundUseCase {
    Mono<AdvanceResult> advanceToNextRound(UUID userId, String careerId);

    record AdvanceResult(
        boolean success,
        boolean tournamentFinished,
        Object career,
        String message,
        int currentRound,
        int totalRounds,
        int season,
        String careerPhase,
        String championTeamId,
        String errorCode,
        List<Object> matchesToPlay,
        List<StandingEntry> standings
    ) {
        public static StandingEntry createStanding(String teamId, String teamName, int points, int won, int drawn, int lost) {
            return new StandingEntry(teamId, teamName, points, won, drawn, lost);
        }

        public static AdvanceResult error(String code, String msg) {
            return new AdvanceResult(false, false, null, msg, 0, 0, 0, null, null, code, List.of(), List.of());
        }

        public record StandingEntry(
            String teamId,
            String teamName,
            int points,
            int won,
            int drawn,
            int lost
        ) {}
    }
}
