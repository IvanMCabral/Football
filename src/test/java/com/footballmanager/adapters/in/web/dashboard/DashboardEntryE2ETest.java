package com.footballmanager.adapters.in.web.dashboard;

import com.footballmanager.AbstractIntegrationTest;
import com.footballmanager.application.service.career.CareerSessionService;
import com.footballmanager.domain.model.entity.*;
import com.footballmanager.domain.ports.out.auth.AuthTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class DashboardEntryE2ETest extends AbstractIntegrationTest {
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    @Autowired CareerSessionService sessions;
    @Autowired AuthTokenService tokens;
    @BeforeEach void clearSessionCache() { sessions.clearCache(); }

    @Test void entryMatchesTheExistingProfileStatusAndStatisticsWithoutLoadingTheWorld() throws Exception {
        var career = new CareerSave(); career.setUserId(OWNER); career.getData().setCareerId("entry-test");
        career.setUserSessionTeamId("team"); career.setTournamentState(new TournamentState());
        var standings = new TeamStandings("team", "Club"); standings.setPlayed(4); standings.setWon(2); standings.setLost(1);
        career.getTournamentState().getStandings().put("team", standings);
        sessions.cacheCareer(career);
        String token = tokens.generateToken(OWNER.toString(), "USER");
        var entry = objectMapper.readTree(read("/dashboard/entry", token));
        assertEquals(objectMapper.readTree(read("/auth/me", token)), entry.get("user"));
        assertEquals(objectMapper.readTree(read("/career/status", token)), entry.get("careerStatus"));
        assertEquals(objectMapper.readTree(read("/dashboard/user-stats", token)), entry.get("userStats"));
        assertFalse(reactiveRedisTemplate.hasKey("world:" + OWNER).block());
        // A caller-supplied owner never overrides the canonical authenticated owner.
        var withForeignQuery = objectMapper.readTree(read("/dashboard/entry?userId=" + UUID.randomUUID(), token));
        assertEquals(OWNER.toString(), withForeignQuery.path("user").path("id").asText());
    }

    @Test void newManagerGetsNoCareerAndZeroStatistics() throws Exception {
        var entry = objectMapper.readTree(read("/dashboard/entry", tokens.generateToken(OWNER.toString(), "USER")));
        assertTrue(entry.path("careerStatus").path("careerId").isNull());
        assertEquals(0, entry.path("userStats").path("matchesPlayed").asInt());
        assertFalse(reactiveRedisTemplate.hasKey("world:" + OWNER).block());
    }

    @Test void anonymousAndInvalidJwtCannotReadEntry() {
        webTestClient.get().uri("/api/v1/dashboard/entry").exchange().expectStatus().isUnauthorized();
        webTestClient.get().uri("/api/v1/dashboard/entry").headers(h -> h.setBearerAuth("invalid"))
                .exchange().expectStatus().isUnauthorized();
    }

    private String read(String path, String token) {
        return webTestClient.get().uri("/api/v1" + path).headers(h -> h.setBearerAuth(token))
                .exchange().expectStatus().isOk().expectBody(String.class).returnResult().getResponseBody();
    }
}
