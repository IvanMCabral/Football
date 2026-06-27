package com.footballmanager.application.service.editor;

import com.footballmanager.adapters.in.web.career.lineup.dto.FormationDTO;
import com.footballmanager.adapters.in.web.career.lineup.dto.FormationPositionDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MVP1-lineup-cancha-1: tests para el servicio de formaciones.
 *
 * <p>V25D36-F2: ahora 7 formaciones (antes 4) — agregadas 5-3-2, 4-1-4-1 y
 * 3-4-3 que el engine ya entendía pero que el servicio no exponía.
 *
 * <p>Cubre el contrato: 7 formaciones con 11 posiciones cada una
 * (1 GK + outfieldPlayers), subdivisionIds únicos dentro de cada formación,
 * counts de defensores/mediocampistas/atacantes coincidentes con la formación.
 */
class FormationServiceTest {

    private final FormationService service = new FormationService();

    @Test
    @DisplayName("getAllFormations retorna exactamente 7 formaciones")
    void returnsExactly7Formations() {
        assertEquals(7, service.getAllFormations().size());
    }

    @Test
    @DisplayName("Las 7 formaciones esperadas están presentes")
    void allExpectedFormationsPresent() {
        Set<String> names = Set.of(
            "4-4-2", "4-3-3", "3-5-2", "4-2-3-1",
            "5-3-2", "4-1-4-1", "3-4-3");
        Set<String> actual = new HashSet<>();
        for (FormationDTO f : service.getAllFormations()) {
            actual.add(f.name());
        }
        assertEquals(names, actual);
    }

    @Test
    @DisplayName("Cada formación tiene exactamente 11 posiciones (1 GK + 10 outfield)")
    void eachFormationHas11Positions() {
        for (FormationDTO f : service.getAllFormations()) {
            assertEquals(11, f.positions().size(),
                "Formación " + f.name() + " no tiene 11 posiciones");
        }
    }

    @Test
    @DisplayName("Cada formación tiene exactamente 1 GK")
    void eachFormationHasExactly1Gk() {
        for (FormationDTO f : service.getAllFormations()) {
            long gkCount = f.positions().stream()
                .filter(p -> "GK".equals(p.role()))
                .count();
            assertEquals(1, gkCount,
                "Formación " + f.name() + " no tiene exactamente 1 GK");
        }
    }

    @Test
    @DisplayName("subdivisionIds de cada formación son únicos")
    void subdivisionIdsUniqueWithinFormation() {
        for (FormationDTO f : service.getAllFormations()) {
            Set<String> ids = new HashSet<>();
            for (FormationPositionDTO p : f.positions()) {
                assertTrue(ids.add(p.subdivisionId()),
                    "subdivisionId duplicado en " + f.name() + ": " + p.subdivisionId());
            }
        }
    }

    @Test
    @DisplayName("Los subdivisionIds de las posiciones referencian subdivisions existentes")
    void subdivisionIdsPointToExistingSubdivisions() {
        FieldSubdivisionService subdivisionService = new FieldSubdivisionService();
        Set<String> validIds = new HashSet<>();
        subdivisionService.getAllSubdivisions().forEach(s -> validIds.add(s.subdivisionId()));

        for (FormationDTO f : service.getAllFormations()) {
            for (FormationPositionDTO p : f.positions()) {
                assertTrue(validIds.contains(p.subdivisionId()),
                    "Formación " + f.name() + " referencia subdivisionId inexistente: "
                        + p.subdivisionId());
            }
        }
    }

    @Test
    @DisplayName("Los counts de defenders/midfielders/attackers suman outfieldPlayers")
    void formationMetaSumsToOutfield() {
        for (FormationDTO f : service.getAllFormations()) {
            int sum = f.defenders() + f.midfielders() + f.attackers();
            assertEquals(f.outfieldPlayers().intValue(), sum,
                "Formación " + f.name() + " - defenders+midfielders+attackers != outfieldPlayers");
        }
    }

    @Test
    @DisplayName("Cada formación suma 10 outfield + 1 GK = 11 jugadores")
    void eachFormationSumsTo11Players() {
        for (FormationDTO f : service.getAllFormations()) {
            assertEquals(10, f.outfieldPlayers().intValue(),
                "outfieldPlayers de " + f.name() + " debería ser 10");
            // 11 posiciones totales (10 outfield + 1 GK)
            assertEquals(11, f.positions().size(),
                "Formación " + f.name() + " debe tener 11 posiciones");
        }
    }

