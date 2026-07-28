package com.footballmanager.adapters.in.web.testharness;

import com.footballmanager.adapters.in.web.common.ControllerHelper;
import com.footballmanager.domain.port.in.testharness.TestHarnessUseCase;
import com.footballmanager.domain.port.in.testharness.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/test-harness/career")
@Profile({"dev", "local", "test"})
@CrossOrigin(origins = "*", maxAge = 3600)
@RequiredArgsConstructor
class TestHarnessLabsController {

    private final TestHarnessUseCase testHarnessUseCase;
    private final ControllerHelper controllerHelper;

    @PostMapping("/labs/offensive-upgrade/prepare")
    Mono<ResponseEntity<LabMutationResult>> prepareOffensiveUpgradeLab(
            Authentication authentication) {
        return ok(authentication, testHarnessUseCase::prepareOffensiveUpgradeLab);
    }

    @PostMapping("/labs/offensive-upgrade/restore")
    Mono<ResponseEntity<LabMutationResult>> restoreOffensiveUpgradeLab(
            Authentication authentication) {
        return ok(authentication, testHarnessUseCase::restoreOffensiveUpgradeLab);
    }

    @PostMapping("/labs/defensive-downgrade/prepare")
    Mono<ResponseEntity<LabMutationResult>> prepareDefensiveDowngradeLab(
            Authentication authentication) {
        return ok(authentication, testHarnessUseCase::prepareDefensiveDowngradeLab);
    }

    @PostMapping("/labs/defensive-downgrade/restore")
    Mono<ResponseEntity<LabMutationResult>> restoreDefensiveDowngradeLab(
            Authentication authentication) {
        return ok(authentication, testHarnessUseCase::restoreDefensiveDowngradeLab);
    }

    @PostMapping("/labs/objective-contrast/prepare")
    Mono<ResponseEntity<LabMutationResult>> prepareObjectiveContrastLab(
            Authentication authentication) {
        return ok(authentication, testHarnessUseCase::prepareObjectiveContrastLab);
    }

    @PostMapping("/labs/objective-contrast/restore")
    Mono<ResponseEntity<LabMutationResult>> restoreObjectiveContrastLab(
            Authentication authentication) {
        return ok(authentication, testHarnessUseCase::restoreObjectiveContrastLab);
    }

    @PostMapping("/labs/weak-wide-defenders/prepare")
    Mono<ResponseEntity<LabMutationResult>> prepareWeakWideDefendersLab(
            Authentication authentication) {
        return ok(authentication, testHarnessUseCase::prepareWeakWideDefendersLab);
    }

    @PostMapping("/labs/weak-wide-defenders/restore")
    Mono<ResponseEntity<LabMutationResult>> restoreWeakWideDefendersLab(
            Authentication authentication) {
        return ok(authentication, testHarnessUseCase::restoreWeakWideDefendersLab);
    }

    @PostMapping("/match/{matchId}/labs/opponent-weak-wide-defenders/prepare")
    Mono<ResponseEntity<LabMutationResult>> prepareOpponentWeakWideDefendersLab(
            @PathVariable String matchId,
            Authentication authentication) {
        return ok(authentication, userId ->
            testHarnessUseCase.prepareOpponentWeakWideDefendersLab(userId, matchId));
    }

    @PostMapping("/match/{matchId}/labs/opponent-weak-wide-defenders/restore")
    Mono<ResponseEntity<LabMutationResult>> restoreOpponentWeakWideDefendersLab(
            @PathVariable String matchId,
            Authentication authentication) {
        return ok(authentication, userId ->
            testHarnessUseCase.restoreOpponentWeakWideDefendersLab(userId, matchId));
    }

    @PostMapping("/match/{matchId}/labs/opponent-weak-left-defender/prepare")
    Mono<ResponseEntity<LabMutationResult>> prepareOpponentWeakLeftDefenderLab(
            @PathVariable String matchId,
            Authentication authentication) {
        return ok(authentication, userId ->
            testHarnessUseCase.prepareOpponentWeakLeftDefenderLab(userId, matchId));
    }

