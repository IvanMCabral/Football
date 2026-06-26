package com.footballmanager.application.service.testharness;

import com.footballmanager.application.engine.match.MatchEngineRegistry;
import com.footballmanager.application.service.career.CareerSessionService;
import com.footballmanager.application.service.simulation.v24.V24DetailedMatchStoragePort;
import com.footballmanager.application.service.simulation.v24.V24MatchContextFactory;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.repository.CareerRepository;
import com.footballmanager.domain.model.valueobject.PlayerSkill;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * V25D35: extended tests for {@link TestHarnessUseCaseImpl#injectPlayerStats}
 * covering the new {@code heightCm} and {@code skillLevels} fields.
 *
 * <p><b>Strategy:</b> Mockito-only (no Spring context), same as the
 * V25D29 {@code TestHarnessUseCaseImplTest}. Verifies state-mutation
 * contract on {@link CareerSave} and delegation to
 * {@link CareerRepository} / {@link CareerSessionService}.
 *
 * <p><b>Backward-compat regression guard:</b> the "legacy 6-stats only"
 * test asserts bit-a-bit compatibility with V25D29 — callers that pass
 * {@code heightCm=null} and {@code skillLevels=null} keep working without
 * any change in behavior (only the 6 stats change; everything else
 * including pre-existing height + skills stays untouched).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InjectPlayerStatsExtended — V25D35 height + skills extension")
class InjectPlayerStatsExtendedTest {

    private static final java.util.UUID USER_ID =
        java.util.UUID.fromString("00000000-0000-0000-0000-000000000099");
    private static final String PLAYER_ID = "ext-p1";
    private static final String MISSING_PLAYER_ID = "ext-does-not-exist";

    @Mock private CareerRepository careerRepository;
    @Mock private CareerSessionService careerSessionService;
    @Mock private V24DetailedMatchStoragePort v24StoragePort;
    @Mock private MatchEngineRegistry matchEngineRegistry;
    // Real factory — same pattern as TestHarnessUseCaseImplTest.
    private V24MatchContextFactory v24ContextFactory;
    private TestHarnessUseCaseImpl useCase;

    private CareerSave career;
    private SessionPlayer player;

    @BeforeEach
    void setUp() {
        v24ContextFactory = new V24MatchContextFactory();
        useCase = new TestHarnessUseCaseImpl(
            careerRepository, careerSessionService,
            v24ContextFactory, v24StoragePort, matchEngineRegistry);

        career = new CareerSave();
        career.setUserId(USER_ID);
        career.setUserSessionTeamId("user-team-id");

        player = new SessionPlayer();
        player.setSessionPlayerId(PLAYER_ID);
        player.setName("Extended Player");
        player.setAge(25);
        player.setPosition("ATT");
        // 6 stats at V25D29 baseline (70 OVR) so we can verify they DO change
        player.setAttack(70);
        player.setDefense(70);
        player.setTechnique(70);
        player.setSpeed(70);
        player.setStamina(70);
        player.setMentality(70);
        // V25D31 physical + skill baseline (set so we can verify the new
        // fields leave them untouched when caller passes null)
        player.setHeightCm(180);
        player.setSkillLevel(PlayerSkill.SPEEDSTER, 50);
        player.setSkillLevel(PlayerSkill.PASSER, 60);
        // Defensive initDefaults replication
        player.setInjured(false);
        player.setYellowCards(0);
        player.setRedCards(0);
        player.setSuspended(false);

        // Register player into career via reflection (same pattern as TestHarnessUseCaseImplTest)
        try {
            java.lang.reflect.Field pmField = CareerSave.class.getDeclaredField("playerManager");
            pmField.setAccessible(true);
            Object playerManager = pmField.get(career);
            java.lang.reflect.Method addSessionPlayer =
                playerManager.getClass().getMethod("addSessionPlayer", SessionPlayer.class);
            addSessionPlayer.invoke(playerManager, player);
        } catch (Exception e) {
            throw new RuntimeException("Failed to register SessionPlayer via reflection", e);
        }
    }

    // ========== 1. Backward-compat: caller V25D29 (no height, no skills) ==========

    @Test
    @DisplayName("legacy 6-stats only: updates 6 stats + leaves height + skills intact (backward-compat)")
    void injectLegacy6StatsOnly_preservesHeightAndSkills() {
        when(careerRepository.findById(USER_ID.toString()))
            .thenReturn(Mono.just(java.util.Optional.of(career)));
        when(careerRepository.save(any(CareerSave.class)))
            .thenReturn(Mono.empty());

        // Pre-condition sanity: player has V25D31 baseline values
        assertThat(player.getHeightCm()).isEqualTo(180);
        assertThat(player.getSkillLevel(PlayerSkill.SPEEDSTER)).isEqualTo(50);
        assertThat(player.getSkillLevel(PlayerSkill.PASSER)).isEqualTo(60);

        useCase.injectPlayerStats(USER_ID, PLAYER_ID,
            /*attack*/ 95, /*defense*/ 60, /*technique*/ 88,
            /*speed*/ 92, /*stamina*/ 80, /*mentality*/ 75,
            /*heightCm*/ null, /*skillLevels*/ null)
            .as(StepVerifier::create)
            .verifyComplete();

        // 6 stats changed
        assertThat(player.getAttack()).isEqualTo(95);
        assertThat(player.getDefense()).isEqualTo(60);
        assertThat(player.getTechnique()).isEqualTo(88);
        assertThat(player.getSpeed()).isEqualTo(92);
        assertThat(player.getStamina()).isEqualTo(80);
        assertThat(player.getMentality()).isEqualTo(75);

        // V25D31 physical + skills: UNCHANGED (backward-compat regression guard)
        assertThat(player.getHeightCm()).isEqualTo(180);
        assertThat(player.getSkillLevel(PlayerSkill.SPEEDSTER)).isEqualTo(50);
        assertThat(player.getSkillLevel(PlayerSkill.PASSER)).isEqualTo(60);

        verify(careerRepository, times(1)).save(career);
    }

    // ========== 2. heightCm: in-range applies, out-of-range rejects ==========

    @Nested
    @DisplayName("heightCm handling")
    class HeightCmTests {

        @Test
        @DisplayName("heightCm=200 sets SessionPlayer.heightCm to 200")
        void injectHeightCmInRange_applies() {
            when(careerRepository.findById(USER_ID.toString()))
                .thenReturn(Mono.just(java.util.Optional.of(career)));
            when(careerRepository.save(any(CareerSave.class)))
                .thenReturn(Mono.empty());

            useCase.injectPlayerStats(USER_ID, PLAYER_ID,
                null, null, null, null, null, null,
                /*heightCm*/ 200, /*skillLevels*/ null)
                .as(StepVerifier::create)
                .verifyComplete();

            assertThat(player.getHeightCm()).isEqualTo(200);

            // No stats changed (all null)
            assertThat(player.getAttack()).isEqualTo(70);
            assertThat(player.getSpeed()).isEqualTo(70);
        }

        @Test
        @DisplayName("heightCm=100 (out of bounds) returns IAE BEFORE career save")
        void injectHeightCmOutOfRange_rejectsBeforeSave() {
            useCase.injectPlayerStats(USER_ID, PLAYER_ID,
                null, null, null, null, null, null,
                /*heightCm*/ 100, /*skillLevels*/ null)
                .as(StepVerifier::create)
                .expectErrorMatches(t ->
                    t instanceof IllegalArgumentException
                        && t.getMessage().contains("heightCm")
                        && t.getMessage().contains("160"))
                .verify();

            // Career MUST NOT be loaded / saved on validation failure
            verify(careerRepository, never()).findById(anyString());
            verify(careerRepository, never()).save(any(CareerSave.class));
        }

        @Test
        @DisplayName("heightCm=220 (out of bounds) returns IAE")
        void injectHeightCmAbove210_rejects() {
            useCase.injectPlayerStats(USER_ID, PLAYER_ID,
                null, null, null, null, null, null,
                /*heightCm*/ 220, /*skillLevels*/ null)
                .as(StepVerifier::create)
                .expectErrorMatches(t ->
                    t instanceof IllegalArgumentException
                        && t.getMessage().contains("heightCm"))
                .verify();
        }

        @Test
        @DisplayName("heightCm=160 (lower bound inclusive) accepted")
        void injectHeightCmLowerBound_accepted() {
            when(careerRepository.findById(USER_ID.toString()))
                .thenReturn(Mono.just(java.util.Optional.of(career)));
            when(careerRepository.save(any(CareerSave.class)))
                .thenReturn(Mono.empty());

            useCase.injectPlayerStats(USER_ID, PLAYER_ID,
                null, null, null, null, null, null,
                /*heightCm*/ 160, /*skillLevels*/ null)
                .as(StepVerifier::create)
                .verifyComplete();

            assertThat(player.getHeightCm()).isEqualTo(160);
        }

        @Test
        @DisplayName("heightCm=210 (upper bound inclusive) accepted")
        void injectHeightCmUpperBound_accepted() {
            when(careerRepository.findById(USER_ID.toString()))
                .thenReturn(Mono.just(java.util.Optional.of(career)));
            when(careerRepository.save(any(CareerSave.class)))
                .thenReturn(Mono.empty());

            useCase.injectPlayerStats(USER_ID, PLAYER_ID,
                null, null, null, null, null, null,
                /*heightCm*/ 210, /*skillLevels*/ null)
                .as(StepVerifier::create)
                .verifyComplete();

            assertThat(player.getHeightCm()).isEqualTo(210);
        }
    }

    // ========== 3. skillLevels: applies per-entry, empty = no-op ==========

    @Nested
    @DisplayName("skillLevels handling")
    class SkillLevelsTests {

        @Test
        @DisplayName("skillLevels={HEADER:80} sets SessionPlayer.getSkillLevel(HEADER)=80")
        void injectSkillSingleEntry_applies() {
            when(careerRepository.findById(USER_ID.toString()))
                .thenReturn(Mono.just(java.util.Optional.of(career)));
            when(careerRepository.save(any(CareerSave.class)))
                .thenReturn(Mono.empty());

            Map<PlayerSkill, Integer> skills = new HashMap<>();
            skills.put(PlayerSkill.HEADER, 80);

            useCase.injectPlayerStats(USER_ID, PLAYER_ID,
                null, null, null, null, null, null,
                /*heightCm*/ null, /*skillLevels*/ skills)
                .as(StepVerifier::create)
                .verifyComplete();

            assertThat(player.getSkillLevel(PlayerSkill.HEADER)).isEqualTo(80);

            // Other pre-existing skills NOT touched (SPEEDSTER=50, PASSER=60 baseline)
            assertThat(player.getSkillLevel(PlayerSkill.SPEEDSTER)).isEqualTo(50);
            assertThat(player.getSkillLevel(PlayerSkill.PASSER)).isEqualTo(60);
        }

        @Test
        @DisplayName("skillLevels with multiple entries applies each")
        void injectSkillMultipleEntries_appliesAll() {
            when(careerRepository.findById(USER_ID.toString()))
                .thenReturn(Mono.just(java.util.Optional.of(career)));
            when(careerRepository.save(any(CareerSave.class)))
                .thenReturn(Mono.empty());

            Map<PlayerSkill, Integer> skills = new HashMap<>();
            skills.put(PlayerSkill.HEADER, 90);
            skills.put(PlayerSkill.DRIBBLER, 85);
            skills.put(PlayerSkill.SHOOTER, 92);
            skills.put(PlayerSkill.MARKER, 0); // 0 should REMOVE from sparse map (sparse semantics)

            useCase.injectPlayerStats(USER_ID, PLAYER_ID,
                null, null, null, null, null, null,
                /*heightCm*/ null, /*skillLevels*/ skills)
                .as(StepVerifier::create)
                .verifyComplete();

            assertThat(player.getSkillLevel(PlayerSkill.HEADER)).isEqualTo(90);
            assertThat(player.getSkillLevel(PlayerSkill.DRIBBLER)).isEqualTo(85);
            assertThat(player.getSkillLevel(PlayerSkill.SHOOTER)).isEqualTo(92);
            // MARKER was not in the sparse map before; setting to 0 leaves it absent
            assertThat(player.getSkillLevel(PlayerSkill.MARKER)).isEqualTo(0);

            // Pre-existing skills (SPEEDSTER, PASSER) STILL preserved
            assertThat(player.getSkillLevel(PlayerSkill.SPEEDSTER)).isEqualTo(50);
            assertThat(player.getSkillLevel(PlayerSkill.PASSER)).isEqualTo(60);
        }

        @Test
        @DisplayName("skill level=120 (out of bounds) returns IAE")
        void injectSkillLevelOutOfRange_rejects() {
            Map<PlayerSkill, Integer> skills = new HashMap<>();
            skills.put(PlayerSkill.HEADER, 120);

            useCase.injectPlayerStats(USER_ID, PLAYER_ID,
                null, null, null, null, null, null,
                /*heightCm*/ null, /*skillLevels*/ skills)
                .as(StepVerifier::create)
                .expectErrorMatches(t ->
                    t instanceof IllegalArgumentException
                        && t.getMessage().contains("Skill level"))
                .verify();

            verify(careerRepository, never()).findById(anyString());
        }

        @Test
        @DisplayName("skill level=-1 (negative) returns IAE")
        void injectSkillLevelNegative_rejects() {
            Map<PlayerSkill, Integer> skills = new HashMap<>();
            skills.put(PlayerSkill.HEADER, -1);

            useCase.injectPlayerStats(USER_ID, PLAYER_ID,
                null, null, null, null, null, null,
                /*heightCm*/ null, /*skillLevels*/ skills)
                .as(StepVerifier::create)
                .expectErrorMatches(t ->
                    t instanceof IllegalArgumentException
                        && t.getMessage().contains("Skill level"))
                .verify();
        }

        @Test
        @DisplayName("empty skillLevels={} is no-op (does NOT clear existing skills)")
        void injectEmptySkillLevels_isNoOp() {
            when(careerRepository.findById(USER_ID.toString()))
                .thenReturn(Mono.just(java.util.Optional.of(career)));
            when(careerRepository.save(any(CareerSave.class)))
                .thenReturn(Mono.empty());

            Map<PlayerSkill, Integer> empty = new HashMap<>();

            useCase.injectPlayerStats(USER_ID, PLAYER_ID,
                null, null, null, null, null, null,
                /*heightCm*/ null, /*skillLevels*/ empty)
                .as(StepVerifier::create)
                .verifyComplete();

            // Pre-existing skills STILL preserved (no-op behavior on empty map)
            assertThat(player.getSkillLevel(PlayerSkill.SPEEDSTER)).isEqualTo(50);
            assertThat(player.getSkillLevel(PlayerSkill.PASSER)).isEqualTo(60);
            // heightCm also untouched
            assertThat(player.getHeightCm()).isEqualTo(180);
        }

        @Test
        @DisplayName("null skillLevels is no-op (same as V25D29 callers)")
        void injectNullSkillLevels_isNoOp() {
            when(careerRepository.findById(USER_ID.toString()))
                .thenReturn(Mono.just(java.util.Optional.of(career)));
            when(careerRepository.save(any(CareerSave.class)))
                .thenReturn(Mono.empty());

            useCase.injectPlayerStats(USER_ID, PLAYER_ID,
                null, null, null, null, null, null,
                /*heightCm*/ null, /*skillLevels*/ null)
                .as(StepVerifier::create)
                .verifyComplete();

            assertThat(player.getSkillLevel(PlayerSkill.SPEEDSTER)).isEqualTo(50);
            assertThat(player.getSkillLevel(PlayerSkill.PASSER)).isEqualTo(60);
        }

        @Test
        @DisplayName("defensive null skip: skillLevels={null: 50, HEADER: 80} skips null key but applies HEADER")
        void injectSkillWithNullKey_skipsNullAppliesRest() {
            when(careerRepository.findById(USER_ID.toString()))
                .thenReturn(Mono.just(java.util.Optional.of(career)));
            when(careerRepository.save(any(CareerSave.class)))
                .thenReturn(Mono.empty());

            Map<PlayerSkill, Integer> skills = new HashMap<>();
            skills.put(null, 50); // defensive null skip
            skills.put(PlayerSkill.HEADER, 80);

            useCase.injectPlayerStats(USER_ID, PLAYER_ID,
                null, null, null, null, null, null,
                /*heightCm*/ null, /*skillLevels*/ skills)
                .as(StepVerifier::create)
                .verifyComplete();

            assertThat(player.getSkillLevel(PlayerSkill.HEADER)).isEqualTo(80);
            // Pre-existing skills still preserved
            assertThat(player.getSkillLevel(PlayerSkill.SPEEDSTER)).isEqualTo(50);
        }
    }

    // ========== 4. Combined: 6 stats + heightCm + skills in one call ==========

    @Test
    @DisplayName("combined: 6 stats + heightCm + skillLevels all apply in one call")
    void injectAllFieldsTogether_appliesAll() {
        when(careerRepository.findById(USER_ID.toString()))
            .thenReturn(Mono.just(java.util.Optional.of(career)));
        when(careerRepository.save(any(CareerSave.class)))
            .thenReturn(Mono.empty());

        Map<PlayerSkill, Integer> skills = new HashMap<>();
        skills.put(PlayerSkill.SPEEDSTER, 99);
        skills.put(PlayerSkill.PASSER, 95);

        useCase.injectPlayerStats(USER_ID, PLAYER_ID,
            /*attack*/ 90, /*defense*/ 50, /*technique*/ 85,
            /*speed*/ 95, /*stamina*/ 80, /*mentality*/ 80,
            /*heightCm*/ 195, /*skillLevels*/ skills)
            .as(StepVerifier::create)
            .verifyComplete();

        // 6 stats
        assertThat(player.getAttack()).isEqualTo(90);
        assertThat(player.getDefense()).isEqualTo(50);
        assertThat(player.getTechnique()).isEqualTo(85);
        assertThat(player.getSpeed()).isEqualTo(95);
        assertThat(player.getStamina()).isEqualTo(80);
        assertThat(player.getMentality()).isEqualTo(80);

        // V25D31 physical
        assertThat(player.getHeightCm()).isEqualTo(195);

        // V25D31 skills (SPEEDSTER + PASSER overwritten with new values)
        assertThat(player.getSkillLevel(PlayerSkill.SPEEDSTER)).isEqualTo(99);
        assertThat(player.getSkillLevel(PlayerSkill.PASSER)).isEqualTo(95);

        verify(careerRepository, times(1)).save(career);
    }

    // ========== 5. Error paths: player not found ==========

    @Test
    @DisplayName("unknown playerId returns IAE")
    void injectUnknownPlayer_rejects() {
        when(careerRepository.findById(USER_ID.toString()))
            .thenReturn(Mono.just(java.util.Optional.of(career)));
        // No save mock — the error path doesn't reach save

        useCase.injectPlayerStats(USER_ID, MISSING_PLAYER_ID,
            70, 70, 70, 70, 70, 70, null, null)
            .as(StepVerifier::create)
            .expectErrorMatches(t ->
                t instanceof IllegalArgumentException
                    && t.getMessage().contains("Player not found"))
            .verify();

        verify(careerRepository, never()).save(any(CareerSave.class));
    }

    @Test
    @DisplayName("blank playerId returns IAE without loading career")
    void injectBlankPlayerId_rejectsBeforeLoad() {
        useCase.injectPlayerStats(USER_ID, "  ",
            70, 70, 70, 70, 70, 70, null, null)
            .as(StepVerifier::create)
            .expectErrorMatches(t ->
                t instanceof IllegalArgumentException
                    && t.getMessage().contains("playerId"))
            .verify();

        verify(careerRepository, never()).findById(anyString());
    }
}