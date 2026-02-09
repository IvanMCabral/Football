package com.footballmanager.domain.model.valueobject;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Objects;
import java.util.UUID;

public class LeagueId {
    private final UUID value;

    private LeagueId(UUID value) {
        this.value = Objects.requireNonNull(value, "LeagueId cannot be null");
    }

    public static LeagueId of(UUID value) {
        return new LeagueId(value);
    }

    public static LeagueId generate() {
        return new LeagueId(UUID.randomUUID());
    }

    @JsonCreator
    public static LeagueId fromString(String value) {
        return new LeagueId(UUID.fromString(value));
    }

    @JsonValue
    public UUID getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LeagueId leagueId = (LeagueId) o;
        return Objects.equals(value, leagueId.value);
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
