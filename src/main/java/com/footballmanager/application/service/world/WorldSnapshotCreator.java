package com.footballmanager.application.service.world;

import com.footballmanager.adapters.out.redis.RedisWorldRepository;
import com.footballmanager.domain.model.entity.WorldPlayer;
import com.footballmanager.domain.model.entity.WorldSnapshot;
import com.footballmanager.domain.model.entity.WorldTeam;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Crea el WorldSnapshot desde PostgreSQL.
 * Delega la carga de datos a LoadBaseDataService.
 */
@Service
@RequiredArgsConstructor
public class WorldSnapshotCreator {

    private final LoadBaseDataService loadBaseDataService;
    private final RedisWorldRepository redisWorldRepository;

    /**
     * Crea el WorldSnapshot desde cero.
     */
    public Mono<WorldSnapshot> create(UUID userId) {
        return loadBaseDataService.load(userId)
                .flatMap(base -> buildAndSaveSnapshot(userId, base));
    }

    private Mono<WorldSnapshot> buildAndSaveSnapshot(UUID userId, LoadBaseDataService.BaseDataResult base) {
        WorldSnapshot snapshot = new WorldSnapshot();
        snapshot.setUserId(userId);
        snapshot.setLeagues(base.leagues());

        // Teams map
        Map<String, WorldTeam> teamsMap = new HashMap<>();
        for (WorldTeam team : base.teams()) {
            teamsMap.put(team.getWorldTeamId(), team);
        }
        snapshot.setWorldTeams(teamsMap);

        // Players map
        Map<String, WorldPlayer> playersMap = new HashMap<>();
        for (WorldPlayer player : base.players()) {
            playersMap.put(player.getWorldPlayerId(), player);
        }
        snapshot.setWorldPlayers(playersMap);

        return redisWorldRepository.save(snapshot);
    }
}
