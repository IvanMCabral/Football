package com.footballmanager.application.service.lineup;

import com.footballmanager.adapters.in.web.career.lineup.dto.PlayerLineupDTO;
import com.footballmanager.domain.model.entity.SessionPlayer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V24D6D7A: Tests for lineup blocking of suspended players.
 */
class LineupBlockingTest {

    private SessionPlayer makePlayer(String id, String name, String position, int energy, boolean injured, boolean suspended, int suspensionRemainingMatches) {
        SessionPlayer p = new SessionPlayer();
        p.setSessionPlayerId(id);
        p.setName(name);
        p.setPosition(position);
        p.setEnergy(energy);
        p.setInjured(injured);
        p.setSuspended(suspended);
        p.setSuspensionRemainingMatches(suspensionRemainingMatches);
        p.setAttack(70);
        p.setDefense(70);
        p.setTechnique(70);
        p.setSpeed(70);
        p.setStamina(70);
        p.setMentality(70);
        return p;
    }

    @Test
    void validatePlayerFitness_acceptsNonSuspendedPlayers() {
        LineupHelper helper = new LineupHelper();
        List<SessionPlayer> players = List.of(
            makePlayer("1", "Player A", "ST", 50, false, false, 0),
            makePlayer("2", "Player B", "CM", 50, false, false, 0)
        );
        assertDoesNotThrow(() -> helper.validatePlayerFitness(players));
    }

    @Test
    void validatePlayerFitness_rejectsSuspendedPlayer_suspendedTrue() {
        LineupHelper helper = new LineupHelper();
        List<SessionPlayer> players = List.of(
            makePlayer("1", "Suspended Player", "ST", 50, false, true, 1)
        );
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> helper.validatePlayerFitness(players));
        assertTrue(ex.getMessage().contains("suspended"));
        assertTrue(ex.getMessage().contains("Suspended Player"));
    }

    @Test
    void validatePlayerFitness_rejectsSuspensionRemainingPositive() {
        LineupHelper helper = new LineupHelper();
        List<SessionPlayer> players = List.of(
            makePlayer("1", "Suspended Player 2", "CM", 50, false, false, 2)
        );
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> helper.validatePlayerFitness(players));
        assertTrue(ex.getMessage().contains("suspended"));
        assertTrue(ex.getMessage().contains("2 match(es)"));
    }

    @Test
    void validatePlayerFitness_acceptsNullSuspended() {
        LineupHelper helper = new LineupHelper();
        SessionPlayer p = new SessionPlayer();
        p.setSessionPlayerId("null-suspend-test");
        p.setName("Null Suspended");
        p.setPosition("CB");
        p.setEnergy(50);
        p.setInjured(false);
        // suspended null, not set
        assertDoesNotThrow(() -> helper.validatePlayerFitness(List.of(p)));
    }

    @Test
    void validatePlayerFitness_rejectsInjured() {
        LineupHelper helper = new LineupHelper();
        List<SessionPlayer> players = List.of(
            makePlayer("1", "Injured Player", "ST", 50, true, false, 0)
        );
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> helper.validatePlayerFitness(players));
        assertTrue(ex.getMessage().contains("injured"));
    }

    @Test
    void validatePlayerFitness_rejectsLowEnergy() {
        LineupHelper helper = new LineupHelper();
        List<SessionPlayer> players = List.of(
            makePlayer("1", "Exhausted Player", "ST", 15, false, false, 0)
        );
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> helper.validatePlayerFitness(players));
        assertTrue(ex.getMessage().contains("low fitness"));
    }
}