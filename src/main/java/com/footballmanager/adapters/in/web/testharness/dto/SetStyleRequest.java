package com.footballmanager.adapters.in.web.testharness.dto;

import com.footballmanager.application.service.domain.TeamStyle;

/**
 * V25D28 — request body for {@code POST /api/v1/test-harness/career/set-style}.
 *
 * <p>Accepts one of: {@code BALANCED}, {@code ATTACKING}, {@code DEFENSIVE},
 * {@code COUNTER}, {@code POSSESSION}. Null check is done imperatively in the
 * UseCase (returns 400 IAE).
 *
 * <p>Persists to {@code SessionTeam.style} so the V24 engine reads the chosen
 * style via {@code V24MatchContextFactory.build()} when test-harness replays
 * a match (replay path doesn't pass an explicit style).
 */
public record SetStyleRequest(
    TeamStyle style
) {}
