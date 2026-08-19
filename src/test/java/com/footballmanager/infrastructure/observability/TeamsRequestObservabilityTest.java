package com.footballmanager.infrastructure.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import com.footballmanager.infrastructure.security.RequestCorrelationWebFilter;
import reactor.util.context.Context;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class TeamsRequestObservabilityTest {

    private static final String REQUEST_ID = "req-observability-123";
    private static final String AUTHORIZATION = "Bearer.synthetic.authorization.secret";
    private static final String USER_ID = "synthetic-user-id-7f7f";

    private final TeamsRequestObservability observability = new TeamsRequestObservability();
    private final Logger logger = (Logger) LoggerFactory.getLogger(TeamsRequestObservability.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void attachAppender() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void emitsRequiredSuccessSequenceWithSafeFields() {
        MockServerWebExchange exchange = teamsExchange();
        long startNanos = System.nanoTime();
        Context context = Context.empty()
                .put(TeamsRequestObservability.CORRELATION_ID_CONTEXT_KEY, REQUEST_ID)
                .put(TeamsRequestObservability.START_NANOS_CONTEXT_KEY, startNanos);

        observability.ingress(REQUEST_ID, startNanos);
        observability.authSuccess(exchange, REQUEST_ID, startNanos);
        observability.controllerEnter(REQUEST_ID, startNanos);
        observability.serviceStart(REQUEST_ID, startNanos);
        observability.serviceSuccess(REQUEST_ID, startNanos);
        exchange.getResponse().setStatusCode(HttpStatus.OK);
        observability.responseCommit(REQUEST_ID, startNanos);
        observability.requestTerminal(exchange, REQUEST_ID, startNanos);

        List<String> messages = appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        assertThat(messages).hasSize(7);
        List<String> eventNames = messages.stream().map(this::eventName).toList();
        assertThat(eventNames).containsExactly(
                "INGRESS", "AUTH_SUCCESS", "CONTROLLER_ENTER", "SERVICE_START",
                "SERVICE_SUCCESS", "RESPONSE_COMMIT", "REQUEST_TERMINAL");
        assertThat(messages).allSatisfy(message -> {
            assertThat(message).contains("correlationId=" + REQUEST_ID);
            assertThat(message).contains("route=/api/v1/world/teams", "method=GET", "timestamp=", "elapsedMs=");
            assertThat(message).doesNotContain("userId=", "Authorization", "responseBody", "query=");
        });
        assertThat(messages.get(6)).contains("status=200");
        assertThat(TeamsRequestObservability.correlationId(context)).isEqualTo(REQUEST_ID);
        assertThat(TeamsRequestObservability.startNanos(context)).isEqualTo(startNanos);
    }

    @Test
    void emitsAuthFailureWithoutSensitiveRequestData() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/world/teams?userId=" + USER_ID)
                        .header("Authorization", AUTHORIZATION)
                        .header("X-Request-Id", REQUEST_ID));

        observability.authFailure(exchange, REQUEST_ID, System.nanoTime());

        String message = appender.list.get(0).getFormattedMessage();
        assertThat(message).contains("event=AUTH_FAILURE");
        assertThat(message).doesNotContain(AUTHORIZATION, USER_ID, "userId", "Authorization");
    }

    @Test
    void filterEmitsCommitBeforeTerminal() {
        MockServerWebExchange exchange = teamsExchange();
        RequestCorrelationWebFilter filter = new RequestCorrelationWebFilter();

        StepVerifier.create(filter.filter(exchange, current -> {
            current.getResponse().setStatusCode(HttpStatus.OK);
            return current.getResponse().setComplete();
        })).verifyComplete();

        List<String> eventNames = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .map(this::eventName)
                .toList();
        assertThat(eventNames).containsExactly("INGRESS", "RESPONSE_COMMIT", "REQUEST_TERMINAL");
    }

    @Test
    void ignoresNonTeamsRoute() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/world/players?userId=" + USER_ID));

        observability.authSuccess(exchange, REQUEST_ID, System.nanoTime());
        observability.authFailure(exchange, REQUEST_ID, System.nanoTime());

        assertThat(appender.list).isEmpty();
    }

    private MockServerWebExchange teamsExchange() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/world/teams?userId=" + USER_ID)
                        .header("Authorization", AUTHORIZATION)
                        .header("X-Request-Id", REQUEST_ID));
        exchange.getResponse().setStatusCode(HttpStatus.OK);
        return exchange;
    }

    private String eventName(String message) {
        return message.substring(message.indexOf("event=") + 6)
                .split(" ", 2)[0];
    }
}
