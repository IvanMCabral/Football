package com.footballmanager.application.service.testharness;

import com.footballmanager.domain.port.in.testharness.LabMutationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
@Profile({"dev", "local", "test"})
@RequiredArgsConstructor
class TestHarnessLabService {

    private final TestHarnessOffensiveLabService offensiveLabService;
    private final TestHarnessObjectiveLabService objectiveLabService;
    private final TestHarnessDefensiveDowngradeLabService defensiveDowngradeLabService;
    private final TestHarnessDefenderLabService defenderLabService;

    public Mono<LabMutationResult> prepareOffensiveUpgradeLab(UUID userId) {
        return offensiveLabService.prepareOffensiveUpgradeLab(userId);
    }

    public Mono<LabMutationResult> restoreOffensiveUpgradeLab(UUID userId) {
        return offensiveLabService.restoreOffensiveUpgradeLab(userId);
    }

    public Mono<LabMutationResult> prepareObjectiveContrastLab(UUID userId) {
        return objectiveLabService.prepareObjectiveContrastLab(userId);
    }

    public Mono<LabMutationResult> restoreObjectiveContrastLab(UUID userId) {
        return objectiveLabService.restoreObjectiveContrastLab(userId);
    }

    public Mono<LabMutationResult> prepareDefensiveDowngradeLab(UUID userId) {
        return defensiveDowngradeLabService.prepareDefensiveDowngradeLab(userId);
    }

    public Mono<LabMutationResult> restoreDefensiveDowngradeLab(UUID userId) {
        return defensiveDowngradeLabService.restoreDefensiveDowngradeLab(userId);
    }

    public Mono<LabMutationResult> prepareWeakWideDefendersLab(UUID userId) {
        return defenderLabService.prepareWeakWideDefendersLab(userId);
    }

    public Mono<LabMutationResult> restoreWeakWideDefendersLab(UUID userId) {
        return defenderLabService.restoreWeakWideDefendersLab(userId);
    }

    public Mono<LabMutationResult> prepareOpponentWeakWideDefendersLab(UUID userId, String matchId) {
        return defenderLabService.prepareOpponentWeakWideDefendersLab(userId, matchId);
    }

    public Mono<LabMutationResult> restoreOpponentWeakWideDefendersLab(UUID userId, String matchId) {
        return defenderLabService.restoreOpponentWeakWideDefendersLab(userId, matchId);
    }

    public Mono<LabMutationResult> prepareOpponentWeakLeftDefenderLab(UUID userId, String matchId) {
        return defenderLabService.prepareOpponentWeakLeftDefenderLab(userId, matchId);
    }

    public Mono<LabMutationResult> restoreOpponentWeakLeftDefenderLab(UUID userId, String matchId) {
        return defenderLabService.restoreOpponentWeakLeftDefenderLab(userId, matchId);
    }

    public Mono<LabMutationResult> prepareOpponentWeakRightDefenderLab(UUID userId, String matchId) {
        return defenderLabService.prepareOpponentWeakRightDefenderLab(userId, matchId);
    }

    public Mono<LabMutationResult> restoreOpponentWeakRightDefenderLab(UUID userId, String matchId) {
        return defenderLabService.restoreOpponentWeakRightDefenderLab(userId, matchId);
    }

    public Mono<LabMutationResult> prepareOpponentWeakCenterBacksLab(UUID userId, String matchId) {
        return defenderLabService.prepareOpponentWeakCenterBacksLab(userId, matchId);
    }

    public Mono<LabMutationResult> restoreOpponentWeakCenterBacksLab(UUID userId, String matchId) {
        return defenderLabService.restoreOpponentWeakCenterBacksLab(userId, matchId);
    }

    public Mono<LabMutationResult> prepareWeakLeftDefenderLab(UUID userId) {
        return defenderLabService.prepareWeakLeftDefenderLab(userId);
    }

    public Mono<LabMutationResult> restoreWeakLeftDefenderLab(UUID userId) {
        return defenderLabService.restoreWeakLeftDefenderLab(userId);
    }

    public Mono<LabMutationResult> prepareWeakRightDefenderLab(UUID userId) {
        return defenderLabService.prepareWeakRightDefenderLab(userId);
    }

    public Mono<LabMutationResult> restoreWeakRightDefenderLab(UUID userId) {
        return defenderLabService.restoreWeakRightDefenderLab(userId);
    }

    public Mono<LabMutationResult> prepareWeakCenterBacksLab(UUID userId) {
        return defenderLabService.prepareWeakCenterBacksLab(userId);
    }

    public Mono<LabMutationResult> restoreWeakCenterBacksLab(UUID userId) {
        return defenderLabService.restoreWeakCenterBacksLab(userId);
    }

}
