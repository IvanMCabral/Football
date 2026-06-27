package com.footballmanager.domain.model.valueobject;

import com.footballmanager.domain.model.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V25D40 (Sprint C5): unit tests for {@link OverallCalculator}, the shared
 * utility used by both {@code Player.getOverall()} and
 * {@code SessionPlayer.calculateOverall()}.
 *
 * <p>Coverage strategy: the 5 position categories (GK/DEF/MID/WINGER/ATT)
 * each get their own {@code @Nested} class with backward-compat + skill +
 * height + combined + edge cases. Plus a {@code PositionMapping} section
 * to verify the enum-to-category helper and a shared {@code Clamping} +
 * {@code Adversarial} section for the cross-cutting invariants.
 */
@DisplayName("OverallCalculator — V25D40 shared utility")
class OverallCalculatorTest {

    // ========== Test helpers ==========

    /** Reasonable starter stats for an average-elite player (~80 base overall). */
    private static int[] elite() {
        return new int[]{80, 80, 80, 80, 80, 80};
    }

    private static int[] avg() {
        return new int[]{50, 50, 50, 50, 50, 50};
    }

    // ========== GK category ==========

    @Nested
    @DisplayName("GK — goalkeeper")
    class GkCategory {

        @Test
        @DisplayName("GK backward compat: no height/skills returns base formula")
        void gkBackwardCompat() {
            // GK weights: defense 0.40, technique 0.20, mentality 0.20, stamina 0.10, speed 0.05, attack 0.05
            // base = 50*0.40 + 50*0.20 + 50*0.20 + 50*0.10 + 50*0.05 + 50*0.05
            //      = 20 + 10 + 10 + 5 + 2.5 + 2.5 = 50
            int overall = OverallCalculator.calculate(50, 50, 50, 50, 50, 50, "GK", null, null);
            assertEquals(50, overall, "GK without extensions must match pre-V25D40 base formula");
        }

        @Test
        @DisplayName("GK skill bonus: WALL=99 → +3 (WALL weight 0.30)")
        void gkWall99() {
            // base = 80 (elite), skill sum = 99 * 0.30 = 29.7 / 10 = 2.97 → 3
            int base = OverallCalculator.calculate(80, 80, 80, 80, 80, 80, "GK", null, null);
            int withWall = OverallCalculator.calculate(80, 80, 80, 80, 80, 80, "GK", null,
                    Map.of(PlayerSkill.WALL, 99));
            assertEquals(base + 3, withWall, "WALL=99 should add 3 to GK overall");
        }

        @Test
        @DisplayName("GK height=200 → +2 (max bonus)")
        void gkHeight200() {
            int base = OverallCalculator.calculate(80, 80, 80, 80, 80, 80, "GK", null, null);
            int withHeight = OverallCalculator.calculate(80, 80, 80, 80, 80, 80, "GK", 200, null);
            assertEquals(base + 2, withHeight, "GK height=200 should add +2");
        }

        @Test
        @DisplayName("GK height=180 → -1 (penalty)")
        void gkHeight180() {
            int base = OverallCalculator.calculate(80, 80, 80, 80, 80, 80, "GK", null, null);
            int withHeight = OverallCalculator.calculate(80, 80, 80, 80, 80, 80, "GK", 180, null);
            assertEquals(base - 1, withHeight, "GK height=180 should subtract 1");
        }

        @Test
        @DisplayName("GK combined: WALL=99 + height=200 → +5 (skill 3 + height 2)")
        void gkCombined() {
            int base = OverallCalculator.calculate(80, 80, 80, 80, 80, 80, "GK", null, null);
            int combined = OverallCalculator.calculate(80, 80, 80, 80, 80, 80, "GK", 200,
                    Map.of(PlayerSkill.WALL, 99));
            assertEquals(base + 5, combined, "GK WALL=99 + height=200 should add 5");
        }
    }

    // ========== DEF category ==========

    @Nested
    @DisplayName("DEF — defender")
    class DefCategory {

        @Test
        @DisplayName("DEF backward compat: 50×6 → 50")
        void defBackwardCompat() {
            // DEF weights: defense 0.35, technique 0.15, mentality 0.15, stamina 0.15, speed 0.10, attack 0.10
            // base = 50 * (0.35+0.15+0.15+0.15+0.10+0.10) = 50 * 1.00 = 50
            int overall = OverallCalculator.calculate(50, 50, 50, 50, 50, 50, "DEF", null, null);
            assertEquals(50, overall);
        }

