package com.footballmanager.infrastructure.persistence.entity;

import com.footballmanager.domain.model.aggregate.Team;
import com.footballmanager.domain.model.valueobject.Division;
import com.footballmanager.domain.model.valueobject.TeamId;
import com.footballmanager.domain.model.valueobject.UserId;
import com.footballmanager.domain.model.valueobject.Formation;
import com.footballmanager.domain.model.valueobject.PlayerId;
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
    /**
     * V25D78-C55.2: división (tier) del equipo dentro de su liga.
     * Persistido como String (VARCHAR(20) en Postgres) — Division enum
     * usa {@link Division#persistValue()} para serializar y
     * {@link Division#fromPersistValue(String)} para deserializar.
     * Default = "PRIMERA" si la fila es pre-V25D78 (no-nullable column
     * with DEFAULT 'PRIMERA' en la migration).
     */
    private String division;
    private Instant createdAt;
    private Instant updatedAt;

    public static TeamEntity fromDomainForInsert(Team team) {
        return new TeamEntity(
                null,  // Let database generate ID
                team.getManagerId().getValue(),
                team.getName(),
                team.getCountry(),
                team.getBudget(),
                team.getFormation().name(),
                team.getDivision().persistValue(),
                team.getCreatedAt(),
                team.getUpdatedAt()
        );
    }

    public static TeamEntity fromDomain(Team team) {
        return new TeamEntity(
                team.getId().getValue(),
                team.getManagerId().getValue(),
                team.getName(),
                team.getCountry(),
                team.getBudget(),
                team.getFormation().name(),
                team.getDivision().persistValue(),
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
                Division.fromPersistValue(division),
                playerIds,
                createdAt,
                updatedAt
        );
    }
}

