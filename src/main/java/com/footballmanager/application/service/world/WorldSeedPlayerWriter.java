package com.footballmanager.application.service.world;

import com.footballmanager.domain.model.entity.Player;
import com.footballmanager.domain.model.entity.WorldPlayer;

import java.util.List;
import java.util.function.Function;

public interface WorldSeedPlayerWriter {

    int upsertPlayersBatched(List<WorldPlayer> players,
                             Function<String, Player.Position> mapper);
}
