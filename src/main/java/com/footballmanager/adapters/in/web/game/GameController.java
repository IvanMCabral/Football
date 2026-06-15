package com.footballmanager.adapters.in.web.game;

import com.footballmanager.adapters.in.web.game.dto.*;
import com.footballmanager.application.service.domain.GameService;
import com.footballmanager.domain.port.in.game.TournamentQueryUseCase;
import com.footballmanager.domain.port.in.match.*;
import com.footballmanager.domain.model.aggregate.Game;
import com.footballmanager.domain.model.entity.RuntimeMatch;
import com.footballmanager.domain.model.valueobject.GameId;
import com.footballmanager.domain.model.valueobject.UserId;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Controller para gestión de Games (partidas guardadas/torneos)
 * Base path: /api/v1/games
 */
@RestController
@RequestMapping("/api/v1/games")
@RequiredArgsConstructor
public class GameController {

    private final GameService gameService;
    private final StartRoundUseCase startRoundUseCase;
    private final GetMatchStateQueryUseCase getMatchStateQueryUseCase;
    private final AdvanceMatchUseCase advanceMatchUseCase;
    private final FinalizeMatchUseCase finalizeMatchUseCase;
    private final TournamentQueryUseCase tournamentQueryUseCase;

    @PostMapping
    public Mono<ResponseEntity<Game>> createGame(@RequestBody CreateGameRequest request, Authentication authentication) {
        String userIdStr = authentication != null ? authentication.getName() : null;
        if (userIdStr == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }
        UserId userId = UserId.of(UUID.fromString(userIdStr));

        if (request.leagueId() == null) {
            return Mono.just(ResponseEntity.badRequest().build());
        }
        UUID leagueId = UUID.fromString(request.leagueId());

        String difficulty = request.difficulty() != null ? request.difficulty() : "NORMAL";
        String gameSpeed = request.gameSpeed() != null ? request.gameSpeed() : "NORMAL";
        int teamsPerDivision = request.teamsPerDivision() != null ? request.teamsPerDivision() : 20;

        Game game = new Game(
            GameId.randomId(),
            userId,
            request.teamId() != null ? com.footballmanager.domain.model.valueobject.TeamId.fromString(request.teamId()) : null,
            request.name(),
            LocalDateTime.now()
        );

        return gameService.createGame(game, leagueId, difficulty, gameSpeed, teamsPerDivision)
            .map(gameCreated -> ResponseEntity.status(HttpStatus.CREATED).body(gameCreated));
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<Game>> getGameById(@PathVariable String id, Authentication authentication) {
        String userIdStr = authentication != null ? authentication.getName() : null;
        if (userIdStr == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }
        UUID userId = UUID.fromString(userIdStr);
        return gameService.getGameById(userId, new GameId(UUID.fromString(id)))
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping("/user/{userId}")
    public Mono<ResponseEntity<Flux<Game>>> getGamesByUserId(@PathVariable String userId, Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }
        String jwtUserIdStr = authentication.getName();
        UUID pathUserUuid;
        UUID jwtUserUuid;
        try {
            pathUserUuid = UUID.fromString(userId);
            jwtUserUuid = UUID.fromString(jwtUserIdStr);
        } catch (IllegalArgumentException e) {
            return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).build());
        }
        if (!pathUserUuid.equals(jwtUserUuid)) {
            return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
        }
        return Mono.just(ResponseEntity.ok(gameService.getGamesByUserId(jwtUserUuid, UserId.of(jwtUserUuid))));
    }

    @GetMapping
    public Mono<ResponseEntity<List<Game>>> getAllGames(Authentication authentication) {
        String userIdStr = authentication != null ? authentication.getName() : null;
        if (userIdStr == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }
        UUID userId = UUID.fromString(userIdStr);
        // V24D12-B.2: collectList() + isEmpty check on the Flux content
        // (not the Mono wrapper) is what makes the 404 fire. The previous
        // B-2 .defaultIfEmpty() was applied to Mono.just(RE) which never
        // emits empty, so the operator never triggered. The Flux<Game>
        // serializes to "[]" before defaultIfEmpty gets a chance to run
        // on the wrapper. The fix is to change the response shape from
        // Mono<ResponseEntity<Flux<Game>>> to Mono<ResponseEntity<List<Game>>>
        // and gate the status code on the collected list size.
        return gameService.getAllGames(userId)
            .collectList()
            .map(list -> list.isEmpty()
                ? ResponseEntity.notFound().<List<Game>>build()
                : ResponseEntity.ok(list));
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> deleteGame(@PathVariable String id, Authentication authentication) {
        String userIdStr = authentication != null ? authentication.getName() : null;
        if (userIdStr == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }
        UUID userId = UUID.fromString(userIdStr);
        return gameService.deleteGame(userId, new GameId(UUID.fromString(id)))
            .then(Mono.just(ResponseEntity.status(HttpStatus.NO_CONTENT).<Void>build()));
    }

    @GetMapping("/{id}/tournament-status")
    public Mono<ResponseEntity<TournamentStatusDTO>> getTournamentStatus(@PathVariable String id, Authentication authentication) {
        String userId = authentication != null ? authentication.getName() : null;
        if (userId == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }
        return tournamentQueryUseCase.getTournamentStatus(userId)
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/standings")
    public Mono<ResponseEntity<List<StandingDTO>>> getStandings(@PathVariable String id, Authentication authentication) {
        String userId = authentication != null ? authentication.getName() : null;
        if (userId == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }
        // V24D12-B.2: same fix as getAllGames - collectList() + isEmpty
        // check. Note: when the tournamentId doesn't exist,
        // tournamentQueryUseCase throws and the ErrorWebExceptionHandler
        // returns 404 with a JSON body (Spring default). When the
        // tournament exists but has no standings, this code returns 404
        // with an empty body (the explicit notFound().build()). This
        // matches the contract that REVISOR's smoke expects for the
        // "no standings" case.
        return tournamentQueryUseCase.getStandings(userId)
            .collectList()
            .map(list -> list.isEmpty()
                ? ResponseEntity.notFound().<List<StandingDTO>>build()
                : ResponseEntity.ok(list));
    }

    @GetMapping("/{id}/champion")
    public Mono<ResponseEntity<ChampionDTO>> getChampion(@PathVariable String id, Authentication authentication) {
        String userId = authentication != null ? authentication.getName() : null;
        if (userId == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }
        return tournamentQueryUseCase.getChampion(userId)
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping("/round/{round}/start")
    public Mono<ResponseEntity<List<RuntimeMatch>>> startRound(
            @RequestParam String careerId,
            @PathVariable int round,
            Authentication authentication) {
        String userIdStr = authentication != null ? authentication.getName() : null;
        if (userIdStr == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }
        UUID userId = UUID.fromString(userIdStr);

        return startRoundUseCase.startRound(userId, careerId, round)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).build());
                });
    }

    @GetMapping("/match/{matchId}")
    public Mono<ResponseEntity<RuntimeMatch>> getMatchState(@PathVariable String matchId, Authentication authentication) {
        String userIdStr = authentication != null ? authentication.getName() : null;
        if (userIdStr == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }
        UUID userId = UUID.fromString(userIdStr);

        return getMatchStateQueryUseCase.getMatchState(userId, matchId)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
                });
    }

    @PostMapping("/match/{matchId}/advance")
    public Mono<ResponseEntity<RuntimeMatch>> advanceMatch(@PathVariable String matchId, Authentication authentication) {
        String userIdStr = authentication != null ? authentication.getName() : null;
        if (userIdStr == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }
        UUID userId = UUID.fromString(userIdStr);

        return advanceMatchUseCase.advanceMatch(userId, matchId)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).build());
                });
    }

    @PostMapping("/match/{matchId}/finalize")
    public Mono<ResponseEntity<Void>> finalizeMatch(@PathVariable String matchId, Authentication authentication) {
        String userIdStr = authentication != null ? authentication.getName() : null;
        if (userIdStr == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }
        UUID userId = UUID.fromString(userIdStr);

        return finalizeMatchUseCase.finalizeMatch(userId, matchId)
                .then(Mono.just(ResponseEntity.ok().<Void>build()))
                .onErrorResume(e -> {
                    return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).build());
                });
    }
}
