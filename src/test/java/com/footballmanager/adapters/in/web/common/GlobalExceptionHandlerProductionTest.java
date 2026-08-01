package com.footballmanager.adapters.in.web.common;

import com.footballmanager.infrastructure.security.RequestCorrelationWebFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerProductionTest {

    private static final String SENSITIVE_MESSAGE =
        "jdbc:postgresql://internal-db:5432/app failed at C:\\secret\\path with LettuceConnectionException";

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(
        new PublicErrorMessageResolver(prodEnvironment()));

    @Test
    void sanitizesValidationMessagesInProductionBodies() {
        MockServerWebExchange exchange = exchangeWithRequestId("req-validation");

        var response = handler.handleIllegalArgument(
            new IllegalArgumentException(SENSITIVE_MESSAGE),
            exchange).block();

        assertThat(response).isNotNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("code")).isEqualTo("LINEUP_VALIDATION_ERROR");
        assertThat(body.get("message")).isEqualTo("La solicitud no es válida.");
        assertThat(body.get("requestId")).isEqualTo("req-validation");
        assertThat(body.toString()).doesNotContain("internal-db", "LettuceConnectionException", "C:\\secret");
    }

    @Test
    void sanitizesUnexpectedMessagesInProductionBodies() {
        MockServerWebExchange exchange = exchangeWithRequestId("req-unexpected");

        var response = handler.handleUnexpected(new RuntimeException(SENSITIVE_MESSAGE), exchange).block();

        assertThat(response).isNotNull();
        ErrorResponseBody body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.code()).isEqualTo("INTERNAL_ERROR");
        assertThat(body.message()).isEqualTo("Ocurrió un error inesperado.");
        assertThat(body.requestId()).isEqualTo("req-unexpected");
        assertThat(body.toString()).doesNotContain("internal-db", "LettuceConnectionException", "C:\\secret");
    }

    @Test
    void sanitizesUnauthorizedMessagesInProductionBodies() {
        MockServerWebExchange exchange = exchangeWithRequestId("req-auth");

        var response = handler.handleUnauthorized(new UnauthorizedException(SENSITIVE_MESSAGE), exchange).block();

        assertThat(response).isNotNull();
        ErrorResponseBody body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.code()).isEqualTo("UNAUTHORIZED");
        assertThat(body.message()).isEqualTo("No autenticado.");
        assertThat(body.requestId()).isEqualTo("req-auth");
        assertThat(body.toString()).doesNotContain("internal-db", "LettuceConnectionException", "C:\\secret");
    }

    private static MockServerWebExchange exchangeWithRequestId(String requestId) {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/test")
            .header(RequestCorrelationWebFilter.REQUEST_ID_HEADER, requestId));
        exchange.getResponse().getHeaders().set(RequestCorrelationWebFilter.REQUEST_ID_HEADER, requestId);
        return exchange;
    }

    private static MockEnvironment prodEnvironment() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        return environment;
    }
}
