package com.footballmanager.application.service.simulation;

import com.footballmanager.application.service.simulation.detailed.CareerMutationPolicy;
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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * which proves the detailed match engine emits the RED_CARD event when a player receives a
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
 * accepts a deterministic detailed match engine provider.
 */
class V24SecondYellowDisciplineLifecycleIntegrationTest {

    private static final String HOME = "11111111-1111-1111-1111-111111111111";
    private static final String AWAY = "22222222-2222-2222-2222-222222222222";
    private static final String PLAYER_ID = "p-second-yellow";

    // ========== T1: Second yellow produces suspended + red + yellows end-to-end ==========

    /**
     * detailed match engine emits [YELLOW_CARD, YELLOW_CARD, RED_CARD] for the same player
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
        MatchTimeline timeline = new MatchTimeline();
        timeline.addEvent(new DetailedMatchEvent(
                30, DetailedMatchEventType.YELLOW_CARD,
                HOME, PLAYER_ID, "Second Yellow Player",
                null, null, 0.0, "Foul"));
        timeline.addEvent(new DetailedMatchEvent(
                83, DetailedMatchEventType.YELLOW_CARD,
                HOME, PLAYER_ID, "Second Yellow Player",
                null, null, 0.0, "Foul"));
        timeline.addEvent(new DetailedMatchEvent(
                83, DetailedMatchEventType.RED_CARD,
                HOME, PLAYER_ID, "Second Yellow Player",
                null, null, 0.0, "received a red card (second yellow)"));

        DetailedMatchResult deterministicResult = DetailedMatchResult.builder()
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
        MatchTimeline timeline = new MatchTimeline();
        timeline.addEvent(new DetailedMatchEvent(
                45, DetailedMatchEventType.RED_CARD,
                HOME, PLAYER_ID, "Direct Red Player",
                null, null, 0.0, "Violent conduct"));

        DetailedMatchResult deterministicResult = DetailedMatchResult.builder()
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

    private static class DeterministicV24Engine implements DetailedMatchEngineProvider {
        private final DetailedMatchResult result;

        DeterministicV24Engine(DetailedMatchResult result) {
            this.result = result;
        }

        @Override
        public DetailedMatchResult simulate(MatchContext context, long seed) {
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
