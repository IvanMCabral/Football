package com.footballmanager.application.service.simulation.v24;

import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionPlayer;

import java.util.HashSet;
import java.util.Set;

/**
 * Pure helper that applies YELLOW_CARD / RED_CARD events from a match result
 * to CareerSave SessionPlayers.
 *
 * <p>This applier is isolated and has no dependencies on Redis, Spring, or IO.
 * It only reads from V24DetailedMatchResult and mutates SessionPlayer discipline fields.
 *
 * <p>Discipline rules:
 * <ul>
 *   <li>YELLOW_CARD: increments SessionPlayer.yellowCards by 1</li>
 *   <li>RED_CARD: increments SessionPlayer.redCards by 1, sets suspended=true, suspensionRemainingMatches=1</li>
 *   <li>Duplicate RED_CARD for same player in same match: counts once (first valid RED_CARD wins)</li>
 *   <li>Multiple YELLOW_CARD for same player: each increments yellowCards</li>
 *   <li>Red card on already-suspended player: redCards++, suspensionRemainingMatches resets to 1</li>
 *   <li>Yellow-card threshold: 5 accumulated yellows → 1-match suspension, yellowCards -= 5</li>
 *   <li>Yellow threshold evaluated per YELLOW_CARD event; per-match cap of 1 threshold suspension per player</li>
 *   <li>RED_CARD takes precedence over yellow threshold in the same match</li>
 *   <li>Already-suspended player reaching threshold: yellowCards accumulate, no additional suspension applied</li>
 *   <li>No suspension decrement — lifecycle deferred to V24D6D6</li>
 * </ul>
 */
public class V24DisciplineMutationApplier {

    private static final int YELLOW_CARD_SUSPENSION_THRESHOLD = 5;

    /**
     * Apply YELLOW_CARD / RED_CARD events from the match result to CareerSave SessionPlayers.
     *
     * @param career the CareerSave to mutate; if null, returns 0
     * @param result the V24DetailedMatchResult containing timeline events; if null, returns 0
     * @param policy the mutation policy; if null, returns 0
     * @return the number of card events applied (each valid YELLOW_CARD or RED_CARD counts 1)
     */
    public int applyDiscipline(CareerSave career, V24DetailedMatchResult result,
                               V24CareerMutationPolicy policy) {
        if (career == null) return 0;
        if (result == null) return 0;
        if (policy == null) return 0;
        if (!policy.isDisciplinePersistenceEnabled()) return 0;

        if (result.timeline() == null || result.timeline().events() == null) return 0;

        int appliedCount = 0;
        Set<String> redCardPlayerIds = new HashSet<>();
        Set<String> redCardApplied = new HashSet<>();
        Set<String> thresholdSuspendedThisMatch = new HashSet<>();

        // Pass 1: collect RED_CARD player IDs so yellow threshold evaluation
        // can apply RED precedence regardless of event order in the timeline
        for (V24MatchEvent event : result.timeline().events()) {
            if (event.type() == V24MatchEventType.RED_CARD && event.playerId() != null
                    && !event.playerId().isBlank()) {
                redCardPlayerIds.add(event.playerId());
            }
        }

        // Pass 2: apply all events
        for (V24MatchEvent event : result.timeline().events()) {
            V24MatchEventType type = event.type();
            String playerId = event.playerId();

            if (playerId == null || playerId.isBlank()) continue;

            if (type == V24MatchEventType.YELLOW_CARD) {
                SessionPlayer player = career.getSessionPlayer(playerId);
                if (player == null) continue;

                Integer current = player.getYellowCards();
                player.setYellowCards(current != null ? current + 1 : 1);
                appliedCount++;

                // Yellow-card threshold check: threshold=5, per-match cap, RED precedence,
                // already-suspended protection, subtract-5-once reset
                if (!thresholdSuspendedThisMatch.contains(playerId)
                        && !redCardPlayerIds.contains(playerId)
                        && !Boolean.TRUE.equals(player.getSuspended())
                        && player.getYellowCards() >= YELLOW_CARD_SUSPENSION_THRESHOLD) {

                    thresholdSuspendedThisMatch.add(playerId);
                    player.setSuspended(true);
                    player.setSuspensionRemainingMatches(1);
                    player.setYellowCards(player.getYellowCards() - YELLOW_CARD_SUSPENSION_THRESHOLD);
                    appliedCount++; // threshold suspension is an additional discipline mutation
                }
            } else if (type == V24MatchEventType.RED_CARD) {
                // Count once per player per match
                if (redCardApplied.contains(playerId)) continue;

                SessionPlayer player = career.getSessionPlayer(playerId);
                if (player == null) continue;

                redCardApplied.add(playerId);

                Integer currentRed = player.getRedCards();
                player.setRedCards(currentRed != null ? currentRed + 1 : 1);

                player.setSuspended(true);
                player.setSuspensionRemainingMatches(1);
                appliedCount++;
            }
        }

        return appliedCount;
    }
}