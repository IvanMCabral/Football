package com.footballmanager.adapters.in.web.career.simulation;

import com.footballmanager.application.service.match.MatchManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Controller for match control operations (pause, resume, stop).
 */
@RestController
@RequestMapping(value = "/api/v1/match-engine/matches", produces = "application/json;charset=UTF-8")
@RequiredArgsConstructor
public class MatchController {

    private final MatchManagementService matchManagementService;

    /**
     * POST /api/v1/match-engine/matches/{matchId}/pause
     * Pauses a match.
     */
    @PostMapping("/{matchId}/pause")
    public Mono<ResponseEntity<Object>> pauseMatch(@PathVariable String matchId) {
        UUID matchIdUuid = UUID.fromString(matchId);

        return matchManagementService.pauseMatch(null, matchIdUuid)
            .thenReturn(ResponseEntity.ok().build())
            .onErrorResume(e -> Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage())));
    }

    /**
     * POST /api/v1/match-engine/matches/{matchId}/resume
     * Resumes a paused match.
     */
    @PostMapping("/{matchId}/resume")
    public Mono<ResponseEntity<Object>> resumeMatch(@PathVariable String matchId) {
        UUID matchIdUuid = UUID.fromString(matchId);

        return matchManagementService.resumeMatch(null, matchIdUuid)
            .thenReturn(ResponseEntity.ok().build())
            .onErrorResume(e -> Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage())));
    }

    /**
     * POST /api/v1/match-engine/matches/{matchId}/stop
     * Stops a match.
     */
    @PostMapping("/{matchId}/stop")
    public Mono<ResponseEntity<Object>> stopMatch(@PathVariable String matchId) {
        UUID matchIdUuid = UUID.fromString(matchId);

        return matchManagementService.stopMatch(null, matchIdUuid)
            .thenReturn(ResponseEntity.ok().build())
            .onErrorResume(e -> Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage())));
    }
}
