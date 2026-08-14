package com.footballmanager.infrastructure.world.canary;

import com.footballmanager.adapters.out.redis.CanonicalWorldCatalogFingerprint;
import com.footballmanager.application.service.world.WorldMigrationReferenceInventory;
import com.footballmanager.application.service.world.WorldReferenceGraph;
import com.footballmanager.application.service.world.WorldStorageMigrationExecutor;
import com.footballmanager.application.service.world.DurableBoundaryClassification;
import com.footballmanager.application.service.world.DurablePersistenceBoundary;
import com.footballmanager.application.service.world.canary.WorldV2CanarySourceProbe;
import com.footballmanager.domain.model.repository.CareerRepository;
import com.footballmanager.domain.ports.out.world.CanonicalWorldCatalogSource;
import com.footballmanager.domain.model.entity.WorldSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.UUID;

/** Uses the normal product read ports to build the canary's source proof. */
@Component
@Profile("world-v2-canary")
@ConditionalOnProperty(name = "world.v2.canary.enabled", havingValue = "true")
@DurableBoundaryClassification(
        value = DurablePersistenceBoundary.Classification.OUT_OF_SCOPE_NON_WORLD_IDENTITY,
        reason = "read-only operational source probe; never a durable world writer")
public final class ProductWorldV2CanarySourceProbe implements WorldV2CanarySourceProbe {

    private final WorldStorageMigrationExecutor executor;
    private final CareerRepository careerRepository;
    private final WorldMigrationReferenceInventory references;
    private final CanonicalWorldCatalogSource canonicalSource;
    private final CanonicalWorldCatalogFingerprint fingerprint;
    private final ReactiveRedisTemplate<String, String> redis;
    private final ObjectMapper objectMapper;

    public ProductWorldV2CanarySourceProbe(WorldStorageMigrationExecutor executor,
                                           CareerRepository careerRepository,
                                           WorldMigrationReferenceInventory references,
                                           CanonicalWorldCatalogSource canonicalSource,
                                           CanonicalWorldCatalogFingerprint fingerprint,
                                           @Qualifier("reactiveRedisTemplate") ReactiveRedisTemplate<String, String> redis,
                                           ObjectMapper objectMapper) {
        this.executor = executor;
        this.careerRepository = careerRepository;
        this.references = references;
        this.canonicalSource = canonicalSource;
        this.fingerprint = fingerprint;
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<SourceSnapshot> inspect(UUID ownerId) {
        return executor.inspect(ownerId)
                .switchIfEmpty(Mono.error(new IllegalStateException("selected owner world is missing")))
                .flatMap(inspection -> careerRepository.findById(ownerId.toString())
                        .defaultIfEmpty(Optional.empty())
                        .zipWith(canonicalSource.rebuild(ownerId))
                        .flatMap(tuple -> {
                            Optional<?> career = tuple.getT1();
                            WorldReferenceGraph graph = career.isEmpty()
                                    ? WorldReferenceGraph.empty()
                                    : references.discover((com.footballmanager.domain.model.entity.CareerSave)
                                            career.orElseThrow());
                            int referenceCount = graph.teamReferences().values().stream()
                                    .mapToInt(java.util.Set::size).sum()
                                    + graph.playerReferences().values().stream()
                                    .mapToInt(java.util.Set::size).sum();
                            boolean ownerMatch = ownerId.equals(inspection.ownerId())
                                    && inspection.snapshot() != null
                                    && ownerId.equals(inspection.snapshot().getUserId());
                            String canonicalFingerprint = fingerprint.fingerprint(tuple.getT2());
                            String key = "world-catalog:v2:" + canonicalFingerprint;
                            return redis.hasKey(key).flatMap(present -> {
                                if (!present) return Mono.just(new CatalogState(false, true));
                                return redis.opsForValue().get(key)
                                        .flatMap(raw -> catalogIsValid(key, canonicalFingerprint, raw))
                                        .defaultIfEmpty(false)
                                        .map(valid -> new CatalogState(true, valid));
                            }).map(catalog -> new SourceSnapshot(inspection.state(),
                                            inspection.sourceChecksum(), ownerMatch, career.isEmpty(),
                                            referenceCount, catalog.compatible(), catalog.present(),
                                            canonicalFingerprint));
                        }));
    }

    private Mono<Boolean> catalogIsValid(String key, String expectedFingerprint, String raw) {
        return Mono.fromCallable(() -> objectMapper.readValue(raw, WorldSnapshot.class))
                .map(snapshot -> expectedFingerprint.equals(fingerprint.fingerprint(snapshot)))
                .flatMap(valid -> redis.getExpire(key)
                        .map(ttl -> valid && ttl != null && !ttl.isZero() && !ttl.isNegative()))
                .onErrorReturn(false);
    }

    private record CatalogState(boolean present, boolean compatible) { }
}
