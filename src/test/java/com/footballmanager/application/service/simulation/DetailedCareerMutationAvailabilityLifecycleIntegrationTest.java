package com.footballmanager.application.service.simulation;

import com.footballmanager.application.service.simulation.detailed.DetailedMatchData;
import com.footballmanager.application.service.simulation.detailed.DetailedMatchEngineProvider;
import com.footballmanager.application.service.simulation.detailed.DetailedMatchResult;
import com.footballmanager.application.service.simulation.detailed.DetailedMatchStoragePort;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import com.footballmanager.application.service.simulation.detailed.MatchContext;
import com.footballmanager.application.service.simulation.detailed.DetailedMatchEvent;
import com.footballmanager.application.service.simulation.detailed.DetailedMatchEventType;
import com.footballmanager.application.service.simulation.detailed.MatchTimeline;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.MatchResult;
import com.footballmanager.domain.model.entity.MatchState;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.entity.TournamentState;
import com.footballmanager.domain.model.entity.career.CareerPlayerManager;
import com.footballmanager.domain.model.entity.career.CareerTeamManager;
import com.footballmanager.domain.model.valueobject.MatchFixture;
import com.footballmanager.domain.service.MatchSimulator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * <p>Covers end-to-end multi-round behaviour for suspension, injury, and the
 * manual-select blocking of suspended/injured players. No Mockito, no random
 * seed — fully deterministic. Uses an engine that returns different
 * exercised in a single test.
 *
 * <p>Located in package {@code com.footballmanager.application.service.simulation}
 * to access the package-private 12-arg {@link LeagueSimulator} constructor.
 */
class DetailedCareerMutationAvailabilityLifecycleIntegrationTest {

    private static final String HOME = "11111111-1111-1111-1111-111111111111";
    private static final String AWAY = "22222222-2222-2222-2222-222222222222";

    // ========== T2: Multi-round suspension lifecycle ==========

    /**
     * Player is healthy in round 1, receives a RED_CARD, ends the round
     * suspended with remaining=1. In round 2 the team has a fixture, the
     * player does not participate, and after the round suspension is cleared.
     *
     * <p>This combines two scenarios that exist separately in
     * {@code DetailedCareerMutationIntegrationTest} into a single end-to-end
     * sequence to catch any regression that breaks the round→round transition.
     */
    @Test
    void redCardThenNextRoundServed_suspensionClearedAcrossRounds() {
        String playerId = "p-suspend-multi";
        String matchR1 = "r1-susp";
        String matchR2 = "r2-susp";

        // Round 1 timeline: RED_CARD for player
        MatchTimeline tl1 = new MatchTimeline();
        tl1.addEvent(new DetailedMatchEvent(
                60, DetailedMatchEventType.RED_CARD,
                HOME, playerId, "Suspended Player",
                null, null, 0.0, "second yellow"));

        DetailedMatchResult r1 = baseResult(matchR1, tl1);

        // Round 2 timeline: empty (player is suspended, won't receive events)
        MatchTimeline tl2 = new MatchTimeline();
        DetailedMatchResult r2 = baseResult(matchR2, tl2);

        MultiMatchEngine engine = new MultiMatchEngine();
        engine.put(matchR1, r1);
        engine.put(matchR2, r2);

        LeagueSimulator simulator = new LeagueSimulator(
                new FakeMatchSimulator(), null, false, true, false, new FakeStoragePort(),
                true, false, false, true, false,
                engine);

        CareerSave career = makeCareerWithHealthyPlayer(HOME, AWAY, HOME, AWAY, playerId);

        // Two fixtures: one per round. Both keep the same teams.
        TournamentState ts = new TournamentState();
        ts.setCurrentRound(1);
        ts.setTotalRounds(2);
        ts.getFixtures().add(new MatchFixture(matchR1, HOME, AWAY, 1));
        ts.getFixtures().add(new MatchFixture(matchR2, HOME, AWAY, 2));
        career.setTournamentState(ts);

        // Round 1
        simulator.simulateLeagueRound(career, 1);
        SessionPlayer p = career.getSessionPlayer(playerId);
        assertNotNull(p);
        assertTrue(p.getSuspended(), "After red card: player must be suspended");
        assertEquals(1, p.getSuspensionRemainingMatches(),
                "After red card: suspensionRemainingMatches must be 1");
        assertEquals(1, p.getRedCards(), "redCards must be 1 after the red card");

        // Production reality: between rounds the user re-creates the lineup and
        // would NOT include a suspended player. Simulate that by removing the
        // suspended player from the HOME starting XI for round 2.
        removeFromStartingXI(career, HOME, playerId);

        // Round 2
        simulator.simulateLeagueRound(career, 2);
        assertFalse(p.getSuspended(),
                "After round 2 (no participation): suspension must be cleared");
        assertEquals(0, p.getSuspensionRemainingMatches(),
                "After round 2: suspensionRemainingMatches must be 0");
    }

