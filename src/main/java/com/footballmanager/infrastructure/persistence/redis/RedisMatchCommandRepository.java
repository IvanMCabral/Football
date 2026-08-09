package com.footballmanager.infrastructure.persistence.redis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.footballmanager.domain.model.entity.MatchCommand;
import com.footballmanager.domain.ports.out.match.MatchCommandRepository;
import com.footballmanager.infrastructure.persistence.redis.CareerOwnershipTouchService;
import com.footballmanager.domain.model.valueobject.CareerWriteContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Reactive Redis implementation for pending match commands.
 */
@Repository
public class RedisMatchCommandRepository implements MatchCommandRepository {

    private static final String KEY_PREFIX = "match:commands:";
    private static final Duration TTL = Duration.ofHours(24);

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final CareerOwnershipTouchService ownershipTouchService;

    @Autowired
    public RedisMatchCommandRepository(ReactiveRedisTemplate<String, String> redisTemplate,
                                       ObjectMapper objectMapper,
                                       CareerOwnershipTouchService ownershipTouchService) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.ownershipTouchService = ownershipTouchService;
    }

    public RedisMatchCommandRepository(ReactiveRedisTemplate<String, String> redisTemplate,
                                       ObjectMapper objectMapper) {
        this(redisTemplate, objectMapper, null);
    }

    @Override
    public Mono<Void> saveCommand(UUID userId, UUID matchId, MatchCommand command) {
        return saveCommand(userId, matchId, command, (String) null);
    }

    @Override
    public Mono<Void> saveCommand(UUID userId, UUID matchId, MatchCommand command, String careerId) {
        if (ownershipTouchService != null && careerId != null && !careerId.isBlank()) {
            return Mono.error(new IllegalStateException("command writer requires lifecycle context"));
        }
        return saveCommandInternal(userId, matchId, command, null);
    }

    @Override
    public Mono<Void> saveCommandWithContext(UUID userId, UUID matchId, MatchCommand command, CareerWriteContext context) {
        if (context == null) {
            return Mono.error(new IllegalArgumentException("career lifecycle context is required"));
        }
        return saveCommandInternal(userId, matchId, command, context);
    }

    private Mono<Void> saveCommandInternal(UUID userId, UUID matchId, MatchCommand command, CareerWriteContext context) {
        String key = buildKey(userId, matchId);
        Mono<Void> operation = findPendingCommands(userId, matchId)
                .defaultIfEmpty(new ArrayList<>())
                .map(commands -> {
                    commands.add(command);
                    return commands;
                })
                .flatMap(commands -> {
                    try {
                        String json = objectMapper.writeValueAsString(commands);
                        return redisTemplate.opsForValue().set(key, json, TTL).then();
                    } catch (Exception e) {
                        return Mono.error(e);
                    }
                });
        Mono<Void> coordinated = ownershipTouchService == null
                ? operation
                : context == null
                        ? Mono.error(new IllegalStateException("command writer requires active career context"))
                        : ownershipTouchService.touchBeforeWrite(context, key, () -> operation);
        return coordinated
                .onErrorMap(e -> e instanceof RedisStateAccessException ? e
                        : new RedisStateAccessException(
                                "Failed to save pending match command for matchId=" + matchId, e));
    }

    @Override
    public Mono<List<MatchCommand>> findPendingCommands(UUID userId, UUID matchId) {
        String key = buildKey(userId, matchId);

        return redisTemplate.opsForValue()
                .get(key)
                .flatMap(json -> {
                    try {
                        List<MatchCommand> commands = objectMapper.readValue(
                                json,
                                new TypeReference<List<MatchCommand>>() {}
                        );
                        return Mono.just(commands);
                    } catch (Exception e) {
                        return Mono.error(new RedisStateAccessException(
                                "Failed to deserialize pending match commands for matchId=" + matchId, e));
                    }
                })
                .defaultIfEmpty(new ArrayList<>())
                .onErrorMap(e -> e instanceof RedisStateAccessException ? e
                        : new RedisStateAccessException(
                                "Failed to read pending match commands for matchId=" + matchId, e))
                .map(list -> (List<MatchCommand>) list);
    }

    @Override
    public Mono<Void> deleteCommands(UUID userId, UUID matchId) {
        if (ownershipTouchService != null) {
            return Mono.error(new IllegalStateException("command deletion requires lifecycle context"));
        }
        return deleteCommandsInternal(userId, matchId, null);
    }

    @Override
    public Mono<Void> deleteCommandsWithContext(UUID userId, UUID matchId, CareerWriteContext context) {
        if (context == null) {
            return Mono.error(new IllegalArgumentException("career lifecycle context is required"));
        }
        return deleteCommandsInternal(userId, matchId, context);
    }

    private Mono<Void> deleteCommandsInternal(UUID userId, UUID matchId, CareerWriteContext context) {
        String key = buildKey(userId, matchId);
        Mono<Void> operation = redisTemplate.delete(key)
                .then()
                .onErrorMap(e -> new RedisStateAccessException(
                        "Failed to delete pending match commands for matchId=" + matchId, e));
        return ownershipTouchService == null || context == null
                ? operation
                : ownershipTouchService.touchBeforeWrite(context, key, () -> operation);
    }

    private String buildKey(UUID userId, UUID matchId) {
        return KEY_PREFIX + userId.toString() + ":" + matchId.toString();
    }
}
