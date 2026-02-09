package com.footballmanager.application.service.world;

import com.footballmanager.adapters.out.redis.RedisWorldRepository;
import com.footballmanager.domain.model.entity.WorldPlayer;
import com.footballmanager.domain.model.entity.WorldSnapshot;
import com.footballmanager.domain.model.entity.WorldTeam;
import com.footballmanager.infrastructure.persistence.redis.LeagueTeamRedisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Actualiza el realLeagueId de los equipos base usando Redis.
 * Respeta el valor existente si Redis no tiene datos.
 */
@Service
@RequiredArgsConstructor
public class RealLeagueIdUpdater {

    private final RedisWorldRepository redisWorldRepository;
    private final LeagueTeamRedisRepository leagueTeamRedisRepository;

    /**
     * Resultado de actualizar realLeagueIds.
     */
    public record UpdateResult(WorldSnapshot snapshot, int updatedCount) {}

    /**
     * Actualiza realLeagueId para todos los equipos del snapshot.
     */
    public Mono<UpdateResult> update(UUID userId, WorldSnapshot snapshot) {
        List<WorldTeam> teams = new ArrayList<>(snapshot.getAllWorldTeams());

        return Flux.fromIterable(teams)
                .flatMap(team -> updateTeamLeagueId(userId, team))
                .collectList()
                .flatMap(updatedTeams -> {
                    WorldSnapshot updated = buildUpdatedSnapshot(snapshot, updatedTeams);
                    int count = countUpdates(teams, updatedTeams);
                    return redisWorldRepository.save(updated)
                            .map(saved -> new UpdateResult(saved, count));
                });
    }

    private Mono<WorldTeam> updateTeamLeagueId(UUID userId, WorldTeam team) {
        String worldTeamId = team.getWorldTeamId();

        try {
            UUID realTeamId = UUID.fromString(worldTeamId);

            return leagueTeamRedisRepository.findLeagueIdsByTeamId(userId, realTeamId)
                    .collectList()
                    .map(leagueIds -> {
                        UUID newLeagueId = leagueIds.isEmpty() ? null : leagueIds.get(0);
                        UUID oldLeagueId = team.getRealLeagueId();

                        // Preservar oldLeagueId si newLeagueId es null
                        UUID finalLeagueId = (newLeagueId == null && oldLeagueId != null)
                                ? oldLeagueId
                                : newLeagueId;

                        if (finalLeagueId == oldLeagueId) {
                            return team;
                        }

                        team.setRealLeagueId(finalLeagueId);
                        return team;
                    });
        } catch (IllegalArgumentException e) {
            return Mono.just(team);
        }
    }

    private WorldSnapshot buildUpdatedSnapshot(WorldSnapshot original, List<WorldTeam> updatedTeams) {
        WorldSnapshot updated = new WorldSnapshot();
        updated.setUserId(original.getUserId());
        updated.setLeagues(original.getLeagues());
        updated.setCreatedAt(original.getCreatedAt());

        Map<String, WorldTeam> teamsMap = updatedTeams.stream()
                .collect(Collectors.toMap(WorldTeam::getWorldTeamId, t -> t));
        updated.setWorldTeams(teamsMap);

        Map<String, WorldPlayer> playersMap = new HashMap<>(original.getWorldPlayers());
        updated.setWorldPlayers(playersMap);

        return updated;
    }

    private int countUpdates(List<WorldTeam> original, List<WorldTeam> updated) {
        int count = 0;
        for (int i = 0; i < original.size(); i++) {
            if (!Objects.equals(original.get(i).getRealLeagueId(), updated.get(i).getRealLeagueId())) {
                count++;
            }
        }
        return count;
    }
}
