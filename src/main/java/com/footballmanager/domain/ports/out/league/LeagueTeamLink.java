package com.footballmanager.domain.ports.out.league;

import java.util.UUID;

public record LeagueTeamLink(UUID leagueId, UUID teamId) {
}
