package com.footballmanager.adapters.in.web.world.dto;

import java.util.UUID;

/**
 * Request para agregar equipo a liga
 */
public record AddTeamToLeagueRequest(UUID userId, String teamId) {}
