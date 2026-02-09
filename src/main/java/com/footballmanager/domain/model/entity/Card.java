package com.footballmanager.domain.model.entity;

import java.util.UUID;

public class Card {
    public enum CardType { YELLOW, RED }
    private UUID playerId;
    private int minute;
    private CardType type;

    public Card() {}
    public Card(UUID playerId, int minute, CardType type) {
        this.playerId = playerId;
        this.minute = minute;
        this.type = type;
    }

    public UUID getPlayerId() { return playerId; }
    public void setPlayerId(UUID playerId) { this.playerId = playerId; }
    public int getMinute() { return minute; }
    public void setMinute(int minute) { this.minute = minute; }
    public CardType getType() { return type; }
    public void setType(CardType type) { this.type = type; }
}
