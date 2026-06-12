package com.footballmanager.application.service.simulation;

import com.footballmanager.application.service.simulation.v24.V24CareerMutationPolicy;
import com.footballmanager.application.service.simulation.v24.V24DetailedMatchData;
import com.footballmanager.application.service.simulation.v24.V24DetailedMatchEngineProvider;
import com.footballmanager.application.service.simulation.v24.V24DetailedMatchResult;
import com.footballmanager.application.service.simulation.v24.V24DetailedMatchStoragePort;
import com.footballmanager.application.service.simulation.v24.V24MatchContext;
import com.footballmanager.application.service.simulation.v24.V24MatchEvent;
import com.footballmanager.application.service.simulation.v24.V24MatchEventType;
import com.footballmanager.application.service.simulation.v24.V24MatchTimeline;
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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V24D6R T1: End-to-end coverage for the second-yellow → red card discipline path.
 *
 * <p>Companion to {@link com.footballmanager.application.service.simulation.v24.V24DetailedMatchEngineSecondYellowTest},
 * which proves the V24 engine emits the RED_CARD event when a player receives a
 * second yellow in the same match. This test proves the discipline mutation
 * applier correctly consumes those engine events and mutates SessionPlayer with
 * the expected discipline state (suspended=true, remaining=1, redCards=1,
 * yellowCards=2).
 *
 * <p>No Mockito, no random seed — fully deterministic. Uses a
 * {@link DeterministicV24Engine} that returns a controlled timeline.
 *
 * <p>Located in package {@code com.footballmanager.application.service.simulation}
 * to access the package-private 12-arg {@link LeagueSimulator} constructor that
 * accepts a deterministic V24 engine provider.
 */
class V24SecondYellowDisciplineLifecycleIntegrationTest {

    private static final String HOME = "11111111-1111-1111-1111-111111111111";
    private static final String AWAY = "22222222-2222-2222-2222-222222222222";
    private static final String PLAYER_ID = "p-second-yellow";

    // ========== T1: Second yellow produces suspended + red + yellows end-to-end ==========

    /**
     * V24 engine emits [YELLOW_CARD, YELLOW_CARD, RED_CARD] for the same player
     * in the same match (this is the V24D6Q fix in {@code V24DetailedMatchEngine}).
     * The discipline applier must consume those events and produce:
     * - yellowCards = 2 (two YELLOW_CARD events)
     * - redCards = 1 (one RED_CARD event)
     * - suspended = true
     * - suspensionRemainingMatches = 1
     *
     * <p>Active flags: mutateCareerState=true, persist-discipline=true,
     * persist-injuries=false, persist-fatigue=false, persist-form=false.
     */
    @Test
    void secondYellowInSameMatch_appliesBothYellowsAndRed_suspendsPlayer() {
        V24MatchTimeline timeline = new V24MatchTimeline();
        timeline.addEvent(new V24MatchEvent(
                30, V24MatchEventType.YELLOW_CARD,
                HOME, PLAYER_ID, "Second Yellow Player",
                null, null, 0.0, "Foul"));
        timeline.addEvent(new V24MatchEvent(
                83, V24MatchEventType.YELLOW_CARD,
                HOME, PLAYER_ID, "Second Yellow Player",
                null, null, 0.0, "Foul"));
        timeline.addEvent(new V24MatchEvent(
                83, V24MatchEventType.RED_CARD,
                HOME, PLAYER_ID, "Second Yellow Player",
                null, null, 0.0, "received a red card (second yellow)"));

        V24DetailedMatchResult deterministicResult = V24DetailedMatchResult.builder()
                .matchId("sy1")
                .homeTeamId(HOME)
                .awayTeamId(AWAY)
                .homeGoals(0).awayGoals(1)
                .homeXg(0.4).awayXg(1.2)
                .homeShots(2).awayShots(6)
                .homePossession(40).awayPossession(60)
                .timeline(timeline)
                .summary("Deterministic: second yellow + red")
                .build();

        LeagueSimulator simulator = new LeagueSimulator(
                new FakeMatchSimulator(), null, false, true, false, new FakeStoragePort(),
                /* mutateCareerState */ true,
                /* persistInjuries  */ false,
                /* persistFatigue   */ false,
                /* persistDiscipline*/ true,
                /* persistForm      */ false,
                new DeterministicV24Engine(deterministicResult));

        CareerSave career = makeCareerWithSinglePlayer(HOME, AWAY, HOME, AWAY, PLAYER_ID);

        career.setTournamentState(makeTournamentState(
                new MatchFixture("sy1", HOME, AWAY, 1)));

        simulator.simulateLeagueRound(career, 1);

        SessionPlayer p = career.getSessionPlayer(PLAYER_ID);
        assertNotNull(p, "Player must exist after the round");
        assertTrue(p.getSuspended(), "Second-yellow player must be suspended after the round");
        assertEquals(1, p.getSuspensionRemainingMatches(),
                "suspensionRemainingMatches must be 1 (initial suspension for direct red)");
        assertEquals(1, p.getRedCards(),
                "redCards must be 1 (one RED_CARD event consumed)");
        assertEquals(2, p.getYellowCards(),
                "yellowCards must be 2 (two YELLOW_CARD events consumed)");
    }

