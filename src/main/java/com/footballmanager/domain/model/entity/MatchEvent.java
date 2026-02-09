package com.footballmanager.domain.model.entity;

import com.footballmanager.domain.model.valueobject.*;
import com.footballmanager.domain.model.aggregate.*;

import java.util.Objects;

public class MatchEvent {
    private final EventType eventType;
    private final int minute;
    private final String playerName;
    private final String description;

    public enum EventType {
        GOAL, CARD, INJURY, SUBSTITUTION
    }

    private MatchEvent(EventType eventType, int minute, String playerName, String description) {
        this.eventType = Objects.requireNonNull(eventType, "Event type cannot be null");
        this.playerName = Objects.requireNonNull(playerName, "Player name cannot be null");
        this.description = Objects.requireNonNull(description, "Description cannot be null");
        
        validateMinute(minute);
        this.minute = minute;
    }

    public static MatchEvent of(EventType eventType, int minute, String playerName, String description) {
        return new MatchEvent(eventType, minute, playerName, description);
    }

    private void validateMinute(int minute) {
        if (minute < 0 || minute > 120) {
            throw new IllegalArgumentException("Match minute must be between 0 and 120");
        }
    }

    public EventType getEventType() {
        return eventType;
    }

    public int getMinute() {
        return minute;
    }

    public String getPlayerName() {
        return playerName;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MatchEvent that = (MatchEvent) o;
        return minute == that.minute &&
                eventType == that.eventType &&
                Objects.equals(playerName, that.playerName) &&
                Objects.equals(description, that.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventType, minute, playerName, description);
    }

    @Override
    public String toString() {
        return String.format("%d' [%s] %s: %s", minute, eventType, playerName, description);
    }
}
