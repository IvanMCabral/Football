package com.footballmanager.adapters.in.web.health;

import com.footballmanager.application.service.world.DurableBoundaryClassification;
import com.footballmanager.application.service.world.DurablePersistenceBoundary;

import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;

@Component
@DurableBoundaryClassification(value = DurablePersistenceBoundary.Classification.OUT_OF_SCOPE_WITH_EXPLICIT_REASON,
        reason = "Redis health probe uses an ephemeral probe key and does not persist a World V2 root")
public class RedisHealthProbe {

    private static final Logger log = LoggerFactory.getLogger(RedisHealthProbe.class);
    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration PROBE_TTL = Duration.ofSeconds(5);

    private final ReactiveRedisTemplate<String, String> redisTemplate;

    public RedisHealthProbe(ReactiveRedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Mono<Boolean> isAvailable() {
        String key = "__manager_healthcheck__:" + UUID.randomUUID();
        return redisTemplate.opsForValue()
            .set(key, "ok", PROBE_TTL)
            .flatMap(written -> Boolean.TRUE.equals(written)
                ? redisTemplate.opsForValue().get(key)
                : Mono.just(""))
            .map("ok"::equals)
            .flatMap(readBack -> cleanup(key)
                .thenReturn(readBack))
            .timeout(PROBE_TIMEOUT)
            .onErrorReturn(false);
    }

    private Mono<Long> cleanup(String key) {
        return redisTemplate.delete(key)
            .doOnError(error -> log.warn(
                "Redis health probe cleanup failed for probeKeyPrefix=__manager_healthcheck__"))
            .onErrorReturn(0L);
    }
}
