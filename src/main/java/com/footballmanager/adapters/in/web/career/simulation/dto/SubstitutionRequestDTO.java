package com.footballmanager.adapters.in.web.career.simulation.dto;

/**
 * LIVE-MATCH-F1-POC: request body for POST /api/v1/match-engine/matches/{matchId}/substitutions.
 *
 * <p>The {@code teamId} is intentionally omitted — the service infers it by
 * searching the V24LiveSession's context for the {@code playerOffId} in
 * home/away starting lineups (or bench players, though those are unlikely
 * candidates to be subbed off). This keeps the API simple for the
 * single-match-detail view in the UI.
 *
 * <p>The {@code minute} is also nullable — the service ALWAYS overrides it
 * with {@code V24LiveSession.currentMinute()} (the authoritative server clock)
 * to avoid drift between client and server.
 */
public record SubstitutionRequestDTO(
    String playerOffId,
    String playerOnId,
    Integer minute
) {}
