package com.footballmanager.adapters.in.web.common;

import com.footballmanager.application.exception.AuthConflictException;
import com.footballmanager.application.exception.AuthCredentialsException;
import com.footballmanager.application.exception.AuthValidationException;
import com.footballmanager.domain.ports.out.career.CareerDataCleanupException;
import com.footballmanager.domain.ports.out.career.CareerDataCleanupResult;
import com.footballmanager.domain.ports.out.career.CareerIndexLimitException;
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
    void classifiesAuthenticationFailuresOutsideLineupContract() {
        ErrorResponseBody conflict = handler.handleAuthConflict(
                new AuthConflictException("jdbc:postgresql://internal-db duplicate"),
                exchangeWithRequestId("req-auth-conflict"))
            .block().getBody();
        ErrorResponseBody credentials = handler.handleAuthCredentials(
                new AuthCredentialsException("internal password details"),
                exchangeWithRequestId("req-auth-credentials"))
            .block().getBody();
        ErrorResponseBody validation = handler.handleAuthValidation(
                new AuthValidationException("internal validation details"),
                exchangeWithRequestId("req-auth-validation"))
            .block().getBody();

        assertThat(conflict).isNotNull();
        assertThat(conflict.code()).isEqualTo("AUTH_EMAIL_EXISTS");
        assertThat(conflict.status()).isEqualTo(409);
        assertThat(conflict.requestId()).isEqualTo("req-auth-conflict");
        assertThat(conflict.toString()).doesNotContain("internal-db");
        assertThat(credentials).isNotNull();
        assertThat(credentials.code()).isEqualTo("AUTH_INVALID_CREDENTIALS");
        assertThat(credentials.status()).isEqualTo(400);
        assertThat(credentials.requestId()).isEqualTo("req-auth-credentials");
        assertThat(validation).isNotNull();
        assertThat(validation.code()).isEqualTo("AUTH_VALIDATION_ERROR");
        assertThat(validation.status()).isEqualTo(422);
        assertThat(validation.requestId()).isEqualTo("req-auth-validation");
    }

    @Test
    void rendersLifecycleConflictMessageWithoutEncodingCorruption() {
        var response = handler.handleIllegalState(
                new IllegalStateException("career lifecycle generation is stale"),
                exchangeWithRequestId("req-stale"))
            .block();

        assertThat(response).isNotNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("code")).isEqualTo("CAREER_STALE_GENERATION");
        assertThat(body.get("message")).isEqualTo("La operación de carrera ya no está vigente.");
        assertThat(body.get("message").toString()).doesNotContain("Ã", "Â", "�");
        assertThat(body.get("requestId")).isEqualTo("req-stale");
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

    @Test
    void exposesStableCleanupCodesWithoutInternalDetails() {
        CareerDataCleanupResult rejected = result(CareerDataCleanupResult.Status.REJECTED_OWNERSHIP);
        ErrorResponseBody rejectedBody = handler.handleCareerCleanup(
                new CareerDataCleanupException(rejected, new IllegalStateException("redis key leaked")),
                exchangeWithRequestId("req-rejected")).block().getBody();
        assertThat(rejectedBody.code()).isEqualTo("CAREER_CLEANUP_OWNERSHIP_REJECTED");
        assertThat(rejectedBody.status()).isEqualTo(422);
        assertThat(rejectedBody.toString()).doesNotContain("redis", "ownerHash");

        CareerDataCleanupResult retryable = result(CareerDataCleanupResult.Status.PARTIAL_RETRYABLE);
        ErrorResponseBody retryableBody = handler.handleCareerCleanup(
                new CareerDataCleanupException(retryable, new IllegalStateException("internal")),
                exchangeWithRequestId("req-retry")).block().getBody();
        assertThat(retryableBody.code()).isEqualTo("CAREER_CLEANUP_RETRYABLE");
        assertThat(retryableBody.status()).isEqualTo(503);

        ErrorResponseBody limitBody = handler.handleCareerIndexLimit(
                new CareerIndexLimitException(), exchangeWithRequestId("req-limit")).block().getBody();
        assertThat(limitBody.code()).isEqualTo("CAREER_INDEX_LIMIT_REACHED");
        assertThat(limitBody.status()).isEqualTo(409);
    }

    private static CareerDataCleanupResult result(CareerDataCleanupResult.Status status) {
        return new CareerDataCleanupResult(0, 0, 0, 0, 0, 0, 0, 0, 0,
                "owner-hash", 0, 0, Map.of(), false, "", "", status, 1);
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
