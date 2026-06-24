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
 * <p>Las 4 formaciones (4-4-2, 4-3-3, 3-5-2, 4-2-3-1) coinciden con las que
 * ya muestra el {@code squad-management.component} en su selector y con las
 * que el motor del partido entiende vía {@link com.footballmanager.domain.model.valueobject.Formation}.
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
     * Devuelve las 4 formaciones hardcoded con sus posiciones.
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
        formations.add(new FormationDTO(
            "3-5-2",
            "3 defensores, 5 mediocampistas, 2 delanteros",
            3, 5, 2, 10,
            List.of(
                // GK
                pos(0, "GK", 50.0, 93.0, 5.0, "GK-1"),
                // DEF line (row 7 — 3 CB)
                pos(1, "CB", 22.0, 83.0, 7.0, "S22-1"),
                pos(2, "CB", 50.0, 88.0, 6.0, "S23-2"),
                pos(3, "CB", 78.0, 83.0, 7.0, "S24-3"),
                // MID line (row 5 — 5 mids)
                pos(4, "LM", 6.0, 55.0, 9.0, "S15-1"),
                pos(5, "CM", 30.0, 61.0, 7.0, "S16-2"),
                pos(6, "CM", 50.0, 66.0, 7.0, "S17-2"),
                pos(7, "CM", 70.0, 61.0, 7.0, "S18-2"),
                pos(8, "RM", 94.0, 55.0, 9.0, "S18-3"),
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