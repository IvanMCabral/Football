package com.footballmanager.infrastructure.persistence.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.footballmanager.domain.model.entity.MatchCommand;
import com.footballmanager.domain.model.valueobject.MatchCommandType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisMatchCommandRepositoryTest {

    @Mock
    private ReactiveRedisTemplate<String, String> redisTemplate;

    @Mock
    private ReactiveValueOperations<String, String> valueOperations;

    private RedisMatchCommandRepository repository;

    private UUID userId;
    private UUID matchId;
    private MatchCommand command;

    @BeforeEach
    void setUp() {
        repository = new RedisMatchCommandRepository(redisTemplate, new ObjectMapper());
        userId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        matchId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        command = new MatchCommand(MatchCommandType.CHANGE_TACTIC, UUID.randomUUID(), true, "balanced");
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void findPendingCommandsReturnsEmptyForRealMiss() {
        when(valueOperations.get(anyString())).thenReturn(Mono.empty());

        StepVerifier.create(repository.findPendingCommands(userId, matchId))
                .expectNext(List.of())
                .verifyComplete();
    }

    @Test
    void corruptPayloadPropagatesInfrastructureError() {
        when(valueOperations.get(anyString())).thenReturn(Mono.just("{not-json"));

        StepVerifier.create(repository.findPendingCommands(userId, matchId))
                .expectError(RedisStateAccessException.class)
                .verify();
    }

    @Test
    void redisReadFailurePropagatesInfrastructureError() {
        when(valueOperations.get(anyString())).thenReturn(Mono.error(
                new RedisConnectionFailureException("simulated outage")));

        StepVerifier.create(repository.findPendingCommands(userId, matchId))
                .expectError(RedisStateAccessException.class)
                .verify();
    }

    @Test
    void saveFailurePropagatesInfrastructureError() {
        when(valueOperations.get(anyString())).thenReturn(Mono.empty());
        when(valueOperations.set(anyString(), anyString(), eq(Duration.ofHours(24))))
                .thenReturn(Mono.error(new RedisConnectionFailureException("simulated outage")));

        StepVerifier.create(repository.saveCommand(userId, matchId, command))
                .expectError(RedisStateAccessException.class)
                .verify();
    }

    @Test
    void deleteFailurePropagatesInfrastructureError() {
        when(redisTemplate.delete(anyString())).thenReturn(Mono.error(
                new RedisConnectionFailureException("simulated outage")));

        StepVerifier.create(repository.deleteCommands(userId, matchId))
                .expectError(RedisStateAccessException.class)
                .verify();
    }

    @Test
    void saveAppendsCommandAndWritesRedisPayload() {
        when(valueOperations.get(anyString())).thenReturn(Mono.empty());
        when(valueOperations.set(anyString(), anyString(), eq(Duration.ofHours(24))))
                .thenReturn(Mono.just(true));

        StepVerifier.create(repository.saveCommand(userId, matchId, command))
                .verifyComplete();

        verify(valueOperations).set(anyString(), anyString(), eq(Duration.ofHours(24)));
    }
}
