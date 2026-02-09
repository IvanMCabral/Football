package com.footballmanager.adapters.in.web.player;

import com.footballmanager.adapters.in.web.common.ControllerHelper;
import com.footballmanager.domain.model.entity.Player;
import com.footballmanager.domain.port.in.player.PlayerManagementUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Controller reactivo para gestión de jugadores con scope de usuario
 */
@RestController
@RequestMapping("/api/v1/players")
public class PlayerControllerReactive {

    private final ControllerHelper controllerHelper;
    private final PlayerManagementUseCase playerManagementUseCase;

    public PlayerControllerReactive(
            ControllerHelper controllerHelper,
            PlayerManagementUseCase playerManagementUseCase) {
        this.controllerHelper = controllerHelper;
        this.playerManagementUseCase = playerManagementUseCase;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Player> createPlayer(@RequestBody Player player, Authentication authentication) {
        UUID userId = controllerHelper.getUserId(authentication);
        return playerManagementUseCase.createPlayer(userId, player);
    }

    @GetMapping("/{playerId}")
    public Mono<Player> getPlayer(@PathVariable UUID playerId, Authentication authentication) {
        UUID userId = controllerHelper.getUserId(authentication);
        return playerManagementUseCase.getPlayer(userId, playerId);
    }

    @GetMapping
    public Flux<Player> getAllPlayers(Authentication authentication) {
        UUID userId = controllerHelper.getUserId(authentication);
        return playerManagementUseCase.getAllPlayersByUserId(userId);
    }

    @GetMapping("/available")
    public Flux<Player> getAvailablePlayers(Authentication authentication) {
        UUID userId = controllerHelper.getUserId(authentication);
        return playerManagementUseCase.getAvailablePlayersByUserId(userId);
    }

    @GetMapping("/by-team/{teamId}")
    public Flux<Player> getPlayersByTeam(@PathVariable UUID teamId) {
        // Este método accede a DB directamente (sin userId)
        return playerManagementUseCase.getPlayersByTeam(teamId);
    }

    @PutMapping("/{playerId}")
    public Mono<Player> updatePlayer(@PathVariable UUID playerId, @RequestBody Player player, Authentication authentication) {
        UUID userId = controllerHelper.getUserId(authentication);
        return playerManagementUseCase.updatePlayer(userId, playerId, player);
    }

    @DeleteMapping("/{playerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deletePlayer(@PathVariable UUID playerId, Authentication authentication) {
        UUID userId = controllerHelper.getUserId(authentication);
        return playerManagementUseCase.deletePlayer(userId, playerId);
    }

    @PatchMapping("/{playerId}/attributes")
    public Mono<Player> updatePlayerAttributes(
            @PathVariable UUID playerId,
            @RequestParam int skillChange,
            Authentication authentication) {
        UUID userId = controllerHelper.getUserId(authentication);
        return playerManagementUseCase.updatePlayerAttributes(userId, playerId, skillChange);
    }
}

