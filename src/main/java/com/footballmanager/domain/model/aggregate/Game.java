package com.footballmanager.domain.model.aggregate;

import com.footballmanager.domain.model.valueobject.*;

import java.time.LocalDateTime;
import java.util.Objects;

public class Game {
    private final GameId id;
    private final UserId userId;
    private final TeamId teamId;
    private final String name;
    private final LocalDateTime createdAt;

    public Game(GameId id, UserId userId, TeamId teamId, String name, LocalDateTime createdAt) {
        this.id = id;
        this.userId = userId;
        this.teamId = teamId;
        this.name = name;
        this.createdAt = createdAt;
    }

    public static Game reconstruct(GameId id, UserId userId, TeamId teamId, String name, LocalDateTime createdAt) {
        return new Game(id, userId, teamId, name, createdAt);
    }

    public GameId getId() { return id; }
    public UserId getUserId() { return userId; }
    public TeamId getTeamId() { return teamId; }
    public String getName() { return name; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Game game = (Game) o;
        return Objects.equals(id, game.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