    @Test
    @DisplayName("Coordenadas xPercent/yPercent están en rango válido [0, 100]")
    void coordinatesInValidRange() {
        for (FormationDTO f : service.getAllFormations()) {
            for (FormationPositionDTO p : f.positions()) {
                assertNotNull(p.xPercent());
                assertNotNull(p.yPercent());
                assertTrue(p.xPercent() >= 0 && p.xPercent() <= 100,
                    f.name() + " - xPercent fuera de rango: " + p.xPercent());
                assertTrue(p.yPercent() >= 0 && p.yPercent() <= 100,
                    f.name() + " - yPercent fuera de rango: " + p.yPercent());
            }
        }
    }

    @Test
    @DisplayName("getFormationByName devuelve la formación correcta")
    void getFormationByNameReturnsCorrect() {
        assertNotNull(service.getFormationByName("4-4-2"));
        assertNotNull(service.getFormationByName("4-3-3"));
        assertNotNull(service.getFormationByName("3-5-2"));
        assertNotNull(service.getFormationByName("4-2-3-1"));
        // V25D36-F2: las 3 formations agregadas también son recuperables.
        assertNotNull(service.getFormationByName("5-3-2"));
        assertNotNull(service.getFormationByName("4-1-4-1"));
        assertNotNull(service.getFormationByName("3-4-3"));
    }

    @Test
    @DisplayName("getFormationByName devuelve null para nombres desconocidos")
    void getFormationByNameReturnsNullForUnknown() {
        assertNull(service.getFormationByName("5-5-5"));
        assertNull(service.getFormationByName(null));
        assertNull(service.getFormationByName(""));
    }

    // ========== V25D53-C14 (Sprint C14 — Field Map Audit) ==========
    //
    // Tests de cobertura que documentan el estado actual del field map.
    // El gap (LM/RM en vez de LWB/RWB para 3-5-2/3-4-3, formations faltantes,
    // profundidad plana) está descrito en docs/field-map.md y los fixes van
    // a C15. Estos tests sirven de golden master para detectar regresiones.

    @Test
    @DisplayName("V25D53-C14: posiciones de cada formación no se solapan (actionRange = extent)")
    void positionsDoNotOverlapWithinFormation() {
        // Para cada formación, validar que ningún par de jugadores (excepto GK que es grande)
        // comparte el rectángulo visible derivado de (xPercent, yPercent) ± actionRangePercent/2.
        // El GK ocupa un slot grande separado (subdivisionId GK-1), lo excluimos del check.
        for (FormationDTO f : service.getAllFormations()) {
            List<FormationPositionDTO> positions = f.positions();
            for (int i = 0; i < positions.size(); i++) {
                FormationPositionDTO a = positions.get(i);
                if ("GK-1".equals(a.subdivisionId())) continue; // GK: slot grande separado
                for (int j = i + 1; j < positions.size(); j++) {
                    FormationPositionDTO b = positions.get(j);
                    if ("GK-1".equals(b.subdivisionId())) continue;

                    double axMin = a.xPercent() - a.actionRangePercent() / 2.0;
                    double axMax = a.xPercent() + a.actionRangePercent() / 2.0;
                    double ayMin = a.yPercent() - a.actionRangePercent() / 2.0;
                    double ayMax = a.yPercent() + a.actionRangePercent() / 2.0;

                    double bxMin = b.xPercent() - b.actionRangePercent() / 2.0;
                    double bxMax = b.xPercent() + b.actionRangePercent() / 2.0;
                    double byMin = b.yPercent() - b.actionRangePercent() / 2.0;
                    double byMax = b.yPercent() + b.actionRangePercent() / 2.0;

                    boolean xOverlap = axMin < bxMax && bxMin < axMax;
                    boolean yOverlap = ayMin < byMax && byMin < ayMax;

                    assertFalse(xOverlap && yOverlap,
                        String.format("Solape en formación %s entre %s (%.2f,%.2f ±%.2f) y %s (%.2f,%.2f ±%.2f)",
                            f.name(),
                            a.subdivisionId(), a.xPercent(), a.yPercent(), a.actionRangePercent(),
                            b.subdivisionId(), b.xPercent(), b.yPercent(), b.actionRangePercent()));
                }
            }
        }
    }

