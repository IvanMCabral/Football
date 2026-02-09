package com.footballmanager.application.service.query;

import com.footballmanager.adapters.in.web.world.dto.TeamWithOVR;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.entity.WorldPlayer;
import com.footballmanager.domain.model.entity.WorldTeam;
import com.footballmanager.domain.model.view.WorldView;
import com.footballmanager.domain.ports.in.query.BuildWorldViewUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Servicio para consultas de Teams con OVR.
 * Usa WorldView para obtener datos.
 */
@Service
@RequiredArgsConstructor
public class TeamOVRQueryService {

    private final BuildWorldViewUseCase buildWorldViewUseCase;

    // ========== Shared Sorting Logic (used by CareerSave and Preview) ==========

    /**
     * Comparator for sorting SessionTeams by OVR (desc), then budget (desc), then name (asc).
     * Reused by CareerSave.assignTeamsToDivisions() and DivisionPreviewService.
     */
    public static Comparator<SessionTeam> sessionTeamComparator(
            java.util.function.Function<String, Integer> ovrProvider) {
        return (a, b) -> {
            int ovrA = ovrProvider.apply(a.getSessionTeamId());
            int ovrB = ovrProvider.apply(b.getSessionTeamId());
            if (ovrA != ovrB) {
                return Integer.compare(ovrB, ovrA);  // Higher OVR first
            }
            int budgetCompare = b.getBudget().compareTo(a.getBudget());
            if (budgetCompare != 0) {
                return budgetCompare;  // Higher budget first
            }
            return a.getName().compareTo(b.getName());  // Alphabetical
        };
    }

    /**
     * Comparator for sorting TeamWithOVR by OVR (desc), then budget (desc), then name (asc).
     */
    public static Comparator<TeamWithOVR> teamWithOVRComparator() {
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
     * Construye lista de TeamWithOVR para una lista de equipos
     */
    public Mono<List<TeamWithOVR>> buildTeamsWithOVR(UUID userId, List<WorldTeam> teams) {
        return buildWorldViewUseCase.build(userId)
                .map(worldView -> buildTeamsWithOVRFromView(worldView, teams));
    }

    private List<TeamWithOVR> buildTeamsWithOVRFromView(WorldView worldView, List<WorldTeam> teams) {
        List<TeamWithOVR> teamsWithOVR = new ArrayList<>();

        for (WorldTeam team : teams) {
            List<WorldPlayer> players = worldView.getPlayersByTeam(team.getWorldTeamId());
            int ovr = calculateTeamOVR(players);
            teamsWithOVR.add(new TeamWithOVR(
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
