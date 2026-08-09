package com.footballmanager.application.observability;

import java.util.concurrent.atomic.AtomicLong;

/** Sanitized per-request timing sink for the public career reset endpoint. */
public final class ResetTiming {
    private final AtomicLong careerLookupNanos = new AtomicLong();
    private final AtomicLong registryNanos = new AtomicLong();
    private final AtomicLong cleanupNanos = new AtomicLong();

    public void careerLookup(long nanos) { careerLookupNanos.set(nanos); }
    public void registry(long nanos) { registryNanos.set(nanos); }
    public void cleanup(long nanos) { cleanupNanos.set(nanos); }
    public long careerLookupMs() { return careerLookupNanos.get() / 1_000_000L; }
    public long registryMs() { return registryNanos.get() / 1_000_000L; }
    public long cleanupMs() { return cleanupNanos.get() / 1_000_000L; }
}
