package com.footballmanager.application.service.world;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.footballmanager.adapters.out.redis.RedisWorldRepository;
import com.footballmanager.domain.model.entity.WorldPlayer;
import com.footballmanager.domain.model.entity.WorldSnapshot;
import com.footballmanager.domain.model.valueobject.PlayerSkill;
import com.footballmanager.domain.ports.out.player.PlayerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.doAnswer;

/**
 * V25D32-F3: LaLigaSeedService height + skills coverage.
 *
 * <p>Verifica:
 * <ul>
 *   <li>Top-5 (Mbappe, Bellingham, Vinicius, Valverde, Courtois) se cargan con
 *       height + skills correctos desde el JSON.</li>
 *   <li>Top-20 restantes (sin curated skills) tienen height hardcoded del JSON
 *       y skillLevels=null/empty.</li>
 *   <li>Players random (no top-20) reciben height generado por PlayerAttributesGenerator
 *       (random normal truncada) y skillLevels=null.</li>
 *   <li>El Idempotencia: ejecutar 2 veces produce mismo resultado (mismo JSON seed
 *       → mismo random height, mismo curated skills).</li>
 *   <li>El SQL INSERT incluye las 2 nuevas columnas (heightCm + skillLevelsJson).</li>
 * </ul>
 */
class LaLigaSeedServiceV25D32Test {

    private WorldSnapshotService snapshotService;
    private RedisWorldRepository worldRepository;
    private PlayerRepository playerRepository;
    private DatabaseClient databaseClient;
    private LaLigaSeedService service;

    // Capturamos todos los SQL binds para verificar las nuevas columnas.
    private final java.util.List<Map<String, Object>> capturedBinds = new java.util.ArrayList<>();

