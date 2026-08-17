package com.footballmanager.application.service.security;

import com.footballmanager.application.port.out.CareerOwnershipPort;
import com.footballmanager.domain.model.valueobject.CareerWriteContext;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Single application boundary for proving that a career belongs to the
 * authenticated principal. The port's mapping and owner-index checks are the
 * durable authority; callers must complete this Mono before private reads.
 */
@Component
public final class CareerOwnershipAuthority {

    private final CareerOwnershipPort ownershipPort;

    public CareerOwnershipAuthority(CareerOwnershipPort ownershipPort) {
        this.ownershipPort = ownershipPort;
    }

    public Mono<CareerWriteContext> requireOwned(UUID ownerId, String careerId) {
        if (ownerId == null || careerId == null || careerId.isBlank()) {
            return Mono.error(new CareerOwnershipDeniedException());
        }
        return ownershipPort.capture(ownerId, careerId)
                .onErrorMap(error -> error instanceof CareerOwnershipDeniedException
                        ? error
                        : new CareerOwnershipDeniedException(error));
    }
}
