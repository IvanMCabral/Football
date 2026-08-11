package com.footballmanager.application.service.world;

import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.valueobject.LineupSlot;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Authoritative inventory and extractor for durable world-ID references. */
@Component
public final class WorldMigrationReferenceInventory {

    private static final String UNRESOLVED_SESSION_TEAM = "__unresolved_session_team__:";
    private static final String UNRESOLVED_SESSION_PLAYER = "__unresolved_session_player__:";

    private final WorldMigrationDurableReferenceRegistry registry;

    public WorldMigrationReferenceInventory() {
        this(new WorldMigrationDurableReferenceRegistry());
    }

    @Autowired
    public WorldMigrationReferenceInventory(WorldMigrationDurableReferenceRegistry registry) {
        this.registry = registry;
        this.registry.requireComplete();
    }

    public WorldReferenceGraph discover(CareerSave career) {
        if (career == null) return WorldReferenceGraph.empty();
        Map<String, Set<String>> teams = new LinkedHashMap<>();
        Map<String, Set<String>> players = new LinkedHashMap<>();
        Map<String, SessionTeam> sessionTeams = career.getTeamManager().getSessionTeams();
        Map<String, SessionPlayer> sessionPlayers = career.getSessionPlayers();

        sessionTeams.values().forEach(team -> add(teams, "career.sessionTeams", team.getWorldTeamId()));
        if (career.getUserSessionTeamId() != null) {
            addTeamFromSession(teams, "career.selectedTeam", career.getUserSessionTeamId(), sessionTeams);
        }
        sessionPlayers.values().forEach(player -> add(players, "career.sessionPlayers", player.getWorldPlayerId()));

        career.getTeamManager().getTeamSquads().forEach((teamId, ids) -> {
            addTeamFromSession(teams, "career.squad.team", teamId, sessionTeams);
            if (ids != null) ids.forEach(id -> addPlayerFromSession(players, "career.squad", id, sessionPlayers));
        });
        career.getTeamStarting11().forEach((teamId, ids) -> {
            addTeamFromSession(teams, "career.startingXI.team", teamId, sessionTeams);
            if (ids != null) ids.forEach(id -> addPlayerFromSession(players, "career.startingXI", id, sessionPlayers));
        });
        career.getTeamStarting11SubdivisionSlots().forEach((teamId, slots) -> {
            addTeamFromSession(teams, "career.lineupSlots.team", teamId, sessionTeams);
            if (slots != null) slots.values().stream().map(LineupSlot::playerId)
                    .forEach(id -> addPlayerFromSession(players, "career.lineupSlots", id, sessionPlayers));
        });

        career.getRemovedPlayers().forEach((worldTeamId, worldPlayerIds) -> {
            add(teams, "career.removedPlayers.team", worldTeamId);
            if (worldPlayerIds != null) worldPlayerIds.forEach(id ->
                    add(players, "career.removedPlayers.player", id));
        });

        career.getTournamentState().getFixtures().forEach(fixture -> {
            addTeamFromSession(teams, "career.fixtures.home", fixture.getHomeTeamId(), sessionTeams);
            addTeamFromSession(teams, "career.fixtures.away", fixture.getAwayTeamId(), sessionTeams);
        });
        career.getTournamentState().getStandings().keySet().forEach(id ->
                addTeamFromSession(teams, "career.standings", id, sessionTeams));

        return new WorldReferenceGraph(teams, players);
    }

    public Set<String> declaredIdFields() {
        return registry.referencePaths();
    }

    public Map<String, Coverage> coverage() {
        Map<String, Coverage> coverage = new LinkedHashMap<>();
        registry.referencePaths().forEach(field -> coverage.put(field, Coverage.VALIDATED));
        coverage.put("runtime/state/commands/detail/baseline/ratings/timeline",
                Coverage.SESSION_SCOPED_NO_DIRECT_WORLD_ID);
        coverage.put("history/results", Coverage.VALIDATED_THROUGH_FIXTURE_SESSION_TEAMS);
        coverage.put("bench/substitutions/injuries",
                Coverage.VALIDATED_THROUGH_SESSION_PLAYER_OR_SQUAD);
        return Map.copyOf(coverage);
    }

    private static void addTeamFromSession(Map<String, Set<String>> refs, String surface, String sessionId,
                                           Map<String, SessionTeam> sessionTeams) {
        if (sessionId == null) return;
        SessionTeam team = sessionTeams.get(sessionId);
        add(refs, surface, team == null ? UNRESOLVED_SESSION_TEAM + sessionId : team.getWorldTeamId());
    }

    private static void addPlayerFromSession(Map<String, Set<String>> refs, String surface, String sessionId,
                                             Map<String, SessionPlayer> sessionPlayers) {
        if (sessionId == null) return;
        SessionPlayer player = sessionPlayers.get(sessionId);
        add(refs, surface, player == null ? UNRESOLVED_SESSION_PLAYER + sessionId : player.getWorldPlayerId());
    }

    private static void add(Map<String, Set<String>> refs, String surface, String value) {
        if (value != null) refs.computeIfAbsent(surface, ignored -> new LinkedHashSet<>()).add(value);
    }

    public enum Coverage {
        VALIDATED,
        VALIDATED_THROUGH_FIXTURE_SESSION_TEAMS,
        VALIDATED_THROUGH_SESSION_PLAYER_OR_SQUAD,
        SESSION_SCOPED_NO_DIRECT_WORLD_ID
    }
}
