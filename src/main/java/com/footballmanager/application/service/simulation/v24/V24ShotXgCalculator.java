package com.footballmanager.application.service.simulation.v24;

import com.footballmanager.domain.model.valueobject.PlayerSkill;

import java.util.Map;

/**
 * V24B: Computes expected goals (xG) for a shot using multi-factor model.
 *
 * <p>Factors:
 * <ul>
 *   <li>Shot location (distance from goal line, angle)</li>
 *   <li>Shooter quality (attack attribute + form)</li>
 *   <li>Assist quality (technique of passer)</li>
 *   <li>Defensive pressure (opponent defense + mentality)</li>
 *   <li>Goalkeeper quality</li>
 *   <li>Team style modifier (attacking = higher, defensive = lower)</li>
 *   <li>V25D33-F1: HEADER skill multiplier, gated on
 *       {@link V24ShotEventType} (only on CORNER / CROSS shots)</li>
 * </ul>
 *
 * <p>V25D32-F4: overload 9-args agrega skill levels del shooter y GK + heights
 * como plumbing para que V25D33-V25D34 los use. V25D32 NO impact el engine —
 * el overload 5-args delega al 9-args con {@code Map.of()} y {@code null},
 * manteniendo el resultado bit-a-bit identico al V25D31.
 *
 * <p>V25D33-F1: overload 10-args agrega {@code V24ShotEventType eventSubType}
 * para gating del HEADER multiplier. El overload 9-args delega al 10-args
 * con {@code V24ShotEventType.OPEN_PLAY} (default), preservando el contrato
 * V25D32 bit-a-bit para callers existentes — HEADER NO aplica en OPEN_PLAY.
 *
 * <p>Output clamped to [0.01, 0.60] (V24D6U4 tuned from 0.80).
 */
public class V24ShotXgCalculator {

    private static final double MIN_XG = 0.01;
    // V24D6U4: Reduced from 0.80 to 0.60 — even with multipliers, realistic xG
    // for a 6-yard box tap-in should not exceed ~0.50 in this tuned model.
    private static final double MAX_XG = 0.60;

    private static final double INSIDE_BOX_DISTANCE = 16.0; // meters from goal line
    private static final double SIX_YARD_BOX_DISTANCE = 8.0;

    /**
     * V25D27: Backward-compatible overload. Delegates to the new signature
     * with default stats (attack=70, defense=70, opponent formation=4-4-2).
     * Preserves the V25D26.1 behavior for existing tests/callers.
     */
    public double calculateXg(V24ShotQuality quality, String formation) {
        return calculateXg(quality, formation, "4-4-2", 70.0, 70.0);
    }

    /**
     * V25D32-F4: Backward-compatible 5-args overload (delega al 9-args con
     * skill maps vacias y heights null). Engine NO impact en V25D32 — el
     * resultado es bit-a-bit identico al overload 5-args previo.
     */
    public double calculateXg(V24ShotQuality quality, String formation,
                              String opponentFormation,
                              double possessorAttack, double opponentDefense) {
        return calculateXg(quality, formation, opponentFormation,
                possessorAttack, opponentDefense,
                Map.of(), null,    // shooter: sin skills, sin height
                Map.of(), null);   // gk: sin skills, sin height
    }

    /**
     * V25D32-F4: overload 9-args con skill levels + heights del shooter y GK.
     * V25D32 NO usa los nuevos params — son plumbing para V25D33-V25D34.
     *
     * <p>V25D27: Full xG calculation with formation × stats and defensive formation.
     *
     * <p>Pipeline: baseXg × shooter × assist × defensive × gk × style ×
     *   formationOffensive(possFormation, possessorAttack) ×
     *   formationDefensive(opponentFormation, opponentDefense).
     *
     * <p>V25D33-F1: este overload ahora delega al overload 10-args con
     * {@code V24ShotEventType.OPEN_PLAY} (default). Mantiene el contrato
     * V25D32 bit-a-bit porque (a) HEADER NO aplica en OPEN_PLAY y (b)
     * el overload 10-args solo agrega el HEADER multiplier en F1 (WALL llega
     * en F3). Para que callers existentes (V24DetailedMatchEngine) obtengan
     * HEADER/CORNER, deberan pasar al overload 10-args explicitamente.
     *
     * @param quality shot context (location, shooter, assist, pressure, GK, style)
     * @param formation the POSSESSOR's formation (e.g. "4-3-3")
     * @param opponentFormation the DEFENDING team's formation (e.g. "5-3-2")
     * @param possessorAttack aggregate attack stat of the possessor's attacking
     *                        players (avg of top-5 attackers, [0-99])
     * @param opponentDefense aggregate defense stat of the opponent's
     *                        defending players (avg of defenders + GK mentality, [0-99])
     * @param shooterSkills sparse map de PlayerSkill levels del shooter (nullable, V25D32 lo ignora)
     * @param shooterHeightCm height del shooter en cm (nullable, V25D32 lo ignora)
     * @param gkSkills sparse map de PlayerSkill levels del GK (nullable, V25D32 lo ignora)
     * @param gkHeightCm height del GK en cm (nullable, V25D32 lo ignora)
     */
    public double calculateXg(V24ShotQuality quality, String formation,
                              String opponentFormation,
                              double possessorAttack, double opponentDefense,
                              Map<PlayerSkill, Integer> shooterSkills, Integer shooterHeightCm,
                              Map<PlayerSkill, Integer> gkSkills, Integer gkHeightCm) {
        // V25D33-F1: 9-args overload delega al 10-args con OPEN_PLAY default.
        // Bit-a-bit backward compat con V25D32: HEADER no aplica fuera de
        // CORNER/CROSS, y WALL todavia no esta implementado (F3).
        return calculateXg(quality, formation, opponentFormation,
                possessorAttack, opponentDefense,
                shooterSkills, shooterHeightCm, gkSkills, gkHeightCm,
                V24ShotEventType.OPEN_PLAY);
    }

