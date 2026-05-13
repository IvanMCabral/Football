package com.footballmanager.application.service.simulation.v24;

/**
 * Immutable policy object that governs V24 career state mutation behavior.
 * Evaluates which mutation effects are enabled based on feature flags.
 *
 * <p>Rules:
 * <ul>
 *   <li>injury persistence: enabled only when mutateCareerState AND persistInjuries are both true</li>
 *   <li>fatigue persistence: enabled only when mutateCareerState AND persistFatigue are both true</li>
 *   <li>discipline persistence: enabled only when mutateCareerState AND persistDiscipline are both true</li>
 *   <li>form persistence: enabled only when mutateCareerState AND persistForm are both true</li>
 * </ul>
 *
 * <p>All flags default to false. Mutation is never enabled by default.
 */
public final class V24CareerMutationPolicy {

    private final boolean mutateCareerState;
    private final boolean persistInjuries;
    private final boolean persistFatigue;
    private final boolean persistDiscipline;
    private final boolean persistForm;

    public V24CareerMutationPolicy(
            boolean mutateCareerState,
            boolean persistInjuries,
            boolean persistFatigue,
            boolean persistDiscipline,
            boolean persistForm) {
        this.mutateCareerState = mutateCareerState;
        this.persistInjuries = persistInjuries;
        this.persistFatigue = persistFatigue;
        this.persistDiscipline = persistDiscipline;
        this.persistForm = persistForm;
    }

    /**
     * Master gate for all career mutation.
     * When false, no mutation effects are applied regardless of individual flags.
     */
    public boolean isCareerMutationEnabled() {
        return mutateCareerState;
    }

    /**
     * Injury persistence is enabled only when master gate AND persistInjuries are both true.
     */
    public boolean isInjuryPersistenceEnabled() {
        return mutateCareerState && persistInjuries;
    }

    /**
     * Fatigue persistence is enabled only when master gate AND persistFatigue are both true.
     */
    public boolean isFatiguePersistenceEnabled() {
        return mutateCareerState && persistFatigue;
    }

    /**
     * Discipline persistence is enabled only when master gate AND persistDiscipline are both true.
     */
    public boolean isDisciplinePersistenceEnabled() {
        return mutateCareerState && persistDiscipline;
    }

    /**
     * Form persistence is enabled only when master gate AND persistForm are both true.
     */
    public boolean isFormPersistenceEnabled() {
        return mutateCareerState && persistForm;
    }

    // Getters for raw flag values (useful for testing/debugging)

    public boolean isMutateCareerState() { return mutateCareerState; }
    public boolean isPersistInjuries() { return persistInjuries; }
    public boolean isPersistFatigue() { return persistFatigue; }
    public boolean isPersistDiscipline() { return persistDiscipline; }
    public boolean isPersistForm() { return persistForm; }

    @Override
    public String toString() {
        return "V24CareerMutationPolicy{mutateCareerState=%s, persistInjuries=%s, persistFatigue=%s, persistDiscipline=%s, persistForm=%s}"
                .formatted(mutateCareerState, persistInjuries, persistFatigue, persistDiscipline, persistForm);
    }
}