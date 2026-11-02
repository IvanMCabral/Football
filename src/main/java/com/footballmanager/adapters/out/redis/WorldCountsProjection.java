package com.footballmanager.adapters.out.redis;

import com.footballmanager.domain.model.entity.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

/** Immutable identities from a validated catalog. Never retains mutable domain objects.
 * Fresh minimal objects let the existing overlay enforce all identity/reference rules.
 */
final class WorldCountsProjection {
    private record Player(String key, String id, UUID realId, String teamId, WorldPlayer.WorldPlayerOrigin origin) {}
    private final WorldMetadataProjection metadata;
    private final List<Player> players;

    private WorldCountsProjection(WorldMetadataProjection metadata, List<Player> players) {
        this.metadata = metadata;
        this.players = List.copyOf(players);
    }

    static WorldCountsProjection from(WorldSnapshot catalog) {
        List<Player> players = new ArrayList<>();
        if (catalog.getWorldPlayers() != null) catalog.getWorldPlayers().forEach((key, player) -> {
            if (player != null) players.add(new Player(key, player.getWorldPlayerId(), player.getRealPlayerId(),
                    player.getWorldTeamId(), player.getOrigin()));
        });
        return new WorldCountsProjection(WorldMetadataProjection.from(catalog), players);
    }

    WorldSnapshot materialize() {
        WorldSnapshot snapshot = metadata.materializeSnapshot();
        var playerMap = new LinkedHashMap<String, WorldPlayer>();
        for (Player identity : players) {
            WorldPlayer player = new WorldPlayer();
            player.setWorldPlayerId(identity.id());
            player.setRealPlayerId(identity.realId());
            player.setWorldTeamId(identity.teamId());
            player.setOrigin(identity.origin());
            playerMap.put(identity.key(), player);
        }
        snapshot.setWorldPlayers(playerMap);
        return snapshot;
    }
}
