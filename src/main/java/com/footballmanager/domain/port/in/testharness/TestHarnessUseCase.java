package com.footballmanager.domain.port.in.testharness;
import com.footballmanager.application.service.domain.TeamStyle;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.valueobject.MatchFixture;
import com.footballmanager.domain.model.valueobject.PlayerSkill;
import reactor.core.publisher.Mono;
import java.util.List;
import java.util.Map;
import java.util.UUID;
public interface TestHarnessUseCase {
    Mono<Void> replaceFixtures(UUID userId, List<CustomFixture> fixtures);
    Mono<Void> resetInjuries(UUID userId);
    Mono<Void> setFormation(UUID userId, String formation);
    Mono<Void> setStyle(UUID userId, TeamStyle style);
    Mono<Void> injectPlayerStats(UUID userId, String playerId,
                                 Integer attack, Integer defense,
                                 Integer technique, Integer speed,
                                 Integer stamina, Integer mentality,
                                 Integer heightCm,
                                 Map<PlayerSkill, Integer> skillLevels);
    Mono<CareerSave> createCustom(UUID userId, String worldLeagueId, String worldTeamId,
                                  String difficulty, String gameSpeed, int teamsPerDivision);
    Mono<CareerSave> snapshot(UUID userId);
    Mono<MatchFixture> replayMatch(UUID userId, String matchId, Long seedOverride);
    Mono<MatchPreviewSummary> runMatchPreviewSummary(
        UUID userId,
        String matchId,
        long seedStart,
        int seedCount,
        String controlledTeamSide);
    Mono<List<FormationMatrixRow>> runFormationMatrix(UUID userId, String matchId, Long seedOverride, String controlledTeamSide);
    Mono<List<FormationMatrixSummaryRow>> runFormationMatrixSummary(UUID userId, String matchId, long seedStart, int seedCount, String controlledTeamSide);
    Mono<List<SideMirrorSyntheticLabRow>> runSideMirrorSyntheticLab(UUID userId, long seedStart, int seedCount);
    Mono<List<ScenarioMatrixRow>> runScenarioMatrix(UUID userId, String matchId, Long seedOverride);
    Mono<List<ScenarioMatrixSummaryRow>> runScenarioMatrixSummary(UUID userId, String matchId, long seedStart, int seedCount, String scenarioGroup, String controlledTeamSide);
    Mono<PlayerSwapMatrixSummaryRow> runPlayerSwapMatrixSummary(
        UUID userId,
        String matchId,
        String starterPlayerId,
        String benchPlayerId,
        String slotId,
        long seedStart,
        int seedCount,
        String controlledTeamSide);
    Mono<SubstitutionWhatIfSummaryRow> runSubstitutionWhatIfSummary(
        UUID userId,
        String matchId,
        String playerOffId,
        String playerOnId,
        Integer minute,
        long seedStart,
        int seedCount,
        String controlledTeamSide);
    Mono<PositionPixelMatrixSummaryRow> runPositionPixelMatrixSummary(
        UUID userId,
        String matchId,
        String playerId,
        Double targetXPercent,
        Double targetYPercent,
        Double deltaXPercent,
        Double deltaYPercent,
        long seedStart,
        int seedCount,
        String controlledTeamSide);
    Mono<List<RoleSlotImpactSummaryRow>> runRoleSlotImpactSummary(
        UUID userId,
        String matchId,
        String slotId,
        List<String> naturalPositions,
        long seedStart,
        int seedCount,
        String controlledTeamSide);
    Mono<LineupDiagnostic> lineupDiagnostic(UUID userId, String matchId, Long seedOverride);
    Mono<LabMutationResult> prepareOffensiveUpgradeLab(UUID userId);
    Mono<LabMutationResult> restoreOffensiveUpgradeLab(UUID userId);
    Mono<LabMutationResult> prepareDefensiveDowngradeLab(UUID userId);
    Mono<LabMutationResult> restoreDefensiveDowngradeLab(UUID userId);
    Mono<LabMutationResult> prepareObjectiveContrastLab(UUID userId);
    Mono<LabMutationResult> restoreObjectiveContrastLab(UUID userId);
    Mono<LabMutationResult> prepareWeakWideDefendersLab(UUID userId);
    Mono<LabMutationResult> restoreWeakWideDefendersLab(UUID userId);
    Mono<LabMutationResult> prepareOpponentWeakWideDefendersLab(UUID userId, String matchId);
    Mono<LabMutationResult> restoreOpponentWeakWideDefendersLab(UUID userId, String matchId);
    Mono<LabMutationResult> prepareOpponentWeakLeftDefenderLab(UUID userId, String matchId);
    Mono<LabMutationResult> restoreOpponentWeakLeftDefenderLab(UUID userId, String matchId);
    Mono<LabMutationResult> prepareOpponentWeakRightDefenderLab(UUID userId, String matchId);
    Mono<LabMutationResult> restoreOpponentWeakRightDefenderLab(UUID userId, String matchId);
    Mono<LabMutationResult> prepareOpponentWeakCenterBacksLab(UUID userId, String matchId);
    Mono<LabMutationResult> restoreOpponentWeakCenterBacksLab(UUID userId, String matchId);
    Mono<LabMutationResult> prepareWeakLeftDefenderLab(UUID userId);
    Mono<LabMutationResult> restoreWeakLeftDefenderLab(UUID userId);
    Mono<LabMutationResult> prepareWeakRightDefenderLab(UUID userId);
    Mono<LabMutationResult> restoreWeakRightDefenderLab(UUID userId);
    Mono<LabMutationResult> prepareWeakCenterBacksLab(UUID userId);
    Mono<LabMutationResult> restoreWeakCenterBacksLab(UUID userId);
    Mono<Void> resetRound(UUID userId, String roundId);
}
