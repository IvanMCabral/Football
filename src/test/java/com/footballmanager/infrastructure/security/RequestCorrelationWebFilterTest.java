package com.footballmanager.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class RequestCorrelationWebFilterTest {

    @Test
    void propagatesRequestIdAndSecurityHeaders() {
        RequestCorrelationWebFilter filter = new RequestCorrelationWebFilter();
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/health")
                .header(RequestCorrelationWebFilter.REQUEST_ID_HEADER, "req-123"));

        StepVerifier.create(filter.filter(exchange, ignored -> Mono.empty())).verifyComplete();

        assertThat(exchange.getResponse().getHeaders().getFirst("X-Request-Id")).isEqualTo("req-123");
        assertThat(exchange.getResponse().getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(exchange.getResponse().getHeaders().getFirst("X-Frame-Options")).isEqualTo("DENY");
        assertThat(exchange.getResponse().getHeaders().getCacheControl()).isEqualTo("no-store");
    }
}
