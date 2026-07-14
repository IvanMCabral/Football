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
        TestHarnessUseCaseImpl useCase = new TestHarnessUseCaseImpl(null, null, null, null, null);
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
