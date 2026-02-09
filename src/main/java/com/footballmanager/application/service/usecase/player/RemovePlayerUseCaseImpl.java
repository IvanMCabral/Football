package com.footballmanager.application.service.usecase.player;

import com.footballmanager.domain.model.entity.WorldPlayer;
import com.footballmanager.domain.model.entity.WorldSnapshot;
import com.footballmanager.domain.ports.in.player.RemovePlayerUseCase;
import com.footballmanager.application.service.world.WorldSnapshotService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Implementacion de RemovePlayerUseCase
 */
@Service
@RequiredArgsConstructor
public class RemovePlayerUseCaseImpl implements RemovePlayerUseCase {

    private final WorldSnapshotService snapshotService;

    @Override
    public Mono<WorldSnapshot> execute(UUID userId, String worldPlayerId) {
        return snapshotService.getSnapshot(userId)
                .flatMap(snapshot -> {
                    WorldPlayer player = snapshot.getWorldPlayer(worldPlayerId);
                    if (player == null) {
                        return Mono.error(new IllegalArgumentException(
                                "WorldPlayer no encontrado: " + worldPlayerId));
                    }

                    player.setWorldTeamId(null);

                    return snapshotService.saveSnapshot(snapshot);
                });
    }
}
