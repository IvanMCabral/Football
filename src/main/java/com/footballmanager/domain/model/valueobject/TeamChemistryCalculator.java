package com.footballmanager.domain.model.valueobject;

import com.footballmanager.domain.model.entity.SessionPlayer;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * V25D41 (Sprint C6): shared team-chemistry calculator for the on-pitch
 * lineup (typically the starting 11 of a SessionTeam).
 *
 * <p>Aggregates individual {@link SessionPlayer#calculateOverall()} values
 * and the 10 {@link PlayerSkill} levels across the lineup into a single
 * chemistry score in {@code [0, 99]}, returned inside a {@link ChemistryDetail}
 * record alongside the per-position-group breakdown needed by the UI.
 *
 * <h2>Formula (Opción C — mixto, refined; unchanged in V25D43)</h2>
 *
 * <p>Each component has a clear mathematical purpose:
 *
 * <pre>
 *   base          = round(AVG(player.calculateOverall()))                 // ~80 for typical lineups
 *   skill_bonus   = round((Σ maxPerSkill[skill] * teamWeight[skill]) / 10)  // bounded ~0..6
 *   coverage_bonus = round(count(skills with maxPerSkill >= 80) * 0.3)      // bounded 0..3
 *   total         = clamp(0, 99, base + skill_bonus + coverage_bonus)
 * </pre>
 *
 * <h3>Why mixto and not pure-AVG (Opción B)?</h3>
 * <p>Opción B (AVG overalls + small skills bonus) is too monotonous — the
 * score barely moves when skills are injected because the AVG is dominated
 * by the 6 base stats. Mixto separates "raw talent" (base) from "special
 * ability" (skill_bonus + coverage_bonus) so each dimension is observable.
 *
 * <h3>Why mixto and not pure-MAX (Opción A)?</h3>
 * <p>Opción A captures "has a star player with X skill" but doesn't reward
 * <b>depth</b> — a team of 11 superstars in different skills gets the same
 * score as a team of 1 superstar + 10 fillers. The coverage_bonus component
 * addresses this by counting <em>how many</em> distinct skills have a
 * strong representation.
 *
 * <h3>Skill weight table (sum ~0.65, max bonus ≈ 6.4)</h3>
 * <p>These are the <b>column averages</b> of the per-position weight tables
 * in {@link OverallCalculator} (which sum to 0.65 per row). Mathematically
 * consistent with the per-position weights — same upper bound, same
 * rounding behavior.
 *
 * <table>
 *   <tr><th>Skill</th><th>Team weight</th><th>Rationale</th></tr>
 *   <tr><td>PASSER</td><td>0.09</td><td>Most universal (present in 4/5 positions)</td></tr>
 *   <tr><td>AERIAL</td><td>0.07</td><td>Important for both GK and DEF</td></tr>
 *   <tr><td>TACKLER</td><td>0.07</td><td>Important for GK, DEF, MID</td></tr>
 *   <tr><td>DRIBBLER</td><td>0.07</td><td>WINGER + ATT, high impact</td></tr>
 *   <tr><td>SPEEDSTER</td><td>0.07</td><td>WINGER + ATT</td></tr>
 *   <tr><td>WALL</td><td>0.06</td><td>GK only but very high impact</td></tr>
 *   <tr><td>MARKER</td><td>0.06</td><td>DEF + MID coverage</td></tr>
 *   <tr><td>PLAYMAKER</td><td>0.06</td><td>MID only but high impact</td></tr>
 *   <tr><td>SHOOTER</td><td>0.06</td><td>WINGER + ATT finishing</td></tr>
 *   <tr><td>HEADER</td><td>0.04</td><td>ATT only, niche</td></tr>
 *   <tr><th>Sum</th><th>0.65</th><th>matches OverallCalculator per-row sums</th></tr>
 * </table>
 *
 * <h3>Coverage bonus threshold</h3>
 * <p>A skill "counts" as covered when at least one player in the lineup has
 * that skill at level &ge; 80. The threshold of 80 matches the V25D39
 * convention (skills &ge; 80 are considered "elite" — the engine treats
 * them as the meaningful tier above the noise floor). Coverage bonus is
 * bounded at 3.0 (10 skills * 0.3), so even a perfect-coverage lineup
 * gains only 3 points — small enough not to dominate the base, large
 * enough to be observable.
 *
 * <h2>V25D43 (Sprint C8) — Opción B signature change</h2>
 * <p>The public {@code calculate(List<SessionPlayer>)} method now returns a
 * {@link ChemistryDetail} record instead of a bare {@code int}. The score
 * is accessible via {@code detail.score()}. This is a breaking change for
 * any caller — the 2 build sites in {@code LineupQueryUseCaseImpl} and
 * {@code LineupCommandUseCaseImpl} have been updated, and the existing
 * 29 tests in {@code TeamChemistryCalculatorTest} have been adjusted
 * (each {@code assertEquals(int, calculate(lineup))} now calls
 * {@code .score()}).
 *
 * <h2>Backward compatibility</h2>
 * <p>If the list is null, empty, or contains only null players → returns a
 * detail with score=0, all groups empty, all maxSkillByType=0, and
 * coveragePercentage=0. If all players lack skill data (legacy V25D31
 * seed lineups) → score is the AVG of overalls only, with no skill or
 * coverage bonuses, and an empty breakdown.
 */
public final class TeamChemistryCalculator {

    /** Threshold above which a skill is considered "elite" for coverage bonus. */
    private static final int COVERAGE_THRESHOLD = 80;

    /** Coverage bonus weight per covered skill. */
    private static final double COVERAGE_BONUS_PER_SKILL = 0.3;

    private TeamChemistryCalculator() {
        // Pure utility — no instances.
    }

    /**
     * Calculates the team chemistry detail (score + per-position-group
     * breakdown) for a list of on-pitch players. Replaces the V25D41
     * {@code int}-returning {@code calculate} — score is now in
     * {@code detail.score()}.
     *
     * @param players the lineup (typically 11 SessionPlayer, but accepts any size)
     * @return chemistry detail (never {@code null}). For null/empty/all-null
     *         lineups → score=0, all groups empty, all maxSkillByType=0,
     *         coveragePercentage=0.
     */
    public static ChemistryDetail calculate(List<SessionPlayer> players) {
        // Backward compat: null/empty/all-null → empty detail, score=0.
        if (players == null || players.isEmpty()) {
            return emptyDetail();
        }
        List<SessionPlayer> valid = players.stream()
                .filter(Objects::nonNull)
                .toList();
        if (valid.isEmpty()) {
            return emptyDetail();
        }

        Map<PlayerSkill, Integer> maxPerSkill = computeMaxPerSkill(valid);
        Map<PlayerSkill, String> contributors = computeContributors(valid, maxPerSkill);

        int base = computeAverageOverall(valid);
        int skillBonus = computeSkillBonus(maxPerSkill);
        int coverageBonus = computeCoverageBonus(maxPerSkill);

        int total = clampScore(base + skillBonus + coverageBonus);

        Map<ChemistryDetail.PositionGroup, List<ChemistryDetail.SkillCoverage>> breakdown =
                computeBreakdown(maxPerSkill, contributors);

        Map<PlayerSkill, Integer> maxSkillByType = new EnumMap<>(maxPerSkill);
        // Fill in 0 for absent skills so the response shape is stable.
        for (PlayerSkill s : PlayerSkill.values()) {
            maxSkillByType.putIfAbsent(s, 0);
        }

        int coveragePercentage = computeCoveragePercentage(maxPerSkill);

        return new ChemistryDetail(total, breakdown, maxSkillByType, coveragePercentage);
    }

    /** Empty ChemistryDetail for null/empty/all-null lineups. */
    private static ChemistryDetail emptyDetail() {
        Map<ChemistryDetail.PositionGroup, List<ChemistryDetail.SkillCoverage>> emptyBreakdown =
                new EnumMap<>(ChemistryDetail.PositionGroup.class);
        for (ChemistryDetail.PositionGroup g : ChemistryDetail.PositionGroup.values()) {
            emptyBreakdown.put(g, List.of());
        }
        Map<PlayerSkill, Integer> zeroMaxSkillByType = new EnumMap<>(PlayerSkill.class);
        for (PlayerSkill s : PlayerSkill.values()) {
            zeroMaxSkillByType.put(s, 0);
        }
        return new ChemistryDetail(0, emptyBreakdown, zeroMaxSkillByType, 0);
    }

    /** Clamp a score into {@code [0, 99]}. */
    private static int clampScore(int v) {
        return Math.max(0, Math.min(99, v));
    }

    /** Rounded average of the 11 individual overalls. */
    private static int computeAverageOverall(List<SessionPlayer> players) {
        long sum = 0;
        for (SessionPlayer p : players) {
            Integer overall = p.calculateOverall();
            sum += (overall != null ? overall : 0);
        }
        // round-half-up: long division + 0.5 trick via double division
        return (int) Math.round((double) sum / players.size());
    }

    /**
     * Skill bonus: sum over each of the 10 PlayerSkill values of
     * {@code maxPerSkill * teamWeight[skill] / 10}, rounded.
     *
     * <p>maxPerSkill is the highest level of that skill across the lineup
     * (treating absent skills as 0). Defensively clamps skill levels to
     * {@code [0, 99]} in case of legacy data with out-of-bounds values.
     */
    private static int computeSkillBonus(Map<PlayerSkill, Integer> maxPerSkill) {
        double weightedSum = 0.0;
        for (Map.Entry<PlayerSkill, Integer> entry : maxPerSkill.entrySet()) {
            Double weight = TEAM_SKILL_WEIGHTS.get(entry.getKey());
            if (weight == null) continue;
            int clamped = Math.max(0, Math.min(99, entry.getValue()));
            weightedSum += clamped * weight;
        }
        return (int) Math.round(weightedSum / 10.0);
    }

    /**
     * Coverage bonus: number of skills (out of 10) where at least one player
     * has level &ge; {@link #COVERAGE_THRESHOLD}, multiplied by
     * {@link #COVERAGE_BONUS_PER_SKILL} and rounded. Bounded at 3.0.
     */
    private static int computeCoverageBonus(Map<PlayerSkill, Integer> maxPerSkill) {
        int covered = 0;
        for (Integer level : maxPerSkill.values()) {
            if (level != null && level >= COVERAGE_THRESHOLD) {
                covered++;
            }
        }
        return (int) Math.round(covered * COVERAGE_BONUS_PER_SKILL);
    }

    /**
     * For each of the 10 PlayerSkill values, find the highest level across
     * the lineup. Absent skills (null map or no entry) count as 0.
     */
    private static Map<PlayerSkill, Integer> computeMaxPerSkill(List<SessionPlayer> players) {
        Map<PlayerSkill, Integer> max = new EnumMap<>(PlayerSkill.class);
        for (SessionPlayer p : players) {
            if (p == null) continue;
            Map<PlayerSkill, Integer> skills = p.getSkillLevels();
            if (skills == null || skills.isEmpty()) continue;
            for (Map.Entry<PlayerSkill, Integer> entry : skills.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) continue;
                int clamped = Math.max(0, Math.min(99, entry.getValue()));
                Integer current = max.get(entry.getKey());
                if (current == null || clamped > current) {
                    max.put(entry.getKey(), clamped);
                }
            }
        }
        return max;
    }

    /**
     * For each skill with a positive max level, find the first player in
     * iteration order with that exact max. Deterministic — same lineup
     * yields the same contributors.
     */
    private static Map<PlayerSkill, String> computeContributors(
            List<SessionPlayer> players, Map<PlayerSkill, Integer> maxPerSkill) {
        Map<PlayerSkill, String> contributors = new EnumMap<>(PlayerSkill.class);
        for (Map.Entry<PlayerSkill, Integer> entry : maxPerSkill.entrySet()) {
            if (entry.getValue() == null || entry.getValue() <= 0) continue;
            PlayerSkill skill = entry.getKey();
            int target = entry.getValue();
            for (SessionPlayer p : players) {
                if (p == null) continue;
                Map<PlayerSkill, Integer> skills = p.getSkillLevels();
                if (skills == null) continue;
                Integer level = skills.get(skill);
                if (level != null && level == target) {
                    contributors.put(skill, p.getSessionPlayerId());
                    break;
                }
            }
        }
        return contributors;
    }

    /**
     * Build the per-position-group breakdown: for each {@link ChemistryDetail.PositionGroup},
     * the list of {@link ChemistryDetail.SkillCoverage} for skills present
     * in the lineup (maxLevel &gt; 0) that have non-zero weight in that
     * group. Order within a group follows {@link PlayerSkill#values()}
     * declaration order — deterministic for the same input.
     */
    private static Map<ChemistryDetail.PositionGroup, List<ChemistryDetail.SkillCoverage>> computeBreakdown(
            Map<PlayerSkill, Integer> maxPerSkill,
            Map<PlayerSkill, String> contributors) {
        Map<ChemistryDetail.PositionGroup, List<ChemistryDetail.SkillCoverage>> result =
                new EnumMap<>(ChemistryDetail.PositionGroup.class);
        for (ChemistryDetail.PositionGroup g : ChemistryDetail.PositionGroup.values()) {
            result.put(g, new ArrayList<>());
        }
        for (PlayerSkill skill : PlayerSkill.values()) {
            Integer level = maxPerSkill.get(skill);
            if (level == null || level <= 0) continue;  // skill absent
            String contributor = contributors.get(skill);
            for (ChemistryDetail.PositionGroup g : ChemistryDetail.groupsForSkill(skill)) {
                result.get(g).add(new ChemistryDetail.SkillCoverage(skill, level, contributor));
            }
        }
        // Freeze for immutability (matches the record contract).
        Map<ChemistryDetail.PositionGroup, List<ChemistryDetail.SkillCoverage>> frozen =
                new EnumMap<>(ChemistryDetail.PositionGroup.class);
        for (Map.Entry<ChemistryDetail.PositionGroup, List<ChemistryDetail.SkillCoverage>> e : result.entrySet()) {
            frozen.put(e.getKey(), List.copyOf(e.getValue()));
        }
        return frozen;
    }

    /**
     * Coverage percentage: {@code round(covered / 10 * 100)} where
     * {@code covered} is the number of skills with maxLevel &ge; 80.
     * Range {@code [0, 100]}.
     */
    private static int computeCoveragePercentage(Map<PlayerSkill, Integer> maxPerSkill) {
        long covered = maxPerSkill.values().stream()
                .filter(v -> v != null && v >= COVERAGE_THRESHOLD)
                .count();
        return (int) Math.round(covered * 100.0 / PlayerSkill.values().length);
    }

    /**
     * Per-skill team-level weight. Column averages of the per-position
     * weight tables in {@link OverallCalculator} (which sum to 0.65 per
     * position row). Sum: 0.65 — same upper bound as the per-position
     * tables for mathematical consistency.
     */
    private static final Map<PlayerSkill, Double> TEAM_SKILL_WEIGHTS = Map.ofEntries(
            Map.entry(PlayerSkill.PASSER,    0.09),
            Map.entry(PlayerSkill.AERIAL,    0.07),
            Map.entry(PlayerSkill.TACKLER,   0.07),
            Map.entry(PlayerSkill.DRIBBLER,  0.07),
            Map.entry(PlayerSkill.SPEEDSTER, 0.07),
            Map.entry(PlayerSkill.WALL,      0.06),
            Map.entry(PlayerSkill.MARKER,    0.06),
            Map.entry(PlayerSkill.PLAYMAKER, 0.06),
            Map.entry(PlayerSkill.SHOOTER,   0.06),
            Map.entry(PlayerSkill.HEADER,    0.04)
    );

    static {
        // Sanity: weights must sum to ~0.65 (consistency invariant with OverallCalculator).
        double sum = TEAM_SKILL_WEIGHTS.values().stream().mapToDouble(Double::doubleValue).sum();
        if (Math.abs(sum - 0.65) > 1e-6) {
            throw new IllegalStateException(
                "TEAM_SKILL_WEIGHTS must sum to 0.65, got " + sum);
        }
    }
}
