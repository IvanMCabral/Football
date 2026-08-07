package com.footballmanager.adapters.in.web.versus;

import com.footballmanager.domain.model.valueobject.MatchEvent;
import com.footballmanager.domain.model.entity.RuntimeMatch;
import com.footballmanager.domain.model.valueobject.MatchStatus;

import java.time.LocalDateTime;
import java.util.List;

/** Public live-match response without internal lifecycle fencing metadata. */
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
                match.getMatchId(), match.getCareerId(), match.getHomeTeamId(),
                match.getAwayTeamId(), match.getRound(), match.getCurrentMinute(),
                match.getStatus(), match.getHomeGoals(), match.getAwayGoals(),
                match.getEvents(), match.getStartedAt(), match.getFinishedAt());
    }
}
