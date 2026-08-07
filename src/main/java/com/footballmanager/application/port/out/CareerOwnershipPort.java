package com.footballmanager.application.port.out;

import com.footballmanager.domain.model.valueobject.CareerWriteContext;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.function.Supplier;

/** Application boundary for lifecycle ownership and generation fencing. */
public interface CareerOwnershipPort {

    Mono<CareerWriteContext> capture(UUID ownerId, String careerId);

    <T> Mono<T> touchBeforeWrite(CareerWriteContext context, Supplier<Mono<T>> write);
}
