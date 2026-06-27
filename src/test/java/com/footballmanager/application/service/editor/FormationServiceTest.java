package com.footballmanager.application.service.editor;

import com.footballmanager.adapters.in.web.career.lineup.dto.FormationDTO;
import com.footballmanager.adapters.in.web.career.lineup.dto.FormationPositionDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MVP1-lineup-cancha-1: tests para el servicio de formaciones.
 *
 * <p>V25D36-F2: 7 formaciones (antes 4) — agregadas 5-3-2, 4-1-4-1 y
 * 3-4-3 que el engine ya entendía pero que el servicio no exponía.
 *
 * <p>V25D54-C15 (Sprint C15 — Formations reality): ahora 12 formaciones
 * (7 originales + 4 nuevas + 1 variante 4-3-3-1). P0 corrigió role labels
 * de 3-5-2/3-4-3 wide mids (LM→LWB, RM→RWB). P1 agregó 3-5-2-CDM, 5-4-1,
 * 3-4-1-2, 4-2-2-2. P2 agregó variante 4-3-3-1 con pivote CDM.
 *
 * <p>Cubre el contrato: 12 formaciones con 11 posiciones cada una
 * (1 GK + outfieldPlayers), subdivisionIds únicos dentro de cada formación,
 * counts de defensores/mediocampistas/atacantes coincidentes con la formación,
 * role labels esperados por formation (golden master).
 */
class FormationServiceTest {

    private final FormationService service = new FormationService();

    @Test
    @DisplayName("getAllFormations retorna exactamente 12 formaciones (7 originales + 4 nuevas V25D54-C15 + 1 variante 4-3-3-1)")
    void returnsExactly12Formations() {
        assertEquals(12, service.getAllFormations().size());
    }

