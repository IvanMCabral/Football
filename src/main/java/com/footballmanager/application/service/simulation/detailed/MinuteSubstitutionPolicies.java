package com.footballmanager.application.service.simulation.detailed;

record MinuteSubstitutionPolicies(
        SubstitutionEngine scheduledSubstitutionEngine,
        SubstitutionEngine automaticSubstitutionEngine) {
    MinuteSubstitutionPolicies {
        java.util.Objects.requireNonNull(scheduledSubstitutionEngine, "scheduledSubstitutionEngine must not be null");
        java.util.Objects.requireNonNull(automaticSubstitutionEngine, "automaticSubstitutionEngine must not be null");
    }
}
