package com.footballmanager.domain.port.in.career;

import com.footballmanager.domain.model.entity.CareerSave;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ContinueCareerUseCase {
    Mono<CareerSave> continueCareer(UUID userId);
    Mono<CareerSave> getCareer(UUID userId);
    Mono<Boolean> exists(UUID userId);
}
