package com.footballmanager.domain.model.entity;

import com.footballmanager.domain.model.valueobject.PlayerSkill;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V25D40 (Sprint C5): integration tests for {@link SessionPlayer#calculateOverall()}
 * after the refactor to delegate to the shared {@link com.footballmanager.domain.model.valueobject.OverallCalculator}.
 *
 * <p>Strategy: focused tests on the SessionPlayer-specific contract
 * (signature returns {@code Integer}, {@code hasNullAttributes} returns 50,
 * string position categories). The full formula coverage lives in
 * {@code OverallCalculatorTest} — these tests verify the integration glue.
 */
@DisplayName("SessionPlayer.calculateOverall — V25D40 shared-formula integration")
class SessionPlayerOverallV25D40Test {

    private static int[] elite() {
        return new int[]{80, 80, 80, 80, 80, 80};
    }

    /** Build a SessionPlayer with all 6 stats and a position. */
    private static SessionPlayer player(String position, int[] stats) {
        if (stats.length != 6) {
            throw new IllegalArgumentException("Need 6 stats");
        }
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

    private static SessionPlayer playerWithHeight(String position, int[] stats, int heightCm) {
        SessionPlayer p = player(position, stats);
        p.setHeightCm(heightCm);
        return p;
    }

    private static SessionPlayer playerWithBoth(String position, int[] stats,
                                                 int heightCm, Map<PlayerSkill, Integer> skills) {
        SessionPlayer p = player(position, stats);
        p.setHeightCm(heightCm);
        for (Map.Entry<PlayerSkill, Integer> e : skills.entrySet()) {
            p.setSkillLevel(e.getKey(), e.getValue());
        }
        return p;
    }

    // ========== Backward compat (pre-C5 contract preserved) ==========

    @Test
    @DisplayName("Backward compat: no height + no skills → base formula unchanged")
    void backwardCompat_noExtensions() {
        // 50×6 with position=ATT: ATT weights sum to 1.0 → base = 50
        SessionPlayer p = player("ATT", new int[]{50, 50, 50, 50, 50, 50});
        assertEquals(50, p.calculateOverall(), "ATT 50×6 should match pre-C5 formula");
    }

    @Test
    @DisplayName("Backward compat: GK elite 80×6 → 80 (no extensions)")
    void backwardCompat_gk_elite() {
        SessionPlayer p = player("GK", elite());
        assertEquals(80, p.calculateOverall());
    }

    @Test
    @DisplayName("Backward compat: DEF elite 80×6 → 80")
    void backwardCompat_def_elite() {
        SessionPlayer p = player("DEF", elite());
        assertEquals(80, p.calculateOverall());
    }

    @Test
    @DisplayName("Backward compat: MID elite 80×6 → 80")
    void backwardCompat_mid_elite() {
        SessionPlayer p = player("MID", elite());
        assertEquals(80, p.calculateOverall());
    }

    @Test
    @DisplayName("Backward compat: WINGER elite 80×6 → 80")
    void backwardCompat_winger_elite() {
        SessionPlayer p = player("WINGER", elite());
        assertEquals(80, p.calculateOverall());
    }

    @Test
    @DisplayName("Backward compat: ATT elite 80×6 → 80")
    void backwardCompat_att_elite() {
        SessionPlayer p = player("ATT", elite());
        assertEquals(80, p.calculateOverall());
    }

    // ========== Skill only (height=null) ==========

    @Test
    @DisplayName("Skill only: GK WALL=99 → base + 3 (matches OverallCalculator)")
    void skillOnly_gk_wall99() {
        // base = 80; skill sum = 99 * 0.30 = 29.7 / 10 = 2.97 → 3
        int base = player("GK", elite()).calculateOverall();
        int withSkill = playerWithSkills("GK", elite(), Map.of(PlayerSkill.WALL, 99))
                .calculateOverall();
        assertEquals(base + 3, withSkill, "GK WALL=99 should add 3");
    }

    @Test
    @DisplayName("Skill only: GK AERIAL=99 → base + 1 (matches OverallCalculator)")
    void skillOnly_gk_aerial99() {
        // base = 80; skill sum = 99 * 0.15 = 14.85 / 10 = 1.485 → 1 (round)
        int base = player("GK", elite()).calculateOverall();
        int withSkill = playerWithSkills("GK", elite(), Map.of(PlayerSkill.AERIAL, 99))
                .calculateOverall();
        assertEquals(base + 1, withSkill, "GK AERIAL=99 should add 1 (round 1.485)");
    }

    @Test
    @DisplayName("Skill only: WINGER SPEEDSTER=99 + DRIBBLER=99 → base + 5 (matches OverallCalculator)")
    void skillOnly_winger_speedsterDribbler99() {
        int base = player("WINGER", elite()).calculateOverall();
        int withSkills = playerWithSkills("WINGER", elite(),
                Map.of(PlayerSkill.SPEEDSTER, 99, PlayerSkill.DRIBBLER, 99))
                .calculateOverall();
        assertEquals(base + 5, withSkills, "WINGER SPEEDSTER+DRIBBLER=99 should add 5");
    }

    // ========== Height only (skills empty) ==========

    @Test
    @DisplayName("Height only: GK height=200 → base + 2 (max bonus)")
    void heightOnly_gk_200() {
        int base = player("GK", elite()).calculateOverall();
        int withHeight = playerWithHeight("GK", elite(), 200).calculateOverall();
        assertEquals(base + 2, withHeight);
    }

    @Test
    @DisplayName("Height only: ATT height=170 → base - 2 (penalty)")
    void heightOnly_att_170() {
        int base = player("ATT", elite()).calculateOverall();
        int withHeight = playerWithHeight("ATT", elite(), 170).calculateOverall();
        assertEquals(base - 2, withHeight);
    }

    @Test
    @DisplayName("Height only: WINGER height=170 → base - 1 (closed boundary, matches V25D39)")
    void heightOnly_winger_170() {
        int base = player("WINGER", elite()).calculateOverall();
        int withHeight = playerWithHeight("WINGER", elite(), 170).calculateOverall();
        assertEquals(base - 1, withHeight, "WINGER height=170 triggers -1 (closed boundary)");
    }

    // ========== Combined: skill + height ==========

    @Test
    @DisplayName("Combined: GK WALL=99 + height=200 → base + 5 (matches OverallCalculator)")
    void combined_gk_wall99_height200() {
        int base = player("GK", elite()).calculateOverall();
        int combined = playerWithBoth("GK", elite(), 200, Map.of(PlayerSkill.WALL, 99))
                .calculateOverall();
        assertEquals(base + 5, combined, "GK WALL=99 + height=200 = base + 5");
    }

    @Test
    @DisplayName("Combined: WINGER SPEEDSTER+DRIBBLER=99 + height=170 → base + 4 (matches V25D39)")
    void combined_winger_skills99_height170() {
        int base = player("WINGER", elite()).calculateOverall();
        int combined = playerWithBoth("WINGER", elite(), 170,
                Map.of(PlayerSkill.SPEEDSTER, 99, PlayerSkill.DRIBBLER, 99))
                .calculateOverall();
        assertEquals(base + 4, combined, "WINGER skills99 + h170 = base + 4 (skill 5 - height 1)");
    }

    // ========== Edge cases specific to SessionPlayer ==========

    @Test
    @DisplayName("Edge: any null stat → return 50 (defensive, preserves pre-C5 behavior)")
    void nullStat_returns50() {
        // Build a player with one null stat via reflection-free approach: use fromWorldPlayer
        // which uses setAttributesFromOverall (all non-null). To inject a null, build via
        // the field setters and null out one stat. (SessionPlayer's setters are public.)
        SessionPlayer p = SessionPlayer.custom("Test", 25, "GK",
                80, 80, 80, 80, 80, 80, BigDecimal.valueOf(1_000_000));
        p.setAttack(null);  // Force a null stat
        assertEquals(50, p.calculateOverall(), "Null stat returns 50 (pre-C5 defensive check)");
    }

    @Test
    @DisplayName("Edge: position='UNKNOWN' → arithmetic mean (default branch)")
    void unknownPosition_mean() {
        SessionPlayer p = player("UNKNOWN", new int[]{50, 50, 50, 50, 50, 50});
        // (50×6) / 6 = 50
        assertEquals(50, p.calculateOverall(), "Unknown position → default branch (mean)");
    }

    @Test
    @DisplayName("Edge: position='UNKNOWN' + skills → mean unchanged (no skill weights for default)")
    void unknownPosition_withSkill_unchanged() {
        SessionPlayer p = playerWithSkills("UNKNOWN", new int[]{50, 50, 50, 50, 50, 50},
                Map.of(PlayerSkill.WALL, 99));
        assertEquals(50, p.calculateOverall(), "Unknown position: skill weights empty, no bonus");
    }

    @Test
    @DisplayName("Edge: setSkillLevel(0) removes entry → reverts to base (sparse map)")
    void setSkillLevelZero_revertsToBase() {
        SessionPlayer base = player("GK", elite());
        SessionPlayer p = player("GK", elite());
        p.setSkillLevel(PlayerSkill.WALL, 99);
        assertTrue(p.calculateOverall() > base.calculateOverall(), "WALL=99 should boost GK");
        p.setSkillLevel(PlayerSkill.WALL, 0);  // removes entry
        assertEquals(base.calculateOverall(), p.calculateOverall(),
                "After setSkillLevel(WALL, 0), overall reverts to base");
    }

    @Test
    @DisplayName("Cross-layer equivalence: Player and SessionPlayer with equivalent state produce same overall")
    void crossLayerEquivalence() {
        // Build equivalent state on both entities.
        int[] stats = elite();
        Map<PlayerSkill, Integer> skills = new HashMap<>();
        skills.put(PlayerSkill.WALL, 99);
        skills.put(PlayerSkill.AERIAL, 50);

        // Player (via Position.GK)
        Player p = Player.create(
                com.footballmanager.domain.model.valueobject.PlayerId.generate(),
                "Test", 25, Player.Position.GK,
                com.footballmanager.domain.model.entity.PlayerAttributes.of(
                        stats[0], stats[1], stats[2], stats[3], stats[4], stats[5]),
                BigDecimal.valueOf(1_000_000));
        p.setHeightCm(190);
        for (Map.Entry<PlayerSkill, Integer> e : skills.entrySet()) {
            p.setSkillLevel(e.getKey(), e.getValue());
        }

        // SessionPlayer (via "GK" string)
        SessionPlayer sp = playerWithBoth("GK", stats, 190, skills);

        assertEquals(p.getOverall(), sp.calculateOverall(),
                "Player and SessionPlayer with equivalent state must produce identical overalls");
    }
}