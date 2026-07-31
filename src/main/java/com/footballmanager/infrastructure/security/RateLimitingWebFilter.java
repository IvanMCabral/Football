package com.footballmanager.infrastructure.security;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

@Component
@Order(-100)
public class RateLimitingWebFilter implements WebFilter {

    private final boolean enabled;
    private final int maxRequests;
    private final Duration window;
    private final Clock clock;
    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();

    @Autowired
    public RateLimitingWebFilter(
            @Value("${app.security.rate-limit.enabled:true}") boolean enabled,
            @Value("${app.security.rate-limit.auth.max-requests:20}") int maxRequests,
            @Value("${app.security.rate-limit.auth.window:PT1M}") Duration window) {
        this(enabled, maxRequests, window, Clock.systemUTC());
    }

    RateLimitingWebFilter(boolean enabled, int maxRequests, Duration window, Clock clock) {
        this.enabled = enabled;
        this.maxRequests = maxRequests;
        this.window = window;
        this.clock = clock;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!enabled || !isLimitedPath(exchange)) {
            return chain.filter(exchange);
        }

        String key = clientKey(exchange);
        long now = clock.millis();
        WindowCounter counter = counters.compute(key, (ignored, existing) -> {
            if (existing == null || now >= existing.windowEndsAtMillis) {
                return new WindowCounter(now + window.toMillis(), 1);
            }
            existing.count.incrementAndGet();
            return existing;
        });

        if (counter.count.get() > maxRequests) {
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            return exchange.getResponse().setComplete();
        }
        return chain.filter(exchange);
    }

    private static boolean isLimitedPath(ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().pathWithinApplication().value();
        return "/api/v1/auth/login".equals(path)
            || "/api/v1/auth/register".equals(path)
            || "/api/v1/auth/refresh".equals(path);
    }

    private static String clientKey(ServerWebExchange exchange) {
        String forwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        if (exchange.getRequest().getRemoteAddress() == null) {
            return "unknown";
        }
        return exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
    }

    private static final class WindowCounter {
        private final long windowEndsAtMillis;
        private final AtomicInteger count;

        private WindowCounter(long windowEndsAtMillis, int initialCount) {
            this.windowEndsAtMillis = windowEndsAtMillis;
            this.count = new AtomicInteger(initialCount);
        }
    }
}