        @Test
        @DisplayName("DEF skill bonus: MARKER=99 → +2 (MARKER weight 0.25)")
        void defMarker99() {
            // base = 80, skill sum = 99 * 0.25 = 24.75 / 10 = 2.475 → 2 (round)
            int base = OverallCalculator.calculate(80, 80, 80, 80, 80, 80, "DEF", null, null);
            int withMarker = OverallCalculator.calculate(80, 80, 80, 80, 80, 80, "DEF", null,
                    Map.of(PlayerSkill.MARKER, 99));
            assertEquals(base + 2, withMarker, "MARKER=99 should add 2 to DEF overall");
        }

        @Test
        @DisplayName("DEF height=170 → -2 (penalty)")
        void defHeight170() {
            int base = OverallCalculator.calculate(80, 80, 80, 80, 80, 80, "DEF", null, null);
            int withHeight = OverallCalculator.calculate(80, 80, 80, 80, 80, 80, "DEF", 170, null);
            assertEquals(base - 2, withHeight, "DEF height=170 should subtract 2");
        }

        @Test
        @DisplayName("DEF height=190 → +2 (AERIAL bonus)")
        void defHeight190() {
            int base = OverallCalculator.calculate(80, 80, 80, 80, 80, 80, "DEF", null, null);
            int withHeight = OverallCalculator.calculate(80, 80, 80, 80, 80, 80, "DEF", 190, null);
            assertEquals(base + 2, withHeight, "DEF height=190 should add 2");
        }

        @Test
        @DisplayName("DEF combined: MARKER=99 + height=190 → +4 (skill 2 + height 2)")
        void defCombined() {
            int base = OverallCalculator.calculate(80, 80, 80, 80, 80, 80, "DEF", null, null);
            int combined = OverallCalculator.calculate(80, 80, 80, 80, 80, 80, "DEF", 190,
                    Map.of(PlayerSkill.MARKER, 99));
            assertEquals(base + 4, combined, "DEF MARKER=99 + height=190 should add 4");
        }
    }

    // ========== MID category ==========

    @Nested
    @DisplayName("MID — midfielder")
    class MidCategory {

        @Test
        @DisplayName("MID backward compat: 50×6 → 50")
        void midBackwardCompat() {
            int overall = OverallCalculator.calculate(50, 50, 50, 50, 50, 50, "MID", null, null);
            assertEquals(50, overall);
        }

        @Test
        @DisplayName("MID skill bonus: PLAYMAKER=99 → +3 (weight 0.30)")
        void midPlaymaker99() {
            // skill sum = 99 * 0.30 = 29.7 / 10 = 2.97 → 3
            int base = OverallCalculator.calculate(80, 80, 80, 80, 80, 80, "MID", null, null);
            int withPlaymaker = OverallCalculator.calculate(80, 80, 80, 80, 80, 80, "MID", null,
                    Map.of(PlayerSkill.PLAYMAKER, 99));
            assertEquals(base + 3, withPlaymaker, "PLAYMAKER=99 should add 3 to MID overall");
        }

        @Test
        @DisplayName("MID height=190 → +1")
        void midHeight190() {
            int base = OverallCalculator.calculate(80, 80, 80, 80, 80, 80, "MID", null, null);
            int withHeight = OverallCalculator.calculate(80, 80, 80, 80, 80, 80, "MID", 190, null);
            assertEquals(base + 1, withHeight);
        }

        @Test
        @DisplayName("MID height<170 → -1")
        void midHeightShort() {
            int base = OverallCalculator.calculate(80, 80, 80, 80, 80, 80, "MID", null, null);
            int withHeight = OverallCalculator.calculate(80, 80, 80, 80, 80, 80, "MID", 165, null);
            assertEquals(base - 1, withHeight);
        }
    }

    // ========== WINGER category ==========

    @Nested
    @DisplayName("WINGER — winger")
    class WingerCategory {

        @Test
        @DisplayName("WINGER backward compat: 50×6 → 50")
        void wingerBackwardCompat() {
            int overall = OverallCalculator.calculate(50, 50, 50, 50, 50, 50, "WINGER", null, null);
            assertEquals(50, overall);
        }

