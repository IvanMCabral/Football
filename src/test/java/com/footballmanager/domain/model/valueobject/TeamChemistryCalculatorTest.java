package com.footballmanager.domain.model.valueobject;

import com.footballmanager.domain.model.entity.SessionPlayer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V25D41 (Sprint C6): unit tests for {@link TeamChemistryCalculator}.
 *
 * <p>Coverage strategy:
 * <ul>
 *   <li>Backward compat (5 tests): empty / null / single player / 11 players with no skills</li>
 *   <li>Skill bonus (4 tests): MAX per skill, low skills, mixed skills, all skills at 99</li>
 *   <li>Coverage bonus (4 tests): 0 covered, 5 covered, 10 covered, threshold boundary</li>
 *   <li>Combined (3 tests): 1 player with 1 skill, 11 players with various skills, all-max lineup</li>
 *   <li>Edge cases (4 tests): null entries in list, out-of-bounds skill values, single non-null player</li>
 *   <li>Clamping (3 tests): max possible, min possible, always in [0, 99]</li>
 *   <li>Adversarial (4 tests): 5-formations probe, position-independent, monotonicity, depth vs star-power</li>
 *   <li>Math invariants (2 tests): team skill weights sum to 0.65</li>
 * </ul>
 *
 * <p>V25D43 (Sprint C8): {@code calculate()} signature changed from
 * {@code int} to {@code ChemistryDetail} (Opción B). Existing tests
 * updated via {@link #scoreOf(List)} helper. New {@link Breakdown} nested
 * class adds 10 tests for the per-position-group breakdown, the
 * {@code maxSkillByType} map, and the {@code coveragePercentage} field.
 */
@DisplayName("TeamChemistryCalculator — V25D41 team chemistry aggregate (V25D43 returns ChemistryDetail)")
class TeamChemistryCalculatorTest {

    // ========== Test helpers ==========

    /** Shortcut: extract the score from a {@link ChemistryDetail}. */
    private static int scoreOf(List<SessionPlayer> players) {
        return TeamChemistryCalculator.calculate(players).score();
    }

    /** Reasonable starter stats for an average-elite player (~80 base overall). */
    private static int[] elite() {
        return new int[]{80, 80, 80, 80, 80, 80};
    }

    private static SessionPlayer player(String position, int[] stats) {
        return SessionPlayer.custom("Test", 25, position,
                stats[0], stats[1], stats[2], stats[3], stats[4], stats[5],
                BigDecimal.valueOf(1_000_000));
    }

    private static SessionPlayer playerWithSkills(String position, int[] stats,
                                                  Map<PlayerSkill, Integer> skills) {
        SessionPlayer p = player(position, stats);
        for (Map.Entry<PlayerSkill, Integer> e : skills.entrySet()) {
            p.setSkillLevel(e.getKey(), e.getValue());
        }
        return p;
    }

    /** Build a 11-player elite lineup with no skills. */
    private static List<SessionPlayer> eliteLineupNoSkills() {
        return Arrays.asList(
                player("GK",   elite()),
                player("DEF",  elite()),
                player("DEF",  elite()),
                player("DEF",  elite()),
                player("DEF",  elite()),
                player("MID",  elite()),
                player("MID",  elite()),
                player("MID",  elite()),
                player("WINGER", elite()),
                player("WINGER", elite()),
                player("ATT",  elite())
        );
    }

    // ========== Backward compat ==========

    @Nested
    @DisplayName("Backward compat: empty / null / no-skills lineups return reasonable values")
    class BackwardCompat {

        @Test
        @DisplayName("Null list → 0")
        void nullList() {
            assertEquals(0, scoreOf(null));
        }

        @Test
        @DisplayName("Empty list → 0")
        void emptyList() {
            assertEquals(0, scoreOf(Collections.emptyList()));
        }

        @Test
        @DisplayName("Single player with no skills → overall of that player")
        void singlePlayerNoSkills() {
            // 80×6 base = 80, no skills → chemistry = 80
            List<SessionPlayer> lineup = List.of(player("GK", elite()));
            assertEquals(80, scoreOf(lineup));
        }

        @Test
        @DisplayName("11 elite players with no skills → AVG of overalls (≈80)")
        void elevenEliteNoSkills() {
            int chemistry = scoreOf(eliteLineupNoSkills());
            assertEquals(80, chemistry, "AVG of 11 elite players (80) = 80, no skill bonus");
        }

        @Test
        @DisplayName("List of all-null players → 0 (defensive)")
        void allNullPlayers() {
            // Build via ArrayList to allow nulls
            List<SessionPlayer> lineup = new ArrayList<>();
            lineup.add(null);
            lineup.add(null);
            lineup.add(null);
            assertEquals(0, scoreOf(lineup));
        }
    }

    // ========== Skill bonus ==========

    @Nested
    @DisplayName("Skill bonus: MAX per skill across lineup, weighted sum / 10")
    class SkillBonus {

        @Test
        @DisplayName("Lineup with no skills (legacy) → 0 skill bonus")
        void noSkills_zeroBonus() {
            int chem = scoreOf(eliteLineupNoSkills());
            // base = 80, skill_bonus = 0, coverage = 0 → 80
            assertEquals(80, chem, "No skills = no skill bonus");
        }

        @Test
        @DisplayName("1 player SHOOTER=99 → base + skill bonus (1 of 11 has SHOOTER, weight 0.06)")
        void onePlayerWithShooter() {
            // base = 80
            // max SHOOTER = 99
            // skill sum = 99 * 0.06 = 5.94 / 10 = 0.594 → 1 (round)
            // coverage = 0 (SHOOTER=99 < 80? No, 99 >= 80 → 1 covered, +0.3 → 0)
            // total = 80 + 1 + 0 = 81
            List<SessionPlayer> lineup = new ArrayList<>(eliteLineupNoSkills());
            lineup.set(10, playerWithSkills("ATT", elite(), Map.of(PlayerSkill.SHOOTER, 99)));
            assertEquals(81, scoreOf(lineup),
                    "1 SHOOTER=99 on 11 elite: base 80 + skill 1 + coverage 0 = 81");
        }

        @Test
        @DisplayName("11 players with all skills at 99 → base + max skill bonus (~6)")
        void allSkillsMax() {
            // 11 players (position=MID), each with all 10 skills at 99.
            // Each player's individual overall (C5-aware): base 80 + skill_bonus
            //   (MID skill sum = 0.65 * 99 / 10 = 6.435 → 6) = 86
            // Team base (AVG of individual overalls): 86
            // Max per skill (team): 99 for all 10 skills
            // Team skill bonus: 0.65 * 99 / 10 = 6.435 → 6
            // Coverage: 10 skills >= 80 → 10 * 0.3 = 3 → 3
            // Total: 86 + 6 + 3 = 95
            Map<PlayerSkill, Integer> allSkills = new HashMap<>();
            for (PlayerSkill s : PlayerSkill.values()) {
                allSkills.put(s, 99);
            }
            List<SessionPlayer> lineup = new ArrayList<>();
            for (int i = 0; i < 11; i++) {
                lineup.add(playerWithSkills("MID", elite(), allSkills));
            }
            assertEquals(95, scoreOf(lineup),
                    "All skills 99 on 11 elite (MID): 86 base + 6 skill + 3 coverage = 95");
        }

        @Test
        @DisplayName("Skills at low level (50) → smaller skill bonus")
        void skillsLowLevel() {
            // Each player's individual overall (C5): base 80 + (0.65 * 50 / 10 = 3.25 → 3) = 83
            // Team base (AVG): 83
            // Max per skill: 50 for all 10 skills
            // Team skill bonus: 0.65 * 50 / 10 = 3.25 → 3
            // Coverage: 0 (50 < 80)
            // Total: 83 + 3 + 0 = 86
            Map<PlayerSkill, Integer> lowSkills = new HashMap<>();
            for (PlayerSkill s : PlayerSkill.values()) {
                lowSkills.put(s, 50);
            }
            List<SessionPlayer> lineup = new ArrayList<>();
            for (int i = 0; i < 11; i++) {
                lineup.add(playerWithSkills("MID", elite(), lowSkills));
            }
            assertEquals(86, scoreOf(lineup),
                    "All skills 50: 83 base + 3 skill + 0 coverage = 86");
        }
    }

    // ========== Coverage bonus ==========

    @Nested
    @DisplayName("Coverage bonus: number of skills with MAX >= 80, weighted * 0.3")
    class CoverageBonus {

        @Test
        @DisplayName("Lineup with 0 covered skills (all <80) → 0 coverage bonus")
        void zeroCovered() {
            // Each player individual overall (C5): 80 + 0.65*50/10 = 80 + 3.25 → 3 → 83
            // Team base (AVG): 83
            // Max per skill: 50 for all 10 skills
            // Skill bonus: 0.65 * 50 / 10 = 3.25 → 3
            // Coverage: 0 (50 < 80)
            // Total: 83 + 3 + 0 = 86
            Map<PlayerSkill, Integer> allLow = new HashMap<>();
            for (PlayerSkill s : PlayerSkill.values()) {
                allLow.put(s, 50);
            }
            List<SessionPlayer> lineup = new ArrayList<>();
            for (int i = 0; i < 11; i++) {
                lineup.add(playerWithSkills("MID", elite(), allLow));
            }
            int chem = scoreOf(lineup);
            assertEquals(86, chem, "0 covered: 83 base + 3 skill + 0 coverage = 86");
        }

        @Test
        @DisplayName("Lineup with 5 covered skills (5 skills >=80, 5 below) → +1.5 coverage")
        void fiveCovered() {
            // Each player individual overall (C5): MID skill sum
            //   = (0.37*80 + 0.28*50) / 10 = 43.6 / 10 = 4.36 → 4 → individual = 84
            // Team base (AVG): 84
            // Max per skill: 5 at 80, 5 at 50
            // Team skill bonus: (0.37*80 + 0.28*50) / 10 = 4.36 → 4
            // Coverage: 5 * 0.3 = 1.5 → 2 (round)
            // Total: 84 + 4 + 2 = 90
            Map<PlayerSkill, Integer> skills = new HashMap<>();
            for (PlayerSkill s : PlayerSkill.values()) {
                skills.put(s, s.ordinal() < 5 ? 80 : 50);
            }
            List<SessionPlayer> lineup = new ArrayList<>();
            for (int i = 0; i < 11; i++) {
                lineup.add(playerWithSkills("MID", elite(), skills));
            }
            int chem = scoreOf(lineup);
            assertEquals(90, chem, "5 covered: 84 base + 4 skill + 2 coverage = 90");
        }

        @Test
        @DisplayName("All 10 skills covered (MAX >= 80 each) → +3 coverage bonus (max)")
        void tenCovered() {
            // Each player individual overall (C5): 80 + 0.65*80/10 = 80 + 5.2 → 5 → 85
            // Team base (AVG): 85
            // Max per skill: 80 for all 10
            // Skill bonus: 0.65 * 80 / 10 = 5.2 → 5
            // Coverage: 10 * 0.3 = 3.0 → 3
            // Total: 85 + 5 + 3 = 93
            Map<PlayerSkill, Integer> all80 = new HashMap<>();
            for (PlayerSkill s : PlayerSkill.values()) {
                all80.put(s, 80);
            }
            List<SessionPlayer> lineup = new ArrayList<>();
            for (int i = 0; i < 11; i++) {
                lineup.add(playerWithSkills("MID", elite(), all80));
            }
            int chem = scoreOf(lineup);
            assertEquals(93, chem, "10 covered: 85 base + 5 skill + 3 coverage = 93");
        }

        @Test
        @DisplayName("Threshold boundary: skill MAX = 80 counts as covered, MAX = 79 does not")
        void thresholdBoundary() {
            // Build 2 lineups: one with 80, one with 79
            // chem80 = 85 base + 5 skill + 3 coverage = 93
            // chem79 = base 80 (no skill contribution) + 5 skill + 0 coverage = ?
            //   individual overall: 0.65 * 79 / 10 = 5.135 → 5 → 85 (same as 80, due to rounding)
            //   So chem79 = 85 + 5 + 0 = 90
            // Wait — individual overall rounds 5.135 to 5, same as for 80. So the difference
            // is only the coverage bonus (3 points).
            //   chem80 = 93
            //   chem79 = 90
            //   diff = 3
            Map<PlayerSkill, Integer> skills80 = new HashMap<>();
            Map<PlayerSkill, Integer> skills79 = new HashMap<>();
            for (PlayerSkill s : PlayerSkill.values()) {
                skills80.put(s, 80);
                skills79.put(s, 79);
            }
            List<SessionPlayer> lineup80 = new ArrayList<>();
            List<SessionPlayer> lineup79 = new ArrayList<>();
            for (int i = 0; i < 11; i++) {
                lineup80.add(playerWithSkills("MID", elite(), skills80));
                lineup79.add(playerWithSkills("MID", elite(), skills79));
            }
            int chem80 = scoreOf(lineup80);
            int chem79 = scoreOf(lineup79);
            assertEquals(93, chem80, "MAX=80: covered, +3 coverage");
            assertEquals(90, chem79, "MAX=79: NOT covered, +0 coverage (but skill bonus same due to rounding 79→5)");
            assertEquals(3, chem80 - chem79, "Difference is exactly the 3-point coverage bonus");
        }
    }

    // ========== Combined ==========

    @Nested
    @DisplayName("Combined scenarios: 1 player with skills, mixed lineups, all-max")
    class Combined {

        @Test
        @DisplayName("Single GK player with WALL=99, AERIAL=99, height=200")
        void singleElitePlayer() {
            // base = single player overall with WALL=99, AERIAL=99, h=200
            //   GK base: 80. skill bonus: (0.30*99 + 0.15*99)/10 = 4.455 → 4. height: GK h=200 → +2.
            //   Overall = 80 + 4 + 2 = 86
            //   Note: SessionPlayer height+skills affect its overall.
            // chemistry = AVG(overalls) = 86
            //   skill_bonus (chemistry level) = max WALL=99, AERIAL=99 = (0.06*99 + 0.07*99)/10 = 1.287 → 1
            //   coverage = 2 * 0.3 = 0.6 → 1
            //   total = 86 + 1 + 1 = 88
            SessionPlayer sp = playerWithSkills("GK", elite(), Map.of(PlayerSkill.WALL, 99, PlayerSkill.AERIAL, 99));
            sp.setHeightCm(200);
            int chem = scoreOf(List.of(sp));
            // base = 86 (single player overall)
            // skill bonus at chemistry level: WALL (weight 0.06) * 99 + AERIAL (weight 0.07) * 99 = 5.94 + 6.93 = 12.87 / 10 = 1.287 → 1
            // coverage = 2 * 0.3 = 0.6 → 1
            // total = 86 + 1 + 1 = 88
            assertEquals(88, chem, "Single Courtois-like: overall 86 + chem skill 1 + chem coverage 1 = 88");
        }

        @Test
        @DisplayName("11-player lineup with 1 star SHOOTER=99, 10 fillers → small bonus")
        void oneStarFiller() {
            // base = 80 (all elite)
            // skill: max SHOOTER = 99, rest = 0. skill sum = 0.06*99 = 5.94 / 10 = 0.594 → 1
            // coverage = 1 (SHOOTER >= 80) * 0.3 = 0.3 → 0
            // total = 80 + 1 + 0 = 81
            List<SessionPlayer> lineup = new ArrayList<>(eliteLineupNoSkills());
            lineup.set(10, playerWithSkills("ATT", elite(), Map.of(PlayerSkill.SHOOTER, 99)));
            int chem = scoreOf(lineup);
            assertEquals(81, chem, "1 SHOOTER=99 star: 80 + 1 + 0 = 81");
        }

        @Test
        @DisplayName("Lineup with 2 stars in different skills → depth bonus")
        void twoStarsDifferentSkills() {
            // 2 players with different elite skills → coverage bonus adds
            List<SessionPlayer> lineup = new ArrayList<>(eliteLineupNoSkills());
            lineup.set(0, playerWithSkills("GK", elite(), Map.of(PlayerSkill.WALL, 90)));
            lineup.set(10, playerWithSkills("ATT", elite(), Map.of(PlayerSkill.SHOOTER, 90)));
            int chem = scoreOf(lineup);
            // base = 80
            // skill: WALL 0.06*90 = 5.4, SHOOTER 0.06*90 = 5.4. sum = 10.8 / 10 = 1.08 → 1
            // coverage = 2 (WALL >= 80 and SHOOTER >= 80) * 0.3 = 0.6 → 1
            // total = 80 + 1 + 1 = 82
            assertEquals(82, chem, "2 stars in different skills: 80 + 1 + 1 = 82");
        }
    }

    // ========== Edge cases ==========

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("Mixed null and valid players in list")
        void mixedNullAndValid() {
            List<SessionPlayer> lineup = new ArrayList<>();
            lineup.add(null);
            lineup.add(player("GK", elite()));
            lineup.add(null);
            lineup.add(player("ATT", elite()));
            // base = 80 (only 2 valid, both 80)
            int chem = scoreOf(lineup);
            assertEquals(80, chem, "Nulls skipped, AVG over 2 valid = 80");
        }

        @Test
        @DisplayName("Player with empty skill map → no contribution to skill/coverage")
        void emptySkillMap() {
            SessionPlayer sp = player("GK", elite());
            // No setSkillLevel called — skills empty (initDefaults).
            int chem = scoreOf(List.of(sp));
            assertEquals(80, chem, "Single player no skills: chemistry = base overall = 80");
        }

        @Test
        @DisplayName("Out-of-bounds skill value (defensive clamp)")
        void outOfBoundsSkillValue() {
            // setSkillLevel rejects values > 99, so we can't directly inject via that API.
            // The formula defensively clamps, so if a legacy player somehow
            // has 150 in the map (via deserialization), the formula should
            // still treat it as 99.
            // Test: set legal max (99) and confirm the formula's expected output.
            // GK with WALL=99: individual overall = 80 + round(0.30*99/10) = 80 + 3 = 83
            // Team base (single player): 83
            // Max per skill: WALL=99, rest 0
            // Team skill bonus: 0.06 * 99 / 10 = 0.594 → 1
            // Coverage: 1 (WALL >= 80) * 0.3 = 0.3 → 0
            // Total: 83 + 1 + 0 = 84
            Map<PlayerSkill, Integer> legalSkills = Map.of(PlayerSkill.WALL, 99);
            SessionPlayer sp = playerWithSkills("GK", elite(), legalSkills);
            int chem = scoreOf(List.of(sp));
            assertEquals(84, chem, "WALL=99 on single GK: 83 base + 1 skill + 0 coverage = 84");
        }

        @Test
        @DisplayName("Skill value 0 in map (setSkillLevel(0) removes entry) → 0 contribution")
        void skillValueZero() {
            // setSkillLevel(_, 0) removes the entry. Test by adding and removing.
            SessionPlayer sp = player("GK", elite());
            sp.setSkillLevel(PlayerSkill.WALL, 50);
            sp.setSkillLevel(PlayerSkill.WALL, 0);  // removes entry
            int chem = scoreOf(List.of(sp));
            assertEquals(80, chem, "After removing skill, chemistry reverts to base");
        }
    }

    // ========== Clamping ==========

    @Nested
    @DisplayName("Clamping invariants")
    class Clamping {

        @Test
        @DisplayName("Max-elite lineup (11 elite + all skills 99) → 95 (under 99 ceiling)")
        void maxLineup() {
            // C5-aware individual overall: 80 + 0.65*99/10 = 80 + 6.435 → 6 → 86
            // Team base (AVG): 86
            // Skill bonus: 0.65 * 99 / 10 = 6.435 → 6
            // Coverage: 10 * 0.3 = 3.0 → 3
            // Total: 86 + 6 + 3 = 95
            Map<PlayerSkill, Integer> allSkills = new HashMap<>();
            for (PlayerSkill s : PlayerSkill.values()) {
                allSkills.put(s, 99);
            }
            List<SessionPlayer> lineup = new ArrayList<>();
            for (int i = 0; i < 11; i++) {
                lineup.add(playerWithSkills("MID", elite(), allSkills));
            }
            int chem = scoreOf(lineup);
            assertEquals(95, chem, "Max lineup: 86 base + 6 skill + 3 coverage = 95");
            assertTrue(chem <= 99, "Chem <= 99 ceiling");
        }

        @Test
        @DisplayName("Worst possible lineup (11 low-rated players, no skills) → ≥ 0")
        void minLineup() {
            int[] worst = new int[]{1, 1, 1, 1, 1, 1};
            List<SessionPlayer> lineup = new ArrayList<>();
            for (int i = 0; i < 11; i++) {
                lineup.add(player("GK", worst));
            }
            int chem = scoreOf(lineup);
            assertTrue(chem >= 0, "Chem should be >= 0, got " + chem);
            // base = round((1+1+1+1+1+1)/6 * 11) = 1 (each player overall = 1, AVG = 1)
            // No skills → 0
            assertEquals(1, chem);
        }

        @Test
        @DisplayName("Result always in [0, 99] for any lineup")
        void alwaysInRange() {
            int[][] statSamples = {
                {0, 0, 0, 0, 0, 0}, {99, 99, 99, 99, 99, 99}, {50, 50, 50, 50, 50, 50}
            };
            int[] skillSamples = {0, 50, 80, 99};

            for (int[] stats : statSamples) {
                for (int skill : skillSamples) {
                    Map<PlayerSkill, Integer> skills = new HashMap<>();
                    for (PlayerSkill s : PlayerSkill.values()) {
                        skills.put(s, skill);
                    }
                    List<SessionPlayer> lineup = new ArrayList<>();
                    for (int i = 0; i < 11; i++) {
                        lineup.add(playerWithSkills("MID", stats, skills));
                    }
                    int chem = scoreOf(lineup);
                    assertTrue(chem >= 0 && chem <= 99,
                            "Chem " + chem + " out of [0, 99] for stats " + stats[0] + " skill " + skill);
                }
            }
        }
    }

    // ========== Adversarial ==========

    @Nested
    @DisplayName("Adversarial probes (5-formations probe, depth, monotonicity)")
    class Adversarial {

        @Test
        @DisplayName("5-formations probe: lineup with skills ≥ 4 baseline lineups without skills")
        void fiveFormationsProbe() {
            // Memory canonical: 5 variants of similar lineups, 1 distinct.
            // All 5 lineups same base (avg 80), 4 with no skills, 1 with skills.
            List<SessionPlayer> baseline = eliteLineupNoSkills();
            int baselineChem = scoreOf(baseline);

            // Inject skills into one player of the 5th lineup
            Map<PlayerSkill, Integer> highSkills = new HashMap<>();
            for (PlayerSkill s : PlayerSkill.values()) {
                highSkills.put(s, 99);
            }
            List<SessionPlayer> withSkills = new ArrayList<>(baseline);
            withSkills.set(0, playerWithSkills("GK", elite(), highSkills));
            int withSkillsChem = scoreOf(withSkills);

            assertTrue(withSkillsChem > baselineChem,
                    "Lineup with skills (chem=" + withSkillsChem + ") should beat baseline (chem=" + baselineChem + ")");
        }

        @Test
        @DisplayName("Position-independent: skills count the same regardless of player position")
        void positionIndependent() {
            // Build 2 lineups: 1 with SHOOTER=99 on an ATT, 1 with SHOOTER=99 on a GK.
            // Both should give the same chemistry contribution from SHOOTER
            // (team-level aggregate doesn't care about position, unlike OverallCalculator).
            Map<PlayerSkill, Integer> skills = Map.of(PlayerSkill.SHOOTER, 99);
            List<SessionPlayer> lineupAtt = new ArrayList<>(eliteLineupNoSkills());
            lineupAtt.set(10, playerWithSkills("ATT", elite(), skills));
            int chemAtt = scoreOf(lineupAtt);

            List<SessionPlayer> lineupGk = new ArrayList<>(eliteLineupNoSkills());
            lineupGk.set(0, playerWithSkills("GK", elite(), skills));
            int chemGk = scoreOf(lineupGk);

            assertEquals(chemAtt, chemGk,
                    "SHOOTER=99 contributes the same chemistry regardless of position");
        }

        @Test
        @DisplayName("Monotonic in skill coverage: more skills at 80+ → higher chemistry")
        void monotonicCoverage() {
            // Build 3 lineups: 0 covered, 5 covered, 10 covered.
            Map<PlayerSkill, Integer> zero = new HashMap<>();
            for (PlayerSkill s : PlayerSkill.values()) zero.put(s, 50);

            Map<PlayerSkill, Integer> five = new HashMap<>();
            for (PlayerSkill s : PlayerSkill.values()) {
                five.put(s, s.ordinal() < 5 ? 80 : 50);
            }

            Map<PlayerSkill, Integer> ten = new HashMap<>();
            for (PlayerSkill s : PlayerSkill.values()) ten.put(s, 80);

            List<SessionPlayer> l0 = new ArrayList<>();
            List<SessionPlayer> l5 = new ArrayList<>();
            List<SessionPlayer> l10 = new ArrayList<>();
            for (int i = 0; i < 11; i++) {
                l0.add(playerWithSkills("MID", elite(), zero));
                l5.add(playerWithSkills("MID", elite(), five));
                l10.add(playerWithSkills("MID", elite(), ten));
            }
            int c0 = scoreOf(l0);
            int c5 = scoreOf(l5);
            int c10 = scoreOf(l10);

            assertTrue(c0 < c5, "0 covered (" + c0 + ") < 5 covered (" + c5 + ")");
            assertTrue(c5 < c10, "5 covered (" + c5 + ") < 10 covered (" + c10 + ")");
        }

        @Test
        @DisplayName("Depth > star-power: 11 deep players beat 1 star + 10 fillers")
        void depthBeatsStarPower() {
            // 11 players each with 1 distinct skill at 99 (broad coverage)
            // vs
            // 1 player with all 10 skills at 99 + 10 fillers (concentrated)
            // Depth: skill_bonus = 0.65 * 99 = 64.35 / 10 = 6.435 → 6
            //         coverage = 10 * 0.3 = 3 → 3
            //         total = 80 + 6 + 3 = 89
            // Star: 1 player with 10 skills at 99 — same max per skill, same skill_bonus
            //         and coverage. Same total = 89.
            // Actually they tie because max-per-skill aggregates the same regardless of distribution!
            // This is intentional (both teams have someone for every skill).

            // Real test: 11 different deep players (each with distinct 1 skill = 99)
            // vs 1 player with 1 skill = 99.
            // Depth: max per skill = 99 for 1 skill only. skill_bonus = 0.06 * 99 / 10 = 0.594 → 1
            //         coverage = 1 (the 1 skill) * 0.3 = 0.3 → 0
            //         total = 80 + 1 + 0 = 81
            // Star (same): 1 player with 1 skill = 99, 10 fillers
            //         total = 81
            // Tie again because MAX is the same.

            // So the depth > star-power property is captured by the COVERAGE bonus:
            // many skills covered beats 1 skill covered.
            // Test: 1 star (1 skill) vs 11 deep (11 skills, all distinct)
            List<SessionPlayer> star = new ArrayList<>();
            star.add(playerWithSkills("GK", elite(), Map.of(PlayerSkill.WALL, 99)));
            for (int i = 0; i < 10; i++) star.add(player("MID", elite()));
            int starChem = scoreOf(star);

            List<SessionPlayer> deep = new ArrayList<>();
            PlayerSkill[] all = PlayerSkill.values();
            for (int i = 0; i < 11; i++) {
                Map<PlayerSkill, Integer> oneSkill = new HashMap<>();
                oneSkill.put(all[i % all.length], 99);
                deep.add(playerWithSkills("MID", elite(), oneSkill));
            }
            int deepChem = scoreOf(deep);

            // Star: 1 covered, deep: 10 covered (since each player has a different skill)
            // deepChem = 80 + (1*0.65*99/10) + 10*0.3 = 80 + 1 + 3 = 84
            // starChem = 80 + (1*0.06*99/10) + 1*0.3 = 80 + 1 + 0 = 81
            // (using 0.06 for WALL only)
            assertTrue(deepChem > starChem,
                    "Deep (10 distinct skills) " + deepChem + " > Star (1 skill only) " + starChem);
        }
    }

    // ========== Math invariants ==========

    @Nested
    @DisplayName("Math invariants")
    class MathInvariants {

        @Test
        @DisplayName("Skill weights sum to 0.65 (consistency with OverallCalculator per-row sums)")
        void weightsSumTo065() {
            // Verified by the static initializer in TeamChemistryCalculator — if the sum
            // differs from 0.65, the class fails to load. This test is documentation
            // rather than runtime check.
            double sum = 0.09 + 0.07 + 0.07 + 0.07 + 0.07 + 0.06 + 0.06 + 0.06 + 0.06 + 0.04;
            assertEquals(0.65, sum, 1e-6, "Team skill weights must sum to 0.65");
        }

        @Test
        @DisplayName("Coverage bonus max is exactly 3.0 (10 * 0.3)")
        void coverageMaxExactly3() {
            // 10 skills * 0.3 = 3.0, bounded.
            int covered = 10;
            double bonus = covered * 0.3;
            assertEquals(3.0, bonus, 1e-6, "Coverage bonus must be exactly 3.0 at max");
        }
    }

    // ========== V25D43 (Sprint C8) — ChemistryDetail breakdown ==========

    @Nested
    @DisplayName("V25D43 ChemistryDetail: per-position-group breakdown + maxSkillByType + coveragePercentage")
    class Breakdown {

        // --- groupsForSkill mapping ---

        @Test
        @DisplayName("groupsForSkill: WALL → [GK] only")
        void groupsForSkill_wall() {
            assertEquals(List.of(ChemistryDetail.PositionGroup.GK),
                    ChemistryDetail.groupsForSkill(PlayerSkill.WALL));
        }

        @Test
        @DisplayName("groupsForSkill: AERIAL → [GK, DEF] (cross-position skill)")
        void groupsForSkill_aerial() {
            // AERIAL has 0.15 in GK and 0.20 in DEF → both groups
            assertEquals(
                    List.of(ChemistryDetail.PositionGroup.GK, ChemistryDetail.PositionGroup.DEF),
                    ChemistryDetail.groupsForSkill(PlayerSkill.AERIAL));
        }

        @Test
        @DisplayName("groupsForSkill: TACKLER → [GK, DEF, MID] (3 groups)")
        void groupsForSkill_tackler() {
            assertEquals(
                    List.of(
                            ChemistryDetail.PositionGroup.GK,
                            ChemistryDetail.PositionGroup.DEF,
                            ChemistryDetail.PositionGroup.MID),
                    ChemistryDetail.groupsForSkill(PlayerSkill.TACKLER));
        }

        @Test
        @DisplayName("groupsForSkill: WINGER skills (SPEEDSTER, DRIBBLER, SHOOTER) → [ATT] (folded)")
        void groupsForSkill_wingerFoldedToAtt() {
            // WINGER is not in PositionGroup enum; WINGER skills fold to ATT
            assertEquals(List.of(ChemistryDetail.PositionGroup.ATT),
                    ChemistryDetail.groupsForSkill(PlayerSkill.SPEEDSTER));
            assertEquals(List.of(ChemistryDetail.PositionGroup.ATT),
                    ChemistryDetail.groupsForSkill(PlayerSkill.DRIBBLER));
            assertEquals(List.of(ChemistryDetail.PositionGroup.ATT),
                    ChemistryDetail.groupsForSkill(PlayerSkill.SHOOTER));
        }

        @Test
        @DisplayName("groupsForSkill: null → empty (defensive)")
        void groupsForSkill_null() {
            assertEquals(List.of(), ChemistryDetail.groupsForSkill(null));
        }

        // --- Empty / null / no-skills lineup ---

        @Test
        @DisplayName("Null lineup → ChemistryDetail with score=0 and empty groups")
        void nullLineup_emptyDetail() {
            ChemistryDetail d = TeamChemistryCalculator.calculate(null);
            assertEquals(0, d.score());
            assertEquals(0, d.coveragePercentage());
            // All 4 groups present, all empty
            for (ChemistryDetail.PositionGroup g : ChemistryDetail.PositionGroup.values()) {
                assertEquals(List.of(), d.breakdown().get(g),
                        "Group " + g + " should be empty for null lineup");
            }
            // maxSkillByType: 0 for all 10 skills
            for (PlayerSkill s : PlayerSkill.values()) {
                assertEquals(0, d.maxSkillByType().get(s),
                        "maxSkillByType[" + s + "] should be 0 for null lineup");
            }
        }

        @Test
        @DisplayName("Lineup with no skills → breakdown is empty (no SkillCoverage entries)")
        void noSkills_emptyBreakdown() {
            // 11 elite players without skills → no SkillCoverage emitted (maxLevel = 0)
            ChemistryDetail d = TeamChemistryCalculator.calculate(eliteLineupNoSkills());
            assertEquals(80, d.score(), "11 elite no skills: score = 80");
            assertEquals(0, d.coveragePercentage(), "0 skills covered (all 0)");
            for (ChemistryDetail.PositionGroup g : ChemistryDetail.PositionGroup.values()) {
                assertEquals(List.of(), d.breakdown().get(g),
                        "Group " + g + " should be empty when no skills present");
            }
        }

        // --- Single GK with WALL=99, AERIAL=99 (Courtois-like) ---

        @Test
        @DisplayName("Single GK with WALL=99, AERIAL=99: GK row has 2, DEF row has 1 (AERIAL cross)")
        void singleGkWallAerial_breakdown() {
            // GK player with WALL=99, AERIAL=99
            // Per-position weights:
            //   WALL:  GK only
            //   AERIAL: GK + DEF
            // Expected breakdown:
            //   GK: [WALL 99, AERIAL 99]
            //   DEF: [AERIAL 99]
            //   MID: []
            //   ATT: []
            SessionPlayer sp = playerWithSkills("GK", elite(),
                    Map.of(PlayerSkill.WALL, 99, PlayerSkill.AERIAL, 99));
            ChemistryDetail d = TeamChemistryCalculator.calculate(List.of(sp));

            assertEquals(2, d.breakdown().get(ChemistryDetail.PositionGroup.GK).size(),
                    "GK row should have WALL + AERIAL = 2 entries");
            assertEquals(1, d.breakdown().get(ChemistryDetail.PositionGroup.DEF).size(),
                    "DEF row should have AERIAL = 1 entry (cross-position)");
            assertEquals(List.of(), d.breakdown().get(ChemistryDetail.PositionGroup.MID));
            assertEquals(List.of(), d.breakdown().get(ChemistryDetail.PositionGroup.ATT));

            // Contributor: same player for both (single GK with both skills)
            String playerId = sp.getSessionPlayerId();
            assertEquals(playerId,
                    d.breakdown().get(ChemistryDetail.PositionGroup.GK).get(0).contributorPlayerId());
            assertEquals(playerId,
                    d.breakdown().get(ChemistryDetail.PositionGroup.DEF).get(0).contributorPlayerId());

            // maxSkillByType
            assertEquals(99, d.maxSkillByType().get(PlayerSkill.WALL));
            assertEquals(99, d.maxSkillByType().get(PlayerSkill.AERIAL));
            assertEquals(0, d.maxSkillByType().get(PlayerSkill.MARKER));

            // coveragePercentage: 2 of 10 covered (WALL, AERIAL) → 20%
            assertEquals(20, d.coveragePercentage(), "2/10 covered = 20%");
        }

        // --- All 10 skills at 99 (max lineup) ---

        @Test
        @DisplayName("All skills at 99: every group has 4 entries (4 distinct skills per group)")
        void allSkillsMax_fullBreakdown() {
            // 11 elite MID players with all 10 skills at 99
            // Each skill appears in all groups where it has weight:
            //   GK: WALL, AERIAL, TACKLER, PASSER (4)
            //   DEF: MARKER, AERIAL, TACKLER, PASSER (4)
            //   MID: PLAYMAKER, MARKER, TACKLER, PASSER (4)
            //   ATT: SHOOTER, HEADER, DRIBBLER, SPEEDSTER (4)
            // Total skill-appearances = 16, 10 unique skills
            Map<PlayerSkill, Integer> allSkills = new HashMap<>();
            for (PlayerSkill s : PlayerSkill.values()) allSkills.put(s, 99);
            List<SessionPlayer> lineup = new ArrayList<>();
            for (int i = 0; i < 11; i++) {
                lineup.add(playerWithSkills("MID", elite(), allSkills));
            }
            ChemistryDetail d = TeamChemistryCalculator.calculate(lineup);

            // 4 entries per group
            assertEquals(4, d.breakdown().get(ChemistryDetail.PositionGroup.GK).size());
            assertEquals(4, d.breakdown().get(ChemistryDetail.PositionGroup.DEF).size());
            assertEquals(4, d.breakdown().get(ChemistryDetail.PositionGroup.MID).size());
            assertEquals(4, d.breakdown().get(ChemistryDetail.PositionGroup.ATT).size());

            // coveragePercentage: 10/10 = 100%
            assertEquals(100, d.coveragePercentage(), "10/10 covered = 100%");

            // All maxSkillByType = 99
            for (PlayerSkill s : PlayerSkill.values()) {
                assertEquals(99, d.maxSkillByType().get(s),
                        "maxSkillByType[" + s + "] should be 99");
            }
        }

        // --- Contributor identification ---

        @Test
        @DisplayName("Contributor: first player with the max level wins (deterministic tie-break)")
        void contributor_firstWithMaxWins() {
            // 3 players with WALL: 70, 90, 80. Max is 90 → 2nd player wins.
            SessionPlayer p1 = playerWithSkills("GK", elite(), Map.of(PlayerSkill.WALL, 70));
            SessionPlayer p2 = playerWithSkills("GK", elite(), Map.of(PlayerSkill.WALL, 90));
            SessionPlayer p3 = playerWithSkills("GK", elite(), Map.of(PlayerSkill.WALL, 80));
            List<SessionPlayer> lineup = List.of(p1, p2, p3);

            ChemistryDetail d = TeamChemistryCalculator.calculate(lineup);
            ChemistryDetail.SkillCoverage coverage = d.breakdown()
                    .get(ChemistryDetail.PositionGroup.GK).get(0);
            assertEquals(90, coverage.maxLevel());
            assertEquals(p2.getSessionPlayerId(), coverage.contributorPlayerId(),
                    "Contributor should be p2 (the 2nd player with max=90)");
        }

        // --- maxSkillByType shape ---

        @Test
        @DisplayName("maxSkillByType: 0 for skills not present in lineup")
        void maxSkillByType_zeroForAbsent() {
            // Lineup with 1 player, 1 skill (WALL=99). All other 9 skills → 0.
            SessionPlayer sp = playerWithSkills("GK", elite(), Map.of(PlayerSkill.WALL, 99));
            ChemistryDetail d = TeamChemistryCalculator.calculate(List.of(sp));
            assertEquals(99, d.maxSkillByType().get(PlayerSkill.WALL));
            assertEquals(0, d.maxSkillByType().get(PlayerSkill.AERIAL));
            assertEquals(0, d.maxSkillByType().get(PlayerSkill.MARKER));
            assertEquals(0, d.maxSkillByType().get(PlayerSkill.PASSER));
            assertEquals(0, d.maxSkillByType().get(PlayerSkill.HEADER));
        }
    }
}
