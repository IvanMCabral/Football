package com.footballmanager.domain.model.valueobject;

import com.footballmanager.domain.model.entity.WorldLeague;
import com.footballmanager.domain.model.entity.WorldTeam;
import com.footballmanager.domain.model.entity.WorldSnapshot;
import java.util.ArrayList;
import java.util.List;

/** Query projection: leagues and clubs, with no player graph exposed. */
public record WorldMetadata(List<WorldLeague> leagues, List<WorldTeam> teams) {
    public static WorldMetadata from(WorldSnapshot snapshot) {
        return new WorldMetadata(snapshot.getLeagues() == null ? null : new ArrayList<>(snapshot.getLeagues()),
                snapshot.getWorldTeams() == null ? List.of() : new ArrayList<>(snapshot.getWorldTeams().values()));
    }
}
