package com.footballmanager.application.service.simulation;

import com.footballmanager.application.service.simulation.v24.V24DetailedMatchData;
import com.footballmanager.application.service.simulation.v24.V24DetailedMatchResult;
import com.footballmanager.application.service.simulation.v24.V24DetailedMatchStoragePort;
import com.footballmanager.application.service.simulation.v24.V24MatchEvent;
import com.footballmanager.application.service.simulation.v24.V24MatchEventType;
import com.footballmanager.application.service.simulation.v24.V24MatchTimeline;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.entity.TournamentState;
import com.footballmanager.domain.model.entity.career.CareerPlayerManager;
import com.footballmanager.domain.model.entity.career.CareerTeamManager;
import com.footballmanager.domain.model.valueobject.MatchFixture;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V24D6R-hotfix: Live-path career mutation coverage.
 *
 * <p>Before the V24D6R-hotfix, {@code LeagueSimulator.persistV24DetailForLiveMatch}
 * only saved V24 detail for stats and never mutated SessionPlayer. The smoke
 * test on the live UI/SSE flow exposed the gap: a player who got INJURY in a
 * match was still selectable in the next round's lineup because
 * {@code SessionPlayer.injured} remained {@code false}.
 *
 * <p>These tests exercise the live path directly via
 * {@code persistV24DetailForLiveMatch(career, v24Result, ...)} with active
 * mutation flags and verify the in-memory {@code CareerSave} is mutated. The
 * orchestrator's existing {@code careerSessionService.saveCareer} at end of
 * round persists the same instance, so subsequent squad/lineup reads see the
 * mutations.
 *
 * <p>No Mockito, no random seed — fully deterministic. The fake storage port
 * records saves without touching Redis.
 */
class V24LivePathCareerMutationIntegrationTest {

    private static final String HOME = "11111111-1111-1111-1111-111111111111";
    private static final String AWAY = "22222222-2222-2222-2222-222222222222";

    // ========== T1: INJURY event mutates SessionPlayer in live path ==========

    @Test
    void livePath_injuryEvent_appliesSessionPlayerInjuryAndSavesDetail() {
        String playerId = "p-live-injured";

        V24MatchTimeline timeline = new V24MatchTimeline();
        timeline.addEvent(new V24MatchEvent(
                82, V24MatchEventType.INJURY,
                HOME, playerId, "Live Injured Player",
                null, null, 0.0, "Player 2 MAD was injured"));

        V24DetailedMatchResult v24Result = baseResult("md-inj-1", timeline, HOME, AWAY);
        RecordingStoragePort storage = new RecordingStoragePort();

        LeagueSimulator simulator = new LeagueSimulator(
                new FakeMatchSimulator(), null, false, true, true, storage,
                /* mutateCareerState */ true,
                /* persistInjuries  */ true,
                /* persistFatigue   */ false,
                /* persistDiscipline*/ false,
                /* persistForm      */ false);

        CareerSave career = makeCareerWithHealthyPlayer(HOME, AWAY, HOME, AWAY, playerId);

        simulator.persistV24DetailForLiveMatch(
                career, v24Result, HOME, AWAY, 1, 0);

        // Detail was saved
        assertEquals(1, storage.saveCount, "V24 detail must be saved");
        assertNotNull(storage.lastDetail, "V24 detail must be saved");

        // SessionPlayer was mutated in memory
        SessionPlayer p = career.getSessionPlayer(playerId);
        assertNotNull(p, "Player must exist in the career");
        assertTrue(p.getInjured(),
                "SessionPlayer.injured must be true after INJURY event in live path");
        assertEquals(2, p.getInjuryRemainingMatches(),
                "injuryRemainingMatches must be 2 (DEFAULT_INJURY_DURATION_MATCHES)");
        assertEquals("MATCH_INJURY", p.getInjuryType(),
                "injuryType must be MATCH_INJURY");
    }

    // ========== T2: RED_CARD event suspends SessionPlayer in live path ==========

