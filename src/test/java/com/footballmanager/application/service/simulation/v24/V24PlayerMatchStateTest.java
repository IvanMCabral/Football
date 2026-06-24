package com.footballmanager.application.service.simulation.v24;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * LIVE-MATCH-F2-LIVE F5 (B7): unit tests for the F5 mutators on
 * {@link V24PlayerMatchState}. Per the F5 spec section 5:
 * <ul>
 *   <li>{@code setPosition_null_throws} (B2)</li>
 * </ul>
 *
 * <p>Position reassignment is the leaf of the formation-change flow: the
 * manager changes formation, the {@code TacticalChangeService} reassigns
 * each affected player to a new slot via this setter, the engine's next
 * replay reads the new positions.
 */
class V24PlayerMatchStateTest {

    @Test
    void setPosition_nullOrBlank_throws() {
        V24PlayerMatchState player = makePlayer("p-1", "MID");
        IllegalArgumentException ex1 = assertThrows(IllegalArgumentException.class,
            () -> player.setPosition(null));
        assertNotNull(ex1.getMessage());

        IllegalArgumentException ex2 = assertThrows(IllegalArgumentException.class,
            () -> player.setPosition(""));
        assertNotNull(ex2.getMessage());

        IllegalArgumentException ex3 = assertThrows(IllegalArgumentException.class,
            () -> player.setPosition("   "));
        assertNotNull(ex3.getMessage());
    }

    @Test
    void setPosition_valid_succeeds() {
        V24PlayerMatchState player = makePlayer("p-1", "MID");
        // Sanity: the initial position is the one fromSessionPlayer was constructed with.
        assertEquals("MID", player.position());

        player.setPosition("ATT");
        assertEquals("ATT", player.position());

        player.setPosition("DEF");
        assertEquals("DEF", player.position());

        // Same position is a no-op (still valid).
        player.setPosition("DEF");
        assertEquals("DEF", player.position());
    }

    // ========== Fixture helpers ==========

    private V24PlayerMatchState makePlayer(String id, String position) {
        com.footballmanager.domain.model.entity.SessionPlayer sp =
            com.footballmanager.domain.model.entity.SessionPlayer.custom(
                id, 25, position, 70, 70, 70, 70, 70, 70,
                BigDecimal.valueOf(70_000L));
        return V24PlayerMatchState.fromSessionPlayer(sp, "team-1");
    }
}
