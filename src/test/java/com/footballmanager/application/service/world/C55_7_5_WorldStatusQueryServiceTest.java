package com.footballmanager.application.service.world;

import com.footballmanager.adapters.in.web.dashboard.dto.WorldStatusResponse;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.TournamentState;
import com.footballmanager.domain.model.entity.WorldSnapshot;
import com.footballmanager.domain.model.valueobject.MatchFixture;
import com.footballmanager.domain.model.valueobject.MatchStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * C55.7.5 — Tests for {@link WorldStatusQueryService#getWorldStatus(UUID)}
 * (MEDIUM bug #30 from C55.7.4).
 *
 * <p>REVISOR C55.7.4 smoke: the dashboard "WORLD STATUS" card always
 * shows "0 MATCHES", regardless of career state. The previous
 * implementation hardcoded {@code 0} in the response with a TODO
 * comment ("matches - requeriria contar de CareerSave o PostgreSQL").
 *
 * <p>The correct contract: read the user's CareerSave from the
 * tournament state, count fixtures with a non-null result, and return
 * that count.
 */
@ExtendWith(MockitoExtension.class)
class C55_7_5_WorldStatusQueryServiceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-00000000c575");

    @Mock
    private WorldService worldService;
    @Mock
    private com.footballmanager.application.service.career.CareerSessionService careerSessionService;

    private WorldStatusQueryService useCase;

    @BeforeEach
    void setUp() {
        useCase = new WorldStatusQueryService(worldService, careerSessionService);
    }

    private WorldSnapshot makeSnapshot(int clubCount, int playerCount) {
        WorldSnapshot snapshot = new WorldSnapshot();
        for (int i = 0; i < clubCount; i++) {
            com.footballmanager.domain.model.entity.WorldTeam team = new com.footballmanager.domain.model.entity.WorldTeam();
            team.setWorldTeamId("team-" + i);
            snapshot.addWorldTeam(team);
        }
        for (int i = 0; i < playerCount; i++) {
            com.footballmanager.domain.model.entity.WorldPlayer player = new com.footballmanager.domain.model.entity.WorldPlayer();
            player.setWorldPlayerId("player-" + i);
            snapshot.addWorldPlayer(player);
        }
        return snapshot;
    }

    private CareerSave makeCareer(int totalFixtures, int playedFixtureCount) {
        CareerSave career = new CareerSave();
        career.setUserId(USER_ID);
        TournamentState state = new TournamentState();
        state.setCurrentRound(1);
        state.setTotalRounds(10);

        for (int i = 0; i < totalFixtures; i++) {
            MatchFixture fixture = new MatchFixture(
                    "match-r1-" + (i + 1),
                    "team-home-" + i,
                    "team-away-" + i,
                    1
            );
            if (i < playedFixtureCount) {
                fixture.complete(new MatchFixture.MatchResultData(2, 1, 50, 50, 5, 3));
            }
            state.getFixtures().add(fixture);
        }
        career.setTournamentState(state);
        return career;
    }

    @Test
    @DisplayName("C55.7.5 #30: WorldStatus returns matches=0 for a fresh career (no played matches)")
    void worldStatus_freshCareer_matchesZero() {
        WorldSnapshot snapshot = makeSnapshot(60, 500);
        CareerSave career = makeCareer(10, 0);  // 10 fixtures, none played.
        when(worldService.getWorldSnapshot(USER_ID)).thenReturn(Mono.just(snapshot));
        when(careerSessionService.getCareerFromCache(USER_ID)).thenReturn(Mono.just(career));

        StepVerifier.create(useCase.getWorldStatus(USER_ID))
                .assertNext(resp -> {
                    assert resp != null;
                    assert resp.matches() == 0
                            : "Fresh career must report matches=0; got " + resp.matches();
                    assert resp.clubs() == 60;
                    assert resp.players() == 500;
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("C55.7.5 #30: WorldStatus returns matches>0 for a career with played matches")
    void worldStatus_postSeason_matchesNonZero() {
        // C55.7.5 #30: REVISOR observed "0 MATCHES" even at post-T1 finished
        // (~40+ matches played). The previous hardcoded 0 left the counter
        // frozen. This test pins the corrected contract: the matches count
        // must reflect fixtures with non-null result.
        WorldSnapshot snapshot = makeSnapshot(60, 500);
        CareerSave career = makeCareer(50, 42);  // 50 fixtures, 42 played (10 rounds × ~4 user matches).
        when(worldService.getWorldSnapshot(USER_ID)).thenReturn(Mono.just(snapshot));
        when(careerSessionService.getCareerFromCache(USER_ID)).thenReturn(Mono.just(career));

        StepVerifier.create(useCase.getWorldStatus(USER_ID))
                .assertNext(resp -> {
                    assert resp != null;
                    assert resp.matches() == 42
                            : "After 42 played matches, WorldStatus must report matches=42; got " + resp.matches();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("C55.7.5 #30: WorldStatus handles null CareerSave gracefully (no career yet)")
    void worldStatus_nullCareer_matchesZero() {
        WorldSnapshot snapshot = makeSnapshot(60, 500);
        when(worldService.getWorldSnapshot(USER_ID)).thenReturn(Mono.just(snapshot));
        when(careerSessionService.getCareerFromCache(USER_ID)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.getWorldStatus(USER_ID))
                .assertNext(resp -> {
                    assert resp != null;
                    assert resp.matches() == 0
                            : "No career must report matches=0; got " + resp.matches();
                })
                .verifyComplete();
    }
}
