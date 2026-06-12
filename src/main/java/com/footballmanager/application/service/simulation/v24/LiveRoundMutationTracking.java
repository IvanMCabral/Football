package com.footballmanager.application.service.simulation.v24;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * V24D6R2: Per-round tracking for live/UI/SSE path lifecycle decrement.
 * Thread-safe (matches may finish concurrently via 6 parallel callbacks).
 *
 * <p>Lives in the scope of one round's startMatches call and is discarded
 * after end-of-round decrement is applied.
 */
public final class LiveRoundMutationTracking {

    // Pre-round state captured at start of round
    public final Set<String> preRoundSuspendedPlayerIds = ConcurrentHashMap.newKeySet();
    public final Set<String> preRoundInjuredPlayerIds = ConcurrentHashMap.newKeySet();

    // Accumulated during the round through applyLiveMatchCareerMutations
    public final Set<String> newlySuspendedPlayerIds = ConcurrentHashMap.newKeySet();
    public final Set<String> newlyInjuredPlayerIds = ConcurrentHashMap.newKeySet();
    public final Set<String> participatedPlayerIds = ConcurrentHashMap.newKeySet();

    public final int roundNumber;
    public final int seasonNumber;

    public LiveRoundMutationTracking(int roundNumber, int seasonNumber) {
        this.roundNumber = roundNumber;
        this.seasonNumber = seasonNumber;
    }
}