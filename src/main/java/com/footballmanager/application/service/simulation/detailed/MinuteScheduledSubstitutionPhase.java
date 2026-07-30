package com.footballmanager.application.service.simulation.detailed;

import org.slf4j.Logger;

final class MinuteScheduledSubstitutionPhase {
    private final Logger log;
    private final MinuteSubstitutionPolicies substitutionPolicies;

    MinuteScheduledSubstitutionPhase(Logger log, MinuteSubstitutionPolicies substitutionPolicies) {
        this.log = java.util.Objects.requireNonNull(log, "log must not be null");
        this.substitutionPolicies = java.util.Objects.requireNonNull(
                substitutionPolicies, "substitutionPolicies must not be null");
    }

    void apply(MinuteSimulationInput minuteContext) {
        int minute = minuteContext.minute();
        for (MatchContext.ScheduledSub sub : minuteContext.matchContext().manualSubstitutions()) {
            if (sub.effectiveMinute() != minute) {
                continue;
            }
            String subKey = sub.effectiveMinute() + ":" + sub.teamId() + ":" + sub.playerOffId();
            TeamMatchState target = sub.teamId().equals(minuteContext.matchContext().homeTeamId())
                    ? minuteContext.homeState() : minuteContext.awayState();
            if (minuteContext.appliedScheduledSubs().contains(subKey)) {
                continue;
            }
            try {
                DetailedMatchEvent subEvent = substitutionPolicies.scheduledSubstitutionEngine().manualSubstitute(
                        target, sub.playerOffId(), sub.playerOnId(), sub.effectiveMinute());
                minuteContext.timeline().addEvent(subEvent);
                minuteContext.appliedScheduledSubs().add(subKey);
                log.trace("Applied scheduled sub at minute {}: teamId={} off={} on={}",
                        minute, sub.teamId(), sub.playerOffId(), sub.playerOnId());
            } catch (IllegalStateException e) {
                log.warn("Could not apply scheduled sub at minute {} "
                        + "teamId={} off={} on={}: {}",
                        minute, sub.teamId(), sub.playerOffId(), sub.playerOnId(), e.getMessage());
            }
        }
    }
}
