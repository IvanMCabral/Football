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
}