package com.footballmanager.application.service.simulation.detailed;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
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
public final class DetailedMatchData {

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
    private final List<DetailedMatchEventDto> timeline;
    private final List<PlayerMatchRatingDto> playerRatings;
    private final String summary;
    private final String engineVersion;
    private final int schemaVersion;
    private final Instant createdAt;
    // con null (Jackson rellena con null al agregar @JsonProperty). UI muestra
    // "—" cuando es null. Riesgo BAJO (additive).
    private final String homeFormation;
    private final String awayFormation;
    private final List<MatchLineupPlayerDto> homeStartingPlayers;
    private final List<MatchLineupPlayerDto> homeBenchPlayers;
    private final List<MatchLineupPlayerDto> awayStartingPlayers;
    private final List<MatchLineupPlayerDto> awayBenchPlayers;

    public DetailedMatchData(
            String matchId,
            String careerId,
            Integer seasonNumber,
            Integer round,
            String homeTeamId,
            String awayTeamId,
            String homeTeamName,
            String awayTeamName,
            int homeGoals,
            int awayGoals,
            double homeXg,
            double awayXg,
            int homeShots,
            int awayShots,
            int homePossession,
            int awayPossession,
            List<DetailedMatchEventDto> timeline,
            List<PlayerMatchRatingDto> playerRatings,
            String summary,
            String engineVersion,
            int schemaVersion,
            Instant createdAt,
            String homeFormation,
            String awayFormation) {
        this(matchId, careerId, seasonNumber, round, homeTeamId, awayTeamId,
                homeTeamName, awayTeamName, homeGoals, awayGoals, homeXg, awayXg,
                homeShots, awayShots, homePossession, awayPossession,
                timeline, playerRatings, summary, engineVersion, schemaVersion, createdAt,
                homeFormation, awayFormation, List.of(), List.of(), List.of(), List.of());
    }

    @JsonCreator
    public DetailedMatchData(
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
            @JsonProperty("timeline") List<DetailedMatchEventDto> timeline,
            @JsonProperty("playerRatings") List<PlayerMatchRatingDto> playerRatings,
            @JsonProperty("summary") String summary,
            @JsonProperty("engineVersion") String engineVersion,
            @JsonProperty("schemaVersion") int schemaVersion,
            @JsonProperty("createdAt") Instant createdAt,
            @JsonProperty("homeFormation") String homeFormation,
            @JsonProperty("awayFormation") String awayFormation,
            @JsonProperty("homeStartingPlayers") List<MatchLineupPlayerDto> homeStartingPlayers,
            @JsonProperty("homeBenchPlayers") List<MatchLineupPlayerDto> homeBenchPlayers,
            @JsonProperty("awayStartingPlayers") List<MatchLineupPlayerDto> awayStartingPlayers,
            @JsonProperty("awayBenchPlayers") List<MatchLineupPlayerDto> awayBenchPlayers) {
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
        // Jackson rellena con null al deserializar JSON sin los campos.
        this.homeFormation = (homeFormation != null && !homeFormation.isBlank()) ? homeFormation : null;
        this.awayFormation = (awayFormation != null && !awayFormation.isBlank()) ? awayFormation : null;
        this.homeStartingPlayers = immutableLineup(homeStartingPlayers);
        this.homeBenchPlayers = immutableLineup(homeBenchPlayers);
        this.awayStartingPlayers = immutableLineup(awayStartingPlayers);
        this.awayBenchPlayers = immutableLineup(awayBenchPlayers);
    }

    private static List<MatchLineupPlayerDto> immutableLineup(List<MatchLineupPlayerDto> players) {
        return players != null ? Collections.unmodifiableList(new ArrayList<>(players)) : Collections.emptyList();
    }

    /**
     *
     * call sites in {@code LeagueSimulator}, {@code MatchComparisonService},
     * {@code TestHarnessUseCaseImpl} and tests. Passes {@code null, null} for
     * {@code homeFormation}/{@code awayFormation} so partidos viejos written
     * before this change keep working unchanged.
     */
    public static DetailedMatchData fromResult(
            String careerId,
            Integer seasonNumber,
            Integer round,
            String homeTeamName,
            String awayTeamName,
            DetailedMatchResult result,
            List<PlayerMatchRatingDto> playerRatings) {
        return fromResult(careerId, seasonNumber, round,
                homeTeamName, awayTeamName,
                null, null,
                result, playerRatings,
                List.of(), List.of(), List.of(), List.of());
    }

