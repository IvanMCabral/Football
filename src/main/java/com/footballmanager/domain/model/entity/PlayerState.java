package com.footballmanager.domain.model.entity;

import java.util.UUID;

public class PlayerState {
    private UUID playerId;
    private int energy;
    private int fatigue;
    private boolean injured;

    public PlayerState() {}
    public PlayerState(UUID playerId, int energy, int fatigue, boolean injured) {
        this.playerId = playerId;
        this.energy = energy;
        this.fatigue = fatigue;
        this.injured = injured;
    }

    public UUID getPlayerId() { return playerId; }
    public void setPlayerId(UUID playerId) { this.playerId = playerId; }
    public int getEnergy() { return energy; }
    public void setEnergy(int energy) { this.energy = energy; }
    public int getFatigue() { return fatigue; }
    public void setFatigue(int fatigue) { this.fatigue = fatigue; }
    public boolean isInjured() { return injured; }
    public void setInjured(boolean injured) { this.injured = injured; }
}
