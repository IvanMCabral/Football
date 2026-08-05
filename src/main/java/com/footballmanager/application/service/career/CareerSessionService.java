package com.footballmanager.application.service.career;

import com.footballmanager.application.engine.round.RoundEngineRegistry;
import com.footballmanager.application.service.match.session.MatchSessionRegistry;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.repository.CareerRepository;
import com.footballmanager.domain.port.in.career.StartCareerUseCase;
import com.footballmanager.domain.port.in.career.ContinueCareerUseCase;
import com.footballmanager.domain.ports.out.career.CareerDataCleanupRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class CareerSessionService {

    private final CareerRepository careerRepository;
    private final StartCareerUseCase startCareerUseCase;
    private final ContinueCareerUseCase continueCareerUseCase;
    private final RoundEngineRegistry roundEngineRegistry;
    private final MatchSessionRegistry matchSessionRegistry;
    private final CareerDataCleanupRepository careerDataCleanupRepository;

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

    /** Indicates whether the request can reuse the in-process career snapshot. */
    public boolean isCareerCached(UUID userId) {
        return userId != null && careerCache.containsKey(userId.toString());
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
                                            String difficulty, String gameSpeed, Integer teamsPerDivision) {
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
        return careerRepository.findById(userId.toString())
                .defaultIfEmpty(java.util.Optional.empty())
                .flatMap(existing -> {
                    String careerId = existing.map(CareerSave::getCareerId).orElse(null);
                    roundEngineRegistry.stopEnginesForOwner(userId, careerId);
                    matchSessionRegistry.clearSessionsForOwner(userId, careerId);
                    return careerDataCleanupRepository.deleteOwnedData(userId, careerId)
                            .doOnNext(result -> log.info(
                                    "[CAREER-CLEANUP] ownerHash={} careers={} discovered={} unique={} requested={} deleted={} batches={} maxBatch={} partialFailure={}",
                                    result.ownerHash(), result.careerCount(), result.keysDiscovered(),
                                    result.uniqueKeys(), result.keysRequestedForDeletion(),
                                    result.keysActuallyDeleted(), result.batchCount(), result.maxBatchSize(),
                                    result.partialFailure()));
                })
                .then(Mono.defer(() -> careerRepository.deleteById(userId.toString())));
    }

    @Deprecated
    public Mono<CareerSave> startNewCareer(UUID userId, String worldLeagueId, String worldTeamId,
                                            String difficulty, String gameSpeed) {
        // Redirige al método correcto con teamsPerDivision default de 5.
        // El deprecated 5-param creaba un Career VACÍO (sin userSessionTeamId) — BUG.
        return startCareerUseCase.start(userId, worldLeagueId, worldTeamId, difficulty, gameSpeed, null);
    }

    @Deprecated
    public Mono<CareerSave> startNewCareer(UUID userId, Long leagueId, Long teamId) {
        // Redirige al método String-based con teamsPerDivision default de 5.
        return startCareerUseCase.start(
                userId,
                leagueId.toString(),
                teamId.toString(),
                "NORMAL",
                "NORMAL",
                5
        );
    }
}
