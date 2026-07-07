package com.footballmanager.application.service.editor;

import com.footballmanager.adapters.in.web.career.lineup.dto.FormationDTO;
import com.footballmanager.adapters.in.web.career.lineup.dto.FormationPositionDTO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Servicio que retorna las formaciones tácticas disponibles con sus posiciones.
 *
 * <p>V25D94: ALL 12 formations re-mapped to SYMMETRIC subdivisionId coords.
 * Pre-V25D94 4-DEF coords were S22-1 (5.5%), S22-2 (16.6%), S23-2 (50%),
 * S24-3 (94.4%) — left-biased, NOT symmetric. Ivan: "no son iguales en un
 * 4-4-2, hay como mas a la izquierda".
 *
 * <p>V25D94 F1-F3 fix: DEF/MID lines use cell CENTERS that distribute evenly
 * across the field:
 * <pre>
 *   4-DEF:  S22-2 (16.6%) | S23-1 (38.8%) | S23-3 (61.1%) | S24-2 (83.3%)
 *           gaps: 22.2 / 22.3 / 22.2  — SYMMETRIC around 50%
 *
 *   4-MID:  S16-2 (16.6%) | S17-1 (38.8%) | S17-3 (61.1%) | S18-2 (83.3%)
 *   3-MID:  S17-1 (38.8%) | S17-2 (50.0%) | S17-3 (61.1%)
 *   2-FW:   S05-1 (38.8%) | S05-3 (61.1%)
 *   3-CB:   S22-3 (27.7%) | S23-2 (50.0%) | S24-1 (72.2%)
 *   5-CB:   ~6%, 28%, 50%, 72%, 94% (cells S22-1, S22-2, S23-2, S23-3, S24-3)
 *   5-MID:  S15-1 (5.5%) | S16-2 (16.6%) | S17-2 (50%) | S18-2 (83.3%) | S18-3 (94.4%)
 * </pre>
 *
 * <p>Each formation lists positions with xPercent/yPercent (cell CENTERS)
 * and the subdivisionId of the cell that contains that center. Coords are
 * computed via cell-index math (sectorCol*3 + (subIndex-1)) * 11.11 + 5.55
 * to land at the cell center.
 *
 * <p>Render flow: formation coords → subdivisionId → field renderer places
 * marker at the cell center. With V25D94 symmetric coords, markers are
 * evenly distributed around the field center (xPercent=50%).
 */
@Service
public class FormationService {

    private static final double FIELD_CENTER_X = 50.0;

    // V25D94: standard symmetric coords used across all formations.
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

    private final List<FormationDTO> cachedFormations;

    public FormationService() {
        this.cachedFormations = Collections.unmodifiableList(buildFormations());
    }

    public List<FormationDTO> getAllFormations() {
        return cachedFormations;
    }

    public FormationDTO getFormationByName(String name) {
        if (name == null) {
            return null;
        }
        return cachedFormations.stream()
            .filter(f -> name.equals(f.name()))
            .findFirst()
            .orElse(null);
    }

    // ========== V25D99.16-BACK subdivision-coord lookup ==========

