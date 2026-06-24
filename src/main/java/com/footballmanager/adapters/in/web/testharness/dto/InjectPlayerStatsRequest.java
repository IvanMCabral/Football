package com.footballmanager.adapters.in.web.testharness.dto;

/**
 * V25D29 — request body for {@code POST /api/v1/test-harness/career/inject-player-stats}.
 *
 * <p>Mutates the persisted {@code SessionPlayer.attack/defense/technique/speed/
 * stamina/mentality} stats in the current career. All stat fields are nullable;
 * a null field leaves that stat unchanged (use to update only specific stats
 * without re-sending all 6). Bounds-checked to {@code [0, 99]} in the UseCase.
 *
 * <p>Persists to Redis via RedisCareerRepository. Cache invalidation follows
 * the same pattern as set-formation and set-style.
 *
 * <p>Side-quest of V25D27 REVISOR smoke: enables empirical validation of
 * Axis 2 (player stats amplify formation effect) without having to set up a
 * fresh career with different seeded squads.
 */
public record InjectPlayerStatsRequest(
    String playerId,
    Integer attack,
    Integer defense,
    Integer technique,
    Integer speed,
    Integer stamina,
    Integer mentality
) {}
