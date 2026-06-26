package com.footballmanager.application.service.world;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.footballmanager.adapters.out.redis.RedisWorldRepository;
import com.footballmanager.domain.model.entity.Player;
import com.footballmanager.domain.model.entity.WorldLeague;
import com.footballmanager.domain.model.entity.WorldPlayer;
import com.footballmanager.domain.model.entity.WorldSnapshot;
import com.footballmanager.domain.model.entity.WorldTeam;
import com.footballmanager.domain.model.valueobject.PlayerSkill;
import com.footballmanager.domain.ports.out.player.PlayerRepository;
import org.springframework.r2dbc.core.DatabaseClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
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
 * V24D6U5: LaLigaSeedService — Puebla el WorldSnapshot de un usuario con La Liga 2024/25 real.
 *
 * <p><b>Comportamiento:</b> lee {@code seed/laliga-2024-25.json} desde classpath, deserializa
 * con Jackson, y aplica UPSERT (por nombre) sobre el WorldSnapshot del usuario en Redis.
 *
 * <p><b>Idempotencia:</b> si un equipo/jugador con el mismo nombre ya existe en el snapshot,
 * se actualizan sus stats. Si no, se crea. Ejecutar N veces produce el mismo resultado.
 * Los IDs son determinísticos (UUID derivado de nombre vía {@link UUID#nameUUIDFromBytes}),
 * lo que permite UPSERT por nombre sin guardar mappings externos.
 *
 * <p><b>Scope:</b> 20 equipos + ~400 jugadores. No toca el simulador V24 ni el flow
 * de WorldSnapshot existente.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LaLigaSeedService {

    private static final String SEED_RESOURCE = "seed/laliga-2024-25.json";

    private final WorldSnapshotService snapshotService;
    private final ObjectMapper objectMapper;
    private final RedisWorldRepository worldRepository;
    private final PlayerRepository playerRepository;
    private final DatabaseClient databaseClient;

    /**
     * V25D32-F3: generador deterministico de heights para los 386 players del seed
     * que NO tienen heightCm hardcoded en el JSON. Mismo seed → mismo height, lo
     * que hace la persistencia en Postgres idempotente para esos fields.
     */
    private final PlayerAttributesGenerator attributesGenerator = new PlayerAttributesGenerator();

    /**
     * Ejecuta el seed para el usuario indicado. Crea/actualiza la league, los 20 equipos
     * y los jugadores de La Liga 2024/25 en el WorldSnapshot del usuario.
     *
     * @return Mono con el resumen del seed (counts y duración)
     */
    public Mono<SeedResult> execute(UUID userId) {
        long start = System.currentTimeMillis();
        return loadSeedData()
                .flatMap(seed -> snapshotService.getSnapshot(userId)
                        .flatMap(snapshot -> applySeed(userId, snapshot, seed, start)));
    }

    private Mono<LaLigaSeedData> loadSeedData() {
        return Mono.fromCallable(() -> {
            try (InputStream in = new ClassPathResource(SEED_RESOURCE).getInputStream()) {
                return objectMapper.readValue(in, LaLigaSeedData.class);
            }
        });
    }

    private Mono<SeedResult> applySeed(UUID userId, WorldSnapshot snapshot, LaLigaSeedData seed, long start) {
        // Asegurar league "La Liga 2024/25"
        UUID realLeagueId = ensureLeague(snapshot, seed);

        // UPSERT equipos: nombre -> WorldTeam
        Map<String, WorldTeam> teamsByName = ensureTeams(snapshot, seed, realLeagueId);

        // UPSERT players: agrupar por team-name para asignar worldTeamId
        List<WorldPlayer> createdOrUpdated = ensurePlayers(snapshot, seed, teamsByName);

        // Capa 3: persiste nombres reales y team_squad en PostgreSQL para que BuildWorldViewUseCase
        // encuentre los jugadores reales (no placeholders) cuando reconstruya el WorldView después del seed
        persistPlayerNamesInPostgres(userId, createdOrUpdated);

        // Capa 2: forzar regeneración del WorldSnapshot borrando el snapshot viejo de Redis
        // después del save. Así, un próximo getOrCreateSnapshot reconstruirá desde el state real.
        return snapshotService.saveSnapshot(snapshot)
                .flatMap(saved -> worldRepository.deleteByUserId(userId)
                        .doOnNext(deleted -> log.info("[LA-LIGA-SEED] snapshot invalidated for rebuild, userId={}, deleted={}", userId, deleted))
                        .thenReturn(saved))
                .doOnSuccess(s -> log.info("[LA-LIGA-SEED] snapshot regenerated for userId={}", userId))
                .map(saved -> {
                    long durationMs = System.currentTimeMillis() - start;
                    int teamsCount = teamsByName.size();
                    int playersCount = createdOrUpdated.size();
                    log.info("V24D6U5 seed: userId={} teams={} players={} durationMs={}",
                            userId, teamsCount, playersCount, durationMs);
                    return new SeedResult(seed.league().name(), teamsCount, playersCount, durationMs);
                });
    }

    /**
     * V24D8-BUG-004 fix: para cada WorldPlayer con realPlayerId (origin REAL),
     * persiste la Player entity completa en PostgreSQL. Esto asegura que cuando
     * BuildWorldViewUseCase rebuild el snapshot desde Postgres (después de que
     * el seed borra el snapshot de Redis), los players tienen los nombres reales
     * y no placeholders.
     *
     * <p>Ejecuta BLOCKING para garantizar que Postgres tenga los datos ANTES de que
     * career/start intente rebuild el WorldView desde la base.
     */
    private void persistPlayerNamesInPostgres(UUID userId, List<WorldPlayer> players) {
        // Clean up old team_squad entries for La Liga teams so only seeded players are returned
        // team_squad is global (no userId) and was populated with placeholder players
        Set<UUID> laLigaTeamIds = players.stream()
                .filter(wp -> wp.getWorldTeamId() != null)
                .map(wp -> {
                    try { return UUID.fromString(wp.getWorldTeamId()); } catch (Exception e) { return null; }
                })
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        if (!laLigaTeamIds.isEmpty()) {
            try {
                databaseClient.sql("DELETE FROM team_squad WHERE team_id = ANY(:teamIds)")
                        .bind("teamIds", laLigaTeamIds.toArray(UUID[]::new))
                        .fetch().rowsUpdated().block();
                log.info("[LA-LIGA-SEED] cleaned team_squad for {} teams", laLigaTeamIds.size());
            } catch (Exception e) {
                log.warn("[LA-LIGA-SEED] failed to clean team_squad: {}", e.getMessage());
            }
        }
        int skipped = 0, inserted = 0, squadEntries = 0, errors = 0;
        java.time.Instant now = java.time.Instant.now();
        for (WorldPlayer wp : players) {
            if (wp.getRealPlayerId() == null) {
                skipped++;
                continue;
            }
            UUID realPlayerId = wp.getRealPlayerId();
            String newName = wp.getName();
            try {
                String posName = mapPosition(wp.getPosition()).name();
                int att = wp.getBaseAttack() != null ? wp.getBaseAttack() : 50;
                int def = wp.getBaseDefense() != null ? wp.getBaseDefense() : 50;
                int tech = wp.getBaseTechnique() != null ? wp.getBaseTechnique() : 50;
                int spd = wp.getBaseSpeed() != null ? wp.getBaseSpeed() : 50;
                int sta = wp.getBaseStamina() != null ? wp.getBaseStamina() : 50;
                int men = wp.getBaseMentality() != null ? wp.getBaseMentality() : 50;
                int age = wp.getAge() != null ? wp.getAge() : 25;
                java.math.BigDecimal mv = wp.getBaseMarketValue() != null
                        ? wp.getBaseMarketValue()
                        : java.math.BigDecimal.valueOf(5_000_000L);

                // V25D32-F3: height + skills vienen del WorldPlayer (seteados en
                // applyHeightAndSkillsFromDto). skillLevels se serializa a JSON
                // (mismo codec que PlayerEntity).
                Integer height = wp.getHeightCm();
                String skillsJson = serializeSkillLevelsOrNull(wp.getSkillLevels());

                databaseClient.sql("""
                    INSERT INTO players (id, name, age, position, attack, defense, technique, speed, stamina, mentality, market_value, energy, injured, created_at, updated_at, height_cm, skill_levels_json)
                    VALUES (:id, :name, :age, :position, :attack, :defense, :technique, :speed, :stamina, :mentality, :mv, :energy, :injured, :createdAt, :updatedAt, :heightCm, :skillLevelsJson)
                    ON CONFLICT (id) DO UPDATE SET
                        name = EXCLUDED.name,
                        height_cm = EXCLUDED.height_cm,
                        skill_levels_json = EXCLUDED.skill_levels_json
                    """)
                    .bind("id", realPlayerId)
                    .bind("name", newName)
                    .bind("age", age)
                    .bind("position", posName)
                    .bind("attack", att)
                    .bind("defense", def)
                    .bind("technique", tech)
                    .bind("speed", spd)
                    .bind("stamina", sta)
                    .bind("mentality", men)
                    .bind("mv", mv)
                    .bind("energy", 100)
                    .bind("injured", false)
                    .bind("createdAt", now)
                    .bind("updatedAt", now)
                    .bind("heightCm", height)
                    .bind("skillLevelsJson", skillsJson)
                    .fetch()
                    .rowsUpdated()
                    .block();

                // Also insert team_squad entry so TeamPlayerLoader finds these players by teamId
                if (wp.getWorldTeamId() != null) {
                    try {
                        UUID worldTeamId = UUID.fromString(wp.getWorldTeamId());
                        databaseClient.sql("""
                            INSERT INTO team_squad (team_id, player_id)
                            VALUES (:teamId, :playerId)
                            ON CONFLICT DO NOTHING
                            """)
                            .bind("teamId", worldTeamId)
                            .bind("playerId", realPlayerId)
                            .fetch()
                            .rowsUpdated()
                            .block();
                        squadEntries++;
                    } catch (Exception sqle) {
                        // ignore squad insert errors
                    }
                }

                inserted++;
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : "";
                if (!msg.contains("players_pkey") && !msg.contains("duplicate key")) {
                    errors++;
                    if (errors <= 3) {
                        log.warn("[LA-LIGA-SEED] INSERT non-pk error: id={}, name={}, error={}", realPlayerId, newName, msg);
                    }
                }
            }
        }
        log.info("[LA-LIGA-SEED] postgres persist: total={}, inserted={}, squadEntries={}, skipped={}, errors={}", players.size(), inserted, squadEntries, skipped, errors);
    }

    // ========== League ==========

    private UUID ensureLeague(WorldSnapshot snapshot, LaLigaSeedData seed) {
        if (snapshot.getLeagues() == null) {
            snapshot.setLeagues(new ArrayList<>());
        }
        // ID determinístico basado en nombre+país para que re-seeds no creen duplicados
        UUID realLeagueId = UUID.nameUUIDFromBytes(
                (seed.league().name() + "|" + seed.league().country()).getBytes());
        boolean exists = snapshot.getLeagues().stream()
                .anyMatch(l -> realLeagueId.equals(l.getRealLeagueId()));
        if (!exists) {
            snapshot.getLeagues().add(
                    WorldLeague.fromRealLeague(
                            realLeagueId, seed.league().name(), seed.league().country(),
                            seed.league().tier() == null ? 1 : seed.league().tier()));
        }
        return realLeagueId;
    }

    // ========== Teams ==========

    private Map<String, WorldTeam> ensureTeams(WorldSnapshot snapshot, LaLigaSeedData seed, UUID realLeagueId) {
        if (snapshot.getWorldTeams() == null) {
            snapshot.setWorldTeams(new HashMap<>());
        }
        // Map de WorldTeams existentes indexado por nombre (case-insensitive)
        Map<String, WorldTeam> byName = indexTeamsByName(snapshot);

        // Map resultante: nombre (case-insensitive) -> WorldTeam (existente o nuevo)
        Map<String, WorldTeam> result = new HashMap<>();
        for (LaLigaSeedData.TeamDto dto : seed.teams()) {
            String key = dto.name().toLowerCase();
            WorldTeam existing = byName.get(key);
            if (existing != null) {
                updateTeamFromDto(existing, dto, realLeagueId);
                result.put(key, existing);
            } else {
                WorldTeam created = createTeamFromDto(dto, realLeagueId);
                snapshot.getWorldTeams().put(created.getWorldTeamId(), created);
                result.put(key, created);
            }
        }
        return result;
    }

    private Map<String, WorldTeam> indexTeamsByName(WorldSnapshot snapshot) {
        Map<String, WorldTeam> map = new HashMap<>();
        for (WorldTeam team : snapshot.getWorldTeams().values()) {
            if (team.getName() != null) {
                map.put(team.getName().toLowerCase(), team);
            }
        }
        return map;
    }

    private WorldTeam createTeamFromDto(LaLigaSeedData.TeamDto dto, UUID realLeagueId) {
        // realTeamId determinístico basado en nombre (permite idempotencia vía fromRealTeam,
        // que setea worldTeamId = realTeamId.toString())
        UUID realTeamId = UUID.nameUUIDFromBytes(("team|" + dto.name()).getBytes());
        BigDecimal budget = BigDecimal.valueOf(dto.budgetMillions() == null ? 50L : dto.budgetMillions())
                .multiply(BigDecimal.valueOf(1_000_000L));
        return WorldTeam.fromRealTeam(
                realTeamId,
                realLeagueId,
                dto.name(),
                "Spain",
                dto.city(),
                budget,
                dto.formation() == null ? "4-3-3" : dto.formation());
    }

    private void updateTeamFromDto(WorldTeam team, LaLigaSeedData.TeamDto dto, UUID realLeagueId) {
        if (dto.formation() != null) {
            team.setBaseFormation(dto.formation());
        }
        if (dto.budgetMillions() != null) {
            team.setBaseBudget(BigDecimal.valueOf(dto.budgetMillions())
                    .multiply(BigDecimal.valueOf(1_000_000L)));
        }
        if (dto.city() != null) {
            team.setCity(dto.city());
        }
        if (realLeagueId != null) {
            team.setRealLeagueId(realLeagueId);
        }
    }

    // ========== Players ==========

    private List<WorldPlayer> ensurePlayers(WorldSnapshot snapshot, LaLigaSeedData seed,
                                            Map<String, WorldTeam> teamsByName) {
        if (snapshot.getWorldPlayers() == null) {
            snapshot.setWorldPlayers(new HashMap<>());
        }
        // Indexar players existentes por (worldTeamId|name) lower
        Map<String, WorldPlayer> existingByKey = indexPlayersByTeamAndName(snapshot);

        List<WorldPlayer> affected = new ArrayList<>();
        for (LaLigaSeedData.PlayerDto dto : seed.players()) {
            String teamKey = dto.team().toLowerCase();
            WorldTeam team = teamsByName.get(teamKey);
            if (team == null) {
                // No debería pasar si el JSON está bien formado
                log.warn("V24D6U5 seed: player {} referencia team {} que no existe",
                        dto.name(), dto.team());
                continue;
            }
            String key = (team.getWorldTeamId() + "|" + dto.name()).toLowerCase();
            WorldPlayer existing = existingByKey.get(key);
            if (existing != null) {
                updatePlayerFromDto(existing, dto, team);
                affected.add(existing);
            } else {
                WorldPlayer created = createPlayerFromDto(dto, team);
                snapshot.getWorldPlayers().put(created.getWorldPlayerId(), created);
                affected.add(created);
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

    private WorldPlayer createPlayerFromDto(LaLigaSeedData.PlayerDto dto, WorldTeam team) {
        // realPlayerId determinístico basado en team+name
        UUID realPlayerId = UUID.nameUUIDFromBytes(
                ("player|" + team.getName() + "|" + dto.name()).getBytes());
        BigDecimal marketValue = calculateMarketValue(
                dto.baseAttack(), dto.baseDefense(), dto.baseTechnique(),
                dto.baseSpeed(), dto.baseStamina(), dto.baseMentality(), dto.age());
        WorldPlayer player = WorldPlayer.fromRealPlayer(
                realPlayerId, team.getWorldTeamId(), dto.name(), dto.age(), dto.position(),
                dto.baseAttack(), dto.baseDefense(), dto.baseTechnique(),
                dto.baseSpeed(), dto.baseStamina(), dto.baseMentality(), marketValue);
        // V25D32-F3: height + skills. Si el JSON los provee, los usamos. Si no,
        // generamos un height aleatorio con seed fijo (para los 386 no-top-20).
        applyHeightAndSkillsFromDto(player, dto);
        return player;
    }

    private void updatePlayerFromDto(WorldPlayer p, LaLigaSeedData.PlayerDto dto, WorldTeam team) {
        p.setName(dto.name());
        p.setWorldTeamId(team.getWorldTeamId());
        p.setAge(dto.age());
        p.setPosition(dto.position());
        p.setBaseAttack(dto.baseAttack());
        p.setBaseDefense(dto.baseDefense());
        p.setBaseTechnique(dto.baseTechnique());
        p.setBaseSpeed(dto.baseSpeed());
        p.setBaseStamina(dto.baseStamina());
        p.setBaseMentality(dto.baseMentality());
        p.setBaseMarketValue(calculateMarketValue(
                dto.baseAttack(), dto.baseDefense(), dto.baseTechnique(),
                dto.baseSpeed(), dto.baseStamina(), dto.baseMentality(), dto.age()));
        p.setOrigin(WorldPlayer.WorldPlayerOrigin.REAL);
        // V25D32-F3: refrescar height + skills en re-seed (idempotencia).
        applyHeightAndSkillsFromDto(p, dto);
    }

    /**
     * V25D32-F3: aplica height + skills al WorldPlayer desde el DTO. Si el DTO
     * tiene heightCm, lo usa. Si no, genera uno aleatorio con el generator
     * deterministico. Si el DTO tiene skillLevels (top-5 curated), los aplica.
     * Si no, deja el map vacio (engine en V25D33 aplica defaults).
     */
    private void applyHeightAndSkillsFromDto(WorldPlayer player, LaLigaSeedData.PlayerDto dto) {
        Integer height = dto.heightCm() != null
                ? dto.heightCm()
                : attributesGenerator.generateHeightCm();
        player.setHeightCm(height);

        // skillLevels: solo si el DTO los provee. NO random para los 386
        // restantes — el prompt de V25D32 lo desaconseja (ruido en smoke canonico).
        if (dto.skillLevels() != null && !dto.skillLevels().isEmpty()) {
            player.setSkillLevels(dto.skillLevels());
        } else {
            player.setSkillLevels(null);  // explicit null (vs empty map)
        }
    }

    private static BigDecimal calculateMarketValue(Integer att, Integer def, Integer tech,
                                                   Integer spd, Integer sta, Integer men, Integer age) {
        int total = safe(att) + safe(def) + safe(tech) + safe(spd) + safe(sta) + safe(men);
        int avg = total / 6;
        long baseValue = avg * 100_000L;
        double ageFactor = 1.0;
        if (age != null) {
            if (age < 23) ageFactor = 1.5;
            else if (age > 30) ageFactor = 0.7;
        }
        return BigDecimal.valueOf((long) (baseValue * ageFactor));
    }

    private static int safe(Integer v) {
        return v == null ? 50 : v;
    }

    /**
     * V25D32-F3: serializa un Map&lt;PlayerSkill, Integer&gt; a JSON string para el
     * INSERT crudo en Postgres. Devuelve null si el map es null o vacio (no string
     * vacia ni literal "null" — queremos que la columna quede NULL en la DB para
     * los players sin skills curated).
     */
    private String serializeSkillLevelsOrNull(Map<PlayerSkill, Integer> skillLevels) {
        if (skillLevels == null || skillLevels.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(skillLevels);
        } catch (Exception e) {
            log.warn("[LA-LIGA-SEED] failed to serialize skillLevels: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Mapea posición del JSON seed a Player.Position enum.
     * El JSON puede tener variantes (MID, ATT, WINGER) que no existen en el enum.
     */
    private static Player.Position mapPosition(String pos) {
        if (pos == null) return Player.Position.CM;
        return switch (pos.toUpperCase()) {
            case "GK" -> Player.Position.GK;
            case "LB" -> Player.Position.LB;
            case "CB" -> Player.Position.CB;
            case "RB" -> Player.Position.RB;
            case "LWB" -> Player.Position.LWB;
            case "RWB" -> Player.Position.RWB;
            case "CDM", "MID" -> Player.Position.CDM;
            case "CM" -> Player.Position.CM;
            case "CAM" -> Player.Position.CAM;
            case "LM" -> Player.Position.LM;
            case "RM" -> Player.Position.RM;
            case "LW", "RW", "WINGER" -> Player.Position.LW;
            case "CF", "ATT" -> Player.Position.CF;
            case "ST" -> Player.Position.ST;
            default -> {
                try {
                    yield Player.Position.valueOf(pos.toUpperCase());
                } catch (IllegalArgumentException e) {
                    yield Player.Position.CM;
                }
            }
        };
    }

    /**
     * Resultado del seed.
     */
    public record SeedResult(
            String leagueName,
            int teamsCount,
            int playersCount,
            long durationMs
    ) {}
}
