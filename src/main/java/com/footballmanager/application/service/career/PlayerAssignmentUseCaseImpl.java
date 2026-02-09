package com.footballmanager.application.service.career;

import com.footballmanager.domain.model.entity.*;
import com.footballmanager.domain.port.in.career.PlayerAssignmentUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Implementación de UseCase para asignación de jugadores.
 *
 * Maneja asignación y remoción de jugadores de equipos de sesión.
 */
@Service
@RequiredArgsConstructor
public class PlayerAssignmentUseCaseImpl implements PlayerAssignmentUseCase {

    private final CareerSessionService sessionService;
    private final CareerPlayerService playerService;

    @Override
    public Mono<CareerSave> assignPlayerToSessionTeam(UUID userId, String playerId, String teamId) {
        return sessionService.getCareer(userId)
            .flatMap(career -> {
                String sessionTeamId = career.findSessionTeamIdByWorldTeamId(teamId);
                if (sessionTeamId == null) {
                    sessionTeamId = teamId;
                }

                SessionPlayer existingPlayer = career.getSessionPlayer(playerId);
                final String finalSessionTeamId = sessionTeamId;

                if (existingPlayer != null) {
                    List<SessionPlayer> currentSquad = career.getTeamSquad(finalSessionTeamId);
                    if (currentSquad.size() >= 25) {
                        return Mono.error(new IllegalStateException("El equipo ya tiene 25 jugadores"));
                    }

                    career.assignPlayerToTeam(playerId, finalSessionTeamId);
                    return sessionService.saveCareer(career);
                } else {
                    return playerService.ensureSessionPlayerExists(career, playerId)
                        .flatMap(updatedCareer -> {
                            String sessionPlayerId = updatedCareer.findSessionPlayerIdByWorldPlayerId(playerId);

                            List<SessionPlayer> currentSquad = updatedCareer.getTeamSquad(finalSessionTeamId);
                            if (currentSquad.size() >= 25) {
                                return Mono.error(new IllegalStateException("El equipo ya tiene 25 jugadores"));
                            }

                            updatedCareer.assignPlayerToTeam(sessionPlayerId, finalSessionTeamId);
                            return sessionService.saveCareer(updatedCareer);
                        });
                }
            });
    }

    @Override
    public Mono<CareerSave> removePlayerFromSessionTeam(UUID userId, String playerId, String teamId) {
        return sessionService.getCareer(userId)
            .flatMap(career -> {
                SessionPlayer sessionPlayer = career.getSessionPlayer(playerId);

                if (sessionPlayer != null) {
                    String sessionTeamId = career.findSessionTeamIdByWorldTeamId(teamId);
                    if (sessionTeamId == null) {
                        sessionTeamId = teamId;
                    }

                    career.removePlayerFromTeam(playerId, sessionTeamId);
                    return sessionService.saveCareer(career);
                } else {
                    String sessionTeamId = career.findSessionTeamIdByWorldTeamId(teamId);
                    String worldTeamId;

                    if (sessionTeamId != null) {
                        worldTeamId = teamId;
                    } else {
                        SessionTeam sessionTeam = career.getSessionTeam(teamId);
                        worldTeamId = sessionTeam != null ? sessionTeam.getWorldTeamId() : teamId;
                    }

                    career.markPlayerAsRemoved(worldTeamId, playerId);
                    return sessionService.saveCareer(career);
                }
            });
    }

    @Override
    public Mono<List<SessionPlayer>> getSessionTeamSquad(UUID userId, String sessionTeamId) {
        return sessionService.continueCareer(userId)
            .map(career -> career.getTeamSquad(sessionTeamId))
            .defaultIfEmpty(Collections.emptyList());
    }
}
