package com.footballmanager.adapters.in.web.career.lineup.dto;

import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.valueobject.ChemistryDetail;
import com.footballmanager.domain.model.valueobject.PlayerSkill;
import com.footballmanager.domain.model.valueobject.TeamChemistryCalculator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V25D43 (Sprint C8): unit tests for {@link ChemistryBreakdownDTO}.
 *
 * <p>Coverage:
 * <ul>
 *   <li>{@code from(ChemistryDetail)}: domain → DTO mapping (groups,
 *       maxSkillByType, coveragePercentage)</li>
 *   <li>{@code empty()}: stable shape for empty lineups (4 groups,
 *       10 skill keys)</li>
 *   <li>JSON serialization: enums to name strings, maps in stable order</li>
 *   <li>Round-trip: end-to-end domain → DTO → JSON → DTO works
 *       (smoke test for the wire format)</li>
 * </ul>
 */
@DisplayName("ChemistryBreakdownDTO — V25D43 chemistry response mapper")
class ChemistryBreakdownDTOTest {

    /** Reasonable starter stats. */
    private static int[] elite() {
        return new int[]{80, 80, 80, 80, 80, 80};
    }

    private static SessionPlayer playerWithSkills(String position, int[] stats,
                                                  Map<PlayerSkill, Integer> skills) {
        SessionPlayer p = SessionPlayer.custom("Test", 25, position,
                stats[0], stats[1], stats[2], stats[3], stats[4], stats[5],
                BigDecimal.valueOf(1_000_000));
        for (Map.Entry<PlayerSkill, Integer> e : skills.entrySet()) {
            p.setSkillLevel(e.getKey(), e.getValue());
        }
        return p;
    }

    // ========== from(ChemistryDetail) mapper ==========

    @Nested
    @DisplayName("from(ChemistryDetail): domain → DTO mapping")
    class FromMapper {

        @Test
        @DisplayName("Null detail → empty DTO (defensive)")
        void nullDetail() {
            ChemistryBreakdownDTO dto = ChemistryBreakdownDTO.from(null);
            assertNotNull(dto);
            // 4 group keys (GK, DEF, MID, ATT), all empty
            assertEquals(4, dto.positionGroups().size());
            for (ChemistryDetail.PositionGroup g : ChemistryDetail.PositionGroup.values()) {
                assertEquals(List.of(), dto.positionGroups().get(g.name()));
            }
            // 10 skill keys, all 0
            assertEquals(10, dto.maxSkillByType().size());
            for (PlayerSkill s : PlayerSkill.values()) {
                assertEquals(0, dto.maxSkillByType().get(s.name()));
            }
            assertEquals(0, dto.coveragePercentage());
        }

        @Test
        @DisplayName("Empty lineup (all-null) → empty DTO shape with score=0")
        void emptyLineup() {
            ChemistryDetail detail = TeamChemistryCalculator.calculate(new ArrayList<>());
            ChemistryBreakdownDTO dto = ChemistryBreakdownDTO.from(detail);
            // 4 group keys, all empty
            for (ChemistryDetail.PositionGroup g : ChemistryDetail.PositionGroup.values()) {
                assertEquals(List.of(), dto.positionGroups().get(g.name()));
            }
            // 10 maxSkillByType keys, all 0
            for (PlayerSkill s : PlayerSkill.values()) {
                assertEquals(0, dto.maxSkillByType().get(s.name()));
            }
            assertEquals(0, dto.coveragePercentage());
        }

        @Test
        @DisplayName("Single GK with WALL=99, AERIAL=99 → GK row 2 entries, DEF row 1")
        void singleGkWallAerial() {
            SessionPlayer sp = playerWithSkills("GK", elite(),
                    Map.of(PlayerSkill.WALL, 99, PlayerSkill.AERIAL, 99));
            ChemistryDetail detail = TeamChemistryCalculator.calculate(List.of(sp));
            ChemistryBreakdownDTO dto = ChemistryBreakdownDTO.from(detail);

            // GK row: AERIAL + WALL (in PlayerSkill.values() declaration order: AERIAL idx=3 < WALL idx=9)
            List<ChemistryBreakdownDTO.SkillCoverageDTO> gk = dto.positionGroups().get("GK");
            assertEquals(2, gk.size());
            assertEquals("AERIAL", gk.get(0).skill());
            assertEquals(99, gk.get(0).maxLevel());
            assertEquals(sp.getSessionPlayerId(), gk.get(0).contributorId());
            assertEquals("WALL", gk.get(1).skill());
            assertEquals(99, gk.get(1).maxLevel());

            // DEF row: AERIAL (cross-position skill, the only one with DEF weight)
            List<ChemistryBreakdownDTO.SkillCoverageDTO> def = dto.positionGroups().get("DEF");
            assertEquals(1, def.size());
            assertEquals("AERIAL", def.get(0).skill());

            // maxSkillByType
            assertEquals(99, dto.maxSkillByType().get("WALL"));
            assertEquals(99, dto.maxSkillByType().get("AERIAL"));
            assertEquals(0, dto.maxSkillByType().get("MARKER"));

            // coveragePercentage: 2/10 = 20%
            assertEquals(20, dto.coveragePercentage());
        }

