package com.footballmanager.adapters.in.web.career.simulation;

import com.footballmanager.adapters.in.web.career.simulation.dto.SubstitutionRequestDTO;
import com.footballmanager.adapters.in.web.career.simulation.dto.SubstitutionResultDTO;
import com.footballmanager.adapters.in.web.common.ControllerHelper;
import com.footballmanager.domain.port.in.match.SubstitutionCommandUseCase;
import com.footballmanager.domain.port.in.match.SubstitutionResult;
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
 * LIVE-MATCH-F2-LIVE F2: controller for manual substitutions during a live match.
 *
 * <p>F2 wire: manual substitutions now affect the match result. The use case
 * drives the substitution through {@code V24LiveSession.mutateContext(...)} +
 * {@code replayFromMinute(...)} (the F1 replay infrastructure), so
 * {@code homeGoals}/{@code awayGoals} can change from the baseline after a
 * substitution is applied. The D1=B invariant was removed in F2.
 *
 * <p>Endpoint: {@code POST /api/v1/match-engine/matches/{matchId}/substitutions}
 * <p>Body: {@link SubstitutionRequestDTO}
 * <p>Response: 200 OK + {@link SubstitutionResultDTO} (with {@code success=true}
 * or {@code success=false} depending on validation outcome)
 *
 * <p>FLAG 1 UX fix: the controller uses {@code .map()} to forward the
 * use case's real {@link SubstitutionResult} (with
 * {@code substitutionsRemaining} and {@code minuteApplied}) so the frontend
 * can decrement its local counter from this value and show the correct
 * minute in the snackbar / dialog UI.
 *
 * <p>Error mapping (FLAG 1 UX):
 * <ul>
 *   <li>Request-shape errors (invalid UUID, blank playerOffId/playerOnId,
 *       null body) → 400 BAD_REQUEST with a {@code SubstitutionResultDTO}
 *       carrying {@code success=false} (controller-level validation,
 *       short-circuited before the use case runs).</li>
 *   <li>Use case validation failures (no session, player not found, max subs
 *       reached, off not in starting, on not on bench, etc.) → 200 OK with
 *       {@code success=false} and a descriptive {@code error} (FLAG 1 fix;
 *       was previously 409 CONFLICT).</li>
 *   <li>Unexpected errors (NPE, DB, etc.) → 500 via the generic catch-all.</li>
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

        log.info("[LIVE-MATCH-F2-F2] Substitution request received: matchId={} userId={} off={} on={}",
            matchUuid, userId, request.playerOffId(), request.playerOnId());

        // FLAG 1 UX fix: use case returns Mono<SubstitutionResult>; we forward the
        // real substitutionsRemaining + minuteApplied via .map() instead of the
        // hardcoded ok(0, 0) placeholder. Use case validation failures are caught
        // internally and returned as success=false; only infrastructure errors
        // (NPE, DB, etc.) propagate as Mono.error and are mapped to 500 below.
        return substitutionCommandUseCase.executeSubstitution(
                userId, matchUuid,
                /* teamId= */ null, // inferred by the use case
                request.playerOffId(),
                request.playerOnId(),
                request.minute())
            .map(result -> ResponseEntity.ok(new SubstitutionResultDTO(
                result.success(),
                result.minuteApplied(),
                result.substitutionsRemaining(),
                result.error())))
            .onErrorResume(e -> {
                // V24D13-2 (F4.4): protocol-level exceptions (IllegalStateException
                // for "no active match session" / missing V24LiveSession / missing
                // context, IllegalArgumentException — and its subclass
                // MinuteInPastException — for "minute in past") must propagate to
                // GlobalExceptionHandler so the frontend gets the right 4xx
                // semantic code (422 LINEUP_STATE_ERROR, 400 MINUTE_IN_PAST).
                // Only genuinely unexpected errors (NPE, DB, etc.) are mapped to
                // 500 here. The previous implementation swallowed all errors and
                // returned 500, hiding the F2.5 protocol semantics from the front.
                if (e instanceof IllegalStateException
                        || e instanceof IllegalArgumentException) {
                    return Mono.error(e);
                }
                log.error("[LIVE-MATCH-F2-F2] Unexpected error during substitution for matchId={}",
                    matchUuid, e);
                return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(SubstitutionResultDTO.error("Internal error: " + e.getMessage())));
            });
    }
}
