package com.footballmanager.domain.simulation.v32.rng;

import com.footballmanager.domain.simulation.v32.constants.SimulationConstants;

/**
 * Scoped RNG that maintains multiple streams.
 * Thread-UNSAFE - use one per match simulation.
 */
public final class ScopedRNG {

    private final SubStream[] streams;
    private int currentStream;

    public ScopedRNG(long seed) {
        this.streams = new SubStream[SimulationConstants.RNG_STREAM_COUNT];
        for (int i = 0; i < streams.length; i++) {
            streams[i] = new SubStream(i, seed);
        }
        this.currentStream = 0;
    }

    /**
     * Creates a snapshot of current RNG state.
     * Use with restore() to revert.
     */
    public RNGState snapshot() {
        long[][] states = new long[streams.length][];
        for (int i = 0; i < streams.length; i++) {
            states[i] = streams[i].getState();
        }
        return new RNGState(states, currentStream);
    }

    /**
     * Restores RNG state from snapshot.
     */
    public void restore(RNGState state) {
        for (int i = 0; i < streams.length; i++) {
            long[] s = state.streamStates[i];
            streams[i].setState(s[0], s[1]);
        }
        this.currentStream = state.currentStream;
    }

    /**
     * Selects a stream by ID.
     * @param streamId Stream ID (0-4)
     * @return this for chaining
     */
    public ScopedRNG selectStream(int streamId) {
        this.currentStream = streamId;
        return this;
    }

    /**
     * Selects PHYSICS stream.
     */
    public ScopedRNG physics() {
        return selectStream(SimulationConstants.STREAM_PHYSICS);
    }

    /**
     * Selects AI stream.
     */
    public ScopedRNG ai() {
        return selectStream(SimulationConstants.STREAM_AI);
    }

    /**
     * Selects EVENT stream.
     */
    public ScopedRNG event() {
        return selectStream(SimulationConstants.STREAM_EVENT);
    }

    /**
     * Selects SHOT stream.
     */
    public ScopedRNG shot() {
        return selectStream(SimulationConstants.STREAM_SHOT);
    }

    /**
     * Selects META stream.
     */
    public ScopedRNG meta() {
        return selectStream(SimulationConstants.STREAM_META);
    }

    // ---- Random operations on current stream ----

    public long nextLong() { return streams[currentStream].nextLong(); }
    public int nextInt() { return streams[currentStream].nextInt(); }
    public float nextFloat() { return streams[currentStream].nextFloat(); }
    public double nextDouble() { return streams[currentStream].nextDouble(); }
    public boolean nextBoolean() { return streams[currentStream].nextBoolean(); }
    public float nextGaussian() { return streams[currentStream].nextGaussian(); }

    public int nextIntRange(int min, int max) {
        return streams[currentStream].nextIntRange(min, max);
    }

    public float nextFloatRange(float min, float max) {
        return streams[currentStream].nextFloatRange(min, max);
    }

    public double nextDoubleRange(double min, double max) {
        return streams[currentStream].nextDoubleRange(min, max);
    }

    public int nextWeightedIndex(float[] weights) {
        return streams[currentStream].nextWeightedIndex(weights);
    }

    /**
     * RNG state snapshot for rollback.
     */
    public static final class RNGState {
        private final long[][] streamStates;
        private final int currentStream;

        private RNGState(long[][] streamStates, int currentStream) {
            this.streamStates = streamStates;
            this.currentStream = currentStream;
        }

        public long[][] getStreamStates() { return streamStates; }
        public int getCurrentStream() { return currentStream; }
    }
}