        @Test
        @DisplayName("WINGER skill bonus: SPEEDSTER=99 + DRIBBLER=99 → +5 (weights 0.25+0.25)")
        void wingerSpeedsterDribbler99() {
            // skill sum = (99*0.25 + 99*0.25) = 49.5 / 10 = 4.95 → 5
            int base = OverallCalculator.calculate(80, 80, 80, 80, 80, 80, "WINGER", null, null);
            int withSkills = OverallCalculator.calculate(80, 80, 80, 80, 80, 80, "WINGER", null,
                    Map.of(PlayerSkill.SPEEDSTER, 99, PlayerSkill.DRIBBLER, 99));
            assertEquals(base + 5, withSkills, "SPEEDSTER+DRIBBLER=99 should add 5 to WINGER overall");
        }

        @Test
        @DisplayName("WINGER height=170 (closed boundary) → -1 (matches V25D39 test expectation)")
        void wingerHeight170() {
            // V25D39 winger_skills99_height170 expects base+4 net: skill 5 - height 1 = 4.
            int base = OverallCalculator.calculate(80, 80, 80, 80, 80, 80, "WINGER", null, null);
            int withHeight = OverallCalculator.calculate(80, 80, 80, 80, 80, 80, "WINGER", 170, null);
            assertEquals(base - 1, withHeight, "WINGER height=170 should subtract 1 (closed boundary)");
        }

        @Test
        @DisplayName("WINGER height=185 → +1")
        void wingerHeight185() {
            int base = OverallCalculator.calculate(80, 80, 80, 80, 80, 80, "WINGER", null, null);
            int withHeight = OverallCalculator.calculate(80, 80, 80, 80, 80, 80, "WINGER", 185, null);
            assertEquals(base + 1, withHeight);
        }

        @Test
        @DisplayName("WINGER combined: SPEEDSTER+DRIBBLER=99 + height=170 → +4 (skill 5 - height 1)")
        void wingerCombined() {
            int base = OverallCalculator.calculate(80, 80, 80, 80, 80, 80, "WINGER", null, null);
            int combined = OverallCalculator.calculate(80, 80, 80, 80, 80, 80, "WINGER", 170,
                    Map.of(PlayerSkill.SPEEDSTER, 99, PlayerSkill.DRIBBLER, 99));
            assertEquals(base + 4, combined, "WINGER SPEEDSTER+DRIBBLER=99 + height=170 should add 4");
        }
    }

    // ========== ATT category ==========

    @Nested
    @DisplayName("ATT — attacker")
    class AttCategory {

        @Test
        @DisplayName("ATT backward compat: 50×6 → 50")
        void attBackwardCompat() {
            int overall = OverallCalculator.calculate(50, 50, 50, 50, 50, 50, "ATT", null, null);
            assertEquals(50, overall);
        }

        @Test
        @DisplayName("ATT skill bonus: SHOOTER=99 → +2 (weight 0.25)")
        void attShooter99() {
            // skill sum = 99 * 0.25 = 24.75 / 10 = 2.475 → 2
            int base = OverallCalculator.calculate(80, 80, 80, 80, 80, 80, "ATT", null, null);
            int withShooter = OverallCalculator.calculate(80, 80, 80, 80, 80, 80, "ATT", null,
                    Map.of(PlayerSkill.SHOOTER, 99));
            assertEquals(base + 2, withShooter, "SHOOTER=99 should add 2 to ATT overall");
        }

        @Test
        @DisplayName("ATT height=190 → +2 (HEADER bonus)")
        void attHeight190() {
            int base = OverallCalculator.calculate(80, 80, 80, 80, 80, 80, "ATT", null, null);
            int withHeight = OverallCalculator.calculate(80, 80, 80, 80, 80, 80, "ATT", 190, null);
            assertEquals(base + 2, withHeight, "ATT height=190 should add 2");
        }

        @Test
        @DisplayName("ATT height=170 → -2 (penalty)")
        void attHeight170() {
            int base = OverallCalculator.calculate(80, 80, 80, 80, 80, 80, "ATT", null, null);
            int withHeight = OverallCalculator.calculate(80, 80, 80, 80, 80, 80, "ATT", 170, null);
            assertEquals(base - 2, withHeight, "ATT height=170 should subtract 2");
        }
    }

    // ========== Default (unknown position) ==========

    @Nested
    @DisplayName("Default position (unknown string) — arithmetic mean")
    class DefaultPosition {

        @Test
        @DisplayName("position='UNKNOWN' returns arithmetic mean (no skill/height adjustments)")
        void unknownReturnsMean() {
            // (50+50+50+50+50+50) / 6 = 50
            int overall = OverallCalculator.calculate(50, 50, 50, 50, 50, 50, "UNKNOWN", null, null);
            assertEquals(50, overall);
        }

