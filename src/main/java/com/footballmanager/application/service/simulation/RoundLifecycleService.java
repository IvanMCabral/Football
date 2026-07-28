package com.footballmanager.application.service.simulation;

import com.footballmanager.application.service.simulation.detailed.CareerMutationPolicy;
import com.footballmanager.application.service.simulation.detailed.EnergyRecoveryLifecycleApplier;
import com.footballmanager.application.service.simulation.detailed.InjuryRecoveryLifecycleApplier;
import com.footballmanager.application.service.simulation.detailed.SuspensionLifecycleApplier;
import com.footballmanager.domain.model.entity.CareerSave;
import com.footballmanager.domain.model.valueobject.MatchFixture;
import org.slf4j.Logger;

import java.util.List;
import java.util.Set;

final class RoundLifecycleService {

    private final CareerMutationPolicy mutationPolicy;
    private final SuspensionLifecycleApplier suspensionLifecycleApplier;
    private final InjuryRecoveryLifecycleApplier injuryRecoveryLifecycleApplier;
    private final EnergyRecoveryLifecycleApplier energyRecoveryLifecycleApplier;
    private final LiveMatchLifecycleService liveLifecycleService;
    private final Logger log;

    RoundLifecycleService(
            CareerMutationPolicy mutationPolicy,
            SuspensionLifecycleApplier suspensionLifecycleApplier,
            InjuryRecoveryLifecycleApplier injuryRecoveryLifecycleApplier,
            EnergyRecoveryLifecycleApplier energyRecoveryLifecycleApplier,
            LiveMatchLifecycleService liveLifecycleService,
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
            RoundMutationTracking tracking,
            Set<String> preRoundInjuredPlayerIds) {
        applySuspensionLifecycle(career, round, allFixtures, tracking);
        applyInjuryRecoveryLifecycle(career, round, allFixtures, tracking, preRoundInjuredPlayerIds);
        applyEnergyRecoveryLifecycle(career, tracking);
    }

    private void applySuspensionLifecycle(
            CareerSave career,
            int round,
            List<MatchFixture> allFixtures,
            RoundMutationTracking tracking) {
        try {
            if (!tracking.detailedRoundProcessed || !mutationPolicy.isDisciplinePersistenceEnabled()) return;
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
            RoundMutationTracking tracking,
            Set<String> preRoundInjuredPlayerIds) {
        try {
            if (!tracking.detailedRoundProcessed || !mutationPolicy.isInjuryPersistenceEnabled()) return;
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

    private void applyEnergyRecoveryLifecycle(CareerSave career, RoundMutationTracking tracking) {
        try {
            if (!tracking.detailedRoundProcessed || !mutationPolicy.isFatiguePersistenceEnabled()) return;
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
