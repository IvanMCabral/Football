package com.footballmanager.application.service.simulation.detailed;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.footballmanager.domain.model.valueobject.TeamStyle;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link MatchContext}.
 *
 * <p>Verifies that the {@code @JsonCreator} annotation on the 14-arg
 * constructor (added so {@code BaselineState} can persist the initial
 * context in Redis) does not break the existing constructors and that
 * Jackson can serialize/deserialize the full object graph including
 * nested {@link SessionPlayer} and {@link SessionTeam} entities.
 */
class MatchContextSerializationTest {

    @Test
    void detailedMatchContext_roundTripsThroughJson_preservesAllFields() throws Exception {
        // Build a minimal-but-realistic MatchContext (mirrors LiveSessionTest.buildContext()).
        SessionTeam homeTeam = SessionTeam.custom("home-team-id", "Home FC", "ARG",
                BigDecimal.valueOf(1_000_000L), "4-3-3");
        SessionTeam awayTeam = SessionTeam.custom("away-team-id", "Away FC", "BRA",
                BigDecimal.valueOf(1_000_000L), "4-4-2");

        List<SessionPlayer> homeStarting = makePlayers("home-team-id", "starter", 11);
        List<SessionPlayer> homeBench = makePlayers("home-team-id", "bench", 5);
        List<SessionPlayer> awayStarting = makePlayers("away-team-id", "starter", 11);
        List<SessionPlayer> awayBench = makePlayers("away-team-id", "bench", 5);

        MatchContext original = new MatchContext(
                "match-serialize-1", "home-team-id", "away-team-id",
                homeTeam, awayTeam,
                homeStarting, awayStarting, homeBench, awayBench,
                "4-3-3", "4-4-2",
                TeamStyle.BALANCED, TeamStyle.BALANCED);

        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();

        String json = mapper.writeValueAsString(original);
        MatchContext deserialized = mapper.readValue(json, MatchContext.class);

        assertEquals("match-serialize-1", deserialized.matchId());
        assertEquals("home-team-id", deserialized.homeTeamId());
        assertEquals("away-team-id", deserialized.awayTeamId());
        assertEquals(11, deserialized.homeStartingPlayers().size());
        assertEquals(11, deserialized.awayStartingPlayers().size());
        assertEquals(5, deserialized.homeBenchPlayers().size());
        assertEquals(5, deserialized.awayBenchPlayers().size());
        assertEquals("4-3-3", deserialized.homeFormation());
        assertEquals("4-4-2", deserialized.awayFormation());
        assertEquals(TeamStyle.BALANCED, deserialized.homeStyle());
        assertEquals(TeamStyle.BALANCED, deserialized.awayStyle());
        assertNotNull(deserialized.homeTeam());
        assertEquals("Home FC", deserialized.homeTeam().getName());
    }

    @Test
    void detailedMatchContext_roundTripsWithManualSubs_preservesSubList() throws Exception {
        SessionTeam homeTeam = SessionTeam.custom("home-id", "Home", "ARG",
                BigDecimal.valueOf(1_000_000L), "4-3-3");
        SessionTeam awayTeam = SessionTeam.custom("away-id", "Away", "BRA",
                BigDecimal.valueOf(1_000_000L), "4-4-2");
        List<SessionPlayer> homeStarting = makePlayers("home-id", "starter", 11);
        List<SessionPlayer> homeBench = makePlayers("home-id", "bench", 5);
        List<SessionPlayer> awayStarting = makePlayers("away-id", "starter", 11);
        List<SessionPlayer> awayBench = makePlayers("away-id", "bench", 5);

        MatchContext original = new MatchContext(
                "match-subs-1", "home-id", "away-id",
                homeTeam, awayTeam,
                homeStarting, awayStarting, homeBench, awayBench,
                "4-3-3", "4-4-2",
                TeamStyle.BALANCED, TeamStyle.BALANCED);

        // Apply one manual sub (F2.5 deferred design)
        MatchContext withSub = original.withManualSubstitution(
                "home-id", "home-id-starter-3", "home-id-bench-0", 60);

        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();

        String json = mapper.writeValueAsString(withSub);
        MatchContext deserialized = mapper.readValue(json, MatchContext.class);

        assertEquals(1, deserialized.manualSubstitutions().size());
        MatchContext.ScheduledSub sub = deserialized.manualSubstitutions().get(0);
        assertEquals("home-id", sub.teamId());
        assertEquals("home-id-starter-3", sub.playerOffId());
        assertEquals("home-id-bench-0", sub.playerOnId());
        assertEquals(60, sub.effectiveMinute());
    }

    @Test
    void detailedMatchContext_legacy13ArgConstructor_stillWorks() {
        // Sanity check: existing 13-arg constructor still compiles and runs
        // (used by 32+ test fixtures and the production wire).
        SessionTeam homeTeam = SessionTeam.custom("home-id", "Home", "ARG",
                BigDecimal.valueOf(1_000_000L), "4-3-3");
        SessionTeam awayTeam = SessionTeam.custom("away-id", "Away", "BRA",
                BigDecimal.valueOf(1_000_000L), "4-4-2");
        List<SessionPlayer> homeStarting = makePlayers("home-id", "starter", 11);
        List<SessionPlayer> homeBench = makePlayers("home-id", "bench", 5);
        List<SessionPlayer> awayStarting = makePlayers("away-id", "starter", 11);
        List<SessionPlayer> awayBench = makePlayers("away-id", "bench", 5);

        MatchContext ctx = new MatchContext(
                "legacy-1", "home-id", "away-id",
                homeTeam, awayTeam,
                homeStarting, awayStarting, homeBench, awayBench,
                "4-3-3", "4-4-2",
                TeamStyle.BALANCED, TeamStyle.BALANCED);

        assertEquals(0, ctx.manualSubstitutions().size());
    }

    /**
     * Mirrors {@code LiveSessionTest.makePlayers(teamId, suffix, count)}
     * so the test data shape matches what the production code uses.
     */
    private List<SessionPlayer> makePlayers(String teamId, String suffix, int count) {
        List<SessionPlayer> players = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            SessionPlayer p = new SessionPlayer();
            p.setSessionPlayerId(teamId + "-" + suffix + "-" + i);
            p.setWorldPlayerId(teamId + "-" + suffix + "-wp-" + i);
            p.setName(suffix.substring(0, 1).toUpperCase() + suffix.substring(1) + " " + i);
            p.setPosition(suffix.equals("starter") ? "MID" : "BENCH");
            p.setAge(25);
            int overall = suffix.equals("starter") ? 70 : 80; // bench stronger for F2 contract tests
            p.setAttack(overall);
            p.setDefense(overall);
            p.setTechnique(overall);
            p.setSpeed(overall);
            p.setStamina(overall);
            p.setMentality(overall);
            p.setMarketValue(BigDecimal.valueOf(1_000_000L));
            p.setEnergy(100);
            p.setForm(50);
            players.add(p);
        }
        return players;
    }
}