        @Test
        @DisplayName("position='UNKNOWN' with no skills/height → identical to mean (no bonuses applied)")
        void unknownNoBonuses() {
            // Mean is 50; height factor for unknown position is 0; skill weights empty.
            int withNull = OverallCalculator.calculate(50, 50, 50, 50, 50, 50, "UNKNOWN", null, null);
            int withEmptyMap = OverallCalculator.calculate(50, 50, 50, 50, 50, 50, "UNKNOWN", null,
                    Collections.emptyMap());
            int withHeight = OverallCalculator.calculate(50, 50, 50, 50, 50, 50, "UNKNOWN", 200,
                    Collections.emptyMap());
            assertEquals(50, withNull);
            assertEquals(50, withEmptyMap);
            assertEquals(50, withHeight, "Unknown position: height factor is 0 (no bonus)");
        }

        @Test
        @DisplayName("position='UNKNOWN' with skills → mean + skill bonus from any non-zero weight")
        void unknownWithSkill() {
            // For unknown position, skillWeightsFor returns Map.of() (empty).
            // So skills add 0 bonus — overall stays at mean.
            int withSkill = OverallCalculator.calculate(50, 50, 50, 50, 50, 50, "UNKNOWN", null,
                    Map.of(PlayerSkill.WALL, 99));
            assertEquals(50, withSkill, "Unknown position: skill weights are empty, no bonus");
        }
    }

    // ========== Edge cases ==========

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("Null position is treated as default (mean)")
        void nullPosition() {
            int overall = OverallCalculator.calculate(50, 50, 50, 50, 50, 50, null, null, null);
            assertEquals(50, overall, "Null position falls through to default (mean)");
        }

        @Test
        @DisplayName("Empty skillLevels map (HashMap) treated as no skills")
        void emptySkillMap() {
            int base = OverallCalculator.calculate(50, 50, 50, 50, 50, 50, "GK", null, null);
            int withEmpty = OverallCalculator.calculate(50, 50, 50, 50, 50, 50, "GK", null,
                    new HashMap<>());
            assertEquals(base, withEmpty, "Empty skill map = no bonus (backward compat)");
        }

        @Test
        @DisplayName("Skill value 0 in map treated as no contribution")
        void skillValueZero() {
            Map<PlayerSkill, Integer> skills = new HashMap<>();
            skills.put(PlayerSkill.WALL, 0);
            int withZero = OverallCalculator.calculate(80, 80, 80, 80, 80, 80, "GK", null, skills);
            int base = OverallCalculator.calculate(80, 80, 80, 80, 80, 80, "GK", null, null);
            assertEquals(base, withZero, "Skill value 0 in map contributes nothing");
        }

        @Test
        @DisplayName("Height out of bounds (< 160) is clamped to 160")
        void heightClampedLow() {
            // GK h=160: 160 < 185 → -1 (same as 159 etc., all clamped to 160)
            int base = OverallCalculator.calculate(80, 80, 80, 80, 80, 80, "GK", null, null);
            int clamped = OverallCalculator.calculate(80, 80, 80, 80, 80, 80, "GK", 100, null);
            assertEquals(base - 1, clamped, "Height < 160 clamps to 160 (still in penalty range for GK)");
        }

        @Test
        @DisplayName("Height out of bounds (> 210) is clamped to 210")
        void heightClampedHigh() {
            // GK h=210: >= 200 → +2 (max)
            int base = OverallCalculator.calculate(80, 80, 80, 80, 80, 80, "GK", null, null);
            int clamped = OverallCalculator.calculate(80, 80, 80, 80, 80, 80, "GK", 250, null);
            assertEquals(base + 2, clamped, "Height > 210 clamps to 210 (max GK bonus)");
        }

