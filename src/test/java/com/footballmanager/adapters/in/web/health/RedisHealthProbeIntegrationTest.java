package com.footballmanager.adapters.in.web.health;

import com.footballmanager.infrastructure.config.RedisConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = {RedisConfig.class, RedisHealthProbe.class})
@ActiveProfiles("test")
class RedisHealthProbeIntegrationTest {

    @Autowired
    private RedisHealthProbe redisHealthProbe;

    @Autowired
    private ReactiveRedisTemplate<String, String> redisTemplate;

    @Test
    void reportsUpWithoutPreExistingHealthKeyAndDoesNotLeaveProbeGarbage() {
        StepVerifier.create(redisTemplate.keys("__manager_healthcheck__:*").collectList())
            .assertNext(keys -> assertThat(keys).isEmpty())
            .verifyComplete();

        StepVerifier.create(redisHealthProbe.isAvailable())
            .expectNext(true)
            .verifyComplete();

        StepVerifier.create(redisTemplate.keys("__manager_healthcheck__:*").collectList())
            .assertNext(keys -> assertThat(keys).isEmpty())
            .verifyComplete();
    }
}
