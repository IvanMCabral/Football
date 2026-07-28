package com.footballmanager.application.service.testharness;
import com.footballmanager.application.service.editor.FormationDefinition;
import com.footballmanager.domain.model.valueobject.LineupSlot;
import com.footballmanager.application.engine.match.MatchEngineRegistry;
import com.footballmanager.application.service.career.CareerSessionService;
import com.footballmanager.domain.model.valueobject.TeamStyle;
import com.footballmanager.application.service.simulation.detailed.BaselineStateStoragePort;
import com.footballmanager.application.service.simulation.detailed.DetailedMatchStoragePort;
import com.footballmanager.application.service.simulation.detailed.MatchContextFactory;
import com.footballmanager.domain.model.entity.CareerPhase;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.repository.CareerRepository;
import com.footballmanager.domain.model.valueobject.MatchFixture;
import com.footballmanager.domain.model.valueobject.PlayerSkill;
import com.footballmanager.domain.port.in.testharness.TestHarnessUseCase;
import com.footballmanager.domain.port.in.testharness.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
@Service
@Profile({"dev", "local", "test"})
@Slf4j
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class TestHarnessUseCaseImpl implements TestHarnessUseCase {
    private final CareerRepository careerRepository;
    private final CareerSessionService careerSessionService;
    private final MatchContextFactory matchContextFactory;
    private final DetailedMatchStoragePort detailedMatchStoragePort;
    private final BaselineStateStoragePort baselineStoragePort;
    private final TestHarnessPreviewRunner previewRunner;
    private final TestHarnessLabService labService;
    private final TestHarnessAdminCommandService adminCommandService;
    private final TestHarnessLineupDiagnosticService lineupDiagnosticService;
    private final TestHarnessFormationMatrixService formationMatrixService;
    private final TestHarnessReplayService replayService;
    private final TestHarnessSubstitutionWhatIfService substitutionWhatIfService;
    private final TestHarnessRoleSlotImpactService roleSlotImpactService;
    private final TestHarnessPositionPixelService positionPixelService;
    private final TestHarnessPlayerSwapService playerSwapService;
    private final TestHarnessScenarioMatrixService scenarioMatrixService;
    private final MatchEngineRegistry matchEngineRegistry;
    TestHarnessUseCaseImpl(
            CareerRepository careerRepository,
            CareerSessionService careerSessionService,
            MatchContextFactory matchContextFactory,
            DetailedMatchStoragePort detailedMatchStoragePort,
            BaselineStateStoragePort baselineStoragePort,
            MatchEngineRegistry matchEngineRegistry) {
        this(
            careerRepository,
            careerSessionService,
            matchContextFactory,
            detailedMatchStoragePort,
            baselineStoragePort,
            TestHarnessUseCaseDependencyFactory.previewRunner(matchContextFactory),
            TestHarnessUseCaseDependencyFactory.labService(careerRepository, careerSessionService),
            TestHarnessUseCaseDependencyFactory.adminService(careerRepository, careerSessionService, detailedMatchStoragePort, matchEngineRegistry),
            TestHarnessUseCaseDependencyFactory.lineupDiagnosticService(careerRepository, matchContextFactory),
            TestHarnessUseCaseDependencyFactory.formationMatrixService(careerRepository, careerSessionService, matchContextFactory, baselineStoragePort, detailedMatchStoragePort),
            TestHarnessUseCaseDependencyFactory.replayService(careerRepository, careerSessionService, matchContextFactory, baselineStoragePort, detailedMatchStoragePort),
            new TestHarnessSubstitutionWhatIfService(careerRepository, matchContextFactory),
            new TestHarnessRoleSlotImpactService(careerRepository, matchContextFactory),
            new TestHarnessPositionPixelService(careerRepository, matchContextFactory),
            new TestHarnessPlayerSwapService(careerRepository, matchContextFactory),
            new TestHarnessScenarioMatrixService(careerRepository, matchContextFactory),
            matchEngineRegistry);
    }
    @Override
    public Mono<Void> replaceFixtures(UUID userId, List<CustomFixture> fixtures) {
        return adminCommandService.replaceFixtures(userId, fixtures);
    }
    @Override
    public Mono<Void> resetInjuries(UUID userId) {
        return adminCommandService.resetInjuries(userId);
    }
    @Override
    public Mono<Void> setFormation(UUID userId, String formation) {
        return adminCommandService.setFormation(userId, formation);
    }
    @Override
    public Mono<Void> setStyle(UUID userId, TeamStyle style) {
        return adminCommandService.setStyle(userId, style);
    }
    @Override
    public Mono<Void> injectPlayerStats(UUID userId, String playerId,
                                       Integer attack, Integer defense,
                                       Integer technique, Integer speed,
                                       Integer stamina, Integer mentality,
                                       Integer heightCm,
                                       Map<PlayerSkill, Integer> skillLevels) {
        return adminCommandService.injectPlayerStats(userId, playerId, attack, defense, technique, speed, stamina, mentality, heightCm, skillLevels);
    }
    @Override
    public Mono<LabMutationResult> prepareOffensiveUpgradeLab(UUID userId) {
        return labService.prepareOffensiveUpgradeLab(userId);
    }
    @Override
    public Mono<LabMutationResult> restoreOffensiveUpgradeLab(UUID userId) {
        return labService.restoreOffensiveUpgradeLab(userId);
    }
    @Override
    public Mono<LabMutationResult> prepareObjectiveContrastLab(UUID userId) {
        return labService.prepareObjectiveContrastLab(userId);
    }
    @Override
    public Mono<LabMutationResult> restoreObjectiveContrastLab(UUID userId) {
        return labService.restoreObjectiveContrastLab(userId);
    }
    @Override
    public Mono<LabMutationResult> prepareDefensiveDowngradeLab(UUID userId) {
        return labService.prepareDefensiveDowngradeLab(userId);
    }
    @Override
    public Mono<LabMutationResult> restoreDefensiveDowngradeLab(UUID userId) {
        return labService.restoreDefensiveDowngradeLab(userId);
    }
    @Override
    public Mono<LabMutationResult> prepareWeakWideDefendersLab(UUID userId) {
        return labService.prepareWeakWideDefendersLab(userId);
    }
    @Override
    public Mono<LabMutationResult> restoreWeakWideDefendersLab(UUID userId) {
        return labService.restoreWeakWideDefendersLab(userId);
    }
    @Override
    public Mono<LabMutationResult> prepareOpponentWeakWideDefendersLab(UUID userId, String matchId) {
        return labService.prepareOpponentWeakWideDefendersLab(userId, matchId);
    }
    @Override
    public Mono<LabMutationResult> restoreOpponentWeakWideDefendersLab(UUID userId, String matchId) {
        return labService.restoreOpponentWeakWideDefendersLab(userId, matchId);
    }
    @Override
    public Mono<LabMutationResult> prepareOpponentWeakLeftDefenderLab(UUID userId, String matchId) {
        return labService.prepareOpponentWeakLeftDefenderLab(userId, matchId);
    }
    @Override
    public Mono<LabMutationResult> restoreOpponentWeakLeftDefenderLab(UUID userId, String matchId) {
        return labService.restoreOpponentWeakLeftDefenderLab(userId, matchId);
    }
    @Override
    public Mono<LabMutationResult> prepareOpponentWeakRightDefenderLab(UUID userId, String matchId) {
        return labService.prepareOpponentWeakRightDefenderLab(userId, matchId);
    }
    @Override
    public Mono<LabMutationResult> restoreOpponentWeakRightDefenderLab(UUID userId, String matchId) {
        return labService.restoreOpponentWeakRightDefenderLab(userId, matchId);
    }
    @Override
    public Mono<LabMutationResult> prepareOpponentWeakCenterBacksLab(UUID userId, String matchId) {
        return labService.prepareOpponentWeakCenterBacksLab(userId, matchId);
    }
    @Override
    public Mono<LabMutationResult> restoreOpponentWeakCenterBacksLab(UUID userId, String matchId) {
        return labService.restoreOpponentWeakCenterBacksLab(userId, matchId);
    }
    @Override
    public Mono<LabMutationResult> prepareWeakLeftDefenderLab(UUID userId) {
        return labService.prepareWeakLeftDefenderLab(userId);
    }
    @Override
    public Mono<LabMutationResult> restoreWeakLeftDefenderLab(UUID userId) {
        return labService.restoreWeakLeftDefenderLab(userId);
    }
    @Override
    public Mono<LabMutationResult> prepareWeakRightDefenderLab(UUID userId) {
        return labService.prepareWeakRightDefenderLab(userId);
    }
    @Override
    public Mono<LabMutationResult> restoreWeakRightDefenderLab(UUID userId) {
        return labService.restoreWeakRightDefenderLab(userId);
    }
    @Override
    public Mono<LabMutationResult> prepareWeakCenterBacksLab(UUID userId) {
        return labService.prepareWeakCenterBacksLab(userId);
    }
    @Override
    public Mono<LabMutationResult> restoreWeakCenterBacksLab(UUID userId) {
        return labService.restoreWeakCenterBacksLab(userId);
    }
    @Override
    public Mono<CareerSave> createCustom(UUID userId, String worldLeagueId, String worldTeamId,
                                          String difficulty, String gameSpeed, int teamsPerDivision) {
        if (teamsPerDivision < 2) {
            return Mono.error(new IllegalArgumentException(
                "teamsPerDivision must be >= 2 (got " + teamsPerDivision + ")"));
        }
        log.info("createCustom userId={} league={} team={} "
                + "difficulty={} gameSpeed={} teamsPerDivision={}",
            userId, worldLeagueId, worldTeamId, difficulty, gameSpeed, teamsPerDivision);
        return careerSessionService.deleteCareer(userId)
            .then(careerSessionService.startNewCareer(
                userId, worldLeagueId, worldTeamId, difficulty, gameSpeed, teamsPerDivision))
            .flatMap(career -> careerRepository.findById(userId.toString())
                .flatMap(opt -> {
                    if (opt.isEmpty()) {
                        return Mono.just(career);
                    }
                    return adminCommandService.resetInjuries(userId)
                        .thenReturn(opt.get());
                }));
    }
    @Override
    public Mono<CareerSave> snapshot(UUID userId) {
        return careerRepository.findById(userId.toString())
            .flatMap(optionalCareer -> {
                if (optionalCareer.isEmpty()) {
                    return Mono.error(new IllegalStateException(
                        "Career not found for userId=" + userId));
                }
                return Mono.just(optionalCareer.get());
            });
    }
    @Override
    public Mono<MatchFixture> replayMatch(UUID userId, String matchId, Long seedOverride) {
        if (matchId == null || matchId.isBlank()) {
            return Mono.error(new IllegalArgumentException("matchId is required"));
        }
        long seed = (seedOverride != null) ? seedOverride : System.currentTimeMillis();
        log.trace("replayMatch userId={}, matchId={}, seed={}",
            userId, matchId, seed);
        return careerRepository.findById(userId.toString())
            .switchIfEmpty(Mono.error(new IllegalStateException(
                "No career for userId=" + userId + " - call create-custom first")))
            .flatMap(optionalCareer -> {
                if (optionalCareer.isEmpty()) {
                    return Mono.error(new IllegalStateException(
                        "Career not found for userId=" + userId));
                }
                CareerSave career = optionalCareer.get();
                return replayService.replay(career, matchId, seed);
            });
    }
    @Override
    public Mono<MatchPreviewSummary> runMatchPreviewSummary(
            UUID userId,
            String matchId,
            long seedStart,
            int seedCount,
            String controlledTeamSide) {
        if (matchId == null || matchId.isBlank()) {
            return Mono.error(new IllegalArgumentException("matchId is required"));
        }
        int safeSeedCount = Math.max(1, Math.min(50, seedCount));
        return careerRepository.findById(userId.toString())
            .switchIfEmpty(Mono.error(new IllegalStateException(
                "No career for userId=" + userId + " - call create-custom first")))
            .flatMap(optionalCareer -> {
                if (optionalCareer.isEmpty()) {
                    return Mono.error(new IllegalStateException(
                        "Career not found for userId=" + userId));
                }
                CareerSave career = optionalCareer.get();
                MatchFixture fixture = career.getTournamentState().getFixtures().stream()
                    .filter(f -> f.getMatchId().equals(matchId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                        "Match not found in current tournament: " + matchId));
                return Mono.just(previewRunner.run(
                    career,
                    fixture,
                    seedStart,
                    safeSeedCount,
                    controlledTeamSide));
            });
    }
    @Override
    public Mono<LineupDiagnostic> lineupDiagnostic(UUID userId, String matchId, Long seedOverride) {
        return lineupDiagnosticService.lineupDiagnostic(userId, matchId, seedOverride);
    }
    @Override
    public Mono<List<FormationMatrixRow>> runFormationMatrix(UUID userId, String matchId, Long seedOverride, String controlledTeamSide) {
        return formationMatrixService.runFormationMatrix(userId, matchId, seedOverride, controlledTeamSide);
    }
    @Override
    public Mono<List<FormationMatrixSummaryRow>> runFormationMatrixSummary(
            UUID userId,
            String matchId,
            long seedStart,
            int seedCount,
            String controlledTeamSide) {
        return formationMatrixService.runFormationMatrixSummary(userId, matchId, seedStart, seedCount, controlledTeamSide);
    }
    @Override
    public Mono<List<SideMirrorSyntheticLabRow>> runSideMirrorSyntheticLab(
            UUID userId,
            long seedStart,
            int seedCount) {
        return formationMatrixService.runSideMirrorSyntheticLab(userId, seedStart, seedCount);
    }
    @Override
    public Mono<List<ScenarioMatrixRow>> runScenarioMatrix(UUID userId, String matchId, Long seedOverride) {
        if (careerSessionService != null) {
            Mono<CareerSave> cached = careerSessionService.getCareerFromCache(userId);
            if (cached != null) {
                return cached.flatMap(career ->
                    scenarioMatrixService.runScenarioMatrix(career, matchId, seedOverride));
            }
        }
        return scenarioMatrixService.runScenarioMatrix(userId, matchId, seedOverride);
    }
    @Override
    public Mono<List<ScenarioMatrixSummaryRow>> runScenarioMatrixSummary(
            UUID userId,
            String matchId,
            long seedStart,
            int seedCount,
            String scenarioGroup,
            String controlledTeamSide) {
        return scenarioMatrixService.runScenarioMatrixSummary(
            userId,
            matchId,
            seedStart,
            seedCount,
            scenarioGroup,
            controlledTeamSide);
    }
    @Override
    public Mono<PlayerSwapMatrixSummaryRow> runPlayerSwapMatrixSummary(
            UUID userId,
            String matchId,
            String starterPlayerId,
            String benchPlayerId,
            String slotId,
            long seedStart,
            int seedCount,
            String controlledTeamSide) {
        return playerSwapService.run(userId, matchId, starterPlayerId, benchPlayerId, slotId, seedStart, seedCount, controlledTeamSide);
    }
    @Override
    public Mono<SubstitutionWhatIfSummaryRow> runSubstitutionWhatIfSummary(
            UUID userId,
            String matchId,
            String playerOffId,
            String playerOnId,
            Integer minute,
            long seedStart,
            int seedCount,
            String controlledTeamSide) {
        return substitutionWhatIfService.run(userId, matchId, playerOffId, playerOnId, minute, seedStart, seedCount, controlledTeamSide);
    }
    @Override
    public Mono<PositionPixelMatrixSummaryRow> runPositionPixelMatrixSummary(
            UUID userId,
            String matchId,
            String playerId,
            Double targetXPercent,
            Double targetYPercent,
            Double deltaXPercent,
            Double deltaYPercent,
            long seedStart,
            int seedCount,
            String controlledTeamSide) {
        return positionPixelService.run(userId, matchId, playerId, targetXPercent, targetYPercent, deltaXPercent, deltaYPercent, seedStart, seedCount, controlledTeamSide);
    }
    public Mono<PositionPixelMatrixSummaryRow> runPositionPixelMatrixSummary(
            UUID userId,
            String matchId,
            String playerId,
            Double targetXPercent,
            Double targetYPercent,
            Double deltaXPercent,
            Double deltaYPercent,
            long seedStart,
            int seedCount) {
        return runPositionPixelMatrixSummary(
            userId,
            matchId,
            playerId,
            targetXPercent,
            targetYPercent,
            deltaXPercent,
            deltaYPercent,
            seedStart,
            seedCount,
            null);
    }
    @Override
    public Mono<List<RoleSlotImpactSummaryRow>> runRoleSlotImpactSummary(
            UUID userId,
            String matchId,
            String slotId,
            List<String> naturalPositions,
            long seedStart,
            int seedCount,
            String controlledTeamSide) {
        return roleSlotImpactService.run(userId, matchId, slotId, naturalPositions, seedStart, seedCount, controlledTeamSide);
    }
    @Override
    public Mono<Void> resetRound(UUID userId, String roundId) {
        return adminCommandService.resetRound(userId, roundId);
    }
}

