package com.footballmanager.application.service.simulation.v24;

import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V24D6J4: Unit tests for V24EnergyRecoveryLifecycleApplier.
 * Tests energy recovery lifecycle in isolation — no Spring, no IO.
 */
class V24EnergyRecoveryLifecycleApplierTest {

    private static final int RECOVERY = 8;
    private static final int CAP = 100;

    private final V24EnergyRecoveryLifecycleApplier applier = new V24EnergyRecoveryLifecycleApplier();

    // ========== Helper builders ==========

    private V24CareerMutationPolicy policy(boolean enabled) {
        return new V24CareerMutationPolicy(enabled, enabled, enabled, enabled, enabled);
    }

    private V24CareerMutationPolicy policy(boolean mutate, boolean injuries,
            boolean fatigue, boolean discipline, boolean form) {
        return new V24CareerMutationPolicy(mutate, injuries, fatigue, discipline, form);
    }

    private SessionPlayer makePlayer(String playerId, int energy, Boolean injured) {
        SessionPlayer p = SessionPlayer.fromWorldPlayer(playerId, "Player " + playerId, "MID", 25, 70);
        p.setEnergy(energy);
        p.setInjured(injured);
        return p;
    }

    private SessionPlayer makePlayerWithSuspended(String playerId, int energy, Boolean injured, boolean suspended) {
        SessionPlayer p = makePlayer(playerId, energy, injured);
        p.setSuspended(suspended);
        return p;
    }

    private CareerSave careerWithTeamAndPlayers(String teamId, List<String> playerIds,
            List<SessionPlayer> players) {
        CareerSave career = new CareerSave();

        SessionTeam team = new SessionTeam();
        team.setSessionTeamId(teamId);
        team.setName("Team " + teamId);
        team.setWorldTeamId(teamId);
        career.addSessionTeam(team);

        for (int i = 0; i < playerIds.size(); i++) {
            String pid = playerIds.get(i);
            SessionPlayer p = players.get(i);
            career.addSessionPlayer(p);
            career.assignPlayerToTeam(pid, teamId);
        }

        return career;
    }

    private CareerSave careerWithTeamAndPlayer(String teamId, String playerId, SessionPlayer player) {
        return careerWithTeamAndPlayers(teamId, List.of(playerId), List.of(player));
    }

    private Set<String> set(String... ids) {
        return new HashSet<>(Arrays.asList(ids));
    }

    // ========== Null-guard / disabled tests ==========

    @Test
    void nullCareer_doesNothing() {
        V24CareerMutationPolicy pol = policy(true, false, true, false, false);
        int result = applier.applyRecovery(null, set("p1"), pol);
        assertEquals(0, result);
    }

    @Test
    void nullPolicy_doesNothing() {
        CareerSave career = careerWithTeamAndPlayer("team-A", "p1",
                makePlayer("p1", 70, false));
        int result = applier.applyRecovery(career, set("p1"), null);
        assertEquals(0, result);
    }

    @Test
    void nullParticipatedSet_doesNothing() {
        CareerSave career = careerWithTeamAndPlayer("team-A", "p1",
                makePlayer("p1", 70, false));
        V24CareerMutationPolicy pol = policy(true, false, true, false, false);
        int result = applier.applyRecovery(career, null, pol);
        assertEquals(0, result);
    }

    @Test
    void fatiguePersistenceFalse_doesNothing() {
        CareerSave career = careerWithTeamAndPlayer("team-A", "p1",
                makePlayer("p1", 70, false));
        V24CareerMutationPolicy pol = policy(true, true, false, true, true);
        int result = applier.applyRecovery(career, set(), pol);
        assertEquals(0, result);
        assertEquals(70, career.getSessionPlayer("p1").getEnergy());
    }

    @Test
    void mutateCareerStateFalse_doesNothing() {
        CareerSave career = careerWithTeamAndPlayer("team-A", "p1",
                makePlayer("p1", 70, false));
        V24CareerMutationPolicy pol = policy(false, true, true, true, true);
        int result = applier.applyRecovery(career, set(), pol);
        assertEquals(0, result);
        assertEquals(70, career.getSessionPlayer("p1").getEnergy());
    }

    // ========== Core recovery tests ==========

