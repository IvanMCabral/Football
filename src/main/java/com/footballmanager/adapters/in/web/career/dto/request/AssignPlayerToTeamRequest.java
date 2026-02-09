package com.footballmanager.adapters.in.web.career.dto.request;

/**
 * Request DTO for assigning a player to a session team.
 */
public record AssignPlayerToTeamRequest(
        String sessionPlayerId
) {}
