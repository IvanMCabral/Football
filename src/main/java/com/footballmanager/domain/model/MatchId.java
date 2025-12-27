package com.footballmanager.domain.model;

import java.util.Objects;
import java.util.UUID;

public class MatchId {
    private final UUID value;

    private MatchId(UUID value) {
        this.value = Objects.requireNonNull(value, "MatchId cannot be null");
    }

    public static MatchId of(UUID value) {
        return new MatchId(value);
    }

    public static MatchId generate() {
        return new MatchId(UUID.randomUUID());
    }

    public static MatchId fromString(String value) {
        return new MatchId(UUID.fromString(value));
    }

    public UUID getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MatchId matchId = (MatchId) o;
        return Objects.equals(value, matchId.value);
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
