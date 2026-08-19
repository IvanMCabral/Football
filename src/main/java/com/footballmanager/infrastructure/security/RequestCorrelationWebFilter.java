package com.footballmanager.infrastructure.security;

import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import com.footballmanager.infrastructure.observability.TeamsRequestObservability;

import reactor.core.publisher.Mono;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCorrelationWebFilter implements WebFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    private final TeamsRequestObservability observability;

    public RequestCorrelationWebFilter() {
        this(new TeamsRequestObservability());
    }

    @Autowired
    public RequestCorrelationWebFilter(TeamsRequestObservability observability) {
        this.observability = observability;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String requestId = resolveRequestId(exchange);
        long startNanos = System.nanoTime();
        exchange.getAttributes().put(TeamsRequestObservability.START_NANOS_ATTRIBUTE, startNanos);
        exchange.getResponse().getHeaders().set(REQUEST_ID_HEADER, requestId);
        exchange.getResponse().getHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponse().getHeaders().set("X-Frame-Options", "DENY");
        exchange.getResponse().getHeaders().set(HttpHeaders.CACHE_CONTROL, "no-store");

        MDC.put("requestId", requestId);
        if (observability.isTeamsRoute(exchange)) {
            observability.ingress(requestId, startNanos);
            exchange.getResponse().beforeCommit(() -> {
                observability.responseCommit(requestId, startNanos);
                return Mono.empty();
            });
        }
        return chain.filter(exchange)
            .doFinally(signal -> {
                if (observability.isTeamsRoute(exchange)) {
                    observability.requestTerminal(exchange, requestId, startNanos);
                }
                MDC.remove("requestId");
            })
            .contextWrite(context -> context
                .put(TeamsRequestObservability.CORRELATION_ID_CONTEXT_KEY, requestId)
                .put(TeamsRequestObservability.START_NANOS_CONTEXT_KEY, startNanos));
    }

    private static String resolveRequestId(ServerWebExchange exchange) {
        String requestId = exchange.getRequest().getHeaders().getFirst(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank() || requestId.length() > 80
                || !requestId.trim().matches("[A-Za-z0-9][A-Za-z0-9_-]{0,79}")) {
            return UUID.randomUUID().toString();
        }
        return requestId.trim();
    }
}
