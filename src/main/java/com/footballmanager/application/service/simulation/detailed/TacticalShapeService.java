package com.footballmanager.application.service.simulation.detailed;

import com.footballmanager.domain.model.valueobject.LineupSlot;
import com.footballmanager.domain.model.valueobject.PlayerSkill;

import java.util.Map;

final class TacticalShapeService {

    private final TacticalPositionService tacticalPositionService;
    private final TacticalEffectivenessService tacticalEffectivenessService;

    TacticalShapeService(
            TacticalPositionService tacticalPositionService,
            TacticalEffectivenessService tacticalEffectivenessService) {
        this.tacticalPositionService = tacticalPositionService;
        this.tacticalEffectivenessService = tacticalEffectivenessService;
    }

    TacticalShapeProfile tacticalShapeProfile(
            TeamMatchState team,
            Map<String, LineupSlot> slotsByPlayerId) {
        return tacticalShapeProfile(team, null, slotsByPlayerId);
    }

    TacticalShapeProfile tacticalShapeProfile(
            TeamMatchState team,
            String formation,
            Map<String, LineupSlot> slotsByPlayerId) {
        if (team == null || team.startingPlayers().isEmpty()) {
            return neutralShapeProfile();
        }

        int gk = 0;
        double def = 0.0;
        double mid = 0.0;
        double att = 0.0;
        double widthSum = 0.0;
        int widthCount = 0;
        double defWidthSum = 0.0;
        double midWidthSum = 0.0;
        double attWidthSum = 0.0;
        double defYSum = 0.0;
        double midYSum = 0.0;
        double attYSum = 0.0;
        double leftLane = 0.0;
        double centerLane = 0.0;
        double rightLane = 0.0;
        double attackLeft = 0.0;
        double attackCenter = 0.0;
        double attackRight = 0.0;
        double defenseLeft = 0.0;
        double defenseCenter = 0.0;
        double defenseRight = 0.0;
        double wingbackProjectionIntent = 0.0;
        double wingbackCoverIntent = 0.0;

        for (PlayerMatchState p : team.startingPlayers()) {
            if (p == null || !p.onPitch() || p.injured() || p.redCard()) continue;
            if ("GK".equals(p.position())) {
                gk++;
                continue;
            }

            double y = tacticalPositionService.tacticalYPercent(p, slotsByPlayerId);
            double x = tacticalPositionService.tacticalXPercent(p, slotsByPlayerId);
            double eff = tacticalEffectivenessService.tacticalEffectiveness(p, slotsByPlayerId);
            double structureEff = clamp(eff, 0.25, 1.0);
            double midfieldEff = midfieldStructureEffectiveness(p, eff);
            double widthFromCenter = Math.min(1.0, Math.abs(x - 50.0) / 50.0);
            double attW = clamp((55.0 - y) / 38.0, 0.0, 1.0);
            double defW = clamp((y - 65.0) / 18.0, 0.0, 1.0);
            double shapeSum = attW + defW;
            if (shapeSum > 1.0) {
                attW /= shapeSum;
                defW /= shapeSum;
                shapeSum = 1.0;
            }
            double midW = 1.0 - shapeSum;

            double weightedAttW = attW * structureEff;
            double weightedMidW = midW * midfieldEff * midfieldProfileMultiplier(p);
            double weightedDefW = defW * structureEff;

            att += weightedAttW;
            mid += weightedMidW;
            def += weightedDefW;
            attWidthSum += widthFromCenter * weightedAttW;
            midWidthSum += widthFromCenter * weightedMidW;
            defWidthSum += widthFromCenter * weightedDefW;
            attYSum += y * weightedAttW;
            midYSum += y * weightedMidW;
            defYSum += y * weightedDefW;

            double leftWeight = tacticalPositionService.laneLeftWeight(x);
            double centerWeight = tacticalPositionService.laneCenterWeight(x);
            double rightWeight = tacticalPositionService.laneRightWeight(x);
            leftLane += leftWeight;
            centerLane += centerWeight;
            rightLane += rightWeight;

            double wingbackVerticalIntent = wingbackVerticalIntent(x, y);
            wingbackProjectionIntent += Math.max(0.0, wingbackVerticalIntent);
            wingbackCoverIntent += Math.max(0.0, -wingbackVerticalIntent);
            double verticalAttackIntent = clamp((100.0 - y) / 100.0, 0.0, 1.0);
            double verticalDefenseIntent = clamp(y / 100.0, 0.0, 1.0);
            double manualLaneIntent = 1.0 + (widthFromCenter - 0.40) * 0.16;
            double attackWeight = verticalAttackIntent
                    * structureEff
                    * clamp(manualLaneIntent, 0.92, 1.10)
                    * clamp(1.0 + Math.max(0.0, wingbackVerticalIntent) * 0.34
                            + Math.min(0.0, wingbackVerticalIntent) * 0.24,
                        0.82, 1.24);
            double defenseQuality = defensiveChannelQuality(p);
            double defenseWeight = verticalDefenseIntent
                    * structureEff
                    * defenseQuality
                    * clamp(1.0 + (widthFromCenter - 0.36) * 0.12, 0.94, 1.10)
                    * clamp(1.0 - Math.max(0.0, wingbackVerticalIntent) * 0.28
                            - Math.min(0.0, wingbackVerticalIntent) * 0.30,
                        0.82, 1.24);
            attackLeft += attackWeight * leftWeight;
            attackCenter += attackWeight * centerWeight;
            attackRight += attackWeight * rightWeight;
            defenseLeft += defenseWeight * leftWeight;
            defenseCenter += defenseWeight * centerWeight;
            defenseRight += defenseWeight * rightWeight;

            widthSum += widthFromCenter;
            widthCount++;
        }

        double width = widthCount > 0 ? widthSum / widthCount : 0.45;
        double defWidth = def > 0 ? defWidthSum / def : width;
        double midWidth = mid > 0 ? midWidthSum / mid : width;
        double attWidth = att > 0 ? attWidthSum / att : width;
        double defAvgY = def > 0 ? defYSum / def : 78.0;
        double midAvgY = mid > 0 ? midYSum / mid : 50.0;
        double attAvgY = att > 0 ? attYSum / att : 15.0;
        double centerShare = widthCount > 0 ? (double) centerLane / widthCount : 0.45;
        double sideBalance = widthCount > 0
                ? 1.0 - (Math.abs(leftLane - rightLane) / (double) widthCount)
                : 1.0;

        double midDelta = (mid - 4.0) * 0.065;
        double midfieldWidthBonus = (midWidth - 0.34) * 0.20;
        double centralOverloadBonus = Math.min(0.065, Math.max(0.0, centerShare - 0.45) * 0.13);
        double noOutletPenalty = Math.max(0.0, 0.22 - attWidth) * 0.22;
        double excessiveWidthPenalty = Math.max(0.0, width - 0.68) * 0.12;
        double midfieldShortagePenalty = Math.max(0.0, 4.0 - mid) * 0.045;
        double possession = 1.0 + midDelta + midfieldWidthBonus + centralOverloadBonus
                - noOutletPenalty - excessiveWidthPenalty - midfieldShortagePenalty;
        double attackDelta = (att - 2.0) * 0.145;
        double usefulAttackWidth = (attWidth - 0.30) * 0.36;
        double supportFromMidfield = (66.6667 - midAvgY) / 66.6667 * 0.10;
        double advancedLineBonus = (22.2222 - attAvgY) / 22.2222 * 0.075;
        double sideImbalancePenalty = Math.max(0.0, 0.72 - sideBalance) * 0.10;
        double noGkPenalty = gk == 1 ? 0.0 : 0.08;
        double attackVolume = 1.0 + attackDelta + usefulAttackWidth + supportFromMidfield
                + advancedLineBonus - sideImbalancePenalty - noGkPenalty;
        attackVolume += wingbackProjectionIntent * 0.035;
        attackVolume -= wingbackCoverIntent * 0.025;
        double defDelta = (def - 4.0) * 0.125;
        double defensiveWidthBonus = Math.min(0.095, Math.max(0.0, defWidth - 0.34) * 0.24);
        double lowBlockBonus = Math.max(0.0, defAvgY - 74.0) * 0.0048;
        double midfieldScreenBonus = Math.max(0.0, mid - 3.0) * 0.030;
        double midfieldScreenPenalty = Math.max(0.0, 4.0 - mid) * 0.070;
        double flankGapPenalty = Math.max(0.0, 0.30 - defWidth) * 0.34;
        double centralGapPenalty = Math.max(0.0, defWidth - 0.72) * 0.19;
        double defensiveStrength = defDelta + defensiveWidthBonus + lowBlockBonus + midfieldScreenBonus
                - midfieldScreenPenalty - flankGapPenalty - centralGapPenalty;
        double resistance = 1.0 - defensiveStrength;
        resistance += wingbackProjectionIntent * 0.026;
        resistance -= wingbackCoverIntent * 0.034;
        double lowBlockBackFiveShell = clamp(
                Math.max(0.0, defAvgY - 78.0) * 0.035
                        + Math.max(0.0, def - 4.0) * 0.35,
                0.0, 0.55);
        double lowBlockSecondLineDepth = clamp((midAvgY - 56.0) / 20.0, 0.0, 1.0);
        double lowBlockShapeIntent = clamp(
                lowBlockBackFiveShell + (lowBlockSecondLineDepth * 0.45),
                0.0, 1.0);

        if ("4-1-2-3".equals(formation)) {
            possession += 0.070;   // pivot improves circulation/control
            attackVolume -= 0.040; // one safer midfielder, but still a real front three
            resistance -= 0.140;   // lower opponent chance quality via central screen
        } else if ("4-3-3".equals(formation)) {
            possession -= 0.010;
            attackVolume += 0.075;
            resistance += 0.065;
        } else if ("4-2-2-2".equals(formation)) {
            possession -= 0.015;   // narrow box can be pressed toward touchlines
            attackVolume += 0.040; // two ST + two inside AMs create vertical punches
            resistance -= 0.024;   // double pivot keeps the narrow box from collapsing centrally
        } else if ("3-5-2-CDM".equals(formation)) {
            possession += 0.025;   // holder gives cleaner reset option
            attackVolume -= 0.020; // one CM sits instead of joining attacks
            resistance -= 0.060;   // real central shield
        } else if ("3-5-2".equals(formation)) {
            possession += 0.010 + (wingbackProjectionIntent * 0.010);
            attackVolume += 0.020 + (wingbackProjectionIntent * 0.030);
            resistance -= 0.055;
            resistance += wingbackProjectionIntent * 0.035; // high carrileros create transition space behind them
        } else if ("5-3-2".equals(formation)) {
            possession += 0.015;   // extra security helps recycle possession
            attackVolume -= 0.040; // fewer natural high/wide outlets
            resistance -= 0.180;   // five defenders should reduce opponent quality/volume
        } else if ("5-4-1".equals(formation)) {
            double secondLineOutlet = Math.max(0.0, 68.0 - midAvgY) * 0.018;
            possession -= 0.012 + (lowBlockShapeIntent * 0.018);
            attackVolume -= 0.085 + (lowBlockShapeIntent * 0.055);
            attackVolume += secondLineOutlet;
            resistance -= 0.080 + (lowBlockShapeIntent * 0.190);
        }

        possession = clamp(possession, 0.84, 1.18);
        attackVolume = clamp(attackVolume, 0.70, 1.30);
        resistance = clamp(resistance, 0.68, 1.24);

        double attackLeftChannel = normalizeChannel(attackLeft);
        double attackCenterChannel = normalizeChannel(attackCenter);
        double attackRightChannel = normalizeChannel(attackRight);
        double defenseLeftChannel = normalizeChannel(defenseLeft);
        double defenseCenterChannel = normalizeChannel(defenseCenter);
        double defenseRightChannel = normalizeChannel(defenseRight);

        if ("5-4-1".equals(formation)) {
            double lowBlockChannelIntent = 0.35 + (lowBlockShapeIntent * 0.65);
            defenseCenterChannel = clamp(defenseCenterChannel + (0.26 * lowBlockChannelIntent), 0.35, 1.65);
            defenseLeftChannel = clamp(defenseLeftChannel + (0.16 * lowBlockChannelIntent), 0.35, 1.65);
            defenseRightChannel = clamp(defenseRightChannel + (0.16 * lowBlockChannelIntent), 0.35, 1.65);
            attackCenterChannel = clamp(attackCenterChannel - (0.08 * lowBlockChannelIntent), 0.35, 1.65);
        } else if ("5-3-2".equals(formation)) {
            defenseCenterChannel = clamp(defenseCenterChannel + 0.14, 0.35, 1.65);
            defenseLeftChannel = clamp(defenseLeftChannel + 0.24, 0.35, 1.65);
            defenseRightChannel = clamp(defenseRightChannel + 0.24, 0.35, 1.65);
        } else if ("3-5-2".equals(formation)) {
            double projectedWingbackAttack = 0.06 + Math.min(0.12, wingbackProjectionIntent * 0.045);
            attackLeftChannel = clamp(attackLeftChannel + projectedWingbackAttack, 0.35, 1.65);
            attackRightChannel = clamp(attackRightChannel + projectedWingbackAttack, 0.35, 1.65);
            defenseCenterChannel = clamp(defenseCenterChannel + 0.06, 0.35, 1.65);
            defenseLeftChannel = clamp(defenseLeftChannel + 0.26, 0.35, 1.65);
            defenseRightChannel = clamp(defenseRightChannel + 0.26, 0.35, 1.65);
        } else if ("3-5-2-CDM".equals(formation)) {
            attackLeftChannel = clamp(attackLeftChannel + 0.06, 0.35, 1.65);
            attackRightChannel = clamp(attackRightChannel + 0.06, 0.35, 1.65);
            defenseCenterChannel = clamp(defenseCenterChannel + 0.10, 0.35, 1.65);
            defenseLeftChannel = clamp(defenseLeftChannel + 0.36, 0.35, 1.65);
            defenseRightChannel = clamp(defenseRightChannel + 0.36, 0.35, 1.65);
        } else if ("4-3-3".equals(formation)) {
            attackLeftChannel = clamp(attackLeftChannel + 0.24, 0.35, 1.65);
            attackRightChannel = clamp(attackRightChannel + 0.24, 0.35, 1.65);
            attackCenterChannel = clamp(attackCenterChannel - 0.06, 0.35, 1.65);
            defenseLeftChannel = clamp(defenseLeftChannel - 0.08, 0.35, 1.65);
            defenseRightChannel = clamp(defenseRightChannel - 0.08, 0.35, 1.65);
        } else if ("4-2-2-2".equals(formation)) {
            defenseCenterChannel = clamp(defenseCenterChannel + 0.10, 0.35, 1.65);
            defenseLeftChannel = clamp(defenseLeftChannel - 0.04, 0.35, 1.65);
            defenseRightChannel = clamp(defenseRightChannel - 0.04, 0.35, 1.65);
        }

        return new TacticalShapeProfile(
                possession,
                attackVolume,
                resistance,
                attackLeftChannel,
                attackCenterChannel,
                attackRightChannel,
                defenseLeftChannel,
                defenseCenterChannel,
                defenseRightChannel);
    }

