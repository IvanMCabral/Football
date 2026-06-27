package com.footballmanager.application.service.editor;

import com.footballmanager.adapters.in.web.career.lineup.dto.FormationDTO;
import com.footballmanager.adapters.in.web.career.lineup.dto.FormationPositionDTO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Servicio que retorna las formaciones tácticas disponibles con sus posiciones.
 *
 * <p>V25D36-F2: retornaba 7 formaciones (4-4-2, 4-3-3, 4-2-3-1, 3-5-2, 5-3-2,
 * 4-1-4-1, 3-4-3). Antes solo se exponían 4 — bug que rompía el dropdown del
 * frontend cuando el engine (V25D27+) o el career start elegían una de las 3
 * formations no-listadas.
 *
 * <p>V25D54-C15 (Sprint C15 — Formations reality): ahora retorna 12
 * formaciones. P0 corrigió los role labels de los wide mids de 3-5-2 y 3-4-3
 * (LM→LWB, RM→RWB) — antes decía wingers donde en realidad juegan
 * wing-backs. P1 agregó 4 formations nuevas (3-5-2-CDM, 5-4-1, 3-4-1-2,
 * 4-2-2-2) feature-requested por Iván. P2 agregó la variante 4-3-3-1 con
 * pivote CDM (manteniendo 4-3-3 flat para compat).
 *
 * <p>Cada formación lista sus posiciones con coordenadas aproximadas
 * ({@code xPercent}, {@code yPercent}) que el modal usa para
 * (1) renderizar el indicador visual "recommended" sobre el slot
 * correspondiente del campo, y (2) mostrar el marcador numerado del
 * jugador en su posición.
 *
 * <p>El {@code subdivisionId} apunta a la subdivisión del field donde se
 * debe renderizar el slot. Coincide con los IDs emitidos por
 * {@link FieldSubdivisionService}.
 */
@Service
public class FormationService {

    private static final double FIELD_CENTER_X = 50.0;

    private final List<FormationDTO> cachedFormations;

    public FormationService() {
        this.cachedFormations = Collections.unmodifiableList(buildFormations());
    }

    /**
     * Devuelve las 12 formaciones hardcoded con sus posiciones
     * (7 originales + 4 nuevas V25D54-C15 + 1 variante 4-3-3-1).
     */
    public List<FormationDTO> getAllFormations() {
        return cachedFormations;
    }

    /**
     * Devuelve una formación por nombre ({@code "4-4-2"}, etc.) o null si no existe.
     */
    public FormationDTO getFormationByName(String name) {
        if (name == null) {
            return null;
        }
        return cachedFormations.stream()
            .filter(f -> name.equals(f.name()))
            .findFirst()
            .orElse(null);
    }

