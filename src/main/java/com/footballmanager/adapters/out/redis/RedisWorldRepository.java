package com.footballmanager.adapters.out.redis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.footballmanager.domain.model.entity.WorldSnapshot;
import com.footballmanager.domain.model.entity.WorldPlayer;
import com.footballmanager.domain.model.entity.WorldTeam;
import com.footballmanager.domain.model.entity.WorldSnapshotOverlay;
import com.footballmanager.domain.ports.out.world.WorldSnapshotRepository;
import com.footballmanager.domain.ports.out.world.CanonicalWorldCatalogSource;
import com.footballmanager.infrastructure.persistence.redis.CareerOwnershipTouchService;
import com.footballmanager.application.observability.ReloadWorldTiming;
import com.footballmanager.application.service.world.WorldMigrationAdmission;
import com.footballmanager.application.service.world.WorldStorageMigrationExecutor;
import com.footballmanager.application.service.world.WorldStoragePhysicalCapacityModel;
import com.footballmanager.domain.model.valueobject.CareerWriteContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Repositorio para WorldSnapshot en Redis.
 * Key: world:{userId}
 *
 * WorldSnapshot se crea una vez y luego se actualiza con una retención acotada.
 * Los catálogos canónicos compartidos usan un TTL independiente y renovable.
 */
@Repository
public class RedisWorldRepository implements WorldSnapshotRepository, WorldStorageMigrationExecutor {

    private static final Logger log = LoggerFactory.getLogger(RedisWorldRepository.class);
    private static final String KEY_PREFIX = "world:";
    private static final String CATALOG_KEY_PREFIX = "world-catalog:v2:";
    private static final Duration MIN_CATALOG_CAPACITY_CREDIT_TTL = Duration.ofMinutes(5);
    private static final RedisScript<Long> COMPARE_AND_REPLACE = RedisScript.of("""
            if redis.call('GET', KEYS[1]) ~= ARGV[1] then
              return 0
            end
            redis.call('SET', KEYS[1], ARGV[2], 'PX', ARGV[3])
            return 1
            """, Long.class);

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final CareerOwnershipTouchService ownershipTouchService;
    private final CanonicalWorldCatalogFingerprint catalogFingerprint;
    private final CanonicalWorldCatalogSource canonicalCatalogSource;
    private final WorldStoragePhysicalCapacityModel physicalCapacityModel =
            new WorldStoragePhysicalCapacityModel();
    @Value("${app.redis.world-ttl:30d}")
    private Duration worldTtl;
    @Value("${app.redis.world-catalog-ttl:365d}")
    private Duration catalogTtl;
    @Value("${app.redis.world-storage-version:2}")
    private int storageVersion;