    @Test
    void nonParticipatingPlayer_recoversByDefaultAmount() {
        CareerSave career = careerWithTeamAndPlayer("team-A", "p1",
                makePlayer("p1", 70, false));
        V24CareerMutationPolicy pol = policy(true, false, true, false, false);

        int result = applier.applyRecovery(career, set(), pol);

        assertEquals(1, result);
        assertEquals(78, career.getSessionPlayer("p1").getEnergy());
    }

    @Test
    void participatingPlayer_doesNotRecover() {
        CareerSave career = careerWithTeamAndPlayer("team-A", "p1",
                makePlayer("p1", 70, false));
        V24CareerMutationPolicy pol = policy(true, false, true, false, false);

        int result = applier.applyRecovery(career, set("p1"), pol);

        assertEquals(0, result);
        assertEquals(70, career.getSessionPlayer("p1").getEnergy());
    }

    @Test
    void recoveryCapsAt100() {
        CareerSave career = careerWithTeamAndPlayer("team-A", "p1",
                makePlayer("p1", 95, false));
        V24CareerMutationPolicy pol = policy(true, false, true, false, false);

        int result = applier.applyRecovery(career, set(), pol);

        assertEquals(1, result);
        assertEquals(CAP, career.getSessionPlayer("p1").getEnergy());
    }

    @Test
    void energyAt100_noChange() {
        CareerSave career = careerWithTeamAndPlayer("team-A", "p1",
                makePlayer("p1", 100, false));
        V24CareerMutationPolicy pol = policy(true, false, true, false, false);

        int result = applier.applyRecovery(career, set(), pol);

        assertEquals(0, result);
        assertEquals(100, career.getSessionPlayer("p1").getEnergy());
    }

    @Test
    void nullEnergy_normalizedTo100() {
        CareerSave career = careerWithTeamAndPlayer("team-A", "p1",
                makePlayer("p1", 100, false));
        // Force null energy via reflection
        setEnergyNull(career.getSessionPlayer("p1"));

        V24CareerMutationPolicy pol = policy(true, false, true, false, false);

        int result = applier.applyRecovery(career, set(), pol);

        // Null treated as 100, already at cap, no change
        assertEquals(0, result);
        assertNull(career.getSessionPlayer("p1").getEnergy());
    }

    @Test
    void injuredPlayer_recoversIfNotParticipated() {
        CareerSave career = careerWithTeamAndPlayer("team-A", "p1",
                makePlayer("p1", 60, true));
        V24CareerMutationPolicy pol = policy(true, false, true, false, false);

        int result = applier.applyRecovery(career, set(), pol);

        assertEquals(1, result);
        assertEquals(68, career.getSessionPlayer("p1").getEnergy());
        assertTrue(career.getSessionPlayer("p1").getInjured());  // injured unchanged
    }

    @Test
    void suspendedPlayer_recoversIfNotParticipated() {
        CareerSave career = careerWithTeamAndPlayer("team-A", "p1",
                makePlayerWithSuspended("p1", 60, false, true));
        V24CareerMutationPolicy pol = policy(true, false, true, false, false);

        int result = applier.applyRecovery(career, set(), pol);

        assertEquals(1, result);
        assertEquals(68, career.getSessionPlayer("p1").getEnergy());
        assertTrue(career.getSessionPlayer("p1").getSuspended());  // suspended unchanged
    }

    @Test
    void duplicatePlayerInMultipleSquads_recoversOnlyOnce() {
        CareerSave career = new CareerSave();

        SessionTeam teamA = new SessionTeam();
        teamA.setSessionTeamId("team-A");
        teamA.setName("Team A");
        teamA.setWorldTeamId("team-A");
        career.addSessionTeam(teamA);

        SessionTeam teamB = new SessionTeam();
        teamB.setSessionTeamId("team-B");
        teamB.setName("Team B");
        teamB.setWorldTeamId("team-B");
        career.addSessionTeam(teamB);

        SessionPlayer p1 = SessionPlayer.fromWorldPlayer("p1", "Player 1", "MID", 25, 70);
        p1.setEnergy(70);
        career.addSessionPlayer(p1);
        // p1 in both teams (duplicate)
        career.assignPlayerToTeam("p1", "team-A");
        career.assignPlayerToTeam("p1", "team-B");

        V24CareerMutationPolicy pol = policy(true, false, true, false, false);

        int result = applier.applyRecovery(career, set(), pol);

        // Only +8 once despite p1 appearing in two squads
        assertEquals(1, result);
        assertEquals(78, career.getSessionPlayer("p1").getEnergy());
    }