    // ========== T3: Multi-round injury lifecycle ==========

    /**
     * Player is healthy in round 1, receives an INJURY, ends the round injured
     * with remaining=2. In round 2 (no participation) remaining drops to 1.
     * In round 3 (no participation) injury is cleared.
     */
    @Test
    void injuryThenTwoRecoveryRounds_playerFullyRecovers() {
        String playerId = "p-injury-multi";
        String matchR1 = "r1-inj";
        String matchR2 = "r2-inj";
        String matchR3 = "r3-inj";

        MatchTimeline tl1 = new MatchTimeline();
        tl1.addEvent(new DetailedMatchEvent(
                40, DetailedMatchEventType.INJURY,
                HOME, playerId, "Injured Player",
                null, null, 0.0, "Muscle strain"));

        MultiMatchEngine engine = new MultiMatchEngine();
        engine.put(matchR1, baseResult(matchR1, tl1));
        engine.put(matchR2, baseResult(matchR2, new MatchTimeline()));
        engine.put(matchR3, baseResult(matchR3, new MatchTimeline()));

        LeagueSimulator simulator = new LeagueSimulator(
                new FakeMatchSimulator(), null, false, true, false, new FakeStoragePort(),
                true, true, false, false, false,
                engine);

        CareerSave career = makeCareerWithHealthyPlayer(HOME, AWAY, HOME, AWAY, playerId);

        TournamentState ts = new TournamentState();
        ts.setCurrentRound(1);
        ts.setTotalRounds(3);
        ts.getFixtures().add(new MatchFixture(matchR1, HOME, AWAY, 1));
        ts.getFixtures().add(new MatchFixture(matchR2, HOME, AWAY, 2));
        ts.getFixtures().add(new MatchFixture(matchR3, HOME, AWAY, 3));
        career.setTournamentState(ts);

        // Round 1: INJURY
        simulator.simulateLeagueRound(career, 1);
        SessionPlayer p = career.getSessionPlayer(playerId);
        assertTrue(p.getInjured(), "After INJURY event: injured must be true");
        assertEquals(2, p.getInjuryRemainingMatches(),
                "After INJURY event: injuryRemainingMatches must be 2");
        assertEquals("MATCH_INJURY", p.getInjuryType(),
                "After INJURY event: injuryType must be MATCH_INJURY");

        // Production reality: between rounds the user re-creates the lineup and
        // would NOT include an injured player. Remove from starting XI for round 2+3.
        removeFromStartingXI(career, HOME, playerId);

        // Round 2: no participation → decrement to 1
        simulator.simulateLeagueRound(career, 2);
        assertTrue(p.getInjured(), "Round 2: injured remains true");
        assertEquals(1, p.getInjuryRemainingMatches(),
                "Round 2: injuryRemainingMatches decrements to 1");
        assertEquals("MATCH_INJURY", p.getInjuryType(),
                "Round 2 (partial): injuryType preserved");

        // Round 3: no participation → recover
        simulator.simulateLeagueRound(career, 3);
        assertFalse(p.getInjured(), "Round 3: player must be recovered");
        assertEquals(0, p.getInjuryRemainingMatches(),
                "Round 3: injuryRemainingMatches must be 0");
        assertNull(p.getInjuryType(), "Round 3: injuryType must be cleared");
    }

    // ========== T6: Round loop with mixed events (RED + INJURY + YELLOW) ==========

