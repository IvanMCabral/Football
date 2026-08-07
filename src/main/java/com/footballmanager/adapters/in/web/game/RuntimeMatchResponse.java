package com.footballmanager.adapters.in.web.game;

import com.footballmanager.domain.model.valueobject.MatchEvent;
import com.footballmanager.domain.model.entity.RuntimeMatch;
import com.footballmanager.domain.model.valueobject.MatchStatus;

import java.time.LocalDateTime;
import java.util.List;

/** Public representation of a live match; lifecycle fencing stays internal. */
public record RuntimeMatchResponse(
        String matchId,
        String careerId,
        String homeTeamId,
        String awayTeamId,
        int round,
        int currentMinute,
        MatchStatus status,
        int homeGoals,
        int awayGoals,
        List<MatchEvent> events,
        LocalDateTime startedAt,
        LocalDateTime finishedAt) {

    public static RuntimeMatchResponse from(RuntimeMatch match) {
        return new RuntimeMatchResponse(
                match.getMatchId(),
                match.getCareerId(),
                match.getHomeTeamId(),
                match.getAwayTeamId(),
                match.getRound(),
                match.getCurrentMinute(),
                match.getStatus(),
                match.getHomeGoals(),
                match.getAwayGoals(),
                match.getEvents(),
                match.getStartedAt(),
                match.getFinishedAt());
    }
}
