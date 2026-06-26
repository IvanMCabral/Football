package com.footballmanager.adapters.in.web.versus;

import com.footballmanager.adapters.in.web.common.ControllerHelper;
import com.footballmanager.domain.model.entity.*;
import com.footballmanager.domain.model.valueobject.*;
import com.footballmanager.domain.ports.out.match.MatchRepository;
import com.footballmanager.domain.port.in.match.MatchSimulationUseCase;
import com.footballmanager.domain.port.in.match.GetMatchStateQueryUseCase;
import com.footballmanager.domain.port.in.match.AdvanceMatchUseCase;
import com.footballmanager.domain.port.in.match.ExecuteMatchCommandUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/matches")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
public class MatchControllerReactive {
    private final MatchRepository matchRepository;
    private final MatchSimulationUseCase matchSimulationService;
    private final GetMatchStateQueryUseCase getMatchStateQueryUseCase;
    private final AdvanceMatchUseCase advanceMatchUseCase;
    private final ExecuteMatchCommandUseCase executeMatchCommandUseCase;
    // V24D12-B: use ControllerHelper for userId extraction so the 401 path
    // matches V24D12's UnauthorizedException -> 401 contract instead of
    // leaking the inline NPE on UUID.fromString(null).
    private final ControllerHelper controllerHelper;