    private List<FormationDTO> buildFormations() {
        List<FormationDTO> formations = new ArrayList<>();

        // 4-4-2: 4 DEF + 4 MID + 2 ATT = 10 outfield + 1 GK
        formations.add(new FormationDTO(
            "4-4-2",
            "4 defensores, 4 mediocampistas, 2 delanteros",
            4, 4, 2, 10,
            List.of(
                // GK
                pos(0, "GK", 50.0, 93.0, 5.0, "GK-1"),
                // DEF line (row 7)
                pos(1, "LB", 11.0, 83.0, 7.0, "S22-1"),
                pos(2, "CB", 33.0, 83.0, 6.0, "S22-2"),
                pos(3, "CB", 67.0, 83.0, 6.0, "S23-2"),
                pos(4, "RB", 89.0, 83.0, 7.0, "S24-3"),
                // MID line (row 5)
                pos(5, "LM", 11.0, 61.0, 8.0, "S16-1"),
                pos(6, "CM", 39.0, 61.0, 7.0, "S16-2"),
                pos(7, "CM", 61.0, 61.0, 7.0, "S17-2"),
                pos(8, "RM", 89.0, 61.0, 8.0, "S18-3"),
                // ATT line (row 1)
                pos(9, "ST", 39.0, 17.0, 7.0, "S05-2"),
                pos(10, "ST", 61.0, 17.0, 7.0, "S05-3")
            )
        ));

        // 4-3-3: 4 DEF + 3 MID + 3 ATT = 10 outfield + 1 GK
        formations.add(new FormationDTO(
            "4-3-3",
            "4 defensores, 3 mediocampistas, 3 delanteros",
            4, 3, 3, 10,
            List.of(
                // GK
                pos(0, "GK", 50.0, 93.0, 5.0, "GK-1"),
                // DEF line (row 7)
                pos(1, "LB", 11.0, 83.0, 7.0, "S22-1"),
                pos(2, "CB", 33.0, 83.0, 6.0, "S22-2"),
                pos(3, "CB", 67.0, 83.0, 6.0, "S23-2"),
                pos(4, "RB", 89.0, 83.0, 7.0, "S24-3"),
                // MID line (row 4 — center)
                pos(5, "CM", 30.0, 50.0, 8.0, "S13-2"),
                pos(6, "CM", 50.0, 55.0, 7.0, "S14-2"),
                pos(7, "CM", 70.0, 50.0, 8.0, "S15-2"),
                // ATT line (row 1)
                pos(8, "LW", 11.0, 17.0, 7.0, "S04-1"),
                pos(9, "ST", 50.0, 12.0, 6.0, "S05-2"),
                pos(10, "RW", 89.0, 17.0, 7.0, "S06-3")
            )
        ));

        // 3-5-2: 3 DEF + 5 MID + 2 ATT = 10 outfield + 1 GK
        // V25D54-C15 P0: pos #4 LM→LWB y pos #8 RM→RWB (en 3-5-2 los wide
        // mids son wing-backs, no wingers). Las coordenadas no cambian.
        formations.add(new FormationDTO(
            "3-5-2",
            "3 defensores, 2 WB + 3 CM, 2 delanteros",
            3, 5, 2, 10,
            List.of(
                // GK
                pos(0, "GK", 50.0, 93.0, 5.0, "GK-1"),
                // DEF line (row 7 — 3 CB)
                pos(1, "CB", 22.0, 83.0, 7.0, "S22-1"),
                pos(2, "CB", 50.0, 88.0, 6.0, "S23-2"),
                pos(3, "CB", 78.0, 83.0, 7.0, "S24-3"),
                // MID line (row 4-5 — LWB + 3 CM + RWB)
                pos(4, "LWB", 6.0, 55.0, 9.0, "S15-1"),
                pos(5, "CM", 30.0, 61.0, 7.0, "S16-2"),
                pos(6, "CM", 50.0, 66.0, 7.0, "S17-2"),
                pos(7, "CM", 70.0, 61.0, 7.0, "S18-2"),
                pos(8, "RWB", 94.0, 55.0, 9.0, "S18-3"),
                // ATT line (row 1)
                pos(9, "ST", 39.0, 17.0, 7.0, "S05-2"),
                pos(10, "ST", 61.0, 17.0, 7.0, "S05-3")
            )
        ));

        // 4-2-3-1: 4 DEF + 5 MID (2 CDM + 3 CAM) + 1 ATT = 10 outfield + 1 GK
        formations.add(new FormationDTO(
            "4-2-3-1",
            "4 defensores, 2 CDM + 3 CAM, 1 delantero",
            4, 5, 1, 10,
            List.of(
                // GK
                pos(0, "GK", 50.0, 93.0, 5.0, "GK-1"),
                // DEF line (row 7)
                pos(1, "LB", 11.0, 83.0, 7.0, "S22-1"),
                pos(2, "CB", 33.0, 83.0, 6.0, "S22-2"),
                pos(3, "CB", 67.0, 83.0, 6.0, "S23-2"),
                pos(4, "RB", 89.0, 83.0, 7.0, "S24-3"),
                // CDM line (row 5 — 2 CDM)
                pos(5, "CDM", 35.0, 66.0, 7.0, "S16-2"),
                pos(6, "CDM", 65.0, 66.0, 7.0, "S17-2"),
                // CAM line (row 3 — 3 CAM)
                pos(7, "LW", 11.0, 39.0, 8.0, "S10-1"),
                pos(8, "CAM", 50.0, 39.0, 8.0, "S11-2"),
                pos(9, "RW", 89.0, 39.0, 8.0, "S12-3"),
                // ATT line (row 0)
                pos(10, "ST", 50.0, 6.0, 6.0, "S02-2")
            )
        ));

        // V25D36-F2: 5-3-2 (5 DEF + 3 MID + 2 ATT). Back-five defensiva. Sin
        // wingers separados (los 3 mids son centrales según V24FormationParser).
        formations.add(new FormationDTO(
            "5-3-2",
            "5 defensores, 3 mediocampistas centrales, 2 delanteros",
            5, 3, 2, 10,
            List.of(
                // GK
                pos(0, "GK", 50.0, 93.0, 5.0, "GK-1"),
                // DEF line (row 7 — 5 defenders: LB, CB, CB, CB, RB)
                pos(1, "LB", 6.0, 83.0, 8.0, "S22-1"),
                pos(2, "CB", 28.0, 83.0, 6.0, "S22-2"),
                pos(3, "CB", 50.0, 86.0, 6.0, "S23-2"),
                pos(4, "CB", 72.0, 83.0, 6.0, "S23-3"),
                pos(5, "RB", 94.0, 83.0, 8.0, "S24-3"),
                // MID line (row 5 — 3 CMs)
                pos(6, "CM", 25.0, 61.0, 7.0, "S16-1"),
                pos(7, "CM", 50.0, 66.0, 7.0, "S17-2"),
                pos(8, "CM", 75.0, 61.0, 7.0, "S18-2"),
                // ATT line (row 1)
                pos(9, "ST", 35.0, 17.0, 7.0, "S05-2"),
                pos(10, "ST", 65.0, 17.0, 7.0, "S05-3")
            )
        ));

        // V25D36-F2: 4-1-4-1 (4 DEF + 5 MID [1 CDM + 4 wide/center] + 1 ATT).
        // 1 anchor CDM + LM/CM/CM/RM en la línea de mediocampistas.
        formations.add(new FormationDTO(
            "4-1-4-1",
            "4 defensores, 1 CDM + 2 CM + LM + RM, 1 delantero",
            4, 5, 1, 10,
            List.of(
                // GK
                pos(0, "GK", 50.0, 93.0, 5.0, "GK-1"),
                // DEF line (row 7)
                pos(1, "LB", 11.0, 83.0, 7.0, "S22-1"),
                pos(2, "CB", 33.0, 83.0, 6.0, "S22-2"),
                pos(3, "CB", 67.0, 83.0, 6.0, "S23-2"),
                pos(4, "RB", 89.0, 83.0, 7.0, "S24-3"),
                // CDM anchor (row 5)
                pos(5, "CDM", 50.0, 66.0, 7.0, "S17-2"),
                // MID line (row 4 — LM, CM, CM, RM)
                pos(6, "LM", 11.0, 50.0, 8.0, "S13-1"),
                pos(7, "CM", 39.0, 50.0, 7.0, "S14-2"),
                pos(8, "CM", 61.0, 50.0, 7.0, "S14-3"),
                pos(9, "RM", 89.0, 50.0, 8.0, "S15-3"),
                // ATT line (row 1)
                pos(10, "ST", 50.0, 12.0, 6.0, "S05-2")
            )
        ));

        // V25D36-F2: 3-4-3 (3 DEF + 4 MID + 3 ATT). Back-three ofensiva con
        // 2 wingers en la línea de mediocampistas y LW/ST/RW arriba.
        // V25D54-C15 P0: pos #4 LM→LWB y pos #7 RM→RWB (3-4-3 usa WB, no wingers).
        formations.add(new FormationDTO(
            "3-4-3",
            "3 defensores, LWB + 2 CM + RWB, 2 wingers + 1 delantero",
            3, 4, 3, 10,
            List.of(
                // GK
                pos(0, "GK", 50.0, 93.0, 5.0, "GK-1"),
                // DEF line (row 7 — 3 CB)
                pos(1, "CB", 22.0, 83.0, 7.0, "S22-1"),
                pos(2, "CB", 50.0, 88.0, 6.0, "S23-2"),
                pos(3, "CB", 78.0, 83.0, 7.0, "S24-3"),
                // MID line (row 4-5 — LWB, CM, CM, RWB)
                pos(4, "LWB", 6.0, 55.0, 9.0, "S15-1"),
                pos(5, "CM", 36.0, 61.0, 7.0, "S16-2"),
                pos(6, "CM", 64.0, 61.0, 7.0, "S17-2"),
                pos(7, "RWB", 94.0, 55.0, 9.0, "S18-3"),
                // ATT line (row 1 — LW, ST, RW)
                pos(8, "LW", 11.0, 17.0, 7.0, "S04-1"),
                pos(9, "ST", 50.0, 12.0, 6.0, "S05-2"),
                pos(10, "RW", 89.0, 17.0, 7.0, "S06-3")
            )
        ));

        // ========== V25D54-C15 P1: 4 formations nuevas ==========

        // 3-5-2-CDM: 3 DEF + 1 CDM + 2 CM + 2 WB + 2 ATT = 10 outfield + 1 GK.
        // Variante explícita del 3-5-2 con pivote CDM claro y 2 WB (no LM/RM).
        formations.add(new FormationDTO(
            "3-5-2-CDM",
            "3 defensores, 1 CDM + 2 CM + 2 WB, 2 delanteros",
            3, 5, 2, 10,
            List.of(
                // GK
                pos(0, "GK", 50.0, 93.0, 5.0, "GK-1"),
                // DEF line (row 7 — 3 CB)
                pos(1, "CB", 22.0, 83.0, 7.0, "S22-1"),
                pos(2, "CB", 50.0, 88.0, 6.0, "S23-2"),
                pos(3, "CB", 78.0, 83.0, 7.0, "S24-3"),
                // CDM anchor (row 5 — entre DEF y MID)
                pos(4, "CDM", 50.0, 72.0, 8.0, "S17-2"),
                // CM line (row 5 — wide CMs)
                pos(5, "CM", 30.0, 61.0, 7.0, "S16-2"),
                pos(6, "CM", 70.0, 61.0, 7.0, "S18-2"),
                // WB line (row 4 — carriles)
                pos(7, "LWB", 6.0, 55.0, 9.0, "S15-1"),
                pos(8, "RWB", 94.0, 55.0, 9.0, "S18-3"),
                // ATT line (row 1)
                pos(9, "ST", 39.0, 17.0, 7.0, "S05-2"),
                pos(10, "ST", 61.0, 17.0, 7.0, "S05-3")
            )
        ));

        // 5-4-1: 5 DEF + 4 MID + 1 ST = 10 outfield + 1 GK.
        // Ultra-defensiva, common en copas. Back-five con 2 LM/RM wide mids.
        formations.add(new FormationDTO(
            "5-4-1",
            "5 defensores, LM + 2 CM + RM, 1 delantero",
            5, 4, 1, 10,
            List.of(
                // GK
                pos(0, "GK", 50.0, 93.0, 5.0, "GK-1"),
                // DEF line (row 7 — 5 defenders)
                pos(1, "LB", 6.0, 83.0, 8.0, "S22-1"),
                pos(2, "CB", 28.0, 83.0, 6.0, "S22-2"),
                pos(3, "CB", 50.0, 86.0, 6.0, "S23-2"),
                pos(4, "CB", 72.0, 83.0, 6.0, "S23-3"),
                pos(5, "RB", 94.0, 83.0, 8.0, "S24-3"),
                // MID line (row 5 — LM, CM, CM, RM)
                pos(6, "LM", 15.0, 61.0, 7.0, "S16-1"),
                pos(7, "CM", 38.0, 66.0, 7.0, "S16-2"),
                pos(8, "CM", 62.0, 66.0, 7.0, "S17-2"),
                pos(9, "RM", 85.0, 61.0, 7.0, "S18-3"),
                // ATT line (row 1 — único ST)
                pos(10, "ST", 50.0, 17.0, 7.0, "S05-2")
            )
        ));

        // 3-4-1-2: 3 DEF + 4 MID + 1 CAM + 2 ST = 10 outfield + 1 GK.
        // "Christmas tree" — 2 WB + 2 CM en MID, 1 CAM (trequartista), 2 ST.
        formations.add(new FormationDTO(
            "3-4-1-2",
            "3 defensores, LWB + 2 CM + RWB, 1 CAM, 2 delanteros",
            3, 5, 2, 10,
            List.of(
                // GK
                pos(0, "GK", 50.0, 93.0, 5.0, "GK-1"),
                // DEF line (row 7 — 3 CB)
                pos(1, "CB", 22.0, 83.0, 7.0, "S22-1"),
                pos(2, "CB", 50.0, 88.0, 6.0, "S23-2"),
                pos(3, "CB", 78.0, 83.0, 7.0, "S24-3"),
                // MID line (row 4-5 — LWB, CM, CM, RWB)
                pos(4, "LWB", 6.0, 55.0, 9.0, "S15-1"),
                pos(5, "CM", 30.0, 61.0, 7.0, "S16-2"),
                pos(6, "CM", 70.0, 61.0, 7.0, "S18-2"),
                pos(7, "RWB", 94.0, 55.0, 9.0, "S18-3"),
                // CAM line (row 3 — trequartista)
                pos(8, "CAM", 50.0, 39.0, 8.0, "S11-2"),
                // ATT line (row 1 — 2 ST)
                pos(9, "ST", 39.0, 17.0, 7.0, "S05-2"),
                pos(10, "ST", 61.0, 17.0, 7.0, "S05-3")
            )
        ));

        // 4-2-2-2: 4 DEF + 2 CDM + 2 wide mids + 2 ST = 10 outfield + 1 GK.
        // Alternativa narrow diamond al 4-4-2.
        formations.add(new FormationDTO(
            "4-2-2-2",
            "4 defensores, 2 CDM, LM + RM, 2 delanteros",
            4, 4, 2, 10,
            List.of(
                // GK
                pos(0, "GK", 50.0, 93.0, 5.0, "GK-1"),
                // DEF line (row 7 — 4-back estándar)
                pos(1, "LB", 11.0, 83.0, 7.0, "S22-1"),
                pos(2, "CB", 33.0, 83.0, 6.0, "S22-2"),
                pos(3, "CB", 67.0, 83.0, 6.0, "S23-2"),
                pos(4, "RB", 89.0, 83.0, 7.0, "S24-3"),
                // CDM line (row 5 — 2 anchors)
                pos(5, "CDM", 35.0, 66.0, 7.0, "S16-2"),
                pos(6, "CDM", 65.0, 66.0, 7.0, "S17-2"),
                // Wide mids (row 4 — LM y RM altos)
                pos(7, "LM", 15.0, 50.0, 8.0, "S13-1"),
                pos(8, "RM", 85.0, 50.0, 8.0, "S15-3"),
                // ATT line (row 1 — 2 ST)
                pos(9, "ST", 39.0, 17.0, 7.0, "S05-2"),
                pos(10, "ST", 61.0, 17.0, 7.0, "S05-3")
            )
        ));

        // ========== V25D54-C15 P2: variante 4-3-3 con pivote ==========

        // 4-3-3-1: 4 DEF + 1 CDM + 2 CM + 3 ATT = 10 outfield + 1 GK.
        // Variante de 4-3-3 con pivote CDM claro (en lugar de 3 CM flat).
        // Mantiene wingers (LW/RW) y ST como el 4-3-3 original — compat total.
        formations.add(new FormationDTO(
            "4-3-3-1",
            "4 defensores, 1 CDM + 2 CM, 2 wingers + 1 delantero",
            4, 3, 3, 10,
            List.of(
                // GK
                pos(0, "GK", 50.0, 93.0, 5.0, "GK-1"),
                // DEF line (row 7 — 4-back estándar)
                pos(1, "LB", 11.0, 83.0, 7.0, "S22-1"),
                pos(2, "CB", 33.0, 83.0, 6.0, "S22-2"),
                pos(3, "CB", 67.0, 83.0, 6.0, "S23-2"),
                pos(4, "RB", 89.0, 83.0, 7.0, "S24-3"),
                // CDM anchor (row 5)
                pos(5, "CDM", 50.0, 66.0, 8.0, "S17-2"),
                // CM line (row 4 — wide CMs)
                pos(6, "CM", 30.0, 50.0, 8.0, "S13-2"),
                pos(7, "CM", 70.0, 50.0, 8.0, "S15-2"),
                // ATT line (row 1 — LW, ST, RW como 4-3-3)
                pos(8, "LW", 11.0, 17.0, 7.0, "S04-1"),
                pos(9, "ST", 50.0, 12.0, 6.0, "S05-2"),
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