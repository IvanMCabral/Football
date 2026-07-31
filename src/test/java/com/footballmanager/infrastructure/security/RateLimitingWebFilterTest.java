package com.footballmanager.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class RateLimitingWebFilterTest {

    @Test
    void returns429AfterConfiguredAuthLimit() {
        RateLimitingWebFilter filter = new RateLimitingWebFilter(
            true,
            2,
            Duration.ofMinutes(1),
            Clock.fixed(Instant.parse("2026-07-31T00:00:00Z"), ZoneOffset.UTC));
        AtomicInteger passed = new AtomicInteger();

        MockServerWebExchange first = exchange("/api/v1/auth/login");
        StepVerifier.create(filter.filter(first, exchange -> {
            passed.incrementAndGet();
            return Mono.empty();
        })).verifyComplete();

        MockServerWebExchange second = exchange("/api/v1/auth/login");
        StepVerifier.create(filter.filter(second, exchange -> {
            passed.incrementAndGet();
            return Mono.empty();
        })).verifyComplete();

        MockServerWebExchange third = exchange("/api/v1/auth/login");
        StepVerifier.create(filter.filter(third, exchange -> {
            passed.incrementAndGet();
            return Mono.empty();
        })).verifyComplete();

        assertThat(third.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(passed).hasValue(2);
    }

    @Test
    void ignoresNonAuthPaths() {
        RateLimitingWebFilter filter = new RateLimitingWebFilter(
            true,
            0,
            Duration.ofMinutes(1),
            Clock.fixed(Instant.parse("2026-07-31T00:00:00Z"), ZoneOffset.UTC));

        MockServerWebExchange exchange = exchange("/api/v1/health");
        StepVerifier.create(filter.filter(exchange, ignored -> Mono.empty())).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    private static MockServerWebExchange exchange(String path) {
        return MockServerWebExchange.from(MockServerHttpRequest.post(path)
            .remoteAddress(new java.net.InetSocketAddress("127.0.0.1", 12345)));
    }
}
