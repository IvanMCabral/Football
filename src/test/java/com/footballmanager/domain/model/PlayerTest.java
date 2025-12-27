package com.footballmanager.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class PlayerTest {

    @Test
    void shouldCreatePlayer() {
        PlayerId id = PlayerId.generate();
        PlayerAttributes attributes = PlayerAttributes.of(85, 70, 88, 82, 80, 85);

        Player player = Player.create(id, "Test Player", 25, Player.Position.ST,
                attributes, new BigDecimal("1000000"));

        assertNotNull(player);
        assertEquals(id, player.getId());
        assertEquals("Test Player", player.getName());
        assertEquals(25, player.getAge());
        assertEquals(Player.Position.ST, player.getPosition());
        assertEquals(100, player.getEnergy());
        assertFalse(player.isInjured());
    }

    @Test
    void shouldValidateAge() {
        assertThrows(IllegalArgumentException.class, () -> {
            Player.create(PlayerId.generate(), "Young Player", 15, Player.Position.ST,
                    PlayerAttributes.of(50, 50, 50, 50, 50, 50), new BigDecimal("100000"));
        });

        assertThrows(IllegalArgumentException.class, () -> {
            Player.create(PlayerId.generate(), "Old Player", 46, Player.Position.ST,
                    PlayerAttributes.of(50, 50, 50, 50, 50, 50), new BigDecimal("100000"));
        });
    }

    @Test
    void shouldUpdateEnergy() {
        Player player = Player.create(PlayerId.generate(), "Test Player", 25, Player.Position.ST,
                PlayerAttributes.of(85, 70, 88, 82, 80, 85), new BigDecimal("1000000"));

        player.updateEnergy(-20);
        assertEquals(80, player.getEnergy());

        player.updateEnergy(-100);
        assertEquals(0, player.getEnergy());

        player.updateEnergy(50);
        assertEquals(50, player.getEnergy());

        player.updateEnergy(100);
        assertEquals(100, player.getEnergy());
    }

    @Test
    void shouldInjureAndHealPlayer() {
        Player player = Player.create(PlayerId.generate(), "Test Player", 25, Player.Position.ST,
                PlayerAttributes.of(85, 70, 88, 82, 80, 85), new BigDecimal("1000000"));

        assertFalse(player.isInjured());
        int energyBefore = player.getEnergy();

        player.injure();
        assertTrue(player.isInjured());
        assertEquals(Math.max(0, energyBefore - 30), player.getEnergy());

        player.heal();
        assertFalse(player.isInjured());
    }

    @Test
    void shouldCalculateOverallRating() {
        PlayerAttributes attributes = PlayerAttributes.of(80, 75, 85, 90, 80, 85);
        Player player = Player.create(PlayerId.generate(), "Test Player", 25, Player.Position.ST,
                attributes, new BigDecimal("1000000"));

        int overall = player.calculateOverallRating();
        int expected = (80 + 75 + 85 + 90 + 80 + 85) / 6;
        assertEquals(expected, overall);
    }

    @Test
    void shouldReconstructPlayer() {
        PlayerId id = PlayerId.generate();
        PlayerAttributes attributes = PlayerAttributes.of(85, 70, 88, 82, 80, 85);

        Player original = Player.create(id, "Test Player", 25, Player.Position.ST,
                attributes, new BigDecimal("1000000"));

        Player reconstructed = Player.reconstruct(id, "Test Player", 25, Player.Position.ST,
                attributes, new BigDecimal("1000000"), 80, false,
                original.getCreatedAt(), original.getUpdatedAt());

        assertEquals(original, reconstructed);
        assertEquals(80, reconstructed.getEnergy());
    }
}