    TacticalShapeProfile neutralShapeProfile() {
        return new TacticalShapeProfile(1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0);
    }

    private double wingbackVerticalIntent(double xPercent, double yPercent) {
        double widthFromCenter = Math.abs(xPercent - 50.0) / 50.0;
        if (widthFromCenter < 0.68 || yPercent < 38.0 || yPercent > 82.0) {
            return 0.0;
        }
        return clamp((55.0 - yPercent) / 17.0, -1.0, 1.0);
    }

    private double defensiveChannelQuality(PlayerMatchState player) {
        if (player == null) return 1.0;
        double defensiveBase = ((player.defense() + player.mentality()) / 2.0) / 70.0;
        double positionalMultiplier = switch (player.position()) {
            case "GK" -> 1.05;
            case "DEF" -> 1.00;
            case "MID" -> 0.88;
            default -> 0.72;
        };
        return clamp(defensiveBase * positionalMultiplier, 0.55, 1.22);
    }

    private double midfieldStructureEffectiveness(PlayerMatchState player, double tacticalEffectiveness) {
        if (player == null) return 1.0;
        double eff = clamp(tacticalEffectiveness, 0.0, 1.0);
        return clamp(eff * eff, 0.20, 1.0);
    }

    private double midfieldProfileMultiplier(PlayerMatchState player) {
        if (player == null) return 1.0;
        double controlProfile =
                player.technique() * 0.38
                        + player.mentality() * 0.24
                        + player.getSkillLevel(PlayerSkill.PASSER) * 0.16
                        + player.getSkillLevel(PlayerSkill.PLAYMAKER) * 0.12
                        + player.stamina() * 0.10;
        double screenProfile =
                player.defense() * 0.44
                        + player.mentality() * 0.20
                        + player.stamina() * 0.14
                        + player.getSkillLevel(PlayerSkill.TACKLER) * 0.14
                        + player.getSkillLevel(PlayerSkill.MARKER) * 0.08;
        double midfieldProfile = (controlProfile * 0.56) + (screenProfile * 0.44);
        double naturalMidfieldFit = midfieldNaturalFit(player);
        return clamp((0.72 + (midfieldProfile / 250.0)) * naturalMidfieldFit, 0.50, 1.12);
    }

    private double midfieldNaturalFit(PlayerMatchState player) {
        if (player == null) return 1.0;
        String natural = player.naturalPosition() != null ? player.naturalPosition() : player.position();
        String tactical = player.position();
        return switch (natural) {
            case "MID" -> 1.0;
            case "DEF" -> "MID".equals(tactical) ? 0.84 : 0.78;
            case "WINGER" -> 0.76;
            case "ATT" -> 0.70;
            case "GK" -> 0.20;
            default -> 0.86;
        };
    }

    private double normalizeChannel(double raw) {
        return clamp(raw / 2.0, 0.35, 1.65);
    }



    private static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }
}