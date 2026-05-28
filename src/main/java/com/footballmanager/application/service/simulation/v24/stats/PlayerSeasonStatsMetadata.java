package com.footballmanager.application.service.simulation.v24.stats;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Objects;

/**
 * V24D6M7: Metadata block for player season stats response.
 *
 * <p>Contains pagination state, data quality indicators, and provenance
 * information. All fields are optional — missing fields indicate
 * metadata was not computed.
 */
public final class PlayerSeasonStatsMetadata {

    private final Integer limit;
    private final Integer offset;
    private final Boolean hasMore;
    private final Integer totalPlayers;
    private final Integer returnedPlayers;
    private final Integer totalMatchesProcessed;
    private final Integer lastUpdatedRound;
    private final String dataSource;
    private final PlayerSeasonStatsDataCompleteness dataCompleteness;
    private final Instant generatedAt;
    private final String versionHash;

    public PlayerSeasonStatsMetadata(
            Integer limit,
            Integer offset,
            Boolean hasMore,
            Integer totalPlayers,
            Integer returnedPlayers,
            Integer totalMatchesProcessed,
            Integer lastUpdatedRound,
            String dataSource,
            PlayerSeasonStatsDataCompleteness dataCompleteness,
            Instant generatedAt,
            String versionHash) {
        this.limit = limit;
        this.offset = offset;
        this.hasMore = hasMore;
        this.totalPlayers = totalPlayers;
        this.returnedPlayers = returnedPlayers;
        this.totalMatchesProcessed = totalMatchesProcessed;
        this.lastUpdatedRound = lastUpdatedRound;
        this.dataSource = dataSource;
        this.dataCompleteness = dataCompleteness;
        this.generatedAt = generatedAt;
        this.versionHash = versionHash;
    }

    @JsonProperty("limit") public Integer limit() { return limit; }
    @JsonProperty("offset") public Integer offset() { return offset; }
    @JsonProperty("hasMore") public Boolean hasMore() { return hasMore; }
    @JsonProperty("totalPlayers") public Integer totalPlayers() { return totalPlayers; }
    @JsonProperty("returnedPlayers") public Integer returnedPlayers() { return returnedPlayers; }
    @JsonProperty("totalMatchesProcessed") public Integer totalMatchesProcessed() { return totalMatchesProcessed; }
    @JsonProperty("lastUpdatedRound") public Integer lastUpdatedRound() { return lastUpdatedRound; }
    @JsonProperty("dataSource") public String dataSource() { return dataSource; }
    @JsonProperty("dataCompleteness") public PlayerSeasonStatsDataCompleteness dataCompleteness() { return dataCompleteness; }
    @JsonProperty("generatedAt") public Instant generatedAt() { return generatedAt; }
    @JsonProperty("versionHash") public String versionHash() { return versionHash; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PlayerSeasonStatsMetadata that)) return false;
        return Objects.equals(limit, that.limit)
                && Objects.equals(offset, that.offset)
                && Objects.equals(hasMore, that.hasMore)
                && Objects.equals(totalPlayers, that.totalPlayers)
                && Objects.equals(returnedPlayers, that.returnedPlayers)
                && Objects.equals(totalMatchesProcessed, that.totalMatchesProcessed)
                && Objects.equals(lastUpdatedRound, that.lastUpdatedRound)
                && Objects.equals(dataSource, that.dataSource)
                && dataCompleteness == that.dataCompleteness
                && Objects.equals(generatedAt, that.generatedAt)
                && Objects.equals(versionHash, that.versionHash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(limit, offset, hasMore, totalPlayers, returnedPlayers,
                totalMatchesProcessed, lastUpdatedRound, dataSource, dataCompleteness,
                generatedAt, versionHash);
    }

    @Override
    public String toString() {
        return "PlayerSeasonStatsMetadata{totalPlayers=%s, returnedPlayers=%s, hasMore=%s, dataCompleteness=%s}"
                .formatted(totalPlayers, returnedPlayers, hasMore, dataCompleteness);
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private Integer limit;
        private Integer offset;
        private Boolean hasMore;
        private Integer totalPlayers;
        private Integer returnedPlayers;
        private Integer totalMatchesProcessed;
        private Integer lastUpdatedRound;
        private String dataSource;
        private PlayerSeasonStatsDataCompleteness dataCompleteness;
        private Instant generatedAt;
        private String versionHash;

        public Builder limit(Integer limit) { this.limit = limit; return this; }
        public Builder offset(Integer offset) { this.offset = offset; return this; }
        public Builder hasMore(Boolean hasMore) { this.hasMore = hasMore; return this; }
        public Builder totalPlayers(Integer totalPlayers) { this.totalPlayers = totalPlayers; return this; }
        public Builder returnedPlayers(Integer returnedPlayers) { this.returnedPlayers = returnedPlayers; return this; }
        public Builder totalMatchesProcessed(Integer totalMatchesProcessed) { this.totalMatchesProcessed = totalMatchesProcessed; return this; }
        public Builder lastUpdatedRound(Integer lastUpdatedRound) { this.lastUpdatedRound = lastUpdatedRound; return this; }
        public Builder dataSource(String dataSource) { this.dataSource = dataSource; return this; }
        public Builder dataCompleteness(PlayerSeasonStatsDataCompleteness dataCompleteness) { this.dataCompleteness = dataCompleteness; return this; }
        public Builder generatedAt(Instant generatedAt) { this.generatedAt = generatedAt; return this; }
        public Builder versionHash(String versionHash) { this.versionHash = versionHash; return this; }

        public PlayerSeasonStatsMetadata build() {
            return new PlayerSeasonStatsMetadata(
                    limit, offset, hasMore, totalPlayers, returnedPlayers,
                    totalMatchesProcessed, lastUpdatedRound, dataSource, dataCompleteness,
                    generatedAt, versionHash
            );
        }
    }
}