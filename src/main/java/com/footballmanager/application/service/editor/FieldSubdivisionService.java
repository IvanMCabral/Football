package com.footballmanager.application.service.editor;

import com.footballmanager.adapters.in.web.career.lineup.dto.FieldSubdivisionDTO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Servicio que retorna las 82 subdivisiones del campo de fútbol.
 *
 * <p>Replica exactamente la shape que el componente front
 * {@code SquadEditorModalComponent} espera: 81 subdivisiones normales
 * (27 sectores × 3 sub-espacios) + 1 slot de GK = 82 totales.
 *
 * <p>El campo está orientado vertical: top% bajo = zona de ATAQUE,
 * top% alto = zona DEFENSA. El GK se renderiza como un slot grande en
 * la parte inferior central del campo.
 *
 * <p>Las coordenadas son porcentajes relativos (0-100) que el front aplica
 * vía {@code [style.left.%]} / {@code [style.top.%]} en cada slot.
 *
 * <p>Estas coordenadas son hardcoded por decisión de diseño (sprint
 * MVP1-lineup-cancha-1): el back no tiene un modelo de campo más rico
 * todavía. Cualquier ajuste futuro a las posiciones debe replicarse acá
 * y en el modal del front.
 */
@Service
public class FieldSubdivisionService {

    /** Total de subdivisiones normales: 27 sectores × 3 sub-espacios. */
    public static final int TOTAL_NORMAL_SUBDIVISIONS = 81;

    /** Total incluyendo el GK: 81 + 1. */
    public static final int TOTAL_SUBDIVISIONS = 82;

    /** Sectores por fila: 3 columnas (cada sector tiene 3 sub-espacios = 9 sub-espacios/fila). */
    private static final int SECTORS_PER_ROW = 3;

    /** Filas de sectores. 9 filas × 3 cols × 3 subs = 81. */
    private static final int TOTAL_ROWS = 9;

    /** Ancho de cada sub-espacio (9 columnas de sub-espacios por fila). */
    private static final double SUB_WIDTH = 11.11;

    /** Alto de cada sub-espacio (9 filas de sub-espacios). */
    private static final double SUB_HEIGHT = 11.11;

    /** Sector donde está el GK (fila 8 = la última, columna del medio). */
    private static final int GK_SECTOR = 26;

    private static final double GK_LEFT = 35.0;
    private static final double GK_TOP = 88.0;
    private static final double GK_WIDTH = 30.0;
    private static final double GK_HEIGHT = 10.0;

    private final List<FieldSubdivisionDTO> cachedSubdivisions;

    public FieldSubdivisionService() {
        this.cachedSubdivisions = Collections.unmodifiableList(buildSubdivisions());
    }

    /**
     * Devuelve las 82 subdivisiones del campo (81 normales + 1 GK).
     * El orden es estable: primero el GK, después las 81 normales en orden
     * de sector/subIndex ascendente.
     */
    public List<FieldSubdivisionDTO> getAllSubdivisions() {
        return cachedSubdivisions;
    }

    /**
     * Devuelve solo el slot de GK (con {@code isGoalkeeper=true}).
     */
    public FieldSubdivisionDTO getGoalkeeperSlot() {
        return cachedSubdivisions.get(0);
    }

    private List<FieldSubdivisionDTO> buildSubdivisions() {
        List<FieldSubdivisionDTO> result = new ArrayList<>(TOTAL_SUBDIVISIONS);

        // GK primero (siempre primero en la lista, determinismo para el front).
        // subdivisionId "GK-1" evita colisión con la subdivisión normal S26-1.
        result.add(new FieldSubdivisionDTO(
            GK_SECTOR,
            1,
            true,
            GK_LEFT,
            GK_TOP,
            GK_WIDTH,
            GK_HEIGHT,
            "GK-1",
            "GK"
        ));

        // 81 subdivisiones normales: 27 sectores × 3 sub-espacios.
        // Disposición: 9 filas × 3 columnas de sectores; cada sector tiene
        // 3 sub-espacios (subIndex 1 = izquierda, 2 = medio, 3 = derecha).
        // Numeración de sector: fila por fila (1-3, 4-6, 7-9, ...).
        for (int sector = 1; sector <= 27; sector++) {
            int sectorRow = (sector - 1) / SECTORS_PER_ROW; // 0..8
            int sectorCol = (sector - 1) % SECTORS_PER_ROW; // 0..2

            // Saltamos el sector 26 (GK) — sus 3 sub-espacios están
            // reemplazados visualmente por el slot de GK grande.
            // Igual los emitimos para mantener el conteo de 81 normales
            // y dar al front cobertura completa del field.
            //
            // Las coordenadas de los 3 subs de sector 26 se calculan igual
            // que el resto, pero el front los va a esconder visualmente
            // (slot-gk tiene CSS diferente y sector 26 queda en la zona
            // inferior).
            String zone = zoneForRow(sectorRow);

            for (int subIndex = 1; subIndex <= 3; subIndex++) {
                double left = (sectorCol * 3 + (subIndex - 1)) * SUB_WIDTH;
                double top = sectorRow * SUB_HEIGHT;

                String subdivisionId = String.format("S%02d-%d", sector, subIndex);

                result.add(new FieldSubdivisionDTO(
                    sector,
                    subIndex,
                    false,
                    round2(left),
                    round2(top),
                    round2(SUB_WIDTH),
                    round2(SUB_HEIGHT),
                    subdivisionId,
                    zone
                ));
            }
        }

        return result;
    }

    private String zoneForRow(int row) {
        // Top del field (row 0-1) = ATAQUE; medio (2-5) = MEDIO; abajo (6-8) = DEFENSA.
        if (row <= 1) {
            return "ATTACK";
        }
        if (row <= 5) {
            return "MIDFIELD";
        }
        return "DEFENSE";
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}