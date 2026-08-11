package com.footballmanager.application.service.world;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.footballmanager.domain.ports.out.world.WorldSnapshotRepository;
import com.footballmanager.domain.model.entity.WorldLeague;
import com.footballmanager.domain.model.entity.WorldPlayer;
import com.footballmanager.domain.model.entity.WorldSnapshot;
import com.footballmanager.domain.model.entity.WorldTeam;
import com.footballmanager.domain.model.valueobject.Division;
import com.footballmanager.domain.model.valueobject.PlayerSkill;
import com.footballmanager.domain.ports.out.player.PlayerRepository;
import com.footballmanager.domain.model.entity.Player.Position;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * "Top 10 mundial" leagues (La Liga, Premier, Bundesliga, Serie A, Ligue 1,
 * Brasileirão, Liga Profesional, MLS, Eredivisie, Championship) into the
 * user's {@link WorldSnapshot} + Postgres.
 *
 * <p><b>Architecture decision:</b> this class is a parallel to
 * {@link LaLigaSeedService}, not a refactor. LaLigaSeedService is
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
    private final WorldSnapshotRepository worldRepository;
    private final PlayerRepository playerRepository;
    private final LaLigaSeedService laLigaSeedService;
    private final WorldSeedPlayerWriter batchWriter;
    private final WorldSeedTeamWriter teamWriter;
    private final SeedResourceLoader seedResourceLoader;
    private final WorldSeedCompletenessChecker completenessChecker;

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
        return Mono.zip(snapshotService.getSnapshot(userId), loadAllSeedData())
                .flatMap(tuple -> completenessChecker.isComplete(tuple.getT1(), tuple.getT2())
                        ? Mono.just(alreadyComplete(tuple.getT2()))
                        : seedAllLeaguesFromSource(userId));
    }

    private Mono<AllSeedResult> seedAllLeaguesFromSource(UUID userId) {
        List<SeedResult> results = new ArrayList<>();
        // Persist La Liga once, then accumulate the remaining canonical seeds
        // in one snapshot and publish one final world envelope. Rewriting a
        // growing multi-megabyte catalog after every league made seed-all pay
        // quadratic serialization/Redis cost.
        return seedLeague(LeagueType.LALIGA, userId)
                .doOnNext(results::add)
                .then(snapshotService.getSnapshot(userId))
                .flatMap(snapshot -> Flux.fromArray(LeagueType.values())
                        .filter(type -> type != LeagueType.LALIGA)
                        .concatMap(type -> applySeedWithoutSnapshotWrite(type, userId, snapshot)
                                .doOnNext(results::add)
                                .onErrorResume(error -> {
                                    log.warn("[WORLD-SEED-ALL] league {} failed: {}",
                                            type.slug(), error.getMessage());
                                    return Mono.empty();
                                }))
                        .then(snapshotService.saveSnapshot(snapshot)
                                .contextWrite(context -> context.put(
                                        WorldSnapshotRepository.CANONICAL_BOOTSTRAP_CONTEXT_KEY, true)))
                        .thenReturn(new AllSeedResult(results)));
    }

    private Mono<List<LaLigaSeedData>> loadAllSeedData() {
        return Flux.fromArray(LeagueType.values())
                .concatMap(type -> loadSeedData(type.resourcePath()))
                .collectList();
    }

    private AllSeedResult alreadyComplete(List<LaLigaSeedData> seeds) {
        List<SeedResult> results = seeds.stream()
                .map(seed -> new SeedResult(seed.league().name(), seed.teams().size(), seed.players().size(), 0))
                .toList();
        log.info("[WORLD-SEED-ALL] complete snapshot detected; skipped {} idempotent persistence passes",
                results.size());
        return new AllSeedResult(results);
    }

    private Mono<SeedResult> applySeedWithoutSnapshotWrite(LeagueType leagueType, UUID userId,
                                                            WorldSnapshot snapshot) {
        long start = System.currentTimeMillis();
        String logPrefix = "[" + leagueType.slug().toUpperCase() + "-SEED]";
        PlayerAttributesGenerator generator = new PlayerAttributesGenerator();
        return loadSeedData(leagueType.resourcePath())
                .map(seed -> applySeedData(userId, snapshot, seed, generator, logPrefix, start));
    }

    // ========== Loading ==========

    private Mono<LaLigaSeedData> loadSeedData(String resourcePath) {
        return Mono.fromCallable(() -> {
            try (InputStream in = seedResourceLoader.open(resourcePath)) {
                return objectMapper.readValue(in, LaLigaSeedData.class);
            }
        });
    }

    // ========== Seed application ==========

    private Mono<SeedResult> applySeed(UUID userId, WorldSnapshot snapshot,
                                       LaLigaSeedData seed,
                                       PlayerAttributesGenerator gen,
                                       String logPrefix, long start) {
        SeedResult result = applySeedData(userId, snapshot, seed, gen, logPrefix, start);
        return snapshotService.saveSnapshot(snapshot)
                .contextWrite(context -> context.put(
                        WorldSnapshotRepository.CANONICAL_BOOTSTRAP_CONTEXT_KEY, true))
                .thenReturn(result);
    }

    private SeedResult applySeedData(UUID userId, WorldSnapshot snapshot,
                                     LaLigaSeedData seed,
                                     PlayerAttributesGenerator gen,
                                     String logPrefix, long start) {
        UUID leagueId = ensureLeague(snapshot, seed);
        Map<String, WorldTeam> teamsByName = ensureTeams(snapshot, seed, leagueId);
        List<WorldPlayer> players = ensurePlayers(userId, snapshot, seed, teamsByName, gen);
        // Java so the Redis snapshot stores correct PRIMERA/SEGUNDA/TERCERA.
        // would redistribute), the read path returns the Redis snapshot
        // verbatim — without this step the response would still show all
        // PRIMERA. See DivisionRankDistributor for details.
        DivisionRankDistributor.applyPerLeagueRankDivision(snapshot);
        // sentinel manager_id (00000000-0000-0000-0000-000000000000) for
        // synthetic teams that have no real user manager.
        teamWriter.upsertTeams(new ArrayList<>(teamsByName.values()), leagueId, logPrefix);
        persistPlayerNamesInPostgres(userId, players, logPrefix);
        long duration = System.currentTimeMillis() - start;
        log.info("{} prepared: teams={} players={} durationMs={}",
                logPrefix, teamsByName.size(), players.size(), duration);
        return new SeedResult(seed.league().name(), teamsByName.size(), players.size(), duration);
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
        // (which writes the rows with division='PRIMERA'). On subsequent loads,
        // TeamPlayerLoaderService picks up the correct division from Postgres.
        return WorldTeam.fromRealTeam(teamId, leagueId, dto.name(),
                country == null ? "" : country,
                dto.city(), budget,
                dto.formation() == null ? "4-3-3" : dto.formation(),
                Division.defaultDivision());
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

    private List<WorldPlayer> ensurePlayers(UUID ownerId, WorldSnapshot snapshot, LaLigaSeedData seed,
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
                wp = createPlayerFromDto(ownerId, dto, team, gen);
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

    private WorldPlayer createPlayerFromDto(UUID ownerId, LaLigaSeedData.PlayerDto dto, WorldTeam team,
                                           PlayerAttributesGenerator gen) {
        UUID pid = UUID.nameUUIDFromBytes(
                ("player|" + team.getName() + "|" + dto.name()).getBytes());
        BigDecimal mv = calculateMarketValue(
                dto.baseAttack(), dto.baseDefense(), dto.baseTechnique(),
                dto.baseSpeed(), dto.baseStamina(), dto.baseMentality(), dto.age());
        WorldPlayer wp = WorldPlayer.fromCanonicalPlayer(
                ownerId, pid, team.getWorldTeamId(),
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

    // 9000 sequential round-trips → ~45 batched round-trips (batch size 200).
    private void persistPlayerNamesInPostgres(UUID userId, List<WorldPlayer> players,
                                              String logPrefix) {
        int written = batchWriter.upsertPlayersBatched(players, this::mapPositionString);
        log.info("{} postgres persist (batched): input={}, written={}", logPrefix, players.size(), written);
    }

    // ========== Result types ==========

    /** Per-league seed result (mirrors {@link LaLigaSeedService.SeedResult}). */
    public record SeedResult(String leagueName, int teamsCount, int playersCount, long durationMs) {}

    /** Result for seed-all — list of per-league results. */
    public record AllSeedResult(List<SeedResult> perLeague) {}
}

