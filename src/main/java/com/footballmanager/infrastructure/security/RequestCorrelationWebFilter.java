package com.footballmanager.infrastructure.security;

import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

@Component
public class RequestCorrelationWebFilter implements WebFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String requestId = resolveRequestId(exchange);
        exchange.getResponse().getHeaders().set(REQUEST_ID_HEADER, requestId);
        exchange.getResponse().getHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponse().getHeaders().set("X-Frame-Options", "DENY");
        exchange.getResponse().getHeaders().set(HttpHeaders.CACHE_CONTROL, "no-store");

        MDC.put("requestId", requestId);
        return chain.filter(exchange)
            .doFinally(signal -> MDC.remove("requestId"));
    }

    private static String resolveRequestId(ServerWebExchange exchange) {
        String requestId = exchange.getRequest().getHeaders().getFirst(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank() || requestId.length() > 80) {
            return UUID.randomUUID().toString();
        }
        return requestId.trim();
    }
}
