package com.footballmanager.application.service.testharness;

import com.footballmanager.domain.port.in.testharness.ScenarioMatrixRow;
import com.footballmanager.domain.port.in.testharness.ScenarioMatrixSummaryRow;

import java.util.Locale;

final class TestHarnessScenarioSummaryAccumulator {
        private final String scenario;
        private final String actionType;
        private final String actionDetail;
        private final String baselineScenario;
        private String baselineFormation;
        private String changedFormation;
        private int count;
        private double sumUserXg;
        private double minUserXg = Double.POSITIVE_INFINITY;
        private double maxUserXg = Double.NEGATIVE_INFINITY;
        private double sumOpponentXg;
        private double sumUserShots;
        private double sumOpponentShots;
        private double sumUserPossession;
        private double sumUserCentral;
        private double sumUserWide;
        private double sumOpponentCentral;
        private double sumOpponentWide;
        private double sumUserCentralXg;
        private double sumUserWideXg;
        private double sumOpponentCentralXg;
        private double sumOpponentWideXg;
        private double sumUserLeftWide;
        private double sumUserRightWide;
        private double sumOpponentLeftWide;
        private double sumOpponentRightWide;
        private double sumUserLeftWideXg;
        private double sumUserRightWideXg;
        private double sumOpponentLeftWideXg;
        private double sumOpponentRightWideXg;

        TestHarnessScenarioSummaryAccumulator(String scenario, String actionType, String actionDetail, String baselineScenario) {
            this.scenario = scenario;
            this.actionType = actionType;
            this.actionDetail = actionDetail;
            this.baselineScenario = baselineScenario;
        }

        void add(ScenarioMatrixRow row, ScenarioMatrixRow baseline, boolean userIsHome) {
            if (baselineFormation == null || baselineFormation.isBlank()) {
                baselineFormation = baseline.formation();
            }
            if (changedFormation == null || changedFormation.isBlank()) {
                changedFormation = "FORMATION".equals(row.actionType())
                    ? row.actionDetail()
                    : row.formation();
            }
            double userXg = userIsHome ? row.homeXg() : row.awayXg();
            double baseUserXg = userIsHome ? baseline.homeXg() : baseline.awayXg();
            double opponentXg = userIsHome ? row.awayXg() : row.homeXg();
            double baseOpponentXg = userIsHome ? baseline.awayXg() : baseline.homeXg();
            int userShots = userIsHome ? row.homeShots() : row.awayShots();
            int baseUserShots = userIsHome ? baseline.homeShots() : baseline.awayShots();
            int opponentShots = userIsHome ? row.awayShots() : row.homeShots();
            int baseOpponentShots = userIsHome ? baseline.awayShots() : baseline.homeShots();
            int userPossession = userIsHome ? row.homePossession() : row.awayPossession();
            int baseUserPossession = userIsHome ? baseline.homePossession() : baseline.awayPossession();
            int userCentral = userIsHome ? row.homeCentralShots() : row.awayCentralShots();
            int baseUserCentral = userIsHome ? baseline.homeCentralShots() : baseline.awayCentralShots();
            int userWide = userIsHome ? row.homeWideShots() : row.awayWideShots();
            int baseUserWide = userIsHome ? baseline.homeWideShots() : baseline.awayWideShots();
            int opponentCentral = userIsHome ? row.awayCentralShots() : row.homeCentralShots();
            int baseOpponentCentral = userIsHome ? baseline.awayCentralShots() : baseline.homeCentralShots();
            int opponentWide = userIsHome ? row.awayWideShots() : row.homeWideShots();
            int baseOpponentWide = userIsHome ? baseline.awayWideShots() : baseline.homeWideShots();
            double userCentralXg = userIsHome ? row.homeCentralXg() : row.awayCentralXg();
            double baseUserCentralXg = userIsHome ? baseline.homeCentralXg() : baseline.awayCentralXg();
            double userWideXg = userIsHome ? row.homeWideXg() : row.awayWideXg();
            double baseUserWideXg = userIsHome ? baseline.homeWideXg() : baseline.awayWideXg();
            double opponentCentralXg = userIsHome ? row.awayCentralXg() : row.homeCentralXg();
            double baseOpponentCentralXg = userIsHome ? baseline.awayCentralXg() : baseline.homeCentralXg();
            double opponentWideXg = userIsHome ? row.awayWideXg() : row.homeWideXg();
            double baseOpponentWideXg = userIsHome ? baseline.awayWideXg() : baseline.homeWideXg();
            int userLeftWide = userIsHome ? row.homeLeftWideShots() : row.awayLeftWideShots();
            int baseUserLeftWide = userIsHome ? baseline.homeLeftWideShots() : baseline.awayLeftWideShots();
            int userRightWide = userIsHome ? row.homeRightWideShots() : row.awayRightWideShots();
            int baseUserRightWide = userIsHome ? baseline.homeRightWideShots() : baseline.awayRightWideShots();
            int opponentLeftWide = userIsHome ? row.awayLeftWideShots() : row.homeLeftWideShots();
            int baseOpponentLeftWide = userIsHome ? baseline.awayLeftWideShots() : baseline.homeLeftWideShots();
            int opponentRightWide = userIsHome ? row.awayRightWideShots() : row.homeRightWideShots();
            int baseOpponentRightWide = userIsHome ? baseline.awayRightWideShots() : baseline.homeRightWideShots();
            double userLeftWideXg = userIsHome ? row.homeLeftWideXg() : row.awayLeftWideXg();
            double baseUserLeftWideXg = userIsHome ? baseline.homeLeftWideXg() : baseline.awayLeftWideXg();
            double userRightWideXg = userIsHome ? row.homeRightWideXg() : row.awayRightWideXg();
            double baseUserRightWideXg = userIsHome ? baseline.homeRightWideXg() : baseline.awayRightWideXg();
            double opponentLeftWideXg = userIsHome ? row.awayLeftWideXg() : row.homeLeftWideXg();
            double baseOpponentLeftWideXg = userIsHome ? baseline.awayLeftWideXg() : baseline.homeLeftWideXg();
            double opponentRightWideXg = userIsHome ? row.awayRightWideXg() : row.homeRightWideXg();
            double baseOpponentRightWideXg = userIsHome ? baseline.awayRightWideXg() : baseline.homeRightWideXg();

            double userXgDelta = userXg - baseUserXg;
            count++;
            sumUserXg += userXgDelta;
            minUserXg = Math.min(minUserXg, userXgDelta);
            maxUserXg = Math.max(maxUserXg, userXgDelta);
            sumOpponentXg += opponentXg - baseOpponentXg;
            sumUserShots += userShots - baseUserShots;
            sumOpponentShots += opponentShots - baseOpponentShots;
            sumUserPossession += userPossession - baseUserPossession;
            sumUserCentral += userCentral - baseUserCentral;
            sumUserWide += userWide - baseUserWide;
            sumOpponentCentral += opponentCentral - baseOpponentCentral;
            sumOpponentWide += opponentWide - baseOpponentWide;
            sumUserCentralXg += userCentralXg - baseUserCentralXg;
            sumUserWideXg += userWideXg - baseUserWideXg;
            sumOpponentCentralXg += opponentCentralXg - baseOpponentCentralXg;
            sumOpponentWideXg += opponentWideXg - baseOpponentWideXg;
            sumUserLeftWide += userLeftWide - baseUserLeftWide;
            sumUserRightWide += userRightWide - baseUserRightWide;
            sumOpponentLeftWide += opponentLeftWide - baseOpponentLeftWide;
            sumOpponentRightWide += opponentRightWide - baseOpponentRightWide;
            sumUserLeftWideXg += userLeftWideXg - baseUserLeftWideXg;
            sumUserRightWideXg += userRightWideXg - baseUserRightWideXg;
            sumOpponentLeftWideXg += opponentLeftWideXg - baseOpponentLeftWideXg;
            sumOpponentRightWideXg += opponentRightWideXg - baseOpponentRightWideXg;
        }

