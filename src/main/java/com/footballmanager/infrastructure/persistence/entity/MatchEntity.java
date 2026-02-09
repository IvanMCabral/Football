package com.footballmanager.infrastructure.persistence.entity;

import com.footballmanager.domain.model.entity.Match;
import com.footballmanager.domain.model.entity.MatchResult;
import com.footballmanager.domain.model.valueobject.GameId;
import com.footballmanager.domain.model.valueobject.MatchId;
import com.footballmanager.domain.model.valueobject.TeamId;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("matches")
public class MatchEntity {
    
    @Id
    private UUID id;
    private UUID gameId;
    private UUID homeTeamId;
    private Integer round;
    private UUID awayTeamId;
    private Instant scheduledAt;
    private String status;
    // Match result fields
    private Integer homeGoals;
    private Integer awayGoals;
    private Integer homePossession;
    private Integer awayPossession;
    private Integer homeShots;
    private Integer awayShots;
    private Instant createdAt;
    private Instant simulatedAt;
    
    public static MatchEntity fromDomain(Match match) {
        MatchEntity entity = MatchEntity.builder()
            .id(match.getId().getValue())
            .gameId(match.getGameId() != null ? match.getGameId().getValue() : null)
            .homeTeamId(match.getHomeTeamId().getValue())
            .awayTeamId(match.getAwayTeamId().getValue())
            .scheduledAt(match.getScheduledAt())
            .status(match.getStatus().name())
            .createdAt(match.getCreatedAt())
            .simulatedAt(match.getSimulatedAt())
            .round(match.getRound())
            .build();
        
        if (match.getResult() != null) {
            MatchResult result = match.getResult();
            entity.setHomeGoals(result.getHomeGoals());
            entity.setAwayGoals(result.getAwayGoals());
            entity.setHomePossession(result.getHomePossession());
            entity.setAwayPossession(result.getAwayPossession());
            entity.setHomeShots(result.getHomeShots());
            entity.setAwayShots(result.getAwayShots());
        }
        
        return entity;
    }
    
    public Match toDomain() {
        MatchResult result = null;
        if (homeGoals != null && awayGoals != null) {
            result = MatchResult.of(
                homeGoals,
                awayGoals,
                homePossession != null ? homePossession : 50,
                awayPossession != null ? awayPossession : 50,
                homeShots != null ? homeShots : 0,
                awayShots != null ? awayShots : 0,
                new ArrayList<>(),
                null
            );
        }
        
        return Match.reconstruct(
            MatchId.of(id),
            TeamId.of(homeTeamId),
            TeamId.of(awayTeamId),
            scheduledAt,
            com.footballmanager.domain.model.valueobject.MatchStatus.valueOf(status),
            result,
            createdAt,
            simulatedAt,
            gameId != null ? new GameId(gameId) : null,
            round
        );
    }
}
