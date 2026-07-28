package com.footballmanager.application.service.simulation.detailed;

import com.footballmanager.domain.model.valueobject.PlayerSkill;

import java.util.Map;

public class ShotXgCalculator {

    private static final double MIN_XG = 0.01;
    private static final double MAX_XG = 0.60;

    private static final double INSIDE_BOX_DISTANCE = 16.0; // meters from goal line
    private static final double SIX_YARD_BOX_DISTANCE = 8.0;

    public double calculateXg(ShotQuality quality, String formation) {
        return calculateXg(quality, formation, "4-4-2", 70.0, 70.0);
    }

    public double calculateXg(ShotQuality quality, String formation,
                              String opponentFormation,
                              double possessorAttack, double opponentDefense) {
        return calculateXg(quality, formation, opponentFormation,
                possessorAttack, opponentDefense,
                Map.of(), null,    // shooter: sin skills, sin height
                Map.of(), null);   // gk: sin skills, sin height
    }

    public double calculateXg(ShotQuality quality, String formation,
                              String opponentFormation,
                              double possessorAttack, double opponentDefense,
                              Map<PlayerSkill, Integer> shooterSkills, Integer shooterHeightCm,
                              Map<PlayerSkill, Integer> gkSkills, Integer gkHeightCm) {
        return calculateXg(quality, formation, opponentFormation,
                possessorAttack, opponentDefense,
                shooterSkills, shooterHeightCm, gkSkills, gkHeightCm,
                ShotEventType.OPEN_PLAY);
    }

    public double calculateXg(ShotQuality quality, String formation,
                              String opponentFormation,
                              double possessorAttack, double opponentDefense,
                              Map<PlayerSkill, Integer> shooterSkills, Integer shooterHeightCm,
                              Map<PlayerSkill, Integer> gkSkills, Integer gkHeightCm,
                              ShotEventType eventSubType) {
        return calculateXg(quality, formation, opponentFormation,
                possessorAttack, opponentDefense,
                shooterSkills, shooterHeightCm,
                gkSkills, gkHeightCm,
                eventSubType,
                Map.of(), null);
    }

    public double calculateXg(ShotQuality quality, String formation,
                              String opponentFormation,
                              double possessorAttack, double opponentDefense,
                              Map<PlayerSkill, Integer> shooterSkills, Integer shooterHeightCm,
                              Map<PlayerSkill, Integer> gkSkills, Integer gkHeightCm,
                              ShotEventType eventSubType,
                              Map<PlayerSkill, Integer> defenderSkills, Integer defenderHeightCm) {
        double baseXgVal = baseXg(quality.location());
        double shooterMult = shooterMultiplier(quality.shooterQuality());
        double assistMult = assistMultiplier(quality.assistQuality());
        double defMult = defensiveMultiplier(quality.defensivePressure());
        double gkMult = goalkeeperMultiplier(quality.goalkeeperQuality());
        double styleMult = styleMultiplier(quality.tacticModifier());
        double offFormMod = formationOffensiveModifier(formation, possessorAttack);
        double defFormMod = formationDefensiveModifier(opponentFormation, opponentDefense);
        double formationModRatio = offFormMod / defFormMod;
        formationModRatio = Math.max(0.5, formationModRatio);
        offFormMod = formationModRatio * defFormMod;

        double headerMult = ShotSkillMultipliers.headerAndAerial(
            eventSubType, shooterSkills, shooterHeightCm);
        double shooterLongRangeMult = ShotSkillMultipliers.longRangeShooter(
            quality.location(), shooterSkills);
        double markerMult = ShotSkillMultipliers.marker(defenderSkills);
        double tacklerMult = ShotSkillMultipliers.tackler(eventSubType, defenderSkills);
        double wallDivisor = ShotSkillMultipliers.wallDivisor(gkSkills);
        double xg = baseXgVal * shooterMult * assistMult * defMult * gkMult * styleMult
                * offFormMod / defFormMod * headerMult * shooterLongRangeMult
                * markerMult * tacklerMult / wallDivisor;

        return clamp(xg);
    }
    private double baseXg(ShotLocation location) {
        return switch (location) {
            case SIX_YARD_BOX -> 0.200;
            case PENALTY_AREA_CENTER -> 0.120;
            case PENALTY_AREA_WIDE -> 0.100;
            case OUTSIDE_BOX -> 0.040;
            case LONG_RANGE -> 0.020;
        };
    }

    private double formationOffensiveModifier(String formation, double teamAttack) {
        double baseMod = 1.00;
        double statsAmp = 1.0 + (teamAttack - 70.0) * 0.025;
        double mod = baseMod * statsAmp;
        return Math.max(0.1, mod);
    }

    private double formationDefensiveModifier(String opponentFormation, double opponentDefense) {
        double baseMod = 1.00;

        double statsAmp = 1.0 + (opponentDefense - 70.0) * 0.025;
        double mod = baseMod * statsAmp;
        return Math.max(0.1, mod);
    }

    private double shooterMultiplier(double shooterQuality) {
        return 0.70 + (shooterQuality * 0.60);
    }

    private double assistMultiplier(double assistQuality) {
        return 0.85 + (assistQuality * 0.30);
    }

    private double defensiveMultiplier(double defensivePressure) {
        return Math.max(0.30, 1.10 - (defensivePressure * 0.80));
    }

    private double goalkeeperMultiplier(double goalkeeperQuality) {
        return Math.max(0.50, 1.05 - (goalkeeperQuality * 0.55));
    }

    private double styleMultiplier(double tacticModifier) {
        double m = Math.max(0.5, Math.min(1.5, tacticModifier));
        return 0.85 + (m - 0.5) * 0.30;
    }

    private double clamp(double xg) {
        if (xg < MIN_XG) return MIN_XG;
        if (xg > MAX_XG) return MAX_XG;
        return Math.round(xg * 1000.0) / 1000.0;
    }
}
