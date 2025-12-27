package com.footballmanager.infrastructure.persistence;

import com.footballmanager.domain.model.League;
import com.footballmanager.domain.model.LeagueId;
import com.footballmanager.domain.model.TeamId;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

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
    
    public static LeagueEntity fromDomain(League league) {
        return LeagueEntity.builder()
            .id(league.getId().getValue())
            .name(league.getName())
            .country(league.getCountry())
            .createdAt(league.getCreatedAt())
            .updatedAt(league.getUpdatedAt())
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
            updatedAt
        );
    }
}