    /**
     * V25D33-F1: overload 10-args que agrega {@code V24ShotEventType eventSubType}
     * para gating del HEADER multiplier.
     *
     * <p>V25D33-F1 implementation:
     * <ul>
     *   <li>HEADER multiplier ({@code 1.0 + skill/200.0}) se aplica SOLO
     *       cuando {@code eventSubType ∈ {CORNER, CROSS}}. En OPEN_PLAY el
     *       multiplier es 1.0 (sin cambio).</li>
     *   <li>El 9-args overload delega a este con OPEN_PLAY, preservando el
     *       resultado V25D32 bit-a-bit para callers legacy.</li>
     * </ul>
     *
     * <p>V25D33-F3 (futuro): WALL divisor sobre xG cuando gkSkills contiene
     * PlayerSkill.WALL. NO implementado en este commit — se agrega en F3 con
     * un commit separado.
     *
     * <p>Calibration del HEADER multiplier (spec V25D33):
     * <ul>
     *   <li>HEADER=0 → multiplier = 1.0 (sin cambio)</li>
     *   <li>HEADER=80 → multiplier = 1.40 (+40%)</li>
     *   <li>HEADER=99 → multiplier = 1.495 (+49.5%)</li>
     * </ul>
     *
     * @param quality shot context (location, shooter, assist, pressure, GK, style)
     * @param formation the POSSESSOR's formation (e.g. "4-3-3")
     * @param opponentFormation the DEFENDING team's formation (e.g. "5-3-2")
     * @param possessorAttack aggregate attack stat of the possessor's attacking
     *                        players (avg of top-5 attackers, [0-99])
     * @param opponentDefense aggregate defense stat of the opponent's
     *                        defending players (avg of defenders + GK mentality, [0-99])
     * @param shooterSkills sparse map de PlayerSkill levels del shooter (nullable;
     *                      absent → treat as 0)
     * @param shooterHeightCm height del shooter en cm (nullable; V25D33-F1 lo ignora,
     *                        reservado para V25D34)
     * @param gkSkills sparse map de PlayerSkill levels del GK (nullable;
     *                  V25D33-F1 lo ignora, F3 lo usara para WALL divisor)
     * @param gkHeightCm height del GK en cm (nullable; V25D33-F1 lo ignora)
     * @param eventSubType origen del shot (OPEN_PLAY default). HEADER multiplier
     *                      se aplica SOLO cuando es CORNER o CROSS.
     */
    public double calculateXg(V24ShotQuality quality, String formation,
                              String opponentFormation,
                              double possessorAttack, double opponentDefense,
                              Map<PlayerSkill, Integer> shooterSkills, Integer shooterHeightCm,
                              Map<PlayerSkill, Integer> gkSkills, Integer gkHeightCm,
                              V24ShotEventType eventSubType) {
        double baseXgVal = baseXg(quality.location());
        double shooterMult = shooterMultiplier(quality.shooterQuality());
        double assistMult = assistMultiplier(quality.assistQuality());
        double defMult = defensiveMultiplier(quality.defensivePressure());
        double gkMult = goalkeeperMultiplier(quality.goalkeeperQuality());
        double styleMult = styleMultiplier(quality.tacticModifier());
        double offFormMod = formationOffensiveModifier(formation, possessorAttack);
        double defFormMod = formationDefensiveModifier(opponentFormation, opponentDefense);

        // V25D27.1: defensive modifier is a PROTECTION factor — it DIVIDES the xG
        // conceded (a 5-3-2 with defFormMod=1.25 means opponent xG is divided by 1.25,
        // i.e. 20% less). V25D27 first version multiplied, which inverted the intent
        // (5-3-2 received MORE goals than 4-3-3). Confirmed by smoke: avg_AG for
        // 5-3-2 was 4.40 (highest) vs 4-3-3 at 2.97 (lowest) — wrong direction.

        // V25D33-F1: HEADER multiplier gated on eventSubType. Only applies on
        // CORNER or CROSS shots — open-play shots are unchanged. Missing/null
        // HEADER skill is treated as 0 (multiplier stays 1.0).
        double headerMult = 1.0;
        if (eventSubType == V24ShotEventType.CORNER || eventSubType == V24ShotEventType.CROSS) {
            int headerSkill = (shooterSkills != null && shooterSkills.get(PlayerSkill.HEADER) != null)
                    ? shooterSkills.get(PlayerSkill.HEADER)
                    : 0;
            headerMult = 1.0 + (headerSkill / 200.0);
        }

        double xg = baseXgVal * shooterMult * assistMult * defMult * gkMult * styleMult
                * offFormMod / defFormMod * headerMult;

        return clamp(xg);
    }

