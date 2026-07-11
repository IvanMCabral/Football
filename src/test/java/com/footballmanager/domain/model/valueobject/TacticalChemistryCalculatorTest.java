package com.footballmanager.domain.model.valueobject;

import com.footballmanager.adapters.in.web.career.lineup.dto.LineupSlotDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TacticalChemistryCalculator — spatial player links")
class TacticalChemistryCalculatorTest {

    private static final Map<String, String> NATURAL = Map.ofEntries(
            Map.entry("gk", "GK"),
            Map.entry("lb", "DEF"),
            Map.entry("cb1", "DEF"),
            Map.entry("cb2", "DEF"),
            Map.entry("rb", "DEF"),
            Map.entry("lm", "MID"),
            Map.entry("cm1", "MID"),
            Map.entry("cm2", "MID"),
            Map.entry("rm", "MID"),
            Map.entry("st1", "ATT"),
            Map.entry("st2", "ATT")
    );

    private static final Map<String, double[]> COORDS = Map.ofEntries(
            Map.entry("GK-1", new double[]{50.0, 96.0}),
            Map.entry("S22-2", new double[]{16.65, 83.0}),
            Map.entry("S23-1", new double[]{38.85, 83.0}),
            Map.entry("S23-3", new double[]{61.05, 83.0}),
            Map.entry("S24-2", new double[]{83.25, 83.0}),
            Map.entry("S16-2", new double[]{16.65, 61.0}),
            Map.entry("S17-1", new double[]{38.85, 61.0}),
            Map.entry("S17-3", new double[]{61.05, 61.0}),
            Map.entry("S18-2", new double[]{83.25, 61.0}),
            Map.entry("S05-1", new double[]{38.85, 17.0}),
            Map.entry("S05-3", new double[]{61.05, 17.0})
    );

    @Test
    @DisplayName("Connected 4-4-2 produces strong line/channel chemistry")
    void connectedShapeProducesStrongChemistry() {
        TacticalChemistry tc = TacticalChemistryCalculator.calculate(canonical442(), NATURAL, COORDS);

        assertTrue(tc.score() >= 80, "Connected canonical shape should have strong tactical chemistry");
        assertTrue(tc.lineScores().get("DEF") >= 75);
        assertTrue(tc.lineScores().get("MID") >= 75);
        assertTrue(tc.lineScores().get("ATT") >= 75);
        assertFalse(tc.links().isEmpty(), "Should expose player links for UI/debug");
    }

    @Test
    @DisplayName("Custom pixel move changes tactical chemistry")
    void customMoveChangesTacticalChemistry() {
        TacticalChemistry base = TacticalChemistryCalculator.calculate(canonical442(), NATURAL, COORDS);
        List<LineupSlotDTO> moved = canonical442().stream()
                .map(s -> "cm2".equals(s.playerId())
                        ? new LineupSlotDTO(s.playerId(), s.subdivisionId(), 5.0, 18.0)
                        : s)
                .toList();

        TacticalChemistry changed = TacticalChemistryCalculator.calculate(moved, NATURAL, COORDS);

        assertNotEquals(base.score(), changed.score(),
                "Moving a midfielder far away should affect tactical chemistry");
    }

    private static List<LineupSlotDTO> canonical442() {
        return List.of(
                new LineupSlotDTO("gk", "GK-1"),
                new LineupSlotDTO("lb", "S22-2"),
                new LineupSlotDTO("cb1", "S23-1"),
                new LineupSlotDTO("cb2", "S23-3"),
                new LineupSlotDTO("rb", "S24-2"),
                new LineupSlotDTO("lm", "S16-2"),
                new LineupSlotDTO("cm1", "S17-1"),
                new LineupSlotDTO("cm2", "S17-3"),
                new LineupSlotDTO("rm", "S18-2"),
                new LineupSlotDTO("st1", "S05-1"),
                new LineupSlotDTO("st2", "S05-3")
        );
    }
}
