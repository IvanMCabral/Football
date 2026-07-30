package com.footballmanager.application.service.simulation;

import com.footballmanager.application.service.simulation.detailed.CareerMutationPolicy;
import com.footballmanager.application.service.simulation.detailed.CareerMutationResult;
import com.footballmanager.application.service.simulation.detailed.CareerMutationService;
import com.footballmanager.application.service.simulation.detailed.DetailedMatchEvent;
import com.footballmanager.application.service.simulation.detailed.DetailedMatchEventType;
import com.footballmanager.application.service.simulation.detailed.DetailedMatchResult;
import com.footballmanager.application.service.simulation.detailed.MatchContext;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionPlayer;
import org.slf4j.Logger;

import java.util.Set;

final class CareerMutationCoordinator {

    private final CareerMutationService careerMutationService;
    private final CareerMutationPolicy careerMutationPolicy;
    private final LiveMatchLifecycleService liveLifecycleService;
    private final Logger log;

    CareerMutationCoordinator(
            CareerMutationService careerMutationService,
            CareerMutationPolicy careerMutationPolicy,
            LiveMatchLifecycleService liveLifecycleService,
            Logger log) {
        this.careerMutationService = careerMutationService;
        this.careerMutationPolicy = careerMutationPolicy;
        this.liveLifecycleService = liveLifecycleService;
        this.log = log;
    }

    void collectStartingXIParticipation(MatchContext context, RoundMutationTracking tracking) {
        for (SessionPlayer player : context.homeStartingPlayers()) {
            collectAvailablePlayer(player, tracking);
        }
        for (SessionPlayer player : context.awayStartingPlayers()) {
            collectAvailablePlayer(player, tracking);
        }
    }

    void collectDetailedResultParticipation(DetailedMatchResult detailedResult, RoundMutationTracking tracking) {
        if (detailedResult == null || detailedResult.timeline() == null) {
            return;
        }
        for (DetailedMatchEvent event : detailedResult.timeline().events()) {
            if (event.playerId() != null && !event.playerId().isBlank()) {
                tracking.participatedPlayerIds.add(event.playerId());
            }
            if (event.relatedPlayerId() != null && !event.relatedPlayerId().isBlank()) {
                tracking.participatedPlayerIds.add(event.relatedPlayerId());
            }
            if (event.type() == DetailedMatchEventType.RED_CARD
                    && event.playerId() != null
                    && !event.playerId().isBlank()) {
                tracking.newlySuspendedPlayerIds.add(event.playerId());
            }
        }
    }

    void applyDetailedCareerMutation(
            CareerSave career,
            DetailedMatchResult detailedResult,
            RoundMutationTracking tracking) {
        try {
            Set<String> preMutationSuspended = liveLifecycleService.capturePreRoundSuspendedPlayerIds(career);
            Set<String> preMutationInjured = liveLifecycleService.capturePreRoundInjuredPlayerIds(career);
            CareerMutationResult mutationResult =
                    careerMutationService.applyMutations(career, detailedResult, careerMutationPolicy);
            logMutationSummary(career, mutationResult);
            captureNewSuspensions(career, tracking, preMutationSuspended);
            captureNewInjuries(career, tracking, preMutationInjured);
        } catch (Exception e) {
            log.warn("Career mutation failed unexpectedly for career {}: {}, continuing round",
                    career.getData().getCareerId(), e.getMessage());
        }
    }

    private void collectAvailablePlayer(SessionPlayer player, RoundMutationTracking tracking) {
        if (player != null
                && player.getSessionPlayerId() != null
                && !Boolean.TRUE.equals(player.getSuspended())) {
            tracking.participatedPlayerIds.add(player.getSessionPlayerId());
        }
    }

    private void logMutationSummary(CareerSave career, CareerMutationResult mutationResult) {
        if (!mutationResult.failures().isEmpty()) {
            log.warn("Career mutation partial failures for career {}: {}",
                    career.getData().getCareerId(), mutationResult.failures());
        }
        if (mutationResult.injuriesApplied() > 0) {
            log.debug("Applied {} injury mutations for career {}",
                    mutationResult.injuriesApplied(), career.getData().getCareerId());
        }
        if (mutationResult.fatigueApplied() > 0) {
            log.debug("Applied {} fatigue mutations for career {}",
                    mutationResult.fatigueApplied(), career.getData().getCareerId());
        }
        if (mutationResult.disciplineApplied() > 0) {
            log.debug("Applied {} discipline mutations for career {}",
                    mutationResult.disciplineApplied(), career.getData().getCareerId());
        }
    }

    private void captureNewSuspensions(
            CareerSave career,
            RoundMutationTracking tracking,
            Set<String> preMutationSuspended) {
        if (!careerMutationPolicy.isDisciplinePersistenceEnabled()) {
            return;
        }
        Set<String> postMutationSuspended = liveLifecycleService.capturePreRoundSuspendedPlayerIds(career);
        postMutationSuspended.removeAll(preMutationSuspended);
        if (!postMutationSuspended.isEmpty()) {
            tracking.newlySuspendedPlayerIds.addAll(postMutationSuspended);
            log.debug("Newly suspended from mutation: {}", postMutationSuspended);
        }
    }

    private void captureNewInjuries(
            CareerSave career,
            RoundMutationTracking tracking,
            Set<String> preMutationInjured) {
        if (!careerMutationPolicy.isInjuryPersistenceEnabled()) {
            return;
        }
        Set<String> postMutationInjured = liveLifecycleService.capturePreRoundInjuredPlayerIds(career);
        postMutationInjured.removeAll(preMutationInjured);
        if (!postMutationInjured.isEmpty()) {
            tracking.newlyInjuredPlayerIds.addAll(postMutationInjured);
            log.debug("Newly injured from mutation: {}", postMutationInjured);
        }
    }
}
