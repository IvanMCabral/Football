package com.footballmanager.application.service.testharness;

import com.footballmanager.application.engine.match.MatchEngineRegistry;
import com.footballmanager.application.service.career.CareerSessionService;
import com.footballmanager.application.service.simulation.detailed.BaselineStateStoragePort;
import com.footballmanager.application.service.simulation.detailed.DetailedMatchStoragePort;
import com.footballmanager.application.service.simulation.detailed.MatchContextFactory;
import com.footballmanager.domain.model.repository.CareerRepository;

final class TestHarnessUseCaseDependencyFactory {

    private TestHarnessUseCaseDependencyFactory() {
    }

    static TestHarnessPreviewRunner previewRunner(MatchContextFactory contextFactory) {
        return new TestHarnessPreviewRunner(contextFactory);
    }

    static TestHarnessLabService labService(
            CareerRepository careerRepository,
            CareerSessionService careerSessionService) {
        TestHarnessDefenderChannelSupport support =
            new TestHarnessDefenderChannelSupport(careerRepository, careerSessionService);
        return new TestHarnessLabService(
            new TestHarnessOffensiveLabService(careerRepository, careerSessionService),
            new TestHarnessObjectiveLabService(careerRepository, careerSessionService),
            new TestHarnessDefensiveDowngradeLabService(careerRepository, careerSessionService),
            new TestHarnessDefenderLabService(
                new TestHarnessWideDefenderLabService(careerRepository, careerSessionService),
                new TestHarnessChannelDefenderLabService(
                    new TestHarnessOpponentDefenderChannelLabService(support),
                    new TestHarnessUserDefenderChannelLabService(support))));
    }

    static TestHarnessAdminCommandService adminService(
            CareerRepository careerRepository,
            CareerSessionService careerSessionService,
            DetailedMatchStoragePort storagePort,
            MatchEngineRegistry matchEngineRegistry) {
        return new TestHarnessAdminCommandService(
            careerRepository,
            careerSessionService,
            storagePort,
            matchEngineRegistry);
    }

    static TestHarnessLineupDiagnosticService lineupDiagnosticService(
            CareerRepository careerRepository,
            MatchContextFactory contextFactory) {
        return new TestHarnessLineupDiagnosticService(careerRepository, contextFactory);
    }

    static TestHarnessFormationMatrixService formationMatrixService(
            CareerRepository careerRepository,
            CareerSessionService careerSessionService,
            MatchContextFactory contextFactory,
            BaselineStateStoragePort baselineStoragePort,
            DetailedMatchStoragePort storagePort) {
        return new TestHarnessFormationMatrixService(
            careerRepository,
            careerSessionService,
            contextFactory,
            baselineStoragePort,
            storagePort,
            new TestHarnessSideMirrorSyntheticLabService());
    }

    static TestHarnessReplayService replayService(
            CareerRepository careerRepository,
            CareerSessionService careerSessionService,
            MatchContextFactory contextFactory,
            BaselineStateStoragePort baselineStoragePort,
            DetailedMatchStoragePort storagePort) {
        return new TestHarnessReplayService(
            careerRepository,
            careerSessionService,
            contextFactory,
            baselineStoragePort,
            storagePort);
    }
}
