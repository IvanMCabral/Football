package com.footballmanager.adapters.in.web.career.lineup.dto;

import com.footballmanager.application.service.lineup.LineupRules;

import java.util.List;

/**
 * DTO para el Starting XI completo.
 *
 * <p>V24D6U2: size is now in {@code [MIN_AVAILABLE_PLAYERS, MAX_LINEUP_PLAYERS]}.
 * An empty list is still permitted (used as the empty state when no lineup
 * has been saved yet). When the lineup is below
 * {@code TARGET_LINEUP_PLAYERS}, the response carries a non-empty
 * {@code warnings} list.
 *
 * <p>MVP1-lineup-cancha-1: el campo {@code slots} es opcional y lista las
 * asignaciones playerId → subdivisionId persistidas. Si está vacío o ausente,
 * el front debe inferir los slots del role del jugador (backward compat).
 */
public record LineupDTO(
    String formation,
    List<PlayerLineupDTO> players,
    boolean confirmed,
    List<LineupWarningDTO> warnings,
    List<LineupSlotDTO> slots,
    /**
     * V25D41 (Sprint C6): team chemistry score in [0, 99], calculated by
     * {@code TeamChemistryCalculator} from the on-pitch players' overalls and
     * skill aggregates. {@code null} for empty lineups (the "no lineup saved"
     * state — see legacy 3-arg ctor below) — populated as 0 for non-empty
     * lineups with no players, or as the calculated value otherwise.
     *
     * <p>Intentionally nullable so legacy callers (pre-C6 build sites) that
     * don't compute chemistry don't need to change. The UI can safely
     * ignore {@code null} (treat as "no data") or {@code 0} (treat as "no
     * lineup strength").
     */
    Integer chemistryScore,
    /**
     * V25D43 (Sprint C8): per-position-group breakdown of the chemistry.
     * Wraps the same calculation as {@link #chemistryScore} but exposes
     * <em>which</em> skills are present in the lineup, at what level, and
     * which player is the "carrier" of each. Nullable for backward compat
     * with V25D41/V25D42 builds that don't compute it. The frontend
     * treats {@code null} as "no breakdown to render" and shows only the
     * score badge.
     */
    ChemistryBreakdownDTO chemistryBreakdown,
    /**
     * V25D47 (Sprint C11a): inferred formation label ({@code "4-4-2"},
     * {@code "3-5-2"}, etc.) + per-player effectiveness multipliers
     * (playerId → 0..1) computed from natural position vs subdivision slot.
     *
     * <p>Nullable for backward compat with V25D46 and earlier builds.
     * The frontend treats {@code null} as "no tactical data" and hides
     * the section. When present, the UI surfaces the inferred formation
     * (which may differ from the formation the manager selected via
     * auto-select if they drag-and-drop players into non-standard slots)
     * and flags low-effectiveness players for the manager to reconsider.
     */
    FormationEffectivenessDTO formationEffectiveness
) {

    /** Compact ctor legacy (3 args, no warnings/slots/chemistry). */
    public LineupDTO(String formation, List<PlayerLineupDTO> players, boolean confirmed) {
        this(formation, players, confirmed, List.of(), List.of(), null, null, null);
    }

    /** Compact ctor (4 args, no slots/chemistry). */
    public LineupDTO(String formation, List<PlayerLineupDTO> players, boolean confirmed,
                     List<LineupWarningDTO> warnings) {
        this(formation, players, confirmed, warnings, List.of(), null, null, null);
    }

    /** Compact ctor (5 args, sin chemistry — for legacy build sites pre-C6). */
    public LineupDTO(String formation, List<PlayerLineupDTO> players, boolean confirmed,
                     List<LineupWarningDTO> warnings, List<LineupSlotDTO> slots) {
        this(formation, players, confirmed, warnings, slots, null, null, null);
    }

    /**
     * V25D43 (Sprint C8): compact ctor (6 args, chemistryScore but no breakdown)
     * for callers that pre-date C8 — they get a null breakdown (backward compat
     * with the V25D42 wire format).
     */
    public LineupDTO(String formation, List<PlayerLineupDTO> players, boolean confirmed,
                     List<LineupWarningDTO> warnings, List<LineupSlotDTO> slots,
                     Integer chemistryScore) {
        this(formation, players, confirmed, warnings, slots, chemistryScore, null, null);
    }

    /**
     * V25D47 (Sprint C11a): compact ctor (7 args, with chemistryBreakdown but
     * no formationEffectiveness) for callers that pre-date C11a — they get
     * a null formationEffectiveness (backward compat with the V25D46 wire format).
     */
    public LineupDTO(String formation, List<PlayerLineupDTO> players, boolean confirmed,
                     List<LineupWarningDTO> warnings, List<LineupSlotDTO> slots,
                     Integer chemistryScore, ChemistryBreakdownDTO chemistryBreakdown) {
        this(formation, players, confirmed, warnings, slots, chemistryScore, chemistryBreakdown, null);
    }

    public LineupDTO {
        if (players == null) {
            players = List.of();
        }
        if (warnings == null) {
            warnings = List.of();
        }
        if (slots == null) {
            slots = List.of();
        }
        // Only validate size when non-empty. Empty list is the "no lineup saved" state.
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
        // Defensive copy of mutable lists to preserve immutability of the record
        players = List.copyOf(players);
        warnings = List.copyOf(warnings);
        slots = List.copyOf(slots);
    }
}