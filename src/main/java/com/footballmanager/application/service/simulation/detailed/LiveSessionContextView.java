package com.footballmanager.application.service.simulation.detailed;

import com.footballmanager.domain.model.valueobject.LineupSlot;
import com.footballmanager.domain.model.valueobject.PlayerSkill;
import com.footballmanager.domain.model.valueobject.PlayerSpecialTrait;
import com.footballmanager.domain.model.valueobject.TeamStyle;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable public read model for a live detailed match session.
 *
 * <p>This type is intentionally made of values only. It must not contain
 * {@code SessionTeam}, {@code SessionPlayer}, engines, services, or mutable
 * collections owned by {@link LiveSession}.</p>
 */
public record LiveSessionContextView(
        String matchId,
        TeamContextView homeTeam,
        TeamContextView awayTeam,
        List<PlayerContextView> homeStartingPlayers,
        List<PlayerContextView> awayStartingPlayers,
        List<PlayerContextView> homeBenchPlayers,
        List<PlayerContextView> awayBenchPlayers,
        List<ScheduledSubstitutionView> manualSubstitutions) {

    public LiveSessionContextView {
        homeStartingPlayers = List.copyOf(homeStartingPlayers);
        awayStartingPlayers = List.copyOf(awayStartingPlayers);
        homeBenchPlayers = List.copyOf(homeBenchPlayers);
        awayBenchPlayers = List.copyOf(awayBenchPlayers);
        manualSubstitutions = List.copyOf(manualSubstitutions);
    }

    public String homeTeamId() {
        return homeTeam.teamId();
    }

    public String awayTeamId() {
        return awayTeam.teamId();
    }

    public String homeFormation() {
        return homeTeam.formation();
    }

    public String awayFormation() {
        return awayTeam.formation();
    }

    public TeamStyle homeStyle() {
        return homeTeam.style();
    }

    public TeamStyle awayStyle() {
        return awayTeam.style();
    }

    public List<PlayerContextView> startingPlayers(String teamId) {
        if (homeTeamId().equals(teamId)) {
            return homeStartingPlayers;
        }
        if (awayTeamId().equals(teamId)) {
            return awayStartingPlayers;
        }
        throw unknownTeam(teamId);
    }

    public List<PlayerContextView> benchPlayers(String teamId) {
        if (homeTeamId().equals(teamId)) {
            return homeBenchPlayers;
        }
        if (awayTeamId().equals(teamId)) {
            return awayBenchPlayers;
        }
        throw unknownTeam(teamId);
    }

    public TeamContextView team(String teamId) {
        if (homeTeamId().equals(teamId)) {
            return homeTeam;
        }
        if (awayTeamId().equals(teamId)) {
            return awayTeam;
        }
        throw unknownTeam(teamId);
    }

    public boolean containsPlayer(String teamId, String playerId) {
        return containsPlayer(startingPlayers(teamId), playerId)
                || containsPlayer(benchPlayers(teamId), playerId);
    }

    public PlayerContextView findPlayer(String teamId, String playerId) {
        PlayerContextView starter = findPlayer(startingPlayers(teamId), playerId);
        if (starter != null) {
            return starter;
        }
        return findPlayer(benchPlayers(teamId), playerId);
    }

    private static boolean containsPlayer(List<PlayerContextView> players, String playerId) {
        return findPlayer(players, playerId) != null;
    }

    private static PlayerContextView findPlayer(List<PlayerContextView> players, String playerId) {
        if (playerId == null) {
            return null;
        }
        for (PlayerContextView player : players) {
            if (player != null && playerId.equals(player.sessionPlayerId())) {
                return player;
            }
        }
        return null;
    }

    private IllegalArgumentException unknownTeam(String teamId) {
        return new IllegalArgumentException(
                "teamId " + teamId + " does not match home (" + homeTeamId()
                        + ") or away (" + awayTeamId() + ") of this match");
    }

    public record TeamContextView(
            String teamId,
            UUID baseTeamId,
            String worldTeamId,
            String name,
            String country,
            BigDecimal budget,
            String formation,
            TeamStyle style,
            String managerName,
            Integer morale,
            Integer reputation,
            Map<String, LineupSlot> slotsByPlayerId) {

        public TeamContextView {
            slotsByPlayerId = Map.copyOf(slotsByPlayerId);
        }
    }

    public record PlayerContextView(
            String sessionPlayerId,
            UUID basePlayerId,
            String worldPlayerId,
            String name,
            Integer age,
            String position,
            Integer attack,
            Integer defense,
            Integer technique,
            Integer speed,
            Integer stamina,
            Integer mentality,
            BigDecimal marketValue,
            Integer energy,
            Integer form,
            Boolean injured,
            String injuryType,
            Integer injuryRemainingMatches,
            Integer matchesPlayedInRow,
            Integer yellowCards,
            Integer redCards,
            Boolean suspended,
            Integer suspensionRemainingMatches,
            Integer heightCm,
            Map<PlayerSkill, Integer> skillLevels,
            List<PlayerSpecialTrait> specialTraits) {

        public PlayerContextView {
            skillLevels = Map.copyOf(skillLevels);
            specialTraits = List.copyOf(specialTraits);
        }
    }

    public record ScheduledSubstitutionView(
            String teamId,
            String playerOffId,
            String playerOnId,
            int effectiveMinute) {
    }
}