    @Test
    void livePath_redCardEvent_appliesSuspensionAndSavesDetail() {
        String playerId = "p-live-red";

        V24MatchTimeline timeline = new V24MatchTimeline();
        timeline.addEvent(new V24MatchEvent(
                60, V24MatchEventType.RED_CARD,
                HOME, playerId, "Live Red Player",
                null, null, 0.0, "second yellow"));

        V24DetailedMatchResult v24Result = baseResult("md-red-1", timeline, HOME, AWAY);
        RecordingStoragePort storage = new RecordingStoragePort();

        LeagueSimulator simulator = new LeagueSimulator(
                new FakeMatchSimulator(), null, false, true, true, storage,
                true, false, false, true, false);

        CareerSave career = makeCareerWithHealthyPlayer(HOME, AWAY, HOME, AWAY, playerId);

        simulator.persistV24DetailForLiveMatch(
                career, v24Result, HOME, AWAY, 1, 0);

        assertEquals(1, storage.saveCount, "V24 detail must be saved");

        SessionPlayer p = career.getSessionPlayer(playerId);
        assertNotNull(p);
        assertTrue(p.getSuspended(), "SessionPlayer.suspended must be true after RED_CARD");
        assertEquals(1, p.getSuspensionRemainingMatches(),
                "suspensionRemainingMatches must be 1");
        assertEquals(1, p.getRedCards(), "redCards must increment to 1");
        assertEquals(0, p.getYellowCards(), "no yellows for direct red");
    }

    // ========== T3: Flags OFF → detail saved, no SessionPlayer mutation ==========

    @Test
    void livePath_flagsFalse_persistsDetailButDoesNotMutateCareer() {
        String playerId = "p-live-readonly";

        V24MatchTimeline timeline = new V24MatchTimeline();
        timeline.addEvent(new V24MatchEvent(
                50, V24MatchEventType.INJURY,
                HOME, playerId, "Read-Only Player",
                null, null, 0.0, "injury"));
        timeline.addEvent(new V24MatchEvent(
                70, V24MatchEventType.RED_CARD,
                HOME, playerId, "Read-Only Player",
                null, null, 0.0, "red"));

        V24DetailedMatchResult v24Result = baseResult("md-ro-1", timeline, HOME, AWAY);
        RecordingStoragePort storage = new RecordingStoragePort();

        // All mutation flags OFF — read-only mode.
        LeagueSimulator simulator = new LeagueSimulator(
                new FakeMatchSimulator(), null, false, true, true, storage,
                /* mutateCareerState */ false,
                /* persistInjuries  */ false,
                /* persistFatigue   */ false,
                /* persistDiscipline*/ false,
                /* persistForm      */ false);

        CareerSave career = makeCareerWithHealthyPlayer(HOME, AWAY, HOME, AWAY, playerId);

        simulator.persistV24DetailForLiveMatch(
                career, v24Result, HOME, AWAY, 1, 0);

        // Detail IS saved (read-only mode still records for stats)
        assertEquals(1, storage.saveCount, "V24 detail must still be saved in read-only mode");
        // But SessionPlayer is NOT mutated
        SessionPlayer p = career.getSessionPlayer(playerId);
        assertFalse(p.getInjured(), "injured must remain false when mutate-career-state=false");
        assertEquals(0, p.getInjuryRemainingMatches());
        assertFalse(p.getSuspended());
        assertEquals(0, p.getRedCards());
    }

    // ========== T4: Second-yellow YELLOW+RED in same match → suspends ==========

    @Test
    void livePath_secondYellowTimeline_yellowPlusRed_appliesSuspension() {
        String playerId = "p-live-2y";

        V24MatchTimeline timeline = new V24MatchTimeline();
        timeline.addEvent(new V24MatchEvent(
                30, V24MatchEventType.YELLOW_CARD,
                HOME, playerId, "Live 2Y Player",
                null, null, 0.0, "foul"));
        timeline.addEvent(new V24MatchEvent(
                83, V24MatchEventType.YELLOW_CARD,
                HOME, playerId, "Live 2Y Player",
                null, null, 0.0, "foul"));
        timeline.addEvent(new V24MatchEvent(
                83, V24MatchEventType.RED_CARD,
                HOME, playerId, "Live 2Y Player",
                null, null, 0.0, "second yellow → red"));

        V24DetailedMatchResult v24Result = baseResult("md-2y-1", timeline, HOME, AWAY);
        RecordingStoragePort storage = new RecordingStoragePort();

        LeagueSimulator simulator = new LeagueSimulator(
                new FakeMatchSimulator(), null, false, true, true, storage,
                true, false, false, true, false);

        CareerSave career = makeCareerWithHealthyPlayer(HOME, AWAY, HOME, AWAY, playerId);

        simulator.persistV24DetailForLiveMatch(
                career, v24Result, HOME, AWAY, 0, 1);

        assertEquals(1, storage.saveCount);

        SessionPlayer p = career.getSessionPlayer(playerId);
        assertNotNull(p);
        assertTrue(p.getSuspended(),
                "Player with second-yellow red must be suspended in live path");
        assertEquals(1, p.getSuspensionRemainingMatches());
        assertEquals(1, p.getRedCards());
        assertEquals(2, p.getYellowCards(),
                "Two YELLOW_CARD events must increment yellowCards to 2");
    }

    // ========== T5: persist-detail OFF → skip detail AND mutation ==========

