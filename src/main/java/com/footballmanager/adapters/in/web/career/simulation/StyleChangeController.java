package com.footballmanager.adapters.in.web.career.simulation;

import com.footballmanager.adapters.in.web.career.simulation.dto.StyleChangeRequestDTO;
import com.footballmanager.adapters.in.web.career.simulation.dto.StyleChangeResultDTO;
import com.footballmanager.adapters.in.web.common.ControllerHelper;
import com.footballmanager.adapters.in.web.common.PublicErrorMessageResolver;
import com.footballmanager.application.service.match.TacticalChangeService;
import com.footballmanager.application.service.match.TacticalStyleChangeResult;
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
 * during a live match.
 *
 * <p>Endpoint: {@code POST /api/v1/match-engine/matches/{matchId}/style}
 * <p>Body: {@link StyleChangeRequestDTO}
 * <p>Response: 200 OK + {@link StyleChangeResultDTO} on success; 4xx with
 * a {@code success=false} body on validation failure.
 *
 * <p>Error mapping:
 * <ul>
 *   <li>Request-shape errors (invalid UUID, null body, null newStyle) â†’
 *       400 BAD_REQUEST with success=false.</li>
 *   <li>Use case validation failures (no session, match finished) â†’
 *       409 CONFLICT with success=false (consistent with the F1 substitution
 *       controller's pattern).</li>
 *   <li>Unexpected errors (NPE, DB, etc.) â†’ 500 via the generic catch-all.</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping(value = "/api/v1/match-engine", produces = "application/json;charset=UTF-8")
@RequiredArgsConstructor
public class StyleChangeController {

    private final TacticalChangeService tacticalChangeService;
    private final ControllerHelper controllerHelper;
    private final PublicErrorMessageResolver errorMessageResolver;

    @PostMapping("/matches/{matchId}/style")
    public Mono<ResponseEntity<StyleChangeResultDTO>> changeStyle(
            @PathVariable String matchId,
            @RequestBody StyleChangeRequestDTO request,
            Authentication authentication) {

        UUID userId = controllerHelper.getUserId(authentication);
        UUID matchUuid;
        try {
            matchUuid = UUID.fromString(matchId);
        } catch (IllegalArgumentException ex) {
            return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(StyleChangeResultDTO.error("matchId must be a valid UUID: " + matchId)));
        }

        if (request == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(StyleChangeResultDTO.error("request body must not be null")));
        }
        if (request.newStyle() == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(StyleChangeResultDTO.error("newStyle must not be null")));
        }

        log.info("Style change request received: matchId={} userId={} newStyle={}",
            matchUuid, userId, request.newStyle());

        return tacticalChangeService.changeStyle(userId, matchUuid, request.newStyle())
            .map(StyleChangeController::toDto)
            .map(ResponseEntity::ok)
            .onErrorResume(IllegalArgumentException.class, e ->
                Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(StyleChangeResultDTO.error(errorMessageResolver.clientMessage(e, "Style change rejected")))))
            .onErrorResume(IllegalStateException.class, e ->
                Mono.just(ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(StyleChangeResultDTO.error(errorMessageResolver.clientMessage(e, "Style change rejected")))))
            .onErrorResume(e -> {
                log.error("Unexpected error during style change for matchId={}",
                    matchUuid, e);
                return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(StyleChangeResultDTO.error("Style change failed")));
            });
    }

    private static StyleChangeResultDTO toDto(TacticalStyleChangeResult result) {
        return new StyleChangeResultDTO(
            result.success(),
            result.minuteApplied(),
            result.currentStyle(),
            result.error());
    }
}
