package com.footballmanager.adapters.out.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.footballmanager.AbstractIntegrationTest;
import com.footballmanager.application.service.world.WorldReferenceGraph;
import com.footballmanager.application.service.world.WorldStorageMigrationPlanner;
import com.footballmanager.application.service.world.WorldStorageMigrationExecutor;
import com.footballmanager.application.service.world.WorldStorageMigrationOrchestrator;
import com.footballmanager.application.service.world.WorldStorageMigrationTestDriver;
import com.footballmanager.application.service.world.WorldEntityFieldAuthority;
import com.footballmanager.application.service.world.WorldMigrationDurableReferenceRegistry;
import com.footballmanager.application.service.world.WorldMigrationReferenceInventory;
import com.footballmanager.application.service.world.WorldIdentityReference;
import com.footballmanager.application.service.world.WorldSemanticComparator;
import com.footballmanager.application.service.world.WorldStorageMigrationLimits;
import com.footballmanager.domain.model.entity.WorldLeague;
import com.footballmanager.domain.model.entity.WorldPlayer;
import com.footballmanager.domain.model.entity.WorldSnapshot;
import com.footballmanager.domain.model.entity.WorldSnapshotOverlay;
import com.footballmanager.domain.model.entity.WorldTeam;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.valueobject.LineupSlot;
import com.footballmanager.domain.model.valueobject.MatchFixture;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldStorageV2NegativeControlsTest extends AbstractIntegrationTest {

    enum Control {
        RANDOM_CANONICAL_PLAYER_ID,
        DETERMINISTIC_ID_COLLISION,
        WRONG_NAMESPACE,
        NORMALIZATION_DRIFT,
        FOREIGN_OWNER_OVERLAY,
        MISSING_CUSTOM_TEAM,
        MISSING_CUSTOM_PLAYER,
        LOST_LEAGUE_RELATION,
        MISSING_LEGACY_ALIAS,
        LINEUP_UNRESOLVED,
        FIXTURE_UNRESOLVED,
        STANDINGS_UNRESOLVED,
        CATALOG_HASH_MISMATCH,
        CATALOG_SEMANTIC_COLLISION,
        MISSING_CATALOG,
        CORRUPT_OVERLAY,
        INVALID_STORAGE_VERSION,
        PARTIAL_MIGRATION,
        MISSING_TTL,
        CATALOG_EXPIRES_FIRST,
        STALE_GENERATION_MIGRATION,
        OWNER_MISMATCH,
        DUPLICATE_CUSTOM_ENTITY,
        CANONICAL_SOURCE_CHANGED,
        SECOND_MIGRATION_DIFFERS,
        REAL_PLAYER_DELTA_LOST,
        REAL_TEAM_FORMATION_DELTA_LOST,
        LEAGUE_DELTA_LOST,
        FIELD_AUTHORITY_UNCOVERED,
        REFERENCE_REGISTRY_UNCOVERED,
        CORRUPT_CATALOG_ZERO_CREDIT,
        MAX_PLUS_ONE_WRITES_PREPARED,
        CROSS_OWNER_DELTA_LEAK
    }

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final CanonicalWorldCatalogFingerprint fingerprint = new CanonicalWorldCatalogFingerprint(mapper);
    private final WorldStorageMigrationPlanner planner = new WorldStorageMigrationPlanner();

    @ParameterizedTest(name = "{0}")
    @EnumSource(Control.class)
    void detectsEveryRequiredFailureMode(Control control) {
        assertTrue(detected(control), () -> "false pass for " + control);
    }

    private boolean detected(Control control) {
        return switch (control) {
            case RANDOM_CANONICAL_PLAYER_ID -> canonicalIdIsStable();
            case DETERMINISTIC_ID_COLLISION -> aliasCollisionIsRejected();
            case CATALOG_SEMANTIC_COLLISION -> materialHashChangeDiffers();
            case DUPLICATE_CUSTOM_ENTITY -> duplicateCustomEntityIsRejected();
            case WRONG_NAMESPACE -> wrongNamespaceDiffers();
            case NORMALIZATION_DRIFT -> normalizedUuidIsStable();
            case FOREIGN_OWNER_OVERLAY -> foreignOwnerEnvelopeIsRejected();
            case OWNER_MISMATCH -> ownerMismatchIsBlocked();
            case MISSING_CUSTOM_TEAM -> missingCustomTeamIsDetected();
            case LOST_LEAGUE_RELATION -> lostLeagueRelationIsDetected();
            case FIXTURE_UNRESOLVED -> unresolvedFixtureIsDiscoveredFromCareer();
            case STANDINGS_UNRESOLVED -> unresolvedStandingIsDiscoveredFromCareer();
            case MISSING_CUSTOM_PLAYER -> missingCustomPlayerIsDetected();
            case MISSING_LEGACY_ALIAS -> missingLegacyAliasIsDetected();
            case LINEUP_UNRESOLVED -> unresolvedLineupIsDiscoveredFromCareer();
            case CATALOG_HASH_MISMATCH -> catalogHashMismatchIsPhysicallyRejected();
            case CANONICAL_SOURCE_CHANGED -> canonicalSourceChangeCreatesDistinctPhysicalCatalog();
            case MISSING_CATALOG -> missingCatalogFailsClosed(false);
            case CORRUPT_OVERLAY -> corruptOverlayChecksumFailsClosed();
            case INVALID_STORAGE_VERSION -> invalidOverlayIsRejected();
            case PARTIAL_MIGRATION -> partialMigrationFailsClosed();
            case MISSING_TTL -> invalidTtlIsRejectedByRepositoryGuardContract();
            case CATALOG_EXPIRES_FIRST -> missingCatalogFailsClosed(true);
            case STALE_GENERATION_MIGRATION -> staleSourceChecksumBlocksWithoutWrite();
            case SECOND_MIGRATION_DIFFERS -> repeatedMigrationConverges();
            case REAL_PLAYER_DELTA_LOST -> playerDeltaRoundTripIsPhysical();
            case REAL_TEAM_FORMATION_DELTA_LOST -> teamDeltaRoundTripIsPhysical();
            case LEAGUE_DELTA_LOST -> leagueDeltaRoundTripIsPhysical();
            case FIELD_AUTHORITY_UNCOVERED -> fieldAuthorityIsFailClosed();
            case REFERENCE_REGISTRY_UNCOVERED -> referenceRegistryIsFailClosed();
            case CORRUPT_CATALOG_ZERO_CREDIT -> corruptCatalogCannotReceiveCapacityCredit();
            case MAX_PLUS_ONE_WRITES_PREPARED -> maxPlusOneIsRejectedBeforeWrite();
            case CROSS_OWNER_DELTA_LEAK -> crossOwnerDeltaIsPhysicallyIsolated();
        };
    }

    private boolean playerDeltaRoundTripIsPhysical() {
        UUID owner = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        WorldSnapshot canonical = new WorldSnapshot(); canonical.setUserId(owner);
        WorldPlayer base = canonical(owner, id, 70);
        canonical.getWorldPlayers().put(base.getWorldPlayerId(), base);
        WorldSnapshot current = new WorldSnapshot(); current.setUserId(owner);
        WorldPlayer changed = canonical(owner, id, 99);
        current.getWorldPlayers().put(changed.getWorldPlayerId(), changed);
        WorldSnapshot reconstructed = migrateAndReload(owner, current, canonicalWorldCopy(owner, id, 70));
        return reconstructed != null && reconstructed.getAllWorldPlayers().getFirst().getBaseAttack() == 99
                && equivalentWithDiagnostic(current, reconstructed);
    }

    private boolean teamDeltaRoundTripIsPhysical() {
        UUID owner = UUID.randomUUID(); UUID league = UUID.randomUUID(); UUID id = UUID.randomUUID();
        WorldSnapshot canonical = new WorldSnapshot(); canonical.setUserId(owner);
        WorldTeam base = WorldTeam.fromRealTeam(id, league, "T", "AR", "C", BigDecimal.ONE, "4-4-2");
        canonical.getWorldTeams().put(base.getWorldTeamId(), base);
        WorldSnapshot current = new WorldSnapshot(); current.setUserId(owner);
        WorldTeam changed = WorldTeam.fromRealTeam(id, league, "T", "AR", "C", BigDecimal.ONE, "3-5-2");
        current.getWorldTeams().put(changed.getWorldTeamId(), changed);
        WorldSnapshot fresh = new WorldSnapshot(); fresh.setUserId(owner);
        WorldTeam freshBase = WorldTeam.fromRealTeam(id, league, "T", "AR", "C", BigDecimal.ONE, "4-4-2");
        fresh.getWorldTeams().put(freshBase.getWorldTeamId(), freshBase);
        WorldSnapshot reconstructed = migrateAndReload(owner, current, fresh);
        return reconstructed != null && "3-5-2".equals(reconstructed.getAllWorldTeams().getFirst().getBaseFormation())
                && equivalentWithDiagnostic(current, reconstructed);
    }

    private boolean leagueDeltaRoundTripIsPhysical() {
        UUID owner = UUID.randomUUID(); UUID id = UUID.randomUUID();
        WorldSnapshot canonical = new WorldSnapshot(); canonical.setUserId(owner);
        canonical.setLeagues(java.util.List.of(new WorldLeague(id, "Base", "AR", 1)));
        WorldSnapshot current = new WorldSnapshot(); current.setUserId(owner);
        current.setLeagues(java.util.List.of(new WorldLeague(id, "Changed", "BR", 2)));
        WorldSnapshot fresh = new WorldSnapshot(); fresh.setUserId(owner);
        fresh.setLeagues(java.util.List.of(new WorldLeague(id, "Base", "AR", 1)));
        WorldSnapshot reconstructed = migrateAndReload(owner, current, fresh);
        return reconstructed != null && equivalentWithDiagnostic(current, reconstructed);
    }

    private boolean fieldAuthorityIsFailClosed() {
        WorldEntityFieldAuthority authority = new WorldEntityFieldAuthority();
        authority.requireComplete();
        return authority.uncoveredFields().isEmpty()
                && authority.uncoveredJacksonProperties(InjectedJacksonProperty.class, Set.of("known"))
                .contains("InjectedJacksonProperty.injected");
    }

    private boolean referenceRegistryIsFailClosed() {
        WorldMigrationDurableReferenceRegistry registry = new WorldMigrationDurableReferenceRegistry();
        registry.requireComplete();
        return registry.uncoveredModelFields().isEmpty()
                && registry.uncoveredAnnotatedReferences(InjectedPersistedReference.class).stream()
                .anyMatch(path -> path.contains(".futureWorldPlayerId@"));
    }

    private static final class InjectedJacksonProperty {
        public String getKnown() { return "known"; }
        public String getInjected() { return "future"; }
    }

    private static final class InjectedPersistedReference {
        @WorldIdentityReference(role = WorldMigrationDurableReferenceRegistry.Role.WORLD_PLAYER)
        private String futureWorldPlayerId;
    }

    private WorldSnapshot migrateAndReload(UUID owner, WorldSnapshot legacy, WorldSnapshot canonical) {
        try {
            legacy.setCreatedAt(java.time.Instant.parse("2026-08-01T00:00:00Z"));
            legacy.setLastUpdated(java.time.Instant.parse("2026-08-02T00:00:00Z"));
            canonical.setCreatedAt(legacy.getCreatedAt());
            canonical.setLastUpdated(legacy.getLastUpdated());
            String key = "world:" + owner;
            reactiveRedisTemplate.opsForValue().set(key, mapper.writeValueAsString(legacy), Duration.ofMinutes(5))
                    .block(Duration.ofSeconds(5));
            RedisWorldRepository migrationRepository = repository();
            var result = WorldStorageMigrationTestDriver.migrate(migrationRepository,
                    ignored -> reactor.core.publisher.Mono.just(canonical), owner,
                    1_000_000, 4_000_000, 32_768, 65_536);
            if (result == null || result.status() != WorldStorageMigrationOrchestrator.Status.MIGRATED) {
                return null;
            }
            return repository().findByUserId(owner).block(Duration.ofSeconds(5));
        } catch (Exception error) {
            return null;
        }
    }

    private boolean equivalentWithDiagnostic(WorldSnapshot expected, WorldSnapshot actual) {
        WorldSemanticComparator.Comparison comparison = new WorldSemanticComparator().compare(expected, actual);
        return comparison.equivalent();
    }

    private boolean foreignOwnerEnvelopeIsRejected() {
        try {
            UUID requested = UUID.randomUUID();
            UUID foreign = UUID.randomUUID();
            WorldSnapshotOverlay overlay = new WorldSnapshotOverlay();
            overlay.setOwnerId(foreign);
            String hash = "a".repeat(64);
            var envelope = new RedisWorldRepository.WorldStorageEnvelope(2, "COMMITTED", foreign,
                    "world-catalog:v2:" + hash, hash, sha(mapper.writeValueAsString(overlay)), overlay);
            reactiveRedisTemplate.opsForValue().set("world:" + requested, mapper.writeValueAsString(envelope),
                    Duration.ofMinutes(5)).block(Duration.ofSeconds(5));
            repository().findByUserId(requested).block(Duration.ofSeconds(5));
            return false;
        } catch (Exception expected) {
            return hasCause(expected, RedisWorldRepository.WorldStorageFormatException.class);
        }
    }

    private boolean catalogHashMismatchIsPhysicallyRejected() {
        try {
            UUID owner = UUID.randomUUID();
            WorldSnapshot canonical = canonicalWorld(owner);
            String expectedFingerprint = fingerprint.fingerprint(canonical);
            String catalogKey = "world-catalog:v2:" + expectedFingerprint;
            WorldSnapshot corrupt = canonicalWorld(owner);
            corrupt.getAllWorldPlayers().getFirst().setBaseAttack(1);
            WorldSnapshotOverlay overlay = WorldSnapshotOverlay.fromSnapshot(canonical, canonical);
            var envelope = new RedisWorldRepository.WorldStorageEnvelope(2, "COMMITTED", owner, catalogKey,
                    expectedFingerprint, sha(mapper.writeValueAsString(overlay)), overlay);
            reactiveRedisTemplate.opsForValue().set(catalogKey, mapper.writeValueAsString(corrupt),
                    Duration.ofMinutes(5)).block(Duration.ofSeconds(5));
            reactiveRedisTemplate.opsForValue().set("world:" + owner, mapper.writeValueAsString(envelope),
                    Duration.ofMinutes(5)).block(Duration.ofSeconds(5));
            repository().findByUserId(owner).block(Duration.ofSeconds(5));
            return false;
        } catch (Exception expected) {
            return hasCause(expected, RedisWorldRepository.WorldStorageFormatException.class);
        }
    }

    private boolean corruptCatalogCannotReceiveCapacityCredit() {
        try {
            UUID owner = UUID.randomUUID();
            WorldSnapshot legacy = canonicalWorld(owner); legacy.setUserId(owner);
            String legacyRaw = mapper.writeValueAsString(legacy);
            reactiveRedisTemplate.opsForValue().set("world:" + owner, legacyRaw, Duration.ofMinutes(5)).block();
            RedisWorldRepository repo = repository();
            var first = WorldStorageMigrationTestDriver.migrate(repo, ignored -> reactor.core.publisher.Mono.just(legacy),
                    owner, 1_000_000, 2_000_000, 0);
            String committed = reactiveRedisTemplate.opsForValue().get("world:" + owner).block();
            String catalogKey = mapper.readTree(committed).path("catalogKey").asText();
            String catalog = reactiveRedisTemplate.opsForValue().get(catalogKey).block();
            reactiveRedisTemplate.opsForValue().set(catalogKey, "{\"bad\":true}", Duration.ofMinutes(5)).block();
            reactiveRedisTemplate.opsForValue().set("world:" + owner, legacyRaw, Duration.ofMinutes(5)).block();
            long quota = first.plannedPeakBytes() - Math.max(1, catalog.getBytes(StandardCharsets.UTF_8).length / 2);
            var blocked = WorldStorageMigrationTestDriver.migrate(repo,
                    ignored -> reactor.core.publisher.Mono.just(legacy), owner, 1_000_000, quota, 0);
            return blocked.status() == WorldStorageMigrationOrchestrator.Status.BLOCKED_CAPACITY
                    && legacyRaw.equals(reactiveRedisTemplate.opsForValue().get("world:" + owner).block())
                    && "{\"bad\":true}".equals(reactiveRedisTemplate.opsForValue().get(catalogKey).block());
        } catch (Exception error) {
            return false;
        }
    }

    private boolean maxPlusOneIsRejectedBeforeWrite() {
        try {
            UUID owner = UUID.randomUUID();
            WorldSnapshot oversized = new WorldSnapshot(); oversized.setUserId(owner);
            for (int i = 0; i <= WorldStorageMigrationLimits.MAX_CUSTOM_TEAMS; i++) {
                WorldTeam team = WorldTeam.createCustom("T", "AR", BigDecimal.ONE, "4-4-2");
                oversized.getWorldTeams().put(team.getWorldTeamId(), team);
            }
            String raw = mapper.writeValueAsString(oversized);
            reactiveRedisTemplate.opsForValue().set("world:" + owner, raw, Duration.ofMinutes(5)).block();
            var result = WorldStorageMigrationTestDriver.migrate(repository(),
                    ignored -> reactor.core.publisher.Mono.just(oversized), owner, 1_000_000, 2_000_000, 0);
            return result.status() == WorldStorageMigrationOrchestrator.Status.INVALID_LEGACY
                    && raw.equals(reactiveRedisTemplate.opsForValue().get("world:" + owner).block())
                    && !mapper.readTree(raw).has("migrationState");
        } catch (Exception error) {
            return false;
        }
    }

    private boolean crossOwnerDeltaIsPhysicallyIsolated() {
        try {
            UUID a = UUID.randomUUID(); UUID b = UUID.randomUUID(); UUID real = UUID.randomUUID();
            WorldSnapshot canonicalA = canonicalWorldCopy(a, real, 70);
            WorldSnapshot changedA = canonicalWorldCopy(a, real, 99);
            WorldSnapshot canonicalB = canonicalWorldCopy(b, real, 70);
            String rawA = mapper.writeValueAsString(changedA); String rawB = mapper.writeValueAsString(canonicalB);
            reactiveRedisTemplate.opsForValue().set("world:" + a, rawA, Duration.ofMinutes(5)).block();
            reactiveRedisTemplate.opsForValue().set("world:" + b, rawB, Duration.ofMinutes(5)).block();
            var result = WorldStorageMigrationTestDriver.migrate(repository(),
                    ignored -> reactor.core.publisher.Mono.just(canonicalA), a, 1_000_000, 2_000_000, 0);
            return result.status() == WorldStorageMigrationOrchestrator.Status.MIGRATED
                    && rawB.equals(reactiveRedisTemplate.opsForValue().get("world:" + b).block())
                    && repository().findByUserId(a).block().getAllWorldPlayers().getFirst().getBaseAttack() == 99;
        } catch (Exception error) {
            return false;
        }
    }

    private WorldSnapshot canonicalWorldCopy(UUID owner, UUID real, int attack) {
        WorldSnapshot world = new WorldSnapshot(); world.setUserId(owner);
        WorldPlayer player = canonical(owner, real, attack);
        world.getWorldPlayers().put(player.getWorldPlayerId(), player);
        return world;
    }

    private boolean canonicalIdIsStable() {
        UUID owner = UUID.randomUUID();
        UUID real = UUID.randomUUID();
        return WorldPlayer.stableCanonicalWorldPlayerId(owner, real)
                .equals(WorldPlayer.stableCanonicalWorldPlayerId(UUID.randomUUID(), real));
    }

    private boolean aliasCollisionIsRejected() {
        UUID owner = UUID.randomUUID();
        WorldPlayer player = canonical(owner, UUID.randomUUID(), 70);
        WorldSnapshot catalog = new WorldSnapshot();
        catalog.getWorldPlayers().put(player.getWorldPlayerId(), player);
        WorldSnapshotOverlay overlay = new WorldSnapshotOverlay();
        overlay.setOwnerId(owner);
        overlay.setLegacyPlayerAliases(Map.of(player.getWorldPlayerId(), UUID.randomUUID().toString()));
        try {
            overlay.applyTo(catalog);
            return false;
        } catch (IllegalStateException expected) {
            return true;
        }
    }

    private boolean wrongNamespaceDiffers() {
        UUID real = UUID.randomUUID();
        String stable = WorldPlayer.stableCanonicalWorldPlayerId(UUID.randomUUID(), real);
        String wrong = UUID.nameUUIDFromBytes(("wrong:" + real).getBytes(StandardCharsets.UTF_8)).toString();
        return !stable.equals(wrong);
    }

    private boolean normalizedUuidIsStable() {
        UUID real = UUID.randomUUID();
        UUID reparsed = UUID.fromString(real.toString().toUpperCase(java.util.Locale.ROOT));
        return WorldPlayer.stableCanonicalWorldPlayerId(UUID.randomUUID(), real)
                .equals(WorldPlayer.stableCanonicalWorldPlayerId(UUID.randomUUID(), reparsed));
    }

    private boolean ownerMismatchIsBlocked() {
        try {
            UUID sourceOwner = UUID.randomUUID();
            UUID requestedOwner = UUID.randomUUID();
            WorldSnapshot foreign = canonicalWorld(sourceOwner);
            String raw = mapper.writeValueAsString(foreign);
            reactiveRedisTemplate.opsForValue().set("world:" + requestedOwner, raw, Duration.ofMinutes(5)).block();
            var outcome = WorldStorageMigrationTestDriver.migrate(repository(),
                    ignored -> reactor.core.publisher.Mono.just(canonicalWorld(requestedOwner)), requestedOwner,
                    1_000_000, 4_000_000, 32_768, 65_536);
            return outcome.status() == WorldStorageMigrationOrchestrator.Status.INVALID_LEGACY
                    && raw.equals(reactiveRedisTemplate.opsForValue().get("world:" + requestedOwner).block());
        } catch (Exception error) {
            return false;
        }
    }

    private boolean missingCustomTeamIsDetected() {
        WorldSnapshot expected = new WorldSnapshot(); expected.setUserId(UUID.randomUUID());
        WorldTeam custom = WorldTeam.createCustom("Custom", "AR", BigDecimal.ONE, "4-4-2");
        expected.getWorldTeams().put(custom.getWorldTeamId(), custom);
        WorldSnapshot actual = new WorldSnapshot(); actual.setUserId(expected.getUserId());
        actual.setCreatedAt(expected.getCreatedAt()); actual.setLastUpdated(expected.getLastUpdated());
        return !new WorldSemanticComparator().compare(expected, actual).equivalent();
    }

    private boolean missingCustomPlayerIsDetected() {
        WorldSnapshot expected = new WorldSnapshot(); expected.setUserId(UUID.randomUUID());
        WorldPlayer custom = WorldPlayer.createCustom("Custom", 20, "MID", 1, 1, 1, 1, 1, 1, BigDecimal.ONE);
        expected.getWorldPlayers().put(custom.getWorldPlayerId(), custom);
        WorldSnapshot actual = new WorldSnapshot(); actual.setUserId(expected.getUserId());
        actual.setCreatedAt(expected.getCreatedAt()); actual.setLastUpdated(expected.getLastUpdated());
        return !new WorldSemanticComparator().compare(expected, actual).equivalent();
    }

    private boolean lostLeagueRelationIsDetected() {
        UUID owner = UUID.randomUUID(); UUID teamId = UUID.randomUUID();
        UUID baseLeague = UUID.randomUUID(); UUID changedLeague = UUID.randomUUID();
        WorldSnapshot canonical = new WorldSnapshot(); canonical.setUserId(owner);
        WorldTeam base = WorldTeam.fromRealTeam(teamId, baseLeague, "T", "AR", "C", BigDecimal.ONE, "4-4-2");
        canonical.getWorldTeams().put(base.getWorldTeamId(), base);
        WorldSnapshot current = new WorldSnapshot(); current.setUserId(owner);
        WorldTeam changed = WorldTeam.fromRealTeam(teamId, changedLeague, "T", "AR", "C", BigDecimal.ONE, "4-4-2");
        current.getWorldTeams().put(changed.getWorldTeamId(), changed);
        WorldSnapshotOverlay overlay = WorldSnapshotOverlay.fromSnapshot(current, canonical);
        overlay.setRealTeamDeltas(Map.of()); overlay.setTeamLeagueAssignments(Map.of());
        WorldSnapshot fresh = new WorldSnapshot(); fresh.setUserId(owner);
        WorldTeam freshTeam = WorldTeam.fromRealTeam(teamId, baseLeague, "T", "AR", "C", BigDecimal.ONE, "4-4-2");
        fresh.getWorldTeams().put(freshTeam.getWorldTeamId(), freshTeam);
        return !new WorldSemanticComparator().compare(current, overlay.applyTo(fresh)).equivalent();
    }

    private boolean missingLegacyAliasIsDetected() {
        UUID owner = UUID.randomUUID(); UUID real = UUID.randomUUID();
        WorldSnapshot canonical = canonicalWorldCopy(owner, real, 70);
        WorldSnapshot current = new WorldSnapshot(); current.setUserId(owner);
        WorldPlayer legacy = WorldPlayer.fromRealPlayer(real, "team", "P", 20, "MID",
                70, 70, 70, 70, 70, 70, BigDecimal.ONE);
        current.getWorldPlayers().put(legacy.getWorldPlayerId(), legacy);
        WorldSnapshotOverlay overlay = WorldSnapshotOverlay.fromSnapshot(current, canonical);
        overlay.setLegacyPlayerAliases(Map.of());
        return !new WorldSemanticComparator().compare(current,
                overlay.applyTo(canonicalWorldCopy(owner, real, 70))).equivalent();
    }

    private boolean unresolvedFixtureIsDiscoveredFromCareer() {
        CareerSurface surface = careerSurface();
        surface.career().getTournamentState().setFixtures(java.util.List.of(new MatchFixture("m",
                surface.team().getSessionTeamId(), surface.team().getSessionTeamId(), 1)));
        return blockedByDiscoveredReferences(surface.owner(), surface.career());
    }

    private boolean unresolvedStandingIsDiscoveredFromCareer() {
        CareerSurface surface = careerSurface();
        surface.career().getTournamentState().initializeStandings(java.util.List.of(surface.team()));
        return blockedByDiscoveredReferences(surface.owner(), surface.career());
    }

    private boolean unresolvedLineupIsDiscoveredFromCareer() {
        CareerSurface surface = careerSurface();
        SessionPlayer player = SessionPlayer.cloneFromWorldPlayer("missing-world-player", "P", "MID", 20, 70,
                surface.team().getSessionTeamId());
        surface.career().addSessionPlayer(player);
        surface.career().setTeamStarting11SubdivisionSlots(Map.of(surface.team().getSessionTeamId(),
                Map.of("S16-2", new LineupSlot(player.getSessionPlayerId(), "S16-2"))));
        return blockedByDiscoveredReferences(surface.owner(), surface.career());
    }

    private CareerSurface careerSurface() {
        UUID owner = UUID.randomUUID();
        CareerSave career = new CareerSave(); career.setUserId(owner);
        SessionTeam team = SessionTeam.custom("missing-world-team", "T", "AR", BigDecimal.ONE, "4-4-2");
        career.addSessionTeam(team);
        return new CareerSurface(owner, career, team);
    }

    private boolean blockedByDiscoveredReferences(UUID owner, CareerSave career) {
        WorldSnapshot legacy = new WorldSnapshot(); legacy.setUserId(owner);
        WorldSnapshot canonical = new WorldSnapshot(); canonical.setUserId(owner);
        return !planner.plan(legacy, canonical, new WorldMigrationReferenceInventory().discover(career)).ready();
    }

    private record CareerSurface(UUID owner, CareerSave career, SessionTeam team) { }

    private boolean unresolvedTeamSurfaceIsBlocked(String surface) {
        UUID owner = UUID.randomUUID();
        WorldSnapshot legacy = new WorldSnapshot(); legacy.setUserId(owner);
        WorldSnapshot canonical = new WorldSnapshot(); canonical.setUserId(owner);
        return !planner.plan(legacy, canonical,
                new WorldReferenceGraph(Map.of(surface, Set.of("missing")), Map.of())).ready();
    }

    private boolean unresolvedPlayerSurfaceIsBlocked(String surface) {
        UUID owner = UUID.randomUUID();
        WorldSnapshot legacy = new WorldSnapshot(); legacy.setUserId(owner);
        WorldSnapshot canonical = new WorldSnapshot(); canonical.setUserId(owner);
        return !planner.plan(legacy, canonical,
                new WorldReferenceGraph(Map.of(), Map.of(surface, Set.of("missing")))).ready();
    }

    private boolean materialHashChangeDiffers() {
        UUID owner = UUID.randomUUID();
        UUID real = UUID.randomUUID();
        WorldSnapshot a = new WorldSnapshot();
        WorldPlayer first = canonical(owner, real, 70);
        a.getWorldPlayers().put(first.getWorldPlayerId(), first);
        WorldSnapshot b = new WorldSnapshot();
        WorldPlayer second = canonical(owner, real, 71);
        b.getWorldPlayers().put(second.getWorldPlayerId(), second);
        return !fingerprint.fingerprint(a).equals(fingerprint.fingerprint(b));
    }

    private boolean canonicalSourceChangeCreatesDistinctPhysicalCatalog() {
        try {
            UUID owner = UUID.randomUUID();
            UUID real = UUID.randomUUID();
            WorldSnapshot first = canonicalWorldCopy(owner, real, 70);
            String worldKey = "world:" + owner;
            reactiveRedisTemplate.opsForValue().set(worldKey, mapper.writeValueAsString(first),
                    Duration.ofMinutes(5)).block();
            var firstOutcome = WorldStorageMigrationTestDriver.migrate(repository(),
                    ignored -> reactor.core.publisher.Mono.just(first), owner,
                    1_000_000, 4_000_000, 32_768, 65_536);
            String firstCatalog = mapper.readTree(reactiveRedisTemplate.opsForValue().get(worldKey).block())
                    .path("catalogKey").asText();

            WorldSnapshot changed = canonicalWorldCopy(owner, real, 71);
            reactiveRedisTemplate.opsForValue().set(worldKey, mapper.writeValueAsString(changed),
                    Duration.ofMinutes(5)).block();
            var secondOutcome = WorldStorageMigrationTestDriver.migrate(repository(),
                    ignored -> reactor.core.publisher.Mono.just(changed), owner,
                    1_000_000, 4_000_000, 32_768, 65_536);
            String secondCatalog = mapper.readTree(reactiveRedisTemplate.opsForValue().get(worldKey).block())
                    .path("catalogKey").asText();
            return firstOutcome.status() == WorldStorageMigrationOrchestrator.Status.MIGRATED
                    && secondOutcome.status() == WorldStorageMigrationOrchestrator.Status.MIGRATED
                    && !firstCatalog.equals(secondCatalog)
                    && Boolean.TRUE.equals(reactiveRedisTemplate.hasKey(firstCatalog).block())
                    && Boolean.TRUE.equals(reactiveRedisTemplate.hasKey(secondCatalog).block());
        } catch (Exception error) {
            return false;
        }
    }

    private boolean invalidOverlayIsRejected() {
        try {
            UUID owner = UUID.randomUUID();
            WorldSnapshot canonical = canonicalWorld(owner);
            String hash = fingerprint.fingerprint(canonical);
            String catalogKey = "world-catalog:v2:" + hash;
            WorldSnapshotOverlay overlay = new WorldSnapshotOverlay();
            overlay.setOwnerId(owner);
            overlay.setStorageVersion(999);
            var envelope = new RedisWorldRepository.WorldStorageEnvelope(2, "COMMITTED", owner,
                    catalogKey, hash, sha(mapper.writeValueAsString(overlay)), overlay);
            reactiveRedisTemplate.opsForValue().set(catalogKey, mapper.writeValueAsString(canonical),
                    Duration.ofMinutes(5)).block();
            reactiveRedisTemplate.opsForValue().set("world:" + owner, mapper.writeValueAsString(envelope),
                    Duration.ofMinutes(5)).block();
            repository().findByUserId(owner).block(Duration.ofSeconds(5));
            return false;
        } catch (Exception expected) {
            return hasCause(expected, RedisWorldRepository.WorldStorageFormatException.class)
                    || hasCause(expected, IllegalStateException.class);
        }
    }

    private boolean invalidTtlIsRejectedByRepositoryGuardContract() {
        RedisWorldRepository repository = repository();
        ReflectionTestUtils.setField(repository, "storageVersion", 1);
        ReflectionTestUtils.setField(repository, "worldTtl", Duration.ZERO);
        WorldSnapshot world = new WorldSnapshot();
        world.setUserId(UUID.randomUUID());
        try {
            repository.saveInitial(world).block(Duration.ofSeconds(5));
            return false;
        } catch (RuntimeException expected) {
            return hasCause(expected, IllegalStateException.class);
        }
    }

    private boolean missingCatalogFailsClosed(boolean expireExistingCatalog) {
        try {
            UUID owner = UUID.randomUUID();
            WorldSnapshot canonical = canonicalWorld(owner);
            WorldSnapshotOverlay overlay = new WorldSnapshotOverlay();
            overlay.setOwnerId(owner);
            String hash = fingerprint.fingerprint(canonical);
            String catalogKey = "world-catalog:v2:" + hash;
            RedisWorldRepository.WorldStorageEnvelope envelope = new RedisWorldRepository.WorldStorageEnvelope(
                    2, "COMMITTED", owner, catalogKey, hash,
                    sha(mapper.writeValueAsString(overlay)), overlay);
            if (expireExistingCatalog) {
                reactiveRedisTemplate.opsForValue().set(catalogKey, mapper.writeValueAsString(canonical),
                        Duration.ofMinutes(1)).block(Duration.ofSeconds(5));
            }
            reactiveRedisTemplate.opsForValue().set("world:" + owner, mapper.writeValueAsString(envelope),
                    Duration.ofMinutes(1)).block(Duration.ofSeconds(5));
            if (expireExistingCatalog) {
                reactiveRedisTemplate.delete(catalogKey).block(Duration.ofSeconds(5));
            }
            repository().findByUserId(owner).block(Duration.ofSeconds(5));
            return false;
        } catch (Exception expected) {
            return hasCause(expected, RedisWorldRepository.WorldStorageFormatException.class);
        }
    }

    private boolean corruptOverlayChecksumFailsClosed() {
        try {
            UUID owner = UUID.randomUUID();
            WorldSnapshot canonical = canonicalWorld(owner);
            WorldSnapshotOverlay overlay = new WorldSnapshotOverlay();
            overlay.setOwnerId(owner);
            String hash = fingerprint.fingerprint(canonical);
            String catalogKey = "world-catalog:v2:" + hash;
            RedisWorldRepository.WorldStorageEnvelope envelope = new RedisWorldRepository.WorldStorageEnvelope(
                    2, "COMMITTED", owner, catalogKey, hash, "0".repeat(64), overlay);
            reactiveRedisTemplate.opsForValue().set(catalogKey, mapper.writeValueAsString(canonical),
                    Duration.ofMinutes(1)).block(Duration.ofSeconds(5));
            reactiveRedisTemplate.opsForValue().set("world:" + owner, mapper.writeValueAsString(envelope),
                    Duration.ofMinutes(1)).block(Duration.ofSeconds(5));
            repository().findByUserId(owner).block(Duration.ofSeconds(5));
            return false;
        } catch (Exception expected) {
            return hasCause(expected, RedisWorldRepository.WorldStorageFormatException.class);
        }
    }

    private boolean partialMigrationFailsClosed() {
        try {
            UUID owner = UUID.randomUUID();
            String hash = "a".repeat(64);
            RedisWorldRepository.PreparedWorldMigrationEnvelope partial =
                    new RedisWorldRepository.PreparedWorldMigrationEnvelope(2, "INCOMPLETE", owner,
                            "world-catalog:v2:" + hash, hash, "not-compressed", "b".repeat(64));
            reactiveRedisTemplate.opsForValue().set("world:" + owner, mapper.writeValueAsString(partial),
                    Duration.ofMinutes(1)).block(Duration.ofSeconds(5));
            repository().findByUserId(owner).block(Duration.ofSeconds(5));
            return false;
        } catch (Exception expected) {
            return hasCause(expected, RedisWorldRepository.WorldStorageFormatException.class);
        }
    }

    private boolean staleSourceChecksumBlocksWithoutWrite() {
        try {
            UUID owner = UUID.randomUUID();
            WorldSnapshot legacy = canonicalWorld(owner);
            legacy.setUserId(owner);
            String key = "world:" + owner;
            String before = mapper.writeValueAsString(legacy);
            reactiveRedisTemplate.opsForValue().set(key, before, Duration.ofMinutes(1))
                    .block(Duration.ofSeconds(5));
            RedisWorldRepository repository = repository();
            WorldSnapshot newer = canonicalWorld(owner);
            String newerRaw = mapper.writeValueAsString(newer);
            WorldStorageMigrationExecutor racing = new WorldStorageMigrationExecutor() {
                @Override public reactor.core.publisher.Mono<SourceInspection> inspect(UUID id) {
                    return repository.inspect(id);
                }
                @Override public reactor.core.publisher.Mono<ExecutionResult> execute(
                        com.footballmanager.application.service.world.WorldMigrationAdmission admission) {
                    return reactiveRedisTemplate.opsForValue().set(key, newerRaw, Duration.ofMinutes(1))
                            .then(repository.execute(admission));
                }
            };
            WorldStorageMigrationOrchestrator.Outcome result = WorldStorageMigrationTestDriver.migrate(
                    racing, ignored -> reactor.core.publisher.Mono.just(legacy), owner,
                    1_000_000, 2_000_000, 1_000);
            String after = reactiveRedisTemplate.opsForValue().get(key).block(Duration.ofSeconds(5));
            return result != null && result.status() == WorldStorageMigrationOrchestrator.Status.SOURCE_CHANGED
                    && newerRaw.equals(after) && !before.equals(after);
        } catch (Exception unexpected) {
            return false;
        }
    }

    private boolean repeatedMigrationConverges() {
        try {
            UUID owner = UUID.randomUUID();
            WorldSnapshot legacy = canonicalWorld(owner);
            legacy.setUserId(owner);
            String key = "world:" + owner;
            reactiveRedisTemplate.opsForValue().set(key, mapper.writeValueAsString(legacy), Duration.ofMinutes(1))
                    .block(Duration.ofSeconds(5));
            RedisWorldRepository repository = repository();
            WorldStorageMigrationOrchestrator orchestrator = WorldStorageMigrationTestDriver.create(repository,
                    ignored -> reactor.core.publisher.Mono.just(legacy));
            var capacity = new WorldStorageMigrationOrchestrator.CapacitySnapshot(1_000_000, 2_000_000, 1_000);
            WorldStorageMigrationOrchestrator.Outcome first = orchestrator.migrate(owner, capacity)
                    .block(Duration.ofSeconds(10));
            String committed = reactiveRedisTemplate.opsForValue().get(key).block(Duration.ofSeconds(5));
            WorldStorageMigrationOrchestrator.Outcome second = orchestrator.migrate(owner, capacity)
                    .block(Duration.ofSeconds(10));
            String afterRetry = reactiveRedisTemplate.opsForValue().get(key).block(Duration.ofSeconds(5));
            return first != null && first.status() == WorldStorageMigrationOrchestrator.Status.MIGRATED
                    && second != null && second.status() == WorldStorageMigrationOrchestrator.Status.ALREADY_MIGRATED_VALID
                    && committed.equals(afterRetry);
        } catch (Exception unexpected) {
            return false;
        }
    }

    private boolean duplicateCustomEntityIsRejected() {
        WorldSnapshot canonical = new WorldSnapshot();
        WorldPlayer existing = WorldPlayer.createCustom("Existing", 20, "MID", 60, 60, 60, 60, 60, 60,
                BigDecimal.ONE);
        canonical.getWorldPlayers().put(existing.getWorldPlayerId(), existing);
        WorldPlayer duplicate = WorldPlayer.createCustom("Duplicate", 21, "ATT", 70, 50, 65, 65, 65, 65,
                BigDecimal.ONE);
        duplicate.setWorldPlayerId(existing.getWorldPlayerId());
        WorldSnapshotOverlay overlay = new WorldSnapshotOverlay();
        overlay.setOwnerId(UUID.randomUUID());
        overlay.setCustomPlayers(Map.of(existing.getWorldPlayerId(), duplicate));
        try {
            overlay.applyTo(canonical);
            return false;
        } catch (IllegalStateException expected) {
            return true;
        }
    }

    private WorldSnapshot canonicalWorld(UUID owner) {
        WorldSnapshot world = new WorldSnapshot();
        world.setUserId(owner);
        UUID real = UUID.randomUUID();
        WorldPlayer player = canonical(owner, real, 70);
        world.getWorldPlayers().put(player.getWorldPlayerId(), player);
        return world;
    }

    private RedisWorldRepository repository() {
        RedisWorldRepository repository = new RedisWorldRepository(reactiveRedisTemplate, mapper);
        ReflectionTestUtils.setField(repository, "worldTtl", Duration.ofDays(30));
        ReflectionTestUtils.setField(repository, "catalogTtl", Duration.ofDays(365));
        ReflectionTestUtils.setField(repository, "storageVersion", 2);
        return repository;
    }

    private static boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        Throwable current = error;
        while (current != null) {
            if (type.isInstance(current)) return true;
            current = current.getCause();
        }
        return false;
    }

    private WorldPlayer canonical(UUID owner, UUID real, int attack) {
        return WorldPlayer.fromCanonicalPlayer(owner, real, "team", "P", 20, "MID",
                attack, 70, 70, 70, 70, 70, BigDecimal.ONE);
    }

    private String sha(String value) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
