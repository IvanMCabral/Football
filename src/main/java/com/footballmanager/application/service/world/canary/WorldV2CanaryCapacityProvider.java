package com.footballmanager.application.service.world.canary;

import reactor.core.publisher.Mono;

/** Read-only provider accounting port used exclusively by the one-shot canary runner. */
public interface WorldV2CanaryCapacityProvider {

    Mono<CapacitySample> sample();

    record CapacitySample(long currentStorageBytes) {
        public CapacitySample {
            if (currentStorageBytes < 0) {
                throw new IllegalArgumentException("invalid provider capacity sample");
            }
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
