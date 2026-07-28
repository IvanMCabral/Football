package com.footballmanager.adapters.in.web.testharness.dto;

import com.footballmanager.domain.model.valueobject.TeamStyle;

/**
 *
 * <p>Accepts one of: {@code BALANCED}, {@code ATTACKING}, {@code DEFENSIVE},
 * {@code COUNTER}, {@code POSSESSION}. Null check is done imperatively in the
 * UseCase (returns 400 IAE).
 *
 * <p>Persists to {@code SessionTeam.style} so the detailed match engine reads the chosen
 * style via {@code MatchContextFactory.build()} when test-harness replays
 * a match (replay path doesn't pass an explicit style).
 */
public record SetStyleRequest(
    TeamStyle style
) {}
