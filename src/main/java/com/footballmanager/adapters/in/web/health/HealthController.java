package com.footballmanager.adapters.in.web.health;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
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

    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(2);
    private final R2dbcEntityTemplate r2dbc;
    private final RedisHealthProbe redisHealthProbe;

    @GetMapping
    public Mono<ResponseEntity<Map<String, Object>>> health() {
        return readiness();
    }

    @GetMapping("/readiness")
    public Mono<ResponseEntity<Map<String, Object>>> readiness() {
        Mono<Boolean> db = databaseIsAvailable();
        Mono<Boolean> redis = redisHealthProbe.isAvailable();

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

    @GetMapping("/liveness")
    public Mono<ResponseEntity<Map<String, Object>>> liveness() {
        return Mono.just(ResponseEntity.ok(Map.of("status", "UP")));
    }

    private Mono<Boolean> databaseIsAvailable() {
        return r2dbc.getDatabaseClient()
            .sql("SELECT 1")
            .fetch()
            .first()
            .map(row -> true)
            .timeout(PROBE_TIMEOUT)
            .onErrorReturn(false);
    }

}
