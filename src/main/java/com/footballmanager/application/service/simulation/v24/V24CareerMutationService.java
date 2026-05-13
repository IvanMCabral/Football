package com.footballmanager.application.service.simulation.v24;

import com.footballmanager.domain.model.entity.CareerSave;

/**
 * Pure orchestration service that coordinates V24 career mutations after a match result.
 *
 * <p>V24D6C2 scope: injury + fatigue mutation. Discipline and form are not implemented.
 *
 * <p>This service is isolated — no Spring, no Redis, no IO.
 * It is not yet wired into LeagueSimulator; it exists as a standalone component.
 */
public class V24CareerMutationService {

    private final V24InjuryMutationApplier injuryMutationApplier;
    private final V24FatigueMutationApplier fatigueMutationApplier;

    public V24CareerMutationService(V24InjuryMutationApplier injuryMutationApplier) {
        this(injuryMutationApplier, new V24FatigueMutationApplier());
    }

    public V24CareerMutationService(
            V24InjuryMutationApplier injuryMutationApplier,
            V24FatigueMutationApplier fatigueMutationApplier) {
        this.injuryMutationApplier = injuryMutationApplier;
        this.fatigueMutationApplier = fatigueMutationApplier != null
                ? fatigueMutationApplier
                : new V24FatigueMutationApplier();
    }

    /**
     * Apply career mutations from V24 match result to CareerSave.
     *
     * @param career the CareerSave to mutate; if null, returns empty result
     * @param result the V24DetailedMatchResult; if null, returns empty result
     * @param policy the mutation policy; if null, returns empty result
     * @return mutation result with counts and any failures
     */
    public V24CareerMutationResult applyMutations(
            CareerSave career,
            V24DetailedMatchResult result,
            V24CareerMutationPolicy policy) {

        if (career == null) return V24CareerMutationResult.empty();
        if (result == null) return V24CareerMutationResult.empty();
        if (policy == null) return V24CareerMutationResult.empty();
        if (!policy.isCareerMutationEnabled()) return V24CareerMutationResult.empty();

        int injuries = 0;
        int fatigue = 0;
        java.util.List<String> failures = new java.util.ArrayList<>();

        if (policy.isInjuryPersistenceEnabled()) {
            try {
                injuries = injuryMutationApplier.applyInjuries(career, result, policy);
            } catch (Exception e) {
                failures.add("Injury mutation failed: " + e.getMessage());
            }
        }

        if (policy.isFatiguePersistenceEnabled()) {
            try {
                fatigue = fatigueMutationApplier.applyFatigue(career, result, policy);
            } catch (Exception e) {
                failures.add("Fatigue mutation failed: " + e.getMessage());
            }
        }

        if (!failures.isEmpty()) {
            return V24CareerMutationResult.failure(injuries, fatigue, failures, injuries > 0 || fatigue > 0);
        }

        if (injuries == 0 && fatigue == 0) {
            return V24CareerMutationResult.empty();
        }

        return V24CareerMutationResult.success(injuries, fatigue, 0, 0);
    }
}