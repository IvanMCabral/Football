package com.footballmanager.application.service.simulation.v24;

/**
 * Simple mutable clock for tracking match minutes.
 * Starts at minute 1.
 */
public class V24MatchClock {

    private int currentMinute;
    private final int maxMinutes;

    public V24MatchClock(int maxMinutes) {
        if (maxMinutes <= 0) {
            throw new IllegalArgumentException("maxMinutes must be > 0");
        }
        this.maxMinutes = maxMinutes;
        this.currentMinute = 1;
    }

    public int currentMinute() { return currentMinute; }
    public int maxMinutes() { return maxMinutes; }

    public boolean isRunning() { return currentMinute < maxMinutes; }

    public void advance() {
        if (currentMinute < maxMinutes) {
            currentMinute++;
        }
    }

    public boolean isHalftime() { return currentMinute == 45; }
}