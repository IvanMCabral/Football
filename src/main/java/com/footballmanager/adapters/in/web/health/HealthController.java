package com.footballmanager.adapters.in.web.health;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/health")
@RequiredArgsConstructor
public class HealthController {

    private final R2dbcEntityTemplate r2dbc;
    private final ReactiveRedisTemplate<String, String> redisTemplate;

    @GetMapping
    public Mono<ResponseEntity<Map<String, Object>>> health() {
        Mono<Boolean> db = r2dbc.getDatabaseClient()
            .sql("SELECT 1")
            .fetch()
            .first()
            .map(row -> true)
            .onErrorReturn(false);

        Mono<Boolean> redis = redisTemplate.hasKey("__manager_healthcheck__")
            .map(ignored -> true)
            .onErrorReturn(false);

        return Mono.zip(db, redis)
            .map(tuple -> {
                boolean dbUp = tuple.getT1();
                boolean redisUp = tuple.getT2();
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("status", dbUp && redisUp ? "UP" : "DOWN");
                body.put("database", dbUp ? "UP" : "DOWN");
                body.put("redis", redisUp ? "UP" : "DOWN");
                return dbUp && redisUp
                    ? ResponseEntity.ok(body)
                    : ResponseEntity.status(503).body(body);
            });
    }
}
