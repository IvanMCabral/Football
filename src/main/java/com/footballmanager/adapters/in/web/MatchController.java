package com.footballmanager.adapters.in.web;

import com.footballmanager.domain.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/matches")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
public class MatchController {

    @PostMapping
    public Mono<ResponseEntity<MatchDTO>> createMatch(@RequestBody CreateMatchRequest request) {
        MatchId matchId = MatchId.generate();
        TeamId homeTeamId = TeamId.of(UUID.fromString(request.homeTeamId()));
        TeamId awayTeamId = TeamId.of(UUID.fromString(request.awayTeamId()));

        Match match = Match.create(matchId, homeTeamId, awayTeamId, request.scheduledAt());
        return Mono.just(ResponseEntity.status(HttpStatus.CREATED).body(mapToDTO(match)));
    }

    @PostMapping("/{matchId}/simulate")
    public Mono<ResponseEntity<MatchDTO>> simulateMatch(@PathVariable String matchId) {
        // This would integrate with match service and engine
        return Mono.just(ResponseEntity.ok(new MatchDTO(
                matchId,
                "team-1",
                "team-2",
                Instant.now(),
                "SIMULATED",
                null,
                Instant.now(),
                null
        )));
    }

    @GetMapping("/{matchId}")
    public Mono<ResponseEntity<MatchDTO>> getMatch(@PathVariable String matchId) {
        return Mono.just(ResponseEntity.ok(new MatchDTO(
                matchId,
                "team-1",
                "team-2",
                Instant.now(),
                "SCHEDULED",
                null,
                Instant.now(),
                null
        )));
    }

    @GetMapping("/scheduled")
    public Flux<MatchDTO> getScheduledMatches() {
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
                match.getSimulatedAt()
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
            Instant simulatedAt
    ) {}
}