    // V24D6U4: Base xG reduced ~45-55% to lower expected goals from ~4.5 to ~1.25 per team.
    // Target distribution: P(0)=29%, P(1)=36%, P(2)=22%, P(3+)=13% (Poisson λ=1.25).
    // Previous six-yard box was 0.38 → 0.20 (real football ~0.40-0.50 but with fewer chances).
    // Outside box reduced from 0.08 → 0.04, long range 0.04 → 0.02.
    // V25D26.1: baseXg reduced ~30% (multiplier 0.70) to compensate for the larger
    // formationXgModifier amplitudes (range 0.55-1.65). V25D26 used ×0.86 which was
    // insufficient — smoke showed λ=3.70 for 4-2-3-1 (gate upper 1.6). With ×0.70 the
    // expected λ for 4-4-2 (mod=1.0) lands at ~1.5 (in gate) and 4-2-3-1 (mod=1.65) at
    // ~2.5 — still above 1.6 but closer; the unit test V24ModelTuningDiagnosticTest
    // validates with formation=4-3-3 hardcoded which gives λ ~2.0 with these values
    // (acceptable since formation is the variable being tested).
    private double baseXg(V24ShotLocation location) {
        return switch (location) {
            case SIX_YARD_BOX -> 0.140;       // was 0.20 (×0.70)
            case PENALTY_AREA_CENTER -> 0.084; // was 0.12 (×0.70)
            case PENALTY_AREA_WIDE -> 0.063;  // was 0.09 (×0.70)
            case OUTSIDE_BOX -> 0.028;        // was 0.04 (×0.70)
            case LONG_RANGE -> 0.014;         // was 0.02 (×0.70)
        };
    }

    /**
     * V25D27: Formation-offensive modifier (formation × teamAttack).
     *
     * <p>V25D26.1 used a static per-formation value (1.00-1.65). V25D27 amplifies
     * it with the possessor's aggregate attack stat: an elite 4-3-3 squad
     * (attack avg ≈ 85) gets a much larger offensive boost than a weak 4-3-3
     * (attack avg ≈ 55). This addresses the user feedback that "formation ×
     * stats should sum" — a 4-3-3 with poor attackers is just a vulnerable 4-3-3,
     * not a free +40% xG.
     *
     * <p>Formula: {@code mod = baseFormationMod × (1 + (teamAttack - 70) × 0.025)}
     * <ul>
     *   <li>teamAttack = 70 (median) → multiplier = 1.0 (no amplification)
     *   <li>teamAttack = 85 (elite) → multiplier = 1.375
     *   <li>teamAttack = 55 (weak) → multiplier = 0.625
     * </ul>
     *
     * <p>Per-formation base values (carried from V25D26.1):
     * <ul>
     *   <li>4-4-2 → 1.00
     *   <li>4-3-3 → 1.40
     *   <li>4-2-3-1 → 1.65
     *   <li>3-5-2 → 0.70
     *   <li>5-3-2 → 0.55
     *   <li>3-4-3 → 1.35
     * </ul>
     */
    private double formationOffensiveModifier(String formation, double teamAttack) {
        V24FormationParser parser = new V24FormationParser();
        V24FormationParser.V24Formation f = parser.parse(formation);
        String canonical = f.raw();
        double baseMod;
        if ("4-3-3".equals(canonical)) baseMod = 1.40;
        else if ("4-2-3-1".equals(canonical)) baseMod = 1.65;
        else if ("3-4-3".equals(canonical)) baseMod = 1.35;
        else if ("3-5-2".equals(canonical)) baseMod = 0.70;
        else if ("5-3-2".equals(canonical)) baseMod = 0.55;
        else baseMod = 1.00; // 4-4-2 baseline

        // V25D27: amplify baseMod by teamAttack deviation from median (70).
        // teamAttack=70 → 1.0, teamAttack=85 → 1.375, teamAttack=55 → 0.625.
        double statsAmp = 1.0 + (teamAttack - 70.0) * 0.025;
        double mod = baseMod * statsAmp;
        return Math.max(0.1, mod);
    }