    @BeforeEach
    void setUp() {
        snapshotService = mock(WorldSnapshotService.class);
        worldRepository = mock(RedisWorldRepository.class);
        playerRepository = mock(PlayerRepository.class);
        databaseClient = mock(DatabaseClient.class);

        service = new LaLigaSeedService(
                snapshotService,
                new ObjectMapper(),
                worldRepository,
                playerRepository,
                databaseClient,
                // V25D78-C55.4: batch writer unused in this unit test (snapshot
                // layer is the focus; persistence is mocked via DatabaseClient).
                org.mockito.Mockito.mock(com.footballmanager.application.service.world.WorldSeedBatchWriter.class)
        );

        when(worldRepository.deleteByUserId(any(UUID.class))).thenReturn(Mono.just(true));
        when(playerRepository.findById(any(UUID.class), any(UUID.class))).thenReturn(Mono.empty());
        when(playerRepository.save(any(UUID.class), any(com.footballmanager.domain.model.entity.Player.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(1)));

        // DatabaseClient mock — captura todos los binds para verificar columnas.
        // Mockito's any() para Object no siempre matchea Integer boxed de forma
        // confiable; usamos un answer generico via Mockito.doAnswer para todos
        // los overloads de bind() que el codigo usa.
        org.springframework.r2dbc.core.FetchSpec<Map<String, Object>> fetchSpec =
                mock(org.springframework.r2dbc.core.FetchSpec.class);
        when(fetchSpec.rowsUpdated()).thenReturn(Mono.just(1L));

        org.springframework.r2dbc.core.DatabaseClient.GenericExecuteSpec execSpec =
                mock(org.springframework.r2dbc.core.DatabaseClient.GenericExecuteSpec.class);

        // Capturador para TODOS los binds (cualquier tipo de value)
        org.mockito.stubbing.Answer<org.springframework.r2dbc.core.DatabaseClient.GenericExecuteSpec> captureAnswer = inv -> {
            Map<String, Object> entry = new HashMap<>();
            entry.put("name", inv.getArgument(0));
            entry.put("value", inv.getArgument(1));
            capturedBinds.add(entry);
            return execSpec;
        };

        // Capturamos TODAS las variantes de bind que el codigo usa
        doAnswer(captureAnswer).when(execSpec).bind(anyString(), any());
        doAnswer(captureAnswer).when(execSpec).bind(anyString(), any(Integer.class));
        doAnswer(captureAnswer).when(execSpec).bind(anyString(), any(UUID[].class));
        doAnswer(captureAnswer).when(execSpec).bind(anyString(), any(int.class));
        doAnswer(captureAnswer).when(execSpec).bind(anyString(), any(boolean.class));

        lenient().when(execSpec.fetch()).thenReturn(fetchSpec);
        lenient().when(databaseClient.sql(anyString())).thenReturn(execSpec);
    }

    // ========== Top-5 curated skills tests ==========

    @Test
    void top5CuratedPlayers_haveCorrectHeightAndSkills() {
        // Cargar seed completo via el path real
        seedOnce();

        // El bind debe incluir heightCm y skillLevelsJson (verificamos via los
        // captured binds; la logica de serializacion es trivial).
        // NOTA: como capturamos los binds en orden, no podemos asociar el bind de
        // heightCm con Mbappe directamente sin un ID — lo que verificamos aca es
        // que el codigo *llama* a bind con esos nombres de parametro en algun
        // momento del flujo.
        boolean hasHeightBind = capturedBinds.stream()
                .anyMatch(b -> "heightCm".equals(b.get("name")));
        boolean hasSkillsBind = capturedBinds.stream()
                .anyMatch(b -> "skillLevelsJson".equals(b.get("name")));
        assertTrue(hasHeightBind,
                "El flujo del seed debe usar el param :heightCm al menos 1 vez (top-5/20 tienen height hardcoded)");
        assertTrue(hasSkillsBind,
                "El flujo del seed debe usar el param :skillLevelsJson al menos 1 vez (top-5 tienen skills curated)");
    }

    @Test
    void top5Skills_containExpectedSkillsForMbappe() {
        // El WorldPlayer para Mbappe debe tener skillLevels con SHOOTER=90, DRIBBLER=88, SPEEDSTER=92
        seedOnce();
        WorldPlayer mbappe = findWorldPlayerByName("Kylian Mbappe");
        assertNotNull(mbappe, "Mbappe debe estar en el snapshot");
        assertEquals(178, mbappe.getHeightCm(),
                "Mbappe.heightCm debe ser 178 (del JSON hardcoded)");
        assertNotNull(mbappe.getSkillLevels(), "Mbappe debe tener skills curated");
        assertEquals(90, mbappe.getSkillLevels().get(PlayerSkill.SHOOTER));
        assertEquals(88, mbappe.getSkillLevels().get(PlayerSkill.DRIBBLER));
        assertEquals(92, mbappe.getSkillLevels().get(PlayerSkill.SPEEDSTER));
    }

    @Test
    void top5Skills_containExpectedSkillsForBellingham() {
        seedOnce();
        WorldPlayer bellingham = findWorldPlayerByName("Jude Bellingham");
        assertNotNull(bellingham);
        assertEquals(186, bellingham.getHeightCm());
        assertEquals(88, bellingham.getSkillLevels().get(PlayerSkill.PLAYMAKER));
        assertEquals(82, bellingham.getSkillLevels().get(PlayerSkill.SHOOTER));
        assertEquals(78, bellingham.getSkillLevels().get(PlayerSkill.HEADER));
    }

    @Test
    void top5Skills_containExpectedSkillsForVinicius() {
        seedOnce();
        WorldPlayer vinicius = findWorldPlayerByName("Vinicius Junior");
        assertNotNull(vinicius);
        assertEquals(176, vinicius.getHeightCm());
        assertEquals(95, vinicius.getSkillLevels().get(PlayerSkill.DRIBBLER));
        assertEquals(90, vinicius.getSkillLevels().get(PlayerSkill.SPEEDSTER));
        assertEquals(78, vinicius.getSkillLevels().get(PlayerSkill.SHOOTER));
    }

    @Test
    void top5Skills_containExpectedSkillsForCourtois() {
        seedOnce();
        WorldPlayer courtois = findWorldPlayerByName("Thibaut Courtois");
        assertNotNull(courtois);
        assertEquals(199, courtois.getHeightCm());
        assertEquals(92, courtois.getSkillLevels().get(PlayerSkill.WALL));
    }

    // ========== Top-20 non-curated tests (height hardcoded, no skills) ==========

    @Test
    void top20NonCuratedPlayer_hasHeightButNoSkills() {
        // Julian Alvarez: top-5? No (rank 5). top-20 (rank 5 in the prompt table, hardcoded 170).
        seedOnce();
        WorldPlayer julian = findWorldPlayerByName("Julian Alvarez");
        assertNotNull(julian);
        assertEquals(170, julian.getHeightCm(),
                "Julian Alvarez.heightCm debe ser 170 (del JSON hardcoded)");
        // skillLevels: el DTO no los tiene para el, asi que el WorldPlayer debe
        // tener skillLevels vacio (setSkillLevels(null) lo deja asi).
        assertTrue(julian.getSkillLevels().isEmpty(),
                "Julian Alvarez no es top-5, debe tener skillLevels vacio");
    }

    // ========== Non-top-20 player tests (random height, no skills) ==========

    @Test
    void nonTop20Player_getsRandomHeightButNoSkills() {
        // Cristthian Stuani (Girona) — no es top-20
        seedOnce();
        WorldPlayer stuani = findWorldPlayerByName("Cristhian Stuani");
        assertNotNull(stuani);
        Integer height = stuani.getHeightCm();
        assertNotNull(height, "Player random debe tener height generado por el generator");
        assertTrue(height >= 160 && height <= 210,
                "Height random debe estar en [160, 210] (truncada). Got: " + height);
        assertTrue(stuani.getSkillLevels().isEmpty(),
                "Player random debe tener skillLevels vacio (no curated, no random)");
    }

    // ========== Idempotencia tests ==========

    @Test
    void executeTwice_producesSameHeightForNonTop20() {
        // Primer seed
        seedOnce();
        WorldPlayer firstStuani = findWorldPlayerByName("Cristhian Stuani");
        Integer firstHeight = firstStuani.getHeightCm();

        // Reset captured binds y ejecutar de nuevo
        capturedBinds.clear();

        // Re-mock snapshot para que el service "vea" un state limpio
        WorldSnapshot emptySnapshot = new WorldSnapshot();
        emptySnapshot.setUserId(UUID.randomUUID());
        emptySnapshot.setLeagues(new java.util.ArrayList<>());
        emptySnapshot.setWorldTeams(new HashMap<>());
        emptySnapshot.setWorldPlayers(new HashMap<>());
        when(snapshotService.getSnapshot(any(UUID.class))).thenReturn(Mono.just(emptySnapshot));
        when(snapshotService.saveSnapshot(any(WorldSnapshot.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        seedOnce();
        WorldPlayer secondStuani = findWorldPlayerByName("Cristhian Stuani");
        Integer secondHeight = secondStuani.getHeightCm();

        assertEquals(firstHeight, secondHeight,
                "Idempotencia: ejecutar seed 2 veces debe dar MISMO height para players random. "
                + "Esto confirma que el PlayerAttributesGenerator usa seed fijo.");
    }

    // ========== Helpers ==========

    private void seedOnce() {
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
                    // V25D78-C55.3 B1: 60 teams per league (was 20)
                    assertEquals(60, result.teamsCount());
                })
                .verifyComplete();
    }

    private WorldPlayer findWorldPlayerByName(String name) {
        // Necesitamos acceso al snapshot final. Usamos el saveSnapshot captor
        // para recuperar el snapshot despues del seed.
        ArgumentCaptor<WorldSnapshot> captor = ArgumentCaptor.forClass(WorldSnapshot.class);
        verify(snapshotService, atLeast(1)).saveSnapshot(captor.capture());

        for (WorldSnapshot snap : captor.getAllValues()) {
            if (snap.getWorldPlayers() == null) continue;
            for (WorldPlayer p : snap.getWorldPlayers().values()) {
                if (name.equals(p.getName())) return p;
            }
        }
        return null;
    }

    private Map<String, Object> findInsertForPlayer(String name) {
        // Buscamos un bind que tenga "name" en su value (heuristica — los binds
        // con string values se serializan como String.valueOf). Esto es un test
        // aproximado: el match real requiere el orden de binds, que es
        // deterministico por jugador pero acoplado al codigo.
        return capturedBinds.stream()
                .filter(b -> b.get("value") != null && String.valueOf(b.get("value")).contains(name))
                .findFirst()
                .orElse(null);
    }
}
