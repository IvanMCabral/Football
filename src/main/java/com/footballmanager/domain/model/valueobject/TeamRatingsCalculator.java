package com.footballmanager.domain.model.valueobject;

import java.util.Collections;
import java.util.List;

public final class TeamRatingsCalculator {

    private TeamRatingsCalculator() {
    }

    private static final double STATS_AMP = 0.025;

    private static final int MEDIAN_STAT = 70;

    public record TeamRatings(double attackRating, double midfieldRating, double defenseRating) {}

    public record PlayerAttrs(
            String playerId,
            String naturalPos,
            String slotCategory,
            Integer attack,
            Integer defense,
            Integer technique,
            Integer mentality,
            Double slotXPercent,
            Double slotYPercent,
            boolean customPosition
    ) {}

    public static TeamRatings compute(List<PlayerAttrs> attrs, String formation) {
        String canonicalFormation = (formation == null || formation.isBlank())
                ? "4-4-2"
                : formation;
        if (attrs == null || attrs.isEmpty()) {
            double attBase = FormationRatingBases.attack(canonicalFormation);
            double midBase = FormationRatingBases.midfield(canonicalFormation);
            double defBase = FormationRatingBases.defense(canonicalFormation);
            return new TeamRatings(attBase * 100.0, midBase * 100.0, defBase * 100.0);
        }
        List<Double> attackerScores = new java.util.ArrayList<>();
        for (PlayerAttrs p : attrs) {
            double eff = subdivisionEffectiveness(p);
            double rawAttack = (p.attack() != null) ? p.attack() : MEDIAN_STAT;
            attackerScores.add(rawAttack * eff * forwardIntentMultiplier(p));
        }
        Collections.sort(attackerScores, Collections.reverseOrder());
        double teamAttack;
        if (attackerScores.isEmpty()) {
            teamAttack = MEDIAN_STAT;
        } else {
            int n = Math.min(7, attackerScores.size());
            double sum = 0;
            for (int i = 0; i < n; i++) {
                sum += attackerScores.get(i);
            }
            teamAttack = sum / n;
        }
        List<Double> defenderScores = new java.util.ArrayList<>();
        for (PlayerAttrs p : attrs) {
            String cat = p.slotCategory();
            if (!"DEF".equals(cat) && !"GK".equals(cat)) {
                continue;
            }
            double eff = subdivisionEffectiveness(p);
            int def = (p.defense() != null) ? p.defense() : MEDIAN_STAT;
            int men = (p.mentality() != null) ? p.mentality() : MEDIAN_STAT;
            defenderScores.add(((def + men) / 2.0) * eff);
        }
        double teamDefense;
        if (defenderScores.isEmpty()) {
            List<Integer> allDef = new java.util.ArrayList<>();
            for (PlayerAttrs p : attrs) {
                if (p.defense() != null) {
                    allDef.add(p.defense());
                }
            }
            teamDefense = allDef.isEmpty()
                    ? MEDIAN_STAT
                    : allDef.stream().mapToInt(Integer::intValue).average().orElse(MEDIAN_STAT);
        } else {
            teamDefense = defenderScores.stream()
                    .mapToDouble(Double::doubleValue)
                    .average()
                    .orElse(MEDIAN_STAT);
        }
        List<Double> midfielderScores = new java.util.ArrayList<>();
        for (PlayerAttrs p : attrs) {
            if (!"MID".equals(p.slotCategory())) {
                continue;
            }
            double eff = subdivisionEffectiveness(p);
            int tech = (p.technique() != null) ? p.technique() : MEDIAN_STAT;
            midfielderScores.add(tech * eff);
        }
        double teamMidfield = midfielderScores.isEmpty()
                ? MEDIAN_STAT
                : midfielderScores.stream().mapToDouble(Double::doubleValue).average().orElse(MEDIAN_STAT);
        double statsAmpAtt = 1.0 + (teamAttack - MEDIAN_STAT) * STATS_AMP;
        double statsAmpDef = 1.0 + (teamDefense - MEDIAN_STAT) * STATS_AMP;
        double statsAmpMid = 1.0 + (teamMidfield - MEDIAN_STAT) * STATS_AMP;

        FormationBaseBlend baseBlend = effectiveFormationBase(attrs, canonicalFormation);
        double attBase = baseBlend.attackBase();
        double defBase = baseBlend.defenseBase();
        double midBase = baseBlend.midfieldBase();

        double attRating = attBase * statsAmpAtt;
        double defRating = defBase * statsAmpDef;
        double midRating = midBase * statsAmpMid;

        return new TeamRatings(
                Math.max(0.1, attRating) * 100.0,
                Math.max(0.1, midRating) * 100.0,
                Math.max(0.1, defRating) * 100.0
        );
    }

