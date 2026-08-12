package com.footballmanager.adapters.out.redis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.data.redis.core.ReactiveRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.UUID;
import java.util.function.Consumer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Test-only fault injection against an isolated Redis representation. */
final class WorldV2PhysicalFaultInjection {

    private WorldV2PhysicalFaultInjection() { }

    static MutationEvidence mutateCommittedEnvelope(ReactiveRedisTemplate<String, String> redis,
                                                    ObjectMapper mapper,
                                                    UUID owner,
                                                    String mutation,
                                                    Consumer<ObjectNode> overlayMutation) {
        String key = "world:" + owner;
        String before = redis.opsForValue().get(key).block(Duration.ofSeconds(5));
        if (before == null) throw new IllegalStateException("committed world root is absent");
        try {
            ObjectNode root = (ObjectNode) mapper.readTree(before);
            JsonNode overlay = root.get("overlay");
            if (!(overlay instanceof ObjectNode overlayNode)) {
                throw new IllegalStateException("committed overlay is absent");
            }
            overlayMutation.accept(overlayNode);
            String after = mapper.writeValueAsString(root);
            Duration ttlDuration = redis.getExpire(key).block();
            long ttl = ttlDuration == null ? -2 : ttlDuration.getSeconds();
            Duration retention = ttl <= 0 ? Duration.ofMinutes(5) : Duration.ofSeconds(ttl);
            Boolean written = redis.opsForValue().set(key, after, retention).block(Duration.ofSeconds(5));
            if (!Boolean.TRUE.equals(written)) throw new IllegalStateException("fault injection write failed");
            MutationEvidence evidence = new MutationEvidence(sha256(key), sha256(before), sha256(after), mutation,
                    "string", ttl, true, false);
            record(mapper, evidence);
            return evidence;
        } catch (Exception error) {
            throw new IllegalStateException("unable to persist isolated Redis fault", error);
        }
    }

    private static synchronized void record(ObjectMapper mapper, MutationEvidence evidence) {
        try {
            Path file = Path.of("target", "world-v2-physical-control-evidence.ndjson");
            Files.createDirectories(file.getParent());
            Files.writeString(file, mapper.writeValueAsString(evidence) + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception error) {
            throw new IllegalStateException("unable to record physical fault evidence", error);
        }
    }

    static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    record MutationEvidence(String keyHash, String beforeChecksum, String afterChecksum,
                            String mutation, String redisType, long ttlSeconds,
                            boolean redisTouched, boolean inMemoryOnlyMutation) {
        boolean malformedPersistedState() { return !beforeChecksum.equals(afterChecksum); }
    }
}
