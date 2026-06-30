package com.footballmanager.adapters.in.web.world;

import com.footballmanager.adapters.in.web.common.ControllerHelper;
import com.footballmanager.adapters.in.web.world.dto.*;
import com.footballmanager.application.service.world.*;
import com.footballmanager.domain.model.entity.WorldTeam;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * <p><b>V25D78-C50 (impersonation sweep):</b> los 3 endpoints POST ahora
 * validan que el {@code userId} del JWT coincide con el {@code userId}
 * del body/query param. Si NO coincide → 403 IMPERSONATION_FORBIDDEN.
 * Antes de C50, un user A autenticado podía crear/sobreescribir equipos
 * en el WorldSnapshot del user B (privilege escalation / data tampering).
 */
@RestController
@RequestMapping("/api/v1/world")
@CrossOrigin(origins = "http://localhost:4200")
@RequiredArgsConstructor
public class TeamCommandController {

    private final CreateCustomTeamService createCustomTeamService;
    private final WorldTeamCommandService worldTeamCommandService;
    private final ControllerHelper controllerHelper;

    /**
     * POST /api/v1/world/create-custom-team
     * Crea un WorldTeam custom en Redis
     */
    @PostMapping("/create-custom-team")
    public Mono<ResponseEntity<WorldTeam>> createCustomTeam(
            @RequestBody CreateCustomTeamRequest request,
            Authentication authentication) {
        controllerHelper.requireSelfUserId(authentication, request.userId());
        return createCustomTeamService.createCustomTeam(
                request.userId(),
                request.name(),
                request.country(),
                request.budget(),
                request.formation()
        )
        .map(ResponseEntity::ok);
    }

    /**
     * POST /api/v1/world/random-team?userId={userId}
     * Crea un equipo aleatorio en WorldSnapshot
     */
    @PostMapping("/random-team")
    public Mono<ResponseEntity<WorldTeam>> createRandomTeam(
            @RequestParam UUID userId,
            Authentication authentication) {
        controllerHelper.requireSelfUserId(authentication, userId);
        return worldTeamCommandService.createRandomTeam(userId)
                .<ResponseEntity<WorldTeam>>map(snapshot -> {
                    var teams = snapshot.getAllWorldTeams();
                    if (teams.isEmpty()) {
                        return ResponseEntity.internalServerError().build();
                    }
                    WorldTeam team = teams.get(teams.size() - 1);
                    return ResponseEntity.ok(team);
                })
                .onErrorResume(e -> {
                    return Mono.just(ResponseEntity.internalServerError().build());
                });
    }

    /**
     * POST /api/v1/world/random-teams
     * Crea múltiples equipos aleatorios en WorldSnapshot
     */
    @PostMapping("/random-teams")
    public Mono<ResponseEntity<RandomTeamsResponse>> createRandomTeams(
            @RequestBody RandomTeamsRequest request,
            Authentication authentication) {
        controllerHelper.requireSelfUserId(authentication, request.userId());
        return worldTeamCommandService.createRandomTeams(request.userId(), request.count())
                .thenReturn(ResponseEntity.ok(RandomTeamsResponse.success(request.count())));
    }
}