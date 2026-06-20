package com.footballmanager.domain.model.valueobject;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;
import java.util.UUID;

/**
 * MatchFixture - Value Object que representa un partido programado
 * Vive SOLO en CareerSave.tournament.fixtures (Redis)
 * NO tiene persistencia en PostgreSQL
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MatchFixture {
    
    private final String matchId;           // UUID único del partido
    private final String homeTeamId;        // sessionTeamId del equipo local
    private final String awayTeamId;        // sessionTeamId del equipo visitante
    private final int round;                // Número de ronda/fecha
    private MatchStatus status;             // Estado del partido
    private MatchResultData result;         // Resultado (null hasta que se juega)
    
    
    /**
     * Constructor para fixture pendiente
     */
    public MatchFixture(String matchId, String homeTeamId, String awayTeamId, int round) {
        this.matchId = Objects.requireNonNull(matchId, "matchId cannot be null");
        this.homeTeamId = Objects.requireNonNull(homeTeamId, "homeTeamId cannot be null");
        this.awayTeamId = Objects.requireNonNull(awayTeamId, "awayTeamId cannot be null");
        this.round = round;
        this.status = MatchStatus.PENDING;
        this.result = null;
    }
    
    /**
     * Constructor completo (para reconstrucción desde Redis)
     */
    @JsonCreator
    public MatchFixture(
            @JsonProperty("matchId") String matchId, 
            @JsonProperty("homeTeamId") String homeTeamId, 
            @JsonProperty("awayTeamId") String awayTeamId, 
            @JsonProperty("round") int round, 
            @JsonProperty("status") MatchStatus status, 
            @JsonProperty("result") MatchResultData result) {
        this.matchId = matchId;
        this.homeTeamId = homeTeamId;
        this.awayTeamId = awayTeamId;
        this.round = round;
        this.status = status;
        this.result = result;
    }
    
    // ========== Domain Methods ==========
    
    /**
     * Marca el fixture como completado con un resultado
     */
    public void complete(MatchResultData result) {
        if (this.status == MatchStatus.COMPLETED) {
            throw new IllegalStateException("Match already completed");
        }
        this.status = MatchStatus.COMPLETED;
        this.result = Objects.requireNonNull(result, "result cannot be null");
    }

    /**
     * V24D20-SANDBOX-V2-MVP: Reset a completed fixture back to PENDING
     * with no result. Used by the test-harness replay endpoint so the
     * fixture can be re-simulated (the {@link #complete(MatchResultData)}
     * method throws if the status is already COMPLETED).
     *
     * <p>After {@code reset()}, the fixture is back to its initial
     * post-construction state and can be completed again.
     */
    public void reset() {
        this.status = MatchStatus.PENDING;
        this.result = null;
    }
    
    /**
     * Marca el fixture como en progreso (live match)
     */
    public void startSimulation() {
        if (this.status != MatchStatus.PENDING) {
            throw new IllegalStateException("Match can only start from PENDING state");
        }
        this.status = MatchStatus.SIMULATING;
    }
    
    /**
     * Verifica si el partido puede ser jugado
     */
    @JsonIgnore
    public boolean canBeSimulated() {
        return this.status == MatchStatus.PENDING;
    }
    
    /**
     * Verifica si el partido ya fue jugado
     */
    @JsonIgnore
    public boolean isCompleted() {
        return this.status == MatchStatus.COMPLETED;
    }
    
    // ========== Getters ==========
    
    public String getMatchId() {
        return matchId;
    }
    
    public String getHomeTeamId() {
        return homeTeamId;
    }
    
    public String getAwayTeamId() {
        return awayTeamId;
    }
    
    public int getRound() {
        return round;
    }
    
    public MatchStatus getStatus() {
        return status;
    }
    
    public void setStatus(MatchStatus status) {
        this.status = status;
    }
    
    public MatchResultData getResult() {
        return result;
    }
    
    public void setResult(MatchResultData result) {
        this.result = result;
    }
    
    // ========== Nested Value Object: MatchResultData ==========
    
    /**
     * Datos del resultado de un partido (embebido en fixture)
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.PUBLIC_ONLY)
    public static class MatchResultData {
        @JsonProperty("homeGoals")
        public final int homeGoals;
        @JsonProperty("awayGoals")
        public final int awayGoals;
        @JsonProperty("homePossession")
        public final int homePossession;
        @JsonProperty("awayPossession")
        public final int awayPossession;
        @JsonProperty("homeShots")
        public final int homeShots;
        @JsonProperty("awayShots")
        public final int awayShots;
        
        @JsonCreator
        public MatchResultData(
                @JsonProperty("homeGoals") int homeGoals, 
                @JsonProperty("awayGoals") int awayGoals, 
                @JsonProperty("homePossession") int homePossession, 
                @JsonProperty("awayPossession") int awayPossession,
                @JsonProperty("homeShots") int homeShots, 
                @JsonProperty("awayShots") int awayShots) {
            this.homeGoals = homeGoals;
            this.awayGoals = awayGoals;
            this.homePossession = homePossession;
            this.awayPossession = awayPossession;
            this.homeShots = homeShots;
            this.awayShots = awayShots;
        }
        
        public int getHomeGoals() {
            return homeGoals;
        }
        
        public int getAwayGoals() {
            return awayGoals;
        }
        
        public int getHomePossession() {
            return homePossession;
        }
        
        public int getAwayPossession() {
            return awayPossession;
        }
        
        public int getHomeShots() {
            return homeShots;
        }
        
        public int getAwayShots() {
            return awayShots;
        }
        
        public boolean isHomeWin() {
            return homeGoals > awayGoals;
        }
        
        public boolean isDraw() {
            return homeGoals == awayGoals;
        }
        
        public boolean isAwayWin() {
            return awayGoals > homeGoals;
        }
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MatchFixture that = (MatchFixture) o;
        return Objects.equals(matchId, that.matchId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(matchId);
    }
}
