package com.footballmanager.application.observability;

import java.util.concurrent.atomic.AtomicLong;
import java.util.Map;
import com.footballmanager.domain.ports.out.career.CareerDataCleanupResult;

/** Sanitized per-request timing sink for the public career reset endpoint. */
public final class ResetTiming {
    private final AtomicLong careerLookupNanos = new AtomicLong();
    private final AtomicLong registryNanos = new AtomicLong();
    private final AtomicLong cleanupNanos = new AtomicLong();
    private volatile Map<String, String> diagnostics = Map.of();

    public void careerLookup(long nanos) { careerLookupNanos.set(nanos); }
    public void registry(long nanos) { registryNanos.set(nanos); }
    public void cleanup(long nanos) { cleanupNanos.set(nanos); }
    public void diagnostics(CareerDataCleanupResult result) {
        diagnostics = result == null ? Map.of() : result.diagnostics();
    }
    public long careerLookupMs() { return careerLookupNanos.get() / 1_000_000L; }
    public long registryMs() { return registryNanos.get() / 1_000_000L; }
    public long cleanupMs() { return cleanupNanos.get() / 1_000_000L; }
    public String diagnostic(String key) { return diagnostics.getOrDefault(key, ""); }
}
