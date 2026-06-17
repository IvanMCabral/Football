package com.footballmanager.adapters.in.web.career.simulation;

import com.footballmanager.adapters.in.web.career.simulation.dto.SubstitutionRequestDTO;
import com.footballmanager.adapters.in.web.career.simulation.dto.SubstitutionResultDTO;
import com.footballmanager.adapters.in.web.common.ControllerHelper;
import com.footballmanager.domain.port.in.match.SubstitutionCommandUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * LIVE-MATCH-F1-POC: controller for manual substitutions during a live match.
 *
 * <p>Phase 1 POC (D1=B): manual substitutions are UI-only. The substitution
 * is recorded in the live session's event stream and the player states
 * are mutated for downstream consumers, but {@code homeGoals}/{@code awayGoals}
 * are NOT recalculated. See {@link SubstitutionCommandUseCaseImpl}.
 *
 * <p>Endpoint: {@code POST /api/v1/match-engine/matches/{matchId}/substitutions}
 * <p>Body: {@link SubstitutionRequestDTO}
 * <p>Response: 200 OK + {@link SubstitutionResultDTO}
 *
 * <p>Error mapping:
 * <ul>
 *   <li>{@link IllegalArgumentException} (player not found, etc.) → 400 BAD_REQUEST</li>
 *   <li>{@link IllegalStateException} (max subs reached, player already subbed, match
 *       finished, etc.) → 409 CONFLICT</li>
 *   <li>other errors → 500 via the global error handler</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/match-engine")
@RequiredArgsConstructor
public class SubstitutionController {

    private final SubstitutionCommandUseCase substitutionCommandUseCase;
    private final ControllerHelper controllerHelper;

    @PostMapping("/matches/{matchId}/substitutions")
    public Mono<ResponseEntity<SubstitutionResultDTO>> substitute(
            @PathVariable String matchId,
            @RequestBody SubstitutionRequestDTO request,
            Authentication authentication) {

        UUID userId = controllerHelper.getUserId(authentication);
        UUID matchUuid;
        try {
            matchUuid = UUID.fromString(matchId);
        } catch (IllegalArgumentException ex) {
            return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(SubstitutionResultDTO.error("matchId must be a valid UUID: " + matchId)));
        }

        if (request == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(SubstitutionResultDTO.error("request body must not be null")));
        }
        if (request.playerOffId() == null || request.playerOffId().isBlank()) {
            return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(SubstitutionResultDTO.error("playerOffId must not be blank")));
        }
        if (request.playerOnId() == null || request.playerOnId().isBlank()) {
            return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(SubstitutionResultDTO.error("playerOnId must not be blank")));
        }

        log.info("[LIVE-MATCH-F1] Substitution request received: matchId={} userId={} off={} on={}",
            matchUuid, userId, request.playerOffId(), request.playerOnId());

        return substitutionCommandUseCase.executeSubstitution(
                userId, matchUuid,
                /* teamId= */ null, // inferred by the use case
                request.playerOffId(),
                request.playerOnId(),
                request.minute())
            .then(Mono.fromSupplier(() -> {
                // We don't have the engine's "substitutionsRemaining" here without an extra
                // round-trip; the result DTO carries the engine's counter.
                // For Phase 1 POC, the frontend can re-fetch from /match-engine/matches/{id}
                // to get the latest state. Return success=200 with a minimal success DTO.
                return ResponseEntity.ok(SubstitutionResultDTO.ok(0, 0));
            }))
            .onErrorResume(IllegalArgumentException.class, e ->
                Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(SubstitutionResultDTO.error(e.getMessage()))))
            .onErrorResume(IllegalStateException.class, e ->
                Mono.just(ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(SubstitutionResultDTO.error(e.getMessage()))));
    }
}
