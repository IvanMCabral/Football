package com.footballmanager.application.service.simulation;

import com.footballmanager.domain.service.MatchSimulator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Simula partidos de OTRAS ligas completamente separadas.
 * Estas ligas no estan en la carrera del usuario.
 */
@Service
@RequiredArgsConstructor
public class OtherLeaguesSimulator {

    private final MatchSimulator matchSimulator;

    /**
     * Simula una fecha completa de una liga externa.
     * Los equipos se generan con OVR aleatorio basado en su "fortaleza".
     */
    public void simulateExternalLeague(int round, List<ExternalTeam> teams) {
        // Generar fixtures si no existen (emparejamientos por ronda)
        List<ExternalMatch> matches = generateFixtures(round, teams);

        for (ExternalMatch match : matches) {
            // Usar simulacion rapida con OVR de los equipos externos
            matchSimulator.simulateQuick(
                    match.homeTeamId(),
                    match.awayTeamId(),
                    match.homeOvr(),
                    match.awayOvr()
            );
        }
    }

    /**
     * Genera emparejamientos para una fecha.
     * Usa algoritmo de ronda (cada equipo juega una vez por fecha).
     */
    private List<ExternalMatch> generateFixtures(int round, List<ExternalTeam> teams) {
        List<ExternalMatch> matches = new ArrayList<>();
        int teamCount = teams.size();

        if (teamCount < 2) return matches;

        // Encontrar equipos que no tienen bye en esta ronda
        List<ExternalTeam> playingTeams = new ArrayList<>(teams);

        // Si hay numero impar, el ultimo tiene bye
        if (teamCount % 2 == 1) {
            playingTeams.remove(playingTeams.size() - 1);
        }

        // Emparejar primero con ultimo, segundo con antepenultimo, etc.
        int half = playingTeams.size() / 2;
        for (int i = 0; i < half; i++) {
            ExternalTeam home = playingTeams.get(i);
            ExternalTeam away = playingTeams.get(playingTeams.size() - 1 - i);
            matches.add(new ExternalMatch(
                    home.id(), home.name(), home.ovr(),
                    away.id(), away.name(), away.ovr()
            ));
        }

        return matches;
    }

    /**
     * Equipo de liga externa.
     */
    public record ExternalTeam(String id, String name, int ovr) {}

    /**
     * Partido de liga externa.
     */
    public record ExternalMatch(
            String homeTeamId, String homeTeamName, int homeOvr,
            String awayTeamId, String awayTeamName, int awayOvr
    ) {}
}
