package com.footballmanager.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import static org.assertj.core.api.Assertions.assertThat;

class RedisConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(RedisConfig.class);

    @Test
    void mapsConfiguredRedisDatabaseToConnectionFactory() {
        contextRunner
            .withPropertyValues(
                "spring.data.redis.host=127.0.0.1",
                "spring.data.redis.port=6379",
                "spring.data.redis.password=test-only-password",
                "spring.data.redis.database=15",
                "spring.data.redis.ssl.enabled=false")
            .run(context -> {
                LettuceConnectionFactory factory = context.getBean(LettuceConnectionFactory.class);
                assertThat(factory.getStandaloneConfiguration().getDatabase()).isEqualTo(15);
                assertThat(factory.getStandaloneConfiguration().getHostName()).isEqualTo("127.0.0.1");
            });
    }
}