    @Test
    @DisplayName("V25D53-C14: cobertura de subdivisionIds — exactamente 26 únicos referenciados por las 7 formations")
    void gridCoverageIsTwentySixUniqueSubdivisionIds() {
        // Golden test del audit C14: documenta que las 7 formations actuales
        // referencian exactamente estos 26 subdivisionIds (1 GK + 25 outfield).
        // Si alguien agrega/quita formations, este test detecta el delta.
        Set<String> actualUsed = new HashSet<>();
        for (FormationDTO f : service.getAllFormations()) {
            for (FormationPositionDTO p : f.positions()) {
                actualUsed.add(p.subdivisionId());
            }
        }
        Set<String> expectedUsed = Set.of(
            // GK
            "GK-1",
            // ATTACK row 0 (4-2-3-1 ST top)
            "S02-2",
            // ATTACK row 1 (wingers + ST centers)
            "S04-1", "S05-2", "S05-3", "S06-3",
            // MIDFIELD row 3 (4-2-3-1 CAM line)
            "S10-1", "S11-2", "S12-3",
            // MIDFIELD row 4 (4-1-4-1 LM/RM + 4-3-3 CMs)
            "S13-1", "S13-2", "S14-2", "S14-3", "S15-1", "S15-2", "S15-3",
            // MIDFIELD row 5 (wide mids + central mids)
            "S16-1", "S16-2", "S17-2", "S18-2", "S18-3",
            // DEFENSE row 7 (4-back/5-back/3-back lines)
            "S22-1", "S22-2", "S23-2", "S23-3", "S24-3"
        );
        assertEquals(26, actualUsed.size(),
            "Cantidad de subdivisionIds usados cambió del golden (26). "
                + "Actual: " + actualUsed);
        assertEquals(expectedUsed, actualUsed,
            "Set de subdivisionIds usados difiere del golden. "
                + "Faltan: " + diff(expectedUsed, actualUsed)
                + ". Sobran: " + diff(actualUsed, expectedUsed));
    }

    private static <T> Set<T> diff(Set<T> a, Set<T> b) {
        Set<T> result = new java.util.LinkedHashSet<>(a);
        result.removeAll(b);
        return result;
    }

    @Test
    @DisplayName("V25D53-C14: 56 subdivisionIds quedan sin usar por ninguna formation (gap documentado)")
    void gridGapIsFiftySixUnusedSubdivisionIds() {
        // Golden test del gap: 82 slots totales - 26 usados = 56 vacíos.
        // Si esto cambia, alguien agregó formations (bien) o rompió la grilla (mal).
        FieldSubdivisionService subdivisionService = new FieldSubdivisionService();
        Set<String> all = new HashSet<>();
        subdivisionService.getAllSubdivisions().forEach(s -> all.add(s.subdivisionId()));

        Set<String> used = new HashSet<>();
        for (FormationDTO f : service.getAllFormations()) {
            for (FormationPositionDTO p : f.positions()) {
                used.add(p.subdivisionId());
            }
        }

        Set<String> unused = new HashSet<>(all);
        unused.removeAll(used);

        assertEquals(82, all.size(), "Cambió el total de subdivisiones");
        assertEquals(26, used.size(), "Cambió la cantidad usada");
        assertEquals(56, unused.size(),
            "Cantidad de slots vacíos cambió. Vacíos actuales: " + unused);
    }

    @Test
    @DisplayName("V25D53-C14: el slot S23-2 es usado por las 7 formations (single point of failure)")
    void s23TwoIsUsedByAllFormations() {
        // Documenta que S23-2 (CB central en row 7) aparece en los 11 jugadores
        // de cada una de las 7 formations. Útil para detectar si alguien cambia
        // el "back line center" de las 4-back formations.
        for (FormationDTO f : service.getAllFormations()) {
            boolean hasS23_2 = f.positions().stream()
                .anyMatch(p -> "S23-2".equals(p.subdivisionId()));
            assertTrue(hasS23_2, "Formación " + f.name() + " no usa S23-2 (CB central)");
        }
    }
}