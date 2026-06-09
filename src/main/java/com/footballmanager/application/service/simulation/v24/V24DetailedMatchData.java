package com.footballmanager.application.service.simulation.v24;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * V24D4A: Immutable snapshot of detailed match data for storage/API layers.
 * Not tied to internal V24DetailedMatchResult — this is a DTO/snapshot object.
 *
 * <p>Stored in Redis as JSON snapshot at key:
 * {@code career:{careerId}:match-detail:{matchId}}
 *
 * <p>schemaVersion: 1 — for future migrations.
 * engineVersion: "V24" — identifies the engine that produced this data.
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.NONE,
        getterVisibility = JsonAutoDetect.Visibility.PUBLIC_ONLY,
        isGetterVisibility = JsonAutoDetect.Visibility.PUBLIC_ONLY,
        setterVisibility = JsonAutoDetect.Visibility.PUBLIC_ONLY,
        creatorVisibility = JsonAutoDetect.Visibility.PUBLIC_ONLY)
public final class V24DetailedMatchData {

    private final String matchId;
    private final String careerId;
    private final Integer seasonNumber;
    private final Integer round;
    private final String homeTeamId;
    private final String awayTeamId;
    private final String homeTeamName;
    private final String awayTeamName;
    private final int homeGoals;
    private final int awayGoals;
    private final double homeXg;
    private final double awayXg;
    private final int homeShots;
    private final int awayShots;
    private final int homePossession;
    private final int awayPossession;
    private final List<V24MatchEventDto> timeline;
    private final List<V24PlayerMatchRatingDto> playerRatings;
    private final String summary;
    private final String engineVersion;
    private final int schemaVersion;
    private final Instant createdAt;

    @JsonCreator
    public V24DetailedMatchData(
            @JsonProperty("matchId") String matchId,
            @JsonProperty("careerId") String careerId,
            @JsonProperty("seasonNumber") Integer seasonNumber,
            @JsonProperty("round") Integer round,
            @JsonProperty("homeTeamId") String homeTeamId,
            @JsonProperty("awayTeamId") String awayTeamId,
            @JsonProperty("homeTeamName") String homeTeamName,
            @JsonProperty("awayTeamName") String awayTeamName,
            @JsonProperty("homeGoals") int homeGoals,
            @JsonProperty("awayGoals") int awayGoals,
            @JsonProperty("homeXg") double homeXg,
            @JsonProperty("awayXg") double awayXg,
            @JsonProperty("homeShots") int homeShots,
            @JsonProperty("awayShots") int awayShots,
            @JsonProperty("homePossession") int homePossession,
            @JsonProperty("awayPossession") int awayPossession,
            @JsonProperty("timeline") List<V24MatchEventDto> timeline,
            @JsonProperty("playerRatings") List<V24PlayerMatchRatingDto> playerRatings,
            @JsonProperty("summary") String summary,
            @JsonProperty("engineVersion") String engineVersion,
            @JsonProperty("schemaVersion") int schemaVersion,
            @JsonProperty("createdAt") Instant createdAt) {
        this.matchId = matchId; // Null/no-blank validation done in fromResult() factory
        this.careerId = careerId; // Null/no-blank validation done in fromResult() factory
        this.seasonNumber = seasonNumber;
        this.round = round;
        this.homeTeamId = homeTeamId;
        this.awayTeamId = awayTeamId;
        this.homeTeamName = (homeTeamName != null) ? homeTeamName : "";
        this.awayTeamName = (awayTeamName != null) ? awayTeamName : "";
        if (homeGoals < 0 || awayGoals < 0) {
            throw new IllegalArgumentException("goals must be non-negative");
        }
        if (!Double.isFinite(homeXg) || homeXg < 0) {
            throw new IllegalArgumentException("homeXg must be >= 0 and finite");
        }
        if (!Double.isFinite(awayXg) || awayXg < 0) {
            throw new IllegalArgumentException("awayXg must be >= 0 and finite");
        }
        if (homeShots < 0 || awayShots < 0) {
            throw new IllegalArgumentException("shots must be non-negative");
        }
        if (homePossession < 0 || homePossession > 100 || awayPossession < 0 || awayPossession > 100) {
            throw new IllegalArgumentException("possession must be between 0 and 100");
        }
        this.homeGoals = homeGoals;
        this.awayGoals = awayGoals;
        this.homeXg = homeXg;
        this.awayXg = awayXg;
        this.homeShots = homeShots;
        this.awayShots = awayShots;
        this.homePossession = homePossession;
        this.awayPossession = awayPossession;
        this.timeline = (timeline != null) ? Collections.unmodifiableList(new ArrayList<>(timeline)) : Collections.emptyList();
        this.playerRatings = (playerRatings != null) ? Collections.unmodifiableList(new ArrayList<>(playerRatings)) : Collections.emptyList();
        this.summary = (summary != null) ? summary : "";
        this.engineVersion = (engineVersion != null) ? engineVersion : "V24";
        this.schemaVersion = schemaVersion;
        this.createdAt = (createdAt != null) ? createdAt : Instant.now();
    }

