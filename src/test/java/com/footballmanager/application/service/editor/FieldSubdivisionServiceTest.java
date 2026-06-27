package com.footballmanager.application.service.editor;

import com.footballmanager.adapters.in.web.career.lineup.dto.FieldSubdivisionDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MVP1-lineup-cancha-1: tests para el servicio de subdivisiones del campo.
 *
 * <p>Cubre el contrato exacto que el front espera: 82 subdivisiones,
 * GK marcado, coords válidas, IDs únicos.
 */
class FieldSubdivisionServiceTest {

    private final FieldSubdivisionService service = new FieldSubdivisionService();

    @Test
    @DisplayName("getAllSubdivisions retorna exactamente 82 subdivisiones (81 normales + 1 GK)")
    void returnsExactly82Subdivisions() {
        List<FieldSubdivisionDTO> all = service.getAllSubdivisions();
        assertEquals(FieldSubdivisionService.TOTAL_SUBDIVISIONS, all.size());
        assertEquals(82, all.size());
    }

    @Test
    @DisplayName("Hay exactamente 1 slot con isGoalkeeper=true")
    void hasExactlyOneGoalkeeperSlot() {
        long gkCount = service.getAllSubdivisions().stream()
            .filter(FieldSubdivisionDTO::isGoalkeeper)
            .count();
        assertEquals(1, gkCount);
    }

    @Test
    @DisplayName("El GK está primero en la lista (determinismo para el front)")
    void goalkeeperIsFirst() {
        FieldSubdivisionDTO first = service.getAllSubdivisions().get(0);
        assertTrue(first.isGoalkeeper());
        assertEquals("GK", first.zone());
    }

    @Test
    @DisplayName("getGoalkeeperSlot devuelve el slot con isGoalkeeper=true")
    void getGoalkeeperSlotReturnsTheGk() {
        FieldSubdivisionDTO gk = service.getGoalkeeperSlot();
        assertNotNull(gk);
        assertTrue(gk.isGoalkeeper());
        assertEquals("GK", gk.zone());
    }

    @Test
    @DisplayName("Todas las subdivisionIds son únicas")
    void allSubdivisionIdsAreUnique() {
        Set<String> ids = new HashSet<>();
        for (FieldSubdivisionDTO sub : service.getAllSubdivisions()) {
            assertTrue(ids.add(sub.subdivisionId()),
                "subdivisionId duplicado: " + sub.subdivisionId());
        }
        assertEquals(82, ids.size());
    }

    @Test
    @DisplayName("Coordenadas de cada subdivisión están en rango válido [0, 100]")
    void coordinatesAreInValidRange() {
        for (FieldSubdivisionDTO sub : service.getAllSubdivisions()) {
            assertNotNull(sub.left(), "left null para " + sub.subdivisionId());
            assertNotNull(sub.top(), "top null para " + sub.subdivisionId());
            assertNotNull(sub.width(), "width null para " + sub.subdivisionId());
            assertNotNull(sub.height(), "height null para " + sub.subdivisionId());

            assertTrue(sub.left() >= 0 && sub.left() <= 100,
                "left fuera de rango para " + sub.subdivisionId() + ": " + sub.left());
            assertTrue(sub.top() >= 0 && sub.top() <= 100,
                "top fuera de rango para " + sub.subdivisionId() + ": " + sub.top());
            assertTrue(sub.width() > 0 && sub.width() <= 100,
                "width fuera de rango para " + sub.subdivisionId() + ": " + sub.width());
            assertTrue(sub.height() > 0 && sub.height() <= 100,
                "height fuera de rango para " + sub.subdivisionId() + ": " + sub.height());
        }
    }

    @Test
    @DisplayName("Cada subdivisionId cumple el patrón S{NN}-{subIndex}")
    void subdivisionIdsFollowPattern() {
        for (FieldSubdivisionDTO sub : service.getAllSubdivisions()) {
            assertNotNull(sub.subdivisionId());
            // GK usa prefijo "GK-" (ej. "GK-1"), subdivisiones normales
            // usan prefijo "S" (ej. "S01-1", "S26-1").
            assertTrue(sub.subdivisionId().matches("(S\\d{1,2}-[1-9]|GK-[1-9])"),
                "subdivisionId con formato inválido: " + sub.subdivisionId());
        }
    }

    @Test
    @DisplayName("Zone de cada subdivisión es uno de los 4 valores válidos")
    void zoneIsValid() {
        Set<String> validZones = Set.of("ATTACK", "MIDFIELD", "DEFENSE", "GK");
        for (FieldSubdivisionDTO sub : service.getAllSubdivisions()) {
            assertTrue(validZones.contains(sub.zone()),
                "zone inválida: " + sub.zone());
        }
    }

