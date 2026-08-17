package com.footballmanager.adapters.in.web.game;

import com.footballmanager.adapters.in.web.game.dto.*;
import com.footballmanager.application.engine.round.RoundEngineRegistry;
import com.footballmanager.application.service.domain.GameService;
import com.footballmanager.domain.port.in.game.TournamentQueryUseCase;
import com.footballmanager.domain.port.in.game.TournamentChampion;
import com.footballmanager.domain.port.in.game.TournamentStanding;
import com.footballmanager.domain.port.in.game.TournamentStatus;
import com.footballmanager.domain.port.in.match.*;
import com.footballmanager.domain.model.aggregate.Game;
import com.footballmanager.domain.model.entity.MatchStateSnapshot;
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
    // C55.14 OBS-1: GameController.matches now returns MatchStateSnapshot
    // legacy GetMatchStateQueryUseCase + MatchRuntimeRepository (Redis)
    private final RoundEngineRegistry roundEngineRegistry;
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

        // to start the Career). Reject null/blank/invalid-UUID with 400 instead of NPE→500.
        if (request.teamId() == null || request.teamId().isBlank()) {
            return Mono.just(ResponseEntity.badRequest().build());
        }
        UUID teamUuid;
        try {
            teamUuid = UUID.fromString(request.teamId());
        } catch (IllegalArgumentException e) {
            return Mono.just(ResponseEntity.badRequest().build());
        }

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
            .map(GameController::toTournamentStatusDto)
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/standings")
    public Mono<ResponseEntity<List<StandingDTO>>> getStandings(@PathVariable String id, Authentication authentication) {
        String userId = authentication != null ? authentication.getName() : null;
        if (userId == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }
        // check. Returns 404 with empty body (the explicit notFound().build())
        // both when the tournament doesn't exist and when it exists but has
        return tournamentQueryUseCase.getStandings(userId)
            .map(GameController::toStandingDto)
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
            .map(GameController::toChampionDto)
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping("/round/{round}/start")
    public Mono<ResponseEntity<List<RuntimeMatchResponse>>> startRound(
            @RequestParam String careerId,
            @PathVariable int round,
            Authentication authentication) {
        String userIdStr = authentication != null ? authentication.getName() : null;
        if (userIdStr == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }
        UUID userId = UUID.fromString(userIdStr);

        return startRoundUseCase.startRound(userId, careerId, round)
                .map(matches -> matches.stream().map(RuntimeMatchResponse::from).toList())
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).build());
                });
    }

    /**
     * C55.14 OBS-1: GET /api/v1/games/match/{matchId} now returns a
     * legacy {@code RuntimeMatch} DTO which lacked
     * {@code homePlayerRatings}, {@code awayPlayerRatings}, and
     * {@code substitutionsRemaining}.
     *
     * <p>Resolution path: parse {@code matchId} as UUID, look up the
     * owning {@code RoundEngine} via {@link RoundEngineRegistry} (the
     * registry is the global source of truth for live matches), then
     * return {@code RoundEngine.getCurrentMatchSnapshot(matchId)} —
     * which delegates to {@code MatchEngine.getCurrentState()} that
     *
     * <p>Status codes:
     * <ul>
     *   <li>401 — no authenticated user</li>
     *   <li>400 — {@code matchId} is not a valid UUID</li>
     *   <li>404 — no active round owns this matchId (match finished
     *       and the registry was unregistered, or the round has been
     *       stopped, or the match was never started)</li>
     * </ul>
     *
     * <p>The registry is global for lookup, but ownership is proven from the
     * resolved engine before its private match snapshot is read.
     */
    @GetMapping("/match/{matchId}")
    public Mono<ResponseEntity<MatchStateSnapshot>> getMatchState(@PathVariable String matchId, Authentication authentication) {
        String userIdStr = authentication != null ? authentication.getName() : null;
        if (userIdStr == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }
        UUID userId = UUID.fromString(userIdStr);

        UUID matchIdUuid;
        try {
            matchIdUuid = UUID.fromString(matchId);
        } catch (IllegalArgumentException e) {
            return Mono.just(ResponseEntity.badRequest().build());
        }

        return Mono.justOrEmpty(roundEngineRegistry.getByMatchId(matchIdUuid))
                .flatMap(roundEngine -> {
                    // Prove ownership from engine metadata before touching the
                    // private match snapshot. A foreign caller may discover
                    // that a match exists, but cannot cause its state to be
                    // read before the owner check succeeds.
                    if (!roundEngine.belongsTo(userId, null)) {
                        return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN)
                                .<MatchStateSnapshot>build());
                    }
                    MatchStateSnapshot snapshot = roundEngine.getCurrentMatchSnapshot(matchIdUuid);
                    if (snapshot == null) {
                        return Mono.just(ResponseEntity.notFound().<MatchStateSnapshot>build());
                    }
                    if (snapshot.userId() == null || snapshot.userId().isBlank()) {
                        return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN)
                                .<MatchStateSnapshot>build());
                    }
                    if (!snapshot.userId().equals(userId.toString())) {
                        return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN)
                                .<MatchStateSnapshot>build());
                    }
                    return Mono.just(ResponseEntity.ok(snapshot));
                })
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping("/match/{matchId}/advance")
    public Mono<ResponseEntity<RuntimeMatchResponse>> advanceMatch(@PathVariable String matchId, Authentication authentication) {
        String userIdStr = authentication != null ? authentication.getName() : null;
        if (userIdStr == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }
        UUID userId = UUID.fromString(userIdStr);

        return advanceMatchUseCase.advanceMatch(userId, matchId)
                .map(RuntimeMatchResponse::from)
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

    private static TournamentStatusDTO toTournamentStatusDto(TournamentStatus status) {
        return new TournamentStatusDTO(
                status.currentRound(),
                status.totalRounds(),
                status.hasNextRound(),
                status.isFinished(),
                status.champion() != null ? toChampionDto(status.champion()) : null);
    }

    private static StandingDTO toStandingDto(TournamentStanding standing) {
        return new StandingDTO(
                standing.teamId(),
                standing.teamName(),
                standing.played(),
                standing.wins(),
                standing.draws(),
                standing.losses(),
                standing.goalsFor(),
                standing.goalsAgainst(),
                standing.goalDifference(),
                standing.points());
    }

    private static ChampionDTO toChampionDto(TournamentChampion champion) {
        return new ChampionDTO(
                champion.teamId(),
                champion.teamName(),
                champion.points(),
                champion.wins(),
                champion.goalDifference());
    }
}