    /**
     * V25D27.1: Formation-defensive modifier (formation × teamDefense).
     *
     * <p>This is a PROTECTION factor — applied as DIVISION in {@link #calculateXg}
     * (not multiplication). A 5-3-2 with mod=1.25 means opponent's xG is divided
     * by 1.25 (i.e. 20% less xG conceded); a 4-3-3 with mod=0.85 means opponent's
     * xG is divided by 0.85 (i.e. 18% MORE xG conceded — wingers don't track back).
     *
     * <p>Formula: {@code mod = baseFormationDef × (1 + (teamDefense - 70) × 0.025)}
     * <ul>
     *   <li>teamDefense = 70 (median) → multiplier = 1.0 (no change)
     *   <li>teamDefense = 85 (elite) → multiplier = 1.375 (more protection)
     *   <li>teamDefense = 55 (weak) → multiplier = 0.625 (less protection)
     * </ul>
     *
     * <p>Per-formation base values (v2, corrected interpretation):
     * <ul>
     *   <li>4-4-2 → 1.00 (balanced)
     *   <li>4-3-3 → 0.85 (wingers don't defend → less protection → more goals conceded)
     *   <li>4-2-3-1 → 0.95 (double pivot screens defense, slight protection)
     *   <li>3-5-2 → 1.10 (3 CBs + wing-backs compress space, decent protection)
     *   <li>5-3-2 → 1.25 (back-five = strong protection, fewest goals conceded)
     *   <li>3-4-3 → 1.05 (3 CBs offset by advanced wing-backs)
     * </ul>
     */
    private double formationDefensiveModifier(String opponentFormation, double opponentDefense) {
        V24FormationParser parser = new V24FormationParser();
        V24FormationParser.V24Formation f = parser.parse(opponentFormation);
        String canonical = f.raw();
        double baseMod;
        if ("4-3-3".equals(canonical)) baseMod = 0.85;
        else if ("4-2-3-1".equals(canonical)) baseMod = 0.95;
        else if ("3-4-3".equals(canonical)) baseMod = 1.05;
        else if ("3-5-2".equals(canonical)) baseMod = 1.10;
        else if ("5-3-2".equals(canonical)) baseMod = 1.25;
        else baseMod = 1.00; // 4-4-2 baseline

        double statsAmp = 1.0 + (opponentDefense - 70.0) * 0.025;
        double mod = baseMod * statsAmp;
        return Math.max(0.1, mod);
    }

    private double shooterMultiplier(double shooterQuality) {
        // shooterQuality is normalized [0, 1] from attack attribute (0-99) + form (0-100)
        // Base: 0.95 at average quality, scale up/down
        return 0.70 + (shooterQuality * 0.60);
    }

    private double assistMultiplier(double assistQuality) {
        // assistQuality normalized [0, 1]
        return 0.85 + (assistQuality * 0.30);
    }

    private double defensiveMultiplier(double defensivePressure) {
        // defensivePressure normalized [0, 1]: 0 = no pressure, 1 = maximum pressure
        // High pressure reduces xG significantly
        return Math.max(0.30, 1.10 - (defensivePressure * 0.80));
    }

    private double goalkeeperMultiplier(double goalkeeperQuality) {
        // goalkeeperQuality normalized [0, 1]
        return Math.max(0.50, 1.05 - (goalkeeperQuality * 0.55));
    }

    private double styleMultiplier(double tacticModifier) {
        // tacticModifier is [0.5, 1.5]
        // Map to: DEFENSIVE=0.85, BALANCED=1.00, ATTACKING=1.15
        // clamp to [0.5, 1.5]
        double m = Math.max(0.5, Math.min(1.5, tacticModifier));
        // Scale to meaningful multiplier range [0.85, 1.15]
        return 0.85 + (m - 0.5) * 0.30;
    }

    private double clamp(double xg) {
        if (xg < MIN_XG) return MIN_XG;
        if (xg > MAX_XG) return MAX_XG;
        return Math.round(xg * 1000.0) / 1000.0;
    }
}