    /**
     * Factory to build V24DetailedMatchData from a V24DetailedMatchResult and player ratings.
     * shotCoordinate will be null in timeline events until V24D3C attaches coordinates to events.
     */
    public static V24DetailedMatchData fromResult(
            String careerId,
            Integer seasonNumber,
            Integer round,
            String homeTeamName,
            String awayTeamName,
            V24DetailedMatchResult result,
            List<V24PlayerMatchRatingDto> playerRatings) {
        Objects.requireNonNull(careerId, "careerId must not be null");
        if (careerId.isBlank()) {
            throw new IllegalArgumentException("careerId must not be blank");
        }
        Objects.requireNonNull(result, "result must not be null");
        String matchId = result.matchId();
        if (matchId == null) {
            throw new NullPointerException("result.matchId must not be null");
        }
        if (matchId.isBlank()) {
            throw new IllegalArgumentException("result.matchId must not be blank");
        }

        List<V24MatchEventDto> eventDtos = new ArrayList<>();
        for (V24MatchEvent event : result.timeline().events()) {
            eventDtos.add(V24MatchEventDto.fromEvent(event));
        }

        return new V24DetailedMatchData(
                result.matchId(),
                careerId,
                seasonNumber,
                round,
                result.homeTeamId(),
                result.awayTeamId(),
                homeTeamName,
                awayTeamName,
                result.homeGoals(),
                result.awayGoals(),
                result.homeXg(),
                result.awayXg(),
                result.homeShots(),
                result.awayShots(),
                result.homePossession(),
                result.awayPossession(),
                eventDtos,
                playerRatings,
                result.summary(),
                "V24",
                1,
                Instant.now()
        );
    }

    // Getters
    @JsonProperty("matchId") public String matchId() { return matchId; }
    @JsonProperty("careerId") public String careerId() { return careerId; }
    @JsonProperty("seasonNumber") public Integer seasonNumber() { return seasonNumber; }
    @JsonProperty("round") public Integer round() { return round; }
    @JsonProperty("homeTeamId") public String homeTeamId() { return homeTeamId; }
    @JsonProperty("awayTeamId") public String awayTeamId() { return awayTeamId; }
    @JsonProperty("homeTeamName") public String homeTeamName() { return homeTeamName; }
    @JsonProperty("awayTeamName") public String awayTeamName() { return awayTeamName; }
    @JsonProperty("homeGoals") public int homeGoals() { return homeGoals; }
    @JsonProperty("awayGoals") public int awayGoals() { return awayGoals; }
    @JsonProperty("homeXg") public double homeXg() { return homeXg; }
    @JsonProperty("awayXg") public double awayXg() { return awayXg; }
    @JsonProperty("homeShots") public int homeShots() { return homeShots; }
    @JsonProperty("awayShots") public int awayShots() { return awayShots; }
    @JsonProperty("homePossession") public int homePossession() { return homePossession; }
    @JsonProperty("awayPossession") public int awayPossession() { return awayPossession; }
    @JsonProperty("timeline") public List<V24MatchEventDto> timeline() { return timeline; }
    @JsonProperty("playerRatings") public List<V24PlayerMatchRatingDto> playerRatings() { return playerRatings; }
    @JsonProperty("summary") public String summary() { return summary; }
    @JsonProperty("engineVersion") public String engineVersion() { return engineVersion; }
    @JsonProperty("schemaVersion") public int schemaVersion() { return schemaVersion; }
    @JsonProperty("createdAt") public Instant createdAt() { return createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof V24DetailedMatchData that)) return false;
        return Objects.equals(matchId, that.matchId)
                && Objects.equals(careerId, that.careerId)
                && Objects.equals(seasonNumber, that.seasonNumber)
                && Objects.equals(round, that.round)
                && Objects.equals(homeTeamId, that.homeTeamId)
                && Objects.equals(awayTeamId, that.awayTeamId)
                && homeGoals == that.homeGoals && awayGoals == that.awayGoals
                && Double.compare(that.homeXg, homeXg) == 0
                && Double.compare(that.awayXg, awayXg) == 0
                && homeShots == that.homeShots && awayShots == that.awayShots
                && homePossession == that.homePossession && awayPossession == that.awayPossession
                && Objects.equals(timeline, that.timeline)
                && Objects.equals(playerRatings, that.playerRatings)
                && Objects.equals(summary, that.summary)
                && Objects.equals(engineVersion, that.engineVersion)
                && schemaVersion == that.schemaVersion
                && Objects.equals(createdAt, that.createdAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(matchId, careerId, seasonNumber, round, homeTeamId, awayTeamId,
                homeGoals, awayGoals, homeXg, awayXg, homeShots, awayShots,
                homePossession, awayPossession, timeline, playerRatings, summary,
                engineVersion, schemaVersion, createdAt);
    }

    @Override
    public String toString() {
        return "V24DetailedMatchData{matchId=%s, careerId=%s, %s %d-%d %s, xG %.2f-%.2f}"
                .formatted(matchId, careerId, homeTeamName, homeGoals, awayGoals, awayTeamName,
                        homeXg, awayXg);
    }
}