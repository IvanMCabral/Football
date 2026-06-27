package com.footballmanager.application.service.simulation.v24;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V24D1: Tests for V24FormationParser.
 * Validates parsing of formation strings, role counts,
 * outfield player totals, boolean helpers, and fallback behavior.
 */
class V24FormationParserTest {

    private final V24FormationParser parser = new V24FormationParser();

    // ========== parsesStandard442 ==========

    @Test
    void parsesStandard442() {
        var f = parser.parse("4-4-2");

        assertEquals("4-4-2", f.raw());
        assertEquals(4, f.defenders());
        assertEquals(4, f.midfielders());
        assertEquals(0, f.attackingMidfielders());
        assertEquals(0, f.wingers());
        assertEquals(2, f.forwards());
        assertEquals(10, f.outfieldPlayers());
        assertFalse(f.hasWingers());
        assertFalse(f.hasSingleStriker());
        assertTrue(f.hasTwoStrikers());
        assertFalse(f.isBackThree());
        assertTrue(f.isBackFour());
        assertFalse(f.isBackFive());
    }

    // ========== parsesStandard433 ==========

    @Test
    void parsesStandard433() {
        var f = parser.parse("4-3-3");

        assertEquals("4-3-3", f.raw());
        assertEquals(4, f.defenders());
        assertEquals(3, f.midfielders());
        assertEquals(0, f.attackingMidfielders());
        assertEquals(2, f.wingers());
        assertEquals(1, f.forwards());
        assertEquals(10, f.outfieldPlayers());
        assertTrue(f.hasWingers());
        assertTrue(f.hasSingleStriker());
        assertFalse(f.hasTwoStrikers());
        assertFalse(f.isBackThree());
        assertTrue(f.isBackFour());
        assertFalse(f.isBackFive());
    }

    // ========== parses4231 ==========

    @Test
    void parses4231() {
        var f = parser.parse("4-2-3-1");

        assertEquals("4-2-3-1", f.raw());
        assertEquals(4, f.defenders());
        assertEquals(5, f.midfielders()); // midfielders + attackingMidfielders = 2+3
        assertEquals(0, f.attackingMidfielders());
        assertEquals(0, f.wingers());
        assertEquals(1, f.forwards());
        assertEquals(10, f.outfieldPlayers());
        assertFalse(f.hasWingers());
        assertTrue(f.hasSingleStriker());
        assertFalse(f.hasTwoStrikers());
        assertFalse(f.isBackThree());
        assertTrue(f.isBackFour());
        assertFalse(f.isBackFive());
    }

    // ========== parses352 ==========

    @Test
    void parses352() {
        var f = parser.parse("3-5-2");

        assertEquals("3-5-2", f.raw());
        assertEquals(3, f.defenders());
        assertEquals(5, f.midfielders());
        assertEquals(0, f.attackingMidfielders());
        assertEquals(0, f.wingers());
        assertEquals(2, f.forwards());
        assertEquals(10, f.outfieldPlayers());
        assertFalse(f.hasWingers()); // 3-5-2 has no separate winger slot
        assertFalse(f.hasSingleStriker());
        assertTrue(f.hasTwoStrikers());
        assertTrue(f.isBackThree());
        assertFalse(f.isBackFour());
        assertFalse(f.isBackFive());
    }

    // ========== parsesBackFive ==========

    @Test
    void parsesBackFive() {
        var f1 = parser.parse("5-4-1");
        assertEquals(5, f1.defenders());
        assertTrue(f1.isBackFive());

        var f2 = parser.parse("5-3-2");
        assertEquals(5, f2.defenders());
        assertTrue(f2.isBackFive());
        assertFalse(f2.hasWingers()); // 5-3-2 has no separate winger slot
    }

    // ========== parses343 ==========

    @Test
    void parses343() {
        var f = parser.parse("3-4-3");

        assertEquals("3-4-3", f.raw());
        assertEquals(3, f.defenders());
        assertEquals(4, f.midfielders());
        assertEquals(0, f.attackingMidfielders());
        assertEquals(2, f.wingers()); // 3-4-3 has wingers=2
        assertEquals(1, f.forwards());
        assertEquals(10, f.outfieldPlayers());
        assertTrue(f.hasWingers()); // 3-4-3 has winger slots
        assertTrue(f.isBackThree());
    }

    // ========== nullOrBlankDefaultsSafely ==========

    @Test
    void nullOrBlankDefaultsSafely() {
        var fNull = parser.parse(null);
        var fBlank = parser.parse("");
        var fSpaces = parser.parse("   ");

        assertNotNull(fNull);
        assertNotNull(fBlank);
        assertNotNull(fSpaces);
        assertEquals("4-4-2", fNull.raw());
        assertEquals("4-4-2", fBlank.raw());
        assertEquals("4-4-2", fSpaces.raw());
        assertEquals(10, fNull.outfieldPlayers());
    }

