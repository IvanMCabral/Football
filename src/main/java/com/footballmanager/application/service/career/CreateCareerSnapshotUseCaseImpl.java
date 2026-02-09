package com.footballmanager.application.service.career;

import com.footballmanager.application.service.fixture.CareerFixtureService;
import com.footballmanager.domain.model.entity.*;
import com.footballmanager.domain.model.view.WorldView;
import com.footballmanager.domain.ports.in.career.CreateCareerSnapshotUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Implementación de CreateCareerSnapshotUseCase.
 *
 * Responsabilidad única: crear CareerSave desde WorldView.
 * Los CareerSave son snapshots inmutables que representan el estado
 * inicial de una carrera.
 *
 * Este use case NO carga datos - recibe WorldView ya construida.
 * La construcción de WorldView es responsabilidad de BuildWorldViewUseCase.
 */
@Service
@RequiredArgsConstructor
public class CreateCareerSnapshotUseCaseImpl implements CreateCareerSnapshotUseCase {

    private final CareerFixtureService careerFixtureService;

    @Override
    public Mono<CareerSave> create(
            WorldView worldView,
            UUID leagueId,
            UUID userTeamId,
            String difficulty,
            String gameSpeed,
            int teamsPerDivision) {

        return Mono.fromCallable(() -> {
            // Validar que la liga existe
            boolean leagueExists = worldView.leagues().stream()
                    .anyMatch(l -> l.getRealLeagueId().equals(leagueId));
            if (!leagueExists) {
                throw new IllegalArgumentException("Liga no encontrada: " + leagueId);
            }

            // Obtener equipos de la liga
            List<WorldTeam> leagueTeams = worldView.getTeamsByLeague(leagueId);
            if (leagueTeams.isEmpty()) {
                throw new IllegalStateException("La liga no tiene equipos");
            }
            if (leagueTeams.size() < 2) {
                throw new IllegalStateException("La liga debe tener al menos 2 equipos");
            }

            // Validar teamsPerDivision
            if (teamsPerDivision < 2 || teamsPerDivision > leagueTeams.size()) {
                throw new IllegalArgumentException("teamsPerDivision inválido");
            }

            // Validar que el equipo del usuario está en la liga
            boolean userTeamExists = leagueTeams.stream()
                    .anyMatch(t -> t.getWorldTeamId().equals(userTeamId.toString()));
            if (!userTeamExists) {
                throw new IllegalArgumentException("El equipo seleccionado no pertenece a la liga");
            }

            // Crear CareerSave
            CareerSave career = new CareerSave();
            career.setUserId(worldView.userId());
            career.setDifficulty(difficulty);
            career.setGameSpeed(gameSpeed);

            // Clonar equipos de la liga -> SessionTeams
            for (WorldTeam worldTeam : leagueTeams) {
                boolean isUserTeam = worldTeam.getWorldTeamId().equals(userTeamId.toString());
                String coachName = isUserTeam ? "User" : null;
                SessionTeam sessionTeam = cloneWorldTeamToSessionTeam(worldTeam, isUserTeam, coachName);
                career.addSessionTeam(sessionTeam);

                if (isUserTeam) {
                    career.setUserSessionTeamId(sessionTeam.getSessionTeamId());
                    career.setUserTeamId(UUID.fromString(sessionTeam.getSessionTeamId()));
                }

                // Clonar jugadores del equipo -> SessionPlayers
                List<WorldPlayer> teamPlayers = worldView.getPlayersByTeam(worldTeam.getWorldTeamId());

                int playerCountBefore = career.getSessionPlayers().size();
                for (WorldPlayer worldPlayer : teamPlayers) {
                    SessionPlayer sessionPlayer = cloneWorldPlayerToSessionPlayer(
                            worldPlayer, sessionTeam.getSessionTeamId());

                    // Verificar si ya existe este jugador
                    if (career.getSessionPlayer(sessionPlayer.getSessionPlayerId()) != null) {
                        continue;
                    }

                    career.addSessionPlayer(sessionPlayer);
                    career.assignPlayerToTeam(sessionPlayer.getSessionPlayerId(), sessionTeam.getSessionTeamId());
                }
            }

            // Asignar equipos a divisiones basándose en OVR
            career.assignTeamsToDivisions(teamsPerDivision);

            // Generar fixtures
            careerFixtureService.setupCareerFixtures(career, true);

            return career;
        });
    }

    private SessionTeam cloneWorldTeamToSessionTeam(WorldTeam worldTeam, boolean isUserTeam, String coachName) {
        return SessionTeam.cloneFromRealTeam(
                UUID.fromString(worldTeam.getWorldTeamId()),
                worldTeam.getWorldTeamId(),
                worldTeam.getName(),
                worldTeam.getCountry(),
                worldTeam.getBaseBudget(),
                worldTeam.getBaseFormation()
        );
    }

    private SessionPlayer cloneWorldPlayerToSessionPlayer(WorldPlayer worldPlayer, String sessionTeamId) {
        return SessionPlayer.cloneFromWorldPlayer(
                worldPlayer.getWorldPlayerId(),
                worldPlayer.getName(),
                worldPlayer.getPosition(),
                worldPlayer.getAge(),
                worldPlayer.calculateOverall(),
                sessionTeamId
        );
    }
}
