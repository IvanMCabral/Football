package com.footballmanager.adapters.in.web.career.dto.response;

import com.footballmanager.adapters.in.web.career.lineup.dto.LineupDTO;
import com.footballmanager.adapters.in.web.career.lineup.dto.PlayerLineupDTO;
import com.footballmanager.adapters.in.web.career.mappers.SessionEntityMapper;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionPlayer.SessionPlayerOrigin;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V24D6D7A: Tests for SessionPlayerDTO and SessionEntityMapper discipline field exposure.
 */
class SessionPlayerDTODisciplineFieldsTest {

    private SessionPlayer makePlayer(boolean suspended, int suspensionRemainingMatches, int yellowCards, int redCards) {
        SessionPlayer p = new SessionPlayer();
        p.setSessionPlayerId(UUID.randomUUID().toString());
        p.setWorldPlayerId("wp-" + UUID.randomUUID());
        p.setBasePlayerId(UUID.randomUUID());
        p.setName("Test Player");
        p.setAge(25);
        p.setPosition("CM");
        p.setAttack(70);
        p.setDefense(70);
        p.setTechnique(70);
        p.setSpeed(70);
        p.setStamina(70);
        p.setMentality(70);
        p.setMarketValue(BigDecimal.valueOf(1_000_000));
        p.setEnergy(80);
        p.setForm(60);
        p.setOrigin(SessionPlayerOrigin.RANDOM);
        p.setSuspended(suspended);
        p.setSuspensionRemainingMatches(suspensionRemainingMatches);
        p.setYellowCards(yellowCards);
        p.setRedCards(redCards);
        return p;
    }

    @Test
    void sessionPlayerDTO_includesDisciplineFields() {
        SessionPlayerDTO dto = new SessionPlayerDTO(
            UUID.randomUUID().toString(),
            UUID.randomUUID(),
            "Player Name",
            25,
            "CM",
            70, 70, 70, 70, 70, 70,
            BigDecimal.valueOf(1_000_000),
            80, 60,
            false, "MUSCLE", 0,
            "RANDOM",
            75,
            3, 1, true, 2
        );

        assertEquals(3, dto.yellowCards());
        assertEquals(1, dto.redCards());
        assertTrue(dto.suspended());
        assertEquals(2, dto.suspensionRemainingMatches());
    }

    @Test
    void sessionEntityMapper_mapsDisciplineFields_suspended() {
        SessionPlayer player = makePlayer(true, 2, 4, 1);
        SessionPlayerDTO dto = SessionEntityMapper.toDTO(player);

        assertEquals(4, dto.yellowCards());
        assertEquals(1, dto.redCards());
        assertTrue(dto.suspended());
        assertEquals(2, dto.suspensionRemainingMatches());
    }

    @Test
    void sessionEntityMapper_mapsDisciplineFields_notSuspended() {
        SessionPlayer player = makePlayer(false, 0, 2, 0);
        SessionPlayerDTO dto = SessionEntityMapper.toDTO(player);

        assertEquals(2, dto.yellowCards());
        assertEquals(0, dto.redCards());
        assertFalse(dto.suspended());
        assertEquals(0, dto.suspensionRemainingMatches());
    }

    @Test
    void sessionEntityMapper_mapsDisciplineFields_nullSuspended() {
        SessionPlayer player = new SessionPlayer();
        player.setSessionPlayerId(UUID.randomUUID().toString());
        player.setWorldPlayerId("wp-" + UUID.randomUUID());
        player.setBasePlayerId(UUID.randomUUID());
        player.setName("Null Suspended Player");
        player.setAge(28);
        player.setPosition("CB");
        player.setAttack(60);
        player.setDefense(75);
        player.setTechnique(65);
        player.setSpeed(60);
        player.setStamina(70);
        player.setMentality(60);
        player.setMarketValue(BigDecimal.valueOf(500_000));
        player.setEnergy(90);
        player.setForm(50);
        player.setOrigin(SessionPlayerOrigin.CUSTOM);
        // suspended left as null
        player.setYellowCards(null);
        player.setRedCards(null);
        player.setSuspensionRemainingMatches(null);

        SessionPlayerDTO dto = SessionEntityMapper.toDTO(player);

        assertEquals(0, dto.yellowCards()); // null-safe getter defaults to 0
        assertEquals(0, dto.redCards());
        assertFalse(dto.suspended()); // null-safe getter defaults to false
        assertEquals(0, dto.suspensionRemainingMatches());
    }

    @Test
    void playerLineupDTO_includesDisciplineFields() {
        com.footballmanager.adapters.in.web.career.lineup.dto.PlayerLineupDTO dto =
            new com.footballmanager.adapters.in.web.career.lineup.dto.PlayerLineupDTO(
                UUID.randomUUID().toString(),
                "Player",
                "ST",
                80,
                70,
                false,
                25,
                5, 2, true, 1
            );

        assertEquals(5, dto.yellowCards());
        assertEquals(2, dto.redCards());
        assertTrue(dto.suspended());
        assertEquals(1, dto.suspensionRemainingMatches());
    }

    @Test
    void lineupDto_includesDisciplineFields() {
        // Build a full 11-player LineupDTO with one player carrying discipline values
        List<PlayerLineupDTO> allPlayers = new ArrayList<>();
        allPlayers.add(new PlayerLineupDTO(
            UUID.randomUUID().toString(), "Disciplined Player", "CM", 82, 75, false, 27,
            2, 1, true, 1
        ));
        // Add remaining 10 players with no discipline values
        allPlayers.add(new PlayerLineupDTO(UUID.randomUUID().toString(), "GK", "GK", 70, 80, false, 28, 0, 0, false, 0));
        allPlayers.add(new PlayerLineupDTO(UUID.randomUUID().toString(), "CB1", "CB", 72, 80, false, 26, 0, 0, false, 0));
        allPlayers.add(new PlayerLineupDTO(UUID.randomUUID().toString(), "CB2", "CB", 71, 80, false, 27, 0, 0, false, 0));
        allPlayers.add(new PlayerLineupDTO(UUID.randomUUID().toString(), "LB", "LB", 68, 80, false, 25, 0, 0, false, 0));
        allPlayers.add(new PlayerLineupDTO(UUID.randomUUID().toString(), "RB", "RB", 67, 80, false, 26, 0, 0, false, 0));
        allPlayers.add(new PlayerLineupDTO(UUID.randomUUID().toString(), "CM1", "CM", 74, 80, false, 27, 0, 0, false, 0));
        allPlayers.add(new PlayerLineupDTO(UUID.randomUUID().toString(), "CM2", "CM", 73, 80, false, 28, 0, 0, false, 0));
        allPlayers.add(new PlayerLineupDTO(UUID.randomUUID().toString(), "LW", "LW", 69, 80, false, 24, 0, 0, false, 0));
        allPlayers.add(new PlayerLineupDTO(UUID.randomUUID().toString(), "RW", "RW", 68, 80, false, 25, 0, 0, false, 0));
        allPlayers.add(new PlayerLineupDTO(UUID.randomUUID().toString(), "ST", "ST", 80, 80, false, 27, 0, 0, false, 0));

        LineupDTO lineup = new LineupDTO("4-4-2", allPlayers, true);

        assertNotNull(lineup.players());
        assertEquals(11, lineup.players().size());

        PlayerLineupDTO found = lineup.players().get(0);
        assertEquals("Disciplined Player", found.name());
        assertEquals(2, found.yellowCards());
        assertEquals(1, found.redCards());
        assertTrue(found.suspended());
        assertEquals(1, found.suspensionRemainingMatches());
    }
}