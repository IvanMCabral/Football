package com.footballmanager.domain.model.valueobject;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V25D31: PlayerSkill enum - 10 habilidades con metadata.
 */
class PlayerSkillTest {

    @Test
    void allSkills_haveUniqueDisplayNames() {
        Set<String> displayNames = Arrays.stream(PlayerSkill.values())
                .map(PlayerSkill::getDisplayName)
                .collect(Collectors.toSet());
        assertEquals(PlayerSkill.values().length, displayNames.size(),
                "Cada PlayerSkill debe tener un displayName unico");
    }

    @Test
    void defensiveSkills_returnsMarkerTacklerWall() {
        Set<PlayerSkill> def = Arrays.stream(PlayerSkill.values())
                .filter(PlayerSkill::primarilyDefensive)
                .collect(Collectors.toCollection(HashSet::new));
        assertEquals(
                new HashSet<>(Arrays.asList(PlayerSkill.MARKER, PlayerSkill.TACKLER, PlayerSkill.WALL)),
                def);
    }

    @Test
    void offensiveSkills_returnsHeaderDribblerPlaymakerShooter() {
        Set<PlayerSkill> off = Arrays.stream(PlayerSkill.values())
                .filter(PlayerSkill::primarilyOffensive)
                .collect(Collectors.toCollection(HashSet::new));
        assertEquals(
                new HashSet<>(Arrays.asList(
                        PlayerSkill.HEADER, PlayerSkill.DRIBBLER,
                        PlayerSkill.PLAYMAKER, PlayerSkill.SHOOTER)),
                off);
    }

    @Test
    void enumSize_equals10() {
        assertEquals(10, PlayerSkill.values().length,
                "PlayerSkill debe tener exactamente 10 valores");
    }
}
