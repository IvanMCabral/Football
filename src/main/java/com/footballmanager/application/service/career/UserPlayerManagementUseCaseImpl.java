package com.footballmanager.application.service.career;

import com.footballmanager.application.service.world.WorldService;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.WorldPlayer;
import com.footballmanager.domain.port.in.career.UserPlayerManagementUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Implementación de UseCase para gestión de jugadores del usuario.
 *
 * Maneja asignación, remoción y consulta del squad del usuario.
 */
@Service
@RequiredArgsConstructor
public class UserPlayerManagementUseCaseImpl implements UserPlayerManagementUseCase {

    private final CareerSessionService sessionService;
    private final WorldService worldService;

    @Override
    public Flux<SessionPlayer> getUserPlayers(UUID userId) {
        return sessionService.getCareer(userId)
            .flatMapMany(career -> Flux.fromIterable(career.getTeamSquad(career.getUserSessionTeamId())));
    }

    @Override
    public Mono<CareerSave> assignPlayerToUserTeam(UUID userId, String sessionPlayerId) {
        return sessionService.getCareer(userId)
            .flatMap(career -> {
                SessionPlayer player = career.getSessionPlayer(sessionPlayerId);

                if (player == null) {
                    boolean isRemovedWorldPlayer = career.getRemovedPlayers().values().stream()
                        .anyMatch(set -> set.contains(sessionPlayerId));

                    if (!isRemovedWorldPlayer) {
                        return Mono.error(new IllegalArgumentException("Jugador no encontrado: " + sessionPlayerId));
                    }

                    return worldService.getAllWorldPlayers(userId)
                        .flatMap(worldPlayers -> {
                            WorldPlayer wp = worldPlayers.stream()
                                .filter(p -> p.getWorldPlayerId().equals(sessionPlayerId))
                                .findFirst()
                                .orElse(null);

                            if (wp == null) {
                                return Mono.error(new IllegalArgumentException("WorldPlayer no encontrado: " + sessionPlayerId));
                            }

                            SessionPlayer newSessionPlayer = SessionPlayer.cloneFromWorldPlayer(
                                wp.getWorldPlayerId(),
                                wp.getName(),
                                wp.getPosition().toString(),
                                wp.getAge(),
                                wp.calculateOverall(),
                                career.getUserTeamId().toString()
                            );

                            career.addSessionPlayer(newSessionPlayer);
                            career.getRemovedPlayers().values().forEach(set -> set.remove(sessionPlayerId));
                            career.assignPlayerToTeam(sessionPlayerId, career.getUserSessionTeamId());

                            return sessionService.saveCareer(career);
                        });
                }

                // Es un SessionPlayer normal
                List<SessionPlayer> currentSquad = career.getTeamSquad(career.getUserSessionTeamId());
                if (currentSquad.size() >= 25) {
                    return Mono.error(new IllegalStateException("El equipo ya tiene 25 jugadores"));
                }

                career.assignPlayerToTeam(sessionPlayerId, career.getUserSessionTeamId());
                return sessionService.saveCareer(career);
            });
    }

    @Override
    public Mono<CareerSave> removePlayerFromUserTeam(UUID userId, String sessionPlayerId) {
        return sessionService.getCareer(userId)
            .flatMap(career -> {
                career.removePlayer(sessionPlayerId);
                return sessionService.saveCareer(career);
            });
    }

    @Override
    public Mono<List<SessionPlayer>> getUserSquad(UUID userId) {
        return sessionService.getCareer(userId)
            .map(career -> {
                String userTeamId = career.getUserSessionTeamId();
                if (userTeamId == null) {
                    return List.<SessionPlayer>of();
                }
                return career.getTeamSquad(userTeamId);
            });
    }
}
