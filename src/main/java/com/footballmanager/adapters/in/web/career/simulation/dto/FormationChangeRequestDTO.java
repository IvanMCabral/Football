package com.footballmanager.adapters.in.web.career.simulation.dto;

import java.util.List;

/**
 * LIVE-MATCH-F2-LIVE F5 (B4): request body for
 * {@code POST /api/v1/match-engine/matches/{matchId}/formation}.
 *
 * <p>The client sends the full formation (10-11 slots). The service validates
 * (per F5 spec D-formation: 10-11 players, exactly 1 GK, unique slots) and
 * applies via {@code V24MatchContext.withNewFormation(teamId, newFormation)}.
 *
 * <p>For pragmatic client ergonomics the request carries slots directly
 * (not a pre-parsed formation code) so the F3 UI can let the manager drag
 * players between slots without first resolving a formation code.
 */
public record FormationChangeRequestDTO(
    List<FormationSlotDTO> players
) {}
