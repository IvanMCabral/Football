package com.footballmanager.domain.model.repository;

import com.footballmanager.domain.model.entity.CareerSave;
import reactor.core.publisher.Mono;

import java.util.Optional;

public interface CareerRepository {
    Mono<Optional<CareerSave>> findById(String id);
    Mono<Void> save(CareerSave careerSave);
    Mono<Void> deleteById(String id);
}
