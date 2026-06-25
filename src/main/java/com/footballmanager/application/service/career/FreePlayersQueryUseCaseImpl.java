package com.footballmanager.application.service.career;

import com.footballmanager.application.service.world.WorldService;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.port.in.career.FreePlayersQueryUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementación de UseCase para consultar free players.
 *
 * Implementa modo dual: Editor vs Gameplay.
 */
@Service
@RequiredArgsConstructor
public class FreePlayersQueryUseCaseImpl implements FreePlayersQueryUseCase {

    private final CareerSessionService sessionService;
    private final WorldService worldService;

    @Override
    public Mono<List<SessionPlayer>> getFreePlayers(java.util.UUID userId) {
        return sessionService.continueCareer(userId)
            .flatMap(career -> {
                List<SessionPlayer> customFreePlayers = career.getFreePlayersObjects();
                Set<String> playersInTeams = new HashSet<>();
                career.getTeamManager().getTeamSquads().values().forEach(playersInTeams::addAll);

                List<SessionPlayer> availableCustomPlayers = customFreePlayers.stream()
                    .filter(p -> !playersInTeams.contains(p.getSessionPlayerId()))
                    .collect(Collectors.toList());

                Set<String> allRemovedPlayerIds = new HashSet<>();
                career.getRemovedPlayers().values().forEach(allRemovedPlayerIds::addAll);

                Set<String> convertedWorldPlayerIds = career.getSessionPlayers().values().stream()
                    .map(SessionPlayer::getWorldPlayerId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

                allRemovedPlayerIds.removeAll(convertedWorldPlayerIds);

                return worldService.getAllWorldPlayers(userId)
                    .map(worldPlayers -> {
                        List<SessionPlayer> removedPlayers = worldPlayers.stream()
                            .filter(wp -> allRemovedPlayerIds.contains(wp.getWorldPlayerId()))
                            .map(wp -> SessionPlayer.cloneFromWorldPlayer(
                                wp.getWorldPlayerId(), wp.getName(),
                                wp.getPosition().toString(), wp.getAge(),
                                wp.calculateOverall(), null,
                                wp.getHeightCm(), wp.getSkillLevels()
                            ))
                            .collect(Collectors.toList());

                        List<SessionPlayer> freeWorldPlayers = worldPlayers.stream()
                            .filter(wp -> (wp.getWorldTeamId() == null || wp.getWorldTeamId().isEmpty()))
                            .filter(wp -> !allRemovedPlayerIds.contains(wp.getWorldPlayerId()))
                            .filter(wp -> !convertedWorldPlayerIds.contains(wp.getWorldPlayerId()))
                            .map(wp -> SessionPlayer.cloneFromWorldPlayer(
                                wp.getWorldPlayerId(), wp.getName(),
                                wp.getPosition().toString(), wp.getAge(),
                                wp.calculateOverall(), null,
                                wp.getHeightCm(), wp.getSkillLevels()
                            ))
                            .collect(Collectors.toList());

                        List<SessionPlayer> combined = new ArrayList<>(availableCustomPlayers);
                        combined.addAll(removedPlayers);
                        combined.addAll(freeWorldPlayers);

                        return combined;
                    });
            })
            .switchIfEmpty(Mono.defer(() -> {
                return worldService.getAllWorldPlayers(userId)
                    .map(worldPlayers -> {
                        List<SessionPlayer> freePlayers = worldPlayers.stream()
                            .filter(wp -> wp.getWorldTeamId() == null || wp.getWorldTeamId().isEmpty())
                            .map(wp -> SessionPlayer.cloneFromWorldPlayer(
                                wp.getWorldPlayerId(), wp.getName(),
                                wp.getPosition().toString(), wp.getAge(),
                                wp.calculateOverall(), null,
                                wp.getHeightCm(), wp.getSkillLevels()
                            ))
                            .collect(Collectors.toList());
                        return freePlayers;
                    })
                    .defaultIfEmpty(Collections.emptyList());
            }));
    }
}