    @PostMapping("/match/{matchId}/labs/opponent-weak-left-defender/restore")
    Mono<ResponseEntity<LabMutationResult>> restoreOpponentWeakLeftDefenderLab(
            @PathVariable String matchId,
            Authentication authentication) {
        return ok(authentication, userId ->
            testHarnessUseCase.restoreOpponentWeakLeftDefenderLab(userId, matchId));
    }

    @PostMapping("/match/{matchId}/labs/opponent-weak-right-defender/prepare")
    Mono<ResponseEntity<LabMutationResult>> prepareOpponentWeakRightDefenderLab(
            @PathVariable String matchId,
            Authentication authentication) {
        return ok(authentication, userId ->
            testHarnessUseCase.prepareOpponentWeakRightDefenderLab(userId, matchId));
    }

    @PostMapping("/match/{matchId}/labs/opponent-weak-right-defender/restore")
    Mono<ResponseEntity<LabMutationResult>> restoreOpponentWeakRightDefenderLab(
            @PathVariable String matchId,
            Authentication authentication) {
        return ok(authentication, userId ->
            testHarnessUseCase.restoreOpponentWeakRightDefenderLab(userId, matchId));
    }

    @PostMapping("/match/{matchId}/labs/opponent-weak-center-backs/prepare")
    Mono<ResponseEntity<LabMutationResult>> prepareOpponentWeakCenterBacksLab(
            @PathVariable String matchId,
            Authentication authentication) {
        return ok(authentication, userId ->
            testHarnessUseCase.prepareOpponentWeakCenterBacksLab(userId, matchId));
    }

    @PostMapping("/match/{matchId}/labs/opponent-weak-center-backs/restore")
    Mono<ResponseEntity<LabMutationResult>> restoreOpponentWeakCenterBacksLab(
            @PathVariable String matchId,
            Authentication authentication) {
        return ok(authentication, userId ->
            testHarnessUseCase.restoreOpponentWeakCenterBacksLab(userId, matchId));
    }

    @PostMapping("/labs/weak-left-defender/prepare")
    Mono<ResponseEntity<LabMutationResult>> prepareWeakLeftDefenderLab(
            Authentication authentication) {
        return ok(authentication, testHarnessUseCase::prepareWeakLeftDefenderLab);
    }

    @PostMapping("/labs/weak-left-defender/restore")
    Mono<ResponseEntity<LabMutationResult>> restoreWeakLeftDefenderLab(
            Authentication authentication) {
        return ok(authentication, testHarnessUseCase::restoreWeakLeftDefenderLab);
    }

    @PostMapping("/labs/weak-right-defender/prepare")
    Mono<ResponseEntity<LabMutationResult>> prepareWeakRightDefenderLab(
            Authentication authentication) {
        return ok(authentication, testHarnessUseCase::prepareWeakRightDefenderLab);
    }

    @PostMapping("/labs/weak-right-defender/restore")
    Mono<ResponseEntity<LabMutationResult>> restoreWeakRightDefenderLab(
            Authentication authentication) {
        return ok(authentication, testHarnessUseCase::restoreWeakRightDefenderLab);
    }

    @PostMapping("/labs/weak-center-backs/prepare")
    Mono<ResponseEntity<LabMutationResult>> prepareWeakCenterBacksLab(
            Authentication authentication) {
        return ok(authentication, testHarnessUseCase::prepareWeakCenterBacksLab);
    }

    @PostMapping("/labs/weak-center-backs/restore")
    Mono<ResponseEntity<LabMutationResult>> restoreWeakCenterBacksLab(
            Authentication authentication) {
        return ok(authentication, testHarnessUseCase::restoreWeakCenterBacksLab);
    }

    private Mono<ResponseEntity<LabMutationResult>> ok(
            Authentication authentication,
            LabCall call) {
        UUID userId = controllerHelper.getUserId(authentication);
        return call.execute(userId).map(ResponseEntity::ok);
    }

    @FunctionalInterface
    private interface LabCall {
        Mono<LabMutationResult> execute(UUID userId);
    }
}
