package com.footballmanager.infrastructure.observability;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeOperationMetricsTest {

    @AfterEach
    void resetMetrics() {
        RuntimeOperationMetrics.reset();
    }

    @Test
    void aggregatesSuccessfulOperationWithoutPayloadOrIdentityData() {
        RuntimeOperationMetrics.measure("redis.test.load", Mono.just("payload")).block();

        var snapshot = RuntimeOperationMetrics.snapshot().get("redis.test.load");
        assertThat(snapshot).isNotNull();
        assertThat(snapshot.count()).isEqualTo(1);
        assertThat(snapshot.success()).isEqualTo(1);
        assertThat(snapshot.errors()).isZero();
        assertThat(snapshot.averageMillis()).isGreaterThanOrEqualTo(0d);
        assertThat(RuntimeOperationMetrics.snapshot().keySet()).doesNotContain("payload");
    }

    @Test
    void aggregatesFailuresWithoutReplacingTheOriginalError() {
        var error = new IllegalStateException("safe test failure");

        var thrown = org.assertj.core.api.Assertions.assertThatThrownBy(() ->
            RuntimeOperationMetrics.measure("postgres.test.query", Mono.<String>error(error)).block())
            .isInstanceOf(IllegalStateException.class)
            .actual();

        assertThat(thrown).isSameAs(error);
        var snapshot = RuntimeOperationMetrics.snapshot().get("postgres.test.query");
        assertThat(snapshot).isNotNull();
        assertThat(snapshot.errors()).isEqualTo(1);
        assertThat(snapshot.success()).isZero();
    }
}