    @Test
    @DisplayName("Sector y subIndex están dentro de rangos esperados")
    void sectorAndSubIndexAreInRange() {
        for (FieldSubdivisionDTO sub : service.getAllSubdivisions()) {
            assertNotNull(sub.sector());
            assertNotNull(sub.subIndex());
            assertTrue(sub.sector() >= 1 && sub.sector() <= 27,
                "sector fuera de rango [1,27]: " + sub.sector());
            assertTrue(sub.subIndex() >= 1 && sub.subIndex() <= 3,
                "subIndex fuera de rango [1,3]: " + sub.subIndex());
        }
    }

    // ========== V25D53-C14 (Sprint C14 — Field Map Audit) ==========
    //
    // Tests geométricos de la grilla que documentan el estado actual.
    // El gap (slots vacíos, filas sin uso) está descrito en docs/field-map.md
    // y los fixes estructurales van a C15. Estos tests sirven de golden master
    // para detectar regresiones en la geometría del field.

    @Test
    @DisplayName("V25D53-C14: los 81 slots normales no se solapan entre sí (grilla 9×9 adyacente)")
    void gridSlotsDoNotOverlap() {
        // Excluimos GK (slot grande separado que overlapea con sector 26 por diseño).
        List<FieldSubdivisionDTO> normals = service.getAllSubdivisions().stream()
            .filter(s -> !s.isGoalkeeper())
            .toList();
        assertEquals(81, normals.size());

        for (int i = 0; i < normals.size(); i++) {
            FieldSubdivisionDTO a = normals.get(i);
            double aRight = a.left() + a.width();
            double aBottom = a.top() + a.height();
            for (int j = i + 1; j < normals.size(); j++) {
                FieldSubdivisionDTO b = normals.get(j);
                double bRight = b.left() + b.width();
                double bBottom = b.top() + b.height();

                boolean xOverlap = a.left() < bRight && b.left() < aRight;
                boolean yOverlap = a.top() < bBottom && b.top() < aBottom;

                assertFalse(xOverlap && yOverlap,
                    String.format("Solape entre %s (left=%.2f, top=%.2f, w=%.2f, h=%.2f) y "
                            + "%s (left=%.2f, top=%.2f, w=%.2f, h=%.2f)",
                        a.subdivisionId(), a.left(), a.top(), a.width(), a.height(),
                        b.subdivisionId(), b.left(), b.top(), b.width(), b.height()));
            }
        }
    }

    @Test
    @DisplayName("V25D53-C14: los 81 slots normales cubren el field completo (grilla contigua sin gaps)")
    void gridSlotsCoverFieldWithoutGaps() {
        // Construimos el set de celdas (col, row) que ocupa cada slot normal.
        // Esperamos 81 celdas distintas en una grilla 9×9 (col 0-8, row 0-8).
        Set<String> cells = new HashSet<>();
        for (FieldSubdivisionDTO sub : service.getAllSubdivisions()) {
            if (sub.isGoalkeeper()) continue;
            int col = (int) Math.round(sub.left() / 11.11);
            int row = (int) Math.round(sub.top() / 11.11);
            assertTrue(col >= 0 && col <= 8,
                "col fuera de rango [0,8] para " + sub.subdivisionId() + ": " + col);
            assertTrue(row >= 0 && row <= 8,
                "row fuera de rango [0,8] para " + sub.subdivisionId() + ": " + row);
            String cell = col + "," + row;
            assertTrue(cells.add(cell),
                "Celda duplicada en " + sub.subdivisionId() + ": " + cell);
        }
        assertEquals(81, cells.size(), "La grilla no cubre las 81 celdas únicas esperadas");
    }

    @Test
    @DisplayName("V25D53-C14: cada slot normal tiene width=height=11.11 (cuadrados uniformes)")
    void gridSlotsAreUniformSquares() {
        for (FieldSubdivisionDTO sub : service.getAllSubdivisions()) {
            if (sub.isGoalkeeper()) continue;
            assertEquals(11.11, sub.width(), 0.01,
                "width inesperado para " + sub.subdivisionId() + ": " + sub.width());
            assertEquals(11.11, sub.height(), 0.01,
                "height inesperado para " + sub.subdivisionId() + ": " + sub.height());
        }
    }

    @Test
    @DisplayName("V25D53-C14: zone ATTACK corresponde a row 0-1, MIDFIELD a row 2-5, DEFENSE a row 6-8")
    void gridZonesCorrespondToRowRanges() {
        for (FieldSubdivisionDTO sub : service.getAllSubdivisions()) {
            if (sub.isGoalkeeper()) continue;
            int row = (int) Math.round(sub.top() / 11.11);
            String expectedZone;
            if (row <= 1) {
                expectedZone = "ATTACK";
            } else if (row <= 5) {
                expectedZone = "MIDFIELD";
            } else {
                expectedZone = "DEFENSE";
            }
            assertEquals(expectedZone, sub.zone(),
                "Zone no coincide con row para " + sub.subdivisionId()
                    + " (row=" + row + ")");
        }
    }
}