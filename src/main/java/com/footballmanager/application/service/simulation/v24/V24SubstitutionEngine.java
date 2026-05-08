package com.footballmanager.application.service.simulation.v24;

import com.footballmanager.application.service.domain.TeamStyle;

import java.util.*;

/**
 * V24C4: Substitution engine for V24 detailed match engine.
 *
 * <p>Provides:
 * <ul>
 *   <li>Max 5 substitutions per team</li>
 *   <li>Priority-based candidate selection (injured > very tired > tired+yellow)</li>
 *   <li>Same-position bench preference with compatible fallback</li>
 *   <li>Duplicate substitution prevention</li>
 *   <li>Red-carded players never substituted</li>
 * </ul>
 *
 * <p>No Spring annotations, no repository dependencies.
 * Deterministic: same state + same context = identical result.
 */
public final class V24SubstitutionEngine {

    private static final int DEFAULT_MAX_SUBS = 5;

    private final int maxSubstitutions;
    private final Map<String, Integer> substitutionsUsed;
    private final Set<String> substitutedOffPlayerIds;
    private final Set<String> substitutedOnPlayerIds;

    public V24SubstitutionEngine() {
        this(DEFAULT_MAX_SUBS);
    }

    public V24SubstitutionEngine(int maxSubstitutions) {
        if (maxSubstitutions < 0) {
            throw new IllegalArgumentException("maxSubstitutions must be >= 0");
        }
        this.maxSubstitutions = maxSubstitutions;
        this.substitutionsUsed = new HashMap<>();
        this.substitutedOffPlayerIds = new HashSet<>();
        this.substitutedOnPlayerIds = new HashSet<>();
    }

    public Optional<V24MatchEvent> attemptSubstitution(V24TeamMatchState team, int minute) {
        if (team == null) {
            throw new IllegalArgumentException("team must not be null");
        }
        if (minute < 1 || minute > 130) {
            throw new IllegalArgumentException("minute must be between 1 and 130, got " + minute);
        }

        String teamId = team.teamId();

        if (!hasSubstitutionsRemaining(teamId)) {
            return Optional.empty();
        }

        // Priority 1: injured players
        var injuredCandidate = findCandidate(team.startingPlayers(), p ->
                p.injured() && !p.redCard() && !isSubstitutedOff(p.sessionPlayerId()));
        if (injuredCandidate.isPresent()) {
            return makeSubstitution(team, injuredCandidate.get(), minute);
        }

        // Priority 2: very tired players (currentStamina < 30)
        var veryTiredCandidate = findCandidate(team.startingPlayers(), p ->
                p.onPitch() && !p.redCard() && !p.injured()
                        && p.currentStamina() < 30 && !isSubstitutedOff(p.sessionPlayerId()));
        if (veryTiredCandidate.isPresent()) {
            return makeSubstitution(team, veryTiredCandidate.get(), minute);
        }

        // Priority 3: tired + yellow-carded players (currentStamina < 50 and yellowCards >= 1)
        var tiredYellowCandidate = findCandidate(team.startingPlayers(), p ->
                p.onPitch() && !p.redCard() && !p.injured()
                        && p.currentStamina() < 50 && p.yellowCards() >= 1 && !isSubstitutedOff(p.sessionPlayerId()));
        if (tiredYellowCandidate.isPresent()) {
            return makeSubstitution(team, tiredYellowCandidate.get(), minute);
        }

        return Optional.empty();
    }

    public int substitutionsUsed(String teamId) {
        if (teamId == null) {
            throw new IllegalArgumentException("teamId must not be null");
        }
        return substitutionsUsed.getOrDefault(teamId, 0);
    }

    public int substitutionsRemaining(String teamId) {
        if (teamId == null) {
            throw new IllegalArgumentException("teamId must not be null");
        }
        return Math.max(0, maxSubstitutions - substitutionsUsed(teamId));
    }

    public boolean hasSubstitutionsRemaining(String teamId) {
        return substitutionsRemaining(teamId) > 0;
    }

    private Optional<V24MatchEvent> makeSubstitution(V24TeamMatchState team, V24PlayerMatchState subOff, int minute) {
        String teamId = team.teamId();

        V24PlayerMatchState subOn = selectBenchPlayer(team.benchPlayers(), subOff.position());
        if (subOn == null) {
            return Optional.empty();
        }

        // Mark both players as substituted
        substitutedOffPlayerIds.add(subOff.sessionPlayerId());
        substitutedOnPlayerIds.add(subOn.sessionPlayerId());

        // Update state
        subOff.substituteOff();
        subOn.setTeamId(teamId);
        subOn.substituteOn();

        // Increment counter
        substitutionsUsed.merge(teamId, 1, Integer::sum);

        String description = "Substitution: " + subOn.name() + " on for " + subOff.name();

        return Optional.of(new V24MatchEvent(
                minute,
                V24MatchEventType.SUBSTITUTION,
                teamId,
                subOff.sessionPlayerId(),
                subOff.name(),
                subOn.sessionPlayerId(),
                subOn.name(),
                0.0,
                description
        ));
    }

    private Optional<V24PlayerMatchState> findCandidate(List<V24PlayerMatchState> players,
                                                         java.util.function.Predicate<V24PlayerMatchState> filter) {
        return players.stream()
                .filter(filter)
                .findFirst();
    }

    private V24PlayerMatchState selectBenchPlayer(List<V24PlayerMatchState> bench, String position) {
        // Filter eligible bench players
        var eligible = bench.stream()
                .filter(p -> !p.onPitch() && !p.redCard() && !p.injured() && !isSubstitutedOn(p.sessionPlayerId()))
                .toList();

        if (eligible.isEmpty()) {
            return null;
        }

        // Prefer same position
        var samePos = eligible.stream()
                .filter(p -> p.position().equals(position))
                .findFirst();

        if (samePos.isPresent()) {
            return samePos.get();
        }

        // Compatible fallback
        var compatible = eligible.stream()
                .filter(p -> isCompatiblePosition(position, p.position()))
                .findFirst();

        if (compatible.isPresent()) {
            return compatible.get();
        }

        // Final fallback: any eligible
        return eligible.get(0);
    }

    private boolean isCompatiblePosition(String offPosition, String onPosition) {
        // GK -> GK only
        if ("GK".equals(offPosition)) return false;

        // DEF -> DEF, MID
        if ("DEF".equals(offPosition)) {
            return "DEF".equals(onPosition) || "MID".equals(onPosition);
        }

        // MID -> MID, DEF, WINGER
        if ("MID".equals(offPosition)) {
            return "MID".equals(onPosition) || "DEF".equals(onPosition) || "WINGER".equals(onPosition);
        }

        // WINGER -> WINGER, MID, ATT
        if ("WINGER".equals(offPosition)) {
            return "WINGER".equals(onPosition) || "MID".equals(onPosition) || "ATT".equals(onPosition);
        }

        // ATT -> ATT, WINGER
        if ("ATT".equals(offPosition)) {
            return "ATT".equals(onPosition) || "WINGER".equals(onPosition);
        }

        return false;
    }

    private boolean isSubstitutedOff(String playerId) {
        return substitutedOffPlayerIds.contains(playerId);
    }

    private boolean isSubstitutedOn(String playerId) {
        return substitutedOnPlayerIds.contains(playerId);
    }
}