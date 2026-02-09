package com.footballmanager.domain.model.valueobject;

import java.util.Objects;
import java.util.UUID;

public class GameId {
    private final UUID value;

    public GameId(UUID value) {
        this.value = value;
    }

    public static GameId randomId() {
        return new GameId(UUID.randomUUID());
    }

    public static GameId of(UUID value) {
        return value != null ? new GameId(value) : null;
    }

    public UUID getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GameId gameId = (GameId) o;
        return Objects.equals(value, gameId.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
