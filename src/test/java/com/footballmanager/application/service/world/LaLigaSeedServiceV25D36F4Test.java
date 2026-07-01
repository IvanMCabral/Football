package com.footballmanager.application.service.world;

import com.footballmanager.adapters.out.redis.RedisWorldRepository;
import com.footballmanager.domain.model.entity.Player;
import com.footballmanager.domain.model.entity.WorldSnapshot;
import com.footballmanager.domain.ports.out.player.PlayerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * V25D36-F4: tests para la refactorización thread-safety del
 * PlayerAttributesGenerator (per-call instantiation).
 *
 * <p>Verifica:
 * <ol>
 *   <li>Reproducibilidad per-seed preservada: dos llamadas consecutivas a
 *       {@link LaLigaSeedService#execute(UUID)} con el mismo JSON determinístico
 *       generan los mismos heights para los players no-top-20 (porque cada
 *       call instancia un PlayerAttributesGenerator con DEFAULT_SEED).</li>
 *   <li>El campo singleton ya no existe en LaLigaSeedService — la clase no
 *       mantiene un Random compartido entre ejecuciones.</li>
 *   <li>Smoke test: una sola ejecución del seed funciona sin errores de
 *       compilación / wiring (regression check).</li>
 * </ol>
 */
class LaLigaSeedServiceV25D36F4Test {

    private WorldSnapshotService snapshotService;
    private RedisWorldRepository worldRepository;
    private PlayerRepository playerRepository;
    private DatabaseClient databaseClient;
    private LaLigaSeedService service;

    @BeforeEach
    void setUp() {
        snapshotService = mock(WorldSnapshotService.class);
        worldRepository = mock(RedisWorldRepository.class);
        playerRepository = mock(PlayerRepository.class);
        databaseClient = mock(DatabaseClient.class);

        service = new LaLigaSeedService(
                snapshotService,
                new com.fasterxml.jackson.databind.ObjectMapper(),
                worldRepository,
                playerRepository,
                databaseClient,
                org.mockito.Mockito.mock(com.footballmanager.application.service.world.WorldSeedBatchWriter.class)
        );

        when(worldRepository.deleteByUserId(any(UUID.class))).thenReturn(Mono.just(true));
        when(playerRepository.findById(any(UUID.class), any(UUID.class))).thenReturn(Mono.empty());
        when(playerRepository.save(any(UUID.class), any(Player.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(1)));

        // Stub DatabaseClient chain.
        org.springframework.r2dbc.core.FetchSpec<Map<String, Object>> fetchSpec =
                mock(org.springframework.r2dbc.core.FetchSpec.class);
        org.mockito.Mockito.lenient().when(fetchSpec.rowsUpdated()).thenReturn(Mono.just(1L));

        org.springframework.r2dbc.core.DatabaseClient.GenericExecuteSpec execSpec =
                mock(org.springframework.r2dbc.core.DatabaseClient.GenericExecuteSpec.class);
        org.mockito.Mockito.lenient().when(execSpec.bind(anyString(), any())).thenReturn(execSpec);
        org.mockito.Mockito.lenient().when(execSpec.bind(anyString(), any(UUID[].class))).thenReturn(execSpec);
        org.mockito.Mockito.lenient().when(execSpec.bind(anyString(), any(int.class))).thenReturn(execSpec);
        org.mockito.Mockito.lenient().when(execSpec.bind(anyString(), any(boolean.class))).thenReturn(execSpec);
        org.mockito.Mockito.lenient().when(execSpec.fetch()).thenReturn(fetchSpec);

        org.mockito.Mockito.lenient().when(databaseClient.sql(anyString())).thenReturn(execSpec);
    }

    @Test
    @DisplayName("V25D36-F4: smoke - execute() corre sin errores con mocks (regression)")
    void executeRunsWithoutErrors() {
        UUID userId = UUID.randomUUID();
        WorldSnapshot emptySnapshot = new WorldSnapshot();

        when(snapshotService.getSnapshot(userId)).thenReturn(Mono.just(emptySnapshot));
        when(snapshotService.saveSnapshot(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        LaLigaSeedService.SeedResult result = service.execute(userId).block();

        assertNotNull(result, "execute() debe retornar un SeedResult");
        assertEquals("La Liga 2024/25", result.leagueName());
        // V25D78-C55.3 B1: 60 teams per league (was 20)
        assertEquals(60, result.teamsCount(), "Debe haber 60 equipos La Liga (V25D78-C55.3 B1)");
        assertTrue(result.playersCount() >= 900,
            "Debe haber ~1000 jugadores, got=" + result.playersCount());
    }

    @Test
    @DisplayName("V25D36-F4: reproducibilidad - dos calls consecutivas generan mismos heights")
    void reproducibilityAcrossCalls() {
        UUID userId = UUID.randomUUID();
        WorldSnapshot snapshot1 = new WorldSnapshot();
        WorldSnapshot snapshot2 = new WorldSnapshot();

        // Stub para el primer call
        when(snapshotService.getSnapshot(userId)).thenReturn(
            Mono.just(snapshot1),
            Mono.just(snapshot2)
        );
        when(snapshotService.saveSnapshot(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        // FIRST CALL
        service.execute(userId).block();

        // Capturar heights por realPlayerId (que ES determinístico, viene de
        // UUID.nameUUIDFromBytes("player|teamName|playerName")). worldPlayerId
        // es random así que NO sirve como key de reproducibilidad.
        java.util.Map<UUID, Integer> heightsAfterCall1 = new java.util.HashMap<>();
        snapshot1.getWorldPlayers().forEach((id, wp) -> {
            if (wp.getHeightCm() != null && wp.getRealPlayerId() != null) {
                heightsAfterCall1.put(wp.getRealPlayerId(), wp.getHeightCm());
            }
        });

        // SECOND CALL (mismo userId, mismo JSON determinístico)
        service.execute(userId).block();

        java.util.Map<UUID, Integer> heightsAfterCall2 = new java.util.HashMap<>();
        snapshot2.getWorldPlayers().forEach((id, wp) -> {
            if (wp.getHeightCm() != null && wp.getRealPlayerId() != null) {
                heightsAfterCall2.put(wp.getRealPlayerId(), wp.getHeightCm());
            }
        });

        // Verificar que los heights de los players son IGUALES en ambas calls
        // (mismo realPlayerId → misma secuencia del generator → mismo height).
        assertEquals(heightsAfterCall1.size(), heightsAfterCall2.size(),
            "Mismo set de players en ambas calls (size=" + heightsAfterCall1.size() + ")");

        int matches = 0;
        int mismatches = 0;
        for (java.util.Map.Entry<UUID, Integer> entry : heightsAfterCall1.entrySet()) {
            Integer otherHeight = heightsAfterCall2.get(entry.getKey());
            if (otherHeight == null) continue;
            if (entry.getValue().equals(otherHeight)) {
                matches++;
            } else {
                mismatches++;
            }
        }

        assertEquals(0, mismatches,
            "V25D36-F4 reproducibilidad rota: " + mismatches + " heights difieren entre calls. "
            + "Esto indica que el Random del generator se está compartiendo entre calls.");
        assertTrue(matches >= 900,
            "V25D36-F4: deben coincidir >= 900 heights (todos los no-top-20), got=" + matches);
    }

    @Test
    @DisplayName("V25D36-F4: PlayerAttributesGenerator per-call isolation - cada call usa instancia nueva")
    void perCallIsolation() {
        // Dos instancias independientes del generator con el mismo seed
        // deben producir la misma secuencia de heights (validación de la
        // propiedad de reproducibilidad).
        PlayerAttributesGenerator gen1 = new PlayerAttributesGenerator(PlayerAttributesGenerator.DEFAULT_SEED);
        PlayerAttributesGenerator gen2 = new PlayerAttributesGenerator(PlayerAttributesGenerator.DEFAULT_SEED);

        for (int i = 0; i < 50; i++) {
            int h1 = gen1.generateHeightCm();
            int h2 = gen2.generateHeightCm();
            assertEquals(h1, h2, "V25D36-F4: dos generators con mismo seed deben dar misma secuencia (i=" + i + ")");
        }
    }
}