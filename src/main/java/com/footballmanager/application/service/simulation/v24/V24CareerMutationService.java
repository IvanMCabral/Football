package com.footballmanager.application.service.simulation.v24;

import com.footballmanager.domain.model.entity.CareerSave;

/**
 * Pure orchestration service that coordinates V24 career mutations after a match result.
 *
 * <p>V24D6B2 scope: injury mutation only. Fatigue, discipline, and form are not implemented.
 *
 * <p>This service is isolated — no Spring, no Redis, no IO.
 * It is not yet wired into LeagueSimulator; it exists as a standalone component.
 */
public final class V24CareerMutationService {

    private final V24InjuryMutationApplier injuryMutationApplier;

    public V24CareerMutationService(V24InjuryMutationApplier injuryMutationApplier) {
        this.injuryMutationApplier = injuryMutationApplier;
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

        // V24D6B2: only injury applier is active
        if (!policy.isInjuryPersistenceEnabled()
                && !policy.isFatiguePersistenceEnabled()
                && !policy.isDisciplinePersistenceEnabled()
                && !policy.isFormPersistenceEnabled()) {
            return V24CareerMutationResult.empty();
        }

        int injuries = 0;
        int failuresCount = 0;

        if (policy.isInjuryPersistenceEnabled()) {
            try {
                injuries = injuryMutationApplier.applyInjuries(career, result, policy);
            } catch (Exception e) {
                return V24CareerMutationResult.failure(
                        "Injury mutation failed: " + e.getMessage());
            }
        }

        return V24CareerMutationResult.success(injuries, 0, 0, 0);
    }
}