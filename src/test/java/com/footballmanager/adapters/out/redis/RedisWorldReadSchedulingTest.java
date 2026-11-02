package com.footballmanager.adapters.out.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.footballmanager.domain.model.entity.WorldSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RedisWorldReadSchedulingTest {
    @Test
    @SuppressWarnings("unchecked")
    void decodesWorldJsonAwayFromTheRedisEventLoop() throws Exception {
        var owner = UUID.randomUUID();
        var world = new WorldSnapshot();
        world.setUserId(owner);
        var mapper = spy(new ObjectMapper().findAndRegisterModules());
        String json = mapper.writeValueAsString(world);
        var decodingThread = new AtomicReference<String>();
        doAnswer(call -> {
            decodingThread.set(Thread.currentThread().getName());
            return call.callRealMethod();
        }).when(mapper).readTree(json);
        ReactiveRedisTemplate<String, String> redis = mock(ReactiveRedisTemplate.class);
        ReactiveValueOperations<String, String> values = mock(ReactiveValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        var redisLoop = Schedulers.newSingle("synthetic-redis-event-loop");
        try {
            when(values.get("world:" + owner)).thenReturn(Mono.just(json).publishOn(redisLoop));
            var loaded = new RedisWorldRepository(redis, mapper).findByUserId(owner).block(Duration.ofSeconds(5));
            assertThat(loaded).isNotNull();
            assertThat(loaded.getUserId()).isEqualTo(owner);
            assertThat(decodingThread.get()).startsWith("boundedElastic-");
        } finally {
            redisLoop.dispose();
        }
    }
}
