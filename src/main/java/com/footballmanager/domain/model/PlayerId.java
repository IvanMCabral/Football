package com.footballmanager.domain.model;

import java.util.Objects;
import java.util.UUID;

public class PlayerId {
    private final UUID value;

    private PlayerId(UUID value) {
        this.value = Objects.requireNonNull(value, "PlayerId cannot be null");
    }

    public static PlayerId of(UUID value) {
        return new PlayerId(value);
    }

    public static PlayerId generate() {
        return new PlayerId(UUID.randomUUID());
    }

    public static PlayerId fromString(String value) {
        return new PlayerId(UUID.fromString(value));
    }

    public UUID getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlayerId playerId = (PlayerId) o;
        return Objects.equals(value, playerId.value);
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
