package com.footballmanager.adapters.in.web.versus;

import com.footballmanager.adapters.in.web.common.ControllerHelper;
import com.footballmanager.application.service.career.CareerSessionService;
import com.footballmanager.application.service.simulation.v24.V24DetailedMatchData;
import com.footballmanager.application.service.simulation.v24.V24DetailedMatchQueryService;
import com.footballmanager.application.service.simulation.v24.V24MatchEventDto;
import com.footballmanager.application.service.world.WorldSnapshotService;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    // V25D78-C53 Bug #1 fix: resolve teamId -> teamName for MatchDTO so the
    // frontend doesn't show "Team vs Team" placeholders. Pre-fetched once
    // per request (the snapshot is per-user) and cached in a local Map for
    // synchronous resolution inside mapToDTO.
    private final WorldSnapshotService worldSnapshotService;
    // V25D78-C53 Bug #3 fix: minute-by-minute endpoint. Reads V24 detail
    // from Redis via the user's active careerId (CareerSessionService) +
    // the V24 query service that already exists for /careers/{careerId}/matches/{matchId}/detail.
    private final CareerSessionService careerSessionService;
    private final V24DetailedMatchQueryService v24DetailedMatchQueryService;

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
        UUID id;
        try {
            id = UUID.fromString(matchId);
        } catch (IllegalArgumentException ex) {
            return Mono.just(ResponseEntity.badRequest().body("matchId must be a valid UUID"));
        }
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

        Mono<Map<String, String>> teamNameIndex = worldSnapshotService.getSnapshot(userId)
                .map(snapshot -> {
                    Map<String, String> idx = new HashMap<>();
                    snapshot.getAllWorldTeams().forEach(t -> {
                        if (t.getWorldTeamId() != null && t.getName() != null) {
                            idx.put(t.getWorldTeamId(), t.getName());
                        }
                    });
                    return idx;
                })
                .defaultIfEmpty(Map.of());

        Flux<Match> matchesFlux;
        if (gameId != null && !gameId.isEmpty()) {
            matchesFlux = matchRepository.findByGameId(userId, new GameId(UUID.fromString(gameId)));
        } else {
            matchesFlux = matchRepository.findAll(userId);
        }

        return teamNameIndex.flatMapMany(idx -> matchesFlux.map(m -> mapToDTO(m, idx)));
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
            .then(Mono.just(ResponseEntity.status(HttpStatus.CREATED).body((Object) mapToDTO(match, Map.of()))));
    }

    @PostMapping("/{matchId}/simulate")
    public Mono<ResponseEntity<MatchDTO>> simulateMatch(@PathVariable String matchId, Authentication authentication) {
        UUID userId = controllerHelper.getUserId(authentication);
        UUID matchUuid;
        try {
            matchUuid = UUID.fromString(matchId);
        } catch (IllegalArgumentException ex) {
            return Mono.just(ResponseEntity.badRequest().<MatchDTO>build());
        }

        return worldSnapshotService.getSnapshot(userId)
                .map(snapshot -> {
                    Map<String, String> idx = new HashMap<>();
                    snapshot.getAllWorldTeams().forEach(t -> {
                        if (t.getWorldTeamId() != null && t.getName() != null) {
                            idx.put(t.getWorldTeamId(), t.getName());
                        }
                    });
                    return idx;
                })
                .defaultIfEmpty(Map.of())
                .flatMap(teamIdx ->
                    matchSimulationService.advanceMatch(userId, matchUuid, 90)
                        .flatMap(state -> matchRepository.findById(userId, MatchId.of(matchUuid))
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
                                    return Mono.just(ResponseEntity.ok(mapToDTO(match, teamIdx)));
                                })
                                .defaultIfEmpty(ResponseEntity.notFound().<MatchDTO>build())))
                .onErrorResume(e -> Mono.just(ResponseEntity.badRequest().<MatchDTO>build()));
    }

    @GetMapping("/{matchId}")
    public Mono<ResponseEntity<MatchDTO>> getMatch(@PathVariable String matchId, Authentication authentication) {
        UUID userId = controllerHelper.getUserId(authentication);
        UUID matchUuid;
        try {
            matchUuid = UUID.fromString(matchId);
        } catch (IllegalArgumentException ex) {
            return Mono.just(ResponseEntity.badRequest().build());
        }

        return worldSnapshotService.getSnapshot(userId)
                .map(snapshot -> {
                    Map<String, String> idx = new HashMap<>();
                    snapshot.getAllWorldTeams().forEach(t -> {
                        if (t.getWorldTeamId() != null && t.getName() != null) {
                            idx.put(t.getWorldTeamId(), t.getName());
                        }
                    });
                    return idx;
                })
                .defaultIfEmpty(Map.of())
                .flatMap(teamIdx ->
                    matchRepository.findById(userId, MatchId.of(matchUuid))
                        .map(m -> mapToDTO(m, teamIdx))
                        .map(ResponseEntity::ok)
                        .defaultIfEmpty(ResponseEntity.notFound().build()));
    }

    @GetMapping("/scheduled")
    public Flux<MatchDTO> getScheduledMatches(Authentication authentication) {
        return Flux.empty();
    }

    /**
     * V25D78-C53 Bug #3 fix: GET /api/v1/matches/{matchId}/minute-by-minute
     *
     * <p>The frontend {@code MatchDetailComponent} calls this endpoint to drive
     * its 700ms-step animation of the match timeline. Pre-fix, the endpoint
     * did not exist → 404 from Spring's no-handler path → frontend stays in
     * loading state. Now we look up the V24 detail for the user's active
     * career + this match and return one synthetic final-state entry that
     * the frontend can render (cumulative goals + all events).
     *
     * <p>Why a single-state list: the V24 detail timeline has all events
     * already grouped by minute in {@code V24DetailedMatchData.timeline()}.
     * Emitting one state with the final score + the full event list keeps
     * the frontend's animation logic simple (it shows the final state in
     * one tick) while solving the "Loading..." indefinitely symptom.
     *
     * <p>404 if no career or no detail (which is the common case — V24
     * detail is only persisted when the V24 engine + persistence are both
     * enabled for that career). The frontend error handler treats this as
     * "no data" and shows the failure message.
     */
    @GetMapping("/{matchId}/minute-by-minute")
    public Mono<ResponseEntity<List<MatchMinuteState>>> getMinuteByMinute(
            @PathVariable String matchId, Authentication authentication) {
        UUID userId = controllerHelper.getUserId(authentication);
        if (matchId == null || matchId.isBlank()) {
            return Mono.just(ResponseEntity.badRequest().build());
        }

        return careerSessionService.getCareerFromCache(userId)
                .flatMap(career -> {
                    String careerId = career.getCareerId();
                    if (careerId == null || careerId.isBlank()) {
                        return Mono.<ResponseEntity<List<MatchMinuteState>>>just(ResponseEntity.notFound().build());
                    }
                    V24DetailedMatchData detail =
                            v24DetailedMatchQueryService.findDetail(careerId, matchId).orElse(null);
                    if (detail == null) {
                        return Mono.<ResponseEntity<List<MatchMinuteState>>>just(ResponseEntity.notFound().build());
                    }
                    List<MatchMinuteState> states = buildMinuteByMinuteStates(detail);
                    return Mono.just(ResponseEntity.ok(states));
                })
                .switchIfEmpty(Mono.fromSupplier(() -> ResponseEntity.notFound().build()));
    }

    private List<MatchMinuteState> buildMinuteByMinuteStates(V24DetailedMatchData detail) {
        List<V24MatchEventDto> events = detail.timeline() == null ? List.of() : detail.timeline();
        // Sort events by minute to keep the order stable regardless of persistence order.
        List<V24MatchEventDto> sorted = new ArrayList<>(events);
        sorted.sort((a, b) -> Integer.compare(a.minute(), b.minute()));

        List<MatchMinuteState> states = new ArrayList<>();
        int homeGoals = 0;
        int awayGoals = 0;
        int lastMinute = 0;
        for (V24MatchEventDto ev : sorted) {
            if ("GOAL".equalsIgnoreCase(ev.type())) {
                if (detail.homeTeamId() != null && detail.homeTeamId().equals(ev.teamId())) {
                    homeGoals++;
                } else {
                    awayGoals++;
                }
            }
            // Emit one state per event-minute to drive the frontend animation.
            if (ev.minute() != lastMinute) {
                states.add(toState(ev.minute(), homeGoals, awayGoals, new ArrayList<>(), detail));
                lastMinute = ev.minute();
            }
            // Append this event to the most recent state's events list.
            states.get(states.size() - 1).events().add(toEvent(ev));
        }
        // Final state with the recorded scores (matches the persisted detail's homeGoals/awayGoals).
        states.add(new MatchMinuteState(
                Math.max(lastMinute, 90),
                detail.homeGoals(),
                detail.awayGoals(),
                new ArrayList<>(),
                "FINISHED"
        ));
        return states;
    }

    private MatchMinuteState toState(int minute, int home, int away, List<MatchEventDTO> evs,
                                     V24DetailedMatchData detail) {
        return new MatchMinuteState(minute, home, away, evs, "IN_PROGRESS");
    }

    private MatchEventDTO toEvent(V24MatchEventDto ev) {
        // Frontend enum: 'GOAL' | 'CARD' | 'INJURY' | 'SUBSTITUTION'.
        // Map V24 type strings to one of those (V24 has more granular types
        // but they all collapse into one of the four frontend buckets).
        String type = "GOAL";
        String upper = ev.type() == null ? "" : ev.type().toUpperCase();
        if (upper.contains("GOAL")) {
            type = "GOAL";
        } else if (upper.contains("CARD") || upper.contains("YELLOW") || upper.contains("RED")) {
            type = "CARD";
        } else if (upper.contains("INJURY")) {
            type = "INJURY";
        } else if (upper.contains("SUB")) {
            type = "SUBSTITUTION";
        }
        return new MatchEventDTO(ev.minute(), type, ev.playerName(),
                ev.description() == null ? "" : ev.description());
    }

    private MatchDTO mapToDTO(Match match, Map<String, String> teamNameIndex) {
        String homeName = teamNameIndex.getOrDefault(match.getHomeTeamId().getValue().toString(), "Team");
        String awayName = teamNameIndex.getOrDefault(match.getAwayTeamId().getValue().toString(), "Team");
        return new MatchDTO(
                match.getId().getValue().toString(),
                match.getHomeTeamId().getValue().toString(),
                match.getAwayTeamId().getValue().toString(),
                homeName,
                awayName,
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
            String homeTeamName,
            String awayTeamName,
            Instant scheduledAt,
            String status,
            MatchResult result,
            Instant createdAt,
            Instant simulatedAt,
            Integer round
    ) {}

    /** V25D78-C53 Bug #3: response shape for {@code GET /matches/{matchId}/minute-by-minute}. */
    public record MatchMinuteState(
            int minute,
            int homeGoals,
            int awayGoals,
            List<MatchEventDTO> events,
            String status
    ) {}

    public record MatchEventDTO(
            int minute,
            String type,
            String playerName,
            String description
    ) {}
}