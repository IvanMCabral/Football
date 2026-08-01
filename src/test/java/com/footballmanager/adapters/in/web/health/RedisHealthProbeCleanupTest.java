package com.footballmanager.adapters.in.web.health;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisHealthProbeCleanupTest {

    @Test
    @SuppressWarnings("unchecked")
    void deleteFailureAfterSuccessfulSetAndGetDoesNotMarkRedisDown() {
        ReactiveRedisTemplate<String, String> template = mock(ReactiveRedisTemplate.class);
        ReactiveValueOperations<String, String> valueOperations = mock(ReactiveValueOperations.class);
        when(template.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.set(any(String.class), eq("ok"), any(Duration.class))).thenReturn(Mono.just(true));
        when(valueOperations.get(any(String.class))).thenReturn(Mono.just("ok"));
        when(template.delete(any(String.class))).thenReturn(Mono.error(new IllegalStateException("delete failed")));

        RedisHealthProbe probe = new RedisHealthProbe(template);

        StepVerifier.create(probe.isAvailable())
            .expectNext(true)
            .verifyComplete();
    }

    @Test
    @SuppressWarnings("unchecked")
    void wrongValueMarksRedisDown() {
        ReactiveRedisTemplate<String, String> template = mock(ReactiveRedisTemplate.class);
        ReactiveValueOperations<String, String> valueOperations = mock(ReactiveValueOperations.class);
        when(template.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.set(any(String.class), eq("ok"), any(Duration.class))).thenReturn(Mono.just(true));
        when(valueOperations.get(any(String.class))).thenReturn(Mono.just("unexpected"));
        when(template.delete(any(String.class))).thenReturn(Mono.just(1L));

        RedisHealthProbe probe = new RedisHealthProbe(template);

        StepVerifier.create(probe.isAvailable())
            .expectNext(false)
            .verifyComplete();
    }
}