    /**
     * V25D99.16-BACK: returns the {@code {xPercent, yPercent}} of a
     * specific subdivision slot within a named formation. The engine
     * now factors in slot geometry (distance from natural-position
     * ideal centroid) when computing the team ratings, so callers
     * resolve coords through this method rather than hard-coding
     * xPct/yPct maps of their own.
     *
     * <p>Returns {@code null} if either the formation or the subdivision
     * is unknown. Callers fall back to the pre-V25D99.16 zone-only
     * effectiveness lookup on null (no harm done &mdash; the new
     * calculator skips geometry when given NaN coords).
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
        FormationDTO f = getFormationByName(formation);
        if (f == null) {
            return null;
        }
        for (FormationPositionDTO pos : f.positions()) {
            if (subdivisionId.equals(pos.subdivisionId())) {
                return new double[]{pos.xPercent(), pos.yPercent()};
            }
        }
        return null;
    }

    /**
     * V25D99.16-BACK: convenience that returns ALL subdivision coords
     * for a formation as a {@code subdivisionId -> {x, y}} map. Used by
     * the lineup preview endpoint to wire subdivision-aware
     * effectiveness without resolving each slot individually (saves
     * a linear scan per slot, ~11 lookups per request).
     *
     * @param formation canonical formation label; null/unknown &rarr; empty map.
     * @return immutable map; safe to pass to {@code FormationEffectiveness.from(...)}.
     */
    public Map<String, double[]> getCoordsByFormation(String formation) {
        if (formation == null || formation.isBlank()) {
            return Map.of();
        }
        FormationDTO f = getFormationByName(formation);
        if (f == null) {
            return Map.of();
        }
        Map<String, double[]> result = new java.util.HashMap<>();
        for (FormationPositionDTO pos : f.positions()) {
            result.put(pos.subdivisionId(),
                    new double[]{pos.xPercent(), pos.yPercent()});
        }
        return java.util.Collections.unmodifiableMap(result);
    }