    /**
     * Companion to the first test: a single direct RED_CARD (no second yellow
     * involved) must also produce suspended=true, remaining=1, redCards=1,
     * yellowCards=0. Confirms the applier does not double-count when only a
     * RED_CARD event is present.
     */
    @Test
    void directRedCardInMatch_appliesRed_suspendsPlayer_doesNotCountYellows() {
        V24MatchTimeline timeline = new V24MatchTimeline();
        timeline.addEvent(new V24MatchEvent(
                45, V24MatchEventType.RED_CARD,
                HOME, PLAYER_ID, "Direct Red Player",
                null, null, 0.0, "Violent conduct"));

        V24DetailedMatchResult deterministicResult = V24DetailedMatchResult.builder()
                .matchId("dr1")
                .homeTeamId(HOME)
                .awayTeamId(AWAY)
                .homeGoals(0).awayGoals(0)
                .homeXg(0.5).awayXg(0.5)
                .homeShots(3).awayShots(3)
                .homePossession(50).awayPossession(50)
                .timeline(timeline)
                .summary("Deterministic: direct red card")
                .build();

        LeagueSimulator simulator = new LeagueSimulator(
                new FakeMatchSimulator(), null, false, true, false, new FakeStoragePort(),
                true, false, false, true, false,
                new DeterministicV24Engine(deterministicResult));

        CareerSave career = makeCareerWithSinglePlayer(HOME, AWAY, HOME, AWAY, PLAYER_ID);

        career.setTournamentState(makeTournamentState(
                new MatchFixture("dr1", HOME, AWAY, 1)));

        simulator.simulateLeagueRound(career, 1);

        SessionPlayer p = career.getSessionPlayer(PLAYER_ID);
        assertNotNull(p);
        assertTrue(p.getSuspended());
        assertEquals(1, p.getSuspensionRemainingMatches());
        assertEquals(1, p.getRedCards());
        assertEquals(0, p.getYellowCards(),
                "Direct red card must NOT count any yellows");
    }

    // ========== Helpers ==========

    /**
     * Creates a minimal career: two SessionTeams (HOME, AWAY), HOME starting XI
     * includes PLAYER_ID. Player is healthy pre-round (no suspended, no injured).
     */
    private static CareerSave makeCareerWithSinglePlayer(
            String homeTeamId, String awayTeamId,
            String homeStartingTeamId, String awayStartingTeamId,
            String playerId) {
        CareerSave save = new CareerSave();
        save.getData().setCareerId("test_sy_" + playerId);
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

        // Target player
        SessionPlayer target = SessionPlayer.custom(
                playerId, 25, "MID", 70, 70, 70, 70, 70, 70,
                BigDecimal.valueOf(1000));
        target.setSessionPlayerId(playerId);
        pm.addSessionPlayer(target);
        tm.assignPlayerToSquad(playerId, homeTeamId);

        // HOME starting XI: target + 10 generic placeholders
        List<String> homeStarterIds = new ArrayList<>();
        homeStarterIds.add(playerId);
        for (int i = 0; i < 10; i++) {
            String pid = "p_HOME_" + i;
            SessionPlayer p = SessionPlayer.custom(
                    pid, 25, "MID", 70, 70, 70, 70, 70, 70,
                    BigDecimal.valueOf(1000));
            p.setSessionPlayerId(pid);
            pm.addSessionPlayer(p);
            tm.assignPlayerToSquad(pid, homeTeamId);
            homeStarterIds.add(pid);
        }

        // AWAY starting XI: 11 generic placeholders
        List<String> awayStarterIds = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            String pid = "p_AWAY_" + i;
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

    private static TournamentState makeTournamentState(MatchFixture... fixtures) {
        TournamentState ts = new TournamentState();
        ts.setCurrentRound(1);
        ts.setTotalRounds(5);
        for (MatchFixture f : fixtures) {
            ts.getFixtures().add(f);
        }
        return ts;
    }

    // ========== Fakes (no Mockito) ==========

    private static class DeterministicV24Engine implements V24DetailedMatchEngineProvider {
        private final V24DetailedMatchResult result;

        DeterministicV24Engine(V24DetailedMatchResult result) {
            this.result = result;
        }

        @Override
        public V24DetailedMatchResult simulate(V24MatchContext context, long seed) {
            return result;
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

    private static class FakeStoragePort implements V24DetailedMatchStoragePort {
        @Override
        public void save(String careerId, V24DetailedMatchData detail) {
            // no-op
        }

        @Override
        public java.util.Optional<V24DetailedMatchData> findByMatchId(String careerId, String matchId) {
            return java.util.Optional.empty();
        }

        @Override
        public List<V24DetailedMatchData> findByCareerId(String careerId) {
            return List.of();
        }

        @Override
        public void deleteByCareerId(String careerId) {
            // no-op
        }
    }
}
