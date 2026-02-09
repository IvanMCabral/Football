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

    public void register(UUID roundId, RoundEngine engine) {
        RoundEngine existing = engines.get(roundId);
        if (existing != null && existing.isRunning()) {
            existing.stop();
        }
        engines.put(roundId, engine);
        for (UUID matchId : engine.getMatchIds()) {
            matchToRoundMap.put(matchId, roundId);
        }
    }

    public RoundEngine get(UUID roundId) {
        return engines.get(roundId);
    }

    public RoundEngine getByMatchId(UUID matchId) {
        UUID roundId = matchToRoundMap.get(matchId);
        if (roundId != null) {
            return engines.get(roundId);
        }
        return null;
    }

    public UUID getRoundIdByMatchId(UUID matchId) {
        return matchToRoundMap.get(matchId);
    }

    public void unregister(UUID roundId) {
        RoundEngine removed = engines.remove(roundId);
        if (removed != null) {
            for (UUID matchId : removed.getMatchIds()) {
                matchToRoundMap.remove(matchId);
            }
            removed.stop();
        }
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

    public Set<UUID> getAllRoundIds() {
        return engines.keySet();
    }

    public void stopAllEngines() {
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
