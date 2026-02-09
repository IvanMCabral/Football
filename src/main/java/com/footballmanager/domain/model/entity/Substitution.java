package com.footballmanager.domain.model.entity;

import java.util.UUID;

public class Substitution {
    private int minute;
    private UUID playerOut;
    private UUID playerIn;

    public Substitution() {}
    public Substitution(int minute, UUID playerOut, UUID playerIn) {
        this.minute = minute;
        this.playerOut = playerOut;
        this.playerIn = playerIn;
    }

    public int getMinute() { return minute; }
    public void setMinute(int minute) { this.minute = minute; }
    public UUID getPlayerOut() { return playerOut; }
    public void setPlayerOut(UUID playerOut) { this.playerOut = playerOut; }
    public UUID getPlayerIn() { return playerIn; }
    public void setPlayerIn(UUID playerIn) { this.playerIn = playerIn; }
}