    private List<FormationDTO> buildFormations() {
        List<FormationDTO> formations = new ArrayList<>();

        // 4-4-2: 4 DEF + 4 MID + 2 ATT = 10 outfield + 1 GK
        // V25D94: 4-DEF symmetric (S22-2, S23-1, S23-3, S24-2). 4-MID symmetric.
        formations.add(new FormationDTO(
            "4-4-2",
            "4 defensores, 4 mediocampistas, 2 delanteros",
            4, 4, 2, 10,
            List.of(
                pos(0, "GK", 50.0, 93.0, 5.0, "GK-1"),
                // DEF line (row 7) — SYMMETRIC V25D94
                pos(1, "LB", CELL_CENTER_COL0_MID,  83.0, 7.0, "S22-2"),
                pos(2, "CB", CELL_CENTER_COL1_LEFT, 83.0, 6.0, "S23-1"),
                pos(3, "CB", CELL_CENTER_COL1_RIGHT,83.0, 6.0, "S23-3"),
                pos(4, "RB", CELL_CENTER_COL2_MID,  83.0, 7.0, "S24-2"),
                // MID line (row 5) — SYMMETRIC V25D94
                pos(5, "LM", CELL_CENTER_COL0_MID,  61.0, 8.0, "S16-2"),
                pos(6, "CM", CELL_CENTER_COL1_LEFT, 61.0, 7.0, "S17-1"),
                pos(7, "CM", CELL_CENTER_COL1_RIGHT,61.0, 7.0, "S17-3"),
                pos(8, "RM", CELL_CENTER_COL2_MID,  61.0, 8.0, "S18-2"),
                // ATT line (row 1) — 2-FW SYMMETRIC V25D94
                pos(9,  "ST", CELL_CENTER_COL1_LEFT,  17.0, 7.0, "S05-1"),
                pos(10, "ST", CELL_CENTER_COL1_RIGHT, 17.0, 7.0, "S05-3")
            )
        ));

        // 4-3-3: 4 DEF + 3 MID + 3 ATT = 10 outfield + 1 GK
        // V25D94: 4-DEF symmetric + 3-MID symmetric.
        formations.add(new FormationDTO(
            "4-3-3",
            "4 defensores, 3 mediocampistas, 3 delanteros",
            4, 3, 3, 10,
            List.of(
                pos(0, "GK", 50.0, 93.0, 5.0, "GK-1"),
                // DEF line (row 7) — SYMMETRIC V25D94
                pos(1, "LB", CELL_CENTER_COL0_MID,  83.0, 7.0, "S22-2"),
                pos(2, "CB", CELL_CENTER_COL1_LEFT, 83.0, 6.0, "S23-1"),
                pos(3, "CB", CELL_CENTER_COL1_RIGHT,83.0, 6.0, "S23-3"),
                pos(4, "RB", CELL_CENTER_COL2_MID,  83.0, 7.0, "S24-2"),
                // MID line (row 4) — 3-MID SYMMETRIC V25D94
                pos(5, "CM", CELL_CENTER_COL1_LEFT, 50.0, 8.0, "S17-1"),
                pos(6, "CM", CELL_CENTER_COL1_MID,  55.0, 7.0, "S17-2"),
                pos(7, "CM", CELL_CENTER_COL1_RIGHT,50.0, 8.0, "S17-3"),
                // ATT line (row 1) — LW/ST/RW. Per V25D94 the wingers (LW/RW)
                // belong to FW family (V25D93.6 fix). Center cells S04-1, S05-2, S06-3.
                pos(8,  "LW", 11.0, 17.0, 7.0, "S04-1"),
                pos(9,  "ST", 50.0, 12.0, 6.0, "S05-2"),
                pos(10, "RW", 89.0, 17.0, 7.0, "S06-3")
            )
        ));

        // 3-5-2: 3 DEF + 5 MID + 2 ATT = 10 outfield + 1 GK
        // V25D94: 3-CB symmetric (S22-3, S23-2, S24-1). 5-MID symmetric.
        formations.add(new FormationDTO(
            "3-5-2",
            "3 defensores, 2 WB + 3 CM, 2 delanteros",
            3, 5, 2, 10,
            List.of(
                pos(0, "GK", 50.0, 93.0, 5.0, "GK-1"),
                // DEF line (row 7) — 3-CB SYMMETRIC V25D94
                pos(1, "CB", CELL_CENTER_COL0_RIGHT, 83.0, 7.0, "S22-3"),
                pos(2, "CB", CELL_CENTER_COL1_MID,   88.0, 6.0, "S23-2"),
                pos(3, "CB", CELL_CENTER_COL2_LEFT,  83.0, 7.0, "S24-1"),
                // MID line (row 4-5) — 5-MID SYMMETRIC V25D94 (LWB + 3 CM + RWB)
                pos(4, "LWB", CELL_CENTER_COL0_LEFT,  55.0, 9.0, "S15-1"),
                pos(5, "CM",  CELL_CENTER_COL1_LEFT,  61.0, 7.0, "S17-1"),
                pos(6, "CM",  CELL_CENTER_COL1_MID,   66.0, 7.0, "S17-2"),
                pos(7, "CM",  CELL_CENTER_COL1_RIGHT, 61.0, 7.0, "S17-3"),
                pos(8, "RWB", CELL_CENTER_COL2_RIGHT, 55.0, 9.0, "S18-3"),
                // ATT line (row 1) — 2-FW SYMMETRIC V25D94
                pos(9,  "ST", CELL_CENTER_COL1_LEFT,  17.0, 7.0, "S05-1"),
                pos(10, "ST", CELL_CENTER_COL1_RIGHT, 17.0, 7.0, "S05-3")
            )
        ));

        // 4-2-3-1: 4 DEF + 5 MID (2 CDM + 3 CAM) + 1 ATT = 10 outfield + 1 GK
        // V25D94: 4-DEF symmetric + 5-MID symmetric (CDM/CDM/LW/CAM/RW).
        formations.add(new FormationDTO(
            "4-2-3-1",
            "4 defensores, 2 CDM + 3 CAM, 1 delantero",
            4, 5, 1, 10,
            List.of(
                pos(0, "GK", 50.0, 93.0, 5.0, "GK-1"),
                // DEF line (row 7) — SYMMETRIC V25D94
                pos(1, "LB", CELL_CENTER_COL0_MID,  83.0, 7.0, "S22-2"),
                pos(2, "CB", CELL_CENTER_COL1_LEFT, 83.0, 6.0, "S23-1"),
                pos(3, "CB", CELL_CENTER_COL1_RIGHT,83.0, 6.0, "S23-3"),
                pos(4, "RB", CELL_CENTER_COL2_MID,  83.0, 7.0, "S24-2"),
                // CDM line (row 5) — 2 CDM SYMMETRIC
                pos(5, "CDM", CELL_CENTER_COL1_LEFT,  66.0, 7.0, "S17-1"),
                pos(6, "CDM", CELL_CENTER_COL1_RIGHT, 66.0, 7.0, "S17-3"),
                // CAM line (row 3) — 3 CAM SYMMETRIC
                pos(7, "LW",  CELL_CENTER_COL0_MID,  39.0, 8.0, "S10-2"),
                pos(8, "CAM", CELL_CENTER_COL1_MID,  39.0, 8.0, "S11-2"),
                pos(9, "RW",  CELL_CENTER_COL2_MID,  39.0, 8.0, "S12-2"),
                // ATT line (row 0) — single ST centered
                pos(10, "ST", 50.0, 6.0, 6.0, "S02-2")
            )
        ));

        // 5-3-2: 5 DEF + 3 MID + 2 ATT = 10 outfield + 1 GK
        // V25D94: 5-DEF spans full width (5.5%, 27.7%, 50%, 72.2%, 94.4%).
        // 3-MID symmetric (S17-1, S17-2, S17-3). 2-FW symmetric.
        formations.add(new FormationDTO(
            "5-3-2",
            "5 defensores, 3 mediocampistas centrales, 2 delanteros",
            5, 3, 2, 10,
            List.of(
                pos(0, "GK", 50.0, 93.0, 5.0, "GK-1"),
                // DEF line (row 7) — 5-CB SPANS FULL WIDTH V25D94
                pos(1, "LWB", CELL_CENTER_COL0_LEFT,  83.0, 8.0, "S22-1"),
                pos(2, "CB",  CELL_CENTER_COL0_MID,   83.0, 6.0, "S22-2"),
                pos(3, "CB",  CELL_CENTER_COL1_MID,   86.0, 6.0, "S23-2"),
                pos(4, "CB",  CELL_CENTER_COL2_MID,   83.0, 6.0, "S24-2"),
                pos(5, "RWB", CELL_CENTER_COL2_RIGHT, 83.0, 8.0, "S24-3"),
                // MID line (row 5) — 3-MID SYMMETRIC V25D94
                pos(6, "CM", CELL_CENTER_COL1_LEFT,  61.0, 7.0, "S17-1"),
                pos(7, "CM", CELL_CENTER_COL1_MID,   66.0, 7.0, "S17-2"),
                pos(8, "CM", CELL_CENTER_COL1_RIGHT, 61.0, 7.0, "S17-3"),
                // ATT line (row 1) — 2-FW SYMMETRIC
                pos(9,  "ST", CELL_CENTER_COL1_LEFT,  17.0, 7.0, "S05-1"),
                pos(10, "ST", CELL_CENTER_COL1_RIGHT, 17.0, 7.0, "S05-3")
            )
        ));

        // 4-1-4-1: 4 DEF + 1 CDM + 2 CM + LM + RM + 1 ATT = 10 outfield + 1 GK
        // V25D94: 4-DEF + 4-MID (LM/CM/CM/RM) symmetric. 1 CDM anchor + 1 ST.
        formations.add(new FormationDTO(
            "4-1-4-1",
            "4 defensores, 1 CDM + LM + 2 CM + RM, 1 delantero",
            4, 5, 1, 10,
            List.of(
                pos(0, "GK", 50.0, 93.0, 5.0, "GK-1"),
                // DEF line (row 7) — SYMMETRIC V25D94
                pos(1, "LB", CELL_CENTER_COL0_MID,  83.0, 7.0, "S22-2"),
                pos(2, "CB", CELL_CENTER_COL1_LEFT, 83.0, 6.0, "S23-1"),
                pos(3, "CB", CELL_CENTER_COL1_RIGHT,83.0, 6.0, "S23-3"),
                pos(4, "RB", CELL_CENTER_COL2_MID,  83.0, 7.0, "S24-2"),
                // CDM anchor (row 5) — single center
                pos(5, "CDM", CELL_CENTER_COL1_MID, 66.0, 7.0, "S17-2"),
                // MID line (row 4) — 4-MID SYMMETRIC V25D94
                pos(6, "LM", CELL_CENTER_COL0_MID,  50.0, 8.0, "S16-2"),
                pos(7, "CM", CELL_CENTER_COL1_LEFT, 50.0, 7.0, "S17-1"),
                pos(8, "CM", CELL_CENTER_COL1_RIGHT,50.0, 7.0, "S17-3"),
                pos(9, "RM", CELL_CENTER_COL2_MID,  50.0, 8.0, "S18-2"),
                // ATT line (row 0) — single ST centered
                pos(10, "ST", 50.0, 6.0, 6.0, "S02-2")
            )
        ));

        // 3-4-3: 3 DEF + 4 MID + 3 ATT = 10 outfield + 1 GK
        // V25D94: 3-CB symmetric. 4-MID (LWB+CM+CM+RWB) symmetric. 3-FW.
        formations.add(new FormationDTO(
            "3-4-3",
            "3 defensores, LWB + 2 CM + RWB, 2 wingers + 1 delantero",
            3, 4, 3, 10,
            List.of(
                pos(0, "GK", 50.0, 93.0, 5.0, "GK-1"),
                // DEF line (row 7) — 3-CB SYMMETRIC V25D94
                pos(1, "CB", CELL_CENTER_COL0_RIGHT, 83.0, 7.0, "S22-3"),
                pos(2, "CB", CELL_CENTER_COL1_MID,   88.0, 6.0, "S23-2"),
                pos(3, "CB", CELL_CENTER_COL2_LEFT,  83.0, 7.0, "S24-1"),
                // MID line (row 4-5) — LWB + 2 CM + RWB SYMMETRIC
                pos(4, "LWB", CELL_CENTER_COL0_LEFT,  55.0, 9.0, "S15-1"),
                pos(5, "CM",  CELL_CENTER_COL1_LEFT,  61.0, 7.0, "S17-1"),
                pos(6, "CM",  CELL_CENTER_COL1_RIGHT, 61.0, 7.0, "S17-3"),
                pos(7, "RWB", CELL_CENTER_COL2_RIGHT, 55.0, 9.0, "S18-3"),
                // ATT line (row 1) — LW/ST/RW (wingers = FW family per V25D93.6)
                pos(8,  "LW", 11.0, 17.0, 7.0, "S04-1"),
                pos(9,  "ST", 50.0, 12.0, 6.0, "S05-2"),
                pos(10, "RW", 89.0, 17.0, 7.0, "S06-3")
            )
        ));

        // ========== V25D94 F1-F3 fix for additional 5 formations ==========

        // 3-5-2-CDM: 3 DEF + 1 CDM + 2 CM + 2 WB + 2 ATT
        // V25D94: 3-CB symmetric + 5-MID symmetric.
        formations.add(new FormationDTO(
            "3-5-2-CDM",
            "3 defensores, 1 CDM + 2 CM + 2 WB, 2 delanteros",
            3, 5, 2, 10,
            List.of(
                pos(0, "GK", 50.0, 93.0, 5.0, "GK-1"),
                // DEF line (row 7) — 3-CB SYMMETRIC V25D94
                pos(1, "CB", CELL_CENTER_COL0_RIGHT, 83.0, 7.0, "S22-3"),
                pos(2, "CB", CELL_CENTER_COL1_MID,   88.0, 6.0, "S23-2"),
                pos(3, "CB", CELL_CENTER_COL2_LEFT,  83.0, 7.0, "S24-1"),
                // CDM anchor (row 5) — center
                pos(4, "CDM", CELL_CENTER_COL1_MID, 72.0, 8.0, "S17-2"),
                // CM line (row 5) — 2 CM SYMMETRIC
                pos(5, "CM", CELL_CENTER_COL1_LEFT,  61.0, 7.0, "S17-1"),
                pos(6, "CM", CELL_CENTER_COL1_RIGHT, 61.0, 7.0, "S17-3"),
                // WB line (row 4)
                pos(7, "LWB", CELL_CENTER_COL0_LEFT,  55.0, 9.0, "S15-1"),
                pos(8, "RWB", CELL_CENTER_COL2_RIGHT, 55.0, 9.0, "S18-3"),
                // ATT line (row 1) — 2-FW SYMMETRIC V25D94
                pos(9,  "ST", CELL_CENTER_COL1_LEFT,  17.0, 7.0, "S05-1"),
                pos(10, "ST", CELL_CENTER_COL1_RIGHT, 17.0, 7.0, "S05-3")
            )
        ));

        // 5-4-1: 5 DEF + 4 MID + 1 ST = 10 outfield + 1 GK
        // V25D94: 5-CB spans full width + 4-MID symmetric.
        formations.add(new FormationDTO(
            "5-4-1",
            "5 defensores, LM + 2 CM + RM, 1 delantero",
            5, 4, 1, 10,
            List.of(
                pos(0, "GK", 50.0, 93.0, 5.0, "GK-1"),
                // DEF line (row 7) — 5-CB SPANS FULL WIDTH V25D94
                pos(1, "LWB", CELL_CENTER_COL0_LEFT,  83.0, 8.0, "S22-1"),
                pos(2, "CB",  CELL_CENTER_COL0_MID,   83.0, 6.0, "S22-2"),
                pos(3, "CB",  CELL_CENTER_COL1_MID,   86.0, 6.0, "S23-2"),
                pos(4, "CB",  CELL_CENTER_COL2_MID,   83.0, 6.0, "S24-2"),
                pos(5, "RWB", CELL_CENTER_COL2_RIGHT, 83.0, 8.0, "S24-3"),
                // MID line (row 5) — 4-MID SYMMETRIC V25D94
                pos(6, "LM", CELL_CENTER_COL0_MID,  61.0, 7.0, "S16-2"),
                pos(7, "CM", CELL_CENTER_COL1_LEFT, 66.0, 7.0, "S17-1"),
                pos(8, "CM", CELL_CENTER_COL1_RIGHT,66.0, 7.0, "S17-3"),
                pos(9, "RM", CELL_CENTER_COL2_MID,  61.0, 7.0, "S18-2"),
                // ATT line (row 1) — single ST centered
                pos(10, "ST", 50.0, 17.0, 7.0, "S05-2")
            )
        ));

        // 3-4-1-2: 3 DEF + 4 MID + 1 CAM + 2 ST = 10 outfield + 1 GK
        // V25D94: 3-CB symmetric. 4-MID + 1 CAM + 2-FW.
        formations.add(new FormationDTO(
            "3-4-1-2",
            "3 defensores, LWB + 2 CM + RWB, 1 CAM, 2 delanteros",
            3, 5, 2, 10,
            List.of(
                pos(0, "GK", 50.0, 93.0, 5.0, "GK-1"),
                // DEF line (row 7) — 3-CB SYMMETRIC V25D94
                pos(1, "CB", CELL_CENTER_COL0_RIGHT, 83.0, 7.0, "S22-3"),
                pos(2, "CB", CELL_CENTER_COL1_MID,   88.0, 6.0, "S23-2"),
                pos(3, "CB", CELL_CENTER_COL2_LEFT,  83.0, 7.0, "S24-1"),
                // MID line (row 4-5) — LWB + 2 CM + RWB
                pos(4, "LWB", CELL_CENTER_COL0_LEFT,  55.0, 9.0, "S15-1"),
                pos(5, "CM",  CELL_CENTER_COL1_LEFT,  61.0, 7.0, "S17-1"),
                pos(6, "CM",  CELL_CENTER_COL1_RIGHT, 61.0, 7.0, "S17-3"),
                pos(7, "RWB", CELL_CENTER_COL2_RIGHT, 55.0, 9.0, "S18-3"),
                // CAM line (row 3) — single CAM centered
                pos(8, "CAM", CELL_CENTER_COL1_MID, 39.0, 8.0, "S11-2"),
                // ATT line (row 1) — 2-FW SYMMETRIC V25D94
                pos(9,  "ST", CELL_CENTER_COL1_LEFT,  17.0, 7.0, "S05-1"),
                pos(10, "ST", CELL_CENTER_COL1_RIGHT, 17.0, 7.0, "S05-3")
            )
        ));

        // 4-2-2-2: 4 DEF + 2 CDM + 2 wide mids + 2 ST = 10 outfield + 1 GK
        // V25D94: 4-DEF + 4-MID (CDM/CDM/LM/RM) symmetric + 2-FW.
        formations.add(new FormationDTO(
            "4-2-2-2",
            "4 defensores, 2 CDM, LM + RM, 2 delanteros",
            4, 4, 2, 10,
            List.of(
                pos(0, "GK", 50.0, 93.0, 5.0, "GK-1"),
                // DEF line (row 7) — SYMMETRIC V25D94
                pos(1, "LB", CELL_CENTER_COL0_MID,  83.0, 7.0, "S22-2"),
                pos(2, "CB", CELL_CENTER_COL1_LEFT, 83.0, 6.0, "S23-1"),
                pos(3, "CB", CELL_CENTER_COL1_RIGHT,83.0, 6.0, "S23-3"),
                pos(4, "RB", CELL_CENTER_COL2_MID,  83.0, 7.0, "S24-2"),
                // CDM line (row 5) — 2 CDM SYMMETRIC
                pos(5, "CDM", CELL_CENTER_COL1_LEFT,  66.0, 7.0, "S17-1"),
                pos(6, "CDM", CELL_CENTER_COL1_RIGHT, 66.0, 7.0, "S17-3"),
                // Wide mids (row 4) — LM + RM
                pos(7, "LM", CELL_CENTER_COL0_MID, 50.0, 8.0, "S16-2"),
                pos(8, "RM", CELL_CENTER_COL2_MID, 50.0, 8.0, "S18-2"),
                // ATT line (row 1) — 2-FW SYMMETRIC V25D94
                pos(9,  "ST", CELL_CENTER_COL1_LEFT,  17.0, 7.0, "S05-1"),
                pos(10, "ST", CELL_CENTER_COL1_RIGHT, 17.0, 7.0, "S05-3")
            )
        ));

        // 4-3-3-1: 4 DEF + 1 CDM + 2 CM + 3 ATT = 10 outfield + 1 GK
        // V25D94: 4-DEF + 3-MID (CDM/CM/CM) symmetric + 3-FW.
        formations.add(new FormationDTO(
            "4-3-3-1",
            "4 defensores, 1 CDM + 2 CM, 2 wingers + 1 delantero",
            4, 3, 3, 10,
            List.of(
                pos(0, "GK", 50.0, 93.0, 5.0, "GK-1"),
                // DEF line (row 7) — SYMMETRIC V25D94
                pos(1, "LB", CELL_CENTER_COL0_MID,  83.0, 7.0, "S22-2"),
                pos(2, "CB", CELL_CENTER_COL1_LEFT, 83.0, 6.0, "S23-1"),
                pos(3, "CB", CELL_CENTER_COL1_RIGHT,83.0, 6.0, "S23-3"),
                pos(4, "RB", CELL_CENTER_COL2_MID,  83.0, 7.0, "S24-2"),
                // MID line (row 5) — CDM anchor + 2 CM wide
                pos(5, "CDM", CELL_CENTER_COL1_MID,  66.0, 8.0, "S17-2"),
                pos(6, "CM",  CELL_CENTER_COL1_LEFT, 50.0, 8.0, "S17-1"),
                pos(7, "CM",  CELL_CENTER_COL1_RIGHT,50.0, 8.0, "S17-3"),
                // ATT line (row 1) — LW/ST/RW (FW family per V25D93.6)
                pos(8,  "LW", 11.0, 17.0, 7.0, "S04-1"),
                pos(9,  "ST", 50.0, 12.0, 6.0, "S05-2"),
                pos(10, "RW", 89.0, 17.0, 7.0, "S06-3")
            )
        ));

        return formations;
    }

    private static FormationPositionDTO pos(int index, String role, double xPct, double yPct,
                                            double actionRange, String subdivisionId) {
        return new FormationPositionDTO(
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
