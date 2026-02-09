package com.footballmanager.application.service.world;

import com.footballmanager.domain.model.entity.WorldSnapshot;
import com.footballmanager.domain.model.entity.WorldTeam;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * WorldTeamCommandService - Responsable de operaciones de comando sobre WorldTeams.
 * Principio de Responsabilidad Unica: solo crea/modifica equipos.
 */
@Service
@RequiredArgsConstructor
public class WorldTeamCommandService {

    private final WorldSnapshotService snapshotService;

    // Datos para generacion random
    private static final String[] PREFIXES = {
        "FC", "SC", "Real", "Atletico", "Deportivo", "CD", "CF", "United", "City", "Sporting"
    };
    private static final String[] CITIES = {
        "Madrid", "Barcelona", "Valencia", "Sevilla", "Bilbao", "Milano", "Roma",
        "Manchester", "Liverpool", "London", "Munich", "Berlin", "Paris", "Lyon", "Amsterdam"
    };
    private static final String[] COUNTRIES = {
        "Espana", "Italia", "Inglaterra", "Alemania", "Francia", "Portugal", "Holanda"
    };

    /**
     * Crea un equipo custom en el WorldSnapshot
     */
    public Mono<WorldSnapshot> createCustomTeam(
            UUID userId, String name, String country,
            BigDecimal budget, String formation) {

        return snapshotService.getSnapshot(userId)
                .flatMap(snapshot -> {
                    WorldTeam team = WorldTeam.createCustom(name, country, budget, formation);

                    snapshot.addWorldTeam(team);

                    return snapshotService.saveSnapshot(snapshot);
                });
    }

    /**
     * Crea un equipo random en el WorldSnapshot
     */
    public Mono<WorldSnapshot> createRandomTeam(UUID userId) {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        String name = PREFIXES[random.nextInt(PREFIXES.length)] + " "
                + CITIES[random.nextInt(CITIES.length)];
        String country = COUNTRIES[random.nextInt(COUNTRIES.length)];
        BigDecimal budget = BigDecimal.valueOf(random.nextInt(10, 100) * 1_000_000L); // 10M - 100M

        return createCustomTeam(userId, name, country, budget, "4-3-3");
    }

    /**
     * Crea N equipos random en el WorldSnapshot
     */
    public Mono<WorldSnapshot> createRandomTeams(UUID userId, int count) {
        if (count < 1 || count > 20) {
            return Mono.error(new IllegalArgumentException("Count must be between 1 and 20"));
        }

        List<Mono<WorldSnapshot>> createMonos = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            createMonos.add(createRandomTeam(userId));
        }

        // Ejecutar todas las creaciones secuencialmente, retornar el ultimo snapshot
        return Flux.concat(createMonos).last();
    }

    /**
     * Asigna un equipo a una liga
     */
    public Mono<WorldSnapshot> assignTeamToLeague(UUID userId, String worldTeamId, UUID realLeagueId) {
        return snapshotService.getSnapshot(userId)
                .flatMap(snapshot -> {
                    WorldTeam team = snapshot.getWorldTeam(worldTeamId);
                    if (team == null) {
                        return Mono.error(new IllegalArgumentException(
                                "WorldTeam no encontrado: " + worldTeamId));
                    }

                    var league = snapshot.getLeagues().stream()
                            .filter(l -> l.getRealLeagueId().equals(realLeagueId))
                            .findFirst()
                            .orElse(null);

                    if (league == null) {
                        return Mono.error(new IllegalArgumentException(
                                "Liga no encontrada: " + realLeagueId));
                    }

                    team.setRealLeagueId(realLeagueId);

                    return snapshotService.saveSnapshot(snapshot);
                });
    }

    /**
     * Remueve un equipo de su liga
     */
    public Mono<WorldSnapshot> removeTeamFromLeague(UUID userId, String worldTeamId) {
        return snapshotService.getSnapshot(userId)
                .flatMap(snapshot -> {
                    WorldTeam team = snapshot.getWorldTeam(worldTeamId);
                    if (team == null) {
                        return Mono.error(new IllegalArgumentException(
                                "WorldTeam no encontrado: " + worldTeamId));
                    }

                    team.setRealLeagueId(null);

                    return snapshotService.saveSnapshot(snapshot);
                });
    }
}
