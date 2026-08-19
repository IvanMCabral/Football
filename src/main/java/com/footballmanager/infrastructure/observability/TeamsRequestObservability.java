package com.footballmanager.infrastructure.observability;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.util.context.ContextView;

/** Sanitized, route-scoped lifecycle logging for the teams catalog read. */
@Component
public class TeamsRequestObservability {

    public static final String ROUTE = "/api/v1/world/teams";
    public static final String START_NANOS_ATTRIBUTE = TeamsRequestObservability.class.getName()
            + ".startNanos";
    public static final String CORRELATION_ID_CONTEXT_KEY = TeamsRequestObservability.class.getName()
            + ".correlationId";
    public static final String START_NANOS_CONTEXT_KEY = TeamsRequestObservability.class.getName()
            + ".startNanos";

    private static final String METHOD = HttpMethod.GET.name();
    private static final Logger log = LoggerFactory.getLogger(TeamsRequestObservability.class);

    public boolean isTeamsRoute(ServerWebExchange exchange) {
        return exchange != null
                && HttpMethod.GET.equals(exchange.getRequest().getMethod())
                && ROUTE.equals(exchange.getRequest().getPath().value());
    }

    public void ingress(String correlationId, long startNanos) {
        event(correlationId, "INGRESS", elapsedMs(startNanos));
    }

    public void authSuccess(ServerWebExchange exchange, String correlationId, long startNanos) {
        if (isTeamsRoute(exchange)) {
            event(correlationId, "AUTH_SUCCESS", elapsedMs(startNanos));
        }
    }

    public void authFailure(ServerWebExchange exchange, String correlationId, long startNanos) {
        if (isTeamsRoute(exchange)) {
            event(correlationId, "AUTH_FAILURE", elapsedMs(startNanos));
        }
    }

    public void controllerEnter(String correlationId, long startNanos) {
        event(correlationId, "CONTROLLER_ENTER", elapsedMs(startNanos));
    }

    public void serviceStart(String correlationId, long startNanos) {
        event(correlationId, "SERVICE_START", elapsedMs(startNanos));
    }

    public void serviceSuccess(String correlationId, long startNanos) {
        event(correlationId, "SERVICE_SUCCESS", elapsedMs(startNanos));
    }

    public void serviceError(String correlationId, long startNanos) {
        event(correlationId, "SERVICE_ERROR", elapsedMs(startNanos));
    }

    public void responseCommit(String correlationId, long startNanos) {
        event(correlationId, "RESPONSE_COMMIT", elapsedMs(startNanos));
    }

    public void requestTerminal(ServerWebExchange exchange, String correlationId, long startNanos) {
        HttpStatusCode status = exchange.getResponse().getStatusCode();
        long elapsedMs = elapsedMs(startNanos);
        if (status == null) {
            event(correlationId, "REQUEST_TERMINAL", elapsedMs);
        } else {
            event(correlationId, "REQUEST_TERMINAL", elapsedMs, status.value());
        }
    }

    public static String correlationId(ContextView contextView) {
        return contextView.getOrDefault(CORRELATION_ID_CONTEXT_KEY, "unavailable");
    }

    public static long startNanos(ContextView contextView) {
        return contextView.getOrDefault(START_NANOS_CONTEXT_KEY, System.nanoTime());
    }

    public static long startNanos(ServerWebExchange exchange) {
        Object value = exchange.getAttributes().get(START_NANOS_ATTRIBUTE);
        return value instanceof Long ? (Long) value : System.nanoTime();
    }

    public static String correlationId(ServerWebExchange exchange) {
        String correlationId = exchange.getResponse().getHeaders()
                .getFirst(com.footballmanager.infrastructure.security.RequestCorrelationWebFilter.REQUEST_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = exchange.getRequest().getHeaders()
                    .getFirst(com.footballmanager.infrastructure.security.RequestCorrelationWebFilter.REQUEST_ID_HEADER);
        }
        return safeCorrelationId(correlationId);
    }

    private static void event(String correlationId, String event, long elapsedMs) {
        log.info("correlationId={} route={} method={} event={} timestamp={} elapsedMs={}",
                safeCorrelationId(correlationId), ROUTE, METHOD, event, Instant.now(), elapsedMs);
    }

    private static void event(String correlationId, String event, long elapsedMs, int status) {
        log.info("correlationId={} route={} method={} event={} timestamp={} elapsedMs={} status={}",
                safeCorrelationId(correlationId), ROUTE, METHOD, event, Instant.now(), elapsedMs, status);
    }

    private static long elapsedMs(long startNanos) {
        return Math.max(0L, (System.nanoTime() - startNanos) / 1_000_000L);
    }

    private static String safeCorrelationId(String correlationId) {
        if (correlationId == null || correlationId.isBlank()
                || correlationId.length() > 80
                || !correlationId.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,79}")) {
            return "unavailable";
        }
        return correlationId;
    }
}
