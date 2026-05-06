package com.footballmanager.application.service.simulation.v24;

/**
 * Immutable output result of V24DetailedMatchEngine.
 */
public final class V24DetailedMatchResult {

    private final String matchId;
    private final String homeTeamId;
    private final String awayTeamId;
    private final int homeGoals;
    private final int awayGoals;
    private final double homeXg;
    private final double awayXg;
    private final int homeShots;
    private final int awayShots;
    private final int homePossession;
    private final int awayPossession;
    private final V24MatchTimeline timeline;
    private final String summary;

    public V24DetailedMatchResult(
            String matchId,
            String homeTeamId,
            String awayTeamId,
            int homeGoals,
            int awayGoals,
            double homeXg,
            double awayXg,
            int homeShots,
            int awayShots,
            int homePossession,
            int awayPossession,
            V24MatchTimeline timeline,
            String summary) {
        if (homeGoals < 0 || awayGoals < 0) {
            throw new IllegalArgumentException("goals must be non-negative");
        }
        if (!Double.isFinite(homeXg) || homeXg < 0) {
            throw new IllegalArgumentException("homeXg must be >= 0 and finite");
        }
        if (!Double.isFinite(awayXg) || awayXg < 0) {
            throw new IllegalArgumentException("awayXg must be >= 0 and finite");
        }
        if (homeShots < 0 || awayShots < 0) {
            throw new IllegalArgumentException("shots must be non-negative");
        }
        if (homePossession < 0 || homePossession > 100 || awayPossession < 0 || awayPossession > 100) {
            throw new IllegalArgumentException("possession must be between 0 and 100");
        }
        this.matchId = matchId;
        this.homeTeamId = homeTeamId;
        this.awayTeamId = awayTeamId;
        this.homeGoals = homeGoals;
        this.awayGoals = awayGoals;
        this.homeXg = homeXg;
        this.awayXg = awayXg;
        this.homeShots = homeShots;
        this.awayShots = awayShots;
        this.homePossession = homePossession;
        this.awayPossession = awayPossession;
        this.timeline = timeline;
        this.summary = (summary != null) ? summary : "";
    }

    public String matchId() { return matchId; }
    public String homeTeamId() { return homeTeamId; }
    public String awayTeamId() { return awayTeamId; }
    public int homeGoals() { return homeGoals; }
    public int awayGoals() { return awayGoals; }
    public double homeXg() { return homeXg; }
    public double awayXg() { return awayXg; }
    public int homeShots() { return homeShots; }
    public int awayShots() { return awayShots; }
    public int homePossession() { return homePossession; }
    public int awayPossession() { return awayPossession; }
    public V24MatchTimeline timeline() { return timeline; }
    public String summary() { return summary; }

    /**
     * Builder for constructing V24DetailedMatchResult progressively during simulation.
     */
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String matchId = "";
        private String homeTeamId = "";
        private String awayTeamId = "";
        private int homeGoals;
        private int awayGoals;
        private double homeXg;
        private double awayXg;
        private int homeShots;
        private int awayShots;
        private int homePossession;
        private int awayPossession;
        private V24MatchTimeline timeline = new V24MatchTimeline();
        private String summary = "";

        public Builder matchId(String v) { this.matchId = v; return this; }
        public Builder homeTeamId(String v) { this.homeTeamId = v; return this; }
        public Builder awayTeamId(String v) { this.awayTeamId = v; return this; }
        public Builder homeGoals(int v) { this.homeGoals = v; return this; }
        public Builder awayGoals(int v) { this.awayGoals = v; return this; }
        public Builder homeXg(double v) { this.homeXg = v; return this; }
        public Builder awayXg(double v) { this.awayXg = v; return this; }
        public Builder homeShots(int v) { this.homeShots = v; return this; }
        public Builder awayShots(int v) { this.awayShots = v; return this; }
        public Builder homePossession(int v) { this.homePossession = v; return this; }
        public Builder awayPossession(int v) { this.awayPossession = v; return this; }
        public Builder timeline(V24MatchTimeline v) { this.timeline = v; return this; }
        public Builder summary(String v) { this.summary = v; return this; }

        public V24DetailedMatchResult build() {
            return new V24DetailedMatchResult(
                    matchId, homeTeamId, awayTeamId,
                    homeGoals, awayGoals, homeXg, awayXg,
                    homeShots, awayShots, homePossession, awayPossession,
                    timeline, summary);
        }
    }
}