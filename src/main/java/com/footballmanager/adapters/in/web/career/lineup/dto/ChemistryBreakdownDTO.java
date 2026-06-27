package com.footballmanager.adapters.in.web.career.lineup.dto;

import com.footballmanager.domain.model.valueobject.ChemistryDetail;
import com.footballmanager.domain.model.valueobject.PlayerSkill;

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
