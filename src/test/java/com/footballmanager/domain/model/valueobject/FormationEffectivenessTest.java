package com.footballmanager.domain.model.valueobject;

import com.footballmanager.adapters.in.web.career.lineup.dto.LineupSlotDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V25D47 (Sprint C11a): unit tests for {@link FormationEffectiveness}.
 *
 * <p>Coverage:
 * <ul>
 *   <li>Static factory {@link FormationEffectiveness#from(List, Map)}:
 *     correct inference + correct per-player effectiveness multipliers.</li>
 *   <li>Backward compat: null inputs, empty inputs.</li>
 *   <li>Team average computation.</li>
 *   <li>{@link FormationEffectiveness#empty()} shape.</li>
 * </ul>
 */
@DisplayName("FormationEffectiveness — V25D47 aggregate (infer + per-player effectiveness)")
class FormationEffectivenessTest {

    private LineupSlotDTO slot(String playerId, String subdivisionId) {
        return new LineupSlotDTO(playerId, subdivisionId);
    }

    @Test
    @DisplayName("from: 4-4-2 lineup with all-natural positions → teamAverage=1.0")
    void from_perfectLineup() {
        // 11 players at their natural positions: 1 GK + 4 DEF + 4 MID + 2 ATT
        List<LineupSlotDTO> slots = new ArrayList<>();
        slots.add(slot("p1", "GK-1"));                          // GK → GK
        slots.add(slot("p2", "S27-1")); slots.add(slot("p3", "S24-1"));  // 2 DEF
        slots.add(slot("p4", "S21-1")); slots.add(slot("p5", "S19-1"));
        slots.add(slot("p6", "S18-1")); slots.add(slot("p7", "S15-1"));  // 2 MID
        slots.add(slot("p8", "S12-1")); slots.add(slot("p9", "S09-1"));
        slots.add(slot("p10", "S06-1")); slots.add(slot("p11", "S01-1")); // 2 ATT

        Map<String, String> natural = new LinkedHashMap<>();
        natural.put("p1", "GK");
        natural.put("p2", "DEF"); natural.put("p3", "DEF");
        natural.put("p4", "DEF"); natural.put("p5", "DEF");
        natural.put("p6", "MID"); natural.put("p7", "MID");
        natural.put("p8", "MID"); natural.put("p9", "MID");
        natural.put("p10", "ATT"); natural.put("p11", "ATT");

        FormationEffectiveness fe = FormationEffectiveness.from(slots, natural);
        assertEquals("4-4-2", fe.inferredFormation());
        assertEquals(11, fe.perPlayerEffectiveness().size());
        assertEquals(1.0, fe.teamAverage(), 0.0001,
                "All-natural lineup → every effectiveness is 1.0 → team average 1.0");
    }

    @Test
    @DisplayName("from: CB in MID slot → effectiveness 0.8, teamAverage reflects penalty")
    void from_cbInMidPenalty() {
        // 1 GK + 4 DEF + 4 MID + 2 ATT, but player p6 is a CB placed in MID slot
        List<LineupSlotDTO> slots = new ArrayList<>();
        slots.add(slot("p1", "GK-1"));
        slots.add(slot("p2", "S27-1")); slots.add(slot("p3", "S24-1"));
        slots.add(slot("p4", "S21-1")); slots.add(slot("p5", "S19-1"));
        slots.add(slot("p6", "S18-1"));    // CB (natural DEF) placed in MID slot
        slots.add(slot("p7", "S15-1"));
        slots.add(slot("p8", "S12-1")); slots.add(slot("p9", "S09-1"));
        slots.add(slot("p10", "S06-1")); slots.add(slot("p11", "S01-1"));

        Map<String, String> natural = new LinkedHashMap<>();
        natural.put("p1", "GK");
        natural.put("p2", "DEF"); natural.put("p3", "DEF");
        natural.put("p4", "DEF"); natural.put("p5", "DEF");
        natural.put("p6", "DEF"); // CB placed in MID
        natural.put("p7", "MID"); natural.put("p8", "MID");
        natural.put("p9", "MID"); natural.put("p10", "ATT");
        natural.put("p11", "ATT");

        FormationEffectiveness fe = FormationEffectiveness.from(slots, natural);
        assertEquals("4-4-2", fe.inferredFormation());
        // p6 (CB in MID) → 0.8. All others at natural position → 1.0.
        // teamAverage = (10*1.0 + 0.8) / 11 = 10.8/11 ≈ 0.9818
        assertEquals(0.8, fe.perPlayerEffectiveness().get("p6"));
        assertEquals(10.8 / 11.0, fe.teamAverage(), 0.0001);
    }

    @Test
    @DisplayName("from: WINGER in MID slot → effectiveness 0.95 (carrilero flexibility)")
    void from_wingerCarrilero() {
        // 11 players but a WINGER in MID slot
        List<LineupSlotDTO> slots = new ArrayList<>();
        slots.add(slot("p1", "GK-1"));
        slots.add(slot("p2", "S27-1")); slots.add(slot("p3", "S24-1"));
        slots.add(slot("p4", "S21-1")); slots.add(slot("p5", "S19-1"));
        slots.add(slot("p6", "S18-1"));
        slots.add(slot("p7", "S15-1")); slots.add(slot("p8", "S12-1"));
        slots.add(slot("p9", "S09-1"));    // WINGER placed in MID slot
        slots.add(slot("p10", "S06-1")); slots.add(slot("p11", "S01-1"));

        Map<String, String> natural = new LinkedHashMap<>();
        natural.put("p9", "WINGER");

        FormationEffectiveness fe = FormationEffectiveness.from(slots, natural);
        assertEquals(0.95, fe.perPlayerEffectiveness().get("p9"),
                "WINGER in MID → 0.95 (carrilero flexibility)");
    }

    @Test
    @DisplayName("from: null slots → defaults to '4-4-2' + empty map + teamAverage=1.0")
    void from_nullSlots() {
        FormationEffectiveness fe = FormationEffectiveness.from(null, Map.of());
        assertEquals("4-4-2", fe.inferredFormation());
        assertTrue(fe.perPlayerEffectiveness().isEmpty());
        assertEquals(1.0, fe.teamAverage());
    }

    @Test
    @DisplayName("from: empty slots → defaults to '4-4-2' + empty map + teamAverage=1.0")
    void from_emptySlots() {
        FormationEffectiveness fe = FormationEffectiveness.from(List.of(), Map.of());
        assertEquals("4-4-2", fe.inferredFormation());
        assertTrue(fe.perPlayerEffectiveness().isEmpty());
        assertEquals(1.0, fe.teamAverage());
    }

    @Test
    @DisplayName("from: null naturalByPlayer → all per-player effectiveness = 1.0 (backward compat)")
    void from_nullNatural() {
        List<LineupSlotDTO> slots = new ArrayList<>();
        slots.add(slot("p1", "GK-1"));
        slots.add(slot("p2", "S27-1"));
        slots.add(slot("p3", "S24-1"));
        slots.add(slot("p4", "S21-1"));
        slots.add(slot("p5", "S19-1"));
        slots.add(slot("p6", "S18-1"));
        slots.add(slot("p7", "S15-1"));
        slots.add(slot("p8", "S12-1"));
        slots.add(slot("p9", "S09-1"));
        slots.add(slot("p10", "S06-1"));
        slots.add(slot("p11", "S01-1"));
        // naturalByPlayer = null
        FormationEffectiveness fe = FormationEffectiveness.from(slots, null);
        assertEquals("4-4-2", fe.inferredFormation());
        assertEquals(11, fe.perPlayerEffectiveness().size());
        // All 1.0 (unknown natural → 1.0 per PositionEffectivenessCalculator backward compat)
        for (double eff : fe.perPlayerEffectiveness().values()) {
            assertEquals(1.0, eff);
        }
        assertEquals(1.0, fe.teamAverage());
    }

    @Test
    @DisplayName("empty(): default formation + empty perPlayer + teamAverage=1.0")
    void empty() {
        FormationEffectiveness fe = FormationEffectiveness.empty();
        assertEquals(FormationInferer.DEFAULT_FORMATION, fe.inferredFormation());
        assertTrue(fe.perPlayerEffectiveness().isEmpty());
        assertEquals(1.0, fe.teamAverage());
    }
}