package com.footballmanager.application.service.query;


import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.entity.WorldPlayer;
import com.footballmanager.domain.model.entity.WorldTeam;
import com.footballmanager.domain.model.view.WorldPlayerOvrProjection;
import com.footballmanager.domain.model.view.WorldView;
import com.footballmanager.application.service.world.WorldQueryService;
import com.footballmanager.domain.service.SessionTeamRankingPolicy;
import com.footballmanager.domain.ports.out.player.PlayerRepository;
import com.footballmanager.domain.ports.out.world.WorldSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Servicio para consultas de Teams con OVR.
 * Usa WorldView para obtener datos.
 */
@Service
@RequiredArgsConstructor
public class TeamOVRQueryService {

    private final WorldQueryService worldQueryService;
    private final PlayerRepository playerRepository;
    private final WorldSnapshotRepository worldSnapshotRepository;

    // ========== Shared Sorting Logic (used by CareerSave and Preview) ==========

    /**
     * Comparator for sorting SessionTeams by OVR (desc), then budget (desc), then name (asc).
     * Reused by CareerSave.assignTeamsToDivisions() and DivisionPreviewViewService.
     */
    public static Comparator<SessionTeam> sessionTeamComparator(
            java.util.function.Function<String, Integer> ovrProvider) {
        return SessionTeamRankingPolicy.byStrengthBudgetAndName(ovrProvider);
    }

    /**
     * Comparator for sorting TeamOvrView by OVR (desc), then budget (desc), then name (asc).
     */
    public static Comparator<TeamOvrView> teamWithOVRComparator() {
        return (a, b) -> {
            if (a.ovr() != b.ovr()) {
                return Integer.compare(b.ovr(), a.ovr());  // Higher OVR first
            }
            int budgetCompare = b.budget().compareTo(a.budget());
            if (budgetCompare != 0) {
                return budgetCompare;  // Higher budget first
            }
            return a.name().compareTo(b.name());  // Alphabetical
        };
    }

    // ========== Instance Methods ==========

    /**
     * Calcula el OVR promedio de una lista de jugadores
     */
    public int calculateTeamOVR(List<WorldPlayer> players) {
        if (players == null || players.isEmpty()) {
            return 50;
        }
        int totalOVR = 0;
        int count = 0;
        for (WorldPlayer player : players) {
            int ovr = player.calculateOverall();
            totalOVR += ovr;
            count++;
        }
        return count > 0 ? totalOVR / count : 50;
    }

    /**
     * Construye lista de TeamOvrView para una lista de equipos
     */
    public Mono<List<TeamOvrView>> buildTeamsWithOVR(UUID userId, List<WorldTeam> teams) {
        return worldQueryService.worldViewForQuery(userId)
                .map(worldView -> buildTeamsWithOVRFromView(worldView, teams));
    }

    /**
     * Builds the league catalog and OVR projection from one world-view read.
     * The previous controller path built the same large world view once to
     * select league teams and a second time to calculate OVR, doubling Redis
     * deserialization and canonical merge cost during career setup.
     */
    public Mono<List<TeamOvrView>> buildTeamsWithOVR(UUID userId, UUID leagueId) {
        return worldSnapshotRepository.existsByUserId(userId)
                .flatMap(snapshotExists -> snapshotExists
                        ? worldQueryService.worldViewForQuery(userId)
                            .map(worldView -> buildTeamsWithOVRFromView(
                                    worldView, worldView.getTeamsByLeague(leagueId)))
                        : buildCanonicalTeamsWithOVR(userId, leagueId));
    }

    private Mono<List<TeamOvrView>> buildCanonicalTeamsWithOVR(UUID userId, UUID leagueId) {
        return worldQueryService.getCanonicalTeamsByLeague(leagueId)
                .zipWith(playerRepository.findPlayersForOvrFromDatabase(),
                        this::buildTeamsWithOVRFromProjections);
    }

    private List<TeamOvrView> buildTeamsWithOVRFromProjections(
            List<WorldTeam> teams,
            List<WorldPlayerOvrProjection> projections) {
        Map<UUID, List<WorldPlayerOvrProjection>> projectionsByTeam = projections.stream()
                .collect(Collectors.groupingBy(WorldPlayerOvrProjection::teamId));
        List<TeamOvrView> result = new ArrayList<>();
        for (WorldTeam team : teams) {
            List<WorldPlayerOvrProjection> teamPlayers = projectionsByTeam.getOrDefault(
                    team.getRealTeamId(), List.of());
            int playerCount = teamPlayers.size();
            int ovr = calculateTeamOvrFromProjections(teamPlayers);
            result.add(new TeamOvrView(
                    team.getWorldTeamId(),
                    team.getName(),
                    team.getCountry(),
                    team.getBaseFormation() != null ? team.getBaseFormation().toString() : "4-3-3",
                    ovr,
                    playerCount,
                    team.getBaseBudget() != null ? team.getBaseBudget() : BigDecimal.ZERO));
        }
        result.sort(teamWithOVRComparator());
        return result;
    }

    private int calculateTeamOvrFromProjections(List<WorldPlayerOvrProjection> players) {
        if (players == null || players.isEmpty()) {
            return 50;
        }
        int totalOvr = players.stream()
                .mapToInt(WorldPlayerOvrProjection::calculateOverall)
                .sum();
        return totalOvr / players.size();
    }

    private List<TeamOvrView> buildTeamsWithOVRFromView(WorldView worldView, List<WorldTeam> teams) {
        List<TeamOvrView> teamsWithOVR = new ArrayList<>();

        for (WorldTeam team : teams) {
            List<WorldPlayer> players = worldView.getPlayersByTeam(team.getWorldTeamId());
            int ovr = calculateTeamOVR(players);
            teamsWithOVR.add(new TeamOvrView(
                    team.getWorldTeamId(),
                    team.getName(),
                    team.getCountry(),
                    team.getBaseFormation() != null ? team.getBaseFormation().toString() : "4-3-3",
                    ovr,
                    players.size(),
                    team.getBaseBudget() != null ? team.getBaseBudget() : BigDecimal.ZERO
            ));
        }

        // Sort by OVR descending, then by budget descending, then by name (deterministic)
        teamsWithOVR.sort(teamWithOVRComparator());

        return teamsWithOVR;
    }
}

