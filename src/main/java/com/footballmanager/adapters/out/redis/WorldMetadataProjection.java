package com.footballmanager.adapters.out.redis;

import com.footballmanager.domain.model.entity.*;
import com.footballmanager.domain.model.valueobject.Division;
import com.footballmanager.domain.model.valueobject.WorldMetadata;
import java.math.BigDecimal;
import java.util.*;

/** Immutable scalar values only; each caller receives fresh domain objects. */
final class WorldMetadataProjection {
    private record League(UUID id, String name, String country, Integer tier) {}
    private record Team(String id, UUID realId, UUID league, String name, String country, String city,
                        BigDecimal budget, String formation, WorldTeam.WorldTeamOrigin origin, Division division) {}
    private final List<League> leagues;
    private final Map<String, Team> teams;

    private WorldMetadataProjection(List<League> leagues, Map<String, Team> teams) {
        this.leagues = leagues == null ? null : Collections.unmodifiableList(leagues);
        this.teams = Collections.unmodifiableMap(teams);
    }

    static WorldMetadataProjection from(WorldSnapshot snapshot) {
        List<League> leagues = snapshot.getLeagues() == null ? null : new ArrayList<>();
        if (leagues != null) for (WorldLeague league : snapshot.getLeagues()) {
            leagues.add(league == null ? null : new League(league.getRealLeagueId(), league.getName(), league.getCountry(), league.getTier()));
        }
        Map<String, Team> teams = new LinkedHashMap<>();
        if (snapshot.getWorldTeams() != null) snapshot.getWorldTeams().forEach((key, team) -> teams.put(key,
                team == null ? null : new Team(team.getWorldTeamId(), team.getRealTeamId(), team.getRealLeagueId(),
                        team.getName(), team.getCountry(), team.getCity(), team.getBaseBudget(), team.getBaseFormation(), team.getOrigin(), team.getDivision())));
        return new WorldMetadataProjection(leagues, teams);
    }

    WorldSnapshot materializeSnapshot() {
        WorldSnapshot snapshot = new WorldSnapshot();
        snapshot.setLeagues(leagues == null ? null : leagues.stream().map(league -> league == null ? null
                : new WorldLeague(league.id(), league.name(), league.country(), league.tier())).toList());
        Map<String, WorldTeam> result = new LinkedHashMap<>();
        teams.forEach((key, value) -> {
            if (value == null) { result.put(key, null); return; }
            WorldTeam team = new WorldTeam();
            team.setWorldTeamId(value.id()); team.setRealTeamId(value.realId()); team.setRealLeagueId(value.league());
            team.setName(value.name()); team.setCountry(value.country()); team.setCity(value.city());
            team.setBaseBudget(value.budget()); team.setBaseFormation(value.formation());
            team.setOrigin(value.origin()); team.setDivision(value.division());
            result.put(key, team);
        });
        snapshot.setWorldTeams(result);
        return snapshot;
    }

    WorldMetadata materialize() { return WorldMetadata.from(materializeSnapshot()); }
}
