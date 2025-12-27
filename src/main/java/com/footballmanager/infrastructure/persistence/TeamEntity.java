package com.footballmanager.infrastructure.persistence;

import com.footballmanager.domain.model.Team;
import com.footballmanager.domain.model.TeamId;
import com.footballmanager.domain.model.UserId;
import com.footballmanager.domain.model.Formation;
import com.footballmanager.domain.model.PlayerId;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Table("teams")
public class TeamEntity {
    @Id
    private UUID id;
    private UUID managerId;
    private String name;
    private String country;
    private BigDecimal budget;
    private String formation;
    private Instant createdAt;
    private Instant updatedAt;

    public static TeamEntity fromDomain(Team team) {
        return new TeamEntity(
                team.getId().getValue(),
                team.getManagerId().getValue(),
                team.getName(),
                team.getCountry(),
                team.getBudget(),
                team.getFormation().name(),
                team.getCreatedAt(),
                team.getUpdatedAt()
        );
    }

    public Team toDomain(Set<PlayerId> playerIds) {
        return Team.reconstruct(
                TeamId.of(id),
                UserId.of(managerId),
                name,
                country,
                budget,
                Formation.fromString(formation),
                playerIds,
                createdAt,
                updatedAt
        );
    }
}
