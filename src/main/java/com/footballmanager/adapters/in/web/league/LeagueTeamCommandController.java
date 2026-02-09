package com.footballmanager.adapters.in.web.league;

import com.footballmanager.adapters.in.web.league.dto.AddTeamToLeagueRequest;
import com.footballmanager.application.service.world.LeagueTeamCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Controlador para operaciones de relación Liga-Equipo.
 * Responsibility: agregar/remover equipos de ligas en Redis y actualizar el WorldSnapshot.
 */
@RestController
@RequestMapping("/api/v1/world/leagues")
@CrossOrigin(origins = "http://localhost:4200")
@RequiredArgsConstructor
public class LeagueTeamCommandController {

    private final LeagueTeamCommandService leagueTeamCommandService;

    /**
     * POST /api/v1/world/leagues/{leagueId}/add-team
     * Agrega un equipo a una liga en Redis y actualiza el WorldSnapshot
     */
    @PostMapping("/{leagueId}/add-team")
    public Mono<ResponseEntity<Void>> addTeamToLeague(
            @PathVariable UUID leagueId,
            @RequestBody AddTeamToLeagueRequest request) {
        return leagueTeamCommandService.addTeamToLeague(request.userId(), leagueId, request.teamId())
                .<ResponseEntity<Void>>thenReturn(ResponseEntity.ok().build());
    }

    /**
     * DELETE /api/v1/world/leagues/{leagueId}/remove-team/{teamId}?userId={userId}
     * Remueve un equipo de una liga en Redis y actualiza el WorldSnapshot
     */
    @DeleteMapping("/{leagueId}/remove-team/{teamId}")
    public Mono<ResponseEntity<Void>> removeTeamFromLeague(
            @PathVariable UUID leagueId,
            @PathVariable UUID teamId,
            @RequestParam UUID userId) {
        return leagueTeamCommandService.removeTeamFromLeague(userId, leagueId, teamId)
                .<ResponseEntity<Void>>thenReturn(ResponseEntity.ok().build());
    }
}
