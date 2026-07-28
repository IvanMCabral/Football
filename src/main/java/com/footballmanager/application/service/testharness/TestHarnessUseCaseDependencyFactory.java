package com.footballmanager.application.service.testharness;

import com.footballmanager.application.engine.match.MatchEngineRegistry;
import com.footballmanager.application.service.career.CareerSessionService;
import com.footballmanager.application.service.simulation.v24.BaselineStateStoragePort;
import com.footballmanager.application.service.simulation.v24.V24DetailedMatchStoragePort;
import com.footballmanager.application.service.simulation.v24.V24MatchContextFactory;
import com.footballmanager.domain.model.repository.CareerRepository;

final class TestHarnessUseCaseDependencyFactory {

    private TestHarnessUseCaseDependencyFactory() {
    }

    static TestHarnessPreviewRunner previewRunner(V24MatchContextFactory contextFactory) {
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
            V24DetailedMatchStoragePort storagePort,
            MatchEngineRegistry matchEngineRegistry) {
        return new TestHarnessAdminCommandService(
            careerRepository,
            careerSessionService,
            storagePort,
            matchEngineRegistry);
    }

    static TestHarnessLineupDiagnosticService lineupDiagnosticService(
            CareerRepository careerRepository,
            V24MatchContextFactory contextFactory) {
        return new TestHarnessLineupDiagnosticService(careerRepository, contextFactory);
    }

    static TestHarnessFormationMatrixService formationMatrixService(
            CareerRepository careerRepository,
            CareerSessionService careerSessionService,
            V24MatchContextFactory contextFactory,
            BaselineStateStoragePort baselineStoragePort,
            V24DetailedMatchStoragePort storagePort) {
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
            V24MatchContextFactory contextFactory,
            BaselineStateStoragePort baselineStoragePort,
            V24DetailedMatchStoragePort storagePort) {
        return new TestHarnessReplayService(
            careerRepository,
            careerSessionService,
            contextFactory,
            baselineStoragePort,
            storagePort);
    }
}