        ScenarioMatrixSummaryRow toRow() {
            return new ScenarioMatrixSummaryRow(
                scenario,
                actionType,
                actionDetail,
                count,
                TestHarnessCommonSupport.round3(sumUserXg / count),
                TestHarnessCommonSupport.round3(minUserXg),
                TestHarnessCommonSupport.round3(maxUserXg),
                TestHarnessCommonSupport.round3(sumOpponentXg / count),
                TestHarnessCommonSupport.round2(sumUserShots / count),
                TestHarnessCommonSupport.round2(sumOpponentShots / count),
                TestHarnessCommonSupport.round2(sumUserPossession / count),
                TestHarnessCommonSupport.round2(sumUserCentral / count),
                TestHarnessCommonSupport.round2(sumUserWide / count),
                TestHarnessCommonSupport.round2(sumOpponentCentral / count),
                TestHarnessCommonSupport.round2(sumOpponentWide / count),
                TestHarnessCommonSupport.round3(sumUserCentralXg / count),
                TestHarnessCommonSupport.round3(sumUserWideXg / count),
                TestHarnessCommonSupport.round3(sumOpponentCentralXg / count),
                TestHarnessCommonSupport.round3(sumOpponentWideXg / count),
                TestHarnessCommonSupport.round2(sumUserLeftWide / count),
                TestHarnessCommonSupport.round2(sumUserRightWide / count),
                TestHarnessCommonSupport.round2(sumOpponentLeftWide / count),
                TestHarnessCommonSupport.round2(sumOpponentRightWide / count),
                TestHarnessCommonSupport.round3(sumUserLeftWideXg / count),
                TestHarnessCommonSupport.round3(sumUserRightWideXg / count),
                TestHarnessCommonSupport.round3(sumOpponentLeftWideXg / count),
                TestHarnessCommonSupport.round3(sumOpponentRightWideXg / count),
                baselineScenario,
                baselineFormation,
                changedFormation,
                normalizedFormation(baselineFormation).equals(normalizedFormation(changedFormation)));
        }

        private static String normalizedFormation(String formation) {
            return formation == null ? "" : formation.trim().toUpperCase(Locale.ROOT);
        }
    }
