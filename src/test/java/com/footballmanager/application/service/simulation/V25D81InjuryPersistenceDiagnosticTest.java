package com.footballmanager.application.service.simulation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.footballmanager.application.service.career.CareerSessionService;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.career.CareerPlayerManager;
import com.footballmanager.domain.model.entity.career.CareerTeamManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * F0 investigation shows the in-memory state IS mutated and orchestrator's
 * saveCareer IS called. This test verifies the actual save→load roundtrip
 * for an injured player to determine whether the bug is real.
 *
 * <p>Test approach: create a minimal CareerSave, mutate a player to
 * injured=true, serialize via the real ObjectMapper, deserialize, and
 * verify the injured flag survives. Uses real Redis to verify the
 * persistence layer works as documented.
 *
 * <p>Run with DB_PASSWORD and REDIS_PASSWORD loaded from the local `.env`.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.flyway.enabled=false",
        "spring.data.redis.database=15"
    }
)
@ActiveProfiles("test")
class V25D81InjuryPersistenceDiagnosticTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CareerSessionService careerSessionService;

    @Autowired
    private ReactiveRedisTemplate<String, String> redisTemplate;

    @Test
    void saveLoadRoundtrip_preservesInjuredFlag() throws Exception {
        UUID userId = UUID.randomUUID();
        String redisKey = "career:" + userId;

        // Build minimal career with 1 player
        CareerSave save = new CareerSave();
        save.setUserId(userId);
        save.getData().setCareerId("test-injury-" + userId);

        CareerTeamManager tm = new CareerTeamManager();
        CareerPlayerManager pm = new CareerPlayerManager();
        save.setTeamManager(tm);
        save.setPlayerManager(pm);

        String playerId = "injured-p1";
        SessionPlayer p = SessionPlayer.custom(
                playerId, 25, "MID", 70, 70, 70, 70, 70, 70,
                java.math.BigDecimal.valueOf(1000));
        p.setSessionPlayerId(playerId);
        pm.addSessionPlayer(p);

        // Save via service
        careerSessionService.saveCareer(save).block();

        // Clear in-memory cache
        careerSessionService.clearCache();

        // Reload
        CareerSave loaded = careerSessionService.continueCareer(userId).block();
        assertNotNull(loaded, "Career must reload from Redis");
        SessionPlayer loadedP = loaded.getSessionPlayer(playerId);
        assertNotNull(loadedP, "Player must exist in reloaded career");
        assertFalse(loadedP.getInjured(), "Player must NOT be injured before mutation");

        // Mutate: set injured
        loadedP.setInjured(true);
        loadedP.setInjuryType("MATCH_INJURY");
        loadedP.setInjuryRemainingMatches(2);

        // Save again
        careerSessionService.saveCareer(loaded).block();

        // Verify JSON in Redis directly
        String json = redisTemplate.opsForValue().get(redisKey).block();
        assertNotNull(json, "JSON must exist in Redis");
        assertTrue(json.contains("\"injured\":true"),
                "Redis JSON must contain 'injured:true' (raw inspection)");
        assertTrue(json.contains("MATCH_INJURY"),
                "Redis JSON must contain 'MATCH_INJURY' (raw inspection)");

        // Clear cache and reload fresh
        careerSessionService.clearCache();
        CareerSave reloaded = careerSessionService.continueCareer(userId).block();
        assertNotNull(reloaded, "Career must reload from Redis after mutation");
        SessionPlayer reloadedP = reloaded.getSessionPlayer(playerId);
        assertNotNull(reloadedP, "Player must exist in reloaded career after mutation");
        assertTrue(reloadedP.getInjured(),
                "SessionPlayer.injured MUST be true after save→load roundtrip");
        assertEquals("MATCH_INJURY", reloadedP.getInjuryType());
        assertEquals(2, reloadedP.getInjuryRemainingMatches());
    }
}
