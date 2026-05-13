package com.footballmanager.application.service.simulation.v24;

import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionPlayer;

import java.util.HashSet;
import java.util.Set;

/**
 * Pure helper that applies post-match energy drain to CareerSave SessionPlayers
 * based on V24 match participation.
 *
 * <p>This applier is isolated and has no dependencies on Redis, Spring, or IO.
 * It only reads from V24DetailedMatchResult and mutates SessionPlayer.energy field.
 *
 * <p>Energy drain rules:
 * <ul>
 *   <li>Players who participated in at least one non-substitution event: drain DEFAULT_FULL_MATCH_DRAIN</li>
 *   <li>Players involved only in SUBSTITUTION events: drain DEFAULT_SUBSTITUTE_DRAIN</li>
 *   <li>Each player drains at most once per match</li>
 *   <li>Energy never goes below 0</li>
 *   <li>Does not mutate injured players — skips them</li>
 * </ul>
 */
public class V24FatigueMutationApplier {

    public static final int DEFAULT_FULL_MATCH_DRAIN = 12;
    public static final int DEFAULT_SUBSTITUTE_DRAIN = 6;

    private final int fullMatchDrain;
    private final int substituteDrain;

    public V24FatigueMutationApplier() {
        this(DEFAULT_FULL_MATCH_DRAIN, DEFAULT_SUBSTITUTE_DRAIN);
    }

    public V24FatigueMutationApplier(int fullMatchDrain, int substituteDrain) {
        this.fullMatchDrain = fullMatchDrain;
        this.substituteDrain = substituteDrain;
    }

    /**
     * Apply post-match energy drain to CareerSave SessionPlayers.
     *
     * @param career the CareerSave to mutate; if null, returns 0
     * @param result the V24DetailedMatchResult containing timeline events; if null, returns 0
     * @param policy the mutation policy; if null, returns 0
     * @return the number of players whose energy was reduced
     */
    public int applyFatigue(CareerSave career, V24DetailedMatchResult result, V24CareerMutationPolicy policy) {
        if (career == null) return 0;
        if (result == null) return 0;
        if (policy == null) return 0;
        if (!policy.isFatiguePersistenceEnabled()) return 0;

        Set<String> substituteOnlyPlayers = new HashSet<>();

        for (V24MatchEvent event : result.timeline().events()) {
            if (event.playerId() == null || event.playerId().isBlank()) continue;

            if (event.type() == V24MatchEventType.SUBSTITUTION) {
                substituteOnlyPlayers.add(event.playerId());
            }

            if (event.relatedPlayerId() != null && !event.relatedPlayerId().isBlank()) {
                if (event.type() == V24MatchEventType.SUBSTITUTION) {
                    substituteOnlyPlayers.add(event.relatedPlayerId());
                }
            }
        }

        for (V24MatchEvent event : result.timeline().events()) {
            if (event.playerId() == null || event.playerId().isBlank()) continue;
            if (event.type() == V24MatchEventType.SUBSTITUTION) continue;
            substituteOnlyPlayers.remove(event.playerId());

            if (event.relatedPlayerId() != null && !event.relatedPlayerId().isBlank()) {
                substituteOnlyPlayers.remove(event.relatedPlayerId());
            }
        }

        Set<String> allParticipatingPlayerIds = new HashSet<>();

        for (V24MatchEvent event : result.timeline().events()) {
            if (event.playerId() != null && !event.playerId().isBlank()) {
                allParticipatingPlayerIds.add(event.playerId());
            }
            if (event.relatedPlayerId() != null && !event.relatedPlayerId().isBlank()) {
                allParticipatingPlayerIds.add(event.relatedPlayerId());
            }
        }

        int count = 0;

        for (String playerId : allParticipatingPlayerIds) {
            SessionPlayer player = career.getSessionPlayer(playerId);
            if (player == null) continue;
            if (Boolean.TRUE.equals(player.getInjured())) continue;

            int drain = substituteOnlyPlayers.contains(playerId)
                    ? substituteDrain
                    : fullMatchDrain;

            Integer currentEnergy = player.getEnergy();
            if (currentEnergy == null) currentEnergy = 100;

            int newEnergy = Math.max(0, currentEnergy - drain);
            player.setEnergy(newEnergy);
            count++;
        }

        return count;
    }
}