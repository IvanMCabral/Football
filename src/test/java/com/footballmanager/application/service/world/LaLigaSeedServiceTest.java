package com.footballmanager.application.service.world;

import com.footballmanager.domain.model.entity.WorldPlayer;
import com.footballmanager.domain.model.entity.WorldSnapshot;
import com.footballmanager.domain.model.entity.WorldTeam;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.HashMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * V24D6U5: Tests del LaLigaSeedService.
 *
 * <p>Verifica: idempotencia, conteo de equipos/players, asignación de league, persistencia.
 */
class LaLigaSeedServiceTest {

    private WorldSnapshotService snapshotService;
    private LaLigaSeedService service;

    @BeforeEach
    void setUp() {
        snapshotService = mock(WorldSnapshotService.class);
        service = new LaLigaSeedService(snapshotService, new com.fasterxml.jackson.databind.ObjectMapper());
    }

    @Test
    void execute_seedsAllTeamsAndPlayers() {
        WorldSnapshot emptySnapshot = new WorldSnapshot();
        emptySnapshot.setUserId(UUID.randomUUID());
        emptySnapshot.setLeagues(new java.util.ArrayList<>());
        emptySnapshot.setWorldTeams(new HashMap<>());
        emptySnapshot.setWorldPlayers(new HashMap<>());

        when(snapshotService.getSnapshot(any(UUID.class))).thenReturn(Mono.just(emptySnapshot));
        when(snapshotService.saveSnapshot(any(WorldSnapshot.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        UUID userId = UUID.randomUUID();

        StepVerifier.create(service.execute(userId))
                .assertNext(result -> {
                    assertEquals("La Liga 2024/25", result.leagueName());
                    assertEquals(20, result.teamsCount(), "Debe seedear 20 equipos");
                    assertTrue(result.playersCount() >= 380 && result.playersCount() <= 500,
                            "Debe seedear 380-500 jugadores, fue " + result.playersCount());
                    assertTrue(result.durationMs() >= 0);
                })
                .verifyComplete();

        // El snapshot persistido debe tener 20 equipos y ~400 jugadores
        ArgumentCaptor<WorldSnapshot> captor = ArgumentCaptor.forClass(WorldSnapshot.class);
        verify(snapshotService, atLeastOnce()).saveSnapshot(captor.capture());
        WorldSnapshot persisted = captor.getValue();
        assertEquals(20, persisted.getWorldTeams().size());
        assertTrue(persisted.getWorldPlayers().size() >= 380);
    }

    @Test
    void execute_isIdempotent_secondRunDoesNotDuplicate() {
        WorldSnapshot firstSnapshot = new WorldSnapshot();
        firstSnapshot.setUserId(UUID.randomUUID());
        firstSnapshot.setLeagues(new java.util.ArrayList<>());
        firstSnapshot.setWorldTeams(new HashMap<>());
        firstSnapshot.setWorldPlayers(new HashMap<>());

        // Primer getSnapshot: vacío
        // Siguientes: el mismo snapshot ya populado
        when(snapshotService.getSnapshot(any(UUID.class)))
                .thenReturn(Mono.just(firstSnapshot));
        when(snapshotService.saveSnapshot(any(WorldSnapshot.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        UUID userId = UUID.randomUUID();

        // Primera ejecución
        StepVerifier.create(service.execute(userId))
                .assertNext(r1 -> {
                    assertEquals(20, r1.teamsCount());
                    int firstPlayers = r1.playersCount();
                    assertTrue(firstPlayers >= 380);

                    // Segunda ejecución sobre el snapshot ya populado
                    WorldSnapshot populated = new WorldSnapshot();
                    populated.setUserId(userId);
                    populated.setLeagues(firstSnapshot.getLeagues());
                    populated.setWorldTeams(firstSnapshot.getWorldTeams());
                    populated.setWorldPlayers(firstSnapshot.getWorldPlayers());

                    when(snapshotService.getSnapshot(any(UUID.class)))
                            .thenReturn(Mono.just(populated));

                    StepVerifier.create(service.execute(userId))
                            .assertNext(r2 -> {
                                assertEquals(20, r2.teamsCount(),
                                        "Idempotencia: segunda ejecución debe seguir teniendo 20 equipos, no 40");
                                assertEquals(firstPlayers, r2.playersCount(),
                                        "Idempotencia: segunda ejecución debe tener la misma cantidad de players");
                            })
                            .verifyComplete();
                })
                .verifyComplete();
    }

    @Test
    void execute_assignsAllPlayersToTheirTeams() {
        WorldSnapshot emptySnapshot = new WorldSnapshot();
        emptySnapshot.setUserId(UUID.randomUUID());
        emptySnapshot.setLeagues(new java.util.ArrayList<>());
        emptySnapshot.setWorldTeams(new HashMap<>());
        emptySnapshot.setWorldPlayers(new HashMap<>());

        when(snapshotService.getSnapshot(any(UUID.class))).thenReturn(Mono.just(emptySnapshot));
        when(snapshotService.saveSnapshot(any(WorldSnapshot.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(service.execute(UUID.randomUUID()))
                .assertNext(result -> assertTrue(result.playersCount() > 0))
                .verifyComplete();

        // Verificar que todos los players tienen worldTeamId seteado y es válido
        WorldSnapshot persisted = emptySnapshot;
        assertFalse(persisted.getWorldPlayers().isEmpty());

        int playersWithoutTeam = 0;
        for (WorldPlayer p : persisted.getWorldPlayers().values()) {
            if (p.getWorldTeamId() == null || p.getWorldTeamId().isEmpty()) {
                playersWithoutTeam++;
            } else {
                // worldTeamId debe matchear un WorldTeam existente
                assertTrue(persisted.getWorldTeams().containsKey(p.getWorldTeamId()),
                        "Player " + p.getName() + " tiene worldTeamId " + p.getWorldTeamId()
                                + " que no existe en worldTeams");
            }
        }
        assertEquals(0, playersWithoutTeam, "Ningún player debería quedar sin worldTeamId");
    }

    @Test
    void execute_starPlayersCalculateOverallInExpectedRange() {
        WorldSnapshot emptySnapshot = new WorldSnapshot();
        emptySnapshot.setUserId(UUID.randomUUID());
        emptySnapshot.setLeagues(new java.util.ArrayList<>());
        emptySnapshot.setWorldTeams(new HashMap<>());
        emptySnapshot.setWorldPlayers(new HashMap<>());

        when(snapshotService.getSnapshot(any(UUID.class))).thenReturn(Mono.just(emptySnapshot));
        when(snapshotService.saveSnapshot(any(WorldSnapshot.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(service.execute(UUID.randomUUID()))
                .assertNext(result -> assertTrue(result.playersCount() > 0))
                .verifyComplete();

        // Buscar estrellas esperadas y validar que calculateOverall está en rango 80-95
        WorldSnapshot persisted = emptySnapshot;
        WorldPlayer vinicius = findPlayer(persisted, "Vinicius");
        assertNotNull(vinicius, "Vinicius debe estar en el seed");
        int viniciusOverall = vinicius.calculateOverall();
        assertTrue(viniciusOverall >= 80 && viniciusOverall <= 95,
                "Vinicius overall esperado en [80, 95], fue " + viniciusOverall);

        WorldPlayer courtois = findPlayer(persisted, "Courtois");
        assertNotNull(courtois, "Courtois debe estar en el seed");
        int courtoisOverall = courtois.calculateOverall();
        assertTrue(courtoisOverall >= 80 && courtoisOverall <= 95,
                "Courtois overall esperado en [80, 95], fue " + courtoisOverall);
    }

    @Test
    void execute_setsOriginToReal() {
        WorldSnapshot emptySnapshot = new WorldSnapshot();
        emptySnapshot.setUserId(UUID.randomUUID());
        emptySnapshot.setLeagues(new java.util.ArrayList<>());
        emptySnapshot.setWorldTeams(new HashMap<>());
        emptySnapshot.setWorldPlayers(new HashMap<>());

        when(snapshotService.getSnapshot(any(UUID.class))).thenReturn(Mono.just(emptySnapshot));
        when(snapshotService.saveSnapshot(any(WorldSnapshot.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(service.execute(UUID.randomUUID()))
                .assertNext(r -> assertTrue(r.playersCount() > 0))
                .verifyComplete();

        WorldSnapshot persisted = emptySnapshot;
        int realCount = 0;
        for (WorldPlayer p : persisted.getWorldPlayers().values()) {
            if (p.getOrigin() == WorldPlayer.WorldPlayerOrigin.REAL) {
                realCount++;
            }
        }
        assertEquals(persisted.getWorldPlayers().size(), realCount,
                "Todos los players del seed LaLiga deben tener origin=REAL");
    }

    private WorldPlayer findPlayer(WorldSnapshot snapshot, String namePrefix) {
        return snapshot.getWorldPlayers().values().stream()
                .filter(p -> p.getName() != null && p.getName().contains(namePrefix))
                .findFirst()
                .orElse(null);
    }

    @SuppressWarnings("unused")
    private WorldTeam findTeam(WorldSnapshot snapshot, String name) {
        return snapshot.getWorldTeams().values().stream()
                .filter(t -> t.getName() != null && t.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }
}
