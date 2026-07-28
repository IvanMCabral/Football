package com.footballmanager.application.service.simulation.v24;

import com.footballmanager.domain.model.valueobject.PlayerSkill;

import java.util.Map;

final class V24ShotSkillMultipliers {

    private V24ShotSkillMultipliers() {
    }

    static double headerAndAerial(
            V24ShotEventType eventSubType,
            Map<PlayerSkill, Integer> shooterSkills,
            Integer shooterHeightCm) {
        if (eventSubType != V24ShotEventType.CORNER && eventSubType != V24ShotEventType.CROSS) {
            return 1.0;
        }

        int headerSkill = skill(shooterSkills, PlayerSkill.HEADER);
        double multiplier = 1.0 + (headerSkill / 200.0);

        int aerialSkill = skill(shooterSkills, PlayerSkill.AERIAL);
        if (aerialSkill > 0 && shooterHeightCm != null && shooterHeightCm >= 185) {
            multiplier *= 1.0 + (aerialSkill / 300.0);
        }
        return multiplier;
    }

    static double longRangeShooter(V24ShotLocation location, Map<PlayerSkill, Integer> shooterSkills) {
        int shooterSkill = skill(shooterSkills, PlayerSkill.SHOOTER);
        if (shooterSkill <= 0 || location != V24ShotLocation.LONG_RANGE) {
            return 1.0;
        }
        return 1.0 + (shooterSkill / 250.0);
    }

    static double marker(Map<PlayerSkill, Integer> defenderSkills) {
        return 1.0 - (skill(defenderSkills, PlayerSkill.MARKER) / 300.0);
    }

    static double tackler(V24ShotEventType eventSubType, Map<PlayerSkill, Integer> defenderSkills) {
        if (eventSubType != V24ShotEventType.OPEN_PLAY) {
            return 1.0;
        }
        return 1.0 - (skill(defenderSkills, PlayerSkill.TACKLER) / 250.0);
    }

    static double wallDivisor(Map<PlayerSkill, Integer> gkSkills) {
        int wallSkill = skill(gkSkills, PlayerSkill.WALL);
        return wallSkill <= 0 ? 1.0 : 1.0 + (wallSkill / 150.0);
    }

    private static int skill(Map<PlayerSkill, Integer> skills, PlayerSkill skill) {
        if (skills == null || skills.get(skill) == null) {
            return 0;
        }
        return skills.get(skill);
    }
}