    @Test
    void livePath_persistDetailFalse_skipsBothDetailAndMutation() {
        String playerId = "p-live-nodetail";

        V24MatchTimeline timeline = new V24MatchTimeline();
        timeline.addEvent(new V24MatchEvent(
                50, V24MatchEventType.INJURY,
                HOME, playerId, "No Detail Player",
                null, null, 0.0, "injury"));

        V24DetailedMatchResult v24Result = baseResult("md-nd-1", timeline, HOME, AWAY);
        RecordingStoragePort storage = new RecordingStoragePort();

        LeagueSimulator simulator = new LeagueSimulator(
                new FakeMatchSimulator(), null, false, true, /* persistDetail */ false, storage,
                true, true, false, false, false);

        CareerSave career = makeCareerWithHealthyPlayer(HOME, AWAY, HOME, AWAY, playerId);

        simulator.persistV24DetailForLiveMatch(
                career, v24Result, HOME, AWAY, 0, 0);

        assertEquals(0, storage.saveCount, "No detail must be saved when persistDetail=false");
        SessionPlayer p = career.getSessionPlayer(playerId);
        assertFalse(p.getInjured(),
                "No mutation must be applied when persistDetail=false (early return)");
    }

    // ========== Helpers ==========

    private static V24DetailedMatchResult baseResult(
            String matchId, V24MatchTimeline tl, String homeId, String awayId) {
        return V24DetailedMatchResult.builder()
                .matchId(matchId)
                .homeTeamId(homeId)
                .awayTeamId(awayId)
                .homeGoals(1).awayGoals(0)
                .homeXg(1.0).awayXg(0.3)
                .homeShots(5).awayShots(3)
                .homePossession(55).awayPossession(45)
                .timeline(tl)
                .summary("Live test: " + matchId)
                .build();
    }

    /**
     * Creates a minimal career with the given healthy player in the HOME
     * starting XI. Includes a 1-fixture tournament state so the round lookup
     * in persistV24DetailForLiveMatch succeeds.
     */
    private static CareerSave makeCareerWithHealthyPlayer(
            String homeTeamId, String awayTeamId,
            String homeStartingTeamId, String awayStartingTeamId,
            String playerId) {
        CareerSave save = new CareerSave();
        save.getData().setCareerId("test_live_" + playerId);
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

        SessionPlayer target = SessionPlayer.custom(
                playerId, 25, "MID", 70, 70, 70, 70, 70, 70,
                BigDecimal.valueOf(1000));
        target.setSessionPlayerId(playerId);
        pm.addSessionPlayer(target);
        tm.assignPlayerToSquad(playerId, homeTeamId);

        List<String> homeStarterIds = new ArrayList<>();
        homeStarterIds.add(playerId);
        for (int i = 0; i < 10; i++) {
            String pid = "p_HOME_" + i;
            if (pid.equals(playerId)) continue;
            SessionPlayer p = SessionPlayer.custom(
                    pid, 25, "MID", 70, 70, 70, 70, 70, 70,
                    BigDecimal.valueOf(1000));
            p.setSessionPlayerId(pid);
            pm.addSessionPlayer(p);
            tm.assignPlayerToSquad(pid, homeTeamId);
            homeStarterIds.add(pid);
        }
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

        TournamentState ts = new TournamentState();
        ts.setCurrentRound(1);
        ts.setTotalRounds(5);
        ts.getFixtures().add(new MatchFixture("md-inj-1", homeTeamId, awayTeamId, 1));
        save.setTournamentState(ts);
        return save;
    }

    // ========== Fakes ==========

    /** Storage port that records every save (no Redis). */
    private static class RecordingStoragePort implements V24DetailedMatchStoragePort {
        int saveCount = 0;
        String lastCareerId;
        V24DetailedMatchData lastDetail;

        @Override
        public void save(String careerId, V24DetailedMatchData detail) {
            saveCount++;
            lastCareerId = careerId;
            lastDetail = detail;
        }

        @Override
        public Optional<V24DetailedMatchData> findByMatchId(String careerId, String matchId) {
            return Optional.empty();
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

    private static class FakeMatchSimulator
            implements com.footballmanager.domain.service.MatchSimulator {
        @Override
        public com.footballmanager.domain.model.entity.MatchState simulateReal(
                com.footballmanager.domain.model.entity.MatchState state, int toMinute) {
            return state;
        }

        @Override
        public com.footballmanager.domain.service.MatchSimulator.MatchResult simulateQuick(
                String homeTeamId, String awayTeamId, int homeOvr, int awayOvr) {
            return new com.footballmanager.domain.service.MatchSimulator.MatchResult(0, 0);
        }
    }
}
