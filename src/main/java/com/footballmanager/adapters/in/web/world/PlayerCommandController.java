package com.footballmanager.adapters.in.web.world;

import com.footballmanager.adapters.in.web.world.dto.*;
import com.footballmanager.application.service.world.*;
import com.footballmanager.domain.model.entity.WorldPlayer;
import com.footballmanager.domain.model.entity.WorldSnapshot;
import com.footballmanager.domain.ports.in.player.AssignPlayerUseCase;
import com.footballmanager.domain.ports.in.player.RemovePlayerUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/world")
@CrossOrigin(origins = "http://localhost:4200")
@RequiredArgsConstructor
public class PlayerCommandController {

    private final WorldPlayerCommandService worldPlayerCommandService;
    private final AssignPlayerUseCase assignPlayerUseCase;
    private final RemovePlayerUseCase removePlayerUseCase;

    /**
     * POST /api/v1/world/create-custom-player
     * Crea un WorldPlayer custom en WorldSnapshot
     */
    @PostMapping("/create-custom-player")
    public Mono<ResponseEntity<WorldPlayer>> createCustomPlayer(@RequestBody CreateCustomPlayerRequest request) {
        return worldPlayerCommandService.createCustomPlayer(
                        request.userId(),
                        request.name(),
                        request.age(),
                        request.position(),
                        request.attack(),
                        request.defense(),
                        request.technique(),
                        request.speed(),
                        request.stamina(),
                        request.mentality()
                )
                .<ResponseEntity<WorldPlayer>>map(snapshot -> {
                    var players = snapshot.getAllWorldPlayers();
                    WorldPlayer player = players.get(players.size() - 1);
                    return ResponseEntity.ok(player);
                })
                .onErrorResume(e -> {
                    return Mono.just(ResponseEntity.internalServerError().build());
                });
    }

    /**
     * POST /api/v1/world/create-random-player
     * Crea un jugador aleatorio en WorldSnapshot
     */
    @PostMapping("/create-random-player")
    public Mono<ResponseEntity<WorldPlayer>> createRandomPlayer(@RequestBody UUIDRequest request) {
        UUID userId = request.userId();

        return worldPlayerCommandService.createRandomPlayer(userId)
                .<ResponseEntity<WorldPlayer>>map(snapshot -> {
                    var players = snapshot.getAllWorldPlayers();
                    WorldPlayer player = players.get(players.size() - 1);
                    return ResponseEntity.ok(player);
                })
                .onErrorResume(e -> {
                    return Mono.just(ResponseEntity.internalServerError().build());
                });
    }

    /**
     * POST /api/v1/world/create-random-players
     * Crea múltiples jugadores aleatorios en WorldSnapshot
     */
    @PostMapping("/create-random-players")
    public Mono<ResponseEntity<BatchPlayerCreationResponse>> createRandomPlayers(@RequestBody BatchPlayerCreationRequest request) {
        return worldPlayerCommandService.createRandomPlayers(request.userId(), request.count())
                .thenReturn(ResponseEntity.ok(new BatchPlayerCreationResponse(request.count(), "Players created successfully")));
    }

    /**
     * POST /api/v1/world/assign-player
     * Asigna un jugador a un equipo en WorldSnapshot
     */
    @PostMapping("/assign-player")
    public Mono<ResponseEntity<WorldSnapshot>> assignPlayer(@RequestBody AssignPlayerRequest request) {
        return assignPlayerUseCase.execute(request.userId(), request.playerId(), request.teamId())
                .<ResponseEntity<WorldSnapshot>>map(snapshot -> {
                    return ResponseEntity.ok(snapshot);
                })
                .onErrorResume(e -> {
                    return Mono.just(ResponseEntity.internalServerError().build());
                });
    }

    /**
     * POST /api/v1/world/remove-player
     * Remueve un jugador de su equipo (queda como free agent)
     */
    @PostMapping("/remove-player")
    public Mono<ResponseEntity<WorldSnapshot>> removePlayer(@RequestBody RemovePlayerRequest request) {
        return removePlayerUseCase.execute(request.userId(), request.playerId())
                .<ResponseEntity<WorldSnapshot>>map(snapshot -> {
                    return ResponseEntity.ok(snapshot);
                })
                .onErrorResume(e -> {
                    return Mono.just(ResponseEntity.internalServerError().build());
                });
    }
}
