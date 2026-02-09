package com.footballmanager.infrastructure.persistence.redis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.footballmanager.domain.model.entity.MatchCommand;
import com.footballmanager.domain.ports.out.match.MatchCommandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Implementación reactiva del repositorio de comandos de partidos usando Redis.
 * Almacena comandos pendientes de forma distribuida y reactiva.
 */
@Repository
@RequiredArgsConstructor
public class RedisMatchCommandRepository implements MatchCommandRepository {

    private static final String KEY_PREFIX = "match:commands:";
    private static final Duration TTL = Duration.ofHours(24);

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> saveCommand(UUID userId, UUID matchId, MatchCommand command) {
        String key = buildKey(userId, matchId);

        return findPendingCommands(userId, matchId)
                .defaultIfEmpty(new ArrayList<>())
                .map(commands -> {
                    commands.add(command);
                    return commands;
                })
                .flatMap(commands -> {
                    try {
                        String json = objectMapper.writeValueAsString(commands);
                        return redisTemplate.opsForValue()
                                .set(key, json, TTL)
                                .then();
                    } catch (Exception e) {
                        return Mono.error(e);
                    }
                })
                .onErrorResume(e -> Mono.empty());
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
                        return Mono.<List<MatchCommand>>just(new ArrayList<>());
                    }
                })
                .defaultIfEmpty(new ArrayList<>())
                .map(list -> (List<MatchCommand>) list);
    }

    @Override
    public Mono<Void> deleteCommands(UUID userId, UUID matchId) {
        String key = buildKey(userId, matchId);

        return redisTemplate.delete(key)
                .then()
                .onErrorResume(e -> Mono.empty());
    }

    private String buildKey(UUID userId, UUID matchId) {
        return KEY_PREFIX + userId.toString() + ":" + matchId.toString();
    }
}
