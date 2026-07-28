package com.footballmanager.application.service.simulation.v24;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Optional;

public interface V24DetailedMatchStoragePort {

    /**
     * Save a detailed match data snapshot.
     * Idempotent: subsequent calls for the same matchId overwrite.
     */
    Mono<Void> save(String careerId, V24DetailedMatchData detail);

    /**
     * Retrieve detailed match data by matchId.
     */
    Mono<Optional<V24DetailedMatchData>> findByMatchId(String careerId, String matchId);

    /**
     * Retrieve all detailed match data for a given career.
     */
    Flux<V24DetailedMatchData> findByCareerId(String careerId);

    /**
     * Delete all detailed match data for a given career.
     */
    Mono<Void> deleteByCareerId(String careerId);

    /**
     * Used by the test-harness replay endpoint to clear stale V24 detail.
     */
    default Mono<Void> deleteByMatchId(String careerId, String matchId) {
        if (careerId == null || careerId.isBlank()) {
            return Mono.error(new IllegalArgumentException("careerId must not be blank"));
        }
        if (matchId == null || matchId.isBlank()) {
            return Mono.error(new IllegalArgumentException("matchId must not be blank"));
        }
        return Mono.empty();
    }
}
