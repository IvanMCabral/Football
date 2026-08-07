package com.footballmanager.domain.model.repository;

import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.valueobject.CareerWriteContext;
import reactor.core.publisher.Mono;

import java.util.Optional;

public interface CareerRepository {
    Mono<Optional<CareerSave>> findById(String id);
    /** Explicit bootstrap operation for a brand-new career lifecycle. */
    Mono<Void> createInitialCareer(CareerSave careerSave);

    /** Fenced update operation; context is mandatory for an existing career. */
    Mono<Void> saveExistingCareer(CareerWriteContext context, CareerSave careerSave);

    /**
     * Deliberately fail-closed legacy surface. Production callers must choose
     * createInitialCareer or saveExistingCareer explicitly.
     */
    @Deprecated
    default Mono<Void> save(CareerSave careerSave) {
        return Mono.error(new IllegalStateException(
                "career persistence requires explicit lifecycle operation"));
    }
    Mono<Void> deleteById(String id);
}
