package com.footballmanager.application.service.career;

import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.repository.CareerRepository;
import com.footballmanager.domain.port.in.career.ContinueCareerUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.UUID;

/**
 * Implementación de UseCase para continuar una carrera existente.
 *
 * Maneja la carga de CareerSave desde Redis.
 */
@Service
@RequiredArgsConstructor
public class ContinueCareerUseCaseImpl implements ContinueCareerUseCase {

    private final CareerRepository careerRepository;

    @Override
    public Mono<CareerSave> continueCareer(UUID userId) {
        return careerRepository.findById(userId.toString())
            .flatMap(opt -> {
                if (opt.isPresent()) {
                    CareerSave career = opt.get();
                    return Mono.just(career);
                } else {
                    return Mono.empty();
                }
            });
    }

    @Override
    public Mono<CareerSave> getCareer(UUID userId) {
        return careerRepository.findById(userId.toString())
            .flatMap(opt -> opt.map(Mono::just)
                .orElseGet(() -> Mono.error(new IllegalStateException("No existe carrera activa para userId=" + userId))));
    }

    @Override
    public Mono<Boolean> exists(UUID userId) {
        return careerRepository.findById(userId.toString())
            .map(Optional::isPresent);
    }
}
