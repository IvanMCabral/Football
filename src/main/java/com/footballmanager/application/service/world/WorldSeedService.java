package com.footballmanager.application.service.world;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.footballmanager.adapters.out.redis.RedisWorldRepository;
import com.footballmanager.domain.model.entity.WorldLeague;
import com.footballmanager.domain.model.entity.WorldPlayer;
import com.footballmanager.domain.model.entity.WorldSnapshot;
import com.footballmanager.domain.model.entity.WorldTeam;
import com.footballmanager.domain.model.valueobject.PlayerSkill;
import com.footballmanager.domain.ports.out.player.PlayerRepository;
import com.footballmanager.domain.model.entity.Player.Position;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * V25D78-C55.1: Multi-league seed service. Seeds one or more of the 10
 * "Top 10 mundial" leagues (La Liga, Premier, Bundesliga, Serie A, Ligue 1,
 * Brasileirão, Liga Profesional, MLS, Eredivisie, Championship) into the
 * user's {@link WorldSnapshot} + Postgres.
 *
 * <p><b>Architecture decision:</b> this class is a parallel to
 * {@link LaLigaSeedService}, not a refactor. LaLigaSeedService is
 * battle-tested (C44 + V25D32 + V25D36 + V25D37 unit tests, ~10 tests
 * cover its specific code paths including the V25D36-F4 per-call
 * {@link PlayerAttributesGenerator} thread-safety fix). Refactoring it
 * to be fully generic would risk regressions for marginal gain. Instead,
 * WorldSeedService delegates La Liga to the existing service and implements
 * the same UPSERT pattern inline for the other 9 leagues.
 *
 * <p><b>Idempotency:</b> same contract as LaLigaSeedService. Re-running
 * with the same resource file produces the same WorldSnapshot content
 * (byte-for-byte for a deterministic seed).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorldSeedService {

    private final WorldSnapshotService snapshotService;
    private final ObjectMapper objectMapper;
    private final RedisWorldRepository worldRepository;
    private final PlayerRepository playerRepository;
    private final DatabaseClient databaseClient;
    private final LaLigaSeedService laLigaSeedService;
    private final WorldSeedBatchWriter batchWriter;

    /**
     * Seeds a single league. Delegates La Liga to the existing service
     * (preserves all regression tests); implements the generic pattern
     * inline for the other 9 leagues.
     *
     * @param leagueType one of the 10 enum values
     * @param userId     the user whose WorldSnapshot gets the league
     * @return Mono with the {@link SeedResult} summary
     */
    public Mono<SeedResult> seedLeague(LeagueType leagueType, UUID userId) {
        if (leagueType == LeagueType.LALIGA) {
            // Delegate — preserves all LaLigaSeedService unit tests. Map the
            // legacy SeedResult (LaLigaSeedService.SeedResult) to ours so callers
            // see a single SeedResult type.
            return laLigaSeedService.execute(userId)
                    .map(ll -> new SeedResult(ll.leagueName(), ll.teamsCount(),
                            ll.playersCount(), ll.durationMs()));
        }
        long start = System.currentTimeMillis();
        PlayerAttributesGenerator gen = new PlayerAttributesGenerator();
        String logPrefix = "[" + leagueType.slug().toUpperCase() + "-SEED]";
        log.info("{} starting for userId={}", logPrefix, userId);

        return loadSeedData(leagueType.resourcePath())
                .flatMap(seed -> snapshotService.getSnapshot(userId)
                        .flatMap(snapshot -> applySeed(userId, snapshot, seed, gen, logPrefix, start)));
    }

    /**
     * Seeds ALL 10 leagues (idempotent — can be called repeatedly to top up
     * missing leagues). Used by {@code POST /world/seed-all}.
     *
     * <p>Reuses {@link LaLigaSeedService} for La Liga and applies each other
     * league sequentially. Sequential is OK because the seed operations are
     * fast (mostly Redis reads + writes) and concurrent writes to the same
     * WorldSnapshot would race anyway.
     */
    public Mono<AllSeedResult> seedAllLeagues(UUID userId) {
        log.info("[WORLD-SEED-ALL] starting for userId={}", userId);
        List<SeedResult> results = new ArrayList<>();
        // Use concatMap (not flatMap) so each seedLeague waits for the previous
        // one to finish its saveSnapshot. flatMap runs in parallel by default
        // (concurrency=256), which causes race conditions: multiple parallel
        // calls all read the same empty snapshot, each adds its league, the last
        // saveSnapshot overwrites the others, and only 1 league survives.
        return Flux.fromArray(LeagueType.values())
                .concatMap(lt -> seedLeague(lt, userId)
                        .doOnNext(results::add)
                        .onErrorResume(e -> {
                            log.warn("[WORLD-SEED-ALL] league {} failed: {}", lt.slug(), e.getMessage());
                            return Mono.empty();
                        }))
                .collectList()
                .map(seedResults -> new AllSeedResult(results));
    }

    // ========== Loading ==========

    private Mono<LaLigaSeedData> loadSeedData(String resourcePath) {
        return Mono.fromCallable(() -> {
            try (InputStream in = new ClassPathResource(resourcePath).getInputStream()) {
                return objectMapper.readValue(in, LaLigaSeedData.class);
            }
        });
    }

    // ========== Seed application ==========

    private Mono<SeedResult> applySeed(UUID userId, WorldSnapshot snapshot,
                                       LaLigaSeedData seed,
                                       PlayerAttributesGenerator gen,
                                       String logPrefix, long start) {
        UUID leagueId = ensureLeague(snapshot, seed);
        Map<String, WorldTeam> teamsByName = ensureTeams(snapshot, seed, leagueId);
        List<WorldPlayer> players = ensurePlayers(snapshot, seed, teamsByName, gen);
        persistPlayerNamesInPostgres(userId, players, logPrefix);
        // V25D78-C55.3 B1: also persist team rows + league_id so that
        // V25D80 migration can distribute divisions per-league. Uses a
        // sentinel manager_id (00000000-0000-0000-0000-000000000000) for
        // synthetic teams that have no real user manager.
        persistTeamsInPostgres(new ArrayList<>(teamsByName.values()), leagueId, logPrefix);

        return snapshotService.saveSnapshot(snapshot)
                .map(saved -> {
                    long dur = System.currentTimeMillis() - start;
                    log.info("{} done: teams={} players={} durationMs={}",
                            logPrefix, teamsByName.size(), players.size(), dur);
                    return new SeedResult(seed.league().name(),
                            teamsByName.size(), players.size(), dur);
                });
    }

    private UUID ensureLeague(WorldSnapshot snapshot, LaLigaSeedData seed) {
        if (snapshot.getLeagues() == null) snapshot.setLeagues(new ArrayList<>());
        // Idempotency: check by NAME (not by UUID). Real-league IDs from
        // Postgres (loaded via snapshotCreator.create) use the leagues.id UUID
        // column, while seed-only leagues use a name-derived UUID. The only
        // stable identifier across both sources is the league name.
        String seedLeagueName = seed.league().name();
        String seedCountry = seed.league().country() == null ? "" : seed.league().country();
        WorldLeague existing = snapshot.getLeagues().stream()
                .filter(l -> l.getName() != null && l.getName().equals(seedLeagueName))
                .findFirst().orElse(null);
        if (existing != null) {
            return existing.getRealLeagueId();
        }
        UUID leagueId = UUID.nameUUIDFromBytes(
                (seedLeagueName + "|" + seedCountry).getBytes());
        snapshot.getLeagues().add(
                WorldLeague.fromRealLeague(leagueId,
                        seedLeagueName,
                        seedCountry,
                        seed.league().tier() == null ? 1 : seed.league().tier()));
        return leagueId;
    }

    private Map<String, WorldTeam> ensureTeams(WorldSnapshot snapshot,
                                                LaLigaSeedData seed, UUID leagueId) {
        if (snapshot.getWorldTeams() == null) snapshot.setWorldTeams(new HashMap<>());
        Map<String, WorldTeam> byName = indexTeamsByName(snapshot);
        Map<String, WorldTeam> result = new HashMap<>();
        for (LaLigaSeedData.TeamDto dto : seed.teams()) {
            String key = dto.name().toLowerCase();
            WorldTeam existing = byName.get(key);
            if (existing != null) {
                updateTeamFromDto(existing, dto, leagueId);
                result.put(key, existing);
            } else {
                WorldTeam created = createTeamFromDto(dto, leagueId, seed.league().country());
                snapshot.getWorldTeams().put(created.getWorldTeamId(), created);
                result.put(key, created);
            }
        }
        return result;
    }

    private Map<String, WorldTeam> indexTeamsByName(WorldSnapshot snapshot) {
        Map<String, WorldTeam> map = new HashMap<>();
        for (WorldTeam t : snapshot.getWorldTeams().values()) {
            if (t.getName() != null) map.put(t.getName().toLowerCase(), t);
        }
        return map;
    }

    private WorldTeam createTeamFromDto(LaLigaSeedData.TeamDto dto,
                                        UUID leagueId, String country) {
        UUID teamId = UUID.nameUUIDFromBytes(("team|" + dto.name()).getBytes());
        BigDecimal budget = BigDecimal.valueOf(dto.budgetMillions() == null ? 50L : dto.budgetMillions())
                .multiply(BigDecimal.valueOf(1_000_000L));
        return WorldTeam.fromRealTeam(teamId, leagueId, dto.name(),
                country == null ? "" : country,
                dto.city(), budget,
                dto.formation() == null ? "4-3-3" : dto.formation());
    }

    private void updateTeamFromDto(WorldTeam team, LaLigaSeedData.TeamDto dto, UUID leagueId) {
        if (dto.formation() != null) team.setBaseFormation(dto.formation());
        if (dto.budgetMillions() != null) {
            team.setBaseBudget(BigDecimal.valueOf(dto.budgetMillions())
                    .multiply(BigDecimal.valueOf(1_000_000L)));
        }
        if (dto.city() != null) team.setCity(dto.city());
        if (leagueId != null) team.setRealLeagueId(leagueId);
    }

    private List<WorldPlayer> ensurePlayers(WorldSnapshot snapshot, LaLigaSeedData seed,
                                            Map<String, WorldTeam> teamsByName,
                                            PlayerAttributesGenerator gen) {
        if (snapshot.getWorldPlayers() == null) snapshot.setWorldPlayers(new HashMap<>());
        Map<String, WorldPlayer> existing = indexPlayersByTeamAndName(snapshot);
        List<WorldPlayer> affected = new ArrayList<>();
        for (LaLigaSeedData.PlayerDto dto : seed.players()) {
            String teamKey = dto.team().toLowerCase();
            WorldTeam team = teamsByName.get(teamKey);
            if (team == null) {
                log.warn("seed: player {} references unknown team {}", dto.name(), dto.team());
                continue;
            }
            String key = (team.getWorldTeamId() + "|" + dto.name()).toLowerCase();
            WorldPlayer wp = existing.get(key);
            if (wp != null) {
                updatePlayerFromDto(wp, dto, team, gen);
                affected.add(wp);
            } else {
                wp = createPlayerFromDto(dto, team, gen);
                snapshot.getWorldPlayers().put(wp.getWorldPlayerId(), wp);
                affected.add(wp);
            }
        }
        return affected;
    }

    private Map<String, WorldPlayer> indexPlayersByTeamAndName(WorldSnapshot snapshot) {
        Map<String, WorldPlayer> map = new HashMap<>();
        for (WorldPlayer p : snapshot.getWorldPlayers().values()) {
            if (p.getWorldTeamId() != null && p.getName() != null) {
                map.put((p.getWorldTeamId() + "|" + p.getName()).toLowerCase(), p);
            }
        }
        return map;
    }

    private WorldPlayer createPlayerFromDto(LaLigaSeedData.PlayerDto dto, WorldTeam team,
                                           PlayerAttributesGenerator gen) {
        UUID pid = UUID.nameUUIDFromBytes(
                ("player|" + team.getName() + "|" + dto.name()).getBytes());
        BigDecimal mv = calculateMarketValue(
                dto.baseAttack(), dto.baseDefense(), dto.baseTechnique(),
                dto.baseSpeed(), dto.baseStamina(), dto.baseMentality(), dto.age());
        WorldPlayer wp = WorldPlayer.fromRealPlayer(
                pid, team.getWorldTeamId(),
                dto.name(), dto.age(),
                dto.position() == null ? "MID" : dto.position(),
                dto.baseAttack(), dto.baseDefense(),
                dto.baseTechnique(), dto.baseSpeed(),
                dto.baseStamina(), dto.baseMentality(),
                mv);
        wp.setHeightCm(dto.heightCm() != null ? dto.heightCm() : gen.generateHeightCm());
        Map<PlayerSkill, Integer> skills = dto.skillLevels() != null
                ? dto.skillLevels()
                : gen.generateSkillLevels();
        wp.setSkillLevels(skills);
        return wp;
    }

    private void updatePlayerFromDto(WorldPlayer wp, LaLigaSeedData.PlayerDto dto,
                                     WorldTeam team, PlayerAttributesGenerator gen) {
        if (dto.age() != null) wp.setAge(dto.age());
        if (dto.baseAttack() != null) wp.setBaseAttack(dto.baseAttack());
        if (dto.baseDefense() != null) wp.setBaseDefense(dto.baseDefense());
        if (dto.baseTechnique() != null) wp.setBaseTechnique(dto.baseTechnique());
        if (dto.baseSpeed() != null) wp.setBaseSpeed(dto.baseSpeed());
        if (dto.baseStamina() != null) wp.setBaseStamina(dto.baseStamina());
        if (dto.baseMentality() != null) wp.setBaseMentality(dto.baseMentality());
        if (dto.position() != null) wp.setPosition(dto.position());
        if (dto.heightCm() != null) wp.setHeightCm(dto.heightCm());
        if (dto.skillLevels() != null) wp.setSkillLevels(dto.skillLevels());
    }

    /** Map MANAGER's 5-cat code (GK/DEF/MID/WINGER/ATT) → {@link Position} enum for the
     *  Postgres `players` table (which uses the 15-role enum). */
    private Position mapPositionString(String pos) {
        if (pos == null) return Position.CM;
        return switch (pos.toUpperCase()) {
            case "GK" -> Position.GK;
            case "DEF" -> Position.CB;
            case "MID" -> Position.CM;
            case "WINGER" -> Position.LW;
            case "ATT" -> Position.ST;
            default -> Position.CM;
        };
    }

    private BigDecimal calculateMarketValue(Integer att, Integer def, Integer tech,
                                            Integer spd, Integer sta, Integer men, Integer age) {
        if (att == null || def == null || tech == null || spd == null || sta == null || men == null) {
            return BigDecimal.valueOf(5_000_000L);
        }
        double overall = (att + def + tech + spd + sta + men) / 6.0;
        double ageFactor = age == null ? 1.0 : Math.max(0.4, 1.0 - Math.abs(26 - age) * 0.04);
        long value = (long) (Math.pow(overall - 50, 2.5) * 50_000L * ageFactor);
        if (value < 100_000L) value = 100_000L;
        return BigDecimal.valueOf(value);
    }

    // ========== Postgres persistence (mirrors LaLigaSeedService for new ligas) ==========

    // V25D78-C55.4: delegate the heavy per-row INSERT loop to the batched writer.
    // 9000 sequential round-trips → ~45 batched round-trips (batch size 200).
    private void persistPlayerNamesInPostgres(UUID userId, List<WorldPlayer> players,
                                              String logPrefix) {
        int written = batchWriter.upsertPlayersBatched(players, this::mapPositionString);
        log.info("{} postgres persist (batched): input={}, written={}", logPrefix, players.size(), written);
    }

    /**
     * V25D78-C55.3 B1: Upsert team rows into Postgres teams table.
     *
     * <p>The seeder previously only manipulated the in-memory WorldSnapshot.
     * C55.3 B1 needs the Postgres teams table populated with league_id so
     * V25D80 migration can redistribute division per-league.
     *
     * <p>teams.manager_id has a FK to users.id (auto-managed by Hibernate).
     * Since synthetic B1 teams have no real user manager, we ensure a
     * sentinel "synthetic-manager" row exists in users (one per league,
     * reused across all teams in that league) and use its id.
     */
    private static final java.time.Duration BLOCK_TIMEOUT = java.time.Duration.ofSeconds(120);

    private void persistTeamsInPostgres(List<WorldTeam> teams, UUID leagueId, String logPrefix) {
        int upserted = 0, skipped = 0, errors = 0;
        java.time.Instant now = java.time.Instant.now();
        // Ensure a sentinel user exists for FK on teams.manager_id.
        UUID syntheticManagerId = ensureSyntheticManager(leagueId, logPrefix);
        for (WorldTeam t : teams) {
            UUID teamId = t.getRealTeamId();
            if (teamId == null) { skipped++; continue; }
            String name = t.getName() == null ? "Unknown" : t.getName();
            String country = t.getCountry() == null ? "" : t.getCountry();
            String formation = t.getBaseFormation() == null ? "4-3-3" : t.getBaseFormation();
            java.math.BigDecimal budget = t.getBaseBudget() == null
                    ? java.math.BigDecimal.valueOf(10_000_000L) : t.getBaseBudget();
            try {
                databaseClient.sql("""
                    INSERT INTO teams (id, manager_id, name, country, formation, league_id, budget, created_at, updated_at)
                    VALUES (:id, :managerId, :name, :country, :formation, :leagueId, :budget, :createdAt, :updatedAt)
                    ON CONFLICT (id) DO UPDATE SET
                        league_id = EXCLUDED.league_id,
                        updated_at = EXCLUDED.updated_at
                    """)
                    .bind("id", teamId)
                    .bind("managerId", syntheticManagerId)
                    .bind("name", name)
                    .bind("country", country)
                    .bind("formation", formation)
                    .bind("leagueId", leagueId)
                    .bind("budget", budget)
                    .bind("createdAt", now)
                    .bind("updatedAt", now)
                    .fetch().rowsUpdated().block(BLOCK_TIMEOUT);
                upserted++;
            } catch (Exception e) {
                log.error("{} team upsert failed for {} (id={}): {}", logPrefix, name, teamId, e.getMessage(), e);
                errors++;
            }
        }
        log.info("{} postgres teams upsert: total={}, upserted={}, skipped={}, errors={}",
                logPrefix, teams.size(), upserted, skipped, errors);
    }

    /**
     * Ensure a sentinel user exists for the FK from teams.manager_id.
     * Returns the user's id (deterministic per leagueId so re-runs reuse it).
     */
    private UUID ensureSyntheticManager(UUID leagueId, String logPrefix) {
        UUID managerId = UUID.nameUUIDFromBytes(("synthetic-manager|" + leagueId.toString()).getBytes());
        try {
            databaseClient.sql("""
                INSERT INTO users (id, username, email, password_hash, created_at, updated_at)
                VALUES (:id, :username, :email, :password, :createdAt, :updatedAt)
                ON CONFLICT (id) DO NOTHING
                """)
                .bind("id", managerId)
                .bind("username", "synthetic_" + leagueId.toString().substring(0, 8))
                .bind("email", "synthetic_" + leagueId.toString().substring(0, 8) + "@synthetic.local")
                .bind("password", "synthetic_no_login")
                .bind("createdAt", now())
                .bind("updatedAt", now())
                .fetch().rowsUpdated().block(BLOCK_TIMEOUT);
        } catch (Exception e) {
            log.warn("{} synthetic manager INSERT failed (may be schema mismatch): {}",
                    logPrefix, e.getMessage());
        }
        return managerId;
    }

    private java.time.Instant now() {
        return java.time.Instant.now();
    }

    // ========== Result types ==========

    /** Per-league seed result (mirrors {@link LaLigaSeedService.SeedResult}). */
    public record SeedResult(String leagueName, int teamsCount, int playersCount, long durationMs) {}

    /** Result for seed-all — list of per-league results. */
    public record AllSeedResult(List<SeedResult> perLeague) {}
}