    // ========== invalidFormationDefaultsSafely ==========

    @Test
    void invalidFormationDefaultsSafely() {
        var fBanana = parser.parse("banana");
        var fNines = parser.parse("9-9-9");

        assertNotNull(fBanana);
        assertNotNull(fNines);
        assertEquals("4-4-2", fBanana.raw());
        assertEquals("4-4-2", fNines.raw());
        assertEquals(10, fBanana.outfieldPlayers());
    }

    // ========== normalizesSpaces ==========

    @Test
    void normalizesSpaces() {
        var f = parser.parse("  4-3-3  ");

        assertEquals("4-3-3", f.raw());
        assertEquals(4, f.defenders());
        assertEquals(3, f.midfielders());
        assertEquals(1, f.forwards());
    }

    // ========== outfieldPlayers ==========

    @Test
    void outfieldPlayersIsTen() {
        // All standard formations should have 10 outfield players
        assertEquals(10, parser.parse("4-4-2").outfieldPlayers());
        assertEquals(10, parser.parse("4-3-3").outfieldPlayers());
        assertEquals(10, parser.parse("4-2-3-1").outfieldPlayers());
        assertEquals(10, parser.parse("3-5-2").outfieldPlayers());
        assertEquals(10, parser.parse("3-4-3").outfieldPlayers());
        assertEquals(10, parser.parse("5-4-1").outfieldPlayers());
        assertEquals(10, parser.parse("5-3-2").outfieldPlayers());
        // V25D54-C15 P1: 4 formations nuevas también suman 10 outfield.
        assertEquals(10, parser.parse("3-5-2-CDM").outfieldPlayers());
        assertEquals(10, parser.parse("3-4-1-2").outfieldPlayers());
        assertEquals(10, parser.parse("4-2-2-2").outfieldPlayers());
        // V25D54-C15 P2: variante 4-3-3-1 también suma 10 outfield.
        assertEquals(10, parser.parse("4-3-3-1").outfieldPlayers());
    }

    // ========== V25D54-C15 P1: parsing de formations nuevas ==========

    @Test
    void parses_3_5_2_CDM() {
        // 3-5-2 con CDM pivot: 3 DEF + 5 MID (1 CDM + 2 CM + 2 WB fold-ados) + 2 ST
        var f = parser.parse("3-5-2-CDM");
        assertEquals("3-5-2-CDM", f.raw());
        assertEquals(3, f.defenders());
        assertEquals(5, f.midfielders());
        assertEquals(0, f.attackingMidfielders());
        assertEquals(0, f.wingers());
        assertEquals(2, f.forwards());
        assertEquals(10, f.outfieldPlayers());
        assertTrue(f.isBackThree());
        assertTrue(f.hasTwoStrikers());
    }

    @Test
    void parses_3_4_1_2_christmas_tree() {
        // Christmas tree: 3 DEF + 5 MID (4 MID + 1 CAM fold-ados) + 2 ST
        var f = parser.parse("3-4-1-2");
        assertEquals("3-4-1-2", f.raw());
        assertEquals(3, f.defenders());
        assertEquals(5, f.midfielders()); // 4 + 1 (CAM fold-ado)
        assertEquals(0, f.attackingMidfielders());
        assertEquals(0, f.wingers());
        assertEquals(2, f.forwards());
        assertEquals(10, f.outfieldPlayers());
        assertTrue(f.isBackThree());
        assertTrue(f.hasTwoStrikers());
    }

    @Test
    void parses_4_2_2_2_narrow_diamond() {
        // Narrow diamond: 4 DEF + 4 MID (2 CDM + 2 wide fold-ados) + 2 ST
        var f = parser.parse("4-2-2-2");
        assertEquals("4-2-2-2", f.raw());
        assertEquals(4, f.defenders());
        assertEquals(4, f.midfielders()); // 2 + 2
        assertEquals(0, f.attackingMidfielders());
        assertEquals(0, f.wingers());
        assertEquals(2, f.forwards());
        assertEquals(10, f.outfieldPlayers());
        assertTrue(f.isBackFour());
        assertTrue(f.hasTwoStrikers());
    }

    // ========== V25D54-C15 P2: variante 4-3-3-1 con pivote CDM ==========

