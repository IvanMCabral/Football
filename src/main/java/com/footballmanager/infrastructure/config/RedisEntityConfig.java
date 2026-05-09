package com.footballmanager.infrastructure.config;

import com.footballmanager.infrastructure.persistence.entity.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
@RequiredArgsConstructor
public class RedisEntityConfig {

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Bean
    public ReactiveRedisTemplate<String, LeagueEntity> leagueEntityRedisTemplate(
            ReactiveRedisConnectionFactory connectionFactory) {
        return createTemplate(connectionFactory, LeagueEntity.class);
    }

    @Bean
    public ReactiveRedisTemplate<String, GameEntity> gameEntityRedisTemplate(
            ReactiveRedisConnectionFactory connectionFactory) {
        return createTemplate(connectionFactory, GameEntity.class);
    }

    @Bean
    public ReactiveRedisTemplate<String, PlayerEntity> playerEntityRedisTemplate(
            ReactiveRedisConnectionFactory connectionFactory) {
        return createTemplate(connectionFactory, PlayerEntity.class);
    }

    @Bean
    public ReactiveRedisTemplate<String, MatchEntity> matchEntityRedisTemplate(
            ReactiveRedisConnectionFactory connectionFactory) {
        return createTemplate(connectionFactory, MatchEntity.class);
    }

    @Bean
    public ReactiveRedisTemplate<String, TeamEntity> teamEntityRedisTemplate(
            ReactiveRedisConnectionFactory connectionFactory) {
        return createTemplate(connectionFactory, TeamEntity.class);
    }

    @Bean
    public ReactiveRedisTemplate<String, TeamSquadEntity> teamSquadEntityRedisTemplate(
            ReactiveRedisConnectionFactory connectionFactory) {
        return createTemplate(connectionFactory, TeamSquadEntity.class);
    }

    @Bean
    public ReactiveRedisTemplate<String, LeagueTeamEntity> leagueTeamEntityRedisTemplate(
            ReactiveRedisConnectionFactory connectionFactory) {
        return createTemplate(connectionFactory, LeagueTeamEntity.class);
    }

    @Bean
    public ReactiveRedisTemplate<String, com.footballmanager.application.service.simulation.v24.V24DetailedMatchData> v24DetailedMatchDataRedisTemplate(
            ReactiveRedisConnectionFactory connectionFactory) {
        return createTemplate(connectionFactory,
                com.footballmanager.application.service.simulation.v24.V24DetailedMatchData.class);
    }

    private <T> ReactiveRedisTemplate<String, T> createTemplate(
            ReactiveRedisConnectionFactory connectionFactory, Class<T> entityClass) {
        StringRedisSerializer keySerializer = new StringRedisSerializer();
        Jackson2JsonRedisSerializer<T> valueSerializer = new Jackson2JsonRedisSerializer<>(entityClass);
        valueSerializer.setObjectMapper(objectMapper);
        RedisSerializationContext<String, T> context = RedisSerializationContext
                .<String, T>newSerializationContext(keySerializer)
                .value(valueSerializer)
                .build();
        return new ReactiveRedisTemplate<>(connectionFactory, context);
    }
}
