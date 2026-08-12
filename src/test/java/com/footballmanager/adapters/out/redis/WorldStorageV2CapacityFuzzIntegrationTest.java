package com.footballmanager.adapters.out.redis;

import com.footballmanager.AbstractIntegrationTest;
import com.footballmanager.application.service.world.WorldStorageMigrationOrchestrator;
import com.footballmanager.application.service.world.WorldStorageMigrationTestDriver;
import com.footballmanager.domain.model.entity.WorldLeague;
import com.footballmanager.domain.model.entity.WorldPlayer;
import com.footballmanager.domain.model.entity.WorldSnapshot;
import com.footballmanager.domain.model.entity.WorldTeam;
import com.footballmanager.domain.model.valueobject.PlayerSkill;
import com.footballmanager.domain.model.valueobject.PlayerSpecialTrait;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.api.sync.RedisCommands;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldStorageV2CapacityFuzzIntegrationTest extends AbstractIntegrationTest {

    private static final long QUOTA = 64L * 1024L * 1024L;
    private static final long PROVIDER_MARGIN = 32_768L;
    private static final long LOCAL_MARGIN = 65_536L;

    @Test
    void reproducibleFiveHundredWorldFuzzHasNoUnsafePhysicalAdmission() throws Exception {
        Random random = new Random(0x79E2026L);
        int admitted = 0;
        int blocked = 0;
        long maxUnderestimate = Long.MIN_VALUE;
        for (int index = 0; index < 500; index++) {
            UUID owner = UUID.nameUUIDFromBytes(("capacity-fuzz-" + index).getBytes(StandardCharsets.UTF_8));
            Pair pair = fuzzPair(owner, index, random);
            String legacyRaw = objectMapper.writeValueAsString(pair.legacy());
            String worldKey = "world:" + owner;
            reactiveRedisTemplate.opsForValue().set(worldKey, legacyRaw, Duration.ofDays(30)).block();
            long legacyMemory = memoryUsage(worldKey);
            long baseline = index % 5 == 0 ? QUOTA - 100_000L : 10_000_000L;
            var outcome = WorldStorageMigrationTestDriver.migrate(repository(),
                    ignored -> Mono.just(copy(pair.canonical())), owner,
                    baseline, QUOTA, PROVIDER_MARGIN, LOCAL_MARGIN);
            assertNotNull(outcome);
            if (outcome.status() == WorldStorageMigrationOrchestrator.Status.BLOCKED_CAPACITY) {
                blocked++;
                assertEquals(legacyRaw, reactiveRedisTemplate.opsForValue().get(worldKey).block());
            } else {
                assertEquals(WorldStorageMigrationOrchestrator.Status.MIGRATED, outcome.status(), outcome.reason());
                admitted++;
                Physical actual = physicalState(owner, legacyRaw, legacyMemory, baseline);
                long underestimate = actual.peak() - outcome.plannedPeakBytes();
                maxUnderestimate = Math.max(maxUnderestimate, underestimate);
                assertTrue(underestimate <= 0,
                        "unsafe admission index=" + index + " underestimate=" + underestimate);
                assertTrue(actual.peak() + PROVIDER_MARGIN + LOCAL_MARGIN <= QUOTA,
                        "physical peak violates guarded quota index=" + index);
                reactiveRedisTemplate.delete(actual.catalogKey(), actual.preparedProbe()).block();
            }
            reactiveRedisTemplate.delete(worldKey).block();
        }
        assertTrue(admitted >= 350, "admitted=" + admitted);
        assertTrue(blocked >= 90, "blocked=" + blocked);
        assertTrue(maxUnderestimate <= 0);
        System.out.printf("[WORLD-CAPACITY-FUZZ] seed=%d cases=500 admitted=%d blocked=%d unsafe=0 maxUnderestimate=%d%n",
                0x79E2026L, admitted, blocked, maxUnderestimate);
    }

    @Test
    void independentCapacityAuthoritySeedCoversOneThousandCases() {
        Random random = new Random(0x79E279E5L);
        var model = new com.footballmanager.application.service.world.WorldStoragePhysicalCapacityModel();
        long maximumSlack = Long.MIN_VALUE;
        for (int index = 0; index < 1_000; index++) {
            long current = 1_000_000L + random.nextInt(20_000_000);
            long legacy = 10_000L + random.nextInt(500_000);
            long prepared = 20_000L + random.nextInt(700_000);
            long committed = 20_000L + random.nextInt(700_000);
            long catalog = 20_000L + random.nextInt(700_000);
            var estimate = model.estimate(current, legacy, prepared, committed, catalog,
                    index % 2 == 0);
            long upperBound = estimate.compactedBaselinePhysicalBytes()
                    + Math.max(estimate.preparedPhysicalBytes(), estimate.committedPhysicalBytes())
                    + estimate.catalogPhysicalBytes();
            assertEquals(estimate.physicalPeakBytes(), upperBound);
            maximumSlack = Math.max(maximumSlack, upperBound - estimate.physicalPeakBytes());
        }
        assertTrue(maximumSlack >= 0);
        System.out.printf("[WORLD-CAPACITY-AUTHORITY] seed=%d cases=1000 unsafe=0 maxSlack=%d%n",
                0x79E279E5L, maximumSlack);
    }

    @Test
    void multidimensionalStressSearchFindsConservativeAdmittedAndBlockedFixtures() throws Exception {
        long baseline = 268_320_000L;
        long quota = 268_435_456L;
        int largestAdmitted = 0;
        int firstBlocked = 0;
        long largestPlanned = 0;
        long largestPhysical = 0;
        for (int scale = 1; scale <= 128; scale++) {
            UUID owner = UUID.nameUUIDFromBytes(("capacity-worst-" + scale).getBytes(StandardCharsets.UTF_8));
            Pair pair = stressPair(owner, scale);
            String legacyRaw = objectMapper.writeValueAsString(pair.legacy());
            String worldKey = "world:" + owner;
            reactiveRedisTemplate.opsForValue().set(worldKey, legacyRaw, Duration.ofDays(30)).block();
            long legacyMemory = memoryUsage(worldKey);
            var outcome = WorldStorageMigrationTestDriver.migrate(repository(),
                    ignored -> Mono.just(copy(pair.canonical())), owner,
                    baseline, quota, PROVIDER_MARGIN, LOCAL_MARGIN);
            if (outcome.status() == WorldStorageMigrationOrchestrator.Status.BLOCKED_CAPACITY) {
                firstBlocked = scale;
                reactiveRedisTemplate.delete(worldKey).block();
                break;
            }
            assertEquals(WorldStorageMigrationOrchestrator.Status.MIGRATED, outcome.status(), outcome.reason());
            Physical actual = physicalState(owner, legacyRaw, legacyMemory, baseline);
            assertTrue(actual.peak() <= outcome.plannedPeakBytes());
            assertTrue(actual.peak() + PROVIDER_MARGIN + LOCAL_MARGIN <= quota);
            largestAdmitted = scale;
            largestPlanned = outcome.plannedPeakBytes();
            largestPhysical = actual.peak();
            reactiveRedisTemplate.delete(worldKey, actual.catalogKey(), actual.preparedProbe()).block();
        }
        assertTrue(largestAdmitted > 0);
        assertEquals(largestAdmitted + 1, firstBlocked);
        System.out.printf("[WORLD-WORST-ADMITTED] scale=%d players=%d aliases=%d customPlayers=%d customTeams=%d leagues=%d traitsPerPlayer=32 skillsPerPlayer=%d textChars=512 unicode=true removals=true relations=true planned=%d physical=%d firstBlockedScale=%d%n",
                largestAdmitted, largestAdmitted, largestAdmitted, largestAdmitted,
                Math.max(1, largestAdmitted / 4), largestAdmitted,
                PlayerSkill.values().length, largestPlanned, largestPhysical, firstBlocked);
    }

    private Pair fuzzPair(UUID owner, int index, Random random) throws Exception {
        int players = 1 + random.nextInt(6);
        WorldSnapshot canonical = base(owner, players, false, 8);
        WorldSnapshot legacy = copy(canonical);
        String text = (index % 2 == 0 ? "Á⚽" : "x").repeat(1 + random.nextInt(20));
        legacy.getLeagues().getFirst().setName(text);
        legacy.getAllWorldTeams().getFirst().setBaseFormation(index % 3 == 0 ? "3-5-2" : "4-3-3");
        int position = 0;
        Map<String, String> aliases = new LinkedHashMap<>(legacy.getWorldPlayerAliases());
        for (WorldPlayer player : legacy.getAllWorldPlayers()) {
            player.setName(text + position);
            player.setBaseAttack(70 + random.nextInt(30));
            int traitCount = random.nextInt(4);
            List<PlayerSpecialTrait> traits = new ArrayList<>();
            for (int t = 0; t < traitCount; t++) {
                traits.add(new PlayerSpecialTrait(player.getRealPlayerId(), "T" + t, text, text));
            }
            player.setSpecialTraits(traits);
            EnumMap<PlayerSkill, Integer> skills = new EnumMap<>(PlayerSkill.class);
            for (int s = 0; s < random.nextInt(PlayerSkill.values().length + 1); s++) {
                skills.put(PlayerSkill.values()[s], 1 + random.nextInt(5));
            }
            player.setSkillLevels(skills);
            if (position < index % (players + 1)) {
                aliases.put("legacy-" + index + "-" + position, player.getWorldPlayerId());
            }
            position++;
        }
        legacy.setWorldPlayerAliases(aliases);
        int custom = index % 3;
        addCustomEntities(legacy, owner, custom, Math.min(custom, 1), text);
        if (index % 7 == 0 && legacy.getWorldPlayers().size() > 1) {
            String removed = legacy.getWorldPlayers().keySet().stream().skip(1).findFirst().orElseThrow();
            legacy.getWorldPlayers().remove(removed);
            removeAliasesTargeting(legacy, removed);
        }
        return new Pair(canonical, legacy);
    }

    private Pair stressPair(UUID owner, int scale) throws Exception {
        WorldSnapshot canonical = base(owner, scale, false, 12);
        WorldSnapshot legacy = copy(canonical);
        String longUnicode = "Á⚽".repeat(170) + "xy";
        for (WorldLeague league : legacy.getLeagues()) {
            league.setName(longUnicode);
            league.setCountry(longUnicode);
            league.setTier(3);
        }
        int i = 0;
        Map<String, String> aliases = new LinkedHashMap<>(legacy.getWorldPlayerAliases());
        for (WorldPlayer player : legacy.getAllWorldPlayers()) {
            player.setName(longUnicode);
            player.setPosition(longUnicode);
            player.setBaseAttack(99);
            player.setWorldTeamId(legacy.getAllWorldTeams().getFirst().getWorldTeamId());
            EnumMap<PlayerSkill, Integer> skills = new EnumMap<>(PlayerSkill.class);
            for (PlayerSkill skill : PlayerSkill.values()) skills.put(skill, 5);
            player.setSkillLevels(skills);
            List<PlayerSpecialTrait> traits = new ArrayList<>();
            for (int trait = 0; trait < 32; trait++) {
                traits.add(new PlayerSpecialTrait(player.getRealPlayerId(), "T" + trait,
                        longUnicode, longUnicode));
            }
            player.setSpecialTraits(traits);
            aliases.put("stress-alias-" + i, player.getWorldPlayerId());
            i++;
        }
        legacy.setWorldPlayerAliases(aliases);
        WorldTeam team = legacy.getAllWorldTeams().getFirst();
        team.setName(longUnicode);
        team.setCountry(longUnicode);
        team.setCity(longUnicode);
        team.setBaseFormation(longUnicode);
        addCustomEntities(legacy, owner, scale, Math.max(1, scale / 4), longUnicode);
        if (scale > 1) {
            String removed = legacy.getWorldPlayers().keySet().stream().findFirst().orElseThrow();
            legacy.getWorldPlayers().remove(removed);
            removeAliasesTargeting(legacy, removed);
        }
        return new Pair(canonical, legacy);
    }

    private static void removeAliasesTargeting(WorldSnapshot snapshot, String removedPlayerId) {
        Map<String, String> surviving = new LinkedHashMap<>(snapshot.getWorldPlayerAliases());
        surviving.entrySet().removeIf(entry -> removedPlayerId.equals(entry.getValue())
                || removedPlayerId.equals(entry.getKey()));
        snapshot.setWorldPlayerAliases(surviving);
    }

    private void addCustomEntities(WorldSnapshot snapshot, UUID owner, int players, int teams, String text) {
        List<String> customTeamIds = new ArrayList<>();
        for (int i = 0; i < teams; i++) {
            WorldTeam team = WorldTeam.createCustom(text + i, text, BigDecimal.TEN, text);
            team.setWorldTeamId(UUID.nameUUIDFromBytes(("custom-team-" + owner + "-" + i)
                    .getBytes(StandardCharsets.UTF_8)).toString());
            snapshot.getWorldTeams().put(team.getWorldTeamId(), team);
            customTeamIds.add(team.getWorldTeamId());
        }
        for (int i = 0; i < players; i++) {
            WorldPlayer player = WorldPlayer.createCustom(text + i, 22, "MID",
                    70, 70, 70, 70, 70, 70, BigDecimal.TEN);
            player.setWorldPlayerId(UUID.nameUUIDFromBytes(("custom-player-" + owner + "-" + i)
                    .getBytes(StandardCharsets.UTF_8)).toString());
            if (!customTeamIds.isEmpty()) player.setWorldTeamId(customTeamIds.get(i % customTeamIds.size()));
            snapshot.getWorldPlayers().put(player.getWorldPlayerId(), player);
        }
    }

    private Physical physicalState(UUID owner, String legacyRaw, long legacyMemory, long baseline) throws Exception {
        String worldKey = "world:" + owner;
        String committedRaw = reactiveRedisTemplate.opsForValue().get(worldKey).block();
        var committed = objectMapper.readTree(committedRaw);
        String catalogKey = committed.path("catalogKey").asText();
        String catalogRaw = reactiveRedisTemplate.opsForValue().get(catalogKey).block();
        var prepared = new RedisWorldRepository.PreparedWorldMigrationEnvelope(
                2, "PREPARED", owner, catalogKey, committed.path("catalogFingerprint").asText(),
                compress(legacyRaw), sha256(legacyRaw));
        String preparedRaw = objectMapper.writeValueAsString(prepared);
        String probe = "world-audit-prepared:" + owner;
        reactiveRedisTemplate.opsForValue().set(probe, preparedRaw, Duration.ofMinutes(5)).block();
        long preparedMemory = memoryUsage(probe);
        long committedMemory = memoryUsage(worldKey);
        long catalogMemory = memoryUsage(catalogKey);
        long peak = Math.max(baseline - legacyMemory + preparedMemory + catalogMemory,
                baseline - legacyMemory + committedMemory + catalogMemory);
        return new Physical(peak, catalogKey, probe);
    }

    private RedisWorldRepository repository() {
        RedisWorldRepository repository = new RedisWorldRepository(reactiveRedisTemplate, objectMapper);
        ReflectionTestUtils.setField(repository, "worldTtl", Duration.ofDays(30));
        ReflectionTestUtils.setField(repository, "catalogTtl", Duration.ofDays(365));
        ReflectionTestUtils.setField(repository, "storageVersion", 2);
        return repository;
    }

    private WorldSnapshot copy(WorldSnapshot source) {
        try {
            return objectMapper.readValue(objectMapper.writeValueAsBytes(source), WorldSnapshot.class);
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    private static WorldSnapshot base(UUID owner, int players, boolean unused, int textSize) {
        UUID leagueId = UUID.nameUUIDFromBytes(("league-" + owner).getBytes(StandardCharsets.UTF_8));
        UUID teamId = UUID.nameUUIDFromBytes(("team-" + owner).getBytes(StandardCharsets.UTF_8));
        WorldSnapshot snapshot = new WorldSnapshot();
        snapshot.setUserId(owner);
        snapshot.setCreatedAt(Instant.parse("2026-08-01T00:00:00Z"));
        snapshot.setLastUpdated(Instant.parse("2026-08-02T00:00:00Z"));
        snapshot.setLeagues(List.of(new WorldLeague(leagueId, "L".repeat(textSize), "AR", 1)));
        WorldTeam team = WorldTeam.fromRealTeam(teamId, leagueId, "T".repeat(textSize), "AR", "C",
                BigDecimal.TEN, "4-4-2");
        snapshot.setWorldTeams(new LinkedHashMap<>(Map.of(team.getWorldTeamId(), team)));
        Map<String, WorldPlayer> values = new LinkedHashMap<>();
        for (int i = 0; i < players; i++) {
            UUID playerId = UUID.nameUUIDFromBytes(("player-" + owner + "-" + i)
                    .getBytes(StandardCharsets.UTF_8));
            WorldPlayer player = WorldPlayer.fromCanonicalPlayer(owner, playerId, team.getWorldTeamId(),
                    "P" + i, 22, "MID", 70, 71, 72, 73, 74, 75, BigDecimal.TEN);
            values.put(player.getWorldPlayerId(), player);
        }
        snapshot.setWorldPlayers(values);
        snapshot.setWorldPlayerAliases(new LinkedHashMap<>());
        return snapshot;
    }

    private long memoryUsage(String key) throws Exception {
        RedisConnectionFactory factory = (RedisConnectionFactory) reactiveRedisTemplate.getConnectionFactory();
        try (RedisConnection connection = factory.getConnection()) {
            Object nativeConnection = connection.getNativeConnection();
            byte[] encoded = key.getBytes(StandardCharsets.UTF_8);
            if (nativeConnection instanceof RedisCommands<?, ?> commands) {
                @SuppressWarnings("unchecked") RedisCommands<byte[], byte[]> binary =
                        (RedisCommands<byte[], byte[]>) commands;
                return binary.memoryUsage(encoded);
            }
            if (nativeConnection instanceof RedisAsyncCommands<?, ?> commands) {
                @SuppressWarnings("unchecked") RedisAsyncCommands<byte[], byte[]> binary =
                        (RedisAsyncCommands<byte[], byte[]>) commands;
                return binary.memoryUsage(encoded).get();
            }
            if (nativeConnection instanceof StatefulRedisConnection<?, ?> stateful) {
                @SuppressWarnings("unchecked") StatefulRedisConnection<byte[], byte[]> binary =
                        (StatefulRedisConnection<byte[], byte[]>) stateful;
                return binary.sync().memoryUsage(encoded);
            }
            throw new IllegalStateException("Unsupported Redis connection");
        }
    }

    private static String compress(String value) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
            gzip.write(value.getBytes(StandardCharsets.UTF_8));
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray());
    }

    private static String sha256(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder();
        for (byte item : digest) result.append(String.format("%02x", item));
        return result.toString();
    }

    private record Pair(WorldSnapshot canonical, WorldSnapshot legacy) { }
    private record Physical(long peak, String catalogKey, String preparedProbe) { }
}
