package com.footballmanager.domain.service;

import com.footballmanager.domain.model.valueobject.TeamId;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Validador de fixtures de tournament.
 * Verifica que un fixture generado cumpla con todas las reglas del torneo.
 */
@Component
public class FixtureValidator {

    public void validate(List<FixtureGenerator.FixtureRound> fixture, List<TeamId> teamIds,
                         boolean hasBye, boolean isDoubleRound) {
        int teamCount = teamIds.size();

        Set<String> idaMatches = new HashSet<>();
        Set<String> vueltaMatches = new HashSet<>();
        Map<TeamId, Integer> partidosPorEquipo = new HashMap<>();

        for (FixtureGenerator.FixtureRound fr : fixture) {
            Set<TeamId> jugadosEnRonda = new HashSet<>();
            boolean isVuelta = fr.isReturnLeg();

            for (FixtureGenerator.FixtureSlot slot : fr.matches()) {
                TeamId home = slot.home();
                TeamId away = slot.away();

                // null es bye, no lo contamos
                if (home == null || away == null) {
                    continue;
                }

                // Validar que no haya equipos duplicados en la misma ronda
                if (!jugadosEnRonda.add(home)) {
                    throw new IllegalStateException("Equipo " + home + " repite en ronda " + fr.roundNumber());
                }
                if (!jugadosEnRonda.add(away)) {
                    throw new IllegalStateException("Equipo " + away + " repite en ronda " + fr.roundNumber());
                }

                // Contar partidos por equipo
                partidosPorEquipo.put(home, partidosPorEquipo.getOrDefault(home, 0) + 1);
                partidosPorEquipo.put(away, partidosPorEquipo.getOrDefault(away, 0) + 1);

                // Crear clave única para el cruce (ordenado)
                String matchKey = createMatchKey(home, away);

                // Validar cruces únicos
                if (isVuelta) {
                    if (!vueltaMatches.add(matchKey)) {
                        throw new IllegalStateException("Cruce duplicado en VUELTA: " + home + "-" + away);
                    }
                } else {
                    if (!idaMatches.add(matchKey)) {
                        throw new IllegalStateException("Cruce duplicado en IDA: " + home + "-" + away);
                    }
                }
            }
        }

        int expectedCruces = teamCount * (teamCount - 1) / 2;
        if (idaMatches.size() != expectedCruces) {
            throw new IllegalStateException("IDA no tiene todos los cruces: esperado " + expectedCruces +
                ", obtenido " + idaMatches.size() + " (con " + teamCount + " equipos)");
        }

        if (isDoubleRound && vueltaMatches.size() != idaMatches.size()) {
            throw new IllegalStateException("VUELTA no tiene mismos cruces que IDA");
        }

        // Calcular partidos esperados por equipo
        int expectedPerTeam = (teamCount - 1) * (isDoubleRound ? 2 : 1);

        for (TeamId id : teamIds) {
            int actual = partidosPorEquipo.getOrDefault(id, 0);
            if (actual != expectedPerTeam) {
                throw new IllegalStateException("Equipo " + id + " tiene " + actual + " partidos, esperados " + expectedPerTeam);
            }
        }
    }

    private String createMatchKey(TeamId a, TeamId b) {
        // Ordenar los UUIDs para que A-B y B-A sean el mismo partido
        String idA = a.getValue().toString();
        String idB = b.getValue().toString();
        int cmp = idA.compareTo(idB);
        return cmp < 0 ? idA + "|" + idB : idB + "|" + idA;
    }
}
