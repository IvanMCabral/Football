package com.footballmanager.adapters.in.web.world;

import com.footballmanager.adapters.in.web.world.dto.*;
import com.footballmanager.application.service.world.*;
import com.footballmanager.domain.model.entity.WorldTeam;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/world")
@CrossOrigin(origins = "http://localhost:4200")
@RequiredArgsConstructor
public class TeamCommandController {

    private final CreateCustomTeamService createCustomTeamService;
    private final WorldTeamCommandService worldTeamCommandService;

    /**
     * POST /api/v1/world/create-custom-team
     * Crea un WorldTeam custom en Redis
     */
    @PostMapping("/create-custom-team")
    public Mono<ResponseEntity<WorldTeam>> createCustomTeam(@RequestBody CreateCustomTeamRequest request) {
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
    public Mono<ResponseEntity<WorldTeam>> createRandomTeam(@RequestParam UUID userId) {
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
    public Mono<ResponseEntity<RandomTeamsResponse>> createRandomTeams(@RequestBody RandomTeamsRequest request) {
        return worldTeamCommandService.createRandomTeams(request.userId(), request.count())
                .thenReturn(ResponseEntity.ok(RandomTeamsResponse.success(request.count())));
    }
}
