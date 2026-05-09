package com.footballmanager.application.service.simulation.v24;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * V24D4C: Query service for detailed match data.
 *
 * <p>Reads V24DetailedMatchData from storage via V24DetailedMatchStoragePort.
 * Does not simulate matches, does not persist data, does not call LeagueSimulator.
 *
 * <p>Feature-gated: returns empty when {@code app.simulation.v24.expose-detail-api=false}.
 */
@Service
public class V24DetailedMatchQueryService {

    private final V24DetailedMatchStoragePort storagePort;
    private final boolean apiEnabled;

    public V24DetailedMatchQueryService(
            V24DetailedMatchStoragePort storagePort,
            @Qualifier("v24DetailApiEnabled") boolean apiEnabled) {
        this.storagePort = storagePort;
        this.apiEnabled = apiEnabled;
    }

    /**
     * Find detailed match data if the feature flag is enabled and data exists.
     *
     * @param careerId the career identifier
     * @param matchId  the match identifier
     * @return Optional containing the detail if API enabled and found, empty otherwise
     * @throws IllegalArgumentException if careerId or matchId is blank
     */
    public Optional<V24DetailedMatchData> findDetail(String careerId, String matchId) {
        if (!apiEnabled) {
            return Optional.empty();
        }
        validateIds(careerId, matchId);
        return storagePort.findByMatchId(careerId, matchId);
    }

    private void validateIds(String careerId, String matchId) {
        if (careerId == null || careerId.isBlank()) {
            throw new IllegalArgumentException("careerId must not be blank");
        }
        if (matchId == null || matchId.isBlank()) {
            throw new IllegalArgumentException("matchId must not be blank");
        }
    }

    /**
     * Check whether the detail API is enabled via feature flag.
     */
    public boolean isApiEnabled() {
        return apiEnabled;
    }
}