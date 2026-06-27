package com.footballmanager.adapters.in.web.career.lineup.dto;

import com.footballmanager.domain.model.valueobject.ChemistryDetail;
import com.footballmanager.domain.model.valueobject.FormationEffectiveness;
import com.footballmanager.domain.model.valueobject.FormationInferer;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * V25D47 (Sprint C11a): response DTO for the tactical formation effectiveness.
 * Sits alongside {@link ChemistryBreakdownDTO} (C8) on {@link LineupDTO}.
 *
 * <p>Wire shape:
 * <pre>
 *   {
 *     "inferredFormation": "3-5-2",
 *     "perPlayerEffectiveness": {
 *       "p1": 1.0,
 *       "p2": 0.8,
 *       ...
 *     },
 *     "teamAverage": 0.93
 *   }
 * </pre>
 *
 * <p>Mirrors the back record {@link FormationEffectiveness} 1:1 (Jackson
 * serializes records via their components). Field naming uses camelCase
 * on the wire (matches the existing C8 {@code ChemistryBreakdownDTO}
 * convention); the {@code from()} mapper is the single source of truth
 * for back → DTO conversion.
 *
 * <p><b>Nullable on {@link LineupDTO}:</b> this DTO is added as
 * {@code formationEffectiveness?} (optional) so lineups persisted
 * before V25D47 (which lack the field) still deserialize without 422
 * errors. The frontend treats null as "no tactical info available" and
 * hides the section.
 */
public record FormationEffectivenessDTO(
    String inferredFormation,
    Map<String, Double> perPlayerEffectiveness,
    double teamAverage
) {

    /**
     * Mapper: domain record → response DTO. Returns an empty DTO
     * ({@code inferredFormation = "4-4-2"}, {@code perPlayerEffectiveness = {}},
     * {@code teamAverage = 1.0}) when the input is null — graceful
     * degradation, the build sites always populate the field with a
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
                domain.teamAverage());
    }

    /**
     * Convenience: returns the backward-compat empty instance. Same shape
     * as {@code from(FormationEffectiveness.empty())} — the default
     * formation with no per-player penalties.
     */
    public static FormationEffectivenessDTO empty() {
        return new FormationEffectivenessDTO(
                FormationInferer.DEFAULT_FORMATION,
                Map.of(),
                1.0);
    }
}