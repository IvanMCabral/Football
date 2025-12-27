package com.footballmanager.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class TeamTest {

    @Test
    void shouldCreateTeam() {
        TeamId teamId = TeamId.generate();
        UserId managerId = UserId.generate();

        Team team = Team.create(teamId, managerId, "Test Team", "England",
                new BigDecimal("10000000"), Formation.ofDefault());

        assertNotNull(team);
        assertEquals(teamId, team.getId());
        assertEquals(managerId, team.getManagerId());
        assertEquals("Test Team", team.getName());
        assertEquals("England", team.getCountry());
        assertEquals(new BigDecimal("10000000"), team.getBudget());
        assertEquals(0, team.getSquadSize());
        assertFalse(team.isBankrupt());
    }

    @Test
    void shouldValidateTeamName() {
        assertThrows(IllegalArgumentException.class, () -> {
            Team.create(TeamId.generate(), UserId.generate(), "AB", "England",
                    new BigDecimal("10000000"), Formation.ofDefault());
        });
    }

    @Test
    void shouldAddPlayerToSquad() {
        Team team = Team.create(TeamId.generate(), UserId.generate(), "Test Team", "England",
                new BigDecimal("10000000"), Formation.ofDefault());
        PlayerId playerId = PlayerId.generate();

        team.addPlayer(playerId);

        assertEquals(1, team.getSquadSize());
        assertTrue(team.getSquadPlayerIds().contains(playerId));
    }

    @Test
    void shouldNotExceedSquadLimit() {
        Team team = Team.create(TeamId.generate(), UserId.generate(), "Test Team", "England",
                new BigDecimal("10000000"), Formation.ofDefault());

        for (int i = 0; i < 30; i++) {
            team.addPlayer(PlayerId.generate());
        }

        assertThrows(IllegalStateException.class, () -> {
            team.addPlayer(PlayerId.generate());
        });
    }

    @Test
    void shouldRemovePlayerFromSquad() {
        Team team = Team.create(TeamId.generate(), UserId.generate(), "Test Team", "England",
                new BigDecimal("10000000"), Formation.ofDefault());
        PlayerId playerId = PlayerId.generate();

        team.addPlayer(playerId);
        assertEquals(1, team.getSquadSize());

        team.removePlayer(playerId);

        assertEquals(0, team.getSquadSize());
        assertFalse(team.getSquadPlayerIds().contains(playerId));
    }

    @Test
    void shouldUpdateBudget() {
        Team team = Team.create(TeamId.generate(), UserId.generate(), "Test Team", "England",
                new BigDecimal("10000000"), Formation.ofDefault());

        team.updateBudget(new BigDecimal("5000000"));
        assertEquals(new BigDecimal("15000000"), team.getBudget());

        team.updateBudget(new BigDecimal("-5000000"));
        assertEquals(new BigDecimal("10000000"), team.getBudget());
    }

    @Test
    void shouldTrackBankruptcy() {
        Team team = Team.create(TeamId.generate(), UserId.generate(), "Test Team", "England",
                new BigDecimal("1000"), Formation.ofDefault());

        assertFalse(team.isBankrupt());

        team.updateBudget(new BigDecimal("-1000"));
        assertTrue(team.isBankrupt());
    }

    @Test
    void shouldNotAllowNegativeBudget() {
        Team team = Team.create(TeamId.generate(), UserId.generate(), "Test Team", "England",
                new BigDecimal("1000"), Formation.ofDefault());

        assertThrows(IllegalArgumentException.class, () -> {
            team.updateBudget(new BigDecimal("-2000"));
        });
    }
}
