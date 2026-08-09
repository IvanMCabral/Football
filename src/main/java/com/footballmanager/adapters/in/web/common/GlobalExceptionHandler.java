package com.footballmanager.adapters.in.web.common;

import com.footballmanager.application.exception.MinuteInPastException;
import com.footballmanager.application.exception.NotEnoughPlayersException;
import com.footballmanager.application.exception.AuthConflictException;
import com.footballmanager.application.exception.AuthCredentialsException;
import com.footballmanager.application.exception.AuthValidationException;
import com.footballmanager.domain.ports.out.career.CareerDataCleanupException;
import com.footballmanager.domain.ports.out.career.CareerDataCleanupResult;
import com.footballmanager.domain.ports.out.career.CareerIndexLimitException;
import com.footballmanager.infrastructure.security.RequestCorrelationWebFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String GENERIC_UNEXPECTED_MESSAGE = "Ocurrió un error inesperado.";
    private static final String INVALID_REQUEST_MESSAGE = "La solicitud no es válida.";
    private static final String INVALID_STATE_MESSAGE = "La operación no es válida para el estado actual.";
    private static final String NOT_ENOUGH_PLAYERS_MESSAGE =
        "No hay suficientes jugadores disponibles para formar la alineación.";
    private static final String MINUTE_IN_PAST_MESSAGE =
        "No se puede modificar una acción que ya pertenece al pasado del partido.";
    private static final String UNAUTHORIZED_MESSAGE = "No autenticado.";
    private static final String FORBIDDEN_MESSAGE = "No tenés permiso para operar sobre ese recurso.";
    private static final String NOT_FOUND_MESSAGE = "El recurso solicitado no existe.";
    private static final String CONFLICT_MESSAGE = "La operación no puede completarse por un conflicto de estado.";

    private final PublicErrorMessageResolver messageResolver;

    @ExceptionHandler(AuthConflictException.class)
    public Mono<ResponseEntity<ErrorResponseBody>> handleAuthConflict(
            AuthConflictException ex,
            ServerWebExchange exchange) {
        return Mono.just(ResponseEntity.status(HttpStatus.CONFLICT)
            .contentType(MediaType.APPLICATION_JSON)
            .body(new ErrorResponseBody(
                "AUTH_EMAIL_EXISTS",
                messageResolver.clientMessage(ex, "El email ya esta registrado."),
                HttpStatus.CONFLICT.value(),
                requestId(exchange))));
    }

    @ExceptionHandler(AuthCredentialsException.class)
    public Mono<ResponseEntity<ErrorResponseBody>> handleAuthCredentials(
            AuthCredentialsException ex,
            ServerWebExchange exchange) {
        return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .contentType(MediaType.APPLICATION_JSON)
            .body(new ErrorResponseBody(
                "AUTH_INVALID_CREDENTIALS",
                messageResolver.clientMessage(ex, "Las credenciales no son validas."),
                HttpStatus.BAD_REQUEST.value(),
                requestId(exchange))));
    }

    @ExceptionHandler(AuthValidationException.class)
    public Mono<ResponseEntity<ErrorResponseBody>> handleAuthValidation(
            AuthValidationException ex,
            ServerWebExchange exchange) {
        return Mono.just(ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
            .contentType(MediaType.APPLICATION_JSON)
            .body(new ErrorResponseBody(
                "AUTH_VALIDATION_ERROR",
                messageResolver.clientMessage(ex, "La solicitud de autenticacion no es valida."),
                HttpStatus.UNPROCESSABLE_ENTITY.value(),
                requestId(exchange))));
    }

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
        log.warn("Public request validation failure: path={}, exception={}",
                exchange.getRequest().getPath().pathWithinApplication().value(),
                ex.getClass().getName());
        return validationError(ex, exchange);
    }

    @ExceptionHandler(CareerDataCleanupException.class)
    public Mono<ResponseEntity<ErrorResponseBody>> handleCareerCleanup(
            CareerDataCleanupException ex,
            ServerWebExchange exchange) {
        CareerDataCleanupResult.Status status = ex.result().status();
        HttpStatus httpStatus;
        String code;
        String message;
        if (status == CareerDataCleanupResult.Status.REJECTED_OWNERSHIP) {
            httpStatus = HttpStatus.UNPROCESSABLE_ENTITY;
            code = "CAREER_CLEANUP_OWNERSHIP_REJECTED";
            message = "La carrera no puede eliminarse porque su ownership no pudo validarse.";
        } else if (status == CareerDataCleanupResult.Status.PARTIAL_RETRYABLE) {
            httpStatus = HttpStatus.SERVICE_UNAVAILABLE;
            code = "CAREER_CLEANUP_RETRYABLE";
            message = "La limpieza no terminó. Podés reintentar más tarde.";
        } else if ("CLEANUP_TIMEOUT".equals(ex.result().failureReason())) {
            httpStatus = HttpStatus.SERVICE_UNAVAILABLE;
            code = "CAREER_CLEANUP_TIMEOUT";
            message = "La limpieza excediÃ³ el tiempo permitido. PodÃ©s reintentar mÃ¡s tarde.";
        } else {
            httpStatus = HttpStatus.SERVICE_UNAVAILABLE;
            code = "CAREER_CLEANUP_FAILED";
            message = "La carrera no pudo eliminarse por un problema temporal.";
        }
        return Mono.just(ResponseEntity.status(httpStatus)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorResponseBody(code, message, httpStatus.value(), requestId(exchange))));
    }

    @ExceptionHandler(CareerIndexLimitException.class)
    public Mono<ResponseEntity<ErrorResponseBody>> handleCareerIndexLimit(
            CareerIndexLimitException ex,
            ServerWebExchange exchange) {
        return Mono.just(ResponseEntity.status(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorResponseBody(
                        "CAREER_INDEX_LIMIT_REACHED",
                        "La carrera alcanzó el límite de historial permitido.",
                        HttpStatus.CONFLICT.value(),
                        requestId(exchange))));
    }

    @ExceptionHandler(ServerWebInputException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleServerWebInput(
            ServerWebInputException ex,
            ServerWebExchange exchange) {
        Throwable cause = ex.getCause();
        log.warn("Public request decoding failure: path={}, exception={}, cause={}",
                exchange.getRequest().getPath().pathWithinApplication().value(),
                ex.getClass().getName(),
                causeChain(cause));
        return validationError(ex, exchange);
    }

    @ExceptionHandler(IllegalStateException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleIllegalState(
            IllegalStateException ex,
            ServerWebExchange exchange) {
        Map<String, Object> body = new HashMap<>();
        String internal = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase(java.util.Locale.ROOT);
        boolean lifecycleConflict = internal.contains("lifecycle") || internal.contains("generation")
                || internal.contains("ownership index");
        HttpStatus status = lifecycleConflict ? HttpStatus.CONFLICT : HttpStatus.UNPROCESSABLE_ENTITY;
        body.put("code", lifecycleConflict
                ? (internal.contains("stale") ? "CAREER_STALE_GENERATION" : "CAREER_OPERATION_CONFLICT")
                : "LINEUP_STATE_ERROR");
        body.put("message", lifecycleConflict
                ? "La operaciÃ³n de carrera ya no estÃ¡ vigente."
                : messageResolver.clientMessage(ex, INVALID_STATE_MESSAGE));
        body.put("status", status.value());
        body.put("requestId", requestId(exchange));
        return Mono.just(
            ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
        );
    }

    @ExceptionHandler(UnauthorizedException.class)
    public Mono<ResponseEntity<ErrorResponseBody>> handleUnauthorized(
            UnauthorizedException ex,
            ServerWebExchange exchange) {
        return Mono.just(
            ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header("WWW-Authenticate", "Bearer")
                .contentType(MediaType.APPLICATION_JSON)
                .body(ErrorResponseBody.unauthorized(
                    messageResolver.clientMessage(ex, UNAUTHORIZED_MESSAGE),
                    requestId(exchange)))
        );
    }

    @ExceptionHandler(ImpersonationForbiddenException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleImpersonationForbidden(
            ImpersonationForbiddenException ex,
            ServerWebExchange exchange) {
        return forbidden(ex, exchange, "IMPERSONATION_FORBIDDEN");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleAccessDenied(
            AccessDeniedException ex,
            ServerWebExchange exchange) {
        return forbidden(ex, exchange, "FORBIDDEN");
    }

    @ExceptionHandler(ResponseStatusException.class)
    public Mono<ResponseEntity<ErrorResponseBody>> handleResponseStatus(
            ResponseStatusException ex,
            ServerWebExchange exchange) {
        HttpStatusCode status = ex.getStatusCode();
        ErrorResponseBody body = new ErrorResponseBody(
            publicCodeFor(status),
            messageResolver.clientMessage(ex, publicMessageFor(status)),
            status.value(),
            requestId(exchange));
        return Mono.just(
            ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
        );
    }

    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<ErrorResponseBody>> handleUnexpected(
            Exception ex,
            ServerWebExchange exchange) {
        Throwable authFailure = findAuthFailure(ex);
        if (authFailure instanceof AuthConflictException) {
            return Mono.just(ResponseEntity.status(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorResponseBody("AUTH_EMAIL_EXISTS", "El email ya esta registrado.",
                    HttpStatus.CONFLICT.value(), requestId(exchange))));
        }
        if (authFailure instanceof AuthCredentialsException) {
            return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorResponseBody("AUTH_INVALID_CREDENTIALS", "Las credenciales no son validas.",
                    HttpStatus.BAD_REQUEST.value(), requestId(exchange))));
        }
        if (authFailure instanceof AuthValidationException) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorResponseBody("AUTH_VALIDATION_ERROR", "La solicitud de autenticacion no es valida.",
                    HttpStatus.UNPROCESSABLE_ENTITY.value(), requestId(exchange))));
        }
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

    private static Throwable findAuthFailure(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof AuthConflictException
                || current instanceof AuthCredentialsException
                || current instanceof AuthValidationException) {
                return current;
            }
            current = current.getCause();
        }
        return null;
    }

    private static String causeChain(Throwable cause) {
        if (cause == null) {
            return "none";
        }
        StringBuilder chain = new StringBuilder();
        Throwable current = cause;
        int depth = 0;
        while (current != null && depth++ < 4) {
            if (chain.length() > 0) {
                chain.append(" -> ");
            }
            chain.append(current.getClass().getName());
            current = current.getCause();
        }
        return chain.toString();
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

    private Mono<ResponseEntity<Map<String, Object>>> authError(
            ServerWebExchange exchange,
            String code,
            String message,
            HttpStatus status) {
        Map<String, Object> body = new HashMap<>();
        body.put("code", code);
        body.put("message", message);
        body.put("status", status.value());
        body.put("requestId", requestId(exchange));
        return Mono.just(ResponseEntity.status(status)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body));
    }

    private Mono<ResponseEntity<Map<String, Object>>> forbidden(
            Exception ex,
            ServerWebExchange exchange,
            String code) {
        Map<String, Object> body = new HashMap<>();
        body.put("code", code);
        body.put("message", messageResolver.clientMessage(ex, FORBIDDEN_MESSAGE));
        body.put("status", HttpStatus.FORBIDDEN.value());
        body.put("requestId", requestId(exchange));
        return Mono.just(
            ResponseEntity.status(HttpStatus.FORBIDDEN)
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

    private static String publicCodeFor(HttpStatusCode status) {
        if (status.value() == HttpStatus.NOT_FOUND.value()) {
            return "NOT_FOUND";
        }
        if (status.value() == HttpStatus.CONFLICT.value()) {
            return "CONFLICT";
        }
        if (status.value() == HttpStatus.FORBIDDEN.value()) {
            return "FORBIDDEN";
        }
        return "REQUEST_ERROR";
    }

    private static String publicMessageFor(HttpStatusCode status) {
        if (status.value() == HttpStatus.NOT_FOUND.value()) {
            return NOT_FOUND_MESSAGE;
        }
        if (status.value() == HttpStatus.CONFLICT.value()) {
            return CONFLICT_MESSAGE;
        }
        if (status.value() == HttpStatus.FORBIDDEN.value()) {
            return FORBIDDEN_MESSAGE;
        }
        return INVALID_REQUEST_MESSAGE;
    }
}
