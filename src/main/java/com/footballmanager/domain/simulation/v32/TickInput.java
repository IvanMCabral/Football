package com.footballmanager.domain.simulation.v32;

import com.footballmanager.domain.simulation.v32.constants.SimulationConstants;

/**
 * Immutable input data for a single simulation tick.
 */
public final class TickInput {

    private final long tickNumber;
    private final double deltaTime;
    private final int minute;
    private final int second;

    private TickInput(long tickNumber, double deltaTime, int minute, int second) {
        this.tickNumber = tickNumber;
        this.deltaTime = deltaTime;
        this.minute = minute;
        this.second = second;
    }

    public static TickInput create(long tickNumber) {
        double dt = SimulationConstants.TICK_DURATION;
        int totalSeconds = (int) (tickNumber * dt);
        int minute = (totalSeconds / 60) % 90;
        int second = totalSeconds % 60;
        return new TickInput(tickNumber, dt, minute, second);
    }

    public long getTickNumber() { return tickNumber; }
    public double getDeltaTime() { return deltaTime; }
    public int getMinute() { return minute; }
    public int getSecond() { return second; }

    /** @return true if this is first tick of a half */
    public boolean isFirstTickOfHalf() {
        return tickNumber == 0 || tickNumber == SimulationConstants.TICKS_PER_HALF;
    }

    /** @return true if this is kickoff tick */
    public boolean isKickoff() {
        return tickNumber == 0 || tickNumber == SimulationConstants.TICKS_PER_HALF;
    }
}
