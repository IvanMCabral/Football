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
class TestHarnessChannelDefenderLabService {

    private final TestHarnessOpponentDefenderChannelLabService opponentChannelLabService;
    private final TestHarnessUserDefenderChannelLabService userChannelLabService;

    public Mono<LabMutationResult> prepareOpponentWeakLeftDefenderLab(UUID userId, String matchId) {
        return opponentChannelLabService.prepareOpponentWeakLeftDefenderLab(userId, matchId);
    }

    public Mono<LabMutationResult> restoreOpponentWeakLeftDefenderLab(UUID userId, String matchId) {
        return opponentChannelLabService.restoreOpponentWeakLeftDefenderLab(userId, matchId);
    }

    public Mono<LabMutationResult> prepareOpponentWeakRightDefenderLab(UUID userId, String matchId) {
        return opponentChannelLabService.prepareOpponentWeakRightDefenderLab(userId, matchId);
    }

    public Mono<LabMutationResult> restoreOpponentWeakRightDefenderLab(UUID userId, String matchId) {
        return opponentChannelLabService.restoreOpponentWeakRightDefenderLab(userId, matchId);
    }

    public Mono<LabMutationResult> prepareOpponentWeakCenterBacksLab(UUID userId, String matchId) {
        return opponentChannelLabService.prepareOpponentWeakCenterBacksLab(userId, matchId);
    }

    public Mono<LabMutationResult> restoreOpponentWeakCenterBacksLab(UUID userId, String matchId) {
        return opponentChannelLabService.restoreOpponentWeakCenterBacksLab(userId, matchId);
    }

    public Mono<LabMutationResult> prepareWeakLeftDefenderLab(UUID userId) {
        return userChannelLabService.prepareWeakLeftDefenderLab(userId);
    }

    public Mono<LabMutationResult> restoreWeakLeftDefenderLab(UUID userId) {
        return userChannelLabService.restoreWeakLeftDefenderLab(userId);
    }

    public Mono<LabMutationResult> prepareWeakRightDefenderLab(UUID userId) {
        return userChannelLabService.prepareWeakRightDefenderLab(userId);
    }

    public Mono<LabMutationResult> restoreWeakRightDefenderLab(UUID userId) {
        return userChannelLabService.restoreWeakRightDefenderLab(userId);
    }

    public Mono<LabMutationResult> prepareWeakCenterBacksLab(UUID userId) {
        return userChannelLabService.prepareWeakCenterBacksLab(userId);
    }

    public Mono<LabMutationResult> restoreWeakCenterBacksLab(UUID userId) {
        return userChannelLabService.restoreWeakCenterBacksLab(userId);
    }

}
