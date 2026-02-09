package com.footballmanager.adapters.in.web.league.dto;

import java.util.UUID;

/**
 * Request para agregar un equipo a una liga
 */
public record AddTeamToLeagueRequest(
        UUID userId,
        UUID teamId
) {}
