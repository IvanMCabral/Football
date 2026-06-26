package com.footballmanager.application.service.simulation.v24;

import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.valueobject.PlayerSkill;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V25D32-F4: V24PlayerMatchState — height + skills plumbing tests.
 *
 * <p>Verifica que los nuevos fields se copian correctamente del SessionPlayer
 * al V24PlayerMatchState, y que el engine NO los usa todavia (no impact en
 * V25D32). El engine impact viene en V25D33-V25D34.
 */
class V24PlayerMatchStateSkillsTest {

    @Test
    void fromSessionPlayer_copiesHeightAndSkills() {
        SessionPlayer player = new SessionPlayer();
        player.setSessionPlayerId("sp-test-1");
        player.setName("Mbappe");
        player.setPosition("ATT");
        player.setAttack(94);
        player.setDefense(40);
        player.setTechnique(89);
        player.setSpeed(96);
        player.setStamina(82);
        player.setMentality(80);
        player.setEnergy(100);
        player.setForm(85);
        player.setHeightCm(178);
        player.setSkillLevel(PlayerSkill.SHOOTER, 90);
        player.setSkillLevel(PlayerSkill.DRIBBLER, 88);
        player.setSkillLevel(PlayerSkill.SPEEDSTER, 92);

        V24PlayerMatchState state = V24PlayerMatchState.fromSessionPlayer(player, "team-A");

        assertEquals(178, state.heightCm(),
                "heightCm debe copiarse del SessionPlayer");
        assertNotNull(state.skillLevels(), "skillLevels debe inicializarse");
        assertEquals(90, state.skillLevels().get(PlayerSkill.SHOOTER));
        assertEquals(88, state.skillLevels().get(PlayerSkill.DRIBBLER));
        assertEquals(92, state.skillLevels().get(PlayerSkill.SPEEDSTER));
    }

    @Test
    void fromSessionPlayer_nullHeightAndEmptySkills_yieldsNullAndEmpty() {
        // Caso normal: player random del seed sin height/skills (V25D32).
        // V24D32 engine NO los usa — el state los carga como null/empty.
        SessionPlayer player = new SessionPlayer();
        player.setSessionPlayerId("sp-test-2");
        player.setName("Random Player");
        player.setPosition("MID");
        player.setAttack(70);
        player.setDefense(70);
        player.setTechnique(70);
        player.setSpeed(70);
        player.setStamina(70);
        player.setMentality(70);
        // NO setHeightCm, NO setSkillLevel

        V24PlayerMatchState state = V24PlayerMatchState.fromSessionPlayer(player, "team-B");

        assertNull(state.heightCm(),
                "heightCm null en SessionPlayer → null en V24PlayerMatchState");
        assertNotNull(state.skillLevels());
        assertTrue(state.skillLevels().isEmpty(),
                "skillLevels vacio en SessionPlayer → empty map en V24PlayerMatchState");
    }

    @Test
    void fromSessionPlayer_sparseMapOnlyContainsNonZeroSkills() {
        // V25D31: skill con level 0 se omite del sparse map. Verificamos que
        // el state tambien omite esos skills (no quedan con level 0 explicito).
        SessionPlayer player = new SessionPlayer();
        player.setSessionPlayerId("sp-test-3");
        player.setName("Player C");
        player.setPosition("GK");
        player.setAttack(20);
        player.setDefense(85);
        player.setTechnique(75);
        player.setSpeed(55);
        player.setStamina(75);
        player.setMentality(85);
        player.setHeightCm(188);
        player.setSkillLevel(PlayerSkill.WALL, 92);
        // setSkillLevel(SHOOTER, 0) → lo omite del sparse map
        player.setSkillLevel(PlayerSkill.SHOOTER, 0);

        V24PlayerMatchState state = V24PlayerMatchState.fromSessionPlayer(player, "team-C");

        assertEquals(92, state.skillLevels().get(PlayerSkill.WALL));
        assertFalse(state.skillLevels().containsKey(PlayerSkill.SHOOTER),
                "SHOOTER con level 0 debe estar AUSENTE del sparse map (no presente con 0)");
        // Tamano: solo WALL
        assertEquals(1, state.skillLevels().size());
    }

    @Test
    void skillLevels_isReadOnly() {
        // V24PlayerMatchState.skillLevels() debe retornar unmodifiable view
        // (defensiva: mutar el map del state NO debe afectar al SessionPlayer source).
        SessionPlayer player = new SessionPlayer();
        player.setSessionPlayerId("sp-test-4");
        player.setName("Player D");
        player.setPosition("ATT");
        player.setAttack(80);
        player.setDefense(50);
        player.setTechnique(80);
        player.setSpeed(85);
        player.setStamina(80);
        player.setMentality(80);
        player.setSkillLevel(PlayerSkill.SHOOTER, 80);

        V24PlayerMatchState state = V24PlayerMatchState.fromSessionPlayer(player, "team-D");
        Map<PlayerSkill, Integer> view = state.skillLevels();

        assertThrows(UnsupportedOperationException.class,
                () -> view.put(PlayerSkill.SHOOTER, 99),
                "skillLevels() debe retornar view read-only");
    }

    @Test
    void fromSessionPlayer_defensiveCopyOnSkills() {
        // Si el caller muta el map del SessionPlayer despues de crear el state,
        // el state NO debe cambiar (defensive copy).
        Map<PlayerSkill, Integer> skills = new HashMap<>();
        skills.put(PlayerSkill.SHOOTER, 80);

        SessionPlayer player = new SessionPlayer();
        player.setSessionPlayerId("sp-test-5");
        player.setName("Player E");
        player.setPosition("ATT");
        player.setAttack(80);
        player.setDefense(50);
        player.setTechnique(80);
        player.setSpeed(85);
        player.setStamina(80);
        player.setMentality(80);
        for (Map.Entry<PlayerSkill, Integer> e : skills.entrySet()) {
            player.setSkillLevel(e.getKey(), e.getValue());
        }

        V24PlayerMatchState state = V24PlayerMatchState.fromSessionPlayer(player, "team-E");
        assertEquals(80, state.skillLevels().get(PlayerSkill.SHOOTER));

        // Mutamos el map source
        skills.put(PlayerSkill.SHOOTER, 99);
        skills.put(PlayerSkill.SPEEDSTER, 95);

        // El state debe seguir con sus valores originales
        assertEquals(80, state.skillLevels().get(PlayerSkill.SHOOTER),
                "Defensive copy: mutar source map no debe afectar al state");
        assertEquals(1, state.skillLevels().size(),
                "Defensive copy: agregar skills al source no debe aparecer en el state");
    }
}