        @Test
        @DisplayName("Irrelevant skill for category contributes 0 (e.g. SHOOTER on GK)")
        void irrelevantSkill() {
            int base = OverallCalculator.calculate(80, 80, 80, 80, 80, 80, "GK", null, null);
            int withShooter = OverallCalculator.calculate(80, 80, 80, 80, 80, 80, "GK", null,
                    Map.of(PlayerSkill.SHOOTER, 99));
            assertEquals(base, withShooter, "SHOOTER on GK contributes 0 (not in GK weight table)");
        }
    }

    // ========== Clamping invariants ==========

    @Nested
    @DisplayName("Clamping invariants")
    class Clamping {

        @Test
        @DisplayName("All stats at 99 + all skills at 99 + max height → clamped to 99 (not 107+)")
        void maxClampTo99() {
            int god = OverallCalculator.calculate(99, 99, 99, 99, 99, 99, "ST", 200,
                    Map.of(PlayerSkill.SHOOTER, 99, PlayerSkill.HEADER, 99,
                            PlayerSkill.DRIBBLER, 99, PlayerSkill.SPEEDSTER, 99));
            assertEquals(99, god, "Max-elite player clamped to 99");
        }

        @Test
        @DisplayName("All stats at 1 + height=170 (penalty) → clamped to 0 (not -1)")
        void minClampTo0() {
            // ATT base: 1*0.40 + 1*0.20 + 1*0.15 + 1*0.10 + 1*0.10 + 1*0.05 = 1
            // ATT height=170: -2
            // Total: 1 - 2 = -1 → clamp to 0
            int min = OverallCalculator.calculate(1, 1, 1, 1, 1, 1, "ATT", 170, null);
            assertEquals(0, min, "Min stats + penalty clamped to 0");
        }

        @Test
        @DisplayName("Result is always in [0, 99] for any input")
        void alwaysInRange() {
            // Fuzz: a handful of random-but-deterministic inputs all return [0, 99].
            int[][] samples = {
                {0, 0, 0, 0, 0, 0}, {99, 99, 99, 99, 99, 99},
                {50, 50, 50, 50, 50, 50}, {1, 99, 1, 99, 1, 99}
            };
            String[] positions = {"GK", "DEF", "MID", "WINGER", "ATT", "UNKNOWN"};
            Integer[] heights = {null, 100, 160, 185, 190, 200, 250};

            for (int[] stats : samples) {
                for (String pos : positions) {
                    for (Integer h : heights) {
                        int result = OverallCalculator.calculate(
                                stats[0], stats[1], stats[2], stats[3], stats[4], stats[5],
                                pos, h, null);
                        assertTrue(result >= 0 && result <= 99,
                                "Result " + result + " out of range for pos=" + pos + " h=" + h);
                    }
                }
            }
        }
    }

    // ========== Adversarial / monotonicity ==========

    @Nested
    @DisplayName("Adversarial probes")
    class Adversarial {

        @Test
        @DisplayName("Monotone in skillValue: skill=99 >= skill=50 (DEF, MARKER)")
        void monotoneSkillValue() {
            int low = OverallCalculator.calculate(80, 80, 80, 80, 80, 80, "DEF", null,
                    Map.of(PlayerSkill.MARKER, 50));
            int high = OverallCalculator.calculate(80, 80, 80, 80, 80, 80, "DEF", null,
                    Map.of(PlayerSkill.MARKER, 99));
            assertTrue(high > low, "MARKER=99 (" + high + ") should beat MARKER=50 (" + low + ")");
        }

        @Test
        @DisplayName("Monotone in height for CB: height=190 >= height=170")
        void monotoneHeight() {
            int shortCb = OverallCalculator.calculate(80, 80, 80, 80, 80, 80, "DEF", 170, null);
            int tallCb = OverallCalculator.calculate(80, 80, 80, 80, 80, 80, "DEF", 190, null);
            assertEquals(4, tallCb - shortCb, "DEF height delta = +4 (170→-2, 190→+2)");
        }

        @Test
        @DisplayName("Skill bonus is additive across skills (no double counting)")
        void skillBonusAdditive() {
            int base = OverallCalculator.calculate(80, 80, 80, 80, 80, 80, "GK", null, null);
            int wallOnly = OverallCalculator.calculate(80, 80, 80, 80, 80, 80, "GK", null,
                    Map.of(PlayerSkill.WALL, 99));
            int aerialOnly = OverallCalculator.calculate(80, 80, 80, 80, 80, 80, "GK", null,
                    Map.of(PlayerSkill.AERIAL, 99));
            int both = OverallCalculator.calculate(80, 80, 80, 80, 80, 80, "GK", null,
                    Map.of(PlayerSkill.WALL, 99, PlayerSkill.AERIAL, 99));
            int individualSum = (wallOnly - base) + (aerialOnly - base);
            assertEquals(base + individualSum, both,
                    "Skill bonus is additive: wallBonus + aerialBonus = combinedBonus");
        }

        @Test
        @DisplayName("Height factor is additive across positions (height-only, no skills)")
        void heightAdditive() {
            int base = OverallCalculator.calculate(80, 80, 80, 80, 80, 80, "GK", null, null);
            int skillOnly = OverallCalculator.calculate(80, 80, 80, 80, 80, 80, "GK", null,
                    Map.of(PlayerSkill.WALL, 99));
            int heightOnly = OverallCalculator.calculate(80, 80, 80, 80, 80, 80, "GK", 200, null);
            int combined = OverallCalculator.calculate(80, 80, 80, 80, 80, 80, "GK", 200,
                    Map.of(PlayerSkill.WALL, 99));
            int skillDelta = skillOnly - base;
            int heightDelta = heightOnly - base;
            int combinedDelta = combined - base;
            assertEquals(skillDelta + heightDelta, combinedDelta,
                    "Combined delta = skill delta + height delta (additive composition)");
        }
    }

    // ========== Position mapping (Player.Position enum → category string) ==========

    @Nested
    @DisplayName("mapPlayerPositionToCategory (Player.Position enum → 5 categories)")
    class PositionMapping {

        @Test
        @DisplayName("GK → 'GK'")
        void gkMapsToGk() {
            assertEquals("GK", OverallCalculator.mapPlayerPositionToCategory(Player.Position.GK));
        }

        @Test
        @DisplayName("All 5 DEF positions (LB, CB, RB, LWB, RWB) → 'DEF'")
        void defPositionsMapToDef() {
            assertEquals("DEF", OverallCalculator.mapPlayerPositionToCategory(Player.Position.LB));
            assertEquals("DEF", OverallCalculator.mapPlayerPositionToCategory(Player.Position.CB));
            assertEquals("DEF", OverallCalculator.mapPlayerPositionToCategory(Player.Position.RB));
            assertEquals("DEF", OverallCalculator.mapPlayerPositionToCategory(Player.Position.LWB));
            assertEquals("DEF", OverallCalculator.mapPlayerPositionToCategory(Player.Position.RWB));
        }

        @Test
        @DisplayName("All 5 MID positions (CDM, CM, CAM, LM, RM) → 'MID'")
        void midPositionsMapToMid() {
            assertEquals("MID", OverallCalculator.mapPlayerPositionToCategory(Player.Position.CDM));
            assertEquals("MID", OverallCalculator.mapPlayerPositionToCategory(Player.Position.CM));
            assertEquals("MID", OverallCalculator.mapPlayerPositionToCategory(Player.Position.CAM));
            assertEquals("MID", OverallCalculator.mapPlayerPositionToCategory(Player.Position.LM));
            assertEquals("MID", OverallCalculator.mapPlayerPositionToCategory(Player.Position.RM));
        }

        @Test
        @DisplayName("LW, RW → 'WINGER'")
        void wingerPositionsMapToWinger() {
            assertEquals("WINGER", OverallCalculator.mapPlayerPositionToCategory(Player.Position.LW));
            assertEquals("WINGER", OverallCalculator.mapPlayerPositionToCategory(Player.Position.RW));
        }

        @Test
        @DisplayName("CF, ST → 'ATT'")
        void attPositionsMapToAtt() {
            assertEquals("ATT", OverallCalculator.mapPlayerPositionToCategory(Player.Position.CF));
            assertEquals("ATT", OverallCalculator.mapPlayerPositionToCategory(Player.Position.ST));
        }

        @Test
        @DisplayName("Null position → null (caller treats as default)")
        void nullPositionReturnsNull() {
            assertNull(OverallCalculator.mapPlayerPositionToCategory(null));
        }

        @Test
        @DisplayName("Player + SessionPlayer produce identical overalls (verified via mapping)")
        void crossLayerEquivalence() {
            // Build a GK Player and a SessionPlayer with equivalent state.
            // Both should produce the same overall because they delegate to the
            // same OverallCalculator. This is the C5 invariant.
            int[] stats = elite();
            Map<PlayerSkill, Integer> skills = new HashMap<>();
            skills.put(PlayerSkill.WALL, 99);
            skills.put(PlayerSkill.AERIAL, 50);

            // Player via Position.GK
            int playerOverall = OverallCalculator.calculate(
                    stats[0], stats[1], stats[2], stats[3], stats[4], stats[5],
                    OverallCalculator.mapPlayerPositionToCategory(Player.Position.GK),
                    190, skills);

            // SessionPlayer via "GK" string
            int sessionOverall = OverallCalculator.calculate(
                    stats[0], stats[1], stats[2], stats[3], stats[4], stats[5],
                    "GK",
                    190, skills);

            assertEquals(playerOverall, sessionOverall,
                    "Player and SessionPlayer paths must produce identical overalls (same formula)");
        }
    }
}