package com.footballmanager.application.service.testharness;

import com.footballmanager.domain.port.in.testharness.FormationMatrixRow;
import com.footballmanager.domain.port.in.testharness.FormationMatrixSummaryRow;

final class FormationMatrixSummaryAccumulator {

    private final String formation;
    private int count;
    private double goalsFor;
    private double goalsAgainst;
    private double possessionFor;
    private double shotsFor;
    private double shotsAgainst;
    private double xgFor;
    private double xgAgainst;
    private double centralShotsFor;
    private double wideShotsFor;
    private double longShotsFor;
    private double centralShotsAgainst;
    private double wideShotsAgainst;
    private double longShotsAgainst;
    private double leftWideShotsFor;
    private double rightWideShotsFor;
    private double leftWideShotsAgainst;
    private double rightWideShotsAgainst;
    private double leftWideXgFor;
    private double rightWideXgFor;
    private double leftWideXgAgainst;
    private double rightWideXgAgainst;
    private double shapePossessionMultiplier;
    private double shapeAttackVolumeMultiplier;
    private double shapeDefensiveResistanceMultiplier;
    private double shapeAttackLeft;
    private double shapeAttackCenter;
    private double shapeAttackRight;
    private double shapeDefenseLeft;
    private double shapeDefenseCenter;
    private double shapeDefenseRight;

    FormationMatrixSummaryAccumulator(String formation) {
        this.formation = formation;
    }

    void add(FormationMatrixRow row, boolean userIsHome) {
        count++;
        goalsFor += userIsHome ? row.homeGoals() : row.awayGoals();
        goalsAgainst += userIsHome ? row.awayGoals() : row.homeGoals();
        possessionFor += userIsHome ? row.homePossession() : row.awayPossession();
        shotsFor += userIsHome ? row.homeShots() : row.awayShots();
        shotsAgainst += userIsHome ? row.awayShots() : row.homeShots();
        xgFor += userIsHome ? row.homeXg() : row.awayXg();
        xgAgainst += userIsHome ? row.awayXg() : row.homeXg();
        centralShotsFor += userIsHome ? row.homeCentralShots() : row.awayCentralShots();
        wideShotsFor += userIsHome ? row.homeWideShots() : row.awayWideShots();
        longShotsFor += userIsHome ? row.homeLongShots() : row.awayLongShots();
        centralShotsAgainst += userIsHome ? row.awayCentralShots() : row.homeCentralShots();
        wideShotsAgainst += userIsHome ? row.awayWideShots() : row.homeWideShots();
        longShotsAgainst += userIsHome ? row.awayLongShots() : row.homeLongShots();
        leftWideShotsFor += userIsHome ? row.homeLeftWideShots() : row.awayLeftWideShots();
        rightWideShotsFor += userIsHome ? row.homeRightWideShots() : row.awayRightWideShots();
        leftWideShotsAgainst += userIsHome ? row.awayLeftWideShots() : row.homeLeftWideShots();
        rightWideShotsAgainst += userIsHome ? row.awayRightWideShots() : row.homeRightWideShots();
        leftWideXgFor += userIsHome ? row.homeLeftWideXg() : row.awayLeftWideXg();
        rightWideXgFor += userIsHome ? row.homeRightWideXg() : row.awayRightWideXg();
        leftWideXgAgainst += userIsHome ? row.awayLeftWideXg() : row.homeLeftWideXg();
        rightWideXgAgainst += userIsHome ? row.awayRightWideXg() : row.homeRightWideXg();
        shapePossessionMultiplier += row.shapePossessionMultiplier();
        shapeAttackVolumeMultiplier += row.shapeAttackVolumeMultiplier();
        shapeDefensiveResistanceMultiplier += row.shapeDefensiveResistanceMultiplier();
        shapeAttackLeft += row.shapeAttackLeft();
        shapeAttackCenter += row.shapeAttackCenter();
        shapeAttackRight += row.shapeAttackRight();
        shapeDefenseLeft += row.shapeDefenseLeft();
        shapeDefenseCenter += row.shapeDefenseCenter();
        shapeDefenseRight += row.shapeDefenseRight();
    }

    FormationMatrixSummaryRow toRow(long seedStart, int seedCount) {
        int safeCount = Math.max(1, count);
        double avgGoalsFor = round2(goalsFor / safeCount);
        double avgGoalsAgainst = round2(goalsAgainst / safeCount);
        double avgShotsFor = round2(shotsFor / safeCount);
        double avgShotsAgainst = round2(shotsAgainst / safeCount);
        double avgXgFor = round3(xgFor / safeCount);
        double avgXgAgainst = round3(xgAgainst / safeCount);
        return new FormationMatrixSummaryRow(
            formation,
            seedStart,
            seedStart + seedCount - 1L,
            seedCount,
            avgGoalsFor,
            avgGoalsAgainst,
            round2(avgGoalsFor - avgGoalsAgainst),
            round2(possessionFor / safeCount),
            avgShotsFor,
            avgShotsAgainst,
            round2(avgShotsFor - avgShotsAgainst),
            avgXgFor,
            avgXgAgainst,
            round3(avgXgFor - avgXgAgainst),
            round2(centralShotsFor / safeCount),
            round2(wideShotsFor / safeCount),
            round2(longShotsFor / safeCount),
            round2(centralShotsAgainst / safeCount),
            round2(wideShotsAgainst / safeCount),
            round2(longShotsAgainst / safeCount),
            round2(leftWideShotsFor / safeCount),
            round2(rightWideShotsFor / safeCount),
            round2(leftWideShotsAgainst / safeCount),
            round2(rightWideShotsAgainst / safeCount),
            round3(leftWideXgFor / safeCount),
            round3(rightWideXgFor / safeCount),
            round3(leftWideXgAgainst / safeCount),
            round3(rightWideXgAgainst / safeCount),
            round3(shapePossessionMultiplier / safeCount),
            round3(shapeAttackVolumeMultiplier / safeCount),
            round3(shapeDefensiveResistanceMultiplier / safeCount),
            round3(shapeAttackLeft / safeCount),
            round3(shapeAttackCenter / safeCount),
            round3(shapeAttackRight / safeCount),
            round3(shapeDefenseLeft / safeCount),
            round3(shapeDefenseCenter / safeCount),
            round3(shapeDefenseRight / safeCount));
    }

    private static double round3(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
