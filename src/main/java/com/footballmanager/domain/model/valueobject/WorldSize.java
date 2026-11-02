package com.footballmanager.domain.model.valueobject;

import com.footballmanager.domain.model.entity.WorldSnapshot;

public record WorldSize(int clubs, int players, int leagues) {
    public static WorldSize from(WorldSnapshot snapshot) {
        return new WorldSize(snapshot.getWorldTeams() == null ? 0 : snapshot.getWorldTeams().size(),
                snapshot.getWorldPlayers() == null ? 0 : snapshot.getWorldPlayers().size(),
                snapshot.getLeagues() == null ? 0 : snapshot.getLeagues().size());
    }

    public boolean incomplete() { return clubs == 0 || players == 0 || leagues == 0; }
}