    @Test
    void playerNotFoundInCareer_skipsSafely() {
        CareerSave career = careerWithTeamAndPlayer("team-A", "p1",
                makePlayer("p1", 70, false));
        V24CareerMutationPolicy pol = policy(true, false, true, false, false);

        // p2 not in career
        int result = applier.applyRecovery(career, set("p1", "p2"), pol);

        // p1 participated, p2 not found — both skip safely
        assertEquals(0, result);
        assertEquals(70, career.getSessionPlayer("p1").getEnergy());
    }

    @Test
    void multipleNonParticipatingPlayers_recoverTogether() {
        SessionPlayer p1 = makePlayer("p1", 70, false);
        SessionPlayer p2 = makePlayer("p2", 80, false);
        CareerSave career = careerWithPlayersInTeam("team-A", List.of("p1", "p2"), List.of(p1, p2));
        V24CareerMutationPolicy pol = policy(true, false, true, false, false);

        int result = applier.applyRecovery(career, set(), pol);

        assertEquals(2, result);
        assertEquals(78, career.getSessionPlayer("p1").getEnergy());
        assertEquals(88, career.getSessionPlayer("p2").getEnergy());
    }

    @Test
    void participatingAndNonParticipating_mixedRecovery() {
        SessionPlayer p1 = makePlayer("p1", 70, false);
        SessionPlayer p2 = makePlayer("p2", 80, false);
        CareerSave career = careerWithPlayersInTeam("team-A", List.of("p1", "p2"), List.of(p1, p2));
        V24CareerMutationPolicy pol = policy(true, false, true, false, false);

        // p1 participated, p2 did not
        int result = applier.applyRecovery(career, set("p1"), pol);

        assertEquals(1, result);
        assertEquals(70, career.getSessionPlayer("p1").getEnergy());  // unchanged
        assertEquals(88, career.getSessionPlayer("p2").getEnergy());  // recovered
    }

    // ========== Edge cases ==========

    @Test
    void emptyTeamSquad_skipsSafely() {
        CareerSave career = new CareerSave();
        SessionTeam team = new SessionTeam();
        team.setSessionTeamId("team-A");
        team.setName("Team A");
        team.setWorldTeamId("team-A");
        career.addSessionTeam(team);
        // No players assigned

        V24CareerMutationPolicy pol = policy(true, false, true, false, false);
        int result = applier.applyRecovery(career, set(), pol);

        assertEquals(0, result);
    }

    @Test
    void teamWithNullSquad_skipsSafely() {
        CareerSave career = new CareerSave();
        SessionTeam team = new SessionTeam();
        team.setSessionTeamId("team-A");
        team.setName("Team A");
        team.setWorldTeamId("team-A");
        career.addSessionTeam(team);
        // teamA has no squad (null)

        SessionPlayer p1 = SessionPlayer.fromWorldPlayer("p1", "Player 1", "MID", 25, 70);
        p1.setEnergy(70);
        career.addSessionPlayer(p1);
        // p1 not assigned to any team

        V24CareerMutationPolicy pol = policy(true, false, true, false, false);
        int result = applier.applyRecovery(career, set(), pol);

        assertEquals(0, result);
    }

    // ========== Helpers ==========

    private CareerSave careerWithPlayersInTeam(String teamId, List<String> playerIds,
            List<SessionPlayer> players) {
        CareerSave career = new CareerSave();

        SessionTeam team = new SessionTeam();
        team.setSessionTeamId(teamId);
        team.setName("Team " + teamId);
        team.setWorldTeamId(teamId);
        career.addSessionTeam(team);

        for (int i = 0; i < playerIds.size(); i++) {
            career.addSessionPlayer(players.get(i));
            career.assignPlayerToTeam(playerIds.get(i), teamId);
        }

        return career;
    }

    private void setEnergyNull(SessionPlayer p) {
        try {
            java.lang.reflect.Field field = SessionPlayer.class.getDeclaredField("energy");
            field.setAccessible(true);
            field.set(p, null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}