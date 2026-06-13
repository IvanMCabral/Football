package com.footballmanager.adapters.in.web.common;

import com.footballmanager.application.exception.NotEnoughPlayersException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * Manejador global de excepciones.
 * Ubicado en common/ por ser transversal a todos los controllers.
 *
 * <p>V24D6U2: Extends the 422 response to include the
 * {@code LINEUP_MINIMUM_PLAYERS_NOT_MET} code, the available count and
 * the minimum required count, so the UI can render a meaningful error
 * banner.
 *
 * <p>V24D6T2: Added handlers for IllegalArgumentException and IllegalStateException
 * thrown by LineupHelper.validatePlayerFitness() and manual-select validation,
 * mapping them to 422 Unprocessable Entity instead of 400/500.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotEnoughPlayersException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleNotEnoughPlayers(NotEnoughPlayersException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("code", "LINEUP_MINIMUM_PLAYERS_NOT_MET");
        body.put("message", ex.getMessage());
        body.put("status", HttpStatus.UNPROCESSABLE_ENTITY.value());
        body.put("minimumRequired",
            com.footballmanager.application.service.lineup.LineupRules.MIN_AVAILABLE_PLAYERS);
        return Mono.just(
            ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(body)
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleIllegalArgument(IllegalArgumentException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("code", "LINEUP_VALIDATION_ERROR");
        body.put("message", ex.getMessage());
        body.put("status", HttpStatus.UNPROCESSABLE_ENTITY.value());
        return Mono.just(
            ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(body)
        );
    }

    @ExceptionHandler(IllegalStateException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleIllegalState(IllegalStateException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("code", "LINEUP_STATE_ERROR");
        body.put("message", ex.getMessage());
        body.put("status", HttpStatus.UNPROCESSABLE_ENTITY.value());
        return Mono.just(
            ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(body)
        );
    }
}
