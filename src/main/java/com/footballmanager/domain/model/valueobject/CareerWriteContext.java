package com.footballmanager.domain.model.valueobject;

import java.util.Objects;
import java.util.UUID;

/** Immutable lifecycle fencing context captured at a career operation boundary. */
public record CareerWriteContext(UUID ownerId, String careerId, String expectedGeneration) {
    public CareerWriteContext {
        Objects.requireNonNull(ownerId, "ownerId");
        if (careerId == null || careerId.isBlank()) {
            throw new IllegalArgumentException("careerId must not be blank");
        }
        if (expectedGeneration == null || expectedGeneration.isBlank()) {
            throw new IllegalArgumentException("expectedGeneration must not be blank");
        }
    }
}
