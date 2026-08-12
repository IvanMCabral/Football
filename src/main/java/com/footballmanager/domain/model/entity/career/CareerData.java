package com.footballmanager.domain.model.entity.career;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.footballmanager.domain.model.metadata.WorldIdentityDomain;
import com.footballmanager.domain.model.metadata.WorldIdentityReference;

import java.time.Instant;
import java.util.UUID;

/**
 * CareerData - Datos de configuración y metadata de una carrera.
 * Responsabilidad: almacenar información estática de la carrera.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CareerData {

    @WorldIdentityReference(domain = WorldIdentityDomain.OTHER_ID)
    private String careerId;
    @WorldIdentityReference(domain = WorldIdentityDomain.OTHER_ID)
    private UUID userId;
    @WorldIdentityReference(domain = WorldIdentityDomain.REAL_TEAM)
    private UUID userTeamId;
    @WorldIdentityReference(domain = WorldIdentityDomain.SESSION_TEAM)
    private String userSessionTeamId;
    @WorldIdentityReference(domain = WorldIdentityDomain.NON_ID_TEXT)
    private String difficulty;
    @WorldIdentityReference(domain = WorldIdentityDomain.NON_ID_TEXT)
    private String gameSpeed;
    private Instant createdAt;
    private Instant lastUpdated;

    public CareerData() {
        this.careerId = UUID.randomUUID().toString();
        this.createdAt = Instant.now();
        this.lastUpdated = Instant.now();
    }

    public String getCareerId() { return careerId; }
    public void setCareerId(String careerId) { this.careerId = careerId; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public UUID getUserTeamId() { return userTeamId; }
    public void setUserTeamId(UUID userTeamId) { this.userTeamId = userTeamId; }

    public String getUserSessionTeamId() { return userSessionTeamId; }
    public void setUserSessionTeamId(String userSessionTeamId) { this.userSessionTeamId = userSessionTeamId; }

    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }

    public String getGameSpeed() { return gameSpeed; }
    public void setGameSpeed(String gameSpeed) { this.gameSpeed = gameSpeed; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(Instant lastUpdated) { this.lastUpdated = lastUpdated; }

    public void touch() { this.lastUpdated = Instant.now(); }
}
