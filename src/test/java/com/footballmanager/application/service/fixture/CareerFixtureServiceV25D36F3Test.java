package com.footballmanager.application.service.fixture;

import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.Division;
import com.footballmanager.domain.model.valueobject.MatchFixture;
import com.footballmanager.domain.model.valueobject.TeamId;
import com.footballmanager.domain.service.FixtureGenerator;
import com.footballmanager.domain.service.FixtureGenerator.FixtureRound;
import com.footballmanager.domain.service.FixtureGenerator.FixtureSlot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

/**
 * V25D36-F3: tests para los guards defensivos en CareerFixtureService.
 *
 * <p>El bug original reportado: "Real Madrid vs Real Madrid" en el fixture del
 * career, con el mismo teamId apareciendo como home y away. Sin poder
 * reproducir el caso exacto sin levantar el stack, este test verifica que
 * la defensa en profundidad funciona:
 * <ol>
 *   <li>Deduplicación de teamIds en division (preserva orden, skip null/blank).</li>
 *   <li>Skip de MatchSlots donde home == away (defense si la dedup falla).</li>
 *   <li>Count de fixtures consistente con round-robin matemático.</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class CareerFixtureServiceV25D36F3Test {

    @Mock
    private FixtureGenerator fixtureGenerator;

    /**
     * Stub que simula un round-robin retornando siempre
     * los mismos slots que le pasamos en el argumento teamIds.
     * Sirve para verificar que el guard filtra los self-pairings.
     */
    private List<FixtureRound> buildSingleRoundStub(List<TeamId> input, int roundNumber) {
        // Construimos un round con: A vs B (input[0] vs input[1]),
        // C vs D (input[2] vs input[3]). Si n impar, último es self.
        List<FixtureSlot> slots = new ArrayList<>();
        for (int i = 0; i + 1 < input.size(); i += 2) {
            slots.add(new FixtureSlot(input.get(i), input.get(i + 1)));
        }
        if (!slots.isEmpty() || input.isEmpty()) {
            return List.of(new FixtureRound(roundNumber, slots, false));
        }
        return List.of();
    }

    @Test
    @DisplayName("V25D36-F3: deduplica teamIds duplicados antes de generar fixture")
    void deduplicatesDuplicateTeamIds() {
        // GIVEN: division con teamId "A" duplicado
        Division division = new Division("Division 1", 1);
        String teamA = UUID.randomUUID().toString();
        String teamB = UUID.randomUUID().toString();
        String teamC = UUID.randomUUID().toString();
        // [A, A, B, C] → dedup → [A, B, C] (preserva orden de primera aparición)
        division.setTeamIds(Arrays.asList(teamA, teamA, teamB, teamC));

        CareerSave career = new CareerSave();
        career.setCurrentSeason(1);

        // Capturar lo que el fixtureGenerator recibe como teamIds
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<TeamId>> captor =
            org.mockito.ArgumentCaptor.forClass(List.class);

        // Stub: devuelve round 1 con un match (los dos primeros del input shuffled).
        when(fixtureGenerator.generate(captor.capture(), anyBoolean())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            List<TeamId> arg = (List<TeamId>) inv.getArgument(0);
            // Simula un round con 1 match cualquiera de los teams disponibles.
            // Importante: el orden post-shuffle es no-determinístico, así que
            // tomamos los primeros 2.
            return List.of(new FixtureRound(1,
                List.of(new FixtureSlot(arg.get(0), arg.get(1))), false));
        });

        CareerFixtureService service = new CareerFixtureService(fixtureGenerator);

        // WHEN
        List<MatchFixture> fixtures = service.generateFixturesForDivision(division, career);

        // THEN 1: el generator recibió exactamente 3 teamIds (dedup exitoso)
        List<TeamId> passedToGenerator = captor.getValue();
        assertEquals(3, passedToGenerator.size(),
            "V25D36-F3 dedup: el generator debe recibir 3 teamIds unicos, got=" + passedToGenerator.size());

        // THEN 2: los 3 teamIds únicos son exactamente teamA, teamB, teamC
        java.util.Set<String> uniqueIdsPassed = new java.util.HashSet<>();
        for (TeamId tid : passedToGenerator) {
            uniqueIdsPassed.add(tid.getValue().toString());
        }
        java.util.Set<String> expectedUnique = new java.util.HashSet<>(Arrays.asList(teamA, teamB, teamC));
        assertEquals(expectedUnique, uniqueIdsPassed,
            "V25D36-F3 dedup: el generator debe recibir exactamente {A, B, C}");

        // THEN 3: el fixture generado tiene home != away y ambos vienen del set dedup
        assertEquals(1, fixtures.size());
        MatchFixture fixture = fixtures.get(0);
        assertNotEquals(fixture.getHomeTeamId(), fixture.getAwayTeamId());
        assertTrue(expectedUnique.contains(fixture.getHomeTeamId()),
            "homeTeamId debe ser uno de los unique teamIds");
        assertTrue(expectedUnique.contains(fixture.getAwayTeamId()),
            "awayTeamId debe ser uno de los unique teamIds");
    }

    @Test
    @DisplayName("V25D36-F3: skip null y blank teamIds sin romper")
    void skipsNullAndBlankTeamIds() {
        Division division = new Division("Division 1", 1);
        String teamA = UUID.randomUUID().toString();
        String teamB = UUID.randomUUID().toString();
        // [null, "", A, B] → dedup → [A, B]
        List<String> withGarbage = new ArrayList<>();
        withGarbage.add(null);
        withGarbage.add("");
        withGarbage.add(teamA);
        withGarbage.add(teamB);
        division.setTeamIds(withGarbage);

        CareerSave career = new CareerSave();
        career.setCurrentSeason(1);

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<TeamId>> captor =
            org.mockito.ArgumentCaptor.forClass(List.class);

        when(fixtureGenerator.generate(captor.capture(), anyBoolean())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            List<TeamId> arg = (List<TeamId>) inv.getArgument(0);
            return List.of(new FixtureRound(1,
                List.of(new FixtureSlot(arg.get(0), arg.get(1))), false));
        });

        CareerFixtureService service = new CareerFixtureService(fixtureGenerator);

        List<MatchFixture> fixtures = service.generateFixturesForDivision(division, career);

        // THEN: el generator recibió EXACTAMENTE 2 teamIds (los null/blank
        // fueron skipeados). El orden post-shuffle es no-determinístico,
        // así que solo verificamos el set.
        List<TeamId> passedToGenerator = captor.getValue();
        assertEquals(2, passedToGenerator.size(),
            "Solo A y B deberían llegar al generator, got=" + passedToGenerator.size());

        java.util.Set<String> uniqueIdsPassed = new java.util.HashSet<>();
        for (TeamId tid : passedToGenerator) {
            uniqueIdsPassed.add(tid.getValue().toString());
        }
        java.util.Set<String> expected = new java.util.HashSet<>(Arrays.asList(teamA, teamB));
        assertEquals(expected, uniqueIdsPassed,
            "V25D36-F3: solo A y B deberían llegar al generator");

        // El fixture generado tiene home y away del set dedup, distintos entre sí
        assertEquals(1, fixtures.size());
        MatchFixture f = fixtures.get(0);
        assertNotEquals(f.getHomeTeamId(), f.getAwayTeamId());
        assertTrue(expected.contains(f.getHomeTeamId()));
        assertTrue(expected.contains(f.getAwayTeamId()));
    }

    @Test
    @DisplayName("V25D36-F3: skipea MatchSlot con home==away (defense in depth)")
    void skipsSelfPairingSlot() {
        Division division = new Division("Division 1", 1);
        String teamA = UUID.randomUUID().toString();
        String teamB = UUID.randomUUID().toString();
        division.setTeamIds(List.of(teamA, teamB));

        CareerSave career = new CareerSave();
        career.setCurrentSeason(1);

        // El upstream malicioso/roto retorna: 1 slot normal (A vs B) + 1 self (A vs A).
        TeamId teamIdA = TeamId.of(UUID.fromString(teamA));
        TeamId teamIdB = TeamId.of(UUID.fromString(teamB));
        when(fixtureGenerator.generate(any(), anyBoolean())).thenReturn(List.of(
            new FixtureRound(1, List.of(
                new FixtureSlot(teamIdA, teamIdB),     // OK
                new FixtureSlot(teamIdA, teamIdA)      // self — debe skipearse
            ), false)
        ));

        CareerFixtureService service = new CareerFixtureService(fixtureGenerator);

        List<MatchFixture> fixtures = service.generateFixturesForDivision(division, career);

        // THEN: solo 1 fixture (A vs B), el self fue skipeado
        assertEquals(1, fixtures.size(), "El self-pairing debe skipearse");
        assertEquals(teamA, fixtures.get(0).getHomeTeamId());
        assertEquals(teamB, fixtures.get(0).getAwayTeamId());
    }

    @Test
    @DisplayName("V25D36-F3: caso normal 4 teams genera 6 fixtures (3 ida + 3 vuelta), sin self-pair")
    void normalCaseNoSelfPair() {
        Division division = new Division("Division 1", 1);
        List<String> teamIds = List.of(
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString()
        );
        division.setTeamIds(teamIds);

        CareerSave career = new CareerSave();
        career.setCurrentSeason(1);

        // Stub: genera 4 rounds de 2 matches (round-robin real)
        when(fixtureGenerator.generate(any(), anyBoolean())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            List<TeamId> arg = (List<TeamId>) inv.getArgument(0);
            List<FixtureRound> result = new ArrayList<>();
            // 3 rounds ida + 3 rounds vuelta = 6 rounds con 2 matches c/u = 12
            for (int r = 1; r <= 6; r++) {
                result.add(new FixtureRound(r, List.of(
                    new FixtureSlot(arg.get(0), arg.get(1)),
                    new FixtureSlot(arg.get(2), arg.get(3))
                ), r > 3));
            }
            return result;
        });

        CareerFixtureService service = new CareerFixtureService(fixtureGenerator);

        List<MatchFixture> fixtures = service.generateFixturesForDivision(division, career);

        assertEquals(12, fixtures.size());
        // Verificar NINGÚN self-pair
        for (MatchFixture f : fixtures) {
            assertNotEquals(f.getHomeTeamId(), f.getAwayTeamId(),
                "Fixture con self-pair detectado: " + f.getHomeTeamId());
        }
    }
}