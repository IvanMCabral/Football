package com.footballmanager.application.service.career;

import com.footballmanager.application.engine.round.RoundEngineRegistry;
import com.footballmanager.application.service.match.session.MatchSessionRegistry;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.valueobject.CareerWriteContext;
import com.footballmanager.application.observability.ResetTiming;
import com.footballmanager.domain.model.repository.CareerRepository;
import com.footballmanager.domain.port.in.career.StartCareerUseCase;
import com.footballmanager.domain.port.in.career.ContinueCareerUseCase;
import com.footballmanager.domain.ports.out.career.CareerDataCleanupRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;

/**
 * CareerSessionService - Facade para gestión de sesión Career.
 */
@Service
@Slf4j
public class CareerSessionService {

    private final CareerRepository careerRepository;
    private final StartCareerUseCase startCareerUseCase;
    private final ContinueCareerUseCase continueCareerUseCase;
    private final RoundEngineRegistry roundEngineRegistry;
    private final MatchSessionRegistry matchSessionRegistry;
    private final CareerDataCleanupRepository careerDataCleanupRepository;
    private final CareerLifecycleCoordinator lifecycleCoordinator;

    private final Map<String, CareerSave> careerCache = new ConcurrentHashMap<>();
    private final Map<String, CareerSave> lastPersistedSnapshot = new ConcurrentHashMap<>();
    private final Set<String> dirtyOwners = ConcurrentHashMap.newKeySet();

    @Autowired
    public CareerSessionService(CareerRepository careerRepository,
                                StartCareerUseCase startCareerUseCase,
                                ContinueCareerUseCase continueCareerUseCase,
                                RoundEngineRegistry roundEngineRegistry,
                                MatchSessionRegistry matchSessionRegistry,
                                CareerDataCleanupRepository careerDataCleanupRepository,
                                CareerLifecycleCoordinator lifecycleCoordinator) {
        this.careerRepository = careerRepository;
        this.startCareerUseCase = startCareerUseCase;
        this.continueCareerUseCase = continueCareerUseCase;
        this.roundEngineRegistry = roundEngineRegistry;
        this.matchSessionRegistry = matchSessionRegistry;
        this.careerDataCleanupRepository = careerDataCleanupRepository;
        this.lifecycleCoordinator = lifecycleCoordinator;
    }

    /** Compatibility constructor for isolated unit tests and tooling. */
    public CareerSessionService(CareerRepository careerRepository,
                                StartCareerUseCase startCareerUseCase,
                                ContinueCareerUseCase continueCareerUseCase,
                                RoundEngineRegistry roundEngineRegistry,
                                MatchSessionRegistry matchSessionRegistry,
                                CareerDataCleanupRepository careerDataCleanupRepository) {
        this(careerRepository, startCareerUseCase, continueCareerUseCase, roundEngineRegistry,
                matchSessionRegistry, careerDataCleanupRepository, new CareerLifecycleCoordinator(null));
    }

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
        lastPersistedSnapshot.remove(userId.toString());
        dirtyOwners.remove(userId.toString());
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
        // Reuse the owner-scoped in-process snapshot when the request path has
        // already loaded it. All writes refresh this cache after the lifecycle
        // coordinator accepts them, so this is a read de-duplication only; the
        // Redis career remains the durable authority and reset invalidates it.
        return getCareerFromCache(userId);
    }

    /** Seeds the owner-scoped snapshot immediately after a successful career start. */
    public void cacheCareer(CareerSave career) {
        if (career == null || career.getUserId() == null) {
            return;
        }
        String key = career.getUserId().toString();
        careerCache.put(key, career);
        lastPersistedSnapshot.put(key, career);
        dirtyOwners.remove(key);
    }

    public Mono<CareerSave> getCareer(UUID userId) {
        return continueCareerUseCase.getCareer(userId);
    }

    public Mono<Boolean> careerExists(UUID userId) {
        return continueCareerUseCase.exists(userId);
    }

    public Mono<CareerSave> saveCareer(CareerSave career) {
        if (career == null || career.getUserId() == null || career.getCareerId() == null
                || career.getLifecycleGeneration() == null || career.getLifecycleGeneration().isBlank()) {
            return Mono.error(new IllegalStateException(
                    "career update requires captured lifecycle generation"));
        }
        String key = career.getUserId().toString();
        CareerSave persisted = lastPersistedSnapshot.get(key);
        if (persisted == career && !dirtyOwners.contains(key)) {
            return Mono.just(career);
        }
        CareerWriteContext context = new CareerWriteContext(
                career.getUserId(), career.getCareerId(), career.getLifecycleGeneration());
        return lifecycleCoordinator.serialize(career.getUserId(), careerRepository.saveExistingCareer(context, career))
            .doOnSuccess(saved -> {
                careerCache.put(key, career);
                lastPersistedSnapshot.put(key, career);
                dirtyOwners.remove(key);
            })
            .doOnError(error -> {
                // Log error silently
            })
            .thenReturn(career);
    }

    /** Marks a cached career as changed before a mutating command persists it. */
    public void markDirty(UUID userId) {
        if (userId != null) {
            dirtyOwners.add(userId.toString());
        }
    }

    public Mono<Void> deleteCareer(UUID userId) {
        return deleteCareer(userId, null);
    }

    public Mono<Void> deleteCareer(UUID userId, ResetTiming timing) {
        return lifecycleCoordinator.serializeReset(userId, Mono.defer(() -> {
            String ownerKey = userId.toString();
            CareerSave cachedCareer = careerCache.get(ownerKey);
            invalidateCache(userId);
            Mono<java.util.Optional<CareerSave>> existingCareer;
            if (cachedCareer != null) {
                existingCareer = Mono.just(java.util.Optional.of(cachedCareer));
                if (timing != null) timing.careerLookup(0);
            } else {
                long lookupStarted = System.nanoTime();
                existingCareer = careerRepository.findById(userId.toString())
                        .doFinally(signal -> { if (timing != null) timing.careerLookup(System.nanoTime() - lookupStarted); })
                        .defaultIfEmpty(java.util.Optional.empty());
            }
            return existingCareer
                    .flatMap(existing -> {
                        String careerId = existing.map(CareerSave::getCareerId).orElse(null);
                        long registryStarted = System.nanoTime();
                        roundEngineRegistry.stopEnginesForOwner(userId, careerId);
                        matchSessionRegistry.clearSessionsForOwner(userId, careerId);
                        if (timing != null) timing.registry(System.nanoTime() - registryStarted);
                        long cleanupStarted = System.nanoTime();
                        Mono<com.footballmanager.domain.ports.out.career.CareerDataCleanupResult> cleanup =
                                careerDataCleanupRepository.deleteOwnedData(userId, careerId);
                        if (careerId != null) {
                            cleanup = lifecycleCoordinator.serializeCareer(careerId, cleanup);
                        }
                        return cleanup
                                .doFinally(signal -> { if (timing != null) timing.cleanup(System.nanoTime() - cleanupStarted); })
                                .doOnNext(result -> log.info(
                                        "[CAREER-CLEANUP] ownerHash={} careers={} discovered={} unique={} requested={} deleted={} batches={} maxBatch={} partialFailure={}",
                                        result.ownerHash(), result.careerCount(), result.keysDiscovered(),
                                        result.uniqueKeys(), result.keysRequestedForDeletion(),
                                        result.keysActuallyDeleted(), result.batchCount(), result.maxBatchSize(),
                                        result.partialFailure()))
                                .then();
                    });
        }));
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
