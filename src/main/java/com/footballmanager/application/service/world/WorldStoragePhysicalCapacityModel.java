package com.footballmanager.application.service.world;

/** Conservative conversion from serialized payloads to local Redis physical admission bytes. */
public final class WorldStoragePhysicalCapacityModel {

    public static final long REDIS_STRING_ENTRY_OVERHEAD_BYTES = 256L;
    public static final long DEFAULT_LOCAL_ACCOUNTING_UNCERTAINTY_MARGIN_BYTES = 64L * 1024L;

    public Estimate estimate(long currentDatasetPhysicalBytes, long legacySerializedBytes,
                             long preparedSerializedBytes, long committedSerializedBytes,
                             long catalogSerializedBytes, boolean catalogAlreadyExists) {
        requireNonNegative(currentDatasetPhysicalBytes, legacySerializedBytes, preparedSerializedBytes,
                committedSerializedBytes, catalogSerializedBytes);
        // The provider baseline is physical, while the legacy measurement is only serialized bytes.
        // Subtracting no more than the serialized payload intentionally retains the old key overhead.
        long compactedBaseline = Math.max(0, currentDatasetPhysicalBytes - legacySerializedBytes);
        long preparedPhysical = redisStringBytes(preparedSerializedBytes);
        long committedPhysical = redisStringBytes(committedSerializedBytes);
        long catalogPhysical = catalogAlreadyExists ? 0 : redisStringBytes(catalogSerializedBytes);
        long peak = Math.addExact(compactedBaseline,
                Math.addExact(Math.max(preparedPhysical, committedPhysical), catalogPhysical));
        return new Estimate(compactedBaseline, preparedPhysical, committedPhysical, catalogPhysical, peak);
    }

    public long redisStringBytes(long serializedPayloadBytes) {
        if (serializedPayloadBytes < 0) throw new IllegalArgumentException("payload bytes cannot be negative");
        return serializedPayloadBytes == 0 ? 0
                : Math.addExact(serializedPayloadBytes, REDIS_STRING_ENTRY_OVERHEAD_BYTES);
    }

    private static void requireNonNegative(long... values) {
        for (long value : values) {
            if (value < 0) throw new IllegalArgumentException("capacity measurements cannot be negative");
        }
    }

    public record Estimate(long compactedBaselinePhysicalBytes,
                           long preparedPhysicalBytes,
                           long committedPhysicalBytes,
                           long catalogPhysicalBytes,
                           long physicalPeakBytes) { }
}
