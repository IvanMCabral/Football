package com.footballmanager.infrastructure.observability;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Request-scoped, sanitized timing evidence for the public round-start path.
 *
 * <p>The trace deliberately contains timings and counters only.  It is not a
 * debug endpoint and never stores authentication, payloads, or domain data.
 * Missing sub-stage instrumentation is represented as {@code -1} rather than
 * being mistaken for a zero-cost operation.</p>
 */
@Slf4j
public final class MatchStartRequestTrace {

    private final String requestId;
    private final long startedNanos;
    private final Map<String, Long> stages = new LinkedHashMap<>();
    private final AtomicBoolean completed = new AtomicBoolean();
    private UUID roundId;
    private boolean metadataCacheHit;
    private long redisGetCount;
    private long redisGetMs = -1;
    private long redisSetCount;
    private long redisSetMs = -1;
    private long serializationMs = -1;
    private long deserializationMs = -1;
    private long postgresQueryCount;
    private long postgresQueryMs = -1;
    private long responseBytes = -1;
    private long motorConsultableOffsetMs = -1;
    private long streamAvailableOffsetMs = -1;

    private MatchStartRequestTrace(String requestId) {
        this.requestId = sanitize(requestId);
        this.startedNanos = System.nanoTime();
    }

    public static MatchStartRequestTrace create(String requestId) {
        return new MatchStartRequestTrace(requestId);
    }

    public static MatchStartRequestTrace create() {
        return create(null);
    }

    public static String sanitize(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return "ms-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        }
        String normalized = candidate.replaceAll("[^A-Za-z0-9_-]", "");
        if (normalized.isBlank()) {
            return "ms-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        }
        return normalized.substring(0, Math.min(32, normalized.length()));
    }

    public String requestId() {
        return requestId;
    }

    public void roundId(UUID value) {
        this.roundId = value;
    }

    public void metadataCacheHit(boolean value) {
        this.metadataCacheHit = value;
    }

    public void mark(String name, long started) {
        stages.put(name, elapsed(started));
    }

    public void duration(String name, long millis) {
        stages.put(name, Math.max(0, millis));
    }

    public void redisGet(long count, long millis) {
        redisGetCount = count;
        redisGetMs = Math.max(0, millis);
    }

    public void redisSet(long count, long millis) {
        redisSetCount = count;
        redisSetMs = Math.max(0, millis);
    }

    public void serialization(long millis) {
        serializationMs = Math.max(0, millis);
    }

    public void deserialization(long millis) {
        deserializationMs = Math.max(0, millis);
    }

    public void postgres(long count, long millis) {
        postgresQueryCount = count;
        postgresQueryMs = Math.max(0, millis);
    }

    public void responseBytes(long value) {
        responseBytes = value;
    }

    public void motorConsultable() {
        motorConsultableOffsetMs = elapsed(startedNanos);
    }

    public void streamAvailable() {
        streamAvailableOffsetMs = elapsed(startedNanos);
    }

    public void complete(boolean success) {
        if (!completed.compareAndSet(false, true)) {
            return;
        }
        stages.putIfAbsent("totalMs", elapsed(startedNanos));
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("requestId", requestId);
        fields.put("roundId", roundId == null ? "unknown" : roundId.toString());
        fields.put("success", success);
        fields.put("totalMs", stages.get("totalMs"));
        fields.put("authMs", stage("authMs"));
        fields.put("careerLoadMs", stage("careerLoadMs"));
        fields.put("redisGetCount", redisGetCount);
        fields.put("redisGetMs", redisGetMs);
        fields.put("redisSetCount", redisSetCount);
        fields.put("redisSetMs", redisSetMs);
        fields.put("redisSerializationMs", serializationMs);
        fields.put("redisDeserializationMs", deserializationMs);
        fields.put("postgresQueryCount", postgresQueryCount);
        fields.put("postgresQueryMs", postgresQueryMs);
        fields.put("lineupLoadMs", stage("lineupLoadMs"));
        fields.put("lineupValidationMs", stage("lineupValidationMs"));
        fields.put("fixturesLoadMs", stage("fixturesLoadMs"));
        fields.put("teamsLoadMs", stage("teamsLoadMs"));
        fields.put("playersLoadMs", stage("playersLoadMs"));
        fields.put("contextBuildMs", stage("contextBuildMs"));
        fields.put("engineCreationMs", stage("engineCreationMs"));
        fields.put("engineRegistrationMs", stage("engineRegistrationMs"));
        fields.put("initialStatePersistenceMs", stage("initialStatePersistenceMs"));
        fields.put("responseMappingMs", stage("responseMappingMs"));
        fields.put("responseSerializationMs", stage("responseSerializationMs"));
        fields.put("responseBytes", responseBytes);
        fields.put("metadataCache", metadataCacheHit ? "hit" : "miss");
        fields.put("motorConsultableMs", motorConsultableOffsetMs);
        fields.put("streamAvailableMs", streamAvailableOffsetMs);
        log.info("[MATCH-START-TRACE] {}", fields);
    }

    private long stage(String name) {
        return stages.getOrDefault(name, -1L);
    }

    private static long elapsed(long started) {
        return Math.max(0, (System.nanoTime() - started) / 1_000_000L);
    }
}
