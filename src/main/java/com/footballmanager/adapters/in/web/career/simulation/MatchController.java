package com.footballmanager.adapters.in.web.career.simulation;

import com.footballmanager.application.service.match.MatchManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Controller for match control operations (pause, resume, stop).
 */
@RestController
@RequestMapping("/api/v1/match-engine/matches")
@RequiredArgsConstructor
public class MatchController {

    private final MatchManagementService matchManagementService;

    /**
     * POST /api/v1/match-engine/matches/{matchId}/pause
     * Pauses a match.
     */
    @PostMapping("/{matchId}/pause")
    public ResponseEntity<Object> pauseMatch(@PathVariable String matchId) {
        UUID matchIdUuid = UUID.fromString(matchId);

        try {
            matchManagementService.pauseMatch(null, matchIdUuid).block();
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    /**
     * POST /api/v1/match-engine/matches/{matchId}/resume
     * Resumes a paused match.
     */
    @PostMapping("/{matchId}/resume")
    public ResponseEntity<Object> resumeMatch(@PathVariable String matchId) {
        UUID matchIdUuid = UUID.fromString(matchId);

        try {
            matchManagementService.resumeMatch(null, matchIdUuid).block();
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    /**
     * POST /api/v1/match-engine/matches/{matchId}/stop
     * Stops a match.
     */
    @PostMapping("/{matchId}/stop")
    public ResponseEntity<Object> stopMatch(@PathVariable String matchId) {
        UUID matchIdUuid = UUID.fromString(matchId);

        try {
            matchManagementService.stopMatch(null, matchIdUuid).block();
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }
}
