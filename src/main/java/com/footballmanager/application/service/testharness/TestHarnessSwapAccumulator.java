package com.footballmanager.application.service.testharness;

import com.footballmanager.application.service.simulation.v24.V24DetailedMatchResult;

final class TestHarnessSwapAccumulator {

    private int count;
    private double goalsFor;
    private double goalsAgainst;
    private double shotsFor;
    private double shotsAgainst;
    private double possessionFor;
    private double xgFor;
    private double xgAgainst;
    private double centralShotsFor;
    private double wideShotsFor;
    private double longShotsFor;
    private double centralShotsAgainst;
    private double wideShotsAgainst;
    private double longShotsAgainst;
    private double centralXgFor;
    private double wideXgFor;
    private double longXgFor;
    private double centralXgAgainst;
    private double wideXgAgainst;
    private double longXgAgainst;
    private double leftWideShotsFor;
    private double rightWideShotsFor;
    private double leftWideShotsAgainst;
    private double rightWideShotsAgainst;
    private double leftWideXgFor;
    private double rightWideXgFor;
    private double leftWideXgAgainst;
    private double rightWideXgAgainst;

    void add(V24DetailedMatchResult result, boolean userIsHome) {
        TestHarnessZoneCounts zones = TestHarnessZoneCounter.count(result);
        count++;
        goalsFor += userIsHome ? result.homeGoals() : result.awayGoals();
        goalsAgainst += userIsHome ? result.awayGoals() : result.homeGoals();
        shotsFor += userIsHome ? result.homeShots() : result.awayShots();
        shotsAgainst += userIsHome ? result.awayShots() : result.homeShots();
        possessionFor += userIsHome ? result.homePossession() : result.awayPossession();
        xgFor += userIsHome ? result.homeXg() : result.awayXg();
        xgAgainst += userIsHome ? result.awayXg() : result.homeXg();
        centralShotsFor += userIsHome ? zones.homeCentral() : zones.awayCentral();
        wideShotsFor += userIsHome ? zones.homeWide() : zones.awayWide();
        longShotsFor += userIsHome ? zones.homeLong() : zones.awayLong();
        centralShotsAgainst += userIsHome ? zones.awayCentral() : zones.homeCentral();
        wideShotsAgainst += userIsHome ? zones.awayWide() : zones.homeWide();
        longShotsAgainst += userIsHome ? zones.awayLong() : zones.homeLong();
        centralXgFor += userIsHome ? zones.homeCentralXg() : zones.awayCentralXg();
        wideXgFor += userIsHome ? zones.homeWideXg() : zones.awayWideXg();
        longXgFor += userIsHome ? zones.homeLongXg() : zones.awayLongXg();
        centralXgAgainst += userIsHome ? zones.awayCentralXg() : zones.homeCentralXg();
        wideXgAgainst += userIsHome ? zones.awayWideXg() : zones.homeWideXg();
        longXgAgainst += userIsHome ? zones.awayLongXg() : zones.homeLongXg();
        leftWideShotsFor += userIsHome ? zones.homeLeftWide() : zones.awayLeftWide();
        rightWideShotsFor += userIsHome ? zones.homeRightWide() : zones.awayRightWide();
        leftWideShotsAgainst += userIsHome ? zones.awayLeftWide() : zones.homeLeftWide();
        rightWideShotsAgainst += userIsHome ? zones.awayRightWide() : zones.homeRightWide();
        leftWideXgFor += userIsHome ? zones.homeLeftWideXg() : zones.awayLeftWideXg();
        rightWideXgFor += userIsHome ? zones.homeRightWideXg() : zones.awayRightWideXg();
        leftWideXgAgainst += userIsHome ? zones.awayLeftWideXg() : zones.homeLeftWideXg();
        rightWideXgAgainst += userIsHome ? zones.awayRightWideXg() : zones.homeRightWideXg();
    }

    TestHarnessSwapAverages averages() {
        if (count <= 0) {
            return new TestHarnessSwapAverages(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }
        double avgGoalsFor = round2(goalsFor / count);
        double avgGoalsAgainst = round2(goalsAgainst / count);
        double avgShotsFor = round2(shotsFor / count);
        double avgShotsAgainst = round2(shotsAgainst / count);
        double avgXgFor = round3(xgFor / count);
        double avgXgAgainst = round3(xgAgainst / count);
        return new TestHarnessSwapAverages(
            avgGoalsFor,
            avgGoalsAgainst,
            round2(avgGoalsFor - avgGoalsAgainst),
            avgShotsFor,
            avgShotsAgainst,
            round2(possessionFor / count),
            avgXgFor,
            avgXgAgainst,
            round3(avgXgFor - avgXgAgainst),
            round2(centralShotsFor / count),
            round2(wideShotsFor / count),
            round2(longShotsFor / count),
            round2(centralShotsAgainst / count),
            round2(wideShotsAgainst / count),
            round2(longShotsAgainst / count),
            round3(centralXgFor / count),
            round3(wideXgFor / count),
            round3(longXgFor / count),
            round3(centralXgAgainst / count),
            round3(wideXgAgainst / count),
            round3(longXgAgainst / count),
            round2(leftWideShotsFor / count),
            round2(rightWideShotsFor / count),
            round2(leftWideShotsAgainst / count),
            round2(rightWideShotsAgainst / count),
            round3(leftWideXgFor / count),
            round3(rightWideXgFor / count),
            round3(leftWideXgAgainst / count),
            round3(rightWideXgAgainst / count));
    }

    private static double round3(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
