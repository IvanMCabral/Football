package com.footballmanager.infrastructure.security;

import com.footballmanager.infrastructure.persistence.RequestUserLookup;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import java.util.Set;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestUserLookupWebFilter implements WebFilter {
    private static final Set<String> READS = Set.of(
            "/api/v1/auth/me", "/api/v1/dashboard/user-stats");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (exchange.getRequest().getMethod() == HttpMethod.GET
                && READS.contains(exchange.getRequest().getPath().value())) {
            return chain.filter(exchange).contextWrite(RequestUserLookup::enable);
        }
        return chain.filter(exchange);
    }
}