    private static double subdivisionEffectiveness(PlayerAttrs p) {
        double xPct = (p.slotXPercent() != null) ? p.slotXPercent() : Double.NaN;
        double yPct = (p.slotYPercent() != null) ? p.slotYPercent() : Double.NaN;
        if (Double.isNaN(xPct) || Double.isNaN(yPct)) {
            return PositionEffectivenessCalculator.effectiveness(
                    p.naturalPos(), p.slotCategory());
        }
        return SubdivisionEffectivenessCalculator.effectiveness(
                p.naturalPos(), xPct, yPct, p.slotCategory());
    }

    private static double forwardIntentMultiplier(PlayerAttrs p) {
        if (p == null || "ATT".equals(p.slotCategory())) {
            return 1.0;
        }
        double yPct = (p.slotYPercent() != null) ? p.slotYPercent() : Double.NaN;
        if (Double.isNaN(yPct)) {
            return 1.0;
        }
        double forward = Math.max(0.0, Math.min(1.0, (55.0 - yPct) / 40.0));
        return 1.0 + (0.25 * forward);
    }

    private record FormationBaseBlend(double attackBase, double midfieldBase, double defenseBase) {}

    private static FormationBaseBlend effectiveFormationBase(List<PlayerAttrs> attrs, String selectedFormation) {
        double selectedAttack = FormationRatingBases.attack(selectedFormation);
        double selectedMidfield = FormationRatingBases.midfield(selectedFormation);
        double selectedDefense = FormationRatingBases.defense(selectedFormation);
        if (attrs == null || attrs.isEmpty()) {
            return new FormationBaseBlend(selectedAttack, selectedMidfield, selectedDefense);
        }
        boolean hasManualShape = attrs.stream().anyMatch(PlayerAttrs::customPosition);
        if (!hasManualShape) {
            return new FormationBaseBlend(selectedAttack, selectedMidfield, selectedDefense);
        }

        SoftShape soft = softShapeFromCoords(attrs);
        if (soft.totalOutfield() < 8.0) {
            return new FormationBaseBlend(selectedAttack, selectedMidfield, selectedDefense);
        }

        ShapeCandidate best = null;
        ShapeCandidate second = null;
        for (String candidate : FormationRatingBases.formations()) {
            int[] counts = parseCoarseFormation(candidate);
            if (counts == null) {
                continue;
            }
            double distance = Math.abs(soft.def() - counts[0])
                    + Math.abs(soft.mid() - counts[1])
                    + Math.abs(soft.att() - counts[2]);
            ShapeCandidate sc = new ShapeCandidate(candidate, distance);
            if (best == null || isBetterShapeCandidate(sc, best, selectedFormation, soft)) {
                second = best;
                best = sc;
            } else if (second == null || isBetterShapeCandidate(sc, second, selectedFormation, soft)) {
                second = sc;
            }
        }

        if (best == null || best.formation().equals(selectedFormation)) {
            return new FormationBaseBlend(selectedAttack, selectedMidfield, selectedDefense);
        }
        int[] selectedCounts = parseCoarseFormation(selectedFormation);
        int[] bestCounts = parseCoarseFormation(best.formation());
        if (sameCoarseShape(selectedCounts, bestCounts)) {
            return new FormationBaseBlend(selectedAttack, selectedMidfield, selectedDefense);
        }
        second = secondDistinctShapeCandidate(best, soft, selectedFormation);

        double separation = (second == null) ? 2.0 : Math.max(0.0, second.distance() - best.distance());
        double clarity = clamp01(separation / 2.0);
        double closeness = clamp01((0.55 - best.distance()) / 0.55);
        double blend = clarity * closeness;
        if (blend <= 0.05) {
            return new FormationBaseBlend(selectedAttack, selectedMidfield, selectedDefense);
        }

        double targetAttack = FormationRatingBases.attack(best.formation());
        double targetMidfield = FormationRatingBases.midfield(best.formation());
        double targetDefense = FormationRatingBases.defense(best.formation());
        return new FormationBaseBlend(
                selectedAttack + (targetAttack - selectedAttack) * blend,
                selectedMidfield + (targetMidfield - selectedMidfield) * blend,
                selectedDefense + (targetDefense - selectedDefense) * blend);
    }

