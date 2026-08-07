package com.footballmanager.domain.model.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DurableGenerationSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void runtimeMatchRoundTripRetainsLifecycleGeneration() throws Exception {
        RuntimeMatch original = new RuntimeMatch("match-1", "career-1", "home", "away", 1, "generation-1");

        String json = mapper.writeValueAsString(original);
        RuntimeMatch restored = mapper.readValue(json, RuntimeMatch.class);

        assertEquals("generation-1", restored.getLifecycleGeneration());
        assertTrue(json.contains("\"lifecycleGeneration\":\"generation-1\""));
    }

    @Test
    void matchStateRoundTripRetainsLifecycleGeneration() throws Exception {
        UUID owner = UUID.randomUUID();
        MatchState original = new MatchState(UUID.randomUUID(), owner, "career-1", "generation-1");

        String json = mapper.writeValueAsString(original);
        MatchState restored = mapper.readValue(json, MatchState.class);

        assertEquals("generation-1", restored.getLifecycleGeneration());
        assertEquals(owner.toString(), restored.getUserId());
    }

    @Test
    void publicRuntimeResponseDoesNotExposeLifecycleGeneration() throws Exception {
        RuntimeMatch runtime = new RuntimeMatch("match-1", "career-1", "home", "away", 1, "generation-1");

        String json = mapper.writeValueAsString(
                com.footballmanager.adapters.in.web.game.RuntimeMatchResponse.from(runtime));

        assertTrue(!json.contains("lifecycleGeneration"));
    }
}
