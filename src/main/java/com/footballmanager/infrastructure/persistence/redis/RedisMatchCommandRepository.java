package com.footballmanager.infrastructure.persistence.redis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.footballmanager.domain.model.entity.MatchCommand;
import com.footballmanager.domain.ports.out.match.MatchCommandRepository;
import com.footballmanager.infrastructure.persistence.redis.CareerOwnershipTouchService;
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
        return saveCommand(userId, matchId, command, null);
    }

    @Override
    public Mono<Void> saveCommand(UUID userId, UUID matchId, MatchCommand command, String careerId) {
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
                : careerId == null || careerId.isBlank()
                        ? ownershipTouchService.touchOwnerBeforeWrite(userId, () -> operation)
                        : ownershipTouchService.touchBeforeWrite(careerId, () -> operation);
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
        String key = buildKey(userId, matchId);

        return redisTemplate.delete(key)
                .then()
                .onErrorMap(e -> new RedisStateAccessException(
                        "Failed to delete pending match commands for matchId=" + matchId, e));
    }

    private String buildKey(UUID userId, UUID matchId) {
        return KEY_PREFIX + userId.toString() + ":" + matchId.toString();
    }
}
