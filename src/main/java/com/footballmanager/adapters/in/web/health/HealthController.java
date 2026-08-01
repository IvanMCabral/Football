package com.footballmanager.adapters.in.web.health;

import java.util.LinkedHashMap;
import java.util.Map;

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

    private final DatabaseHealthProbe databaseHealthProbe;
    private final RedisHealthProbe redisHealthProbe;

    @GetMapping
    public Mono<ResponseEntity<Map<String, Object>>> health() {
        return readiness();
    }

    @GetMapping("/readiness")
    public Mono<ResponseEntity<Map<String, Object>>> readiness() {
        Mono<Boolean> db = databaseHealthProbe.isAvailable();
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

}