    @Test
    @DisplayName("Las 12 formaciones esperadas están presentes")
    void allExpectedFormationsPresent() {
        Set<String> names = Set.of(
            // V25D36-F2: 7 originales
            "4-4-2", "4-3-3", "3-5-2", "4-2-3-1",
            "5-3-2", "4-1-4-1", "3-4-3",
            // V25D54-C15 P1: 4 nuevas
            "3-5-2-CDM", "5-4-1", "3-4-1-2", "4-2-2-2",
            // V25D54-C15 P2: 1 variante
            "4-3-3-1");
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
        // V25D54-C15 P1: las 4 formations nuevas.
        assertNotNull(service.getFormationByName("3-5-2-CDM"));
        assertNotNull(service.getFormationByName("5-4-1"));
        assertNotNull(service.getFormationByName("3-4-1-2"));
        assertNotNull(service.getFormationByName("4-2-2-2"));
        // V25D54-C15 P2: la variante 4-3-3-1.
        assertNotNull(service.getFormationByName("4-3-3-1"));
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
    @DisplayName("V25D53-C14: el slot S23-2 es usado por todas las formations (single point of failure)")
    void s23TwoIsUsedByAllFormations() {
        // Documenta que S23-2 (CB central en row 7) aparece en los 11 jugadores
        // de cada formación. Útil para detectar si alguien cambia el "back line
        // center" de las 4-back formations.
        // V25D54-C15: ahora son 12 formations — sigue siendo cierto.
        for (FormationDTO f : service.getAllFormations()) {
            boolean hasS23_2 = f.positions().stream()
                .anyMatch(p -> "S23-2".equals(p.subdivisionId()));
            assertTrue(hasS23_2, "Formación " + f.name() + " no usa S23-2 (CB central)");
        }
    }

    // ========== V25D54-C15 P0 (Sprint C15 — Formations reality: role labels) ==========
    //
    // Golden tests que validan los role labels esperados por formation. Atrapan
    // regresiones si alguien edita `buildFormations()` y cambia roles sin querer.
    // Estos tests son el guardrail para el fix P0 (LM→LWB en 3-5-2/3-4-3).

    @Test
    @DisplayName("V25D54-C15 P0: 3-5-2 wide mids son LWB/RWB (no LM/RM)")
    void formation_3_5_2_usesLwbRwbForWideMids() {
        FormationDTO f = service.getFormationByName("3-5-2");
        assertNotNull(f);
        // pos #4 (slot S15-1) debe ser LWB
        FormationPositionDTO leftWide = f.positions().stream()
            .filter(p -> "S15-1".equals(p.subdivisionId()))
            .findFirst().orElseThrow();
        assertEquals("LWB", leftWide.role(),
            "3-5-2 pos #4 (S15-1) esperaba LWB, fue " + leftWide.role());

        // pos #8 (slot S18-3) debe ser RWB
        FormationPositionDTO rightWide = f.positions().stream()
            .filter(p -> "S18-3".equals(p.subdivisionId()))
            .findFirst().orElseThrow();
        assertEquals("RWB", rightWide.role(),
            "3-5-2 pos #8 (S18-3) esperaba RWB, fue " + rightWide.role());
    }

    @Test
    @DisplayName("V25D54-C15 P0: 3-4-3 wide mids son LWB/RWB (no LM/RM)")
    void formation_3_4_3_usesLwbRwbForWideMids() {
        FormationDTO f = service.getFormationByName("3-4-3");
        assertNotNull(f);
        FormationPositionDTO leftWide = f.positions().stream()
            .filter(p -> "S15-1".equals(p.subdivisionId()))
            .findFirst().orElseThrow();
        assertEquals("LWB", leftWide.role(),
            "3-4-3 pos #4 (S15-1) esperaba LWB, fue " + leftWide.role());

        FormationPositionDTO rightWide = f.positions().stream()
            .filter(p -> "S18-3".equals(p.subdivisionId()))
            .findFirst().orElseThrow();
        assertEquals("RWB", rightWide.role(),
            "3-4-3 pos #7 (S18-3) esperaba RWB, fue " + rightWide.role());
    }

    @Test
    @DisplayName("V25D54-C15 P0: 4-4-2 wide mids siguen siendo LM/RM (no afectados por P0)")
    void formation_4_4_2_wideMidsRemainLmRm() {
        FormationDTO f = service.getFormationByName("4-4-2");
        assertNotNull(f);
        FormationPositionDTO leftWide = f.positions().stream()
            .filter(p -> "S16-1".equals(p.subdivisionId()))
            .findFirst().orElseThrow();
        assertEquals("LM", leftWide.role());

        FormationPositionDTO rightWide = f.positions().stream()
            .filter(p -> "S18-3".equals(p.subdivisionId()))
            .findFirst().orElseThrow();
        assertEquals("RM", rightWide.role());
    }

    @Test
    @DisplayName("V25D54-C15 P0: golden roles para las 7 formations originales")
    void goldenRolesForOriginal7Formations() {
        // Snapshot de los role labels esperados. Si alguno cambia sin razón,
        // este test detecta el delta y obliga a actualizar el golden.
        Map<String, List<String>> expectedRoles = Map.of(
            "4-4-2", List.of("GK", "LB", "CB", "CB", "RB", "LM", "CM", "CM", "RM", "ST", "ST"),
            "4-3-3", List.of("GK", "LB", "CB", "CB", "RB", "CM", "CM", "CM", "LW", "ST", "RW"),
            "3-5-2", List.of("GK", "CB", "CB", "CB", "LWB", "CM", "CM", "CM", "RWB", "ST", "ST"),
            "4-2-3-1", List.of("GK", "LB", "CB", "CB", "RB", "CDM", "CDM", "LW", "CAM", "RW", "ST"),
            "5-3-2", List.of("GK", "LB", "CB", "CB", "CB", "RB", "CM", "CM", "CM", "ST", "ST"),
            "4-1-4-1", List.of("GK", "LB", "CB", "CB", "RB", "CDM", "LM", "CM", "CM", "RM", "ST"),
            "3-4-3", List.of("GK", "CB", "CB", "CB", "LWB", "CM", "CM", "RWB", "LW", "ST", "RW")
        );
        for (var entry : expectedRoles.entrySet()) {
            String formationName = entry.getKey();
            List<String> expected = entry.getValue();
            FormationDTO f = service.getFormationByName(formationName);
            assertNotNull(f, formationName + " no encontrada");
            List<String> actual = f.positions().stream()
                .map(FormationPositionDTO::role)
                .toList();
            assertEquals(expected, actual,
                formationName + " roles no coinciden. Esperaba " + expected + " pero fue " + actual);
        }
    }

    // ========== V25D54-C15 P1 (4 formations nuevas) + P2 (variante 4-3-3-1) ==========

    @Test
    @DisplayName("V25D54-C15 P1+P2: golden roles para las 5 formations nuevas")
    void goldenRolesForNew5Formations() {
        Map<String, List<String>> expectedRoles = Map.of(
            // P1.1: 3-5-2-CDM — 3 CB + 1 CDM + 2 CM + 2 WB + 2 ST
            "3-5-2-CDM", List.of("GK", "CB", "CB", "CB", "CDM", "CM", "CM", "LWB", "RWB", "ST", "ST"),
            // P1.2: 5-4-1 — 5 DEF + LM + 2 CM + RM + 1 ST
            "5-4-1", List.of("GK", "LB", "CB", "CB", "CB", "RB", "LM", "CM", "CM", "RM", "ST"),
            // P1.3: 3-4-1-2 (Christmas tree) — 3 CB + LWB + 2 CM + RWB + CAM + 2 ST
            "3-4-1-2", List.of("GK", "CB", "CB", "CB", "LWB", "CM", "CM", "RWB", "CAM", "ST", "ST"),
            // P1.4: 4-2-2-2 — 4 DEF + 2 CDM + LM + RM + 2 ST
            "4-2-2-2", List.of("GK", "LB", "CB", "CB", "RB", "CDM", "CDM", "LM", "RM", "ST", "ST"),
            // P2: 4-3-3-1 (variant con CDM pivot) — 4 DEF + CDM + 2 CM + LW + ST + RW
            "4-3-3-1", List.of("GK", "LB", "CB", "CB", "RB", "CDM", "CM", "CM", "LW", "ST", "RW")
        );
        for (var entry : expectedRoles.entrySet()) {
            String formationName = entry.getKey();
            List<String> expected = entry.getValue();
            FormationDTO f = service.getFormationByName(formationName);
            assertNotNull(f, formationName + " no encontrada");
            List<String> actual = f.positions().stream()
                .map(FormationPositionDTO::role)
                .toList();
            assertEquals(expected, actual,
                formationName + " roles no coinciden. Esperaba " + expected + " pero fue " + actual);
        }
    }

    @Test
    @DisplayName("V25D54-C15 P1: cada formation nueva tiene 11 subdivisionIds únicos y coords en [0,100]")
    void newFormationsHaveUniqueSubdivisionIdsAndValidCoords() {
        String[] newFormations = {"3-5-2-CDM", "5-4-1", "3-4-1-2", "4-2-2-2", "4-3-3-1"};
        for (String formationName : newFormations) {
            FormationDTO f = service.getFormationByName(formationName);
            assertNotNull(f, formationName + " no encontrada");
            assertEquals(11, f.positions().size(),
                formationName + " no tiene 11 posiciones");

            // subdivisionIds únicos
            Set<String> ids = new HashSet<>();
            for (FormationPositionDTO p : f.positions()) {
                assertTrue(ids.add(p.subdivisionId()),
                    formationName + " tiene subdivisionId duplicado: " + p.subdivisionId());
            }

            // coords en [0, 100]
            for (FormationPositionDTO p : f.positions()) {
                assertNotNull(p.xPercent());
                assertNotNull(p.yPercent());
                assertTrue(p.xPercent() >= 0 && p.xPercent() <= 100,
                    formationName + " xPercent fuera de rango: " + p.xPercent());
                assertTrue(p.yPercent() >= 0 && p.yPercent() <= 100,
                    formationName + " yPercent fuera de rango: " + p.yPercent());
            }

            // exactamente 1 GK
            long gkCount = f.positions().stream()
                .filter(p -> "GK".equals(p.role()))
                .count();
            assertEquals(1, gkCount,
                formationName + " debería tener exactamente 1 GK, tuvo " + gkCount);

            // meta defenders+midfielders+attackers == outfieldPlayers
            int sum = f.defenders() + f.midfielders() + f.attackers();
            assertEquals(f.outfieldPlayers().intValue(), sum,
                formationName + " meta no cuadra");
        }
    }

    @Test
    @DisplayName("V25D54-C15: el slot S23-2 sigue siendo usado por las 5 formations nuevas")
    void s23TwoIsUsedByNewFormations() {
        // Golden master de S23-2 ahora cubre las 12 formations (7 originales
        // + 5 nuevas). Las nuevas formations usan back-three o back-four con
        // CB central en S23-2, igual que las originales.
        String[] newFormations = {"3-5-2-CDM", "5-4-1", "3-4-1-2", "4-2-2-2", "4-3-3-1"};
        for (String formationName : newFormations) {
            FormationDTO f = service.getFormationByName(formationName);
            assertNotNull(f);
            boolean hasS23_2 = f.positions().stream()
                .anyMatch(p -> "S23-2".equals(p.subdivisionId()));
            assertTrue(hasS23_2,
                formationName + " no usa S23-2 (CB central) — esperado por golden master");
        }
    }
}