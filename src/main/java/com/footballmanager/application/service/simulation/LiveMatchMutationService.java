package com.footballmanager.application.service.simulation;

import com.footballmanager.application.service.simulation.detailed.LiveRoundMutationTracking;
import com.footballmanager.application.service.simulation.detailed.CareerMutationPolicy;
import com.footballmanager.application.service.simulation.detailed.CareerMutationResult;
import com.footballmanager.application.service.simulation.detailed.CareerMutationService;
import com.footballmanager.application.service.simulation.detailed.DetailedMatchResult;
import com.footballmanager.application.service.simulation.detailed.DetailedMatchEvent;
import com.footballmanager.domain.model.entity.CareerSave;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.Set;

final class LiveMatchMutationService {

    private final CareerMutationService mutationService;
    private final CareerMutationPolicy mutationPolicy;
    private final LiveMatchLifecycleService lifecycleService;
    private final Logger log;

    LiveMatchMutationService(
            CareerMutationService mutationService,
            CareerMutationPolicy mutationPolicy,
            LiveMatchLifecycleService lifecycleService,
            Logger log) {
        this.mutationService = mutationService;
        this.mutationPolicy = mutationPolicy;
        this.lifecycleService = lifecycleService;
        this.log = log;
    }

    void apply(CareerSave career, DetailedMatchResult result, LiveRoundMutationTracking tracking) {
        if (career == null || result == null) {
            return;
        }
        if (!mutationPolicy.isCareerMutationEnabled()) {
            log.debug("Skipped for match {}: mutate-career-state=false", result.matchId());
            return;
        }
        Set<String> preSuspended = tracking != null
                ? new HashSet<>(lifecycleService.capturePreRoundSuspendedPlayerIds(career))
                : null;
        Set<String> preInjured = tracking != null
                ? new HashSet<>(lifecycleService.capturePreRoundInjuredPlayerIds(career))
                : null;
        try {
            CareerMutationResult mutationResult =
                    mutationService.applyMutations(career, result, mutationPolicy);
            logMutationResult(career, result, mutationResult);
            updateTracking(career, result, tracking, preSuspended, preInjured);
        } catch (Exception e) {
            log.warn("Failed for match {}: {}, continuing", result.matchId(), e.getMessage());
        }
    }

    private void logMutationResult(
            CareerSave career,
            DetailedMatchResult result,
            CareerMutationResult mutationResult) {
        if (!mutationResult.failures().isEmpty()) {
            log.warn("Partial failures for match {}: {}", result.matchId(), mutationResult.failures());
        }
        int total = mutationResult.injuriesApplied()
                + mutationResult.fatigueApplied()
                + mutationResult.disciplineApplied()
                + mutationResult.formApplied();
        if (total > 0) {
            log.info("careerId={}, matchId={}, injuriesApplied={}, fatigueApplied={}, disciplineApplied={}, formApplied={}, totalMutations={}",
                    career.getData().getCareerId(), result.matchId(),
                    mutationResult.injuriesApplied(), mutationResult.fatigueApplied(),
                    mutationResult.disciplineApplied(), mutationResult.formApplied(), total);
        } else {
            log.debug("careerId={}, matchId={}, no mutations applied (no qualifying events)",
                    career.getData().getCareerId(), result.matchId());
        }
    }

    private void updateTracking(
            CareerSave career,
            DetailedMatchResult result,
            LiveRoundMutationTracking tracking,
            Set<String> preSuspended,
            Set<String> preInjured) {
        if (tracking == null) {
            return;
        }
        Set<String> currentlySuspended = lifecycleService.capturePreRoundSuspendedPlayerIds(career);
        if (result.timeline() != null) {
            for (DetailedMatchEvent event : result.timeline().events()) {
                addParticipantIfEligible(tracking, currentlySuspended, event.playerId());
                addParticipantIfEligible(tracking, currentlySuspended, event.relatedPlayerId());
            }
        }
        addNewSuspensions(career, tracking, preSuspended);
        addNewInjuries(career, tracking, preInjured);
    }

    private void addParticipantIfEligible(
            LiveRoundMutationTracking tracking,
            Set<String> currentlySuspended,
            String playerId) {
        if (playerId != null && !playerId.isBlank() && !currentlySuspended.contains(playerId)) {
            tracking.participatedPlayerIds.add(playerId);
        }
    }

    private void addNewSuspensions(
            CareerSave career,
            LiveRoundMutationTracking tracking,
            Set<String> preSuspended) {
        if (preSuspended == null) {
            return;
        }
        Set<String> postSuspended = lifecycleService.capturePreRoundSuspendedPlayerIds(career);
        postSuspended.removeAll(preSuspended);
        tracking.newlySuspendedPlayerIds.addAll(postSuspended);
    }

    private void addNewInjuries(
            CareerSave career,
            LiveRoundMutationTracking tracking,
            Set<String> preInjured) {
        if (preInjured == null) {
            return;
        }
        Set<String> postInjured = lifecycleService.capturePreRoundInjuredPlayerIds(career);
        postInjured.removeAll(preInjured);
        tracking.newlyInjuredPlayerIds.addAll(postInjured);
    }
}
