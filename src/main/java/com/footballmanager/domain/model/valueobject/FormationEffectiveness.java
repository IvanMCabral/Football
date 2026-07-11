package com.footballmanager.domain.model.valueobject;

import com.footballmanager.adapters.in.web.career.lineup.dto.LineupSlotDTO;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * V25D47 (Sprint C11a): aggregate record combining
 * {@link FormationInferer#infer} + {@link PositionEffectivenessCalculator#effectiveness}
 * for a full lineup. Mirrors the response DTO
 * {@code FormationEffectivenessDTO} (1:1 field set).
 *
 * <p>Three fields:
 * <ul>
 *   <li>{@code inferredFormation} — canonical label produced by
 *       {@link FormationInferer#infer(List)} from the lineup's subdivision
 *       slots (e.g., {@code "4-4-2"}, {@code "3-5-2"}, {@code "5-3-2"}).</li>
 *   <li>{@code perPlayerEffectiveness} — map of {@code subdivisionId →
 *       multiplier [0, 1]} where the multiplier is
 *       {@code effectiveness(naturalPosition, slotCategory)} for the player
 *       occupying that subdivision. Players at a perfect-match slot get
 *       {@code 1.0}; mismatches get a reduced multiplier (e.g., a CB in a
 *       MID slot → 0.8). Keyed by subdivisionId so the frontend can
 *       correlate a slot with its effectiveness score without joining
 *       against playerId.</li>
 *   <li>{@code teamAverage} — arithmetic mean of {@code perPlayerEffectiveness}
 *       values. Informational: a quick indicator of "how well does this
 *       lineup fit its formation?". A perfect 4-4-2 with all-natural
 *       positions → 1.0; an experimental 3-5-2 with CB in MID → ~0.85.</li>
 * </ul>
 *
 * <h2>Backward compat</h2>
 * <p>If slots are null/empty/malformed, {@code inferredFormation} falls
 * back to {@code FormationInferer.DEFAULT_FORMATION} ({@code "4-4-2"}) and
 * {@code perPlayerEffectiveness} is empty (all-natural positions default
 * to 1.0 per the {@link PositionEffectivenessCalculator} backward-compat
 * rule when {@code slotCategory} is unknown).
 *
 * <h2>Why this is a record, not a class</h2>
 * <p>Same rationale as {@code ChemistryDetail} (C8) — the data is
 * value-like (immutable snapshot of a computation), and the engine /
 * DTO mapping code reads better when the shape is explicit. Use the
 * static factory {@link #from(List, Map)} or the convenience
 * {@link #empty()} for construction.
 */
public record FormationEffectiveness(
    String inferredFormation,
    Map<String, Double> perPlayerEffectiveness,
    double teamAverage,
    /** V25D99.15-BACK: attack modifier × 100 (formation × statsAmp(teamAttack)). */
    Double attackRating,
    /** V25D99.15-BACK: midfield modifier × 100 (formation × statsAmp(teamMidfield)). */
    Double midfieldRating,
    /** V25D99.15-BACK: defense modifier × 100 (formation × statsAmp(teamDefense)). */
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
     * <p><b>V25D52 (Sprint C13b):</b> {@code perPlayerEffectiveness} is
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
            List<LineupSlotDTO> slots,
            Map<String, String> naturalByPlayer) {
        return from(slots, naturalByPlayer, null, List.of(), null, Map.of());
    }

    /**
     * V25D99.15-BACK: overload that also takes per-player attributes so
     * {@link TeamRatingsCalculator} can compute the engine's
     * teamAttack / teamDefense / teamMidfield aggregates. Without
     * attributes, ratings default to the formation baselines
     * (4-4-2 = 100/100/100, scaled for others).
     *
     * <p>V25D99.16-BACK: added {@code coordsBySubdivision} parameter so
     * the rating calculator can apply the new subdivision-aware
     * distance penalty. Pass an empty map (or {@link Map#of()}) to skip
     * the geometry penalty and preserve the legacy zone-only math
     * (callers that don't have formation coords wired in still work).
     */
    public static FormationEffectiveness from(
            List<LineupSlotDTO> slots,
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
        // V25D99.16-BACK: snapshot coords lookup for null-safety inside
        // the loop. Mirrors the safeNatural pattern right below.
        Map<String, double[]> safeCoords = (coordsBySubdivision != null) ? coordsBySubdivision : Map.of();
        if (slots != null) {
            // Per-player effectiveness, even when naturalByPlayer is null
            // (calculator returns 1.0 for unknown natural — backward compat).
            // Without this loop, an empty/null naturalByPlayer would yield
            // an empty perPlayer map, hiding the lineup's actual composition.
            //
            // V25D52 (Sprint C13b): key by subdivisionId, not playerId — the
            // frontend correlates a slot with its effectiveness score
            // directly (see FormationEffectivenessDTO wire shape).
            //
            // V25D99.16-BACK: when the caller supplies coords for this
            // subdivision, layer the subdivision-aware distance penalty
            // on top of the zone lookup. Otherwise fall back to the
            // legacy zone-only math (preserves pre-V25D99.16 perPlayer
            // values for callers that haven't wired coords).
            Map<String, String> safeNatural = (naturalByPlayer != null) ? naturalByPlayer : Map.of();
            for (LineupSlotDTO slot : slots) {
                if (slot == null) continue;
                if (slot.playerId() == null || slot.subdivisionId() == null) continue;
                String natural = safeNatural.get(slot.playerId());
                String slotCat = roleAwareCategoryFor(slot.subdivisionId(), formationForCalc);
                double eff;
                // V25D99.17-BACK: prefer the player's free-positioning override
                // coords when the front sets them (customXPercent / customYPercent).
                // The canonical coords from safeCoords still apply when the
                // override is null (legacy path, pre-V25D99.17 saves, players
                // dropped directly on a slot center).
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

        // V25D99.15-BACK: compute the engine's per-zone ratings (attack /
        // midfield / defense). Build the PlayerAttrs list from the slots
        // + the caller-supplied attributes. Players without attributes
        // are skipped (calculator falls back to median 70 for missing
        // values, so a 7-attribute lineup still works).
        //
        // V25D99.16-BACK: also thread the per-subdivision coords into
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
     * V25D99.15-BACK: helper that bridges the existing slot +
     * naturalByPlayer + attrsByPlayer shape into the calculator's
     * {@link TeamRatingsCalculator.PlayerAttrs} list. Skips players that
     * don't appear in any slot (bench) — the engine's teamAttack /
     * teamDefense aggregates only count on-field players.
     *
     * <p>V25D99.16-BACK: also threads {@code coordsBySubdivision} (a
     * pre-resolved {@code subdivisionId -> {xPct, yPct}} map, typically
     * built from {@code FormationService.getCoordsByFormation(formation)})
     * into each {@code PlayerAttrs} entry. Players whose subdivision has
     * no coords entry get {@code Double.NaN} so
     * {@link TeamRatingsCalculator} falls back to the legacy zone-only
     * effectiveness lookup (mixed-coords lineups during partial drag
     * still produce sensible numbers).
     */
    private static TeamRatingsCalculator.TeamRatings computeRatings(
            List<LineupSlotDTO> slots,
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
        for (LineupSlotDTO slot : slots) {
            if (slot == null || slot.playerId() == null) continue;
            String natural = (naturalByPlayer != null) ? naturalByPlayer.get(slot.playerId()) : null;
            String slotCat = roleAwareCategoryFor(slot.subdivisionId(), formationForRatings);
            PlayerAttrDTO attr = attrsIdx.get(slot.playerId());
            // V25D99.17-BACK: prefer the player's free-positioning override
            // coords (customXPercent / customYPercent) over the canonical
            // slot coords. Same override semantics as the perPlayer loop
            // above; the helper keeps both spots in lockstep.
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
                    slotY
            ));
        }
        return TeamRatingsCalculator.compute(calculatorAttrs, formationForRatings);
    }

    /**
     * V25D99.17-BACK: pick the effective field coords for a slot.
     *
     * <p>Resolution order:
     * <ol>
     *   <li>If the slot carries a numeric {@code customXPercent} AND
     *       {@code customYPercent} override (V25D98 free-positioning),
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
            LineupSlotDTO slot,
            Map<String, double[]> safeCoords,
            String naturalPosition) {
        Double cx = slot.customXPercent();
        Double cy = slot.customYPercent();
        double[] canonical = safeCoords.get(slot.subdivisionId());
        boolean genericNatural = isGenericOutfieldPosition(naturalPosition);

        // V25D99.20.9-BACK: real career data often stores broad positions
        // (DEF/MID/ATT/WINGER) instead of granular roles (LB/CM/ST/LW).
        // A canonical 4-4-2 should not penalize a generic DEF just because
        // the slot is wide LB/RB, nor a generic MID because it is LM/RM.
        //
        // For generic players, canonical coordinates mean "perfect enough".
        // When the user free-drags the marker, measure only the DELTA from
        // its canonical slot by translating that delta around the generic
        // category's centroid. This keeps tiny manual moves tiny, large
        // manual moves large, and avoids a sudden penalty for starting from
        // a wide canonical slot.
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
            // V25D99.20.9-BACK: the grid row is not always the tactical
            // role. In back-three formations, LWB/RWB live visually in the
            // midfield row but should be evaluated as DEF. In 4-2-3-1, the
            // wide LW/RW attacking-midfield slots live in row 3 but should
            // contribute as ATT. The visual FormationService roles are the
            // real tactical source; this helper mirrors the current 12
            // canonical layouts without changing FormationInferer, whose job
            // remains coarse row-based inference for legacy/custom shapes.
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
     * V25D99.15-BACK: thin DTO so callers can supply per-player attributes
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
     * V25D55 (Sprint C16): overload that forwards the persisted formation to
     * {@link FormationInferer#infer(List, String)} so the resulting
     * {@code inferredFormation} field matches the label the manager actually
     * selected (e.g. {@code "3-5-2-CDM"}, {@code "5-4-1"}). Without this, the
     * slot-inference algorithm collapses every multi-role formation into the
     * {@code "X-Y-Z"} triple, and the front-end reports a different label
     * than the one shown in the formation modal.
     *
     * <p>V25D99.15-BACK: thin delegate to the 6-arg overload — no
     * attributes are passed so ratings fall back to the formation
     * baseline (4-4-2 = 100/100/100).
     *
     * <p>V25D99.16-BACK: no coords passed so ratings fall back to the
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
            List<LineupSlotDTO> slots,
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