    @PostMapping("/{matchId}/advance")
    public Mono<ResponseEntity<RuntimeMatch>> advanceMatch(@PathVariable String matchId, @RequestBody AdvanceRequest req, Authentication authentication) {
        UUID userId = controllerHelper.getUserId(authentication);
        return advanceMatchUseCase.advanceMatch(userId, matchId)
            .map(ResponseEntity::ok)
            .onErrorResume(e -> {
                return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).build());
            });
    }

    @PostMapping("/{matchId}/commands")
    public Mono<ResponseEntity<String>> applyCommand(@PathVariable String matchId, @RequestBody MatchCommand command, Authentication authentication) {
        UUID userId = controllerHelper.getUserId(authentication);
        UUID id = UUID.fromString(matchId);
        return executeMatchCommandUseCase.execute(userId, id, command)
            .map(applied -> applied
                ? ResponseEntity.ok("Comando aplicado")
                : ResponseEntity.badRequest().body("Comando no aplicado"))
            .onErrorResume(e -> {
                return Mono.just(ResponseEntity.badRequest().body(e.getMessage()));
            });
    }

    @GetMapping("/{matchId}/state")
    public Mono<ResponseEntity<RuntimeMatch>> getMatchState(@PathVariable String matchId, Authentication authentication) {
        UUID userId = controllerHelper.getUserId(authentication);
        return getMatchStateQueryUseCase.getMatchState(userId, matchId)
            .map(ResponseEntity::ok)
            .switchIfEmpty(Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).build()))
            .onErrorResume(e -> {
                return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
            });
    }

    public record AdvanceRequest(int toMinute) {}

    @GetMapping
    public Flux<MatchDTO> getMatches(@RequestParam(value = "gameId", required = false) String gameId, Authentication authentication) {
        UUID userId = controllerHelper.getUserId(authentication);

        if (gameId != null && !gameId.isEmpty()) {
            return matchRepository.findByGameId(userId, new GameId(UUID.fromString(gameId)))
                .map(this::mapToDTO);
        } else {
            return matchRepository.findAll(userId)
                .map(this::mapToDTO);
        }
    }

    @PostMapping
    public Mono<ResponseEntity<Object>> createMatch(@RequestBody CreateMatchRequest request, Authentication authentication) {
        UUID userId = controllerHelper.getUserId(authentication);

        // V25D37-F3: pre-validate the request body before touching UUID.fromString.
        // Before this fix, an empty/malformed body ({} or missing homeTeamId/awayTeamId)
        // caused UUID.fromString(null) → NPE → 500 Internal Server Error with the
        // confusing message "Cannot invoke \"String.length()\" because \"name\" is null"
        // (BUG_MATCH_DETAIL_NPE_ON_BAD_BODY — actually surfaces on the
        // {@code POST /api/v1/matches} endpoint, not a /match-detail endpoint).
        // Now we return 400 Bad Request with a clear, structured error body.
        if (request == null) {
            return Mono.just(ResponseEntity.badRequest().body(
                java.util.Map.of("error", "request body must not be null")));
        }
        if (request.homeTeamId() == null || request.homeTeamId().isBlank()) {
            return Mono.just(ResponseEntity.badRequest().body(
                java.util.Map.of("error", "homeTeamId must not be blank")));
        }
        if (request.awayTeamId() == null || request.awayTeamId().isBlank()) {
            return Mono.just(ResponseEntity.badRequest().body(
                java.util.Map.of("error", "awayTeamId must not be blank")));
        }
        if (request.scheduledAt() == null) {
            return Mono.just(ResponseEntity.badRequest().body(
                java.util.Map.of("error", "scheduledAt must not be null")));
        }

        UUID homeUuid;
        UUID awayUuid;
        try {
            homeUuid = UUID.fromString(request.homeTeamId());
            awayUuid = UUID.fromString(request.awayTeamId());
        } catch (IllegalArgumentException ex) {
            // Malformed UUID string (non-null, non-blank, but invalid format).
            return Mono.just(ResponseEntity.badRequest().body(
                java.util.Map.of("error", "teamIds must be valid UUIDs: " + ex.getMessage())));
        }

        MatchId matchId = MatchId.generate();
        TeamId homeTeamId = TeamId.of(homeUuid);
        TeamId awayTeamId = TeamId.of(awayUuid);

        Match match = Match.schedule(matchId, homeTeamId, awayTeamId, request.scheduledAt(), 1);
        return matchRepository.save(userId, match)
            .then(Mono.just(ResponseEntity.status(HttpStatus.CREATED).body((Object) mapToDTO(match))));
    }

    @PostMapping("/{matchId}/simulate")
    public Mono<ResponseEntity<MatchDTO>> simulateMatch(@PathVariable String matchId, Authentication authentication) {
        UUID userId = controllerHelper.getUserId(authentication);
        UUID matchUuid = UUID.fromString(matchId);

        return matchSimulationService.advanceMatch(userId, matchUuid, 90)
                .flatMap(state -> {
                    return matchRepository.findById(userId, MatchId.of(matchUuid))
                            .flatMap(match -> {
                                if (state.getScore() != null) {
                                    MatchResult result = MatchResult.of(
                                        state.getScore().home(),
                                        state.getScore().away(),
                                        50, 50,
                                        10, 10,
                                        null, null
                                    );
                                    match.simulate(result);
                                }
                                return Mono.just(ResponseEntity.ok(mapToDTO(match)));
                            })
                            .defaultIfEmpty(ResponseEntity.notFound().build());
                })
                .onErrorResume(e -> {
                    return Mono.just(ResponseEntity.badRequest().<MatchDTO>build());
                });
    }

    @GetMapping("/{matchId}")
    public Mono<ResponseEntity<MatchDTO>> getMatch(@PathVariable String matchId, Authentication authentication) {
        UUID userId = controllerHelper.getUserId(authentication);
        return matchRepository.findById(userId, MatchId.of(UUID.fromString(matchId)))
                .map(this::mapToDTO)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping("/scheduled")
    public Flux<MatchDTO> getScheduledMatches(Authentication authentication) {
        return Flux.empty();
    }

    private MatchDTO mapToDTO(Match match) {
        return new MatchDTO(
                match.getId().getValue().toString(),
                match.getHomeTeamId().getValue().toString(),
                match.getAwayTeamId().getValue().toString(),
                match.getScheduledAt(),
                match.getStatus().name(),
                match.getResult(),
                match.getCreatedAt(),
                match.getSimulatedAt(),
                match.getRound()
        );
    }

    public record CreateMatchRequest(
            String homeTeamId,
            String awayTeamId,
            Instant scheduledAt
    ) {}

    public record MatchDTO(
            String id,
            String homeTeamId,
            String awayTeamId,
            Instant scheduledAt,
            String status,
            MatchResult result,
            Instant createdAt,
            Instant simulatedAt,
            Integer round
    ) {}
}