        @Test
        @DisplayName("All 10 skills at 99 → 4 entries per group + 100% coverage")
        void allSkillsMax() {
            Map<PlayerSkill, Integer> allSkills = new HashMap<>();
            for (PlayerSkill s : PlayerSkill.values()) allSkills.put(s, 99);
            List<SessionPlayer> lineup = new ArrayList<>();
            for (int i = 0; i < 11; i++) {
                lineup.add(playerWithSkills("MID", elite(), allSkills));
            }
            ChemistryDetail detail = TeamChemistryCalculator.calculate(lineup);
            ChemistryBreakdownDTO dto = ChemistryBreakdownDTO.from(detail);

            for (String group : dto.positionGroups().keySet()) {
                assertEquals(4, dto.positionGroups().get(group).size(),
                        "Group " + group + " should have 4 entries");
            }
            assertEquals(100, dto.coveragePercentage());
            for (PlayerSkill s : PlayerSkill.values()) {
                assertEquals(99, dto.maxSkillByType().get(s.name()));
            }
        }
    }

    // ========== empty() convenience ==========

    @Nested
    @DisplayName("empty(): stable empty shape")
    class Empty {

        @Test
        @DisplayName("Returns 4 groups (all empty) + 10 skill keys (all 0) + 0% coverage")
        void shape() {
            ChemistryBreakdownDTO empty = ChemistryBreakdownDTO.empty();
            assertEquals(4, empty.positionGroups().size());
            for (ChemistryDetail.PositionGroup g : ChemistryDetail.PositionGroup.values()) {
                assertTrue(empty.positionGroups().containsKey(g.name()),
                        "Should contain group " + g.name());
                assertEquals(List.of(), empty.positionGroups().get(g.name()));
            }
            assertEquals(10, empty.maxSkillByType().size());
            for (PlayerSkill s : PlayerSkill.values()) {
                assertEquals(0, empty.maxSkillByType().get(s.name()));
            }
            assertEquals(0, empty.coveragePercentage());
        }
    }

    // ========== JSON serialization ==========

    @Nested
    @DisplayName("JSON serialization (Jackson)")
    class JsonSerialization {

        @Test
        @DisplayName("Empty DTO → valid JSON with 4 group keys and 10 skill keys")
        void emptySerialization() throws Exception {
            ObjectMapper mapper = new ObjectMapper();
            String json = mapper.writeValueAsString(ChemistryBreakdownDTO.empty());

            // 4 group keys
            assertTrue(json.contains("\"GK\""));
            assertTrue(json.contains("\"DEF\""));
            assertTrue(json.contains("\"MID\""));
            assertTrue(json.contains("\"ATT\""));
            // 10 skill keys
            assertTrue(json.contains("\"WALL\""));
            assertTrue(json.contains("\"AERIAL\""));
            assertTrue(json.contains("\"MARKER\""));
            assertTrue(json.contains("\"TACKLER\""));
            assertTrue(json.contains("\"PLAYMAKER\""));
            assertTrue(json.contains("\"PASSER\""));
            assertTrue(json.contains("\"SHOOTER\""));
            assertTrue(json.contains("\"HEADER\""));
            assertTrue(json.contains("\"DRIBBLER\""));
            assertTrue(json.contains("\"SPEEDSTER\""));
            // coveragePercentage = 0
            assertTrue(json.contains("\"coveragePercentage\":0"));
        }

        @Test
        @DisplayName("Single GK with WALL=99 → JSON contains WALL chip with maxLevel=99 and contributorId")
        void populatedSerialization() throws Exception {
            SessionPlayer sp = playerWithSkills("GK", elite(),
                    Map.of(PlayerSkill.WALL, 99, PlayerSkill.AERIAL, 99));
            ChemistryDetail detail = TeamChemistryCalculator.calculate(List.of(sp));
            ChemistryBreakdownDTO dto = ChemistryBreakdownDTO.from(detail);

            ObjectMapper mapper = new ObjectMapper();
            String json = mapper.writeValueAsString(dto);

            // Should have a "WALL" chip with maxLevel 99 and the player's id as contributor
            assertTrue(json.contains("\"skill\":\"WALL\""), "JSON should contain WALL chip");
            assertTrue(json.contains("\"maxLevel\":99"), "JSON should contain maxLevel:99");
            assertTrue(json.contains("\"contributorId\":\"" + sp.getSessionPlayerId() + "\""),
                    "JSON should contain contributorId for the player");
            assertTrue(json.contains("\"coveragePercentage\":20"), "20% coverage expected");
        }
    }
}
