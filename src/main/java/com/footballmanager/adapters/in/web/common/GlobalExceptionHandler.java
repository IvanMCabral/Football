package com.footballmanager.adapters.in.web.common;

import com.footballmanager.application.exception.MinuteInPastException;
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
 *
 * <p>LIVE-MATCH-F2-LIVE F2.5: added a dedicated
 * {@link MinuteInPastException} handler that returns HTTP 400. The
 * handler is registered for the most specific class
 * ({@code MinuteInPastException extends IllegalArgumentException}) and
 * takes precedence over the generic {@code IllegalArgumentException}
 * handler below per Spring's exception resolution rules. This honors
 * the F2.5 D-protocolo rule: a request for a past minute is a
 * PROTOCOL failure (manager is trying to change the past), not a
 * business validation failure, and the API must return 400 rather than
 * 200 + {@code success=false} (FLAG 1 UX) or 422 (lineup validation).
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

    /**
     * LIVE-MATCH-F2-LIVE F2.5: protocol-level handler for past-minute
     * substitution requests. Returns HTTP 400 BAD_REQUEST with code
     * {@code MINUTE_IN_PAST} so the frontend can distinguish a
     * protocol failure (manager tried to change the past) from a
     * business validation failure (422 LINEUP_VALIDATION_ERROR) and
     * from the FLAG 1 UX 200 + {@code success=false} body.
     *
     * <p>Spring's {@code @ExceptionHandler} resolution uses the most
     * specific match, so this handler runs BEFORE the generic
     * {@link #handleIllegalArgument(IllegalArgumentException)} below
     * for {@code MinuteInPastException} instances (since
     * {@code MinuteInPastException extends IllegalArgumentException}).
     */
    @ExceptionHandler(MinuteInPastException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleMinuteInPast(MinuteInPastException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("code", "MINUTE_IN_PAST");
        body.put("message", ex.getMessage());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        return Mono.just(
            ResponseEntity.status(HttpStatus.BAD_REQUEST)
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

    /**
     * V24D12-3: Auth failures (no JWT, no name in Authentication) thrown by
     * ControllerHelper.getUserId() now land here as HTTP 401 instead of
     * being caught by the IllegalArgumentException handler and incorrectly
     * mapped to 422 LINEUP_VALIDATION_ERROR. The front can now show a
     * dedicated 'please log in again' banner on UNAUTHORIZED codes.
     */
    @ExceptionHandler(UnauthorizedException.class)
    public Mono<ResponseEntity<ErrorResponseBody>> handleUnauthorized(UnauthorizedException ex) {
        return Mono.just(
            ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                // V24D12.1.2: add WWW-Authenticate: Bearer header (RFC 7235)
                // so this handler produces the same header set as the
                // SecurityConfig entry point (V24D12.1.1).
                .header("WWW-Authenticate", "Bearer")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(ErrorResponseBody.unauthorized(ex.getMessage()))
        );
    }
}
