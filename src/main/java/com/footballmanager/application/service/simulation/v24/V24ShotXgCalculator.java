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
 *       {@link V24ShotEventType} (only on CORNER / CROSS shots)</li>
 *       &ge; 185 cm (tall header specialist). Applied AFTER HEADER, gated on
 *       CORNER / CROSS like HEADER.</li>
 *       shots only (long-range specialist). Applied AFTER HEADER/AERIAL but
 *       BEFORE WALL divisor so WALL still compounds.</li>
 *       (no eventSubType gating) because 1v1 duels happen in any context.</li>
 *       {@link V24ShotEventType#OPEN_PLAY}). En corners/crosses NO aplica â€”
 *       modelo "entradas en juego abierto", no en balon parado.</li>
 * </ul>
 *
 * el overload 5-args delega al 9-args con {@code Map.of()} y {@code null},
 *
 * para gating del HEADER multiplier. El overload 9-args delega al 10-args
 * con {@code V24ShotEventType.OPEN_PLAY} (default), preservando el contrato
 *
 * {@link PlayerSkill#WALL}. WALL divisor: reduces xG by 1/(1 + skill/150).
 * With WALL=99, xg is divided by 1.66 (~40% reduction).
 * Stored as divisor value (1+skill/150), then {@code xg = ... / wallDivisor}
 * (NOT reciprocal). Sigue el memory lesson "modifier de proteccion/reduccion
 *
 * cuando {@code shooterHeightCm &ge; 185}. Formula:
 * {@code headerMult = (1 + headerSkill/200) * (1 + aerialSkill/300)}. Si
 * HEADER=0 y AERIAL=80 con height=190cm â†’ headerMult = 1.0 Ã— 1.267 = 1.267
 * (aun con HEADER ausente el AERIAL agrega bonus). Si HEADER=80 y AERIAL=80
 * con height=190cm â†’ headerMult = 1.4 Ã— 1.267 = 1.774 (+77%). Sin height o
 * con height &lt; 185 â†’ AERIAL NO aplica.
 *
 * {@link V24ShotLocation#LONG_RANGE}. Formula:
 * {@code shooterLongRangeMult = 1 + shooterSkill/250}. SHOOTER=0 o skill
 * ausente â†’ multiplier = 1.0 (sin cambio). SHOOTER=90 â†’ multiplier = 1.36
 * (+36%). El resto de las locations (SIX_YARD_BOX, PENALTY_AREA_*,
 * OUTSIDE_BOX) NO reciben bonus aunque tengan SHOOTER alto â€” modela "el
 * rematador long-range" (MbappÃ© style).
 *
 * overload 11-args con {@code defenderSkills}. Este overload 10-args delega
 * al 11-args con {@code Map.of()} (sin defending skills) â†’ MARKER y TACKLER
 * NO aplican (no-op para callers legacy que solo pasan hasta 10-args).
 *
 * MARKER=0 â†’ Ã—1.0 (no change). MARKER=90 â†’ Ã—0.70 (-30%). Aplica SIEMPRE
 * (en cualquier eventSubType) porque los duelos 1v1 ocurren en todo
 * contexto â€” corner, cross, open play, penalty.
 *
 * {@code xg *= (1 - skill/250)}. TACKLER=0 â†’ Ã—1.0 (no change). TACKLER=90
 * â†’ Ã—0.64 (-36%). Gated en {@link V24ShotEventType#OPEN_PLAY} â€” en corners
 * / crosses / penalties NO aplica (las entradas a balon parado son
 * diferentes y dependen de WALL mas que de TACKLER).
 *
 * <ul>
 *   <li>AERIAL absent o height &lt; 185 â†’ headerMult no cambia.</li>
 *   <li>SHOOTER absent o location != LONG_RANGE â†’ shooterLongRangeMult = 1.0.</li>
 *   <li>Overloads 5/9/10-args â†’ delegan al 11-args con {@code Map.of()}
 *       (sin defender skills) â†’ MARKER y TACKLER no aplican.</li>
 *   <li>Por lo tanto, los overloads 5-args / 9-args / 10-args con OPEN_PLAY
 *       defending skills.</li>
 * </ul>
 *
 */
public class V24ShotXgCalculator {

    private static final double MIN_XG = 0.01;
    // for a 6-yard box tap-in should not exceed ~0.50 in this tuned model.
    private static final double MAX_XG = 0.60;

    private static final double INSIDE_BOX_DISTANCE = 16.0; // meters from goal line
    private static final double SIX_YARD_BOX_DISTANCE = 8.0;

    /**
     * with default stats (attack=70, defense=70, opponent formation=4-4-2).
     */
    public double calculateXg(V24ShotQuality quality, String formation) {
        return calculateXg(quality, formation, "4-4-2", 70.0, 70.0);
    }

    /**
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
     *
     *
     * <p>Pipeline: baseXg Ã— shooter Ã— assist Ã— defensive Ã— gk Ã— style Ã—
     *   formationOffensive(possFormation, possessorAttack) Ã—
     *   formationDefensive(opponentFormation, opponentDefense).
     *
     * {@code V24ShotEventType.OPEN_PLAY} (default). Mantiene el contrato
     * el overload 10-args solo agrega el HEADER multiplier en F1 (WALL llega
     * HEADER/CORNER, deberan pasar al overload 10-args explicitamente.
     *
     * @param quality shot context (location, shooter, assist, pressure, GK, style)
     * @param formation the POSSESSOR's formation (e.g. "4-3-3")
     * @param opponentFormation the DEFENDING team's formation (e.g. "5-3-2")
     * @param possessorAttack aggregate attack stat of the possessor's attacking
     *                        players (avg of top-7 attackers, [0-99])
     * @param opponentDefense aggregate defense stat of the opponent's
     *                        defending players (avg of defenders + GK mentality, [0-99])
     */
    public double calculateXg(V24ShotQuality quality, String formation,
                              String opponentFormation,
                              double possessorAttack, double opponentDefense,
                              Map<PlayerSkill, Integer> shooterSkills, Integer shooterHeightCm,
                              Map<PlayerSkill, Integer> gkSkills, Integer gkHeightCm) {
        // CORNER/CROSS, y WALL todavia no esta implementado (F3).
        return calculateXg(quality, formation, opponentFormation,
                possessorAttack, opponentDefense,
                shooterSkills, shooterHeightCm, gkSkills, gkHeightCm,
                V24ShotEventType.OPEN_PLAY);
    }

    /**
     * para gating del HEADER multiplier.
     *
     * <ul>
     *   <li>HEADER multiplier ({@code 1.0 + skill/200.0}) se aplica SOLO
     *       cuando {@code eventSubType âˆˆ {CORNER, CROSS}}. En OPEN_PLAY el
     *       multiplier es 1.0 (sin cambio).</li>
     *   <li>El 9-args overload delega a este con OPEN_PLAY, preservando el
     * </ul>
     *
     * <ul>
     *   <li>WALL divisor ({@code 1.0 + skill/150.0}) se aplica cuando
     *       {@code gkSkills} contiene {@link PlayerSkill#WALL}. WALL=0 (o skill
     *       ausente) â†’ divisor = 1.0 (sin cambio). WALL=99 â†’ divisor = 1.66
     *       (xg / 1.66 â‰ˆ xg * 0.602, â‰ˆ40% menos xG). Stored as divisor value
     *       (1+skill/150), then xg = ... / wallDivisor (NOT reciprocal).</li>
     *   <li>WALL es un DIVISOR (no multiplicador) siguiendo el memory lesson
     *       DIVISOR. WALL=92 â†’ xg /= 1.613 (-38%); WALL=99 â†’ xg /= 1.66 (-40%).</li>
     * </ul>
     *
     * <ul>
     *   <li>AERIAL ({@code 1.0 + skill/300.0}) MULTIPLICA el HEADER multiplier
     *       cuando shooter height &ge; 185 cm. Si height &lt; 185 o ausente,
     *       AERIAL NO aplica. Compounding: HEADER=80 + AERIAL=80 + height=190
     *       â†’ headerMult = 1.4 Ã— 1.267 = 1.774 (+77%).</li>
     *   <li>SHOOTER ({@code 1.0 + skill/250.0}) aplica SOLO en
     *       {@link V24ShotLocation#LONG_RANGE}. SHOOTER=90 en LONG_RANGE â†’
     *       shooterLongRangeMult = 1.36 (+36%). El resto de las locations NO
     *       reciben bonus aunque SHOOTER sea alto.</li>
     *   <li>Ambos se aplican DESPUES del HEADER multiplier y ANTES del WALL
     *       divisor â€” el orden es: shooter/assist/def/gk/style/formation â†’
     *       HEADER (Ã—mult) â†’ AERIAL (Ã—mult si aplica) â†’ SHOOTER (Ã—mult si
     *       aplica) â†’ WALL (/div).</li>
     * </ul>
     *
     * <ul>
     *   <li>HEADER=0 â†’ multiplier = 1.0 (sin cambio)</li>
     *   <li>HEADER=80 â†’ multiplier = 1.40 (+40%)</li>
     *   <li>HEADER=99 â†’ multiplier = 1.495 (+49.5%)</li>
     * </ul>
     *
     * <ul>
     *   <li>AERIAL=0 o height &lt; 185 â†’ no compounding (headerMult unchanged)</li>
     *   <li>AERIAL=80, height=190 â†’ headerMult *= 1.267 (+26.7% adicional)</li>
     *   <li>AERIAL=99, height=190 â†’ headerMult *= 1.33 (+33% adicional)</li>
     * </ul>
     *
     * <ul>
     *   <li>SHOOTER=0 o location != LONG_RANGE â†’ multiplier = 1.0 (sin cambio)</li>
     *   <li>SHOOTER=90 en LONG_RANGE â†’ multiplier = 1.36 (+36%)</li>
     *   <li>SHOOTER=99 en LONG_RANGE â†’ multiplier = 1.396 (+39.6%)</li>
     * </ul>
     *
     * <ul>
     *   <li>WALL=0 â†’ divisor = 1.0 (sin cambio)</li>
     *   <li>WALL=92 â†’ divisor = 1 + 92/150 = 1.613 (xg / 1.613 â‰ˆ -38%)</li>
     *   <li>WALL=99 â†’ divisor = 1 + 99/150 = 1.660 (xg / 1.660 â‰ˆ -39.8%)</li>
     * </ul>
     *
     * @param quality shot context (location, shooter, assist, pressure, GK, style)
     * @param formation the POSSESSOR's formation (e.g. "4-3-3")
     * @param opponentFormation the DEFENDING team's formation (e.g. "5-3-2")
     * @param possessorAttack aggregate attack stat of the possessor's attacking
     *                        players (avg of top-7 attackers, [0-99])
     * @param opponentDefense aggregate defense stat of the opponent's
     *                        defending players (avg of defenders + GK mentality, [0-99])
     * @param shooterSkills sparse map de PlayerSkill levels del shooter (nullable;
     *                      and SHOOTER (LONG_RANGE bonus))
     * @param gkSkills sparse map de PlayerSkill levels del GK (nullable;
     * @param eventSubType origen del shot (OPEN_PLAY default). HEADER multiplier
     *                      se aplica SOLO cuando es CORNER o CROSS.
     */
    public double calculateXg(V24ShotQuality quality, String formation,
                              String opponentFormation,
                              double possessorAttack, double opponentDefense,
                              Map<PlayerSkill, Integer> shooterSkills, Integer shooterHeightCm,
                              Map<PlayerSkill, Integer> gkSkills, Integer gkHeightCm,
                              V24ShotEventType eventSubType) {
        // MARKER y TACKLER no aplican (no-op para callers legacy que solo
        return calculateXg(quality, formation, opponentFormation,
                possessorAttack, opponentDefense,
                shooterSkills, shooterHeightCm,
                gkSkills, gkHeightCm,
                eventSubType,
                Map.of(), null);
    }

    /**
     * {@code defenderHeightCm} para aplicar las defending skills MARKER y
     * TACKLER. El overload 10-args delega a este con {@code Map.of()} y
     * {@code null} (no-op para callers que no pasan defending skills).
     *
     * <p>{@code defenderSkills} representa el AVG de MARKER y TACKLER de los
     * defensores (DEF position) en cancha del equipo oponente. El caller
     * {@code aggregateOpponentDefenderSkills(...)} antes de invocar este
     * overload. Modelo simple (no individual 1v1 duel) â€” si en el futuro
     * se necesita marcador especifico por atacante, se puede refactor.
     *
     * <ul>
     *   <li>MARKER multiplier ({@code 1 - skill/300}) se aplica SIEMPRE
     *       (en cualquier eventSubType) â€” los duelos 1v1 ocurren en cualquier
     *       contexto (corner, cross, open play).</li>
     *   <li>TACKLER multiplier ({@code 1 - skill/250}) se aplica SOLO en
     *       {@link V24ShotEventType#OPEN_PLAY} â€” en corners/crosses no hay
     *       entradas abiertas.</li>
     *   <li>Ambos se aplican DESPUES del HEADER/AERIAL/SHOOTER y ANTES del
     *       WALL divisor (orden: ... * headerMult * shooterLongRangeMult
     *       * markerMult * tacklerMult / wallDivisor).</li>
     *   <li>Con {@code Map.of()} (defender skills vacios) o MARKER=0 y
     *       TACKLER=0 â†’ ambos multipliers = 1.0 â†’ resultado identico al
     *       overload 10-args.</li>
     * </ul>
     *
     * <ul>
     *   <li>MARKER=0 â†’ multiplier = 1.0 (sin cambio)</li>
     *   <li>MARKER=50 â†’ multiplier = 0.833 (-16.7%)</li>
     *   <li>MARKER=90 â†’ multiplier = 0.70 (-30%)</li>
     *   <li>MARKER=99 â†’ multiplier = 0.67 (-33%)</li>
     * </ul>
     *
     * <ul>
     *   <li>TACKLER=0 â†’ multiplier = 1.0 (sin cambio)</li>
     *   <li>TACKLER=50 â†’ multiplier = 0.80 (-20%)</li>
     *   <li>TACKLER=90 â†’ multiplier = 0.64 (-36%)</li>
     *   <li>TACKLER=99 â†’ multiplier = 0.604 (-39.6%)</li>
     * </ul>
     *
     * @param quality shot context (location, shooter, assist, pressure, GK, style)
     * @param formation the POSSESSOR's formation (e.g. "4-3-3")
     * @param opponentFormation the DEFENDING team's formation (e.g. "5-3-2")
     * @param possessorAttack aggregate attack stat of the possessor's attacking
     *                        players (avg of top-7 attackers, [0-99])
     * @param opponentDefense aggregate defense stat of the opponent's
     *                        defending players (avg of defenders + GK mentality, [0-99])
     * @param shooterSkills sparse map de PlayerSkill levels del shooter (nullable)
     * @param shooterHeightCm height del shooter en cm (nullable)
     * @param gkSkills sparse map de PlayerSkill levels del GK (nullable)
     * @param gkHeightCm height del GK en cm (nullable)
     * @param eventSubType origen del shot (OPEN_PLAY default). HEADER y TACKLER
     *                      tienen gating distinto (HEADER: CORNER/CROSS;
     *                      TACKLER: OPEN_PLAY only).
     * @param defenderSkills sparse map de PlayerSkill levels agregados de los
     *                       defensores del equipo oponente (nullable; empty map =
     *                       no defending skills). F2 reads MARKER + TACKLER.
     * @param defenderHeightCm height promedio de los defensores (nullable;
     */
    public double calculateXg(V24ShotQuality quality, String formation,
                              String opponentFormation,
                              double possessorAttack, double opponentDefense,
                              Map<PlayerSkill, Integer> shooterSkills, Integer shooterHeightCm,
                              Map<PlayerSkill, Integer> gkSkills, Integer gkHeightCm,
                              V24ShotEventType eventSubType,
                              Map<PlayerSkill, Integer> defenderSkills, Integer defenderHeightCm) {
        double baseXgVal = baseXg(quality.location());
        double shooterMult = shooterMultiplier(quality.shooterQuality());
        double assistMult = assistMultiplier(quality.assistQuality());
        double defMult = defensiveMultiplier(quality.defensivePressure());
        double gkMult = goalkeeperMultiplier(quality.goalkeeperQuality());
        double styleMult = styleMultiplier(quality.tacticModifier());
        double offFormMod = formationOffensiveModifier(formation, possessorAttack);
        double defFormMod = formationDefensiveModifier(opponentFormation, opponentDefense);

        // conceded (a 5-3-2 with defFormMod=1.25 means opponent xG is divided by 1.25,
        // (5-3-2 received MORE goals than 4-3-3). Confirmed by smoke: avg_AG for
        // 5-3-2 was 4.40 (highest) vs 4-3-3 at 2.97 (lowest) â€” wrong direction.

        // xG inflation when a high-attack team (e.g. Real Madrid, OVR 84) meets a
        // low-defense opponent (e.g. Deportivo Verde, OVR 60). Without the cap,
        // offFormMod/defFormMod reaches 2.98x, pushing most shots to MAX_XG=0.60
        // and producing 50%+ conversion (runtime smoke showed 7.14 goals/match avg).
        // With the cap, the ratio is bounded at 2.0x â€” strong teams still dominate
        // but match outcomes stay in [3.0, 4.5] for intermedios per spec.
        // The cap is symmetric (Math.max vs Math.min): if ratio is < 0.5 (defense
        // dominates offense by 2x), also clamped to 0.5 to keep defensive ceiling
        // meaningful. This protects against degenerate edges.
        //
// Pre-C31 empirical runtime: intermedios 5.45, top wins 100% (C30 smoke).
// The lower bound (Math.max(0.5, ...)) is preserved to keep defensive ceiling
// meaningful when defense dominates offense by 2x.
        double formationModRatio = offFormMod / defFormMod;
        formationModRatio = Math.max(0.5, formationModRatio);
        // Reapply clamped ratio back to offFormMod (defFormMod stays untouched
        // so the documentation and downstream uses of defFormMod as protection
        // factor remain semantically correct).
        offFormMod = formationModRatio * defFormMod;

        double headerMult = V24ShotSkillMultipliers.headerAndAerial(
            eventSubType, shooterSkills, shooterHeightCm);
        double shooterLongRangeMult = V24ShotSkillMultipliers.longRangeShooter(
            quality.location(), shooterSkills);
        double markerMult = V24ShotSkillMultipliers.marker(defenderSkills);
        double tacklerMult = V24ShotSkillMultipliers.tackler(eventSubType, defenderSkills);
        double wallDivisor = V24ShotSkillMultipliers.wallDivisor(gkSkills);
        double xg = baseXgVal * shooterMult * assistMult * defMult * gkMult * styleMult
                * offFormMod / defFormMod * headerMult * shooterLongRangeMult
                * markerMult * tacklerMult / wallDivisor;

        return clamp(xg);
    }

    // Fewer total attempts need each actual shot to represent a cleaner chance
    // than the old 40+ shot-noise model. These bases intentionally sit between
    // the older low-xG flood and real-world raw location xG; defensive/keeper/
    // skill layers still pull them down heavily in simulation.
    private double baseXg(V24ShotLocation location) {
        return switch (location) {
            case SIX_YARD_BOX -> 0.200;
            case PENALTY_AREA_CENTER -> 0.120;
            case PENALTY_AREA_WIDE -> 0.100;
            case OUTSIDE_BOX -> 0.040;
            case LONG_RANGE -> 0.020;
        };
    }

    /**
     *
     * it with the possessor's aggregate attack stat: an elite 4-3-3 squad
     * (attack avg â‰ˆ 85) gets a much larger offensive boost than a weak 4-3-3
     * (attack avg â‰ˆ 55). This addresses the user feedback that "formation Ã—
     * stats should sum" â€” a 4-3-3 with poor attackers is just a vulnerable 4-3-3,
     * not a free +40% xG.
     *
     * <p>Formula: {@code mod = baseFormationMod Ã— (1 + (teamAttack - 70) Ã— 0.012)}
     * <ul>
     *   <li>teamAttack = 70 (median) â†’ multiplier = 1.0 (no amplification)
     *   <li>teamAttack = 85 (elite) â†’ multiplier = 1.18
     *   <li>teamAttack = 55 (weak) â†’ multiplier = 0.82
     * </ul>
     *
     * reduced from 0.025 â†’ 0.012 to prevent extreme xG inflation in asymmetric
     * matchups (e.g. Real Madrid OVR=84 vs Deportivo Verde OVR=60 â†’ 2.98x xG
     * boost). The reduced coefficient still gives elite teams a meaningful
     * advantage (1.18x for OVR=85) but caps the asymptotic blowout potential.
     *
     * bonus/penalty. Shape effects now come from the persisted tactical slots in
     * amplification so two similarly shaped lineups behave similarly even if one
     * was selected from "4-4-2" and the other from "4-3-3".
     */
    private double formationOffensiveModifier(String formation, double teamAttack) {
        double baseMod = 1.00;

// Pre-C31 empirical runtime: intermedios 5.45, top wins 100% (C30 smoke).
// Elite teams (OVR=85) get 1.18x modifier (vs 1.09x with C31's 0.012).
        double statsAmp = 1.0 + (teamAttack - 70.0) * 0.025;
        double mod = baseMod * statsAmp;
        return Math.max(0.1, mod);
    }

    /**
     *
     * <p>This is a PROTECTION factor â€” applied as DIVISION in {@link #calculateXg}
     * (not multiplication). A 5-3-2 with mod=1.25 means opponent's xG is divided
     * by 1.25 (i.e. 20% less xG conceded); a 4-3-3 with mod=0.85 means opponent's
     * xG is divided by 0.85 (i.e. 18% MORE xG conceded â€” wingers don't track back).
     *
     * <p>Formula: {@code mod = baseFormationDef Ã— (1 + (teamDefense - 70) Ã— 0.012)}
     * <ul>
     *   <li>teamDefense = 70 (median) â†’ multiplier = 1.0 (no change)
     *   <li>teamDefense = 85 (elite) â†’ multiplier = 1.18 (more protection)
     *   <li>teamDefense = 55 (weak) â†’ multiplier = 0.82 (less protection)
     * </ul>
     *
     * reduced from 0.025 â†’ 0.012 (matches formationOffensiveModifier change).
     *
     * Defensive protection now comes from the actual tactical shape (defensive
     */
    private double formationDefensiveModifier(String opponentFormation, double opponentDefense) {
        double baseMod = 1.00;

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
