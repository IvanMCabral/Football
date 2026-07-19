package com.footballmanager.application.service.testharness;

import com.footballmanager.adapters.in.web.career.lineup.dto.FormationDTO;
import com.footballmanager.adapters.in.web.career.lineup.dto.LineupSlotDTO;
import com.footballmanager.application.service.editor.FormationService;
import com.footballmanager.domain.model.entity.SessionPlayer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestHarnessFormationMatrixSlotAssignmentTest {

    @Test
    @DisplayName("Formation matrix assigns shuffled starters by role, not list index")
    @SuppressWarnings("unchecked")
    void formationMatrixSlotsUseRoleFitInsteadOfStarterIndex() throws Exception {
        TestHarnessUseCaseImpl useCase = new TestHarnessUseCaseImpl(null, null, null, null, null, null);
        FormationDTO formation = new FormationService().getFormationByName("4-4-2");
        List<SessionPlayer> shuffled = List.of(
            player("att0", "ATT"),
            player("mid0", "MID"),
            player("def0", "DEF"),
            player("gk0", "GK"),
            player("mid1", "MID"),
            player("att1", "ATT"),
            player("def1", "DEF"),
            player("mid2", "MID"),
            player("def2", "DEF"),
            player("mid3", "MID"),
            player("def3", "DEF")
        );

        Method method = TestHarnessUseCaseImpl.class.getDeclaredMethod(
            "buildFormationMatrixSlots", List.class, FormationDTO.class);
        method.setAccessible(true);

        Map<String, LineupSlotDTO> slots =
            (Map<String, LineupSlotDTO>) method.invoke(useCase, shuffled, formation);

        assertEquals("GK-1", slots.get("gk0").subdivisionId(),
            "GK must be assigned to the GK slot even when starters are shuffled");
        assertTrue(slots.get("def0").subdivisionId().startsWith("S2"),
            "DEF must land in the defensive line, not in the first ATT slot");
        assertTrue(slots.get("att0").customYPercent() <= 25.0,
            "ATT must land high in the formation, not wherever its source index was");
    }

    @Test
    @DisplayName("Position pixel fallback keeps real roles away from generic midfield")
    void positionPixelFallbackNormalizesRealFootballRoles() throws Exception {
        TestHarnessUseCaseImpl useCase = new TestHarnessUseCaseImpl(null, null, null, null, null, null);
        Method fallbackSubdivision = TestHarnessUseCaseImpl.class.getDeclaredMethod("fallbackSubdivision", String.class);
        Method fallbackYPercent = TestHarnessUseCaseImpl.class.getDeclaredMethod("fallbackYPercent", String.class);
        Method canonicalXPercent = TestHarnessUseCaseImpl.class.getDeclaredMethod("canonicalXPercent", String.class);
        Method canonicalYPercent = TestHarnessUseCaseImpl.class.getDeclaredMethod("canonicalYPercent", String.class);
        fallbackSubdivision.setAccessible(true);
        fallbackYPercent.setAccessible(true);
        canonicalXPercent.setAccessible(true);
        canonicalYPercent.setAccessible(true);

        String rbSlot = (String) fallbackSubdivision.invoke(useCase, "RB");
        String cfSlot = (String) fallbackSubdivision.invoke(useCase, "CF");

        assertEquals("S24-3", rbSlot, "RB fallback must stay wide/right defensive, not generic midfield");
        assertEquals("S05-2", cfSlot, "CF fallback must stay high central, not generic midfield");
        assertEquals(78.0, (double) fallbackYPercent.invoke(useCase, "RB"), 0.01);
        assertEquals(18.0, (double) fallbackYPercent.invoke(useCase, "CF"), 0.01);
        assertTrue(((java.util.Optional<Double>) canonicalXPercent.invoke(useCase, rbSlot)).orElseThrow() > 70.0,
            "RB canonical x should be on the right side");
        assertTrue(((java.util.Optional<Double>) canonicalYPercent.invoke(useCase, rbSlot)).orElseThrow() > 70.0,
            "RB canonical y should be defensive");
        assertTrue(((java.util.Optional<Double>) canonicalYPercent.invoke(useCase, cfSlot)).orElseThrow() < 30.0,
            "CF canonical y should be attacking");
    }

    private static SessionPlayer player(String id, String position) {
        SessionPlayer player = new SessionPlayer();
        player.setSessionPlayerId(id);
        player.setWorldPlayerId("wp-" + id);
        player.setName(id);
        player.setPosition(position);
        player.setAttack("ATT".equals(position) ? 86 : 60);
        player.setDefense("DEF".equals(position) || "GK".equals(position) ? 86 : 60);
        player.setTechnique("MID".equals(position) ? 84 : 62);
        player.setSpeed(70);
        player.setStamina(80);
        player.setMentality(78);
        player.setEnergy(100);
        return player;
    }
}