    /**
     * Single round with three different career-state events on three different
     * players. With discipline + injuries flags active, each player's state
     * must mutate correctly. persist-fatigue and persist-form remain off.
     */
    @Test
    void roundLoopWithMixedEvents_eachPlayerMutatedCorrectly() {
        String redId = "p-mix-red";
        String injId = "p-mix-inj";
        String yelId = "p-mix-yel";
        String matchId = "r1-mix";

        MatchTimeline tl = new MatchTimeline();
        tl.addEvent(new DetailedMatchEvent(
                25, DetailedMatchEventType.RED_CARD,
                HOME, redId, "Mix Red", null, null, 0.0, "violent conduct"));
        tl.addEvent(new DetailedMatchEvent(
                50, DetailedMatchEventType.INJURY,
                HOME, injId, "Mix Inj", null, null, 0.0, "hamstring"));
        tl.addEvent(new DetailedMatchEvent(
                70, DetailedMatchEventType.YELLOW_CARD,
                AWAY, yelId, "Mix Yel", null, null, 0.0, "foul"));

        MultiMatchEngine engine = new MultiMatchEngine();
        engine.put(matchId, baseResult(matchId, tl));

        LeagueSimulator simulator = new LeagueSimulator(
                new FakeMatchSimulator(), null, false, true, false, new FakeStoragePort(),
                true, true, false, true, false,
                engine);

        CareerSave career = makeCareerWithHealthyPlayers(
                HOME, AWAY, HOME, AWAY,
                List.of(redId, injId, yelId));

        TournamentState ts = new TournamentState();
        ts.setCurrentRound(1);
        ts.setTotalRounds(1);
        ts.getFixtures().add(new MatchFixture(matchId, HOME, AWAY, 1));
        career.setTournamentState(ts);

        simulator.simulateLeagueRound(career, 1);

        SessionPlayer red = career.getSessionPlayer(redId);
        assertTrue(red.getSuspended(), "RED_CARD player must be suspended");
        assertEquals(1, red.getSuspensionRemainingMatches());
        assertEquals(1, red.getRedCards());

        SessionPlayer inj = career.getSessionPlayer(injId);
        assertTrue(inj.getInjured(), "INJURY player must be injured");
        assertEquals(2, inj.getInjuryRemainingMatches());
        assertEquals("MATCH_INJURY", inj.getInjuryType());

        SessionPlayer yel = career.getSessionPlayer(yelId);
        assertEquals(1, yel.getYellowCards(),
                "YELLOW_CARD player must have yellowCards=1");
        assertFalse(yel.getSuspended(),
                "YELLOW_CARD player must NOT be suspended after 1 yellow");
    }

    /**
     * Removes the given player from the starting XI of the given team and
     * keeps the XI at 11 by adding a placeholder if needed. Mirrors what the
     * user does between rounds when re-creating the lineup.
     */
    private static void removeFromStartingXI(CareerSave career, String teamId, String playerId) {
        List<String> ids = career.getTeamStarting11().get(teamId);
        if (ids == null) return;
        ids.remove(playerId);
        // If we dropped below 11, add a placeholder to keep the detailed match path valid.
        if (ids.size() < 11) {
            String placeholder = "p_PLACEHOLDER_" + UUID.randomUUID();
            SessionPlayer ph = SessionPlayer.custom(
                    placeholder, 25, "MID", 70, 70, 70, 70, 70, 70,
                    BigDecimal.valueOf(1000));
            ph.setSessionPlayerId(placeholder);
            career.getPlayerManager().addSessionPlayer(ph);
            career.getTeamManager().assignPlayerToSquad(placeholder, teamId);
            ids.add(placeholder);
        }
    }

    // ========== Helpers ==========

    private static DetailedMatchResult baseResult(String matchId, MatchTimeline tl) {
        return DetailedMatchResult.builder()
                .matchId(matchId)
                .homeTeamId(HOME)
                .awayTeamId(AWAY)
                .homeGoals(0).awayGoals(0)
                .homeXg(0.5).awayXg(0.5)
                .homeShots(3).awayShots(3)
                .homePossession(50).awayPossession(50)
                .timeline(tl)
                .summary("Deterministic: " + matchId)
                .build();
    }

    /**
     * Creates a minimal career with a single healthy player in the HOME starting
     * XI. Used by T2/T3 lifecycle tests.
     */
    private static CareerSave makeCareerWithHealthyPlayer(
            String homeTeamId, String awayTeamId,
            String homeStartingTeamId, String awayStartingTeamId,
            String playerId) {
        return makeCareerWithHealthyPlayers(
                homeTeamId, awayTeamId, homeStartingTeamId, awayStartingTeamId,
                List.of(playerId));
    }

