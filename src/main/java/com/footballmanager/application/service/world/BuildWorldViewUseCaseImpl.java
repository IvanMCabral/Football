package com.footballmanager.application.service.world;

import com.footballmanager.domain.ports.out.world.WorldSnapshotRepository;
import com.footballmanager.domain.model.entity.WorldPlayer;
import com.footballmanager.domain.model.entity.WorldSnapshot;
import com.footballmanager.domain.model.entity.WorldTeam;
import com.footballmanager.domain.model.view.WorldView;
import com.footballmanager.domain.ports.in.query.BuildWorldViewUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.*;

/**
 * BuildWorldViewUseCase - Construye la vista del mundo.
 *
 * Responsibilities:
 * 1. Si existe snapshot en Redis, usarlo como base
 * 2. Si no existe, crearlo desde PostgreSQL (solo la primera vez)
 * 3. Actualizar realLeagueId de equipos desde Redis
 * 4. Retornar WorldView con todos los datos
 */
@Service
@RequiredArgsConstructor
public class BuildWorldViewUseCaseImpl implements BuildWorldViewUseCase {

    private final LoadBaseDataService loadBaseDataService;
    private final WorldSnapshotRepository WorldSnapshotRepository;
    private final RealLeagueIdUpdater realLeagueIdUpdater;

    @Override
    public Mono<WorldView> build(UUID userId) {
        return getOrCreateSnapshot(userId)
                .flatMap(snapshot -> updateRealLeagueIdsIfRequired(snapshot, userId))
                .map(this::buildWorldView)
                .onErrorResume(e -> {
                    return Mono.error(e);
                });
    }

    /**
     * A freshly assembled snapshot already carries the canonical league id on
     * every team.  Re-running the legacy per-team Redis reconciliation on every
     * read turned a simple world query into hundreds of remote round trips.  We
     * retain the repair path for incomplete/legacy snapshots, but keep the
     * normal read path local and bounded.
     */
    private Mono<WorldSnapshot> updateRealLeagueIdsIfRequired(WorldSnapshot snapshot, UUID userId) {
        boolean requiresRepair = snapshot.getAllWorldTeams().stream()
                .anyMatch(team -> team.getRealLeagueId() == null);
        return requiresRepair ? updateRealLeagueIds(snapshot, userId) : Mono.just(snapshot);
    }

    private Mono<WorldSnapshot> getOrCreateSnapshot(UUID userId) {
        return WorldSnapshotRepository.findByUserId(userId)
                .defaultIfEmpty(new WorldSnapshot())
                .flatMap(snapshot -> {
                    if (snapshot.getWorldTeams() == null || snapshot.getWorldTeams().isEmpty()) {
                        return loadBaseDataService.load(userId)
                                .flatMap(base -> createAndSaveSnapshot(userId, base));
                    }
                    return Mono.just(snapshot);
                });
    }

    private Mono<WorldSnapshot> createAndSaveSnapshot(UUID userId, LoadBaseDataService.BaseDataResult base) {
        WorldSnapshot newSnapshot = new WorldSnapshot();
        newSnapshot.setUserId(userId);
        newSnapshot.setLeagues(base.leagues());

        Map<String, WorldTeam> teamsMap = new HashMap<>();
        for (WorldTeam team : base.teams()) {
            teamsMap.put(team.getWorldTeamId(), team);
        }
        newSnapshot.setWorldTeams(teamsMap);

        Map<String, WorldPlayer> playersMap = new HashMap<>();
        for (WorldPlayer player : base.players()) {
            playersMap.put(player.getWorldPlayerId(), player);
        }
        newSnapshot.setWorldPlayers(playersMap);

        return WorldSnapshotRepository.saveInitial(newSnapshot);
    }

    private Mono<WorldSnapshot> updateRealLeagueIds(WorldSnapshot snapshot, UUID userId) {
        return realLeagueIdUpdater.update(userId, snapshot)
                .map(RealLeagueIdUpdater.UpdateResult::snapshot);
    }

    private WorldView buildWorldView(WorldSnapshot snapshot) {
        List<WorldTeam> allTeams = snapshot.getAllWorldTeams();
        List<WorldPlayer> allPlayers = new ArrayList<>(snapshot.getWorldPlayers().values());

        return new WorldView(
                snapshot.getUserId(),
                snapshot.getLeagues(),
                allTeams,
                allPlayers,
                new HashMap<>()
        );
    }
}

