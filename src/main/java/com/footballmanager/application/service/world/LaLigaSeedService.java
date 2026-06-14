package com.footballmanager.application.service.world;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.footballmanager.adapters.out.redis.RedisWorldRepository;
import com.footballmanager.domain.model.entity.Player;
import com.footballmanager.domain.model.entity.WorldLeague;
import com.footballmanager.domain.model.entity.WorldPlayer;
import com.footballmanager.domain.model.entity.WorldSnapshot;
import com.footballmanager.domain.model.entity.WorldTeam;
import com.footballmanager.domain.ports.out.player.PlayerRepository;
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
import java.util.UUID;

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

        // Capa 3: persistir nombres reales en PostgreSQL (fire-and-forget, no bloquea el seed)
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
     * V24D8-BUG-002 Capa 3: para cada WorldPlayer con realPlayerId (es decir, origin REAL),
     * busca la Player entity correspondiente en Postgres y, si el nombre difiere del nuevo
     * nombre del seed, persiste un rename via {@link Player#rename(String)}.
     *
     * <p>Fire-and-forget: el seed no espera a que las updates de Postgres terminen. Esto es
     * aceptable porque (a) el seed ya escribió el snapshot nuevo en Redis con los nombres
     * correctos, y (b) la career que se cree a continuación va a leer del snapshot, no de
     * Postgres. La persistencia en Postgres es para mantener consistencia con futuras
     * reconstrucciones del WorldView desde la base.
     */
    private void persistPlayerNamesInPostgres(UUID userId, List<WorldPlayer> players) {
        for (WorldPlayer wp : players) {
            if (wp.getRealPlayerId() == null) continue;
            UUID realPlayerId = wp.getRealPlayerId();
            String newName = wp.getName();
            playerRepository.findById(userId, realPlayerId)
                    .flatMap(dbPlayer -> {
                        if (newName != null && !newName.equals(dbPlayer.getName())) {
                            return playerRepository.save(userId, dbPlayer.rename(newName));
                        }
                        return Mono.empty();
                    })
                    .subscribe(
                            saved -> log.debug("[LA-LIGA-SEED] postgres player renamed: realId={}, newName={}", realPlayerId, newName),
                            err -> log.warn("[LA-LIGA-SEED] failed to rename postgres player realId={}: {}", realPlayerId, err.getMessage())
                    );
        }
        log.info("[LA-LIGA-SEED] postgres player names update dispatched for userId={}, count={}", userId, players.size());
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
        return WorldPlayer.fromRealPlayer(
                realPlayerId, team.getWorldTeamId(), dto.name(), dto.age(), dto.position(),
                dto.baseAttack(), dto.baseDefense(), dto.baseTechnique(),
                dto.baseSpeed(), dto.baseStamina(), dto.baseMentality(), marketValue);
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
     * Resultado del seed.
     */
    public record SeedResult(
            String leagueName,
            int teamsCount,
            int playersCount,
            long durationMs
    ) {}
}
