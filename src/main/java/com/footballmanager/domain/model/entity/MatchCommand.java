package com.footballmanager.domain.model.entity;

import com.footballmanager.domain.model.valueobject.*;
import java.io.Serializable;
import java.util.UUID;

public class MatchCommand implements Serializable {
    private MatchCommandType type;
    private UUID teamId;
    private boolean isHomeTeam;
    private Tactic tactic;
    private UUID playerOut;
    private UUID playerIn;
    private Object payload;

    public MatchCommand() {}

    public MatchCommand(MatchCommandType type, UUID teamId, boolean isHomeTeam, Object payload) {
        this.type = type;
        this.teamId = teamId;
        this.isHomeTeam = isHomeTeam;
        this.payload = payload;
    }

    public MatchCommandType getType() { return type; }
    public void setType(MatchCommandType type) { this.type = type; }
    public UUID getTeamId() { return teamId; }
    public void setTeamId(UUID teamId) { this.teamId = teamId; }
    public boolean isHomeTeam() { return isHomeTeam; }
    public void setHomeTeam(boolean homeTeam) { this.isHomeTeam = homeTeam; }
    public Tactic getTactic() { return tactic; }
    public void setTactic(Tactic tactic) { this.tactic = tactic; }
    public UUID getPlayerOut() { return playerOut; }
    public void setPlayerOut(UUID playerOut) { this.playerOut = playerOut; }
    public UUID getPlayerIn() { return playerIn; }
    public void setPlayerIn(UUID playerIn) { this.playerIn = playerIn; }
    public Object getPayload() { return payload; }
    public void setPayload(Object payload) { this.payload = payload; }
}
