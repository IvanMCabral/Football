package com.footballmanager.domain.model.entity;

import com.footballmanager.domain.model.valueobject.PlayerSkill;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V25D33-F0-mapping: WorldPlayer → SessionPlayer height + skill propagation.
 *
 * <p>V25D32 SENIOR flagged that the WorldPlayer → SessionPlayer mapping was
 * silently dropping {@code heightCm} and {@code skillLevels}. The
 * {@link LaLigaSeedService} (V25D32-F3) was setting these on WorldPlayer
 * (top-20 heights hardcoded + curated skills for top-5) but the clone
 * factories in {@link SessionPlayer} were throwing them away. V25D33 closes
 * the gap with new overloads that propagate both, and the existing 5/6-arg
 * overloads delegate to the new ones with {@code null}/{@code empty} so the
 * backward-compat contract is preserved bit-a-bit.
 *
 * <p>Scope:
 * <ul>
 *   <li>New {@code SessionPlayer.cloneFromWorldPlayer(...,heightCm,skillLevels)}
 *       and {@code SessionPlayer.fromWorldPlayer(...,heightCm,skillLevels)}
 *       propagate height + skills.</li>
 *   <li>Legacy 5/6-arg overloads delegate to the new ones with null/empty
 *       (no regression for callers that have not been updated yet).</li>
 *   <li>Bounds are enforced via the existing {@link SessionPlayer#setHeightCm}
 *       ({@code [160, 210]}) and {@link SessionPlayer#setSkillLevel}
 *       ({@code [0, 99]}, sparse — zero removes the entry).</li>
 *   <li>Defensive copy: mutating the input {@code skillLevels} map after
 *       construction does NOT leak into the SessionPlayer.</li>
 * </ul>
 */
class WorldPlayerToSessionPlayerMappingTest {

    @Test
    void cloneWithHeightAndSkills_propagatesBoth() {
        // Top-5 LaLiga pattern: height + curated skills.
        Map<PlayerSkill, Integer> sourceSkills = new HashMap<>();
        sourceSkills.put(PlayerSkill.HEADER, 80);
        sourceSkills.put(PlayerSkill.SHOOTER, 90);

        SessionPlayer sp = SessionPlayer.cloneFromWorldPlayer(
                "wp-1", "Mbappé", "ATT", 25, 91,
                "team-real", 178, sourceSkills);

        assertEquals(Integer.valueOf(178), sp.getHeightCm(),
                "height debe propagarse del WorldPlayer al SessionPlayer");
        assertEquals(80, sp.getSkillLevel(PlayerSkill.HEADER),
                "HEADER skill debe propagarse");
        assertEquals(90, sp.getSkillLevel(PlayerSkill.SHOOTER),
                "SHOOTER skill debe propagarse");
        assertEquals(0, sp.getSkillLevel(PlayerSkill.DRIBBLER),
                "Skills ausentes en el map fuente deben quedar en 0");
    }

    @Test
    void cloneWithoutHeightOrSkills_yieldsNullHeightAndEmptySkills() {
        // 386 LaLiga random players (V25D32-F3 generator) tienen height random
        // pero NO skills (sparse vacio). El SessionPlayer clonado debe
        // reflejar eso: height puede ser null o int, skillLevels={}.
        SessionPlayer sp = SessionPlayer.cloneFromWorldPlayer(
                "wp-2", "Random Player", "MID", 23, 70,
                "team-rayo", null, null);

        assertNull(sp.getHeightCm(), "height null en WorldPlayer -> null en SessionPlayer");
        assertNotNull(sp.getSkillLevels(), "skillLevels nunca debe ser null internamente");
        assertTrue(sp.getSkillLevels().isEmpty(),
                "skillLevels null en WorldPlayer -> map vacio en SessionPlayer");
        assertEquals(0, sp.getSkillLevel(PlayerSkill.HEADER));
        assertEquals(0, sp.getSkillLevel(PlayerSkill.WALL));
    }

    @Test
    void cloneWithEmptySkillMap_keepsEmptyMap() {
        // Edge case: skillLevels no-null pero empty (diferencia semantica con null).
        Map<PlayerSkill, Integer> empty = new HashMap<>();
        SessionPlayer sp = SessionPlayer.cloneFromWorldPlayer(
                "wp-3", "Player X", "DEF", 28, 75,
                "team-atleti", 185, empty);

        assertEquals(Integer.valueOf(185), sp.getHeightCm());
        assertTrue(sp.getSkillLevels().isEmpty(),
                "empty map explicito debe quedar como empty map (no null)");
    }

    @Test
    void cloneDefensiveCopy_inputSkillMapMutationDoesNotLeak() {
        // El caller (LaLigaSeedService / use cases) tiene una referencia al
        // WorldPlayer.getSkillLevels() (unmodifiable view) pero si por error
        // pasamos un Map mutable y despues lo mutamos, el SessionPlayer NO
        // debe ver el cambio. Defensive copy via setSkillLevel iteracion.
        Map<PlayerSkill, Integer> mutable = new HashMap<>();
        mutable.put(PlayerSkill.HEADER, 80);

        SessionPlayer sp = SessionPlayer.cloneFromWorldPlayer(
                "wp-4", "Vinícius", "WINGER", 23, 88,
                "team-real", 176, mutable);

        // Mutamos el map fuente despues de la clonacion
        mutable.put(PlayerSkill.SHOOTER, 95);
        mutable.put(PlayerSkill.HEADER, 99);  // intento de cambiar HEADER post-clone

        assertEquals(80, sp.getSkillLevel(PlayerSkill.HEADER),
                "Mutacion del map fuente POST-clone NO debe afectar al SessionPlayer");
        assertEquals(0, sp.getSkillLevel(PlayerSkill.SHOOTER),
                "Skills agregados al map fuente POST-clone NO deben aparecer");
    }

    @Test
    void legacy5ArgOverload_delegatesWithNullsAndEmpty() {
        // Backward compat: callers existentes que pasan el 5-arg overload
        // (sin height/skills) deben seguir funcionando y producir
        // height=null + skillLevels vacio (no null).
        SessionPlayer sp = SessionPlayer.cloneFromWorldPlayer(
                "wp-5", "Legacy Caller", "GK", 30, 80, "team-getafe");

        assertNull(sp.getHeightCm());
        assertNotNull(sp.getSkillLevels());
        assertTrue(sp.getSkillLevels().isEmpty());
    }

    @Test
    void heightOutOfBounds_throwsIllegalArgument() {
        // Los bounds [160, 210] los enforce el setter de SessionPlayer.
        // Si el WorldPlayer tiene height fuera de rango (no deberia pasar
        // porque el seeder valida, pero defendemos en profundidad),
        // el cloneFromWorldPlayer debe tirar IAE — NO silenciar a null.
        IllegalArgumentException exBelow = assertThrows(IllegalArgumentException.class, () ->
                SessionPlayer.cloneFromWorldPlayer(
                        "wp-bad-low", "Short", "ATT", 22, 70,
                        "team-x", 150, null),
                "height < 160 debe tirar IAE");
        assertTrue(exBelow.getMessage().contains("160"));

        IllegalArgumentException exAbove = assertThrows(IllegalArgumentException.class, () ->
                SessionPlayer.cloneFromWorldPlayer(
                        "wp-bad-high", "Tall", "ATT", 22, 70,
                        "team-x", 220, null),
                "height > 210 debe tirar IAE");
        assertTrue(exAbove.getMessage().contains("210"));
    }

    @Test
    void skillLevelOutOfBounds_throwsIllegalArgument() {
        // Bounds [0, 99]. Si el seeder produce un valor fuera de rango,
        // el cloneFromWorldPlayer debe tirar IAE — NO aceptar silenciosamente.
        Map<PlayerSkill, Integer> badAbove = new HashMap<>();
        badAbove.put(PlayerSkill.HEADER, 100);
        assertThrows(IllegalArgumentException.class, () ->
                SessionPlayer.cloneFromWorldPlayer(
                        "wp-bad-skill", "Player", "ATT", 25, 80,
                        "team-x", 180, badAbove),
                "HEADER=100 (>99) debe tirar IAE");

        Map<PlayerSkill, Integer> badBelow = new HashMap<>();
        badBelow.put(PlayerSkill.HEADER, -1);
        assertThrows(IllegalArgumentException.class, () ->
                SessionPlayer.cloneFromWorldPlayer(
                        "wp-bad-skill2", "Player", "ATT", 25, 80,
                        "team-x", 180, badBelow),
                "HEADER=-1 (<0) debe tirar IAE");
    }

    @Test
    void sparseSkillMap_zeroValueIsRemoved() {
        // Sparse contract: skillLevels solo contiene entries con level > 0.
        // Si el WorldPlayer tiene HEADER=0 (que es el default si no se setea),
        // NO debe aparecer en el SessionPlayer.getSkillLevels() map.
        Map<PlayerSkill, Integer> sparse = new HashMap<>();
        sparse.put(PlayerSkill.HEADER, 0);
        sparse.put(PlayerSkill.SHOOTER, 85);

        SessionPlayer sp = SessionPlayer.cloneFromWorldPlayer(
                "wp-sparse", "Sparse Player", "ATT", 24, 78,
                "team-x", 180, sparse);

        assertEquals(0, sp.getSkillLevel(PlayerSkill.HEADER),
                "HEADER=0 -> getSkillLevel devuelve 0");
        assertFalse(sp.getSkillLevels().containsKey(PlayerSkill.HEADER),
                "HEADER=0 NO debe aparecer en el sparse map");
        assertEquals(85, sp.getSkillLevel(PlayerSkill.SHOOTER),
                "SHOOTER=85 debe propagarse normal");
    }

    @Test
    void fromWorldPlayer7Arg_directFactory_propagatesBoth() {
        // fromWorldPlayer 7-arg es la factory interna; cloneFromWorldPlayer
        // delega aqui. Verificamos el path directo.
        Map<PlayerSkill, Integer> skills = new HashMap<>();
        skills.put(PlayerSkill.DRIBBLER, 95);

        SessionPlayer sp = SessionPlayer.fromWorldPlayer(
                "wp-direct", "Direct Factory", "WINGER", 22, 85,
                175, skills);

        assertEquals(Integer.valueOf(175), sp.getHeightCm());
        assertEquals(95, sp.getSkillLevel(PlayerSkill.DRIBBLER));
        assertEquals(SessionPlayer.SessionPlayerOrigin.CLONED, sp.getOrigin());
    }

    @Test
    void fromWorldPlayer5Arg_legacyOverload_delegatesWithNulls() {
        // Backward compat: fromWorldPlayer 5-arg (legacy) sigue funcionando.
        SessionPlayer sp = SessionPlayer.fromWorldPlayer(
                "wp-legacy", "Legacy", "DEF", 27, 72);

        assertNull(sp.getHeightCm());
        assertTrue(sp.getSkillLevels().isEmpty());
        assertEquals(SessionPlayer.SessionPlayerOrigin.CLONED, sp.getOrigin());
    }

    @Test
    void sessionPlayerIdMatchesWorldPlayerId() {
        // El cloneFromWorldPlayer mantiene la identidad: sessionPlayerId ==
        // worldPlayerId (mismo UUID para que el engine pueda lookup).
        SessionPlayer sp = SessionPlayer.cloneFromWorldPlayer(
                "wp-id-test", "Identity Test", "MID", 25, 80,
                "team-x", 180, Map.of());

        assertEquals("wp-id-test", sp.getSessionPlayerId());
        assertEquals("wp-id-test", sp.getWorldPlayerId());
    }

    @Test
    void nullSkillEntriesInMapAreSkipped() {
        // Defensive: si el map tiene null entries (PlayerSkill=null o
        // Integer=null), el cloneFromWorldPlayer los SKIP sin tirar NPE.
        Map<PlayerSkill, Integer> messy = new HashMap<>();
        messy.put(PlayerSkill.HEADER, 75);
        messy.put(null, 50);                       // null key — skip
        messy.put(PlayerSkill.WALL, null);         // null value — skip

        SessionPlayer sp = SessionPlayer.cloneFromWorldPlayer(
                "wp-messy", "Messy", "ATT", 25, 80,
                "team-x", 180, messy);

        assertEquals(75, sp.getSkillLevel(PlayerSkill.HEADER));
        assertEquals(0, sp.getSkillLevel(PlayerSkill.WALL),
                "null value debe tratarse como ausente (0)");
        assertFalse(sp.getSkillLevels().containsKey(null),
                "null key NO debe aparecer en el sparse map");
    }
}