package com.footballmanager.application.service.simulation.v24;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P1b: Unit tests for V24CareerMutationPolicy.
 * Immutable policy with 5 flags and 5 derived getters (AND logic).
 * Covers all 4 flag combinations per derived getter, plus all-true/all-false baselines,
 * raw getters, toString, and cross-flag independence.
 */
class V24CareerMutationPolicyTest {

    private V24CareerMutationPolicy policy(boolean mutate, boolean injuries,
            boolean fatigue, boolean discipline, boolean form) {
        return new V24CareerMutationPolicy(mutate, injuries, fatigue, discipline, form);
    }

    // ========== Master gate (isCareerMutationEnabled) ==========

    @Test
    void isCareerMutationEnabled_reflectsMutateCareerState() {
        assertTrue(policy(true, false, false, false, false).isCareerMutationEnabled());
        assertFalse(policy(false, true, true, true, true).isCareerMutationEnabled());
    }

    // ========== isInjuryPersistenceEnabled — 4 combinations ==========

    @Test
    void isInjuryPersistenceEnabled_trueTrueTrue() {
        assertTrue(policy(true, true, false, false, false).isInjuryPersistenceEnabled());
    }

    @Test
    void isInjuryPersistenceEnabled_trueTrueFalse() {
        assertFalse(policy(true, false, false, false, false).isInjuryPersistenceEnabled());
    }

    @Test
    void isInjuryPersistenceEnabled_trueFalseTrue() {
        assertFalse(policy(false, true, false, false, false).isInjuryPersistenceEnabled());
    }

    @Test
    void isInjuryPersistenceEnabled_falseFalse() {
        assertFalse(policy(false, false, false, false, false).isInjuryPersistenceEnabled());
    }

    // ========== isFatiguePersistenceEnabled — 4 combinations ==========

    @Test
    void isFatiguePersistenceEnabled_trueTrueTrue() {
        assertTrue(policy(true, false, true, false, false).isFatiguePersistenceEnabled());
    }

    @Test
    void isFatiguePersistenceEnabled_trueTrueFalse() {
        assertFalse(policy(true, false, false, false, false).isFatiguePersistenceEnabled());
    }

    @Test
    void isFatiguePersistenceEnabled_trueFalseTrue() {
        assertFalse(policy(false, false, true, false, false).isFatiguePersistenceEnabled());
    }

    @Test
    void isFatiguePersistenceEnabled_falseFalse() {
        assertFalse(policy(false, false, false, false, false).isFatiguePersistenceEnabled());
    }

    // ========== isDisciplinePersistenceEnabled — 4 combinations ==========

    @Test
    void isDisciplinePersistenceEnabled_trueTrueTrue() {
        assertTrue(policy(true, false, false, true, false).isDisciplinePersistenceEnabled());
    }

    @Test
    void isDisciplinePersistenceEnabled_trueTrueFalse() {
        assertFalse(policy(true, false, false, false, false).isDisciplinePersistenceEnabled());
    }

    @Test
    void isDisciplinePersistenceEnabled_trueFalseTrue() {
        assertFalse(policy(false, false, false, true, false).isDisciplinePersistenceEnabled());
    }

    @Test
    void isDisciplinePersistenceEnabled_falseFalse() {
        assertFalse(policy(false, false, false, false, false).isDisciplinePersistenceEnabled());
    }

    // ========== isFormPersistenceEnabled — 4 combinations ==========

    @Test
    void isFormPersistenceEnabled_trueTrueTrue() {
        assertTrue(policy(true, false, false, false, true).isFormPersistenceEnabled());
    }

    @Test
    void isFormPersistenceEnabled_trueTrueFalse() {
        assertFalse(policy(true, false, false, false, false).isFormPersistenceEnabled());
    }

    @Test
    void isFormPersistenceEnabled_trueFalseTrue() {
        assertFalse(policy(false, false, false, false, true).isFormPersistenceEnabled());
    }

    @Test
    void isFormPersistenceEnabled_falseFalse() {
        assertFalse(policy(false, false, false, false, false).isFormPersistenceEnabled());
    }

    // ========== Raw flag getters ==========

    @Test
    void rawGetters_reflectFlagValues() {
        V24CareerMutationPolicy p = policy(true, true, false, true, false);
        assertTrue(p.isMutateCareerState());
        assertTrue(p.isPersistInjuries());
        assertFalse(p.isPersistFatigue());
        assertTrue(p.isPersistDiscipline());
        assertFalse(p.isPersistForm());
    }

    // ========== Cross-flag independence ==========

    @Test
    void flagsAreIndependent_onlyInjuryEnabled() {
        V24CareerMutationPolicy p = policy(true, true, false, false, false);
        assertTrue(p.isInjuryPersistenceEnabled());
        assertFalse(p.isFatiguePersistenceEnabled());
        assertFalse(p.isDisciplinePersistenceEnabled());
        assertFalse(p.isFormPersistenceEnabled());
    }

    // ========== All-true and all-false baselines ==========

    @Test
    void allFlagsTrue_allDerivedGettersTrue() {
        V24CareerMutationPolicy p = policy(true, true, true, true, true);
        assertTrue(p.isInjuryPersistenceEnabled());
        assertTrue(p.isFatiguePersistenceEnabled());
        assertTrue(p.isDisciplinePersistenceEnabled());
        assertTrue(p.isFormPersistenceEnabled());
    }

    @Test
    void allFlagsFalse_allDerivedGettersFalse() {
        V24CareerMutationPolicy p = policy(false, false, false, false, false);
        assertFalse(p.isInjuryPersistenceEnabled());
        assertFalse(p.isFatiguePersistenceEnabled());
        assertFalse(p.isDisciplinePersistenceEnabled());
        assertFalse(p.isFormPersistenceEnabled());
    }

    // ========== toString ==========

    @Test
    void toString_containsAllFiveFlagNames() {
        String s = policy(true, false, true, false, true).toString();
        assertTrue(s.contains("mutateCareerState"));
        assertTrue(s.contains("persistInjuries"));
        assertTrue(s.contains("persistFatigue"));
        assertTrue(s.contains("persistDiscipline"));
        assertTrue(s.contains("persistForm"));
    }
}
