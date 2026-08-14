package com.footballmanager.application.service.world.canary;

import reactor.core.publisher.Mono;

/** Read-only provider accounting port used exclusively by the one-shot canary runner. */
public interface WorldV2CanaryCapacityProvider {

    Mono<CapacitySample> sample();

    record CapacitySample(long currentStorageBytes, long quotaBytes,
                          long requiredHeadroomBytes, long retainedCushionBytes) {
        public CapacitySample {
            if (currentStorageBytes < 0 || quotaBytes <= 0
                    || requiredHeadroomBytes < 0 || retainedCushionBytes < 0) {
                throw new IllegalArgumentException("invalid provider capacity sample");
            }
        }

        public long admissionThresholdBytes() {
            return Math.subtractExact(Math.subtractExact(quotaBytes, requiredHeadroomBytes),
                    retainedCushionBytes);
        }

        public boolean admitted(long configuredThresholdBytes) {
            return currentStorageBytes <= configuredThresholdBytes
                    && currentStorageBytes <= admissionThresholdBytes();
        }
    }

    enum FailureKind { AUTHENTICATION, UNAVAILABLE, MALFORMED }

    final class ProviderCapacityException extends RuntimeException {
        private final FailureKind kind;

        public ProviderCapacityException(FailureKind kind, String message, Throwable cause) {
            super(message, cause);
            this.kind = kind;
        }

        public FailureKind kind() {
            return kind;
        }
    }
}
