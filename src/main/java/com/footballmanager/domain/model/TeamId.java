package com.footballmanager.domain.model;

import java.util.Objects;
import java.util.UUID;

public class TeamId {
    private final UUID value;

    private TeamId(UUID value) {
        this.value = Objects.requireNonNull(value, "TeamId cannot be null");
    }

    public static TeamId of(UUID value) {
        return new TeamId(value);
    }

    public static TeamId generate() {
        return new TeamId(UUID.randomUUID());
    }

    public static TeamId fromString(String value) {
        return new TeamId(UUID.fromString(value));
    }

    public UUID getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TeamId teamId = (TeamId) o;
        return Objects.equals(value, teamId.value);
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
