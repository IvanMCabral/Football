package com.footballmanager.application.engine.round;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registro global de RoundEngines activos.
 */
@Component
public class RoundEngineRegistry {

    private final Map<UUID, RoundEngine> engines = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> matchToRoundMap = new ConcurrentHashMap<>();

    public synchronized void register(UUID roundId, RoundEngine engine) {
        RoundEngine existing = engines.get(roundId);
        if (existing != null && existing.isRunning()) {
            existing.stop();
        }
        removeRoundMappings(roundId);
        engines.put(roundId, engine);
        for (UUID matchId : engine.getMatchIds()) {
            matchToRoundMap.put(matchId, roundId);
        }
    }

    public RoundEngine get(UUID roundId) {
        return engines.get(roundId);
    }

    /** Reads live activity only for the exact owner, career and generation. */
    public RoundEngine getActiveForOwner(UUID ownerId, String careerId, String generation) {
        return engines.values().stream()
            .filter(engine -> engine.belongsTo(ownerId, careerId))
            .filter(engine -> java.util.Objects.equals(generation, engine.getLifecycleGeneration()))
            .filter(RoundEngine::isRunning)
            .filter(engine -> engine.getLatestState().getStatus() !=
                com.footballmanager.application.engine.model.RoundState.RoundStatus.COMPLETED)
            .findFirst().orElse(null);
    }

    public synchronized RoundEngine getByMatchId(UUID matchId) {
        UUID roundId = matchToRoundMap.get(matchId);
        if (roundId != null) {
            return engines.get(roundId);
        }
        return null;
    }

    public synchronized UUID getRoundIdByMatchId(UUID matchId) {
        return matchToRoundMap.get(matchId);
    }

    /** Returns a match's round only after the owning engine proves the actor. */
    public synchronized UUID getOwnedRoundIdByMatchId(UUID matchId, UUID ownerId) {
        UUID roundId = matchToRoundMap.get(matchId);
        if (roundId == null) {
            return null;
        }
        RoundEngine engine = engines.get(roundId);
        return engine != null && engine.belongsTo(ownerId, null) ? roundId : null;
    }

    public synchronized void unregister(UUID roundId) {
        RoundEngine removed = engines.remove(roundId);
        removeRoundMappings(roundId);
        if (removed != null) {
            removed.stop();
        }
    }

    private void removeRoundMappings(UUID roundId) {
        // stop() releases the engine's match collection before persistence can
        // unregister it. The index must therefore be cleaned by round identity.
        matchToRoundMap.entrySet().removeIf(entry -> roundId.equals(entry.getValue()));
    }

    public boolean exists(UUID roundId) {
        return engines.containsKey(roundId);
    }

    public int getActiveCount() {
        return engines.size();
    }

    public int getActiveRoundCount() {
        return engines.size();
    }

    /** Stops only rounds explicitly registered for the requested owner. */
    public synchronized int stopEnginesForOwner(UUID userId, String careerId) {
        int stopped = 0;
        for (Map.Entry<UUID, RoundEngine> entry : engines.entrySet()) {
            if (entry.getValue().belongsTo(userId, careerId) && engines.remove(entry.getKey(), entry.getValue())) {
                try {
                    entry.getValue().stop();
                } catch (Exception ignored) {
                    // cleanup remains scoped even if one engine fails to stop
                }
                removeRoundMappings(entry.getKey());
                stopped++;
            }
        }
        return stopped;
    }

    public synchronized Set<UUID> getAllRoundIds() {
        return Set.copyOf(engines.keySet());
    }

    public synchronized void stopAllEngines() {
        engines.values().forEach(engine -> {
            try {
                engine.stop();
            } catch (Exception ignored) {
            }
        });
        engines.clear();
        matchToRoundMap.clear();
    }
}