    private record SoftShape(double def, double mid, double att, double totalOutfield) {}

    private record ShapeCandidate(String formation, double distance) {}

    private static boolean isBetterShapeCandidate(
            ShapeCandidate candidate,
            ShapeCandidate current,
            String selectedFormation,
            SoftShape soft
    ) {
        double diff = candidate.distance() - current.distance();
        if (diff < -0.0001) {
            return true;
        }
        if (diff > 0.0001) {
            return false;
        }

        int[] selected = parseCoarseFormation(selectedFormation);
        if (selected != null) {
            double attDrift = soft.att() - selected[2];
            double defDrift = soft.def() - selected[0];
            if (attDrift > 0.35) {
                return FormationRatingBases.attack(candidate.formation())
                        > FormationRatingBases.attack(current.formation());
            }
            if (defDrift > 0.35) {
                return FormationRatingBases.defense(candidate.formation())
                        > FormationRatingBases.defense(current.formation());
            }
        }

        return candidate.formation().compareTo(current.formation()) < 0;
    }

    private static ShapeCandidate secondDistinctShapeCandidate(
            ShapeCandidate best,
            SoftShape soft,
            String selectedFormation
    ) {
        int[] bestCounts = parseCoarseFormation(best.formation());
        ShapeCandidate second = null;
        for (String candidate : FormationRatingBases.formations()) {
            int[] counts = parseCoarseFormation(candidate);
            if (counts == null || sameCoarseShape(counts, bestCounts)) {
                continue;
            }
            double distance = Math.abs(soft.def() - counts[0])
                    + Math.abs(soft.mid() - counts[1])
                    + Math.abs(soft.att() - counts[2]);
            ShapeCandidate sc = new ShapeCandidate(candidate, distance);
            if (second == null || isBetterShapeCandidate(sc, second, selectedFormation, soft)) {
                second = sc;
            }
        }
        return second;
    }

    private static SoftShape softShapeFromCoords(List<PlayerAttrs> attrs) {
        double def = 0.0;
        double mid = 0.0;
        double att = 0.0;
        double total = 0.0;
        for (PlayerAttrs p : attrs) {
            if (p == null || "GK".equals(p.slotCategory())) {
                continue;
            }
            double y = (p.slotYPercent() != null) ? p.slotYPercent() : Double.NaN;
            if (Double.isNaN(y)) {
                String cat = p.slotCategory();
                if ("DEF".equals(cat)) def += 1.0;
                else if ("ATT".equals(cat)) att += 1.0;
                else mid += 1.0;
                total += 1.0;
                continue;
            }
            double attW = clamp01((55.0 - y) / 38.0);
            double defW = clamp01((y - 65.0) / 18.0);
            double sum = attW + defW;
            if (sum > 1.0) {
                attW /= sum;
                defW /= sum;
                sum = 1.0;
            }
            double midW = 1.0 - sum;
            att += attW;
            mid += midW;
            def += defW;
            total += 1.0;
        }
        return new SoftShape(def, mid, att, total);
    }

    private static int[] parseCoarseFormation(String formation) {
        if (formation == null || formation.isBlank()) {
            return null;
        }
        String[] parts = formation.split("-");
        if (parts.length < 3) {
            return null;
        }
        try {
            int def = Integer.parseInt(parts[0]);
            int att = Integer.parseInt(parts[parts.length - 1]);
            int mid = 10 - def - att;
            if (def < 0 || mid < 0 || att < 0) {
                return null;
            }
            return new int[]{def, mid, att};
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static boolean sameCoarseShape(int[] left, int[] right) {
        return left != null && right != null
                && left[0] == right[0]
                && left[1] == right[1]
                && left[2] == right[2];
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
