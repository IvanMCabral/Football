package com.footballmanager.adapters.in.web.game.dto;

/**
 * Request para crear un nuevo Game/Career
 */
public record CreateGameRequest(
    String name,
    String teamId,
    String leagueId,
    String difficulty,
    String gameSpeed,
    Integer teamsPerDivision
) {}
