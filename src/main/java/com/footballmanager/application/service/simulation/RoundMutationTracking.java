package com.footballmanager.application.service.simulation;

import java.util.HashSet;
import java.util.Set;

final class RoundMutationTracking {
    final Set<String> newlySuspendedPlayerIds = new HashSet<>();
    final Set<String> participatedPlayerIds = new HashSet<>();
    final Set<String> newlyInjuredPlayerIds = new HashSet<>();
    boolean detailedRoundProcessed = false;
}
