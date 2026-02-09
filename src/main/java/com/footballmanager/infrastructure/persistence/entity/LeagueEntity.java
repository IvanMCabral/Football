package com.footballmanager.infrastructure.persistence.entity;

import com.footballmanager.domain.model.aggregate.League;
import com.footballmanager.domain.model.valueobject.LeagueId;
import com.footballmanager.domain.model.valueobject.TeamId;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.relational.core.mapping.Column;

import java.time.Instant;
import java.util.HashSet;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("leagues")
public class LeagueEntity {
    @Id
    private UUID id;
    private String name;
    private String country;
    private Instant createdAt;
    private Instant updatedAt;
    private String status;
    @Column("winner_id")
    private UUID winnerTeamId;

    public static LeagueEntity fromDomain(League league) {
        return LeagueEntity.builder()
            .id(league.getId().getValue())
            .name(league.getName())
            .country(league.getCountry())
            .createdAt(league.getCreatedAt())
            .updatedAt(league.getUpdatedAt())
            .status(league.getStatus() != null ? league.getStatus().name() : null)
            .winnerTeamId(league.getWinnerTeamId())
            .build();
    }

    public League toDomain() {
        // La relación league-teams se maneja en tabla separada league_teams
        // Por ahora retornamos sin teamIds, se cargarían en un adapter diferente
        return League.reconstruct(
            LeagueId.of(id),
            name,
            country,
            new HashSet<>(),
            createdAt,
            updatedAt,
            0, // No seasonId in DB
            winnerTeamId,
            status != null ? com.footballmanager.domain.model.valueobject.LeagueStatus.valueOf(status) : null
        );
    }
}


