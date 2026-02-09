package com.footballmanager.domain.model.entity;

import com.footballmanager.domain.model.valueobject.*;
import java.io.Serializable;
import java.util.*;

public class MatchState implements Serializable {
    private UUID matchId;
    private UUID homeTeamId;
    private UUID awayTeamId;
    private String careerId;
    private String userId;
    private int currentMinute;
    private MatchStatus status;
    private Score score;
    private Tactic homeTactic;
    private Tactic awayTactic;
    private List<PlayerState> players;
    private List<Card> cards;
    private List<Substitution> substitutions;
    private List<MatchEvent> events;

    public MatchState(UUID matchId) {
        this.matchId = matchId;
        this.currentMinute = 0;
        this.status = MatchStatus.PAUSED;
        this.score = new Score();
        this.homeTactic = Tactic.BALANCED;
        this.awayTactic = Tactic.BALANCED;
        this.players = new ArrayList<>();
        this.cards = new ArrayList<>();
        this.substitutions = new ArrayList<>();
        this.events = new ArrayList<>();
    }

    public UUID getMatchId() { return matchId; }
    public UUID getHomeTeamId() { return homeTeamId; }
    public void setHomeTeamId(UUID homeTeamId) { this.homeTeamId = homeTeamId; }
    public UUID getAwayTeamId() { return awayTeamId; }
    public void setAwayTeamId(UUID awayTeamId) { this.awayTeamId = awayTeamId; }
    public String getCareerId() { return careerId; }
    public void setCareerId(String careerId) { this.careerId = careerId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public int getCurrentMinute() { return currentMinute; }
    public void setCurrentMinute(int minute) { this.currentMinute = minute; }
    public MatchStatus getStatus() { return status; }
    public void setStatus(MatchStatus status) { this.status = status; }
    public Score getScore() { return score; }
    public void setScore(Score score) { this.score = score; }
    public Tactic getHomeTactic() { return homeTactic; }
    public void setHomeTactic(Tactic tactic) { this.homeTactic = tactic; }
    public Tactic getAwayTactic() { return awayTactic; }
    public void setAwayTactic(Tactic tactic) { this.awayTactic = tactic; }
    public List<PlayerState> getPlayers() { return players; }
    public void setPlayers(List<PlayerState> players) {
        this.players.clear();
        if (players != null) {
            this.players.addAll(players);
        }
    }
    public List<Card> getCards() { return cards; }
    public void setCards(List<Card> cards) {
        this.cards.clear();
        if (cards != null) {
            this.cards.addAll(cards);
        }
    }
    public List<Substitution> getSubstitutions() { return substitutions; }
    public void setSubstitutions(List<Substitution> substitutions) {
        this.substitutions.clear();
        if (substitutions != null) {
            this.substitutions.addAll(substitutions);
        }
    }
    public List<MatchEvent> getEvents() { return events; }
    public void setEvents(List<MatchEvent> events) {
        this.events.clear();
        if (events != null) {
            this.events.addAll(events);
        }
    }
}
