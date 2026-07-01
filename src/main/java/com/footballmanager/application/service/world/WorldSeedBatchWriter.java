package com.footballmanager.application.service.world;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.footballmanager.domain.model.entity.Player;
import com.footballmanager.domain.model.entity.WorldPlayer;
import com.footballmanager.domain.model.valueobject.PlayerSkill;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * V25D78-C55.4: Batch PostgreSQL persistence for seed player upserts.
 *
 * <p>Replaces the per-row INSERT loop used by {@code LaLigaSeedService} and
 * {@code WorldSeedService} (C55.3 B1 implementation), which was inserting
 * ~9000 rows one round-trip at a time during a full {@code POST /world/seed-all}.
 * That sequence took ~10 seconds, exceeding the Spring WebClient default
 * response timeout of 5s and breaking the C55.1
 * {@code WorldSeedControllerC55E2ETest.seedAll_*} tests.
 *
 * <p>Strategy: build per-column {@code Object[]} arrays and call
 * PostgreSQL's {@code unnest()} in a single INSERT statement per batch.
 * A league with ~900 players now requires ~10 round-trips instead of ~900.
 *
 * <p>Schema mirrors the original per-row INSERT: 16 columns including
 * height_cm (V25D32-F3) and skill_levels_json (V25D32-F3).
 *
 * <p>ON CONFLICT behaviour is preserved: the original UPDATE clause renames
 * the player and updates the hardcoded-height/top-5-skills columns only,
 * leaving attack/defense/technique/speed/stamina/mentality untouched (those
 * come from the seed JSON, which itself doesn't get edited after write).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorldSeedBatchWriter {

    /** Default batch size — tuned for Postgres parameter limits. */
    public static final int DEFAULT_BATCH_SIZE = 200;

    /** Block timeout for batch INSERT round-trips. */
    private static final Duration BLOCK_TIMEOUT = Duration.ofSeconds(30);

    private final DatabaseClient databaseClient;
    private final ObjectMapper objectMapper;

    /**
     * Upsert players in batched INSERTs.
     *
     * @param players  the players to persist (all must have {@code realPlayerId})
     * @param mapper   maps the String position to {@link Player.Position} enum
     * @return         number of players actually upserted (excludes skipped)
     */
    public int upsertPlayersBatched(List<WorldPlayer> players,
                                    Function<String, Player.Position> mapper) {
        // Drop players without realPlayerId (can't be persisted as a Postgres row).
        List<WorldPlayer> toPersist = players.stream()
                .filter(wp -> wp.getRealPlayerId() != null)
                .toList();
        if (toPersist.isEmpty()) {
            log.info("[BATCH-WRITE] no players to persist (skipped={})",
                    players.size());
            return 0;
        }

        // First, bulk-clean team_squad entries for affected teams (matches
        // per-row DELETE behaviour — only happens once before the batch loop).
        Set<UUID> teamIds = toPersist.stream()
                .map(WorldPlayer::getWorldTeamId)
                .filter(s -> s != null)
                .map(s -> {
                    try { return UUID.fromString(s); }
                    catch (Exception e) { return null; }
                })
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        if (!teamIds.isEmpty()) {
            try {
                databaseClient.sql("DELETE FROM team_squad WHERE team_id = ANY(:teamIds)")
                        .bind("teamIds", teamIds.toArray(UUID[]::new))
                        .fetch().rowsUpdated().block(BLOCK_TIMEOUT);
            } catch (Exception e) {
                log.warn("[BATCH-WRITE] team_squad cleanup failed: {}", e.getMessage());
            }
        }

        Instant now = Instant.now();
        int totalWritten = 0;
        for (int from = 0; from < toPersist.size(); from += DEFAULT_BATCH_SIZE) {
            int to = Math.min(from + DEFAULT_BATCH_SIZE, toPersist.size());
            List<WorldPlayer> chunk = toPersist.subList(from, to);
            try {
                upsertPlayerChunk(chunk, mapper, now);
                totalWritten += chunk.size();
            } catch (Exception e) {
                log.error("[BATCH-WRITE] chunk {}-{} failed: {}", from, to, e.getMessage());
                // Fall back to per-row to salvage what we can; this is the
                // safety net for any malformed row that breaks a whole batch.
                totalWritten += upsertPlayersPerRow(chunk, mapper, now);
            }
        }
        log.info("[BATCH-WRITE] persisted total={} (input={})", totalWritten, players.size());

        // Second pass: batched team_squad inserts (one INSERT per chunk).
        int totalSquad = 0;
        for (int from = 0; from < toPersist.size(); from += DEFAULT_BATCH_SIZE) {
            int to = Math.min(from + DEFAULT_BATCH_SIZE, toPersist.size());
            List<WorldPlayer> chunk = toPersist.subList(from, to);
            totalSquad += upsertTeamSquadChunk(chunk);
        }
        log.info("[BATCH-WRITE] squad entries total={}", totalSquad);

        return totalWritten;
    }

    /**
     * One batched INSERT using PostgreSQL {@code unnest()} to expand column
     * arrays into rows. ON CONFLICT preserves the legacy UPDATE semantics
     * (rename + height + skills only).
     */
    private void upsertPlayerChunk(List<WorldPlayer> chunk,
                                   Function<String, Player.Position> mapper,
                                   Instant now) {
        int n = chunk.size();
        Object[] ids        = new Object[n];
        Object[] names      = new String[n];
        Object[] ages       = new Integer[n];
        Object[] positions  = new String[n];
        Object[] attacks    = new Integer[n];
        Object[] defenses   = new Integer[n];
        Object[] techniques = new Integer[n];
        Object[] speeds     = new Integer[n];
        Object[] staminas   = new Integer[n];
        Object[] mentalities= new Integer[n];
        Object[] mvs        = new BigDecimal[n];
        Object[] energies   = new Integer[n];
        Object[] injureds   = new Boolean[n];
        Object[] createdAts = new Instant[n];
        Object[] updatedAts = new Instant[n];
        Object[] heightCms  = new Integer[n];
        Object[] skillJsons = new String[n];

        for (int i = 0; i < n; i++) {
            WorldPlayer wp = chunk.get(i);
            ids[i] = wp.getRealPlayerId();
            names[i] = wp.getName();
            ages[i] = wp.getAge() != null ? wp.getAge() : 25;
            positions[i] = mapper.apply(wp.getPosition()).name();
            attacks[i] = wp.getBaseAttack() != null ? wp.getBaseAttack() : 50;
            defenses[i] = wp.getBaseDefense() != null ? wp.getBaseDefense() : 50;
            techniques[i] = wp.getBaseTechnique() != null ? wp.getBaseTechnique() : 50;
            speeds[i] = wp.getBaseSpeed() != null ? wp.getBaseSpeed() : 50;
            staminas[i] = wp.getBaseStamina() != null ? wp.getBaseStamina() : 50;
            mentalities[i] = wp.getBaseMentality() != null ? wp.getBaseMentality() : 50;
            mvs[i] = wp.getBaseMarketValue() != null
                    ? wp.getBaseMarketValue()
                    : BigDecimal.valueOf(5_000_000L);
            energies[i] = 100;
            injureds[i] = Boolean.FALSE;
            createdAts[i] = now;
            updatedAts[i] = now;
            Integer h = wp.getHeightCm();
            heightCms[i] = h != null ? h : 0;
            skillJsons[i] = serializeSkillLevelsOrNull(wp.getSkillLevels());
        }

        // Cast Object[] → typed arrays so R2DBC + Postgres get correct type info.
        // Without the cast, Object[] falls back to the default Java type which
        // R2DBC sends as a text array, breaking the unnest(:ids::uuid[]) cast.
        UUID[] idsArr = toUuidArray(ids);
        String[] namesArr = toStringArray(names);
        Integer[] agesArr = toBoxedIntArray(ages);
        String[] positionsArr = toStringArray(positions);
        Integer[] attacksArr = toBoxedIntArray(attacks);
        Integer[] defensesArr = toBoxedIntArray(defenses);
        Integer[] techniquesArr = toBoxedIntArray(techniques);
        Integer[] speedsArr = toBoxedIntArray(speeds);
        Integer[] staminasArr = toBoxedIntArray(staminas);
        Integer[] mentalitiesArr = toBoxedIntArray(mentalities);
        BigDecimal[] mvsArr = toBigDecimalArray(mvs);
        Integer[] energiesArr = toBoxedIntArray(energies);
        Boolean[] injuredsArr = toBoxedBooleanArray(injureds);
        Instant[] createdAtsArr = toInstantArray(createdAts);
        Instant[] updatedAtsArr = toInstantArray(updatedAts);
        Integer[] heightCmsArr = toBoxedIntArray(heightCms);
        String[] skillJsonsArr = toStringArray(skillJsons);

        databaseClient.sql("""
            INSERT INTO players
                (id, name, age, position, attack, defense, technique, speed, stamina, mentality,
                 market_value, energy, injured, created_at, updated_at, height_cm, skill_levels_json)
            SELECT * FROM unnest(
                :ids::uuid[], :names::text[], :ages::int[], :positions::text[],
                :attacks::int[], :defenses::int[], :techniques::int[], :speeds::int[],
                :staminas::int[], :mentalities::int[], :mvs::numeric[],
                :energies::int[], :injureds::boolean[],
                :createdAts::timestamp[], :updatedAts::timestamp[],
                :heightCms::int[], :skillJsons::text[]
            )
            ON CONFLICT (id) DO UPDATE SET
                name = EXCLUDED.name,
                height_cm = EXCLUDED.height_cm,
                skill_levels_json = EXCLUDED.skill_levels_json
            """)
            .bind("ids", idsArr)
            .bind("names", namesArr)
            .bind("ages", agesArr)
            .bind("positions", positionsArr)
            .bind("attacks", attacksArr)
            .bind("defenses", defensesArr)
            .bind("techniques", techniquesArr)
            .bind("speeds", speedsArr)
            .bind("staminas", staminasArr)
            .bind("mentalities", mentalitiesArr)
            .bind("mvs", mvsArr)
            .bind("energies", energiesArr)
            .bind("injureds", injuredsArr)
            .bind("createdAts", createdAtsArr)
            .bind("updatedAts", updatedAtsArr)
            .bind("heightCms", heightCmsArr)
            .bind("skillJsons", skillJsonsArr)
            .fetch().rowsUpdated().block(BLOCK_TIMEOUT);
    }

    /**
     * Per-row fallback for chunks where the batch INSERT fails. Preserves the
     * legacy per-row INSERT behaviour so a single bad row doesn't lose a
     * whole batch.
     */
    private int upsertPlayersPerRow(List<WorldPlayer> chunk,
                                    Function<String, Player.Position> mapper,
                                    Instant now) {
        int written = 0;
        for (WorldPlayer wp : chunk) {
            try {
                databaseClient.sql("""
                    INSERT INTO players (id, name, age, position, attack, defense, technique, speed, stamina, mentality, market_value, energy, injured, created_at, updated_at, height_cm, skill_levels_json)
                    VALUES (:id, :name, :age, :position, :attack, :defense, :technique, :speed, :stamina, :mentality, :mv, :energy, :injured, :createdAt, :updatedAt, :heightCm, :skillLevelsJson)
                    ON CONFLICT (id) DO UPDATE SET
                        name = EXCLUDED.name,
                        height_cm = EXCLUDED.height_cm,
                        skill_levels_json = EXCLUDED.skill_levels_json
                    """)
                    .bind("id", wp.getRealPlayerId())
                    .bind("name", wp.getName())
                    .bind("age", wp.getAge() != null ? wp.getAge() : 25)
                    .bind("position", mapper.apply(wp.getPosition()).name())
                    .bind("attack", wp.getBaseAttack() != null ? wp.getBaseAttack() : 50)
                    .bind("defense", wp.getBaseDefense() != null ? wp.getBaseDefense() : 50)
                    .bind("technique", wp.getBaseTechnique() != null ? wp.getBaseTechnique() : 50)
                    .bind("speed", wp.getBaseSpeed() != null ? wp.getBaseSpeed() : 50)
                    .bind("stamina", wp.getBaseStamina() != null ? wp.getBaseStamina() : 50)
                    .bind("mentality", wp.getBaseMentality() != null ? wp.getBaseMentality() : 50)
                    .bind("mv", wp.getBaseMarketValue() != null
                            ? wp.getBaseMarketValue()
                            : BigDecimal.valueOf(5_000_000L))
                    .bind("energy", 100)
                    .bind("injured", false)
                    .bind("createdAt", now)
                    .bind("updatedAt", now)
                    .bind("heightCm", wp.getHeightCm() != null ? wp.getHeightCm() : 0)
                    .bind("skillLevelsJson", serializeSkillLevelsOrNull(wp.getSkillLevels()))
                    .fetch().rowsUpdated().block(BLOCK_TIMEOUT);
                written++;
            } catch (Exception e) {
                log.warn("[BATCH-WRITE] per-row fallback failed for {}: {}",
                        wp.getName(), e.getMessage());
            }
        }
        return written;
    }

    /**
     * Bulk insert team_squad rows for the chunk. Skips players without
     * worldTeamId (no FK target). Returns the number of squad entries written.
     */
    private int upsertTeamSquadChunk(List<WorldPlayer> chunk) {
        List<WorldPlayer> withTeam = chunk.stream()
                .filter(wp -> wp.getWorldTeamId() != null && wp.getRealPlayerId() != null)
                .toList();
        if (withTeam.isEmpty()) return 0;

        int n = withTeam.size();
        Object[] teamIdsArr = new UUID[n];
        Object[] playerIdsArr = new UUID[n];
        for (int i = 0; i < n; i++) {
            WorldPlayer wp = withTeam.get(i);
            teamIdsArr[i] = UUID.fromString(wp.getWorldTeamId());
            playerIdsArr[i] = wp.getRealPlayerId();
        }
        try {
            databaseClient.sql("""
                INSERT INTO team_squad (team_id, player_id)
                SELECT * FROM unnest(:teamIds::uuid[], :playerIds::uuid[])
                ON CONFLICT DO NOTHING
                """)
                .bind("teamIds", teamIdsArr)
                .bind("playerIds", playerIdsArr)
                .fetch().rowsUpdated().block(BLOCK_TIMEOUT);
            return n;
        } catch (Exception e) {
            log.warn("[BATCH-WRITE] team_squad chunk failed: {}", e.getMessage());
            return 0;
        }
    }

    /** Serialize skillLevels map to JSON for storage; returns "{}" for empty/null. */
    private String serializeSkillLevelsOrNull(Map<PlayerSkill, Integer> skills) {
        if (skills == null || skills.isEmpty()) return "{}";
        try {
            return objectMapper.writeValueAsString(skills);
        } catch (JsonProcessingException e) {
            log.warn("[BATCH-WRITE] skillLevels serialization failed: {}", e.getMessage());
            return "{}";
        }
    }

    // ========== Typed-array conversions (Object[] → primitive-friendly typed arrays) ==========
    // R2DBC binds Object[] as a generic array which the Postgres driver then has
    // to guess the element type for. The casts in the SQL (`::uuid[]`,
    // `::text[]`, etc.) only succeed if the driver is told the array type up
    // front. These helpers produce the typed arrays the binder needs.

    private static UUID[] toUuidArray(Object[] src) {
        UUID[] dst = new UUID[src.length];
        for (int i = 0; i < src.length; i++) dst[i] = (UUID) src[i];
        return dst;
    }

    private static String[] toStringArray(Object[] src) {
        String[] dst = new String[src.length];
        for (int i = 0; i < src.length; i++) dst[i] = (String) src[i];
        return dst;
    }

    private static Integer[] toBoxedIntArray(Object[] src) {
        Integer[] dst = new Integer[src.length];
        for (int i = 0; i < src.length; i++) dst[i] = (Integer) src[i];
        return dst;
    }

    private static Boolean[] toBoxedBooleanArray(Object[] src) {
        Boolean[] dst = new Boolean[src.length];
        for (int i = 0; i < src.length; i++) dst[i] = (Boolean) src[i];
        return dst;
    }

    private static BigDecimal[] toBigDecimalArray(Object[] src) {
        BigDecimal[] dst = new BigDecimal[src.length];
        for (int i = 0; i < src.length; i++) dst[i] = (BigDecimal) src[i];
        return dst;
    }

    private static Instant[] toInstantArray(Object[] src) {
        Instant[] dst = new Instant[src.length];
        for (int i = 0; i < src.length; i++) dst[i] = (Instant) src[i];
        return dst;
    }
}
