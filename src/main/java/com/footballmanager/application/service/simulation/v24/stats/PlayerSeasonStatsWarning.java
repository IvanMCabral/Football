package com.footballmanager.application.service.simulation.v24.stats;

import java.util.Objects;

/**
 * V24D6M7: Warning object in player season stats response.
 *
 * <p>Carries a machine-readable code, human-readable message,
 * and optional field reference.
 */
public final class PlayerSeasonStatsWarning {

    private final PlayerSeasonStatsWarningCode code;
    private final String message;
    private final String field;

    public PlayerSeasonStatsWarning(PlayerSeasonStatsWarningCode code, String message, String field) {
        this.code = Objects.requireNonNull(code, "code must not be null");
        this.message = (message != null) ? message : "";
        this.field = field;
    }

    public PlayerSeasonStatsWarningCode code() { return code; }
    public String message() { return message; }
    public String field() { return field; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PlayerSeasonStatsWarning that)) return false;
        return code == that.code
                && Objects.equals(message, that.message)
                && Objects.equals(field, that.field);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, message, field);
    }

    @Override
    public String toString() {
        return "PlayerSeasonStatsWarning{code=%s, message='%s', field=%s}"
                .formatted(code, message, field);
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private PlayerSeasonStatsWarningCode code;
        private String message;
        private String field;

        public Builder code(PlayerSeasonStatsWarningCode code) { this.code = code; return this; }
        public Builder message(String message) { this.message = message; return this; }
        public Builder field(String field) { this.field = field; return this; }

        public PlayerSeasonStatsWarning build() {
            return new PlayerSeasonStatsWarning(code, message, field);
        }
    }
}