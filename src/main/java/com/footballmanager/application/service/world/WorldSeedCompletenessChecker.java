package com.footballmanager.application.service.world;

import com.footballmanager.domain.model.entity.WorldLeague;
import com.footballmanager.domain.model.entity.WorldPlayer;
import com.footballmanager.domain.model.entity.WorldSnapshot;
import com.footballmanager.domain.model.entity.WorldTeam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Detects a fully materialized seed without issuing idempotent database writes. */
@Component
final class WorldSeedCompletenessChecker {

    boolean isComplete(WorldSnapshot snapshot, List<LaLigaSeedData> seeds) {
        if (snapshot == null || seeds == null || seeds.isEmpty()) return false;
        Map<String, WorldLeague> leagues = new HashMap<>();
        for (WorldLeague league : snapshot.getLeagues()) {
            if (league != null && league.getName() != null) leagues.put(normalize(league.getName()), league);
        }
        Map<String, WorldTeam> teams = new HashMap<>();
        Map<String, String> teamNamesById = new HashMap<>();
        for (WorldTeam team : snapshot.getAllWorldTeams()) {
            if (team != null && team.getName() != null) {
                teams.put(normalize(team.getName()), team);
                teamNamesById.put(team.getWorldTeamId(), team.getName());
            }
        }
        Map<String, WorldPlayer> players = new HashMap<>();
        for (WorldPlayer player : snapshot.getAllWorldPlayers()) {
            String teamName = teamNamesById.get(player.getWorldTeamId());
            if (teamName != null && player.getName() != null) {
                players.put(playerKey(teamName, player.getName()), player);
            }
        }
        Map<String, ExpectedTeam> expectedTeams = new java.util.LinkedHashMap<>();
        Map<String, LaLigaSeedData.PlayerDto> expectedPlayers = new java.util.LinkedHashMap<>();
        for (LaLigaSeedData seed : seeds) {
            if (!leagueMatches(leagues.get(normalize(seed.league().name())), seed.league())) return false;
            for (LaLigaSeedData.TeamDto expected : seed.teams()) {
                expectedTeams.put(normalize(expected.name()), new ExpectedTeam(seed.league().name(), expected));
            }
            for (LaLigaSeedData.PlayerDto expected : seed.players()) {
                expectedPlayers.put(playerKey(expected.team(), expected.name()), expected);
            }
        }
        for (ExpectedTeam expected : expectedTeams.values()) {
            WorldTeam actual = teams.get(normalize(expected.value().name()));
            WorldLeague league = leagues.get(normalize(expected.leagueName()));
            if (!teamMatches(actual, expected.value()) || league == null
                    || !Objects.equals(actual.getRealLeagueId(), league.getRealLeagueId())) return false;
        }
        for (Map.Entry<String, LaLigaSeedData.PlayerDto> entry : expectedPlayers.entrySet()) {
            if (!playerMatches(players.get(entry.getKey()), entry.getValue())) return false;
        }
        return true;
    }

    private static boolean leagueMatches(WorldLeague actual, LaLigaSeedData.LeagueDto expected) {
        return actual != null
                && Objects.equals(actual.getName(), expected.name())
                && Objects.equals(actual.getCountry(), expected.country())
                && actual.getTier() == (expected.tier() == null ? 1 : expected.tier());
    }

    private static boolean teamMatches(WorldTeam actual, LaLigaSeedData.TeamDto expected) {
        if (actual == null
                || !Objects.equals(actual.getName(), expected.name())
                || expected.city() != null && !Objects.equals(actual.getCity(), expected.city())
                || expected.formation() != null && !Objects.equals(actual.getBaseFormation(), expected.formation())) {
            return false;
        }
        if (expected.budgetMillions() == null) return true;
        BigDecimal expectedBudget = BigDecimal.valueOf(expected.budgetMillions()).multiply(BigDecimal.valueOf(1_000_000L));
        return actual.getBaseBudget() != null && actual.getBaseBudget().compareTo(expectedBudget) == 0;
    }

    private static boolean playerMatches(WorldPlayer actual, LaLigaSeedData.PlayerDto expected) {
        if (actual == null) return false;
        if (!matches(expected.age(), actual.getAge())
                || !matches(expected.position(), actual.getPosition())
                || !matches(expected.baseAttack(), actual.getBaseAttack())
                || !matches(expected.baseDefense(), actual.getBaseDefense())
                || !matches(expected.baseTechnique(), actual.getBaseTechnique())
                || !matches(expected.baseSpeed(), actual.getBaseSpeed())
                || !matches(expected.baseStamina(), actual.getBaseStamina())
                || !matches(expected.baseMentality(), actual.getBaseMentality())
                || !matches(expected.heightCm(), actual.getHeightCm())) {
            return false;
        }
        return expected.skillLevels() == null || Objects.equals(expected.skillLevels(), actual.getSkillLevels());
    }

    private static boolean matches(Object expected, Object actual) {
        return expected == null || Objects.equals(expected, actual);
    }

    private static String playerKey(String team, String player) {
        return normalize(team) + "|" + normalize(player);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private record ExpectedTeam(String leagueName, LaLigaSeedData.TeamDto value) { }
}