    /**
     * the formations of the home/away teams are available in the call
     * from {@code SessionTeam.getFormation()}). Pass {@code null} when
     * formation info is not available — the detail will deserialize with
     * {@code homeFormation = awayFormation = null} and the UI renders
     * "—".
     */
    public static DetailedMatchData fromResult(
            String careerId,
            Integer seasonNumber,
            Integer round,
            String homeTeamName,
            String awayTeamName,
            String homeFormation,
            String awayFormation,
            DetailedMatchResult result,
            List<PlayerMatchRatingDto> playerRatings) {
        return fromResult(careerId, seasonNumber, round,
                homeTeamName, awayTeamName,
                homeFormation, awayFormation,
                result, playerRatings,
                List.of(), List.of(), List.of(), List.of());
    }

    public static DetailedMatchData fromResult(
            String careerId,
            Integer seasonNumber,
            Integer round,
            String homeTeamName,
            String awayTeamName,
            String homeFormation,
            String awayFormation,
            DetailedMatchResult result,
            List<PlayerMatchRatingDto> playerRatings,
            List<MatchLineupPlayerDto> homeStartingPlayers,
            List<MatchLineupPlayerDto> homeBenchPlayers,
            List<MatchLineupPlayerDto> awayStartingPlayers,
            List<MatchLineupPlayerDto> awayBenchPlayers) {
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

        List<DetailedMatchEventDto> eventDtos = new ArrayList<>();
        for (DetailedMatchEvent event : result.timeline().events()) {
            eventDtos.add(DetailedMatchEventDto.fromEvent(event));
        }

        return new DetailedMatchData(
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
                Instant.now(),
                homeFormation,
                awayFormation,
                homeStartingPlayers,
                homeBenchPlayers,
                awayStartingPlayers,
                awayBenchPlayers);
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
    @JsonProperty("timeline") public List<DetailedMatchEventDto> timeline() { return timeline; }
    @JsonProperty("playerRatings") public List<PlayerMatchRatingDto> playerRatings() { return playerRatings; }
    @JsonProperty("summary") public String summary() { return summary; }
    @JsonProperty("engineVersion") public String engineVersion() { return engineVersion; }
    @JsonProperty("schemaVersion") public int schemaVersion() { return schemaVersion; }
    @JsonProperty("createdAt") public Instant createdAt() { return createdAt; }
    // change deserialize con null. La UI muestra "—".
    @JsonProperty("homeFormation") public String homeFormation() { return homeFormation; }
    @JsonProperty("awayFormation") public String awayFormation() { return awayFormation; }
    @JsonProperty("homeStartingPlayers") public List<MatchLineupPlayerDto> homeStartingPlayers() { return homeStartingPlayers; }
    @JsonProperty("homeBenchPlayers") public List<MatchLineupPlayerDto> homeBenchPlayers() { return homeBenchPlayers; }
    @JsonProperty("awayStartingPlayers") public List<MatchLineupPlayerDto> awayStartingPlayers() { return awayStartingPlayers; }
    @JsonProperty("awayBenchPlayers") public List<MatchLineupPlayerDto> awayBenchPlayers() { return awayBenchPlayers; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DetailedMatchData that)) return false;
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
                && Objects.equals(createdAt, that.createdAt)
                && Objects.equals(homeFormation, that.homeFormation)
                && Objects.equals(awayFormation, that.awayFormation)
                && Objects.equals(homeStartingPlayers, that.homeStartingPlayers)
                && Objects.equals(homeBenchPlayers, that.homeBenchPlayers)
                && Objects.equals(awayStartingPlayers, that.awayStartingPlayers)
                && Objects.equals(awayBenchPlayers, that.awayBenchPlayers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(matchId, careerId, seasonNumber, round, homeTeamId, awayTeamId,
                homeGoals, awayGoals, homeXg, awayXg, homeShots, awayShots,
                homePossession, awayPossession, timeline, playerRatings, summary,
                engineVersion, schemaVersion, createdAt, homeFormation, awayFormation,
                homeStartingPlayers, homeBenchPlayers, awayStartingPlayers, awayBenchPlayers);
    }

    @Override
    public String toString() {
        String formationPart = "";
        if (homeFormation != null || awayFormation != null) {
            formationPart = ", formation %s/%s".formatted(
                    homeFormation != null ? homeFormation : "—",
                    awayFormation != null ? awayFormation : "—");
        }
        return "DetailedMatchData{matchId=%s, careerId=%s, %s %d-%d %s, xG %.2f-%.2f%s}"
                .formatted(matchId, careerId, homeTeamName, homeGoals, awayGoals, awayTeamName,
                        homeXg, awayXg, formationPart);
    }
}
