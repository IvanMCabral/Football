package com.footballmanager.domain.model.valueobject;

import java.util.List;
import java.util.Map;

/**
 * V25D43 (Sprint C8): rich chemistry detail returned by
 * {@link TeamChemistryCalculator#calculate(java.util.List)}.
 *
 * <p>Wraps the aggregated score (V25D41) plus three new observables that
 * give the UI enough information to render a per-position-group breakdown
 * of which skills are present in the lineup, at what level, and which
 * player is the "carrier" of each skill.
 *
 * <h2>Fields</h2>
 * <ul>
 *   <li>{@code score} — same {@code [0, 99]} scalar as before. The compact
 *       badge in the UI continues to consume this.</li>
 *   <li>{@code breakdown} — per {@link PositionGroup} list of
 *       {@link SkillCoverage} entries, one per skill that is <b>present</b>
 *       in the lineup (max level &gt; 0). A skill can appear in multiple
 *       groups if it has non-zero weight in those groups' per-position
 *       tables (see {@link OverallCalculator#skillWeightsFor} via
 *       {@link #groupsForSkill(PlayerSkill)}). Absent skills are NOT
 *       emitted — the map can be empty for a group when the lineup has no
 *       players with skills weighted to that group.</li>
 *   <li>{@code maxSkillByType} — for each of the 10 {@link PlayerSkill}
 *       values, the max level across the lineup (or {@code 0} if absent).
 *       Useful for tooltips / hover cards.</li>
 *   <li>{@code coveragePercentage} — {@code 0..100} percentage of skills
 *       (out of 10) whose max level is &ge; 80 ("elite" threshold
 *       consistent with V25D39 + V25D41 coverage bonus).</li>
 * </ul>
 *
 * <h2>PositionGroup enum (4 values)</h2>
 * <p>Sparse view of the 5 {@code SessionPlayer.position} categories
 * (GK/DEF/MID/WINGER/ATT). WINGER skills are folded into ATT (the
 * closest offensive group) because:
 * <ol>
 *   <li>The task spec ({@code team-compute skill breakdown}) lists only
 *       4 groups.</li>
 *   <li>WINGER's 4 weighted skills (SPEEDSTER 0.25, DRIBBLER 0.25,
 *       PASSER 0.10, SHOOTER 0.05) all have non-zero weight in ATT for
 *       at least one of them, and ATT is the offensive anchor.</li>
 *   <li>Folding keeps the UI's group count stable and avoids surprising
 *       users with a 5th "WINGER" row when the per-position tables are
 *       already 5 groups.</li>
 * </ol>
 *
 * <h2>Backward compatibility</h2>
 * <p>If the lineup is null/empty/all-null, returns a {@code ChemistryDetail}
 * with score=0, all groups empty, all maxSkillByType=0, and
 * coveragePercentage=0. The "no lineup" state is preserved as in V25D41.
 *
 * @param score chemistry score in {@code [0, 99]}
 * @param breakdown per-group list of skills present in the lineup
 * @param maxSkillByType per-skill max level across the lineup (0 if absent)
 * @param coveragePercentage {@code 0..100} — share of skills with max ≥ 80
 */
public record ChemistryDetail(
    int score,
    Map<PositionGroup, List<SkillCoverage>> breakdown,
    Map<PlayerSkill, Integer> maxSkillByType,
    int coveragePercentage
) {

    /**
     * Sparse view of {@code SessionPlayer.position} for the breakdown.
     * 4 values — WINGER folded into ATT (see class Javadoc).
     */
    public enum PositionGroup { GK, DEF, MID, ATT }

    /**
     * One skill's presence in the lineup.
     *
     * @param skill the skill
     * @param maxLevel max level across the lineup (clamped to {@code [0, 99]})
     * @param contributorPlayerId {@code sessionPlayerId} of the player with
     *        the max level for this skill. Tie-break: first player in
     *        iteration order wins (deterministic for the same lineup).
     *        {@code null} if no player has the skill (shouldn't happen for
     *        entries that are emitted, kept nullable for defensive safety).
     */
    public record SkillCoverage(
        PlayerSkill skill,
        int maxLevel,
        String contributorPlayerId
    ) {}

    /**
     * Returns the {@link PositionGroup}s where {@code skill} has a non-zero
     * weight in the per-position tables of {@link OverallCalculator}.
     *
     * <p>The mapping is derived from {@link OverallCalculator#skillWeightsFor}
     * (which returns the per-position weight map):
     *
     * <table>
     *   <tr><th>Skill</th><th>Primary weight</th><th>PositionGroup(s)</th></tr>
     *   <tr><td>WALL</td><td>0.30 (GK)</td><td>GK</td></tr>
     *   <tr><td>AERIAL</td><td>0.15 (GK) / 0.20 (DEF)</td><td>GK, DEF</td></tr>
     *   <tr><td>MARKER</td><td>0.25 (DEF) / 0.05 (MID)</td><td>DEF, MID</td></tr>
     *   <tr><td>TACKLER</td><td>0.10 (GK) / 0.15 (DEF) / 0.10 (MID)</td><td>GK, DEF, MID</td></tr>
     *   <tr><td>PLAYMAKER</td><td>0.30 (MID)</td><td>MID</td></tr>
     *   <tr><td>PASSER</td><td>0.10 (GK) / 0.05 (DEF) / 0.20 (MID) / 0.10 (WINGER)</td><td>GK, DEF, MID</td></tr>
     *   <tr><td>SPEEDSTER</td><td>0.25 (WINGER) / 0.10 (ATT)</td><td>ATT (WINGER folded)</td></tr>
     *   <tr><td>DRIBBLER</td><td>0.25 (WINGER) / 0.10 (ATT)</td><td>ATT (WINGER folded)</td></tr>
     *   <tr><td>SHOOTER</td><td>0.05 (WINGER) / 0.25 (ATT)</td><td>ATT (WINGER folded)</td></tr>
     *   <tr><td>HEADER</td><td>0.20 (ATT)</td><td>ATT</td></tr>
     * </table>
     *
     * <p>Each skill appears in <b>every</b> group where it has weight
     * (not just its primary) — so a player with AERIAL=99 contributes to
     * both the GK row and the DEF row. This is intentional: it reflects
     * the engine's own treatment of cross-position skills.
     */
    public static List<PositionGroup> groupsForSkill(PlayerSkill skill) {
        if (skill == null) {
            return List.of();
        }
        return switch (skill) {
            case WALL     -> List.of(PositionGroup.GK);
            case AERIAL   -> List.of(PositionGroup.GK, PositionGroup.DEF);
            case MARKER   -> List.of(PositionGroup.DEF, PositionGroup.MID);
            case TACKLER  -> List.of(PositionGroup.GK, PositionGroup.DEF, PositionGroup.MID);
            case PLAYMAKER -> List.of(PositionGroup.MID);
            case PASSER   -> List.of(PositionGroup.GK, PositionGroup.DEF, PositionGroup.MID);
            case SPEEDSTER -> List.of(PositionGroup.ATT);
            case DRIBBLER  -> List.of(PositionGroup.ATT);
            case SHOOTER   -> List.of(PositionGroup.ATT);
            case HEADER    -> List.of(PositionGroup.ATT);
        };
    }
}
