package com.footballmanager.application.service.simulation;

import com.footballmanager.application.service.simulation.v24.LiveRoundMutationTracking;
import com.footballmanager.application.service.simulation.v24.V24CareerMutationPolicy;
import com.footballmanager.application.service.simulation.v24.V24EnergyRecoveryLifecycleApplier;
import com.footballmanager.application.service.simulation.v24.V24InjuryRecoveryLifecycleApplier;
import com.footballmanager.application.service.simulation.v24.V24SuspensionLifecycleApplier;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.valueobject.MatchFixture;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class V24LiveLifecycleService {

    private final V24CareerMutationPolicy mutationPolicy;
    private final V24SuspensionLifecycleApplier suspensionLifecycleApplier = new V24SuspensionLifecycleApplier();
    private final V24InjuryRecoveryLifecycleApplier injuryRecoveryLifecycleApplier = new V24InjuryRecoveryLifecycleApplier();
    private final V24EnergyRecoveryLifecycleApplier energyRecoveryLifecycleApplier = new V24EnergyRecoveryLifecycleApplier();
    private final Logger log;

    V24LiveLifecycleService(V24CareerMutationPolicy mutationPolicy, Logger log) {
        this.mutationPolicy = mutationPolicy;
        this.log = log;
    }

    Set<String> capturePreRoundSuspendedPlayerIds(CareerSave career) {
        Set<String> suspended = new HashSet<>();
        for (SessionTeam team : career.getAllSessionTeams()) {
            for (String playerId : career.getSquadPlayerIds(team.getSessionTeamId())) {
                SessionPlayer player = career.getSessionPlayer(playerId);
                if (player == null) continue;
                if (Boolean.TRUE.equals(player.getSuspended())) {
                    Integer remaining = player.getSuspensionRemainingMatches();
                    if (remaining != null && remaining > 0) {
                        suspended.add(playerId);
                    }
                }
            }
        }
        return suspended;
    }

    Set<String> capturePreRoundInjuredPlayerIds(CareerSave career) {
        Set<String> injured = new HashSet<>();
        for (SessionTeam team : career.getAllSessionTeams()) {
            for (String playerId : career.getSquadPlayerIds(team.getSessionTeamId())) {
                SessionPlayer player = career.getSessionPlayer(playerId);
                if (player == null) continue;
                if (Boolean.TRUE.equals(player.getInjured())) {
                    Integer remaining = player.getInjuryRemainingMatches();
                    if (remaining != null && remaining > 0) {
                        injured.add(playerId);
                    }
                }
            }
        }
        return injured;
    }

    void applyEndOfRoundLiveLifecycle(
            CareerSave career,
            int currentRound,
            List<MatchFixture> allFixtures,
            LiveRoundMutationTracking tracking) {
        if (career == null || tracking == null) return;
        if (!mutationPolicy.isCareerMutationEnabled()) {
            log.debug("Skipped for careerId={} round={}: mutate-career-state=false",
                    career.getData().getCareerId(), currentRound);
            return;
        }
        try {
            List<MatchFixture> roundFixtures = allFixtures.stream()
                    .filter(f -> f.getRound() == currentRound)
                    .toList();
            applySuspensionRecovery(career, currentRound, roundFixtures, tracking);
            applyInjuryRecovery(career, currentRound, roundFixtures, tracking);
            applyEnergyRecovery(career, currentRound, tracking);
        } catch (Exception e) {
            log.warn("Failed for careerId={} round={}: {}, continuing",
                    career.getData().getCareerId(), currentRound, e.getMessage());
        }
    }

    private void applySuspensionRecovery(
            CareerSave career,
            int currentRound,
            List<MatchFixture> roundFixtures,
            LiveRoundMutationTracking tracking) {
        if (!mutationPolicy.isDisciplinePersistenceEnabled()
                || tracking.preRoundSuspendedPlayerIds.isEmpty()) {
            return;
        }
        int served = suspensionLifecycleApplier.applyServedSuspensions(
                career,
                currentRound,
                roundFixtures,
                tracking.preRoundSuspendedPlayerIds,
                tracking.newlySuspendedPlayerIds,
                tracking.participatedPlayerIds,
                mutationPolicy);
        if (served > 0) {
            log.info("careerId={} round={} served {} suspensions",
                    career.getData().getCareerId(), currentRound, served);
        }
    }

    private void applyInjuryRecovery(
            CareerSave career,
            int currentRound,
            List<MatchFixture> roundFixtures,
            LiveRoundMutationTracking tracking) {
        if (!mutationPolicy.isInjuryPersistenceEnabled()
                || tracking.preRoundInjuredPlayerIds.isEmpty()) {
            return;
        }
        int recovered = injuryRecoveryLifecycleApplier.applyRecovery(
                career,
                currentRound,
                roundFixtures,
                tracking.preRoundInjuredPlayerIds,
                tracking.newlyInjuredPlayerIds,
                tracking.participatedPlayerIds,
                mutationPolicy);
        if (recovered > 0) {
            log.info("careerId={} round={} recovered {} injuries",
                    career.getData().getCareerId(), currentRound, recovered);
        }
    }

    private void applyEnergyRecovery(CareerSave career, int currentRound, LiveRoundMutationTracking tracking) {
        if (!mutationPolicy.isFatiguePersistenceEnabled()) {
            return;
        }
        int recoveredEnergy = energyRecoveryLifecycleApplier.applyRecovery(
                career,
                tracking.participatedPlayerIds,
                mutationPolicy);
        if (recoveredEnergy > 0) {
            log.info("careerId={} round={} recovered energy for {} players",
                    career.getData().getCareerId(), currentRound, recoveredEnergy);
        }
    }
}
