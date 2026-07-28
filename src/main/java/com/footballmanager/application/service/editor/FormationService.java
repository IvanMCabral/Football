package com.footballmanager.application.service.editor;

import com.footballmanager.application.service.editor.FormationDefinition;
import com.footballmanager.application.service.editor.FormationPosition;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Catalogo de formaciones tacticas disponibles.
 *
 * <p>Las posiciones usan porcentajes de campo para que el editor visual, el
 * modal de partido y el motor compartan la misma geometria. Cada linea se
 * distribuye de manera simetrica cuando la formacion lo permite.
 */
@Service
public class FormationService {

    private static final double FIELD_CENTER_X = 50.0;

    // 9 cells per row × 11.11% width = 100%. Cell centers (not left edges).
    private static final double CELL_CENTER_COL0_LEFT  = 5.55;   // col 0 sub 1
    private static final double CELL_CENTER_COL0_MID   = 16.65;  // col 0 sub 2 (S22-2 etc)
    private static final double CELL_CENTER_COL0_RIGHT = 27.75;  // col 0 sub 3 (S22-3)
    private static final double CELL_CENTER_COL1_LEFT  = 38.85;  // col 1 sub 1 (S23-1 etc)
    private static final double CELL_CENTER_COL1_MID   = 49.95;  // col 1 sub 2 (S23-2)
    private static final double CELL_CENTER_COL1_RIGHT = 61.05;  // col 1 sub 3 (S23-3)
    private static final double CELL_CENTER_COL2_LEFT  = 72.15;  // col 2 sub 1 (S24-1)
    private static final double CELL_CENTER_COL2_MID   = 83.25;  // col 2 sub 2 (S24-2)
    private static final double CELL_CENTER_COL2_RIGHT = 94.35;  // col 2 sub 3 (S24-3)

    // Ajustes visuales para evitar fichas pegadas a bandas o arcos.
    private static final double WINGBACK_LEFT_X = 12.0;
    private static final double WINGBACK_RIGHT_X = 88.0;
    private static final double WINGER_LEFT_X = 18.0;
    private static final double WINGER_RIGHT_X = 82.0;
    private static final double FIVE_BACK_WIDE_LEFT_X = 10.0;
    private static final double FIVE_BACK_LEFT_CB_X = 30.0;
    private static final double FIVE_BACK_RIGHT_CB_X = 70.0;
    private static final double FIVE_BACK_WIDE_RIGHT_X = 90.0;
    private static final double SINGLE_STRIKER_Y = 14.0;
    private static final double FRONT_THREE_WIDE_Y = 18.0;
    /** El arquero queda fijo dentro del area chica. */
    private static final double GOALKEEPER_Y = 93.5;

    private final List<FormationDefinition> cachedFormations;

    public FormationService() {
        this.cachedFormations = Collections.unmodifiableList(buildFormations());
    }

    public List<FormationDefinition> getAllFormations() {
        return cachedFormations;
    }

    public FormationDefinition getFormationByName(String name) {
        if (name == null) {
            return null;
        }
        return cachedFormations.stream()
            .filter(f -> name.equals(f.name()))
            .findFirst()
            .orElse(null);
    }

    /**
     * Devuelve las coordenadas de un slot dentro de una formacion.
     *
     * @param formation     canonical formation label (e.g., "4-4-2",
     *                      "5-3-2", "4-2-3-1"); null/blank &rarr; null.
     * @param subdivisionId e.g., {@code "S22-1"}, {@code "GK-1"};
     *                      null/blank &rarr; null.
     * @return {@code {xPercent, yPercent}} or null if not found.
     */
    public double[] getCoordsBySubdivision(String formation, String subdivisionId) {
        if (formation == null || formation.isBlank()
                || subdivisionId == null || subdivisionId.isBlank()) {
            return null;
        }
        FormationDefinition f = getFormationByName(formation);
        if (f == null) {
            return null;
        }
        for (FormationPosition pos : f.positions()) {
            if (subdivisionId.equals(pos.subdivisionId())) {
                return new double[]{pos.xPercent(), pos.yPercent()};
            }
        }
        return null;
    }

