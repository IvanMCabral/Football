package com.footballmanager.application.service.simulation.v24;

import com.footballmanager.application.service.domain.TeamStyle;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * LIVE-MATCH-F2-LIVE F5 (B7): unit tests for the F5 mutators on
 * {@link V24TeamMatchState}. Per the F5 spec section 5:
 * <ul>
 *   <li>{@code setStyle_null_throws} + {@code setStyle_valid_succeeds} (B1)</li>
 *   <li>{@code setFormation_tooManyPlayers_throws} (B1)</li>
 *   <li>{@code setFormation_noGoalkeeper_throws} (B1) — proxy: invalid formation rejected</li>
 * </ul>
 *
 * <p>Note: V24TeamMatchState.formation is a String code (e.g. "4-4-2"); the
 * engine always pairs it with 1 GK. So the F5 D-formation rule (10-11 players,
 * exactly 1 GK) collapses to "10 outfield players parse from the code". The
 * tests below exercise the validation path through that lens.
 */
class V24TeamMatchStateTest {

    @Test
    void setStyle_null_throws() {
        V24TeamMatchState team = makeTeam();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> team.setStyle(null));
        // sanity: the message exists
        assertNotNull(ex.getMessage());
    }

    @Test
    void setStyle_valid_succeeds() {
        V24TeamMatchState team = makeTeam();
        team.setStyle(TeamStyle.ATTACKING);
        assertEquals(TeamStyle.ATTACKING, team.style());

        team.setStyle(TeamStyle.DEFENSIVE);
        assertEquals(TeamStyle.DEFENSIVE, team.style());
    }

    @Test
    void setFormation_invalidString_throws() {
        V24TeamMatchState team = makeTeam();
        // "banana" does not parse — V24FormationParser falls back to "4-4-2"
        // and the setter detects the mismatch.
        assertThrows(IllegalArgumentException.class,
            () -> team.setFormation("banana"));
    }

    @Test
    void setFormation_nullOrBlank_throws() {
        V24TeamMatchState team = makeTeam();
        assertThrows(IllegalArgumentException.class,
            () -> team.setFormation(null));
        assertThrows(IllegalArgumentException.class,
            () -> team.setFormation(""));
        assertThrows(IllegalArgumentException.class,
            () -> team.setFormation("   "));
    }

    @Test
    void setFormation_valid_succeeds() {
        V24TeamMatchState team = makeTeam();
        team.setFormation("3-5-2");
        assertEquals("3-5-2", team.formation());

        team.setFormation("4-4-2");
        assertEquals("4-4-2", team.formation());

        // Whitespace tolerance (V24FormationParser normalizes)
        team.setFormation("  4-3-3  ");
        assertEquals("4-3-3", team.formation());
    }

    @Test
    void setFormation_tooManyPlayers_throws() {
        // "9-9-9" sums to 27 outfield players — V24FormationParser treats it
        // as unparseable (the parser only handles single-digit slots).
        V24TeamMatchState team = makeTeam();
        assertThrows(IllegalArgumentException.class,
            () -> team.setFormation("9-9-9"));
    }

    // ========== Fixture helpers ==========

    private V24TeamMatchState makeTeam() {
        List<V24PlayerMatchState> starters = java.util.stream.IntStream.range(0, 11)
            .mapToObj(i -> makePlayer("starter-" + i, positionForIndex(i), 70))
            .toList();
        List<V24PlayerMatchState> bench = java.util.stream.IntStream.range(0, 5)
            .mapToObj(i -> makePlayer("bench-" + i, "MID", 70))
            .toList();
        for (V24PlayerMatchState b : bench) b.substituteOff();
        return new V24TeamMatchState("team-1", "Test Team", "4-3-3",
            TeamStyle.BALANCED, starters, bench);
    }

    private V24PlayerMatchState makePlayer(String id, String position, int ovr) {
        com.footballmanager.domain.model.entity.SessionPlayer sp =
            com.footballmanager.domain.model.entity.SessionPlayer.custom(
                id, 25, position, ovr, ovr, ovr, ovr, ovr, ovr,
                BigDecimal.valueOf(ovr * 1000));
        return V24PlayerMatchState.fromSessionPlayer(sp, "team-1");
    }

    private String positionForIndex(int i) {
        return switch (i) {
            case 0 -> "GK";
            case 1, 2, 3, 4 -> "DEF";
            case 5, 6, 7 -> "MID";
            case 8, 9 -> "WINGER";
            default -> "ATT";
        };
    }
}
