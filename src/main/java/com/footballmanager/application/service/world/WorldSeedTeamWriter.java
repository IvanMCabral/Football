package com.footballmanager.application.service.world;

import com.footballmanager.domain.model.entity.WorldTeam;

import java.util.List;
import java.util.UUID;

public interface WorldSeedTeamWriter {

    void upsertTeams(List<WorldTeam> teams, UUID leagueId, String logPrefix);
}
