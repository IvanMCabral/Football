package com.footballmanager.adapters.in.web.common;

import com.footballmanager.application.exception.MinuteInPastException;
import com.footballmanager.application.exception.NotEnoughPlayersException;
import com.footballmanager.infrastructure.security.RequestCorrelationWebFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * Manejador global de excepciones.
 * Ubicado en common/ por ser transversal a todos los controllers.
 *
 * {@code LINEUP_MINIMUM_PLAYERS_NOT_MET} code, the available count and
 * the minimum required count, so the UI can render a meaningful error
 * banner.
 *
 * thrown by LineupHelper.validatePlayerFitness() and manual-select validation,
 * mapping them to 422 Unprocessable Entity instead of 400/500.
 *
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
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private static final String GENERIC_UNEXPECTED_MESSAGE = "Ocurrió un error inesperado.";
    private static final String INVALID_REQUEST_MESSAGE = "La solicitud no es válida.";
    private static final String INVALID_STATE_MESSAGE = "La operación no es válida para el estado actual.";
    private static final String NOT_ENOUGH_PLAYERS_MESSAGE =
        "No hay suficientes jugadores disponibles para formar la alineación.";
    private static final String MINUTE_IN_PAST_MESSAGE =
        "No se puede modificar una acción que ya pertenece al pasado del partido.";
    private static final String UNAUTHORIZED_MESSAGE = "No autenticado.";
    private static final String FORBIDDEN_MESSAGE = "No tenés permiso para operar sobre ese recurso.";

    private final PublicErrorMessageResolver messageResolver;

    @ExceptionHandler(NotEnoughPlayersException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleNotEnoughPlayers(
            NotEnoughPlayersException ex,
            ServerWebExchange exchange) {
        Map<String, Object> body = new HashMap<>();
        body.put("code", "LINEUP_MINIMUM_PLAYERS_NOT_MET");
        body.put("message", messageResolver.clientMessage(ex, NOT_ENOUGH_PLAYERS_MESSAGE));
        body.put("status", HttpStatus.UNPROCESSABLE_ENTITY.value());
        body.put("requestId", requestId(exchange));
        body.put("minimumRequired",
            com.footballmanager.domain.service.LineupRules.MIN_AVAILABLE_PLAYERS);
        return Mono.just(
            ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
        );
    }

    /**
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
    public Mono<ResponseEntity<Map<String, Object>>> handleMinuteInPast(
            MinuteInPastException ex,
            ServerWebExchange exchange) {
        Map<String, Object> body = new HashMap<>();
        body.put("code", "MINUTE_IN_PAST");
        body.put("message", messageResolver.clientMessage(ex, MINUTE_IN_PAST_MESSAGE));
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("requestId", requestId(exchange));
        return Mono.just(
            ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleIllegalArgument(
            IllegalArgumentException ex,
            ServerWebExchange exchange) {
        return validationError(ex, exchange);
    }

    @ExceptionHandler(ServerWebInputException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleServerWebInput(
            ServerWebInputException ex,
            ServerWebExchange exchange) {
        return validationError(ex, exchange);
    }

    private Mono<ResponseEntity<Map<String, Object>>> validationError(
            Exception ex,
            ServerWebExchange exchange) {
        Map<String, Object> body = new HashMap<>();
        body.put("code", "LINEUP_VALIDATION_ERROR");
        body.put("message", messageResolver.clientMessage(ex, INVALID_REQUEST_MESSAGE));
        body.put("status", HttpStatus.UNPROCESSABLE_ENTITY.value());
        body.put("requestId", requestId(exchange));
        return Mono.just(
            ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
        );
    }

    @ExceptionHandler(IllegalStateException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleIllegalState(
            IllegalStateException ex,
            ServerWebExchange exchange) {
        Map<String, Object> body = new HashMap<>();
        body.put("code", "LINEUP_STATE_ERROR");
        body.put("message", messageResolver.clientMessage(ex, INVALID_STATE_MESSAGE));
        body.put("status", HttpStatus.UNPROCESSABLE_ENTITY.value());
        body.put("requestId", requestId(exchange));
        return Mono.just(
            ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
        );
    }

    /**
     * ControllerHelper.getUserId() now land here as HTTP 401 instead of
     * being caught by the IllegalArgumentException handler and incorrectly
     * mapped to 422 LINEUP_VALIDATION_ERROR. The front can now show a
     * dedicated 'please log in again' banner on UNAUTHORIZED codes.
     */
    @ExceptionHandler(UnauthorizedException.class)
    public Mono<ResponseEntity<ErrorResponseBody>> handleUnauthorized(
            UnauthorizedException ex,
            ServerWebExchange exchange) {
        return Mono.just(
            ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                // so this handler produces the same header set as the
                .header("WWW-Authenticate", "Bearer")
                .contentType(MediaType.APPLICATION_JSON)
                .body(ErrorResponseBody.unauthorized(
                    messageResolver.clientMessage(ex, UNAUTHORIZED_MESSAGE),
                    requestId(exchange)))
        );
    }

    /**
     * {@link ImpersonationForbiddenException} thrown by
     * {@link ControllerHelper#requireSelfUserId} when the JWT userId does
     * NOT match the param/body userId.
     *
     * <p>Wire format matches the inline C47/C48 contract so existing
     * consumers (and the C47/C48 E2E tests that assert on
     * {@code $.code == "IMPERSONATION_FORBIDDEN"}) keep working:
     * <pre>{@code
     * {
     *   "code":    "IMPERSONATION_FORBIDDEN",
     *   "message": "...",
     *   "status":  403
     * }
     * }</pre>
     *
     * <p>Spring's @ExceptionHandler resolution uses the most specific
     * match — {@code ImpersonationForbiddenException} extends
     * {@link RuntimeException}, so this handler runs before any generic
     * fallback.
     */
    @ExceptionHandler(ImpersonationForbiddenException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleImpersonationForbidden(
            ImpersonationForbiddenException ex,
            ServerWebExchange exchange) {
        Map<String, Object> body = new HashMap<>();
        body.put("code", "IMPERSONATION_FORBIDDEN");
        body.put("message", messageResolver.clientMessage(ex, FORBIDDEN_MESSAGE));
        body.put("status", HttpStatus.FORBIDDEN.value());
        body.put("requestId", requestId(exchange));
        return Mono.just(
            ResponseEntity.status(HttpStatus.FORBIDDEN)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
        );
    }

    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<ErrorResponseBody>> handleUnexpected(
            Exception ex,
            ServerWebExchange exchange) {
        ErrorResponseBody body = new ErrorResponseBody(
            "INTERNAL_ERROR",
            GENERIC_UNEXPECTED_MESSAGE,
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            requestId(exchange));
        return Mono.just(
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
        );
    }

    private static String requestId(ServerWebExchange exchange) {
        String responseValue = exchange.getResponse().getHeaders()
            .getFirst(RequestCorrelationWebFilter.REQUEST_ID_HEADER);
        if (responseValue != null && !responseValue.isBlank()) {
            return responseValue;
        }
        String requestValue = exchange.getRequest().getHeaders()
            .getFirst(RequestCorrelationWebFilter.REQUEST_ID_HEADER);
        return requestValue == null || requestValue.isBlank() ? "unavailable" : requestValue;
    }
}
