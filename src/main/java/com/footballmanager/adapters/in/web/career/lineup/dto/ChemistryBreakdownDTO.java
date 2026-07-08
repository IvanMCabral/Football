package com.footballmanager.adapters.in.web.career.lineup.dto;

import com.footballmanager.domain.model.valueobject.ChemistryDetail;
import com.footballmanager.domain.model.valueobject.FormationInferer;
import com.footballmanager.domain.model.valueobject.PlayerSkill;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * V25D43 (Sprint C8): response DTO for the chemistry breakdown carried
 * inside {@link LineupDTO}. Sits alongside the {@code chemistryScore}
 * field (added in V25D41) — the score alone doesn't tell the manager
 * <em>why</em> the chemistry is what it is, this DTO does.
 *
 * <p>Shape (Jackson-serialized as JSON):
 * <pre>
 * {
 *   "positionGroups": {
 *     "GK":  [ { "skill": "WALL",   "maxLevel": 99, "contributorId": "p1" },
 *              { "skill": "AERIAL", "maxLevel": 99, "contributorId": "p1" } ],
 *     "DEF": [ { "skill": "AERIAL", "maxLevel": 80, "contributorId": "p2" } ],
 *     "MID": [],
 *     "ATT": [ { "skill": "SHOOTER", "maxLevel": 90, "contributorId": "p3" } ]
 *   },
 *   "maxSkillByType": { "WALL": 99, "AERIAL": 99, "SHOOTER": 90, ... },
 *   "coveragePercentage": 30
 * }
 * </pre>
 *
 * <p>Two design notes:
 * <ul>
 *   <li>Group keys are {@link ChemistryDetail.PositionGroup#name()}
 *       strings (GK / DEF / MID / ATT) — Jackson serializes enums to
 *       their name by default. Frontend iterates {@code Object.keys()}.</li>
 *   <li>{@code maxSkillByType} is a {@code Map<PlayerSkill, Integer>}
 *       that always contains all 10 keys (absent skills → 0), so the
 *       frontend can do {@code bd.maxSkillByType[skill]} without null
 *       checks.</li>
 * </ul>
 *
 * <p>Backward compat: this DTO is nullable on {@link LineupDTO}. A
 * backend that doesn't populate it (V25D41 / V25D42 builds) returns
 * the lineup without this field — the frontend treats it as "no
 * breakdown" (renders the existing badge but no chip row).
 */
public record ChemistryBreakdownDTO(
    Map<String, List<SkillCoverageDTO>> positionGroups,
    Map<String, Integer> maxSkillByType,
    int coveragePercentage
) {

    /**
     * One skill's contribution to a position group.
     *
     * @param skill {@link PlayerSkill} name (e.g., {@code "WALL"}, {@code "AERIAL"})
     * @param maxLevel max level of this skill across the lineup ({@code [0, 99]})
     * @param contributorId {@code sessionPlayerId} of the player carrying
     *        the max level for this skill
     */
    public record SkillCoverageDTO(
        String skill,
        int maxLevel,
        String contributorId
    ) {}

    /**
     * Mapper: {@link ChemistryDetail} (domain) → {@link ChemistryBreakdownDTO}
     * (response). Preserves the group ordering (GK → DEF → MID → ATT) by
     * using a {@link LinkedHashMap}. {@code maxSkillByType} is also ordered
     * by {@link PlayerSkill#values()} declaration.
     */
    public static ChemistryBreakdownDTO from(ChemistryDetail detail) {
        // Defensive: detail should never be null in practice, but the
        // build sites can hand us one if the lineup is empty (TeamChemistryCalculator
        // returns an empty detail, not null). Guard anyway — return the
        // same stable shape as empty() so callers can rely on 4 group keys.
        if (detail == null) {
            return empty();
        }

        // positionGroups: preserve ChemistryDetail.PositionGroup order.
        Map<String, List<SkillCoverageDTO>> groups = new LinkedHashMap<>();
        for (ChemistryDetail.PositionGroup g : ChemistryDetail.PositionGroup.values()) {
            List<ChemistryDetail.SkillCoverage> rows = detail.breakdown().getOrDefault(g, List.of());
            List<SkillCoverageDTO> dtos = rows.stream()
                    .map(sc -> new SkillCoverageDTO(
                            sc.skill().name(),
                            sc.maxLevel(),
                            sc.contributorPlayerId()))
                    .toList();
            groups.put(g.name(), dtos);
        }

        // maxSkillByType: PlayerSkill declaration order, always 10 keys.
        Map<String, Integer> maxSkillByType = new LinkedHashMap<>();
        for (PlayerSkill s : PlayerSkill.values()) {
            Integer v = detail.maxSkillByType().getOrDefault(s, 0);
            maxSkillByType.put(s.name(), v);
        }

        return new ChemistryBreakdownDTO(groups, maxSkillByType, detail.coveragePercentage());
    }

    /**
     * V25D99.19-BACK (BUG-1 fix): overload that pads the {@code positionGroups}
     * map with slot-category fallback entries when the skill-weight grouping
     * yields an empty group but the lineup has at least one assigned slot
     * in that category. Defensive against the legacy / corner-case data
     * state where a player's {@code skillLevels} map is missing entirely
     * (V25D31 seed lineups, manual-select saves pre-V25D33, players cloned
     * via the 5-arg factory overload) — in those cases the skill-weight
     * {@code computeBreakdown()} emits an empty list per group, and Ivan
     * observed the UI rendering nothing under the
     * {@code Chemistry Breakdown (X% coverage)} header.
     *
     * <p>Behavior:
     * <ol>
     *   <li>Run the skill-weight mapping exactly like {@link #from(ChemistryDetail)}
     *       (same DTO shape, same field semantics).</li>
     *   <li>For each PositionGroup whose skill-weight list is empty AND
     *       the lineup has at least one slot whose
     *       {@link FormationInferer#categoryFor(String)} returns that
     *       group's name: synthesize a single
     *       {@link SkillCoverageDTO} with {@code skill = "{GROUP}-SLOT"},
     *       {@code maxLevel = 0} (visually distinct chip color via the
     *       existing {@code chip-low} class), and the first assigned
     *       player's id as {@code contributorId}. This guarantees the
     *       UI shows the group label with at least one chip.</li>
     *   <li>{@code coveragePercentage} and {@code maxSkillByType} are
     *       untouched — they reflect actual skill data only.</li>
     * </ol>
     *
     * <p>Pre-V25D99.19 callers that still pass {@code null} for either
     * argument fall through to the legacy single-arg {@link #from(ChemistryDetail)}
     * (still exported by V25D99.19) — backward compat preserved.
     *
     * @param detail          the skill-weight aggregate (already computed
     *                        via {@link com.footballmanager.domain.model.valueobject.TeamChemistryCalculator#calculate(java.util.List)}).
     * @param slots           the lineup's persisted slots
     *                        ({@code playerId → subdivisionId}); may be null
     *                        or empty (falls back to skill-weight only).
     * @param naturalByPlayer {@code playerId → natural 5-cat position} for
     *                        contributor resolution; may be null (no contributor
     *                        resolution in the fallback chips).
     */
    public static ChemistryBreakdownDTO from(ChemistryDetail detail,
                                             List<LineupSlotDTO> slots,
                                             Map<String, String> naturalByPlayer) {
        ChemistryBreakdownDTO base = from(detail);
        if (slots == null || slots.isEmpty()) {
            return base;
        }

        Map<String, List<SkillCoverageDTO>> groups = new LinkedHashMap<>(base.positionGroups());
        // Iterate the 4 PositionGroup values in order; for each empty
        // group, see if the lineup has at least one slot in that category.
        for (ChemistryDetail.PositionGroup g : ChemistryDetail.PositionGroup.values()) {
            List<SkillCoverageDTO> existing = groups.get(g.name());
            if (existing != null && !existing.isEmpty()) {
                continue;  // skill-weight produced real entries; leave it alone.
            }
            String firstContributorId = null;
            boolean foundSlot = false;
            for (LineupSlotDTO slot : slots) {
                if (slot == null || slot.subdivisionId() == null) continue;
                String cat = FormationInferer.categoryFor(slot.subdivisionId());
                if (g.name().equals(cat)) {
                    foundSlot = true;
                    if (firstContributorId == null && slot.playerId() != null) {
                        firstContributorId = slot.playerId();
                    }
                }
            }
            if (foundSlot) {
                // Synthesize a placeholder SkillCoverageDTO so the group
                // renders with at least one chip. maxLevel=0 triggers the
                // existing chip-low CSS class so the placeholder is
                // visually distinct from real data (>0 entries).
                List<SkillCoverageDTO> synthesized = new ArrayList<>(existing == null ? 0 : existing.size() + 1);
                if (existing != null) synthesized.addAll(existing);
                synthesized.add(new SkillCoverageDTO(
                        g.name() + "-SLOT",
                        0,
                        firstContributorId));
                groups.put(g.name(), synthesized);
            }
        }
        return new ChemistryBreakdownDTO(groups, base.maxSkillByType(), base.coveragePercentage());
    }

    /**
     * Convenience: return an empty breakdown (used by build sites when
     * they need a non-null but zero-value DTO — e.g., a lineup that
     * somehow bypassed the calculator). Mirrors
     * {@code TeamChemistryCalculator.calculate(null).} behavior in
     * response shape.
     */
    public static ChemistryBreakdownDTO empty() {
        Map<String, List<SkillCoverageDTO>> groups = new LinkedHashMap<>();
        for (ChemistryDetail.PositionGroup g : ChemistryDetail.PositionGroup.values()) {
            groups.put(g.name(), List.of());
        }
        Map<String, Integer> maxSkillByType = new LinkedHashMap<>();
        for (PlayerSkill s : PlayerSkill.values()) {
            maxSkillByType.put(s.name(), 0);
        }
        return new ChemistryBreakdownDTO(groups, maxSkillByType, 0);
    }
}
