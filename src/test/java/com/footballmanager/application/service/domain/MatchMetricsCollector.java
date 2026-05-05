package com.footballmanager.application.service.domain;

import com.footballmanager.domain.model.entity.MatchResult;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Test-only metrics collector for V23 Poisson goal model validation.
 * Aggregates match statistics across many simulations to compute observable metrics.
 *
 * Phase 1A: No production model changes. Pure test utility.
 */
public class MatchMetricsCollector {

    private final AtomicInteger matches = new AtomicInteger(0);
    private final AtomicLong totalGoals = new AtomicLong(0);
    private final AtomicLong homeGoals = new AtomicLong(0);
    private final AtomicLong awayGoals = new AtomicLong(0);
    private final AtomicLong totalShots = new AtomicLong(0);
    private final AtomicLong homeShots = new AtomicLong(0);
    private final AtomicLong awayShots = new AtomicLong(0);
    private final AtomicLong totalXG = new AtomicLong(0);  // stored as long*1000 for precision
    private final AtomicLong homeXG = new AtomicLong(0);
    private final AtomicLong awayXG = new AtomicLong(0);
    private final AtomicInteger zeroZero = new AtomicInteger(0);
    private final AtomicInteger fourPlusGoals = new AtomicInteger(0);
    private final AtomicInteger draws = new AtomicInteger(0);
    private final AtomicInteger homeWins = new AtomicInteger(0);
    private final AtomicInteger awayWins = new AtomicInteger(0);

    public void record(MatchResult result, double homeLambda, double awayLambda, int homeOvr, int awayOvr) {
        matches.incrementAndGet();

        int hg = result.getHomeGoals();
        int ag = result.getAwayGoals();
        int hs = result.getHomeShots();
        int as = result.getAwayShots();

        totalGoals.addAndGet(hg + ag);
        homeGoals.addAndGet(hg);
        awayGoals.addAndGet(ag);
        totalShots.addAndGet(hs + as);
        homeShots.addAndGet(hs);
        awayShots.addAndGet(as);

        // Store xG as integer*1000 for precision
        totalXG.addAndGet((long) (round4(homeLambda + awayLambda) * 1000));
        homeXG.addAndGet((long) (round4(homeLambda) * 1000));
        awayXG.addAndGet((long) (round4(awayLambda) * 1000));

        if (hg == 0 && ag == 0) zeroZero.incrementAndGet();
        if (hg + ag >= 4) fourPlusGoals.incrementAndGet();
        if (hg == ag) draws.incrementAndGet();
        if (hg > ag) homeWins.incrementAndGet();
        if (ag > hg) awayWins.incrementAndGet();
    }

    public int getMatches() { return matches.get(); }
    public double getTotalGoals() { return totalGoals.get(); }
    public double getHomeGoals() { return homeGoals.get(); }
    public double getAwayGoals() { return awayGoals.get(); }
    public double getTotalShots() { return totalShots.get(); }
    public double getHomeShots() { return homeShots.get(); }
    public double getAwayShots() { return awayShots.get(); }
    public double getTotalXG() { return totalXG.get() / 1000.0; }
    public double getHomeXG() { return homeXG.get() / 1000.0; }
    public double getAwayXG() { return awayXG.get() / 1000.0; }
    public int getZeroZero() { return zeroZero.get(); }
    public int getFourPlusGoals() { return fourPlusGoals.get(); }
    public int getDraws() { return draws.get(); }
    public int getHomeWins() { return homeWins.get(); }
    public int getAwayWins() { return awayWins.get(); }

    public double goalsPerMatch() {
        return matches.get() > 0 ? (double) totalGoals.get() / matches.get() : 0;
    }

    public double xgPerMatch() {
        return matches.get() > 0 ? getTotalXG() / matches.get() : 0;
    }

    public double goalsToXgRatio() {
        double xg = getTotalXG();
        if (xg <= 0) return 0;
        return getTotalGoals() / xg;
    }

    public double zeroZeroRate() {
        return matches.get() > 0 ? (double) zeroZero.get() / matches.get() * 100 : 0;
    }

    public double fourPlusGoalsRate() {
        return matches.get() > 0 ? (double) fourPlusGoals.get() / matches.get() * 100 : 0;
    }

    public double drawRate() {
        return matches.get() > 0 ? (double) draws.get() / matches.get() * 100 : 0;
    }

