package com.footballmanager.application.service.simulation.v24;

import com.footballmanager.domain.model.entity.SessionPlayer;

import java.util.Objects;

/**
 * Mutable per-player match state, copied from SessionPlayer at match start.
 * SessionPlayer itself is NOT mutated.
 */
public class V24PlayerMatchState {

    private final String sessionPlayerId;
    private String teamId;
    private final String name;
    private final String position;
    private final int attack;
    private final int defense;
    private final int technique;
    private final int speed;
    private final int stamina;
    private final int mentality;
    private int currentStamina;
    private final int form;
    private int yellowCards;
    private boolean redCard;
    private boolean injured;
    private boolean onPitch;

    private V24PlayerMatchState(
            String sessionPlayerId, String teamId, String name, String position,
            int attack, int defense, int technique, int speed, int stamina, int mentality,
            int currentStamina, int form, int yellowCards, boolean redCard,
            boolean injured, boolean onPitch) {
        this.sessionPlayerId = sessionPlayerId;
        this.teamId = teamId;
        this.name = name;
        this.position = position;
        this.attack = attack;
        this.defense = defense;
        this.technique = technique;
        this.speed = speed;
        this.stamina = stamina;
        this.mentality = mentality;
        this.currentStamina = currentStamina;
        this.form = form;
        this.yellowCards = yellowCards;
        this.redCard = redCard;
        this.injured = injured;
        this.onPitch = onPitch;
    }

    public static V24PlayerMatchState fromSessionPlayer(SessionPlayer player, String teamId) {
        Objects.requireNonNull(player, "player must not be null");
        Objects.requireNonNull(teamId, "teamId must not be null");
        String name = (player.getName() == null || player.getName().isBlank())
                ? "Unknown Player" : player.getName();
        return new V24PlayerMatchState(
                player.getSessionPlayerId(),
                teamId,
                name,
                player.getPosition(),
                intOr(player.getAttack(), 50),
                intOr(player.getDefense(), 50),
                intOr(player.getTechnique(), 50),
                intOr(player.getSpeed(), 50),
                intOr(player.getStamina(), 50),
                intOr(player.getMentality(), 50),
                intOr(player.getEnergy(), 100),
                intOr(player.getForm(), 50),
                0, false,
                player.getInjured() != null && player.getInjured(),
                true  // onPitch initially
        );
    }

    private static int intOr(Integer v, int fallback) {
        return v != null ? v : fallback;
    }

    // Getters
    public String sessionPlayerId() { return sessionPlayerId; }
    public String teamId() { return teamId; }
    public String name() { return name; }
    public String position() { return position; }
    public int attack() { return attack; }
    public int defense() { return defense; }
    public int technique() { return technique; }
    public int speed() { return speed; }
    public int stamina() { return stamina; }
    public int mentality() { return mentality; }
    public int currentStamina() { return currentStamina; }
    public int form() { return form; }
    public int yellowCards() { return yellowCards; }
    public boolean redCard() { return redCard; }
    public boolean injured() { return injured; }
    public boolean onPitch() { return onPitch; }

    // Setters (for match simulation mutability)
    public void setTeamId(String teamId) { this.teamId = teamId; }

    public void drainStamina(int amount) {
        currentStamina = Math.max(0, currentStamina - amount);
    }

    public void addYellowCard() {
        yellowCards++;
        if (yellowCards >= 2) {
            redCard = true;
            onPitch = false;
        }
    }

    public void giveRedCard() {
        redCard = true;
        onPitch = false;
    }

    public void injure() {
        injured = true;
        onPitch = false;
    }

    public void substituteOff() {
        onPitch = false;
    }
}