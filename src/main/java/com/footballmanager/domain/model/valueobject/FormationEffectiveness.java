package com.footballmanager.domain.model.valueobject;

import com.footballmanager.domain.model.valueobject.LineupSlot;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resultado agregado de encaje tactico para una alineacion.
 *
 * <p>Combina la formacion inferida, la efectividad de cada jugador en su slot
 * y los ratings por zona que usa el motor. La clave de
 * {@code perPlayerEffectiveness} es el {@code subdivisionId}, porque el front
 * necesita asociar cada ficha con su propio multiplicador.
 */
public record FormationEffectiveness(
    String inferredFormation,
    Map<String, Double> perPlayerEffectiveness,
    double teamAverage,
    /** Rating ofensivo final, normalizado alrededor de 100. */
    Double attackRating,
    /** Rating de mediocampo final, normalizado alrededor de 100. */
    Double midfieldRating,
    /** Rating defensivo final, normalizado alrededor de 100. */
    Double defenseRating
) {

    /**
     * Static factory: compute the aggregate from the lineup's slots and
     * the playerId → naturalPosition map (typically derived from the
     * 11 SessionPlayers in the lineup).
     *
     * <p>If {@code slots} is null/empty, the inferredFormation defaults to
     * {@code "4-4-2"} and perPlayerEffectiveness is empty (the engine
     * still gets the 4-4-2 default, no penalties applied).
     *
     * keyed by {@code subdivisionId} (NOT playerId) — the frontend's
     * {@code FormationEffectivenessDTO} spec correlates each slot with its
     * effectiveness score directly, without joining against playerId.
     * Prior to this fix the map was keyed by playerId, which produced a
     * silent contract mismatch: the frontend looked up by subdivisionId,
     * always got {@code undefined}, and no CSS class / badge was applied.
     *
     * @param slots           the 11 subdivision slots the manager assigned
     *                       (may be null/empty for legacy lineups).
     * @param naturalByPlayer playerId → natural 5-cat position
     *                       ({@code "GK"/"DEF"/"MID"/"WINGER"/"ATT"}).
     *                       Players not in this map are skipped (their
     *                       multiplier defaults to 1.0 if they appear in
     *                       slots but lack naturalPosition).
     * @return populated {@code FormationEffectiveness}.
     */
    public static FormationEffectiveness from(
            List<LineupSlot> slots,
            Map<String, String> naturalByPlayer) {
        return from(slots, naturalByPlayer, null, List.of(), null, Map.of());
    }

    /**
     * {@link TeamRatingsCalculator} can compute the engine's
     * teamAttack / teamDefense / teamMidfield aggregates. Without
     * attributes, ratings default to the formation baselines
     * (4-4-2 = 100/100/100, scaled for others).
     *
     * the rating calculator can apply the new subdivision-aware
     * distance penalty. Pass an empty map (or {@link Map#of()}) to skip
     * the geometry penalty and preserve the legacy zone-only math
     * (callers that don't have formation coords wired in still work).
     */
    public static FormationEffectiveness from(
            List<LineupSlot> slots,
            Map<String, String> naturalByPlayer,
            String persistedFormation,
            List<PlayerAttrDTO> attrsByPlayer,
            String formationForRatings,
            Map<String, double[]> coordsBySubdivision) {
        String inferred = FormationInferer.infer(slots, persistedFormation);
        String formationForCalc = (formationForRatings != null && !formationForRatings.isBlank())
                ? formationForRatings
                : inferred;

        Map<String, Double> perPlayer = new LinkedHashMap<>();
        // Copia segura para simplificar la logica del loop.
        Map<String, double[]> safeCoords = (coordsBySubdivision != null) ? coordsBySubdivision : Map.of();
        if (slots != null) {
            // Per-player effectiveness, even when naturalByPlayer is null
            // (calculator returns 1.0 for unknown natural — backward compat).
            // Without this loop, an empty/null naturalByPlayer would yield
            // an empty perPlayer map, hiding the lineup's actual composition.
            //
            // frontend correlates a slot with its effectiveness score
            // directly (see FormationEffectivenessDTO wire shape).
            //
            // subdivision, layer the subdivision-aware distance penalty
            // on top of the zone lookup. Otherwise fall back to the
            // values for callers that haven't wired coords).
            Map<String, String> safeNatural = (naturalByPlayer != null) ? naturalByPlayer : Map.of();
            for (LineupSlot slot : slots) {
                if (slot == null) continue;
                if (slot.playerId() == null || slot.subdivisionId() == null) continue;
                String natural = safeNatural.get(slot.playerId());
                String slotCat = roleAwareCategoryFor(slot.subdivisionId(), formationForCalc);
                double eff;
                // Si el jugador fue movido manualmente, esa coordenada manda.
                double[] coords = resolveSlotCoords(slot, safeCoords, natural);
                if (coords != null) {
                    eff = SubdivisionEffectivenessCalculator.effectiveness(
                            natural, coords[0], coords[1], slotCat);
                } else {
                    eff = PositionEffectivenessCalculator.effectiveness(natural, slotCat);
                }
                perPlayer.put(slot.subdivisionId(), eff);
            }
        }

        double avg = perPlayer.isEmpty()
                ? 1.0
                : perPlayer.values().stream().mapToDouble(Double::doubleValue).average().orElse(1.0);

        // midfield / defense). Build the PlayerAttrs list from the slots
        // + the caller-supplied attributes. Players without attributes
        // are skipped (calculator falls back to median 70 for missing
        // values, so a 7-attribute lineup still works).
        //
        // each PlayerAttrs entry so the new distance-aware calculator
        // can run. Empty / missing coords → NaN → legacy zone-only math.
        TeamRatingsCalculator.TeamRatings ratings = computeRatings(
                slots, naturalByPlayer, attrsByPlayer, formationForCalc, safeCoords);

        return new FormationEffectiveness(
                inferred,
                perPlayer,
                avg,
                ratings.attackRating(),
                ratings.midfieldRating(),
                ratings.defenseRating());
    }

    /**
     * naturalByPlayer + attrsByPlayer shape into the calculator's
     * {@link TeamRatingsCalculator.PlayerAttrs} list. Skips players that
     * don't appear in any slot (bench) — the engine's teamAttack /
     * teamDefense aggregates only count on-field players.
     *
     * pre-resolved {@code subdivisionId -> {xPct, yPct}} map, typically
     * built from {@code FormationService.getCoordsByFormation(formation)})
     * into each {@code PlayerAttrs} entry. Players whose subdivision has
     * no coords entry get {@code Double.NaN} so
     * {@link TeamRatingsCalculator} falls back to the legacy zone-only
     * effectiveness lookup (mixed-coords lineups during partial drag
     * still produce sensible numbers).
     */
    private static TeamRatingsCalculator.TeamRatings computeRatings(
            List<LineupSlot> slots,
            Map<String, String> naturalByPlayer,
            List<PlayerAttrDTO> attrsByPlayer,
            String formationForRatings,
            Map<String, double[]> coordsBySubdivision) {
        if (slots == null || slots.isEmpty()) {
            return TeamRatingsCalculator.compute(List.of(), formationForRatings);
        }
        java.util.Map<String, PlayerAttrDTO> attrsIdx = new java.util.HashMap<>();
        if (attrsByPlayer != null) {
            for (PlayerAttrDTO a : attrsByPlayer) {
                if (a != null && a.playerId() != null) {
                    attrsIdx.put(a.playerId(), a);
                }
            }
        }
        Map<String, double[]> safeCoords = (coordsBySubdivision != null) ? coordsBySubdivision : Map.of();
        java.util.List<TeamRatingsCalculator.PlayerAttrs> calculatorAttrs = new java.util.ArrayList<>();
        for (LineupSlot slot : slots) {
            if (slot == null || slot.playerId() == null) continue;
            String natural = (naturalByPlayer != null) ? naturalByPlayer.get(slot.playerId()) : null;
            String slotCat = roleAwareCategoryFor(slot.subdivisionId(), formationForRatings);
            PlayerAttrDTO attr = attrsIdx.get(slot.playerId());
            // Mantiene la misma semantica de coordenadas que la efectividad individual.
            boolean customPosition = isTacticalShapeOverride(slot, safeCoords);
            double[] coords = resolveSlotCoords(slot, safeCoords, natural);
            Double slotX = (coords != null && coords.length >= 1) ? coords[0] : null;
            Double slotY = (coords != null && coords.length >= 2) ? coords[1] : null;
            calculatorAttrs.add(new TeamRatingsCalculator.PlayerAttrs(
                    slot.playerId(),
                    natural,
                    slotCat,
                    attr != null ? attr.attack() : null,
                    attr != null ? attr.defense() : null,
                    attr != null ? attr.technique() : null,
                    attr != null ? attr.mentality() : null,
                    slotX,
                    slotY,
                    customPosition
            ));
        }
        return TeamRatingsCalculator.compute(calculatorAttrs, formationForRatings);
    }

    /**
     *
     * <p>Resolution order:
     * <ol>
     *   <li>If the slot carries a numeric {@code customXPercent} AND
     *       return {@code {customXPercent, customYPercent}}.</li>
     *   <li>Else return the canonical {@code {coords[0], coords[1]}}
     *       from {@code coordsBySubdivision} (resolved by
     *       {@code FormationService.getCoordsByFormation(formation)} in
     *       the controller layer).</li>
     *   <li>Else {@code null} — caller falls back to the legacy
     *       zone-only {@code PositionEffectivenessCalculator}.</li>
     * </ol>
     *
     * <p>The front may send only one of the two coords (NaN-ish). When
     * that happens we still fall back to the canonical pair, otherwise
     * the calculator would compute a junk distance (one axis at 0 or
     * 100, the other at the slot center) and produce a misleadingly
     * large penalty.
     *
     * @param slot       the per-player slot entry (may carry a free-
     *                   positioning override).
     * @param safeCoords canonical coords from {@code FormationService},
     *                   keyed by {@code subdivisionId}.
     * @return {@code double[2]} with the effective x/y, or {@code null}
     *         when no coords can be resolved.
     */
    private static double[] resolveSlotCoords(
            LineupSlot slot,
            Map<String, double[]> safeCoords,
            String naturalPosition) {
        Double cx = slot.customXPercent();
        Double cy = slot.customYPercent();
        double[] canonical = safeCoords.get(slot.subdivisionId());
        boolean genericNatural = isGenericOutfieldPosition(naturalPosition);

        // Las posiciones genericas aceptan el slot base como encaje valido;
        // el arrastre manual mide solamente el desplazamiento real.
        if (cx != null && cy != null && !Double.isNaN(cx) && !Double.isNaN(cy)) {
            if (genericNatural && canonical != null && canonical.length >= 2) {
                double[] ideal = SubdivisionEffectivenessCalculator.idealCoordsFor(naturalPosition);
                if (ideal != null && ideal.length >= 2) {
                    return new double[]{
                            ideal[0] + (cx - canonical[0]),
                            ideal[1] + (cy - canonical[1])
                    };
                }
            }
            return new double[]{cx, cy};
        }
        if (genericNatural) {
            return null;
        }
        if (canonical != null && canonical.length >= 2) {
            return canonical;
        }
        return null;
    }

    /**
     * shape change. Any numeric custom coord still feeds the geometry-aware
     * rating math, but the formation-base blend only activates when the marker
     * is clearly away from its canonical slot. This prevents one-pixel drags
     * around CAM/CDM/variant templates from flipping the whole tactical identity.
     */
    private static boolean isTacticalShapeOverride(
            LineupSlot slot,
            Map<String, double[]> safeCoords
    ) {
        if (slot == null
                || slot.customXPercent() == null
                || slot.customYPercent() == null
                || Double.isNaN(slot.customXPercent())
                || Double.isNaN(slot.customYPercent())) {
            return false;
        }

        double[] canonical = safeCoords.get(slot.subdivisionId());
        if (canonical == null || canonical.length < 2) {
            return true;
        }

        double dx = Math.abs(slot.customXPercent() - canonical[0]);
        double dy = Math.abs(slot.customYPercent() - canonical[1]);
        return dy >= 14.0 || Math.hypot(dx, dy) >= 16.0;
    }

    private static boolean isGenericOutfieldPosition(String naturalPosition) {
        return "DEF".equals(naturalPosition)
                || "MID".equals(naturalPosition)
                || "WINGER".equals(naturalPosition)
                || "ATT".equals(naturalPosition);
    }

    private static String roleAwareCategoryFor(String subdivisionId, String formation) {
        if (subdivisionId == null) {
            return null;
        }
        if (formation != null) {
            // La fila visual no siempre coincide con el rol tactico real.
            if (isBackThreeWingbackFormation(formation)
                    && ("S15-1".equals(subdivisionId) || "S18-3".equals(subdivisionId))) {
                return "DEF";
            }
            if ("4-2-3-1".equals(formation)
                    && ("S10-2".equals(subdivisionId) || "S12-2".equals(subdivisionId))) {
                return "ATT";
            }
        }
        return FormationInferer.categoryFor(subdivisionId);
    }

    private static boolean isBackThreeWingbackFormation(String formation) {
        return "3-5-2".equals(formation)
                || "3-4-3".equals(formation)
                || "3-5-2-CDM".equals(formation)
                || "3-4-1-2".equals(formation);
    }

    /**
     * without depending on {@link com.footballmanager.domain.model.entity
     * .SessionPlayer} directly (the controller layer lives in
     * {@code adapters.in.web}).
     */
    public record PlayerAttrDTO(
            String playerId,
            Integer attack,
            Integer defense,
            Integer technique,
            Integer mentality
    ) {}

    /**
     * {@link FormationInferer#infer(List, String)} so the resulting
     * {@code inferredFormation} field matches the label the manager actually
     * selected (e.g. {@code "3-5-2-CDM"}, {@code "5-4-1"}). Without this, the
     * slot-inference algorithm collapses every multi-role formation into the
     * {@code "X-Y-Z"} triple, and the front-end reports a different label
     * than the one shown in the formation modal.
     *
     * attributes are passed so ratings fall back to the formation
     * baseline (4-4-2 = 100/100/100).
     *
     * legacy zone-only math (no subdivision-aware distance penalty).
     *
     * @param slots              the 11 subdivision slots the manager assigned
     *                           (may be null/empty for legacy lineups).
     * @param naturalByPlayer    playerId → natural 5-cat position
     *                           ({@code "GK"/"DEF"/"MID"/"WINGER"/"ATT"}).
     * @param persistedFormation canonical formation label from
     *                           {@code CareerSave.teamStarting11Formation}, or
     *                           {@code null} for legacy lineups.
     * @return populated {@code FormationEffectiveness}.
     */
    public static FormationEffectiveness from(
            List<LineupSlot> slots,
            Map<String, String> naturalByPlayer,
            String persistedFormation) {
        return from(slots, naturalByPlayer, persistedFormation, List.of(), null, Map.of());
    }

    /**
     * Backward-compat empty instance: inferredFormation = default, no
     * per-player data, teamAverage = 1.0, ratings = 100/100/100 (4-4-2
     * baseline at median stats). Returned by build sites when slots are
     * null/malformed (graceful degradation).
     */
    public static FormationEffectiveness empty() {
        return new FormationEffectiveness(
                FormationInferer.DEFAULT_FORMATION,
                Map.of(),
                1.0,
                100.0,
                100.0,
                100.0);
    }
}
