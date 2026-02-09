package com.footballmanager.adapters.in.web.career.dto.request;

/**
 * Request DTO for starting a new career.
 */
public record CareerStartRequest(
        String leagueId,
        String teamId,
        String difficulty,
        String gameSpeed,
        Integer teamsPerDivision
) {}
