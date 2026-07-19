package com.footballmanager.application.service.simulation.v24;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.footballmanager.domain.model.entity.SessionPlayer;

import java.util.Objects;

/**
 * Lightweight player snapshot stored with V24 match detail so match-detail UI
 * can drive real DT actions (substitutions) without placeholder ids.
 */
public final class V24MatchLineupPlayerDto {

    private final String sessionPlayerId;
    private final String name;
    private final String position;
    private final int overall;
    private final int attack;
    private final int defense;
    private final int energy;
    private final int form;
    private final boolean injured;

    @JsonCreator
    public V24MatchLineupPlayerDto(
            @JsonProperty("sessionPlayerId") String sessionPlayerId,
            @JsonProperty("name") String name,
            @JsonProperty("position") String position,
            @JsonProperty("overall") int overall,
            @JsonProperty("attack") int attack,
            @JsonProperty("defense") int defense,
            @JsonProperty("energy") int energy,
            @JsonProperty("form") int form,
            @JsonProperty("injured") boolean injured) {
        this.sessionPlayerId = sessionPlayerId;
        this.name = name != null ? name : "";
        this.position = position != null ? position : "";
        this.overall = overall;
        this.attack = attack;
        this.defense = defense;
        this.energy = energy;
        this.form = form;
        this.injured = injured;
    }

    public static V24MatchLineupPlayerDto fromSessionPlayer(SessionPlayer player) {
        Objects.requireNonNull(player, "player must not be null");
        return new V24MatchLineupPlayerDto(
                player.getSessionPlayerId(),
                player.getName(),
                player.getPosition(),
                safe(player.calculateOverall(), 50),
                safe(player.getAttack(), 50),
                safe(player.getDefense(), 50),
                safe(player.getEnergy(), 100),
                safe(player.getForm(), 50),
                Boolean.TRUE.equals(player.getInjured()));
    }

    private static int safe(Integer value, int fallback) {
        return value != null ? value : fallback;
    }

    @JsonProperty("sessionPlayerId") public String sessionPlayerId() { return sessionPlayerId; }
    @JsonProperty("name") public String name() { return name; }
    @JsonProperty("position") public String position() { return position; }
    @JsonProperty("overall") public int overall() { return overall; }
    @JsonProperty("attack") public int attack() { return attack; }
    @JsonProperty("defense") public int defense() { return defense; }
    @JsonProperty("energy") public int energy() { return energy; }
    @JsonProperty("form") public int form() { return form; }
    @JsonProperty("injured") public boolean injured() { return injured; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof V24MatchLineupPlayerDto that)) return false;
        return overall == that.overall
                && attack == that.attack
                && defense == that.defense
                && energy == that.energy
                && form == that.form
                && injured == that.injured
                && Objects.equals(sessionPlayerId, that.sessionPlayerId)
                && Objects.equals(name, that.name)
                && Objects.equals(position, that.position);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessionPlayerId, name, position, overall, attack, defense, energy, form, injured);
    }
}
