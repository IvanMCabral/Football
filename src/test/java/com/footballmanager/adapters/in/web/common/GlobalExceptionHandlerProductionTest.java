package com.footballmanager.adapters.in.web.common;

import com.footballmanager.infrastructure.security.RequestCorrelationWebFilter;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

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

    @Test
    void sanitizesForbiddenMessagesInProductionBodies() {
        MockServerWebExchange exchange = exchangeWithRequestId("req-forbidden");

        var response = handler.handleAccessDenied(new AccessDeniedException(SENSITIVE_MESSAGE), exchange).block();

        assertThat(response).isNotNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("code")).isEqualTo("FORBIDDEN");
        assertThat(body.get("message")).isEqualTo("No tenés permiso para operar sobre ese recurso.");
        assertThat(body.get("status")).isEqualTo(403);
        assertThat(body.get("requestId")).isEqualTo("req-forbidden");
        assertThat(body.toString()).doesNotContain("internal-db", "LettuceConnectionException", "C:\\secret");
    }

    @Test
    void sanitizesNotFoundAndConflictMessagesInProductionBodies() {
        ErrorResponseBody notFound = handler.handleResponseStatus(
                new ResponseStatusException(HttpStatus.NOT_FOUND, SENSITIVE_MESSAGE),
                exchangeWithRequestId("req-404"))
            .block()
            .getBody();

        assertThat(notFound).isNotNull();
        assertThat(notFound.code()).isEqualTo("NOT_FOUND");
        assertThat(notFound.status()).isEqualTo(404);
        assertThat(notFound.requestId()).isEqualTo("req-404");
        assertThat(notFound.toString()).doesNotContain("internal-db", "LettuceConnectionException", "C:\\secret");

        ErrorResponseBody conflict = handler.handleResponseStatus(
                new ResponseStatusException(HttpStatus.CONFLICT, SENSITIVE_MESSAGE),
                exchangeWithRequestId("req-409"))
            .block()
            .getBody();

        assertThat(conflict).isNotNull();
        assertThat(conflict.code()).isEqualTo("CONFLICT");
        assertThat(conflict.status()).isEqualTo(409);
        assertThat(conflict.requestId()).isEqualTo("req-409");
        assertThat(conflict.toString()).doesNotContain("internal-db", "LettuceConnectionException", "C:\\secret");
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
