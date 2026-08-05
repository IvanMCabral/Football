package com.footballmanager.adapters.out.redis;

import com.footballmanager.domain.ports.out.career.CareerDataCleanupResult;
import com.footballmanager.domain.ports.out.career.CareerDataCleanupException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.reactivestreams.Publisher;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveSetOperations;
import org.springframework.data.redis.core.ScanOptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisCareerDataCleanupRepositoryTest {

    @Mock ReactiveRedisTemplate<String, String> redisTemplate;
    @Mock ReactiveSetOperations<String, String> setOperations;

    private UUID ownerA;
    private RedisCareerDataCleanupRepository repository;
    private final List<List<String>> deletedBatches = new ArrayList<>();

    @BeforeEach
    void setUp() {
        ownerA = UUID.randomUUID();
        repository = new RedisCareerDataCleanupRepository(redisTemplate);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members("user:" + ownerA + ":career-ids"))
                .thenReturn(Flux.just("career-a"));
        lenient().when(redisTemplate.unlink(any(Publisher.class))).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked") Publisher<String> publisher = invocation.getArgument(0);
            List<String> batch = Flux.from(publisher).collectList().block();
            deletedBatches.add(batch);
            return Mono.just((long) batch.size());
        });
    }

    @Test
    void indexedOrphanDetailsAreDeletedWithoutGlobalCareerScan() {
        when(redisTemplate.scan(any(ScanOptions.class))).thenAnswer(invocation -> {
            String pattern = invocation.<ScanOptions>getArgument(0).getPattern();
            if (pattern.equals("career:career-a:match-detail:*")) {
                return Flux.just("career:career-a:match-detail:m1");
            }
            if (pattern.equals("career:career-a:match-baseline:*")) {
                return Flux.just("career:career-a:match-baseline:m1");
            }
            return Flux.empty();
        });

        CareerDataCleanupResult result = repository.deleteOwnedData(ownerA, null).block();

        assertEquals(2, result.uniqueKeys());
        assertEquals(2, result.keysActuallyDeleted());
        assertEquals(1, result.careerCount());
        assertEquals(0, result.keysDiscovered() - result.uniqueKeys());
        verify(redisTemplate, never()).scan(argThat(options -> options.getPattern().equals("career:*")));
    }

    @Test
    void duplicateDiscoveryIsDeletedOnceAndBatchesNeverExceedOneHundred() {
        List<String> projections = new ArrayList<>();
        for (int i = 0; i < 205; i++) {
            projections.add("user:" + ownerA + ":projection:" + i);
        }
        when(redisTemplate.scan(any(ScanOptions.class))).thenAnswer(invocation -> {
            String pattern = invocation.<ScanOptions>getArgument(0).getPattern();
            if (pattern.equals("user:" + ownerA + ":*")) {
                return Flux.concat(Flux.fromIterable(projections), Flux.just(projections.get(0)));
            }
            return Flux.empty();
        });

        CareerDataCleanupResult result = repository.deleteOwnedData(ownerA, "career-a").block();

        assertEquals(205, result.uniqueKeys());
        assertEquals(205, result.keysRequestedForDeletion());
        assertEquals(205, result.keysActuallyDeleted());
        assertEquals(3, result.batchCount());
        assertEquals(100, result.maxBatchSize());
        assertTrue(deletedBatches.stream().allMatch(batch -> batch.size() <= 100));
    }

    @Test
    void redisDeleteShortCountIsReflectedInResult() {
        when(redisTemplate.scan(any(ScanOptions.class))).thenReturn(Flux.just("world:" + ownerA));
        when(redisTemplate.unlink(any(Publisher.class))).thenReturn(Mono.just(0L));

        CareerDataCleanupResult result = repository.deleteOwnedData(ownerA, null).block();

        assertEquals(1, result.keysRequestedForDeletion());
        assertEquals(0, result.keysActuallyDeleted());
        assertFalse(result.partialFailure());
    }

    @Test
    void redisDeleteFailureReturnsSanitizedPartialResult() {
        when(redisTemplate.scan(any(ScanOptions.class))).thenAnswer(invocation -> {
            String pattern = invocation.<ScanOptions>getArgument(0).getPattern();
            return pattern.equals("world:" + ownerA)
                    ? Flux.just("world:" + ownerA)
                    : Flux.empty();
        });
        when(redisTemplate.unlink(any(Publisher.class))).thenReturn(Mono.error(new IllegalStateException("redis unavailable")));

        CareerDataCleanupException failure = assertThrows(CareerDataCleanupException.class,
                () -> repository.deleteOwnedData(ownerA, null).block());

        assertTrue(failure.result().partialFailure());
        assertEquals("world", failure.result().failedFamily());
        assertEquals(1, failure.result().keysRequestedForDeletion());
        assertEquals(0, failure.result().keysActuallyDeleted());
        assertFalse(failure.getMessage().contains(ownerA.toString()));
    }

    @Test
    void missingRootAndMissingIndexReturnsSafeExplicitEmptyResult() {
        when(setOperations.members("user:" + ownerA + ":career-ids")).thenReturn(Flux.empty());
        when(redisTemplate.scan(any(ScanOptions.class))).thenReturn(Flux.empty());

        CareerDataCleanupResult result = repository.deleteOwnedData(ownerA, null).block();

        assertEquals(0, result.careerCount());
        assertEquals(0, result.uniqueKeys());
        assertEquals(0, result.keysActuallyDeleted());
        assertFalse(result.partialFailure());
    }
}