    /**
     * Devuelve todas las coordenadas de una formacion, indexadas por slot.
     *
     * @param formation canonical formation label; null/unknown &rarr; empty map.
     * @return immutable map; safe to pass to {@code FormationEffectiveness.from(...)}.
     */
    public Map<String, double[]> getCoordsByFormation(String formation) {
        if (formation == null || formation.isBlank()) {
            return Map.of();
        }
        FormationDefinition f = getFormationByName(formation);
        if (f == null) {
            return Map.of();
        }
        Map<String, double[]> result = new java.util.HashMap<>();
        for (FormationPosition pos : f.positions()) {
            result.put(pos.subdivisionId(),
                    new double[]{pos.xPercent(), pos.yPercent()});
        }
        return java.util.Collections.unmodifiableMap(result);
    }

    private List<FormationDefinition> buildFormations() {
        List<FormationDefinition> formations = new ArrayList<>();

        // 4-4-2: 4 DEF + 4 MID + 2 ATT = 10 outfield + 1 GK
        // Lineas defensiva y media simetricas.
        formations.add(new FormationDefinition(
            "4-4-2",
            "4 defensores, 4 mediocampistas, 2 delanteros",
            4, 4, 2, 10,
            List.of(
                pos(0, "GK", 50.0, GOALKEEPER_Y, 5.0, "GK-1"),
                pos(1, "LB", CELL_CENTER_COL0_MID,  83.0, 7.0, "S22-2"),
                pos(2, "CB", CELL_CENTER_COL1_LEFT, 83.0, 6.0, "S23-1"),
                pos(3, "CB", CELL_CENTER_COL1_RIGHT,83.0, 6.0, "S23-3"),
                pos(4, "RB", CELL_CENTER_COL2_MID,  83.0, 7.0, "S24-2"),
                pos(5, "LM", CELL_CENTER_COL0_MID,  61.0, 8.0, "S16-2"),
                pos(6, "CM", CELL_CENTER_COL1_LEFT, 61.0, 7.0, "S17-1"),
                pos(7, "CM", CELL_CENTER_COL1_RIGHT,61.0, 7.0, "S17-3"),
                pos(8, "RM", CELL_CENTER_COL2_MID,  61.0, 8.0, "S18-2"),
                pos(9,  "ST", CELL_CENTER_COL1_LEFT,  17.0, 7.0, "S05-1"),
                pos(10, "ST", CELL_CENTER_COL1_RIGHT, 17.0, 7.0, "S05-3")
            )
        ));

        // 4-3-3: 4 DEF + 3 MID + 3 ATT = 10 outfield + 1 GK
        // Defensa de cuatro y trio central simetricos.
        formations.add(new FormationDefinition(
            "4-3-3",
            "4 defensores, 3 mediocampistas, 3 delanteros",
            4, 3, 3, 10,
            List.of(
                pos(0, "GK", 50.0, GOALKEEPER_Y, 5.0, "GK-1"),
                pos(1, "LB", CELL_CENTER_COL0_MID,  83.0, 7.0, "S22-2"),
                pos(2, "CB", CELL_CENTER_COL1_LEFT, 83.0, 6.0, "S23-1"),
                pos(3, "CB", CELL_CENTER_COL1_RIGHT,83.0, 6.0, "S23-3"),
                pos(4, "RB", CELL_CENTER_COL2_MID,  83.0, 7.0, "S24-2"),
                pos(5, "CM", CELL_CENTER_COL1_LEFT, 50.0, 8.0, "S17-1"),
                pos(6, "CM", CELL_CENTER_COL1_MID,  55.0, 7.0, "S17-2"),
                pos(7, "CM", CELL_CENTER_COL1_RIGHT,50.0, 8.0, "S17-3"),
                pos(8,  "LW", WINGER_LEFT_X, FRONT_THREE_WIDE_Y, 7.0, "S04-1"),
                pos(9,  "ST", 50.0, 12.0, 6.0, "S05-2"),
                pos(10, "RW", WINGER_RIGHT_X, FRONT_THREE_WIDE_Y, 7.0, "S06-3")
            )
        ));

        // 3-5-2: 3 DEF + 5 MID + 2 ATT = 10 outfield + 1 GK
        // Tres centrales simetricos y carrileros.
        formations.add(new FormationDefinition(
            "3-5-2",
            "3 defensores, 2 WB + 3 CM, 2 delanteros",
            3, 5, 2, 10,
            List.of(
                pos(0, "GK", 50.0, GOALKEEPER_Y, 5.0, "GK-1"),
                pos(1, "CB", CELL_CENTER_COL0_RIGHT, 76.0, 7.0, "S22-3"),
                pos(2, "CB", CELL_CENTER_COL1_MID,   78.0, 6.0, "S23-2"),
                pos(3, "CB", CELL_CENTER_COL2_LEFT,  76.0, 7.0, "S24-1"),
                pos(4, "LWB", WINGBACK_LEFT_X,  56.0, 9.0, "S15-1"),
                pos(5, "CM",  CELL_CENTER_COL1_LEFT,  61.0, 7.0, "S17-1"),
                pos(6, "CM",  CELL_CENTER_COL1_MID,   66.0, 7.0, "S17-2"),
                pos(7, "CM",  CELL_CENTER_COL1_RIGHT, 61.0, 7.0, "S17-3"),
                pos(8, "RWB", WINGBACK_RIGHT_X, 56.0, 9.0, "S18-3"),
                pos(9,  "ST", CELL_CENTER_COL1_LEFT,  17.0, 7.0, "S05-1"),
                pos(10, "ST", CELL_CENTER_COL1_RIGHT, 17.0, 7.0, "S05-3")
            )
        ));

        // 4-2-3-1: 4 DEF + 5 MID (2 CDM + 3 CAM) + 1 ATT = 10 outfield + 1 GK
        // Doble pivote con linea de tres ofensiva.
        formations.add(new FormationDefinition(
            "4-2-3-1",
            "4 defensores, 2 CDM + 3 CAM, 1 delantero",
            4, 5, 1, 10,
            List.of(
                pos(0, "GK", 50.0, GOALKEEPER_Y, 5.0, "GK-1"),
                pos(1, "LB", CELL_CENTER_COL0_MID,  83.0, 7.0, "S22-2"),
                pos(2, "CB", CELL_CENTER_COL1_LEFT, 83.0, 6.0, "S23-1"),
                pos(3, "CB", CELL_CENTER_COL1_RIGHT,83.0, 6.0, "S23-3"),
                pos(4, "RB", CELL_CENTER_COL2_MID,  83.0, 7.0, "S24-2"),
                // CDM line (row 5) — 2 CDM SYMMETRIC
                pos(5, "CDM", CELL_CENTER_COL1_LEFT,  66.0, 7.0, "S17-1"),
                pos(6, "CDM", CELL_CENTER_COL1_RIGHT, 66.0, 7.0, "S17-3"),
                // CAM line (row 3) — 3 CAM SYMMETRIC
                pos(7, "LW",  22.0,  39.0, 8.0, "S10-2"),
                pos(8, "CAM", CELL_CENTER_COL1_MID,  39.0, 8.0, "S11-2"),
                pos(9, "RW",  78.0,  39.0, 8.0, "S12-2"),
                // ATT line (row 0) — single ST centered
                pos(10, "ST", 50.0, SINGLE_STRIKER_Y, 6.0, "S02-2")
            )
        ));

        // 5-3-2: 5 DEF + 3 MID + 2 ATT = 10 outfield + 1 GK
        // Linea defensiva de cinco a lo ancho.
        // Trio central y doble punta simetricos.
        formations.add(new FormationDefinition(
            "5-3-2",
            "5 defensores, 3 mediocampistas centrales, 2 delanteros",
            5, 3, 2, 10,
            List.of(
                pos(0, "GK", 50.0, GOALKEEPER_Y, 5.0, "GK-1"),
                pos(1, "LWB", FIVE_BACK_WIDE_LEFT_X,  76.0, 8.0, "S22-1"),
                pos(2, "CB",  FIVE_BACK_LEFT_CB_X,    78.0, 6.0, "S22-2"),
                pos(3, "CB",  CELL_CENTER_COL1_MID,   80.0, 6.0, "S23-2"),
                pos(4, "CB",  FIVE_BACK_RIGHT_CB_X,   78.0, 6.0, "S24-2"),
                pos(5, "RWB", FIVE_BACK_WIDE_RIGHT_X, 76.0, 8.0, "S24-3"),
                pos(6, "CM", CELL_CENTER_COL1_LEFT,  61.0, 7.0, "S17-1"),
                pos(7, "CM", CELL_CENTER_COL1_MID,   66.0, 7.0, "S17-2"),
                pos(8, "CM", CELL_CENTER_COL1_RIGHT, 61.0, 7.0, "S17-3"),
                // ATT line (row 1) — 2-FW SYMMETRIC
                pos(9,  "ST", CELL_CENTER_COL1_LEFT,  17.0, 7.0, "S05-1"),
                pos(10, "ST", CELL_CENTER_COL1_RIGHT, 17.0, 7.0, "S05-3")
            )
        ));

        // 4-1-4-1: 4 DEF + 1 CDM + 2 CM + LM + RM + 1 ATT = 10 outfield + 1 GK
        // Pivote central, linea de cuatro medios y un punta.
        formations.add(new FormationDefinition(
            "4-1-4-1",
            "4 defensores, 1 CDM + LM + 2 CM + RM, 1 delantero",
            4, 5, 1, 10,
            List.of(
                pos(0, "GK", 50.0, GOALKEEPER_Y, 5.0, "GK-1"),
                pos(1, "LB", CELL_CENTER_COL0_MID,  83.0, 7.0, "S22-2"),
                pos(2, "CB", CELL_CENTER_COL1_LEFT, 83.0, 6.0, "S23-1"),
                pos(3, "CB", CELL_CENTER_COL1_RIGHT,83.0, 6.0, "S23-3"),
                pos(4, "RB", CELL_CENTER_COL2_MID,  83.0, 7.0, "S24-2"),
                // CDM anchor (row 5) — single center
                pos(5, "CDM", CELL_CENTER_COL1_MID, 66.0, 7.0, "S17-2"),
                pos(6, "LM", CELL_CENTER_COL0_MID,  50.0, 8.0, "S16-2"),
                pos(7, "CM", CELL_CENTER_COL1_LEFT, 50.0, 7.0, "S17-1"),
                pos(8, "CM", CELL_CENTER_COL1_RIGHT,50.0, 7.0, "S17-3"),
                pos(9, "RM", CELL_CENTER_COL2_MID,  50.0, 8.0, "S18-2"),
                // ATT line (row 0) — single ST centered
                pos(10, "ST", 50.0, SINGLE_STRIKER_Y, 6.0, "S02-2")
            )
        ));

        // 3-4-3: 3 DEF + 4 MID + 3 ATT = 10 outfield + 1 GK
        // Tres centrales, carrileros, doble medio y tridente.
        formations.add(new FormationDefinition(
            "3-4-3",
            "3 defensores, LWB + 2 CM + RWB, 2 wingers + 1 delantero",
            3, 4, 3, 10,
            List.of(
                pos(0, "GK", 50.0, GOALKEEPER_Y, 5.0, "GK-1"),
                pos(1, "CB", CELL_CENTER_COL0_RIGHT, 76.0, 7.0, "S22-3"),
                pos(2, "CB", CELL_CENTER_COL1_MID,   78.0, 6.0, "S23-2"),
                pos(3, "CB", CELL_CENTER_COL2_LEFT,  76.0, 7.0, "S24-1"),
                // MID line (row 4-5) — LWB + 2 CM + RWB SYMMETRIC
                pos(4, "LWB", WINGBACK_LEFT_X,  56.0, 9.0, "S15-1"),
                pos(5, "CM",  CELL_CENTER_COL1_LEFT,  61.0, 7.0, "S17-1"),
                pos(6, "CM",  CELL_CENTER_COL1_RIGHT, 61.0, 7.0, "S17-3"),
                pos(7, "RWB", WINGBACK_RIGHT_X, 56.0, 9.0, "S18-3"),
                pos(8,  "LW", WINGER_LEFT_X, FRONT_THREE_WIDE_Y, 7.0, "S04-1"),
                pos(9,  "ST", 50.0, 12.0, 6.0, "S05-2"),
                pos(10, "RW", WINGER_RIGHT_X, FRONT_THREE_WIDE_Y, 7.0, "S06-3")
            )
        ));

        // 3-5-2-CDM: 3 DEF + 1 CDM + 2 CM + 2 WB + 2 ATT
        // Tres centrales, pivote, interiores, carrileros y dos puntas.
        formations.add(new FormationDefinition(
            "3-5-2-CDM",
            "3 defensores, 1 CDM + 2 CM + 2 WB, 2 delanteros",
            3, 5, 2, 10,
            List.of(
                pos(0, "GK", 50.0, GOALKEEPER_Y, 5.0, "GK-1"),
                pos(1, "CB", CELL_CENTER_COL0_RIGHT, 76.0, 7.0, "S22-3"),
                pos(2, "CB", CELL_CENTER_COL1_MID,   78.0, 6.0, "S23-2"),
                pos(3, "CB", CELL_CENTER_COL2_LEFT,  76.0, 7.0, "S24-1"),
                // CDM anchor (row 6) — deeper than plain 3-5-2 so this shape
                // behaves as a real screen instead of a cloned middle CM, but
                // far enough from the middle CB to avoid visual overlap.
                pos(4, "CDM", CELL_CENTER_COL1_MID, 68.0, 7.0, "S17-2"),
                // CM line (row 5) — a bit ahead of the pivot, but not as flat
                // as the generic 3-5-2 central trio.
                pos(5, "CM", CELL_CENTER_COL1_LEFT,  59.0, 7.0, "S17-1"),
                pos(6, "CM", CELL_CENTER_COL1_RIGHT, 59.0, 7.0, "S17-3"),
                // WB line (row 4) — slightly higher outlets; the CDM pays the
                // defensive tax while wingbacks keep progression.
                pos(7, "LWB", WINGBACK_LEFT_X,  53.0, 9.0, "S15-1"),
                pos(8, "RWB", WINGBACK_RIGHT_X, 53.0, 9.0, "S18-3"),
                pos(9,  "ST", CELL_CENTER_COL1_LEFT,  17.0, 7.0, "S05-1"),
                pos(10, "ST", CELL_CENTER_COL1_RIGHT, 17.0, 7.0, "S05-3")
            )
        ));

        // 5-4-1: 5 DEF + 4 MID + 1 ST = 10 outfield + 1 GK
        // Bloque bajo de cinco con linea media compacta.
        formations.add(new FormationDefinition(
            "5-4-1",
            "5 defensores, LM + 2 CM + RM, 1 delantero",
            5, 4, 1, 10,
            List.of(
                pos(0, "GK", 50.0, GOALKEEPER_Y, 5.0, "GK-1"),
                // DEF line (row 7) — low five-man block. Keep it below the
                // 5-3-2 so the engine reads this as protection-first, not as a
                // duplicate five-defender shape with one fewer outlet.
                pos(1, "LWB", FIVE_BACK_WIDE_LEFT_X,  80.0, 8.0, "S22-1"),
                pos(2, "CB",  FIVE_BACK_LEFT_CB_X,    83.0, 6.0, "S22-2"),
                pos(3, "CB",  CELL_CENTER_COL1_MID,   84.0, 6.0, "S23-2"),
                pos(4, "CB",  FIVE_BACK_RIGHT_CB_X,   83.0, 6.0, "S24-2"),
                pos(5, "RWB", FIVE_BACK_WIDE_RIGHT_X, 80.0, 8.0, "S24-3"),
                // MID line (row 5-6) — compact screen, with wide mids low
                // enough to close flanks and CMs deep enough to protect the box.
                pos(6, "LM", CELL_CENTER_COL0_MID,  66.0, 7.0, "S16-2"),
                pos(7, "CM", CELL_CENTER_COL1_LEFT, 70.0, 7.0, "S17-1"),
                pos(8, "CM", CELL_CENTER_COL1_RIGHT,70.0, 7.0, "S17-3"),
                pos(9, "RM", CELL_CENTER_COL2_MID,  66.0, 7.0, "S18-2"),
                // ATT line (row 1) — lone outlet a little lower than the
                // 5-3-2 pair, because this system clears into one striker.
                pos(10, "ST", 50.0, 22.0, 7.0, "S05-2")
            )
        ));

        // 3-4-1-2: 3 DEF + 4 MID + 1 CAM + 2 ST = 10 outfield + 1 GK
        // Tres centrales, carrileros, enganche y dos puntas.
        formations.add(new FormationDefinition(
            "3-4-1-2",
            "3 defensores, LWB + 2 CM + RWB, 1 CAM, 2 delanteros",
            3, 5, 2, 10,
            List.of(
                pos(0, "GK", 50.0, GOALKEEPER_Y, 5.0, "GK-1"),
                pos(1, "CB", CELL_CENTER_COL0_RIGHT, 76.0, 7.0, "S22-3"),
                pos(2, "CB", CELL_CENTER_COL1_MID,   78.0, 6.0, "S23-2"),
                pos(3, "CB", CELL_CENTER_COL2_LEFT,  76.0, 7.0, "S24-1"),
                // MID line (row 4-5) — LWB + 2 CM + RWB
                pos(4, "LWB", WINGBACK_LEFT_X,  56.0, 9.0, "S15-1"),
                pos(5, "CM",  CELL_CENTER_COL1_LEFT,  61.0, 7.0, "S17-1"),
                pos(6, "CM",  CELL_CENTER_COL1_RIGHT, 61.0, 7.0, "S17-3"),
                pos(7, "RWB", WINGBACK_RIGHT_X, 56.0, 9.0, "S18-3"),
                // CAM line (row 3) — single CAM centered
                pos(8, "CAM", CELL_CENTER_COL1_MID, 39.0, 8.0, "S11-2"),
                pos(9,  "ST", CELL_CENTER_COL1_LEFT,  17.0, 7.0, "S05-1"),
                pos(10, "ST", CELL_CENTER_COL1_RIGHT, 17.0, 7.0, "S05-3")
            )
        ));

        // 4-2-2-2: 4 DEF + 2 CDM + 2 narrow attacking mids + 2 ST = 10 outfield + 1 GK
        // Caja central: dos pivotes protegen,
        // dos mediapuntas conectan por dentro y hay menos amplitud natural.
        formations.add(new FormationDefinition(
            "4-2-2-2",
            "4 defensores, 2 CDM, 2 mediapuntas interiores, 2 delanteros",
            4, 4, 2, 10,
            List.of(
                pos(0, "GK", 50.0, GOALKEEPER_Y, 5.0, "GK-1"),
                pos(1, "LB", CELL_CENTER_COL0_MID,  83.0, 7.0, "S22-2"),
                pos(2, "CB", CELL_CENTER_COL1_LEFT, 83.0, 6.0, "S23-1"),
                pos(3, "CB", CELL_CENTER_COL1_RIGHT,83.0, 6.0, "S23-3"),
                pos(4, "RB", CELL_CENTER_COL2_MID,  83.0, 7.0, "S24-2"),
                // Double pivot: deeper than 4-4-2 CMs, so protection improves
                // but wide press/support is not free.
                pos(5, "CDM", 40.0, 68.0, 7.0, "S17-1"),
                pos(6, "CDM", 60.0, 68.0, 7.0, "S17-3"),
                // Narrow inside mids, not touchline LM/RM. This makes the shape
                // central/vertical and lets the harness differ from 4-4-2.
                pos(7, "CAM", 36.0, 43.0, 8.0, "S10-2"),
                pos(8, "CAM", 64.0, 43.0, 8.0, "S12-2"),
                pos(9,  "ST", CELL_CENTER_COL1_LEFT,  17.0, 7.0, "S05-1"),
                pos(10, "ST", CELL_CENTER_COL1_RIGHT, 17.0, 7.0, "S05-3")
            )
        ));

        // 4-1-2-3: 4 DEF + 1 CDM + 2 CM + 3 ATT = 10 outfield + 1 GK
        // Pivote, dos interiores y tridente.
        formations.add(new FormationDefinition(
            "4-1-2-3",
            "4 defensores, 1 CDM + 2 CM, 2 wingers + 1 delantero",
            4, 3, 3, 10,
            List.of(
                pos(0, "GK", 50.0, GOALKEEPER_Y, 5.0, "GK-1"),
                pos(1, "LB", CELL_CENTER_COL0_MID,  83.0, 7.0, "S22-2"),
                pos(2, "CB", CELL_CENTER_COL1_LEFT, 83.0, 6.0, "S23-1"),
                pos(3, "CB", CELL_CENTER_COL1_RIGHT,83.0, 6.0, "S23-3"),
                pos(4, "RB", CELL_CENTER_COL2_MID,  83.0, 7.0, "S24-2"),
                // MID triangle: a true pivot plus two interiors. This should
                // feel safer through the middle than flat 4-3-3, but a little
                // less immediate in attack.
                pos(5, "CDM", CELL_CENTER_COL1_MID,  70.0, 8.0, "S17-2"),
                pos(6, "CM",  39.0, 53.0, 8.0, "S17-1"),
                pos(7, "CM",  61.0, 53.0, 8.0, "S17-3"),
                pos(8,  "LW", WINGER_LEFT_X, FRONT_THREE_WIDE_Y, 7.0, "S04-1"),
                pos(9,  "ST", 50.0, 12.0, 6.0, "S05-2"),
                pos(10, "RW", WINGER_RIGHT_X, FRONT_THREE_WIDE_Y, 7.0, "S06-3")
            )
        ));

        return formations;
    }

    private static FormationPosition pos(int index, String role, double xPct, double yPct,
                                            double actionRange, String subdivisionId) {
        return new FormationPosition(
            index,
            role,
            round2(xPct),
            round2(yPct),
            round2(actionRange),
            subdivisionId
        );
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}

