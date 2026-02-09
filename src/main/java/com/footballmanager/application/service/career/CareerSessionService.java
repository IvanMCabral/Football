package com.footballmanager.application.service.career;

import com.footballmanager.application.engine.round.RoundEngineRegistry;
import com.footballmanager.application.service.match.session.MatchSessionRegistry;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.repository.CareerRepository;
import com.footballmanager.domain.port.in.career.StartCareerUseCase;
import com.footballmanager.domain.port.in.career.ContinueCareerUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CareerSessionService - Facade para gestión de sesión Career.
 */
@Service
@RequiredArgsConstructor
public class CareerSessionService {

    private final CareerRepository careerRepository;
    private final StartCareerUseCase startCareerUseCase;
    private final ContinueCareerUseCase continueCareerUseCase;
    private final RoundEngineRegistry roundEngineRegistry;
    private final MatchSessionRegistry matchSessionRegistry;

    private final Map<String, CareerSave> careerCache = new ConcurrentHashMap<>();

    public Mono<CareerSave> getCareerFromCache(UUID userId) {
        String key = userId.toString();
        CareerSave cached = careerCache.get(key);

        if (cached != null) {
            return Mono.just(cached);
        }

        return continueCareerUseCase.continueCareer(userId)
            .doOnNext(career -> {
                if (career != null) {
                    careerCache.put(key, career);
                }
            });
    }

    public void invalidateCache(UUID userId) {
        careerCache.remove(userId.toString());
    }

    public void clearCache() {
        careerCache.clear();
    }

    public int getCacheSize() {
        return careerCache.size();
    }

    @Deprecated
    public Mono<CareerSave> startNewCareer(UUID userId, String worldLeagueId, String worldTeamId,
                                            String difficulty, String gameSpeed, int teamsPerDivision) {
        return startCareerUseCase.start(userId, worldLeagueId, worldTeamId, difficulty, gameSpeed, teamsPerDivision);
    }

    public Mono<CareerSave> continueCareer(UUID userId) {
        return continueCareerUseCase.continueCareer(userId);
    }

    public Mono<CareerSave> getCareer(UUID userId) {
        return continueCareerUseCase.getCareer(userId);
    }

    public Mono<Boolean> careerExists(UUID userId) {
        return continueCareerUseCase.exists(userId);
    }

    public Mono<CareerSave> saveCareer(CareerSave career) {
        String key = career.getUserId().toString();

        return careerRepository.save(career)
            .doOnSuccess(saved -> careerCache.put(key, career))
            .doOnError(error -> {
                // Log error silently
            })
            .thenReturn(career);
    }

    public Mono<Void> deleteCareer(UUID userId) {
        invalidateCache(userId);
        roundEngineRegistry.stopAllEngines();
        matchSessionRegistry.clearAllSessions();
        return careerRepository.deleteById(userId.toString());
    }

    @Deprecated
    public Mono<CareerSave> startNewCareer(UUID userId, String worldLeagueId, String worldTeamId,
                                            String difficulty, String gameSpeed) {
        CareerSave career = new CareerSave();
        career.setUserId(userId);
        return careerRepository.save(career).thenReturn(career);
    }

    @Deprecated
    public Mono<CareerSave> startNewCareer(UUID userId, Long leagueId, Long teamId) {
        CareerSave career = new CareerSave();
        career.setUserId(userId);
        return careerRepository.save(career).thenReturn(career);
    }
}
