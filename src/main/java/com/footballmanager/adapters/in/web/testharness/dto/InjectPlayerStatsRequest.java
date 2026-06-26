package com.footballmanager.adapters.in.web.testharness.dto;

import com.footballmanager.domain.model.valueobject.PlayerSkill;

import java.util.Map;

/**
 * V25D29 — request body for {@code POST /api/v1/test-harness/career/inject-player-stats}.
 *
 * <p>Mutates the persisted {@code SessionPlayer.attack/defense/technique/speed/
 * stamina/mentality} stats in the current career. All stat fields are nullable;
 * a null field leaves that stat unchanged (use to update only specific stats
 * without re-sending all 6). Bounds-checked to {@code [0, 99]} in the UseCase.
 *
 * <p><b>V25D35 extension:</b> adds two optional fields so callers can also
 * mutate the physical + skill metadata introduced in V25D31:
 * <ul>
 *   <li>{@code heightCm} — physical height, nullable, bounds-checked to
 *       {@code [160, 210]} in the UseCase (matches {@code SessionPlayer.setHeightCm}).
 *       Null = leave current value unchanged (sparse semantics).</li>
 *   <li>{@code skillLevels} — sparse {@code Map<PlayerSkill, Integer>} of skill
 *       levels. Each entry is bounds-checked to {@code [0, 99]} (matches
 *       {@code SessionPlayer.setSkillLevel}). Null OR empty map = no-op (does
 *       NOT clear existing skills). Setting a skill to {@code 0} via the map
 *       removes that entry from the sparse map (bit-a-bit consistent with
 *       {@code SessionPlayer.setSkillLevel(skill, 0)}).</li>
 * </ul>
 *
 * <p><b>Backward-compat:</b> callers built against V25D29 (only 6 stats + playerId)
 * keep working bit-a-bit — {@code heightCm} and {@code skillLevels} default to
 * {@code null} via the canonical constructor, and the UseCase treats both as
 * "leave unchanged". Jackson auto-deserializes the new fields if present;
 * absent = null.
 *
 * <p>Persists to Redis via RedisCareerRepository. Cache invalidation follows
 * the same pattern as set-formation and set-style.
 *
 * <p>Side-quest of V25D27 REVISOR smoke: enables empirical validation of
 * Axis 2 (player stats amplify formation effect) without having to set up a
 * fresh career with different seeded squads. The V25D35 height + skills
 * fields extend this to also validate the new V25D31-V25D34 skill impacts
 * (HEADER, AERIAL, MARKER, TACKLER, SPEEDSTER, etc.) without modifying the
 * seed.
 */
public record InjectPlayerStatsRequest(
    String playerId,
    Integer attack,
    Integer defense,
    Integer technique,
    Integer speed,
    Integer stamina,
    Integer mentality,
    // V25D35 — physical + skill metadata (nullable = leave unchanged)
    Integer heightCm,
    Map<PlayerSkill, Integer> skillLevels
) {}