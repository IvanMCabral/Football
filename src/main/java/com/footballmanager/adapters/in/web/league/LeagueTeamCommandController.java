package com.footballmanager.adapters.in.web.league;

import com.footballmanager.adapters.in.web.league.dto.AddTeamToLeagueRequest;
import com.footballmanager.application.service.world.LeagueTeamCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Controlador para operaciones de relaciÃ³n Liga-Equipo.
 * Responsibility: agregar/remover equipos de ligas en Redis y actualizar el WorldSnapshot.
 */
@RestController
@RequestMapping("/api/v1/world/leagues")
@RequiredArgsConstructor
@Profile({"dev", "local", "test"})
public class LeagueTeamCommandController {

    private final LeagueTeamCommandService leagueTeamCommandService;

    /**
     * POST /api/v1/world/leagues/{leagueId}/add-team
     * Agrega un equipo a una liga en Redis y actualiza el WorldSnapshot
     */
    @PostMapping("/{leagueId}/add-team")
    public Mono<ResponseEntity<Void>> addTeamToLeague(
            @PathVariable UUID leagueId,
            @RequestBody AddTeamToLeagueRequest request,
            Authentication authentication) {
        UUID authenticatedUserId = authenticatedUserId(authentication);
        validateLegacyUserId(request.userId(), authenticatedUserId);
        return leagueTeamCommandService.addTeamToLeague(authenticatedUserId, leagueId, request.teamId())
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
            @RequestParam(required = false) UUID userId,
            Authentication authentication) {
        UUID authenticatedUserId = authenticatedUserId(authentication);
        validateLegacyUserId(userId, authenticatedUserId);
        return leagueTeamCommandService.removeTeamFromLeague(authenticatedUserId, leagueId, teamId)
                .<ResponseEntity<Void>>thenReturn(ResponseEntity.ok().build());
    }

    private static UUID authenticatedUserId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new IllegalArgumentException("Authenticated user is required");
        }
        return UUID.fromString(authentication.getName());
    }

    private static void validateLegacyUserId(UUID requestUserId, UUID authenticatedUserId) {
        if (requestUserId != null && !requestUserId.equals(authenticatedUserId)) {
            throw new org.springframework.security.access.AccessDeniedException("User mismatch");
        }
    }
}