    @Autowired
    public RedisWorldRepository(@Qualifier("reactiveRedisTemplate") ReactiveRedisTemplate<String, String> redisTemplate,
                                ObjectMapper objectMapper,
                                CareerOwnershipTouchService ownershipTouchService,
                                CanonicalWorldCatalogFingerprint catalogFingerprint,
                                CanonicalWorldCatalogSource canonicalCatalogSource) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.ownershipTouchService = ownershipTouchService;
        this.catalogFingerprint = catalogFingerprint;
        this.canonicalCatalogSource = canonicalCatalogSource;
    }

    public RedisWorldRepository(@Qualifier("reactiveRedisTemplate") ReactiveRedisTemplate<String, String> redisTemplate,
                                ObjectMapper objectMapper) {
        this(redisTemplate, objectMapper, null, new CanonicalWorldCatalogFingerprint(objectMapper), null);
    }

    /**
     * Genera la key de Redis para el WorldSnapshot de un usuario
     */
    private String generateKey(UUID userId) {
        return KEY_PREFIX + userId.toString();
    }

    /**
     * Guarda o actualiza el WorldSnapshot
     */
    public Mono<WorldSnapshot> save(WorldSnapshot snapshot) {
        if (ownershipTouchService != null) {
            return Mono.error(new IllegalStateException("career world writer requires lifecycle context"));
        }
        return saveInitial(snapshot);
    }

    @Override
    public Mono<WorldSnapshot> saveInitial(WorldSnapshot snapshot) {
        String key = generateKey(snapshot.getUserId());
        return Mono.deferContextual(context -> {
            boolean canonicalBootstrap = context.getOrDefault(
                    WorldSnapshotRepository.CANONICAL_BOOTSTRAP_CONTEXT_KEY, false);
            ReloadWorldTiming timing = context.getOrDefault(ReloadWorldTiming.CONTEXT_KEY, null);
            Mono<String> serialize = Mono.defer(() -> {
                long started = System.nanoTime();
                return Mono.fromCallable(() -> objectMapper.writeValueAsString(snapshot))
                        .doOnSuccess(json -> {
                            if (timing != null) {
                                timing.serializedBytes(json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
                                timing.record("serializationMs", started);
                            }
                        });
            });
            Mono<WorldSnapshot> persistLegacy = serialize.flatMap(json -> {
                long started = System.nanoTime();
                Mono<Boolean> write = redisTemplate.opsForValue().set(key, json, requireWorldTtl());
                return write.doOnSuccess(ignored -> {
                            if (timing != null) timing.record("redisSaveMs", started);
                        })
                        .thenReturn(snapshot);
            });
            Mono<WorldSnapshot> persist = storageVersion >= WorldSnapshotOverlay.STORAGE_VERSION
                    ? persistV2(snapshot, timing, !canonicalBootstrap)
                    : persistLegacy;
            if (ownershipTouchService == null) return persist;
            long ownershipStarted = System.nanoTime();
            return ownershipTouchService.initializeWorld(snapshot.getUserId(), () -> persist)
                    .doOnSuccess(ignored -> {
                        if (timing != null) timing.record("ownershipInitMs", ownershipStarted);
                    });
        });
    }

    /** Career-derived world update. It cannot degrade to first-time initialization. */
    @Override
    public Mono<WorldSnapshot> saveWithContext(CareerWriteContext context, WorldSnapshot snapshot) {
        if (context == null || snapshot == null || !context.ownerId().equals(snapshot.getUserId())) {
            return Mono.error(new IllegalArgumentException("career world context does not match snapshot"));
        }
        String key = generateKey(snapshot.getUserId());
        Mono<WorldSnapshot> persist = storageVersion >= WorldSnapshotOverlay.STORAGE_VERSION
                ? persistV2(snapshot, null, true)
                : Mono.fromCallable(() -> objectMapper.writeValueAsString(snapshot))
                    .flatMap(json -> redisTemplate.opsForValue().set(key, json, requireWorldTtl()))
                    .thenReturn(snapshot);
        return ownershipTouchService == null
                ? Mono.error(new IllegalStateException("career ownership service is required"))
                : ownershipTouchService.touchBeforeWrite(context, () -> persist);
    }

    /**
     * Busca el WorldSnapshot por userId
     */
    public Mono<WorldSnapshot> findByUserId(UUID userId) {
        String key = generateKey(userId);

        return redisTemplate.opsForValue().get(key)
                .flatMap(json -> decodeSnapshot(json, userId))
                .onErrorResume(e -> e instanceof WorldStorageFormatException
                        ? Mono.error(e)
                        : Mono.empty());
    }

    /**
     * Verifica si existe un WorldSnapshot para el userId
     */
    public Mono<Boolean> existsByUserId(UUID userId) {
        String key = generateKey(userId);
        return redisTemplate.hasKey(key);
    }

    /**
     * Elimina el WorldSnapshot (solo para testing o reset manual)
     */
    public Mono<Boolean> deleteByUserId(UUID userId) {
        String key = generateKey(userId);
        return redisTemplate.delete(key)
                .map(count -> count > 0);
    }

    private Mono<WorldSnapshot> persistV2(WorldSnapshot snapshot, ReloadWorldTiming timing,
                                          boolean rebuildCanonicalAuthority) {
        Mono<WorldSnapshot> canonicalSource = !rebuildCanonicalAuthority || canonicalCatalogSource == null
                ? Mono.just(canonicalPart(snapshot))
                : canonicalCatalogSource.rebuild(snapshot.getUserId()).map(this::canonicalPart);
        long redisStarted = System.nanoTime();
        return canonicalSource.flatMap(canonical -> Mono.fromCallable(() -> {
            String fingerprint = catalogFingerprint.fingerprint(canonical);
            String catalogKey = catalogKey(fingerprint);
            WorldSnapshotOverlay overlay = WorldSnapshotOverlay.fromSnapshot(snapshot, canonical);
            String overlayChecksum = storedOverlayChecksum(overlay);
            return new WorldStorageEnvelope(WorldSnapshotOverlay.STORAGE_VERSION, "COMMITTED",
                    snapshot.getUserId(), catalogKey, fingerprint, overlayChecksum, overlay);
        }).flatMap(env -> Mono.fromCallable(() -> objectMapper.writeValueAsString(canonical))
                .flatMap(canonicalJson -> ensureCatalog(env.catalogKey(), env.catalogFingerprint(), canonicalJson))
                .then(Mono.fromCallable(() -> objectMapper.writeValueAsString(env)))
                .flatMap(json -> redisTemplate.opsForValue().set(generateKey(snapshot.getUserId()), json, requireWorldTtl()))))
                .doOnSuccess(ignored -> {
                    if (timing != null) timing.record("redisSaveMs", redisStarted);
                })
                .thenReturn(snapshot);
    }

    private String catalogKey(String fingerprint) {
        return CATALOG_KEY_PREFIX + fingerprint;
    }

    private Mono<WorldSnapshot> decodeSnapshot(String json, UUID requestedOwner) {
        return Mono.fromCallable(() -> objectMapper.readTree(json))
                .onErrorMap(error -> error instanceof WorldStorageFormatException
                        ? error
                        : new WorldStorageFormatException("Invalid world storage payload"))
                .flatMap(node -> {
                    if (node.has("migrationState")) {
                        return decodePreparedMigration(node, requestedOwner);
                    }
                    if (!node.has("storageVersion")) {
                        return Mono.fromCallable(() -> objectMapper.treeToValue(node, WorldSnapshot.class))
                                .flatMap(snapshot -> requestedOwner.equals(snapshot.getUserId())
                                        ? Mono.just(snapshot)
                                        : Mono.error(new WorldStorageFormatException("Legacy world owner mismatch")));
                    }
                    int version = node.path("storageVersion").asInt(-1);
                    if (version != WorldSnapshotOverlay.STORAGE_VERSION) {
                        return Mono.error(new WorldStorageFormatException("Unsupported world storage version"));
                    }
                    return Mono.fromCallable(() -> objectMapper.treeToValue(node, WorldStorageEnvelope.class))
                            .flatMap(envelope -> {
                                if (!requestedOwner.equals(envelope.ownerId())) {
                                    return Mono.error(new WorldStorageFormatException("World owner mismatch"));
                                }
                                if (!"COMMITTED".equals(envelope.state())) {
                                    return Mono.error(new WorldStorageFormatException("World storage is not committed"));
                                }
                                return verifyOverlayChecksum(envelope, node.path("overlay"))
                                        .then(loadOrRecoverCatalog(envelope))
                                        .flatMap(catalogJson -> Mono.fromCallable(() -> objectMapper.readValue(
                                                catalogJson, WorldSnapshot.class)))
                                        .map(canonical -> envelope.overlay().applyTo(canonical));
                            });
                })
                .onErrorMap(error -> error instanceof WorldStorageFormatException
                        ? error
                        : new WorldStorageFormatException("Invalid world storage payload"));
    }

    private WorldSnapshot canonicalPart(WorldSnapshot source) {
        WorldSnapshot canonical = new WorldSnapshot();
        canonical.setUserId(null);
        canonical.setCreatedAt(java.time.Instant.EPOCH);
        canonical.setLastUpdated(java.time.Instant.EPOCH);
        canonical.setLeagues(source.getLeagues() == null ? java.util.List.of() : source.getLeagues());
        Map<String, WorldTeam> teams = new LinkedHashMap<>();
        if (source.getWorldTeams() != null) {
            source.getWorldTeams().forEach((id, team) -> {
                if (team != null && team.getOrigin() == WorldTeam.WorldTeamOrigin.REAL
                        && team.getRealTeamId() != null) {
                    WorldTeam copy = WorldTeam.fromRealTeam(team.getRealTeamId(), team.getRealLeagueId(),
                            team.getName(), team.getCountry(), team.getCity(), team.getBaseBudget(),
                            team.getBaseFormation(), team.getDivision());
                    teams.put(copy.getWorldTeamId(), copy);
                }
            });
        }
        canonical.setWorldTeams(teams);
        Map<String, WorldPlayer> players = new LinkedHashMap<>();
        if (source.getWorldPlayers() != null) {
            source.getWorldPlayers().forEach((id, player) -> {
                if (player != null && player.getOrigin() == WorldPlayer.WorldPlayerOrigin.REAL
                        && player.getRealPlayerId() != null) {
                    WorldPlayer copy = WorldPlayer.fromCanonicalPlayer(source.getUserId(), player.getRealPlayerId(),
                            player.getWorldTeamId(), player.getName(), player.getAge(), player.getPosition(),
                            player.getBaseAttack(), player.getBaseDefense(), player.getBaseTechnique(),
                            player.getBaseSpeed(), player.getBaseStamina(), player.getBaseMentality(),
                            player.getBaseMarketValue());
                    copy.setHeightCm(player.getHeightCm());
                    copy.setSkillLevels(player.getSkillLevels());
                    copy.setSpecialTraits(player.getSpecialTraits());
                    players.put(copy.getWorldPlayerId(), copy);
                }
            });
        }
        canonical.setWorldPlayers(players);
        canonical.setWorldPlayerAliases(Map.of());
        return canonical;
    }

    private Mono<String> ensureCatalog(String key, String expectedFingerprint, String canonicalJson) {
        return redisTemplate.opsForValue().get(key)
                .flatMap(existing -> redisTemplate.getExpire(key).defaultIfEmpty(Duration.ofMillis(-2))
                        .flatMap(ttl -> validateCatalog(key, expectedFingerprint, existing)
                                .then(Mono.defer(() -> positiveTtl(ttl)
                                        ? redisTemplate.expire(key, requireCatalogTtl()).thenReturn(existing)
                                        : writeCanonicalCatalog(key, expectedFingerprint, canonicalJson)))
                                .onErrorResume(WorldStorageFormatException.class,
                                        ignored -> writeCanonicalCatalog(key, expectedFingerprint, canonicalJson))))
                .switchIfEmpty(Mono.defer(() -> writeCanonicalCatalog(key, expectedFingerprint, canonicalJson)));
    }

    private Mono<String> writeCanonicalCatalog(String key, String expectedFingerprint, String canonicalJson) {
        return validateCatalog(key, expectedFingerprint, canonicalJson)
                .then(redisTemplate.opsForValue().set(key, canonicalJson, requireCatalogTtl()))
                .flatMap(written -> Boolean.TRUE.equals(written)
                        ? Mono.just(canonicalJson)
                        : Mono.error(new WorldStorageFormatException("World catalog write failed")));
    }

    private Mono<Void> validateCatalog(String key, String expectedFingerprint, String catalogJson) {
        return Mono.fromCallable(() -> objectMapper.readValue(catalogJson, WorldSnapshot.class))
                .flatMap(catalog -> {
                    String actual = catalogFingerprint.fingerprint(catalog);
                    if (!expectedFingerprint.equals(actual) || !key.equals(catalogKey(actual))) {
                        return Mono.error(new WorldStorageFormatException("World catalog fingerprint mismatch"));
                    }
                    return Mono.<Void>empty();
                })
                .onErrorMap(error -> error instanceof WorldStorageFormatException
                        ? error : new WorldStorageFormatException("Invalid world catalog payload"));
    }

    private Mono<String> loadOrRecoverCatalog(WorldStorageEnvelope envelope) {
        return redisTemplate.opsForValue().get(envelope.catalogKey())
                .flatMap(json -> validateCatalog(envelope.catalogKey(), envelope.catalogFingerprint(), json)
                        .then(redisTemplate.expire(envelope.catalogKey(), requireCatalogTtl()))
                        .thenReturn(json))
                .switchIfEmpty(Mono.defer(() -> recoverCatalog(envelope)));
    }

    private Mono<String> recoverCatalog(WorldStorageEnvelope envelope) {
        if (canonicalCatalogSource == null) {
            return Mono.error(new WorldStorageFormatException("World catalog missing and canonical recovery unavailable"));
        }
        return canonicalCatalogSource.rebuild(envelope.ownerId())
                .map(this::canonicalPart)
                .flatMap(catalog -> Mono.fromCallable(() -> objectMapper.writeValueAsString(catalog))
                        .flatMap(json -> validateCatalog(envelope.catalogKey(), envelope.catalogFingerprint(), json)
                                .then(redisTemplate.opsForValue().set(envelope.catalogKey(), json, requireCatalogTtl()))
                                .thenReturn(json)))
                .onErrorMap(error -> error instanceof WorldStorageFormatException
                        ? error : new WorldStorageFormatException("World catalog recovery failed"));
    }

    private Mono<Void> verifyOverlayChecksum(WorldStorageEnvelope envelope, JsonNode storedOverlay) {
        return Mono.fromCallable(() -> sha256(objectMapper.writeValueAsBytes(storedOverlay)))
                .flatMap(actual -> actual.equals(envelope.overlayChecksum())
                        ? Mono.empty()
                        : Mono.error(new WorldStorageFormatException("World overlay checksum mismatch")));
    }

    private Mono<WorldSnapshot> decodePreparedMigration(JsonNode node, UUID requestedOwner) {
        return Mono.fromCallable(() -> objectMapper.treeToValue(node, PreparedWorldMigrationEnvelope.class))
                .flatMap(prepared -> {
                    if (prepared.storageVersion() != WorldSnapshotOverlay.STORAGE_VERSION
                            || !"PREPARED".equals(prepared.migrationState())
                            || !catalogKey(prepared.catalogFingerprint()).equals(prepared.catalogKey())
                            || !requestedOwner.equals(prepared.ownerId())) {
                        return Mono.error(new WorldStorageFormatException("Invalid prepared world migration"));
                    }
                    return Mono.fromCallable(() -> decompress(prepared.compressedLegacy()))
                            .flatMap(legacyJson -> {
                                if (!sha256(legacyJson.getBytes(StandardCharsets.UTF_8)).equals(prepared.legacyChecksum())) {
                                    return Mono.error(new WorldStorageFormatException("Prepared legacy checksum mismatch"));
                                }
                                return Mono.fromCallable(() -> objectMapper.readValue(legacyJson, WorldSnapshot.class));
                            })
                            .flatMap(legacy -> requestedOwner.equals(legacy.getUserId())
                                    ? Mono.just(legacy)
                                    : Mono.error(new WorldStorageFormatException("Prepared legacy owner mismatch")));
                });
    }

    @Override
    public Mono<SourceInspection> inspect(UUID ownerId) {
        if (ownerId == null) return Mono.error(new WorldStorageFormatException("World owner is required"));
        return redisTemplate.opsForValue().get(generateKey(ownerId))
                .switchIfEmpty(Mono.error(new InspectionException(Status.INVALID_LEGACY, "Legacy world missing")))
                .flatMap(raw -> inspectStoredValue(ownerId, raw));
    }

    private Mono<SourceInspection> inspectStoredValue(UUID ownerId, String raw) {
        return Mono.fromCallable(() -> objectMapper.readTree(raw)).flatMap(node -> {
            if (node.has("migrationState")) {
                return decodePreparedMigration(node, ownerId)
                        .map(snapshot -> new SourceInspection(ownerId, StoredState.PREPARED, snapshot,
                                node.path("legacyChecksum").asText(), utf8Length(raw)))
                        .onErrorMap(error -> new InspectionException(Status.INVALID_V2,
                                "Prepared World V2 state is invalid"));
            }
            if (node.has("storageVersion")) {
                return validateCommittedStrict(node, ownerId)
                        .map(snapshot -> new SourceInspection(ownerId, StoredState.COMMITTED, snapshot,
                                sha256(raw.getBytes(StandardCharsets.UTF_8)), utf8Length(raw)))
                        .onErrorMap(error -> new InspectionException(Status.INVALID_V2,
                                "Committed World V2 state is invalid"));
            }
            return Mono.fromCallable(() -> objectMapper.treeToValue(node, WorldSnapshot.class))
                    .flatMap(snapshot -> ownerId.equals(snapshot.getUserId())
                            ? Mono.just(new SourceInspection(ownerId, StoredState.LEGACY, snapshot,
                                    sha256(raw.getBytes(StandardCharsets.UTF_8)), utf8Length(raw)))
                            : Mono.error(new InspectionException(Status.INVALID_LEGACY,
                                    "Legacy world owner mismatch")))
                    .onErrorMap(error -> error instanceof InspectionException ? error
                            : new InspectionException(Status.INVALID_LEGACY, "Invalid legacy world source"));
        }).onErrorMap(error -> error instanceof InspectionException ? error
                : new InspectionException(Status.INVALID_LEGACY, "Invalid world migration source"));
    }

    @Override
    public Mono<ExecutionResult> execute(WorldMigrationAdmission admission) {
        if (admission == null) return Mono.just(result(Status.INVALID_LEGACY, "Migration admission is required"));
        String ownerKey = generateKey(admission.ownerId());
        return redisTemplate.opsForValue().get(ownerKey)
                .switchIfEmpty(Mono.just(""))
                .flatMap(raw -> raw.isEmpty()
                        ? Mono.just(result(Status.INVALID_LEGACY, "Migration source is missing"))
                        : executeAgainstCurrent(admission, raw))
                .onErrorResume(WorldStorageFormatException.class,
                        error -> Mono.just(result(Status.INVALID_V2, error.getMessage())))
                .onErrorResume(error -> {
                    log.error("World V2 migration operation failed for request owner", error);
                    return Mono.just(result(Status.RETRYABLE_PARTIAL, "Migration operation failed"));
                });
    }

    private Mono<ExecutionResult> executeAgainstCurrent(WorldMigrationAdmission admission, String currentRaw) {
        return Mono.fromCallable(() -> objectMapper.readTree(currentRaw)).flatMap(node -> {
            if (node.has("storageVersion") && !node.has("migrationState")) {
                return validateCommittedStrict(node, admission.ownerId())
                        .map(ignored -> new ExecutionResult(Status.ALREADY_MIGRATED_VALID, 0,
                                utf8Length(currentRaw), admission.currentDatasetBytes(), true,
                                "Committed World V2 is valid"));
            }
            if (node.has("migrationState")) {
                return resumePrepared(admission, currentRaw, node);
            }
            String actualChecksum = sha256(currentRaw.getBytes(StandardCharsets.UTF_8));
            if (!actualChecksum.equals(admission.sourceChecksum())) {
                return Mono.just(result(Status.SOURCE_CHANGED, "Migration source changed before prepare"));
            }
            return Mono.fromCallable(() -> objectMapper.treeToValue(node, WorldSnapshot.class))
                    .flatMap(legacy -> prepareWithFence(admission, currentRaw, legacy));
        });
    }

    private Mono<ExecutionResult> prepareWithFence(WorldMigrationAdmission admission, String legacyRaw,
                                                    WorldSnapshot legacy) {
        if (!admission.ownerId().equals(legacy.getUserId())) {
            return Mono.just(result(Status.INVALID_LEGACY, "Legacy world owner mismatch"));
        }
        return buildMigrationMaterial(admission, legacy, legacyRaw)
                .flatMap(material -> capacityPlan(admission, utf8Length(legacyRaw), material)
                        .flatMap(plan -> {
                            if (!plan.feasible()) {
                                return Mono.just(new ExecutionResult(Status.BLOCKED_CAPACITY,
                                        utf8Length(material.preparedJson()), utf8Length(material.committedJson()),
                                        plan.peakBytes(), true, plan.reason()));
                            }
                            return compareAndReplace(generateKey(admission.ownerId()), legacyRaw,
                                    material.preparedJson(), requireWorldTtl())
                                    .flatMap(replaced -> replaced
                                            ? completePrepared(admission, material.preparedJson(), material.prepared(), plan)
                                            : Mono.just(result(Status.SOURCE_CHANGED,
                                                    "Migration source changed during prepare")));
                        }));
    }

    private Mono<ExecutionResult> resumePrepared(WorldMigrationAdmission admission, String preparedRaw,
                                                  JsonNode node) {
        return Mono.fromCallable(() -> objectMapper.treeToValue(node, PreparedWorldMigrationEnvelope.class))
                .flatMap(prepared -> {
                    if (!validPrepared(prepared, admission.ownerId())
                            || !admission.sourceChecksum().equals(prepared.legacyChecksum())) {
                        return Mono.just(result(Status.SOURCE_CHANGED, "Prepared migration does not match admission"));
                    }
                    return buildCompletion(admission, prepared)
                            .flatMap(completion -> capacityPlan(admission, completion.legacyBytes(),
                                            utf8Length(preparedRaw), utf8Length(completion.committedJson()),
                                            prepared.catalogKey(), utf8Length(completion.catalogJson()))
                                    .flatMap(plan -> plan.feasible()
                                            ? commitPrepared(admission, preparedRaw, prepared, completion, plan)
                                            : Mono.just(new ExecutionResult(Status.BLOCKED_CAPACITY,
                                                    utf8Length(preparedRaw), utf8Length(completion.committedJson()),
                                                    plan.peakBytes(), true, plan.reason()))));
                });
    }

    private Mono<ExecutionResult> completePrepared(WorldMigrationAdmission admission, String preparedRaw,
                                                    PreparedWorldMigrationEnvelope prepared, CapacityPlan plan) {
        return buildCompletion(admission, prepared)
                .flatMap(completion -> commitPrepared(admission, preparedRaw, prepared, completion, plan));
    }

    private Mono<ExecutionResult> commitPrepared(WorldMigrationAdmission admission, String preparedRaw,
                                                  PreparedWorldMigrationEnvelope prepared,
                                                  PreparedCompletion completion, CapacityPlan plan) {
        return ensureCatalog(prepared.catalogKey(), prepared.catalogFingerprint(), completion.catalogJson())
                .then(compareAndReplace(generateKey(admission.ownerId()), preparedRaw,
                        completion.committedJson(), requireWorldTtl()))
                .flatMap(replaced -> {
                    if (!replaced) return Mono.just(result(Status.SOURCE_CHANGED,
                            "Migration source changed before commit"));
                    return redisTemplate.opsForValue().get(generateKey(admission.ownerId()))
                            .switchIfEmpty(Mono.error(new WorldStorageFormatException("Committed world missing")))
                            .flatMap(raw -> Mono.fromCallable(() -> objectMapper.readTree(raw))
                                    .flatMap(node -> validateCommittedStrict(node, admission.ownerId())))
                            .map(ignored -> new ExecutionResult(Status.MIGRATED, utf8Length(preparedRaw),
                                    utf8Length(completion.committedJson()), plan.peakBytes(), true,
                                    "World V2 migration committed and validated"));
                });
    }

    private Mono<PreparedMaterial> buildMigrationMaterial(WorldMigrationAdmission admission,
                                                           WorldSnapshot legacy, String legacyRaw) {
        Mono<WorldSnapshot> source = Mono.just(canonicalPart(admission.canonicalSnapshot()));
        return source.flatMap(canonical -> Mono.fromCallable(() -> {
            String fingerprint = catalogFingerprint.fingerprint(canonical);
            PreparedWorldMigrationEnvelope prepared = new PreparedWorldMigrationEnvelope(
                    WorldSnapshotOverlay.STORAGE_VERSION, "PREPARED", admission.ownerId(),
                    catalogKey(fingerprint), fingerprint, compress(legacyRaw), admission.sourceChecksum());
            WorldStorageEnvelope committed = committedEnvelope(admission.ownerId(), legacy, canonical,
                    prepared.catalogKey(), fingerprint);
            return new PreparedMaterial(prepared, objectMapper.writeValueAsString(prepared),
                    objectMapper.writeValueAsString(canonical), objectMapper.writeValueAsString(committed));
        }));
    }

    private Mono<PreparedCompletion> buildCompletion(WorldMigrationAdmission admission,
                                                       PreparedWorldMigrationEnvelope prepared) {
        if (!validPrepared(prepared, admission.ownerId())) {
            return Mono.error(new WorldStorageFormatException("Invalid prepared migration state"));
        }
        return Mono.fromCallable(() -> {
            String legacyRaw = decompress(prepared.compressedLegacy());
            if (!sha256(legacyRaw.getBytes(StandardCharsets.UTF_8)).equals(prepared.legacyChecksum())) {
                throw new WorldStorageFormatException("Prepared legacy checksum mismatch");
            }
            WorldSnapshot legacy = objectMapper.readValue(legacyRaw, WorldSnapshot.class);
            if (!admission.ownerId().equals(legacy.getUserId())) {
                throw new WorldStorageFormatException("Prepared legacy owner mismatch");
            }
            return new DecodedLegacy(legacy, utf8Length(legacyRaw));
        }).flatMap(decoded -> {
            Mono<WorldSnapshot> source = Mono.just(canonicalPart(admission.canonicalSnapshot()));
            return source.flatMap(canonical -> Mono.fromCallable(() -> {
                String fingerprint = catalogFingerprint.fingerprint(canonical);
                if (!fingerprint.equals(prepared.catalogFingerprint())) {
                    throw new WorldStorageFormatException("Prepared catalog fingerprint mismatch");
                }
                WorldStorageEnvelope committed = committedEnvelope(admission.ownerId(), decoded.snapshot(),
                        canonical, prepared.catalogKey(), fingerprint);
                return new PreparedCompletion(objectMapper.writeValueAsString(canonical),
                        objectMapper.writeValueAsString(committed), decoded.serializedBytes());
            }));
        });
    }

    private WorldStorageEnvelope committedEnvelope(UUID ownerId, WorldSnapshot legacy,
                                                    WorldSnapshot canonical, String key, String fingerprint)
            throws Exception {
        WorldSnapshotOverlay overlay = WorldSnapshotOverlay.fromSnapshot(legacy, canonical);
        String overlayChecksum = storedOverlayChecksum(overlay);
        return new WorldStorageEnvelope(WorldSnapshotOverlay.STORAGE_VERSION, "COMMITTED", ownerId,
                key, fingerprint, overlayChecksum, overlay);
    }

    private Mono<WorldSnapshot> validateCommittedStrict(JsonNode node, UUID ownerId) {
        return Mono.fromCallable(() -> objectMapper.treeToValue(node, WorldStorageEnvelope.class))
                .flatMap(envelope -> {
                    if (envelope.storageVersion() != WorldSnapshotOverlay.STORAGE_VERSION
                            || !"COMMITTED".equals(envelope.state())
                            || !ownerId.equals(envelope.ownerId())
                            || envelope.overlay() == null
                            || !catalogKey(envelope.catalogFingerprint()).equals(envelope.catalogKey())) {
                        return Mono.error(new WorldStorageFormatException("Invalid committed World V2 envelope"));
                    }
                    Mono<Duration> ownerTtl = redisTemplate.getExpire(generateKey(ownerId))
                            .defaultIfEmpty(Duration.ofMillis(-2));
                    Mono<Duration> catalogTtlValue = redisTemplate.getExpire(envelope.catalogKey())
                            .defaultIfEmpty(Duration.ofMillis(-2));
                    Mono<String> catalog = redisTemplate.opsForValue().get(envelope.catalogKey())
                            .switchIfEmpty(Mono.error(new WorldStorageFormatException("Committed world catalog missing")));
                    return verifyOverlayChecksum(envelope, node.path("overlay"))
                            .then(Mono.zip(ownerTtl, catalogTtlValue, catalog))
                            .flatMap(tuple -> {
                                if (!positiveTtl(tuple.getT1()) || !positiveTtl(tuple.getT2())) {
                                    return Mono.error(new WorldStorageFormatException("Committed World V2 TTL is invalid"));
                                }
                                String catalogJson = tuple.getT3();
                                return validateCatalog(envelope.catalogKey(), envelope.catalogFingerprint(), catalogJson)
                                        .then(Mono.fromCallable(() -> objectMapper.readValue(catalogJson, WorldSnapshot.class)))
                                        .map(envelope.overlay()::applyTo);
                            });
                });
    }

    private Mono<Boolean> compareAndReplace(String key, String expected, String replacement, Duration ttl) {
        return redisTemplate.execute(COMPARE_AND_REPLACE, java.util.List.of(key),
                        expected, replacement, Long.toString(ttl.toMillis()))
                .single(0L)
                .map(result -> result == 1L);
    }

    private String storedOverlayChecksum(WorldSnapshotOverlay overlay) throws Exception {
        JsonNode storedShape = objectMapper.readTree(objectMapper.writeValueAsBytes(overlay));
        return sha256(objectMapper.writeValueAsBytes(storedShape));
    }

    private Mono<CapacityPlan> capacityPlan(WorldMigrationAdmission admission, int legacyBytes,
                                            PreparedMaterial material) {
        return capacityPlan(admission, legacyBytes, utf8Length(material.preparedJson()),
                utf8Length(material.committedJson()), material.prepared().catalogKey(),
                utf8Length(material.catalogJson()));
    }

    private Mono<CapacityPlan> capacityPlan(WorldMigrationAdmission admission, int legacyBytes,
                                            long preparedBytes, long committedBytes,
                                            String catalogKey, long catalogBytes) {
        return validCatalogForCapacityCredit(catalogKey).map(catalogValid -> {
            WorldStoragePhysicalCapacityModel.Estimate estimate = physicalCapacityModel.estimate(
                    admission.currentDatasetBytes(), legacyBytes, preparedBytes, committedBytes,
                    catalogBytes, Boolean.TRUE.equals(catalogValid));
            long peak = estimate.physicalPeakBytes();
            long limit = admission.quotaBytes() - admission.safetyMarginBytes()
                    - admission.localAccountingUncertaintyMarginBytes();
            return peak <= limit
                    ? new CapacityPlan(true, peak, "Fits conservative physical quota guard")
                    : new CapacityPlan(false, peak, "Owner-specific physical peak exceeds quota guard");
        });
    }

    private Mono<Boolean> validCatalogForCapacityCredit(String key) {
        String expectedFingerprint = key != null && key.startsWith(CATALOG_KEY_PREFIX)
                ? key.substring(CATALOG_KEY_PREFIX.length()) : "";
        return redisTemplate.opsForValue().get(key)
                .flatMap(existing -> redisTemplate.getExpire(key).defaultIfEmpty(Duration.ofMillis(-2))
                        .flatMap(ttl -> validateCatalog(key, expectedFingerprint, existing)
                                .thenReturn(positiveTtl(ttl)
                                        && ttl.compareTo(MIN_CATALOG_CAPACITY_CREDIT_TTL) >= 0)))
                .onErrorResume(WorldStorageFormatException.class, ignored -> Mono.just(false))
                .defaultIfEmpty(false);
    }

    private static boolean validPrepared(PreparedWorldMigrationEnvelope prepared, UUID ownerId) {
        return prepared != null
                && prepared.storageVersion() == WorldSnapshotOverlay.STORAGE_VERSION
                && "PREPARED".equals(prepared.migrationState())
                && ownerId.equals(prepared.ownerId())
                && prepared.catalogFingerprint() != null
                && (CATALOG_KEY_PREFIX + prepared.catalogFingerprint()).equals(prepared.catalogKey())
                && prepared.compressedLegacy() != null
                && prepared.legacyChecksum() != null;
    }

    private static boolean positiveTtl(Duration ttl) {
        return ttl != null && !ttl.isZero() && !ttl.isNegative();
    }

    private static int utf8Length(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }

    private static ExecutionResult result(Status status, String reason) {
        return new ExecutionResult(status, 0, 0, 0, true, reason);
    }

    private static String compress(String value) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
            gzip.write(value.getBytes(StandardCharsets.UTF_8));
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray());
    }

    private static String decompress(String value) throws Exception {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(value)))) {
            return new String(gzip.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String sha256(byte[] value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value);
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte item : digest) hex.append(String.format("%02x", item));
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private Duration requireWorldTtl() {
        if (worldTtl == null || worldTtl.isZero() || worldTtl.isNegative()) {
            throw new IllegalStateException("Redis world TTL must be positive; immortal worlds are disabled");
        }
        return worldTtl;
    }

    private Duration requireCatalogTtl() {
        if (catalogTtl == null || catalogTtl.isZero() || catalogTtl.isNegative()) {
            throw new IllegalStateException("Redis world catalog TTL must be positive");
        }
        return catalogTtl;
    }

    public record WorldStorageEnvelope(int storageVersion, String state, UUID ownerId,
                                       String catalogKey, String catalogFingerprint,
                                       String overlayChecksum, WorldSnapshotOverlay overlay) {}

    public record PreparedWorldMigrationEnvelope(int storageVersion, String migrationState,
                                                  UUID ownerId, String catalogKey,
                                                  String catalogFingerprint, String compressedLegacy,
                                                  String legacyChecksum) {}

    private record PreparedMaterial(PreparedWorldMigrationEnvelope prepared,
                                    String preparedJson, String catalogJson, String committedJson) {}
    private record PreparedCompletion(String catalogJson, String committedJson, int legacyBytes) {}
    private record DecodedLegacy(WorldSnapshot snapshot, int serializedBytes) {}
    private record CapacityPlan(boolean feasible, long peakBytes, String reason) {}

    public static final class WorldStorageFormatException extends IllegalStateException {
        public WorldStorageFormatException(String message) { super(message); }
    }
}