    /**
     * Creates a career with the given healthy player ids in the HOME starting
     * XI. Replaces the first 1..N HOME starters with the listed players.
     */
    private static CareerSave makeCareerWithHealthyPlayers(
            String homeTeamId, String awayTeamId,
            String homeStartingTeamId, String awayStartingTeamId,
            List<String> homePlayerIds) {
        CareerSave save = new CareerSave();
        save.getData().setCareerId("test_avail_" + homePlayerIds);
        CareerTeamManager tm = new CareerTeamManager();
        CareerPlayerManager pm = new CareerPlayerManager();

        for (String tid : List.of(homeTeamId, awayTeamId)) {
            UUID uuid = UUID.fromString(tid);
            SessionTeam team = SessionTeam.fromRealTeam(
                    uuid, "world_" + tid, "Team " + tid,
                    "Country", BigDecimal.ZERO, "4-3-3", null);
            team.setSessionTeamId(tid);
            tm.addSessionTeam(team);
        }

        // Add target players (each as healthy SessionPlayer in HOME)
        for (String pid : homePlayerIds) {
            SessionPlayer p = SessionPlayer.custom(
                    pid, 25, "MID", 70, 70, 70, 70, 70, 70,
                    BigDecimal.valueOf(1000));
            p.setSessionPlayerId(pid);
            pm.addSessionPlayer(p);
            tm.assignPlayerToSquad(pid, homeTeamId);
        }

        // HOME starting XI: target players first, then fill with generic placeholders
        List<String> homeStarterIds = new ArrayList<>(homePlayerIds);
        int i = 0;
        while (homeStarterIds.size() < 11) {
            String pid = "p_HOME_" + i;
            if (!homeStarterIds.contains(pid)) {
                SessionPlayer p = SessionPlayer.custom(
                        pid, 25, "MID", 70, 70, 70, 70, 70, 70,
                        BigDecimal.valueOf(1000));
                p.setSessionPlayerId(pid);
                pm.addSessionPlayer(p);
                tm.assignPlayerToSquad(pid, homeTeamId);
                homeStarterIds.add(pid);
            }
            i++;
        }

        // AWAY starting XI: 11 generic placeholders
        List<String> awayStarterIds = new ArrayList<>();
        for (int j = 0; j < 11; j++) {
            String pid = "p_AWAY_" + j;
            SessionPlayer p = SessionPlayer.custom(
                    pid, 25, "MID", 70, 70, 70, 70, 70, 70,
                    BigDecimal.valueOf(1000));
            p.setSessionPlayerId(pid);
            pm.addSessionPlayer(p);
            tm.assignPlayerToSquad(pid, awayTeamId);
            awayStarterIds.add(pid);
        }

        save.setTeamManager(tm);
        save.setPlayerManager(pm);
        save.getTeamStarting11().put(homeStartingTeamId, homeStarterIds);
        save.getTeamStarting11().put(awayStartingTeamId, awayStarterIds);
        save.setTournamentState(new TournamentState());
        return save;
    }

    // ========== Fakes ==========

    private static class MultiMatchEngine implements DetailedMatchEngineProvider {
        private final Map<String, DetailedMatchResult> byMatchId = new HashMap<>();

        void put(String matchId, DetailedMatchResult result) {
            byMatchId.put(matchId, result);
        }

        @Override
        public DetailedMatchResult simulate(MatchContext context, long seed) {
            DetailedMatchResult r = byMatchId.get(context.matchId());
            if (r == null) {
                throw new IllegalStateException("No result registered for matchId " + context.matchId());
            }
            return r;
        }
    }

    private static class FakeMatchSimulator implements MatchSimulator {
        @Override
        public MatchState simulateReal(MatchState state, int toMinute) {
            return state;
        }

        @Override
        public MatchResult simulateQuick(String homeTeamId, String awayTeamId, int homeOvr, int awayOvr) {
            return new MatchResult(0, 0);
        }
    }

    private static class FakeStoragePort implements DetailedMatchStoragePort {
        @Override
        public Mono<Void> save(String careerId, DetailedMatchData detail) { return Mono.empty(); }

        @Override
        public Mono<java.util.Optional<DetailedMatchData>> findByMatchId(String careerId, String matchId) { return Mono.just(java.util.Optional.empty()); }

        @Override
        public Flux<DetailedMatchData> findByCareerId(String careerId) { return Flux.empty(); }

        @Override
        public Mono<Void> deleteByCareerId(String careerId) { return Mono.empty(); }
    }
}
