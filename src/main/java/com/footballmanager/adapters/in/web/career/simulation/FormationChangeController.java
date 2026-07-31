package com.footballmanager.adapters.in.web.career.simulation;

import com.footballmanager.adapters.in.web.career.simulation.dto.FormationChangeRequestDTO;
import com.footballmanager.adapters.in.web.career.simulation.dto.FormationChangeResultDTO;
import com.footballmanager.adapters.in.web.career.simulation.dto.FormationSlotDTO;
import com.footballmanager.adapters.in.web.common.ControllerHelper;
import com.footballmanager.adapters.in.web.common.PublicErrorMessageResolver;
import com.footballmanager.application.service.match.TacticalChangeService;
import com.footballmanager.application.service.match.TacticalFormationChangeResult;
import com.footballmanager.application.service.match.TacticalFormationSlot;
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

import java.util.List;
import java.util.UUID;

/**
 * during a live match.
 *
 * <p>Endpoint: {@code POST /api/v1/match-engine/matches/{matchId}/formation}
 * <p>Body: {@link FormationChangeRequestDTO}
 * <p>Response: 200 OK + {@link FormationChangeResultDTO} on success; 4xx with
 * a {@code success=false} body on validation failure.
 *
 * <p>Error mapping mirrors the {@link StyleChangeController}:
 * <ul>
 *   <li>Request-shape errors (invalid UUID, null body, empty/null slots) â†’
 *       400 BAD_REQUEST.</li>
 *   <li>Use case validation failures (no session, match finished, invalid
 *       formation, players not in roster) â†’ 400 BAD_REQUEST for formation
 *       shape issues, 409 CONFLICT for session/finished issues.</li>
 *   <li>Unexpected errors â†’ 500 via the generic catch-all.</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping(value = "/api/v1/match-engine", produces = "application/json;charset=UTF-8")
@RequiredArgsConstructor
public class FormationChangeController {

    private final TacticalChangeService tacticalChangeService;
    private final ControllerHelper controllerHelper;
    private final PublicErrorMessageResolver errorMessageResolver;

    @PostMapping("/matches/{matchId}/formation")
    public Mono<ResponseEntity<FormationChangeResultDTO>> changeFormation(
            @PathVariable String matchId,
            @RequestBody FormationChangeRequestDTO request,
            Authentication authentication) {

        UUID userId = controllerHelper.getUserId(authentication);
        UUID matchUuid;
        try {
            matchUuid = UUID.fromString(matchId);
        } catch (IllegalArgumentException ex) {
            return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(FormationChangeResultDTO.error("matchId must be a valid UUID: " + matchId)));
        }

        if (request == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(FormationChangeResultDTO.error("request body must not be null")));
        }
        if (request.players() == null || request.players().isEmpty()) {
            return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(FormationChangeResultDTO.error("players must not be empty")));
        }

        log.info("Formation change request received: matchId={} userId={} slots={} code={}",
            matchUuid, userId, request.players().size(), request.formationCode());

        return tacticalChangeService.changeFormation(
                userId,
                matchUuid,
                request.players().stream().map(FormationChangeController::toDomain).toList(),
                request.formationCode())
            .map(FormationChangeController::toDto)
            .map(ResponseEntity::ok)
            .onErrorResume(IllegalArgumentException.class, e ->
                Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(FormationChangeResultDTO.error(errorMessageResolver.clientMessage(e, "Formation change rejected")))))
            .onErrorResume(IllegalStateException.class, e ->
                Mono.just(ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(FormationChangeResultDTO.error(errorMessageResolver.clientMessage(e, "Formation change rejected")))))
            .onErrorResume(e -> {
                log.error("Unexpected error during formation change for matchId={}",
                    matchUuid, e);
                return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(FormationChangeResultDTO.error("Formation change failed")));
            });
    }

    private static TacticalFormationSlot toDomain(FormationSlotDTO slot) {
        return new TacticalFormationSlot(
            slot.playerId(),
            slot.position(),
            slot.slotIndex(),
            slot.customXPercent(),
            slot.customYPercent());
    }

    private static FormationChangeResultDTO toDto(TacticalFormationChangeResult result) {
        List<FormationSlotDTO> currentFormation = result.currentFormation() == null
            ? null
            : result.currentFormation().stream()
                .map(FormationChangeController::toDto)
                .toList();
        return new FormationChangeResultDTO(
            result.success(),
            result.minuteApplied(),
            currentFormation,
            result.error());
    }

    private static FormationSlotDTO toDto(TacticalFormationSlot slot) {
        return new FormationSlotDTO(
            slot.playerId(),
            slot.position(),
            slot.slotIndex(),
            slot.customXPercent(),
            slot.customYPercent());
    }
}
