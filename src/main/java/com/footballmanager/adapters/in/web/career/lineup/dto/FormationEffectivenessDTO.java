package com.footballmanager.adapters.in.web.career.lineup.dto;

import com.footballmanager.domain.model.valueobject.ChemistryDetail;
import com.footballmanager.domain.model.valueobject.FormationEffectiveness;
import com.footballmanager.domain.model.valueobject.FormationInferer;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sits alongside {@link ChemistryBreakdownDTO} (C8) on {@link LineupDTO}.
 *
 * <p>Wire shape:
 * <pre>
 *   {
 *     "inferredFormation": "3-5-2",
 *     "perPlayerEffectiveness": {
 *       "GK-1": 1.0,
 *       "S22-1": 0.85,
 *       "S15-1": 0.95,
 *       ...
 *     },
 *     "teamAverage": 0.93,
 *     "attackRating": 142.0,
 *     "midfieldRating": 105.0,
 *     "defenseRating": 95.0
 *   }
 * </pre>
 *
 * {@code subdivisionId} (e.g. {@code "GK-1"}, {@code "S22-1"}), not
 * {@code playerId}. This matches the frontend's contract — see
 * {@code front-ciber/.../shared/models/lineup/formation-effectiveness.dto.ts}.
 * Prior to C13b the back returned playerId keys while the front looked up
 * by subdivisionId, so {@code fe.perPlayerEffectiveness?.[subdivisionId]}
 * always returned {@code undefined} and the CSS class / badge never
 * applied. C13b aligns the wire contract on subdivisionId.
 *
 * ({@code attackRating}, {@code midfieldRating}, {@code defenseRating})
 * expose the same modifiers the detailed match simulation engine uses during a real
 * match (ShotXgCalculator.formationOffensiveModifier +
 * formationDefensiveModifier, weighted by PositionEffectivenessCalculator
 * .effectiveness). All three values are in {@code [0, ~200]}; 100 = 4-4-2
 * baseline at median stats. Higher attack = more dangerous, higher defense
 * = more protection. Used by the frontend Team Stats panel as the
 * single source of truth (no more client-side heuristics).
 *
 * <p>Mirrors the back record {@link FormationEffectiveness} 1:1 (Jackson
 * serializes records via their components). Field naming uses camelCase
 * on the wire (matches the existing C8 {@code ChemistryBreakdownDTO}
 * convention); the {@code from()} mapper is the single source of truth
 * for back → DTO conversion.
 *
 * <p><b>Nullable on {@link LineupDTO}:</b> this DTO is added as
 * {@code formationEffectiveness?} (optional) so lineups persisted
 * errors. The frontend treats null as "no tactical info available" and
 * hides the section.
 */
public record FormationEffectivenessDTO(
    String inferredFormation,
    Map<String, Double> perPlayerEffectiveness,
    double teamAverage,
    Double attackRating,
    Double midfieldRating,
    Double defenseRating
) {

    /**
     * Mapper: domain record → response DTO. Returns an empty DTO
     * ({@code inferredFormation = "4-4-2"}, {@code perPlayerEffectiveness = {}},
     * {@code teamAverage = 1.0}, ratings = 100/100/100) when the input is null —
     * graceful degradation, the build sites always populate the field with a
     * non-null value.
     *
     * <p>Per-player map order is preserved via {@link LinkedHashMap} so
     * the JSON is deterministic (matters for snapshot testing and
     * potential future caching).
     */
    public static FormationEffectivenessDTO from(FormationEffectiveness domain) {
        if (domain == null) {
            return empty();
        }
        Map<String, Double> players = (domain.perPlayerEffectiveness() == null)
                ? Map.of()
                : new LinkedHashMap<>(domain.perPlayerEffectiveness());
        return new FormationEffectivenessDTO(
                domain.inferredFormation(),
                players,
                domain.teamAverage(),
                domain.attackRating(),
                domain.midfieldRating(),
                domain.defenseRating());
    }

    /**
     * Convenience: returns the backward-compat empty instance. Same shape
     * as {@code from(FormationEffectiveness.empty())} — the default
     * formation with no per-player penalties. Ratings default to 100/100/100
     * (4-4-2 baseline at median stats) so the frontend has something to
     * render while no lineup is loaded yet.
     */
    public static FormationEffectivenessDTO empty() {
        return new FormationEffectivenessDTO(
                FormationInferer.DEFAULT_FORMATION,
                Map.of(),
                1.0,
                100.0,
                100.0,
                100.0);
    }
}
