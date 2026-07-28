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
class TestHarnessDefenderLabService {

    private final TestHarnessWideDefenderLabService wideDefenderLabService;
    private final TestHarnessChannelDefenderLabService channelDefenderLabService;

    public Mono<LabMutationResult> prepareWeakWideDefendersLab(UUID userId) {
        return wideDefenderLabService.prepareWeakWideDefendersLab(userId);
    }

    public Mono<LabMutationResult> restoreWeakWideDefendersLab(UUID userId) {
        return wideDefenderLabService.restoreWeakWideDefendersLab(userId);
    }

    public Mono<LabMutationResult> prepareOpponentWeakWideDefendersLab(UUID userId, String matchId) {
        return wideDefenderLabService.prepareOpponentWeakWideDefendersLab(userId, matchId);
    }

    public Mono<LabMutationResult> restoreOpponentWeakWideDefendersLab(UUID userId, String matchId) {
        return wideDefenderLabService.restoreOpponentWeakWideDefendersLab(userId, matchId);
    }

    public Mono<LabMutationResult> prepareOpponentWeakLeftDefenderLab(UUID userId, String matchId) {
        return channelDefenderLabService.prepareOpponentWeakLeftDefenderLab(userId, matchId);
    }

    public Mono<LabMutationResult> restoreOpponentWeakLeftDefenderLab(UUID userId, String matchId) {
        return channelDefenderLabService.restoreOpponentWeakLeftDefenderLab(userId, matchId);
    }

    public Mono<LabMutationResult> prepareOpponentWeakRightDefenderLab(UUID userId, String matchId) {
        return channelDefenderLabService.prepareOpponentWeakRightDefenderLab(userId, matchId);
    }

    public Mono<LabMutationResult> restoreOpponentWeakRightDefenderLab(UUID userId, String matchId) {
        return channelDefenderLabService.restoreOpponentWeakRightDefenderLab(userId, matchId);
    }

    public Mono<LabMutationResult> prepareOpponentWeakCenterBacksLab(UUID userId, String matchId) {
        return channelDefenderLabService.prepareOpponentWeakCenterBacksLab(userId, matchId);
    }

    public Mono<LabMutationResult> restoreOpponentWeakCenterBacksLab(UUID userId, String matchId) {
        return channelDefenderLabService.restoreOpponentWeakCenterBacksLab(userId, matchId);
    }

    public Mono<LabMutationResult> prepareWeakLeftDefenderLab(UUID userId) {
        return channelDefenderLabService.prepareWeakLeftDefenderLab(userId);
    }

    public Mono<LabMutationResult> restoreWeakLeftDefenderLab(UUID userId) {
        return channelDefenderLabService.restoreWeakLeftDefenderLab(userId);
    }

    public Mono<LabMutationResult> prepareWeakRightDefenderLab(UUID userId) {
        return channelDefenderLabService.prepareWeakRightDefenderLab(userId);
    }

    public Mono<LabMutationResult> restoreWeakRightDefenderLab(UUID userId) {
        return channelDefenderLabService.restoreWeakRightDefenderLab(userId);
    }

    public Mono<LabMutationResult> prepareWeakCenterBacksLab(UUID userId) {
        return channelDefenderLabService.prepareWeakCenterBacksLab(userId);
    }

    public Mono<LabMutationResult> restoreWeakCenterBacksLab(UUID userId) {
        return channelDefenderLabService.restoreWeakCenterBacksLab(userId);
    }

}
