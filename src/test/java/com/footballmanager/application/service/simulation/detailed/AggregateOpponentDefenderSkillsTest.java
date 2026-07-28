package com.footballmanager.application.service.simulation.detailed;

import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.valueobject.PlayerSkill;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *
 * was {@code private} and only exercised indirectly through the full
 * gap (helper logic was untestable without reflection on private methods).
 * {@code @VisibleForTesting} so this suite can drive it directly.
 *
 * <p><b>Contract under test:</b>
 * <ul>
 *   <li>Filters to {@code onPitch()} AND {@code position == "DEF"} — MID,
 *       ATT, FWD and OFF-PITCH players are ignored.</li>
 *   <li>For each skill (MARKER, TACKLER), computes the average across
 *       matching DEF on-pitch players and rounds to nearest int with
 *       {@link Math#round}.</li>
 *   <li>Sparse map semantics: an avg of {@code 0} (no DEF on-pitch with
 *       that skill) results in the entry being OMITTED from the returned
 *       map (not stored as 0). Callers treat absent entries as 0.</li>
 *   <li>Returns an empty {@code Map.of()} when no DEF on-pitch players
 *       are present (no averages to compute).</li>
 * </ul>
 */
@DisplayName("DetailedMatchEngine.aggregateOpponentDefenderSkills — unit tests")
class AggregateOpponentDefenderSkillsTest {

    private DetailedMatchEngine engine;

    @BeforeEach
    void setUp() {
        engine = new DetailedMatchEngine();
    }

    // ========== Filter contract ==========

    @Nested
    @DisplayName("filter contract")
    class FilterContract {

        @Test
        @DisplayName("empty defenders list returns Map.of()")
        void emptyDefenders_returnsEmptyMap() {
            Map<PlayerSkill, Integer> result = engine.aggregateOpponentDefenderSkills(List.of());
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("only OFF-PITCH DEF players → empty (off-pitch ignored)")
        void onlyOffPitchDefenders_returnsEmpty() {
            List<PlayerMatchState> opponents = List.of(
                defOnBench("p1", "DEF", PlayerSkill.MARKER, 80),
                defOnBench("p2", "DEF", PlayerSkill.TACKLER, 70));
            Map<PlayerSkill, Integer> result = engine.aggregateOpponentDefenderSkills(opponents);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("MID on-pitch players are ignored (only DEF counts)")
        void onlyMidPlayers_returnsEmpty() {
            List<PlayerMatchState> opponents = List.of(
                buildDefender("m1", "MID", PlayerSkill.MARKER, 80, true),
                buildDefender("m2", "MID", PlayerSkill.TACKLER, 90, true));
            Map<PlayerSkill, Integer> result = engine.aggregateOpponentDefenderSkills(opponents);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("ATT/FWD/GK on-pitch are ignored (only DEF counts)")
        void onlyNonDefPositions_returnsEmpty() {
            List<PlayerMatchState> opponents = List.of(
                buildDefender("g1", "GK",  PlayerSkill.MARKER, 95, true),
                buildDefender("a1", "ATT", PlayerSkill.TACKLER, 90, true),
                buildDefender("f1", "FWD", PlayerSkill.MARKER, 80, true));
            Map<PlayerSkill, Integer> result = engine.aggregateOpponentDefenderSkills(opponents);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("mixed positions: only DEF on-pitch are aggregated")
        void mixedPositions_onlyDefOnPitchCounted() {
            List<PlayerMatchState> opponents = new ArrayList<>();
            // 2 DEF on-pitch (will be averaged)
            opponents.add(buildDefender("d1", "DEF", PlayerSkill.MARKER, 70, true));
            opponents.add(buildDefender("d2", "DEF", PlayerSkill.MARKER, 90, true));
            // 1 DEF off-pitch (ignored)
            opponents.add(buildDefender("d3", "DEF", PlayerSkill.MARKER, 99, false));
            // 2 MID on-pitch (ignored — even at 95 to keep within bounds)
            opponents.add(buildDefender("m1", "MID", PlayerSkill.MARKER, 95, true));
            opponents.add(buildDefender("m2", "MID", PlayerSkill.MARKER, 95, true));

            Map<PlayerSkill, Integer> result = engine.aggregateOpponentDefenderSkills(opponents);
            // avg MARKER = (70 + 90) / 2 = 80 (MID 95s ignored)
            assertThat(result).containsExactly(Map.entry(PlayerSkill.MARKER, 80));
        }
    }

    // ========== Avg + round contract ==========

    @Nested
    @DisplayName("average + round contract")
    class AvgContract {

        @Test
        @DisplayName("single DEF on-pitch with MARKER=80 → Map.of(MARKER, 80)")
        void singleDef_returnsExactLevel() {
            List<PlayerMatchState> opponents = List.of(
                buildDefender("d1", "DEF", PlayerSkill.MARKER, 80, true));
            Map<PlayerSkill, Integer> result = engine.aggregateOpponentDefenderSkills(opponents);
            assertThat(result).containsExactly(Map.entry(PlayerSkill.MARKER, 80));
        }

        @Test
        @DisplayName("DEF on-pitch with MARKER + TACKLER → both keys present (avg=exact)")
        void singleDefWithBothSkills_returnsBothKeys() {
            List<PlayerMatchState> opponents = List.of(
                buildDefenderWithSkills("d1", "DEF", true,
                    Map.of(PlayerSkill.MARKER, 70, PlayerSkill.TACKLER, 85)));
            Map<PlayerSkill, Integer> result = engine.aggregateOpponentDefenderSkills(opponents);
            assertThat(result).containsExactlyInAnyOrderEntriesOf(
                Map.of(PlayerSkill.MARKER, 70, PlayerSkill.TACKLER, 85));
        }

        @Test
        @DisplayName("multiple DEF on-pitch with same skill → averaged and rounded")
        void multipleDefWithSameSkill_averagesAndRounds() {
            List<PlayerMatchState> opponents = new ArrayList<>();
            opponents.add(buildDefender("d1", "DEF", PlayerSkill.MARKER, 70, true));
            opponents.add(buildDefender("d2", "DEF", PlayerSkill.MARKER, 80, true));
            opponents.add(buildDefender("d3", "DEF", PlayerSkill.MARKER, 90, true));
            // avg = (70 + 80 + 90) / 3 = 80.0
            Map<PlayerSkill, Integer> result = engine.aggregateOpponentDefenderSkills(opponents);
            assertThat(result).containsExactly(Map.entry(PlayerSkill.MARKER, 80));
        }

        @Test
        @DisplayName("Math.round: 67 + 68 → avg 67.5 → rounds to 68 (HALF_UP)")
        void mathRound_halfUp_behavior() {
            // Math.round(67.5) == 68 (HALF_UP). avg = (67+68)/2 = 67.5 → 68.
            List<PlayerMatchState> opponents = List.of(
                buildDefender("d1", "DEF", PlayerSkill.MARKER, 67, true),
                buildDefender("d2", "DEF", PlayerSkill.MARKER, 68, true));
            Map<PlayerSkill, Integer> result = engine.aggregateOpponentDefenderSkills(opponents);
            assertThat(result).containsExactly(Map.entry(PlayerSkill.MARKER, 68));
        }

        @Test
        @DisplayName("Math.round: 33 + 34 → avg 33.5 → rounds to 34")
        void mathRound_oddPair_lowBoundary() {
            List<PlayerMatchState> opponents = List.of(
                buildDefender("d1", "DEF", PlayerSkill.TACKLER, 33, true),
                buildDefender("d2", "DEF", PlayerSkill.TACKLER, 34, true));
            Map<PlayerSkill, Integer> result = engine.aggregateOpponentDefenderSkills(opponents);
            assertThat(result).containsExactly(Map.entry(PlayerSkill.TACKLER, 34));
        }

        @Test
        @DisplayName("multiple DEF: MARKER avg + TACKLER avg computed independently")
        void multipleDef_bothSkillsComputedIndependently() {
            List<PlayerMatchState> opponents = new ArrayList<>();
            // 4 DEF on-pitch (capped at 99 to stay within SessionPlayer.setSkillLevel bounds):
            //   MARKER levels: 60, 80, 80, 95 → avg 78.75 → Math.round → 79
            //   TACKLER levels: 50, 50, 90, 90 → avg 70 (exact)
            opponents.add(buildDefenderWithSkills("d1", "DEF", true,
                Map.of(PlayerSkill.MARKER, 60, PlayerSkill.TACKLER, 50)));
            opponents.add(buildDefenderWithSkills("d2", "DEF", true,
                Map.of(PlayerSkill.MARKER, 80, PlayerSkill.TACKLER, 50)));
            opponents.add(buildDefenderWithSkills("d3", "DEF", true,
                Map.of(PlayerSkill.MARKER, 80, PlayerSkill.TACKLER, 90)));
            opponents.add(buildDefenderWithSkills("d4", "DEF", true,
                Map.of(PlayerSkill.MARKER, 95, PlayerSkill.TACKLER, 90)));

            Map<PlayerSkill, Integer> result = engine.aggregateOpponentDefenderSkills(opponents);
            assertThat(result).containsExactlyInAnyOrderEntriesOf(
                Map.of(PlayerSkill.MARKER, 79, PlayerSkill.TACKLER, 70));
        }
    }

    // ========== Sparse map contract ==========

    @Nested
    @DisplayName("sparse map contract")
    class SparseContract {

        @Test
        @DisplayName("DEF on-pitch without MARKER → MARKER absent from result (not 0)")
        void defWithoutMarker_sparseOmitsKey() {
            List<PlayerMatchState> opponents = List.of(
                buildDefenderWithSkills("d1", "DEF", true,
                    Map.of(PlayerSkill.TACKLER, 70)));
            Map<PlayerSkill, Integer> result = engine.aggregateOpponentDefenderSkills(opponents);
            assertThat(result).doesNotContainKey(PlayerSkill.MARKER);
            assertThat(result).containsExactly(Map.entry(PlayerSkill.TACKLER, 70));
        }

        @Test
        @DisplayName("multiple DEF, NONE have MARKER → MARKER absent (avg=0 omitted)")
        void multipleDefNoneWithMarker_sparseOmitsKey() {
            List<PlayerMatchState> opponents = new ArrayList<>();
            opponents.add(buildDefender("d1", "DEF", PlayerSkill.TACKLER, 50, true));
            opponents.add(buildDefender("d2", "DEF", PlayerSkill.TACKLER, 60, true));
            opponents.add(buildDefender("d3", "DEF", PlayerSkill.TACKLER, 70, true));

            Map<PlayerSkill, Integer> result = engine.aggregateOpponentDefenderSkills(opponents);
            assertThat(result).doesNotContainKey(PlayerSkill.MARKER);
            assertThat(result).containsExactly(Map.entry(PlayerSkill.TACKLER, 60));
        }

        @Test
        @DisplayName("DEF with MARKER=0 (set explicitly) → avg=0 → sparse omits")
        void defWithMarkerZero_sparseOmitsKey() {
            // SessionPlayer.setSkillLevel(skill, 0) removes from sparse map,
            // so getSkillLevel returns 0. The helper should avg to 0 and
            // OMIT the entry from the result map.
            SessionPlayer sp = newDefender("d1", "DEF");
            sp.setSkillLevel(PlayerSkill.MARKER, 0);
            PlayerMatchState p = PlayerMatchState.fromSessionPlayer(sp, "rival");
            // onPitch defaults to true via fromSessionPlayer

            Map<PlayerSkill, Integer> result = engine.aggregateOpponentDefenderSkills(List.of(p));
            assertThat(result).doesNotContainKey(PlayerSkill.MARKER);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("empty result map is unmodifiable (Map.of semantics)")
        void emptyResult_isUnmodifiable() {
            Map<PlayerSkill, Integer> result = engine.aggregateOpponentDefenderSkills(List.of());
            assertThat(result).isEmpty();
            // Map.of() returns an immutable map — put should throw UnsupportedOperationException
            org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> result.put(PlayerSkill.MARKER, 99));
        }
    }

    // ========== Helpers ==========

    /**
     * Build a SessionPlayer with a specific position + a single skill, then
     * wrap it as {@link PlayerMatchState} with onPitch toggled.
     */
    private PlayerMatchState buildDefender(String id, String position,
                                              PlayerSkill skill, int level,
                                              boolean onPitch) {
        SessionPlayer sp = newDefender(id, position);
        sp.setSkillLevel(skill, level);
        PlayerMatchState state = PlayerMatchState.fromSessionPlayer(sp, "rival-team");
        if (!onPitch) {
            state.substituteOff();
        }
        return state;
    }

    /**
     * Build a SessionPlayer with a specific position + multiple skills.
     */
    private PlayerMatchState buildDefenderWithSkills(String id, String position,
                                                       boolean onPitch,
                                                       Map<PlayerSkill, Integer> skills) {
        SessionPlayer sp = newDefender(id, position);
        for (Map.Entry<PlayerSkill, Integer> e : skills.entrySet()) {
            sp.setSkillLevel(e.getKey(), e.getValue());
        }
        PlayerMatchState state = PlayerMatchState.fromSessionPlayer(sp, "rival-team");
        if (!onPitch) {
            state.substituteOff();
        }
        return state;
    }

    /**
     * Build a DEF SessionPlayer with no skills (for the off-pitch filter test).
     * Convenience: returns a state already off-pitch via {@code substituteOff()}.
     */
    private PlayerMatchState defOnBench(String id, String position,
                                            PlayerSkill skill, int level) {
        SessionPlayer sp = newDefender(id, position);
        sp.setSkillLevel(skill, level);
        PlayerMatchState state = PlayerMatchState.fromSessionPlayer(sp, "rival-team");
        state.substituteOff();
        return state;
    }

    /**
     * Minimal SessionPlayer builder. Defaults match the
     * {@code TestHarnessUseCaseImplTest.healthyPlayer} pattern but trimmed
     * to the fields PlayerMatchState actually reads.
     */
    private SessionPlayer newDefender(String id, String position) {
        SessionPlayer p = new SessionPlayer();
        p.setSessionPlayerId(id);
        p.setName("DEF " + id);
        p.setAge(25);
        p.setPosition(position);
        p.setAttack(70);
        p.setDefense(70);
        p.setTechnique(70);
        p.setSpeed(70);
        p.setStamina(70);
        p.setMentality(70);
        // Defensive initDefaults replication so PlayerMatchState.fromSessionPlayer
        // does not NPE on null form/injured/energy.
        p.setInjured(false);
        p.setEnergy(100);
        p.setForm(50);
        // skillLevels default to empty map via initDefaults; we add via setSkillLevel
        // above. fromSessionPlayer pulls getSkillLevels() which returns unmodifiable
        // view → we don't need to wire more here.
        return p;
    }

    /**
     * Sanity check helper: assert the helper contract is at least invoked
     * with the parameters the engine actually passes (i.e. opponents is
     * non-null in production paths). Not part of the public contract test,
     * but useful as a smoke if {@code fromSessionPlayer} signature changes.
     */
    @Test
    @DisplayName("smoke: PlayerMatchState.fromSessionPlayer wires skillLevels correctly")
    void smokeFromSessionPlayer_wiresSkillLevels() {
        SessionPlayer sp = newDefender("smoke", "DEF");
        sp.setSkillLevel(PlayerSkill.MARKER, 42);
        sp.setSkillLevel(PlayerSkill.TACKLER, 7);
        PlayerMatchState state = PlayerMatchState.fromSessionPlayer(sp, "rival");

        assertThat(state.position()).isEqualTo("DEF");
        assertThat(state.onPitch()).isTrue();
        assertThat(state.getSkillLevel(PlayerSkill.MARKER)).isEqualTo(42);
        assertThat(state.getSkillLevel(PlayerSkill.TACKLER)).isEqualTo(7);
        assertThat(state.getSkillLevel(PlayerSkill.SHOOTER)).isEqualTo(0); // sparse default

        // Drive the helper to make sure non-empty input is handled without exception
        Map<PlayerSkill, Integer> result = engine.aggregateOpponentDefenderSkills(
            Collections.singletonList(state));
        assertThat(result).containsExactlyInAnyOrderEntriesOf(
            Map.of(PlayerSkill.MARKER, 42, PlayerSkill.TACKLER, 7));
    }
}