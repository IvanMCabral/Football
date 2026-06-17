package com.footballmanager.adapters.in.web.career.simulation.dto;

import com.footballmanager.application.service.domain.TeamStyle;

/**
 * LIVE-MATCH-F2-LIVE F5 (B4): request body for
 * {@code POST /api/v1/match-engine/matches/{matchId}/style}.
 *
 * <p>The client sends the desired tactical style. The service applies it via
 * {@code V24MatchContext.withNewStyle(teamId, newStyle)} +
 * {@code V24LiveSession.replayFromMinute(currentMinute)}.
 *
 * <p>Validation: {@code newStyle} is required (NOT NULL). Style itself is an
 * enum, so Jackson rejects unknown values at deserialization.
 */
public record StyleChangeRequestDTO(
    TeamStyle newStyle
) {}