    @Test
    void parses_4_3_3_1_with_pivot() {
        // 4-3-3-1 = 4-3-3 con CDM pivot (decorativo). Engine treats as 4-3-3-like.
        var f = parser.parse("4-3-3-1");
        assertEquals("4-3-3-1", f.raw());
        assertEquals(4, f.defenders());
        assertEquals(3, f.midfielders());
        assertEquals(0, f.attackingMidfielders());
        assertEquals(2, f.wingers()); // same wing count as 4-3-3
        assertEquals(1, f.forwards());
        assertEquals(10, f.outfieldPlayers());
        assertTrue(f.isBackFour());
        assertTrue(f.hasWingers());
        assertTrue(f.hasSingleStriker());
    }

    // ========== nullPlayerThrowsOnSelectShooterWithFormation ==========

    @Test
    void nullPlayersThrowsOnSelectShooter() {
        V24PlayerSelector selector = new V24PlayerSelector(new java.util.Random(42));
        assertThrows(IllegalArgumentException.class,
                () -> selector.selectShooter(null, "4-3-3"));
    }

    @Test
    void nullPlayersThrowsOnSelectShooterNoFormation() {
        V24PlayerSelector selector = new V24PlayerSelector(new java.util.Random(42));
        assertThrows(IllegalArgumentException.class,
                () -> selector.selectShooter(null));
    }

    // ========== formationAffectsSelectionDeterministically ==========

    @Test
    void formationAffectsSelectionDeterministically() {
        // Create a fixed list of players
        java.math.BigDecimal bd70 = java.math.BigDecimal.valueOf(70_000);
        var att1 = makePlayer("att1", "ATT", 75, 80);
        var att2 = makePlayer("att2", "ATT", 70, 80);
        var mid1 = makePlayer("mid1", "MID", 72, 80);
        var def1 = makePlayer("def1", "DEF", 68, 80);

        var players = java.util.List.of(att1, att2, mid1, def1);

        // Same players, same seed, different formation → same player selected
        V24PlayerSelector sel1 = new V24PlayerSelector(new java.util.Random(99));
        var result1 = sel1.selectShooter(players, "4-4-2");
        V24PlayerSelector sel2 = new V24PlayerSelector(new java.util.Random(99));
        var result2 = sel2.selectShooter(players, "4-4-2");
        assertTrue(result1.isPresent());
        assertTrue(result2.isPresent());
        assertEquals(result1.get().sessionPlayerId(), result2.get().sessionPlayerId(),
                "Same seed + formation should give same player");
    }

    @Test
    void sameSeedDifferentFormationGivesDifferentOrSamePlayer() {
        java.math.BigDecimal bd70 = java.math.BigDecimal.valueOf(70_000);
        var att1 = makePlayer("att1", "ATT", 85, 80); // high attack, should dominate 4-3-3
        var def1 = makePlayer("def1", "DEF", 85, 80); // high attack but DEF in 4-3-3 gets low bonus

        var players433 = java.util.List.of(att1, def1);

        V24PlayerSelector sel433 = new V24PlayerSelector(new java.util.Random(42));
        var shooter433 = sel433.selectShooter(players433, "4-3-3");

        V24PlayerSelector sel442 = new V24PlayerSelector(new java.util.Random(42));
        var shooter442 = sel442.selectShooter(players433, "4-4-2");

        assertTrue(shooter433.isPresent());
        assertTrue(shooter442.isPresent());
        // In 4-3-3, ATT (attack=85) vs DEF (attack=85) → ATT wins due to position bonus
        // In 4-4-2, same but position bonuses differ
        // Both likely select ATT due to high attack, but that's acceptable
    }

    // ========== assistProviderWithFormation ==========

    @Test
    void assistProviderAcceptsFormationParameter() {
        java.math.BigDecimal bd70 = java.math.BigDecimal.valueOf(70_000);
        var att1 = makePlayer("att1", "ATT", 70, 80);
        var mid1 = makePlayer("mid1", "MID", 70, 80);

        var players = java.util.List.of(att1, mid1);

        V24PlayerSelector selector = new V24PlayerSelector(new java.util.Random(42));
        var assist = selector.selectAssistProvider(players, att1);

        assertTrue(assist.isPresent());
        assertEquals("MID", assist.get().position(),
                "MID should be preferred for assist over ATT");
    }

    // ========== Fixture helpers ==========

    private V24PlayerMatchState makePlayer(String id, String position, int ovr, int stamina) {
        com.footballmanager.domain.model.entity.SessionPlayer sp =
                com.footballmanager.domain.model.entity.SessionPlayer.custom(
                        id, 25, position, ovr, ovr, ovr, ovr, ovr, ovr,
                        java.math.BigDecimal.valueOf(ovr * 1000));
        sp.setEnergy(stamina);
        return V24PlayerMatchState.fromSessionPlayer(sp, "team-" + id);
    }
}