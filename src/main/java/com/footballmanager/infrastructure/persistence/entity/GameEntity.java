package com.footballmanager.infrastructure.persistence.entity;

import com.footballmanager.domain.model.aggregate.Game;
import com.footballmanager.domain.model.valueobject.GameId;
import com.footballmanager.domain.model.valueobject.TeamId;
import com.footballmanager.domain.model.valueobject.UserId;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Table("games")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Setter
public class GameEntity {
    @Id
    private UUID id;
    private UUID userId;
    private UUID teamId;
    private String name;
    private LocalDateTime createdAt;

    public static GameEntity fromDomain(Game game) {
        return new GameEntity(
            game.getId() != null ? game.getId().getValue() : null,
            game.getUserId() != null ? game.getUserId().getValue() : null,
            game.getTeamId() != null ? game.getTeamId().getValue() : null,
            game.getName(),
            game.getCreatedAt()
        );
    }

    public Game toDomain() {
        return Game.reconstruct(
            id != null ? GameId.of(id) : null,
            userId != null ? UserId.of(userId) : null,
            teamId != null ? TeamId.of(teamId) : null,
            name,
            createdAt
        );
    }
}
