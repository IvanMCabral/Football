package com.footballmanager.domain.port.in.lineup;

import com.footballmanager.application.service.lineup.LineupRules;
import com.footballmanager.domain.model.valueobject.ChemistryDetail;
import com.footballmanager.domain.model.valueobject.FormationEffectiveness;
import com.footballmanager.domain.model.valueobject.LineupSlot;
import com.footballmanager.domain.model.valueobject.TacticalChemistry;

import java.util.List;

public record LineupView(
    String formation,
    List<LineupPlayerView> players,
    boolean confirmed,
    List<LineupWarning> warnings,
    List<LineupSlot> slots,
    Integer chemistryScore,
    ChemistryDetail chemistryBreakdown,
    TacticalChemistry tacticalChemistry,
    FormationEffectiveness formationEffectiveness
) {
    public LineupView {
        if (players == null) {
            players = List.of();
        }
        if (warnings == null) {
            warnings = List.of();
        }
        if (slots == null) {
            slots = List.of();
        }
        if (!players.isEmpty()) {
            int size = players.size();
            if (size < LineupRules.MIN_AVAILABLE_PLAYERS) {
                throw new IllegalArgumentException(
                    "Lineup must have at least " + LineupRules.MIN_AVAILABLE_PLAYERS
                        + " players, found: " + size);
            }
            if (size > LineupRules.MAX_LINEUP_PLAYERS) {
                throw new IllegalArgumentException(
                    "Lineup must have at most " + LineupRules.MAX_LINEUP_PLAYERS
                        + " players, found: " + size);
            }
        }
        players = List.copyOf(players);
        warnings = List.copyOf(warnings);
        slots = List.copyOf(slots);
    }
}
