package com.footballmanager.application.service.simulation.v24;

import java.util.List;
import java.util.Optional;

/**
 * V24D4A: Storage port interface for detailed match data.
 *
 * <p>No implementation in this phase. V24D4B would provide a Redis adapter.
 * V24D4A is interface-only — no wiring, no Spring, no dependency injection.
 */
public interface V24DetailedMatchStoragePort {

    /**
     * Save a detailed match data snapshot.
     * Idempotent: subsequent calls for the same matchId overwrite.
     *
     * @param careerId  the career this match belongs to
     * @param detail    the detailed match data snapshot
     * @throws IllegalArgumentException if careerId or detail is null
     */
    void save(String careerId, V24DetailedMatchData detail);

    /**
     * Retrieve detailed match data by matchId.
     *
     * @param careerId  the career this match belongs to
     * @param matchId   the match identifier
     * @return Optional containing the detail if found, empty otherwise
     * @throws IllegalArgumentException if careerId or matchId is null
     */
    Optional<V24DetailedMatchData> findByMatchId(String careerId, String matchId);

    /**
     * Retrieve all detailed match data for a given career.
     *
     * <p>MVP implementation note: uses Redis KEYS scan which is acceptable for
     * small-to-medium-sized careers. For production at scale, replace with a
     * career-scoped index key to avoid KEYS overhead.
     *
     * @param careerId  the career whose match details to retrieve
     * @return list of all V24DetailedMatchData for the career (never null)
     * @throws IllegalArgumentException if careerId is null
     */
    List<V24DetailedMatchData> findByCareerId(String careerId);

    /**
     * Delete all detailed match data for a given career.
     * Typically called when a career is deleted.
     *
     * @param careerId  the career to delete all match details for
     * @throws IllegalArgumentException if careerId is null
     */
    void deleteByCareerId(String careerId);
}