    public double homeWinRate() {
        return matches.get() > 0 ? (double) homeWins.get() / matches.get() * 100 : 0;
    }

    public double awayWinRate() {
        return matches.get() > 0 ? (double) awayWins.get() / matches.get() * 100 : 0;
    }

    public double shotsPerMatch() {
        return matches.get() > 0 ? (double) totalShots.get() / matches.get() : 0;
    }

    public void printReport(String scenarioName) {
        System.out.println("=== " + scenarioName + " (" + matches.get() + " matches) ===");
        System.out.printf("  Goals/match:      %.3f%n", goalsPerMatch());
        System.out.printf("  xG/match:          %.3f%n", xgPerMatch());
        System.out.printf("  Goals/xG ratio:    %.4f%n", goalsToXgRatio());
        System.out.printf("  Shots/match:       %.3f%n", shotsPerMatch());
        System.out.printf("  0-0 rate:          %.1f%%%n", zeroZeroRate());
        System.out.printf("  4+ goals rate:     %.1f%%%n", fourPlusGoalsRate());
        System.out.printf("  Draw rate:         %.1f%%%n", drawRate());
        System.out.printf("  Home win rate:     %.1f%%%n", homeWinRate());
        System.out.printf("  Away win rate:     %.1f%%%n", awayWinRate());
        System.out.printf("  Home xG:           %.3f%n", getHomeXG() / matches.get());
        System.out.printf("  Away xG:           %.3f%n", getAwayXG() / matches.get());
    }

    public void assertWithinRanges(String scenario) {
        double gpm = goalsPerMatch();
        double xgpm = xgPerMatch();
        double ratio = goalsToXgRatio();
        double zzr = zeroZeroRate();
        double fpg = fourPlusGoalsRate();
        double dr = drawRate();
        double hwr = homeWinRate();
        double awr = awayWinRate();
        double spm = shotsPerMatch();

        assertTrue(gpm >= 2.0 && gpm <= 3.5,
                scenario + ": goals/match " + fmt(gpm) + " expected 2.0-3.5");
        assertTrue(xgpm >= 2.0 && xgpm <= 3.5,
                scenario + ": xG/match " + fmt(xgpm) + " expected 2.0-3.5");
        assertTrue(ratio >= 0.80 && ratio <= 1.20,
                scenario + ": goals/xG " + fmt(ratio) + " expected 0.80-1.20");
        assertTrue(zzr >= 5.0,
                scenario + ": 0-0 rate " + fmt(zzr) + "% expected >=5%");
        assertTrue(fpg <= 35.0,
                scenario + ": 4+ rate " + fmt(fpg) + "% expected <=35%");
        assertTrue(dr >= 18.0 && dr <= 32.0,
                scenario + ": draw rate " + fmt(dr) + "% expected 18-32%");
        assertTrue(spm > 0 && !Double.isNaN(spm) && !Double.isInfinite(spm),
                scenario + ": shots/match " + fmt(spm) + " must be finite positive");
        assertTrue(!Double.isNaN(ratio) && !Double.isInfinite(ratio),
                scenario + ": goals/xG ratio must be finite, got " + fmt(ratio));
    }

    private void assertTrue(boolean condition, String message) {
        if (!condition) throw new AssertionError("[MatchMetricsCollector] " + message);
    }

    private String fmt(double v) { return String.format("%.3f", v); }

    private double round4(double v) { return Math.round(v * 10000.0) / 10000.0; }

    /**
     * Reproduce the exact lambda formula from MatchEngineImpl.performSimulation().
     * Duplication is acceptable in Phase 1A test-only code.
     */
    public static double[] computeLambdas(int homeOvr, int awayOvr) {
        int ovrDiff = homeOvr - awayOvr;

        double baseTotalLambda = 2.60;
        double imbalanceBoost = Math.abs(ovrDiff) * 0.012;
        double totalLambda = clamp(baseTotalLambda + imbalanceBoost, 2.3, 3.05);

        double homeBaseShare = 0.52;
        double strengthShift = ovrDiff / 220.0;
        double homeShare = clamp(homeBaseShare + strengthShift, 0.25, 0.75);

        double homeLambda = totalLambda * homeShare;
        double awayLambda = totalLambda * (1.0 - homeShare);

        return new double[] { homeLambda, awayLambda };
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}