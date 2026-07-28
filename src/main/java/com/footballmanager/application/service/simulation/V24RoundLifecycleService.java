package com.footballmanager.application.service.simulation;

import com.footballmanager.application.service.simulation.v24.V24CareerMutationPolicy;
import com.footballmanager.application.service.simulation.v24.V24EnergyRecoveryLifecycleApplier;
import com.footballmanager.application.service.simulation.v24.V24InjuryRecoveryLifecycleApplier;
import com.footballmanager.application.service.simulation.v24.V24SuspensionLifecycleApplier;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.valueobject.MatchFixture;
import org.slf4j.Logger;

import java.util.List;
import java.util.Set;

final class V24RoundLifecycleService {

    private final V24CareerMutationPolicy mutationPolicy;
    private final V24SuspensionLifecycleApplier suspensionLifecycleApplier;
    private final V24InjuryRecoveryLifecycleApplier injuryRecoveryLifecycleApplier;
    private final V24EnergyRecoveryLifecycleApplier energyRecoveryLifecycleApplier;
    private final V24LiveLifecycleService liveLifecycleService;
    private final Logger log;

    V24RoundLifecycleService(
            V24CareerMutationPolicy mutationPolicy,
            V24SuspensionLifecycleApplier suspensionLifecycleApplier,
            V24InjuryRecoveryLifecycleApplier injuryRecoveryLifecycleApplier,
            V24EnergyRecoveryLifecycleApplier energyRecoveryLifecycleApplier,
            V24LiveLifecycleService liveLifecycleService,
            Logger log) {
        this.mutationPolicy = mutationPolicy;
        this.suspensionLifecycleApplier = suspensionLifecycleApplier;
        this.injuryRecoveryLifecycleApplier = injuryRecoveryLifecycleApplier;
        this.energyRecoveryLifecycleApplier = energyRecoveryLifecycleApplier;
        this.liveLifecycleService = liveLifecycleService;
        this.log = log;
    }

    void applyEndOfRound(
            CareerSave career,
            int round,
            List<MatchFixture> allFixtures,
            V24RoundMutationTracking tracking,
            Set<String> preRoundInjuredPlayerIds) {
        applySuspensionLifecycle(career, round, allFixtures, tracking);
        applyInjuryRecoveryLifecycle(career, round, allFixtures, tracking, preRoundInjuredPlayerIds);
        applyEnergyRecoveryLifecycle(career, tracking);
    }

    private void applySuspensionLifecycle(
            CareerSave career,
            int round,
            List<MatchFixture> allFixtures,
            V24RoundMutationTracking tracking) {
        try {
            if (!tracking.v24RoundProcessed || !mutationPolicy.isDisciplinePersistenceEnabled()) return;
            Set<String> preRoundSuspended = liveLifecycleService.capturePreRoundSuspendedPlayerIds(career);
            if (preRoundSuspended.isEmpty()) return;
            List<MatchFixture> roundFixtures = roundFixtures(allFixtures, round);
            int served = suspensionLifecycleApplier.applyServedSuspensions(
                    career,
                    round,
                    roundFixtures,
                    preRoundSuspended,
                    tracking.newlySuspendedPlayerIds,
                    tracking.participatedPlayerIds,
                    mutationPolicy);
            if (served > 0) {
                log.debug("Served {} suspensions for career {} round {}",
                        served, career.getData().getCareerId(), round);
            }
        } catch (Exception e) {
            log.warn("Suspension lifecycle failed unexpectedly for career {} round {}: {}, continuing round",
                    career.getData().getCareerId(), round, e.getMessage());
        }
    }

    private void applyInjuryRecoveryLifecycle(
            CareerSave career,
            int round,
            List<MatchFixture> allFixtures,
            V24RoundMutationTracking tracking,
            Set<String> preRoundInjuredPlayerIds) {
        try {
            if (!tracking.v24RoundProcessed || !mutationPolicy.isInjuryPersistenceEnabled()) return;
            int recovered = injuryRecoveryLifecycleApplier.applyRecovery(
                    career,
                    round,
                    roundFixtures(allFixtures, round),
                    preRoundInjuredPlayerIds,
                    tracking.newlyInjuredPlayerIds,
                    tracking.participatedPlayerIds,
                    mutationPolicy);
            if (recovered > 0) {
                log.debug("Recovered {} injuries for career {} round {}",
                        recovered, career.getData().getCareerId(), round);
            }
        } catch (Exception e) {
            log.warn("Injury recovery lifecycle failed unexpectedly for career {} round {}: {}, continuing round",
                    career.getData().getCareerId(), round, e.getMessage());
        }
    }

    private void applyEnergyRecoveryLifecycle(CareerSave career, V24RoundMutationTracking tracking) {
        try {
            if (!tracking.v24RoundProcessed || !mutationPolicy.isFatiguePersistenceEnabled()) return;
            int recovered = energyRecoveryLifecycleApplier.applyRecovery(
                    career,
                    tracking.participatedPlayerIds,
                    mutationPolicy);
            if (recovered > 0) {
                log.debug("Recovered energy for {} players in career {}",
                        recovered, career.getData().getCareerId());
            }
        } catch (Exception e) {
            log.warn("Energy recovery lifecycle failed unexpectedly for career {}: {}, continuing round",
                    career.getData().getCareerId(), e.getMessage());
        }
    }

    private List<MatchFixture> roundFixtures(List<MatchFixture> allFixtures, int round) {
        return allFixtures.stream().filter(f -> f.getRound() == round).toList();
    }
}
