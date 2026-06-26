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
 *   <li>V25D33-F3: WALL skill divisor on xG (reduces xG when GK has WALL).</li>
 *   <li>V25D34-F1: AERIAL skill COMPOUNDS with HEADER when shooter height
 *       &ge; 185 cm (tall header specialist). Applied AFTER HEADER, gated on
 *       CORNER / CROSS like HEADER.</li>
 *   <li>V25D34-F1: SHOOTER skill adds xG bonus on {@link V24ShotLocation#LONG_RANGE}
 *       shots only (long-range specialist). Applied AFTER HEADER/AERIAL but
 *       BEFORE WALL divisor so WALL still compounds.</li>
 *   <li>V25D34-F2: MARKER skill reduces xG (1v1 marcaje). Applied ALWAYS
 *       (no eventSubType gating) because 1v1 duels happen in any context.</li>
 *   <li>V25D34-F2: TACKLER skill reduces xG in open play only (gated on
 *       {@link V24ShotEventType#OPEN_PLAY}). En corners/crosses NO aplica —
 *       modelo "entradas en juego abierto", no en balon parado.</li>
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
 * <p>V25D33-F3: WALL divisor se aplica cuando {@code gkSkills} contiene
 * {@link PlayerSkill#WALL}. Como DIVISOR (1.0 / (1.0 + skill/150.0)) — mas
 * WALL → menos xG. Sigue el memory lesson "modifier de proteccion/reduccion
 * va como DIVISOR, no multiplicador" (V25D27.1 — formationDefensiveModifier).
 *
 * <p>V25D34-F1 (AERIAL): AERIAL compoundea con HEADER en tiros CORNER/CROSS
 * cuando {@code shooterHeightCm &ge; 185}. Formula:
 * {@code headerMult = (1 + headerSkill/200) * (1 + aerialSkill/300)}. Si
 * HEADER=0 y AERIAL=80 con height=190cm → headerMult = 1.0 × 1.267 = 1.267
 * (aun con HEADER ausente el AERIAL agrega bonus). Si HEADER=80 y AERIAL=80
 * con height=190cm → headerMult = 1.4 × 1.267 = 1.774 (+77%). Sin height o
 * con height &lt; 185 → AERIAL NO aplica.
 *
 * <p>V25D34-F1 (SHOOTER): SHOOTER aplica bonus xG SOLO en
 * {@link V24ShotLocation#LONG_RANGE}. Formula:
 * {@code shooterLongRangeMult = 1 + shooterSkill/250}. SHOOTER=0 o skill
 * ausente → multiplier = 1.0 (sin cambio). SHOOTER=90 → multiplier = 1.36
 * (+36%). El resto de las locations (SIX_YARD_BOX, PENALTY_AREA_*,
 * OUTSIDE_BOX) NO reciben bonus aunque tengan SHOOTER alto — modela "el
 * rematador long-range" (Mbappé style).
 *
 * <p>V25D34-F2 (MARKER + TACKLER): defending skills. Se aplican via el
 * overload 11-args con {@code defenderSkills}. Este overload 10-args delega
 * al 11-args con {@code Map.of()} (sin defending skills) → MARKER y TACKLER
 * NO aplican (no-op para callers legacy que solo pasan hasta 10-args).
 *
 * <p>V25D34-F2 (MARKER): 1v1 marcaje. Formula: {@code xg *= (1 - skill/300)}.
 * MARKER=0 → ×1.0 (no change). MARKER=90 → ×0.70 (-30%). Aplica SIEMPRE
 * (en cualquier eventSubType) porque los duelos 1v1 ocurren en todo
 * contexto — corner, cross, open play, penalty.
 *
 * <p>V25D34-F2 (TACKLER): entradas en juego abierto. Formula:
 * {@code xg *= (1 - skill/250)}. TACKLER=0 → ×1.0 (no change). TACKLER=90
 * → ×0.64 (-36%). Gated en {@link V24ShotEventType#OPEN_PLAY} — en corners
 * / crosses / penalties NO aplica (las entradas a balon parado son
 * diferentes y dependen de WALL mas que de TACKLER).
 *
 * <p>No-op regression (V25D33 baseline preservation):
 * <ul>
 *   <li>AERIAL absent o height &lt; 185 → headerMult no cambia.</li>
 *   <li>SHOOTER absent o location != LONG_RANGE → shooterLongRangeMult = 1.0.</li>
 *   <li>Overloads 5/9/10-args → delegan al 11-args con {@code Map.of()}
 *       (sin defender skills) → MARKER y TACKLER no aplican.</li>
 *   <li>Por lo tanto, los overloads 5-args / 9-args / 10-args con OPEN_PLAY
 *       producen resultado bit-a-bit identico a V25D33 cuando no se pasan
 *       defending skills.</li>
 * </ul>
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
     * <p>V25D33-F3 implementation:
     * <ul>
     *   <li>WALL divisor ({@code 1.0 + skill/150.0}) se aplica cuando
     *       {@code gkSkills} contiene {@link PlayerSkill#WALL}. WALL=0 (o skill
     *       ausente) → divisor = 1.0 (sin cambio). WALL=99 → divisor = 1.66
     *       (xg / 1.66 ≈ xg * 0.602, ≈40% menos xG). Stored as divisor value
     *       (1+skill/150), then xg = ... / wallDivisor (NOT reciprocal).</li>
     *   <li>WALL es un DIVISOR (no multiplicador) siguiendo el memory lesson
     *       de V25D27.1 — modifiers de proteccion/reduccion siempre van como
     *       DIVISOR. WALL=92 → xg /= 1.613 (-38%); WALL=99 → xg /= 1.66 (-40%).</li>
     * </ul>
     *
     * <p>V25D34-F1 implementation (AERIAL compounding + SHOOTER long-range):
     * <ul>
     *   <li>AERIAL ({@code 1.0 + skill/300.0}) MULTIPLICA el HEADER multiplier
     *       cuando shooter height &ge; 185 cm. Si height &lt; 185 o ausente,
     *       AERIAL NO aplica. Compounding: HEADER=80 + AERIAL=80 + height=190
     *       → headerMult = 1.4 × 1.267 = 1.774 (+77%).</li>
     *   <li>SHOOTER ({@code 1.0 + skill/250.0}) aplica SOLO en
     *       {@link V24ShotLocation#LONG_RANGE}. SHOOTER=90 en LONG_RANGE →
     *       shooterLongRangeMult = 1.36 (+36%). El resto de las locations NO
     *       reciben bonus aunque SHOOTER sea alto.</li>
     *   <li>Ambos se aplican DESPUES del HEADER multiplier y ANTES del WALL
     *       divisor — el orden es: shooter/assist/def/gk/style/formation →
     *       HEADER (×mult) → AERIAL (×mult si aplica) → SHOOTER (×mult si
     *       aplica) → WALL (/div).</li>
     * </ul>
     *
     * <p>Calibration del HEADER multiplier (spec V25D33-F1):
     * <ul>
     *   <li>HEADER=0 → multiplier = 1.0 (sin cambio)</li>
     *   <li>HEADER=80 → multiplier = 1.40 (+40%)</li>
     *   <li>HEADER=99 → multiplier = 1.495 (+49.5%)</li>
     * </ul>
     *
     * <p>Calibration del AERIAL compounding (spec V25D34-F1):
     * <ul>
     *   <li>AERIAL=0 o height &lt; 185 → no compounding (headerMult unchanged)</li>
     *   <li>AERIAL=80, height=190 → headerMult *= 1.267 (+26.7% adicional)</li>
     *   <li>AERIAL=99, height=190 → headerMult *= 1.33 (+33% adicional)</li>
     * </ul>
     *
     * <p>Calibration del SHOOTER bonus (spec V25D34-F1):
     * <ul>
     *   <li>SHOOTER=0 o location != LONG_RANGE → multiplier = 1.0 (sin cambio)</li>
     *   <li>SHOOTER=90 en LONG_RANGE → multiplier = 1.36 (+36%)</li>
     *   <li>SHOOTER=99 en LONG_RANGE → multiplier = 1.396 (+39.6%)</li>
     * </ul>
     *
     * <p>Calibration del WALL divisor (spec V25D33-F3):
     * <ul>
     *   <li>WALL=0 → divisor = 1.0 (sin cambio)</li>
     *   <li>WALL=92 → divisor = 1 + 92/150 = 1.613 (xg / 1.613 ≈ -38%)</li>
     *   <li>WALL=99 → divisor = 1 + 99/150 = 1.660 (xg / 1.660 ≈ -39.8%)</li>
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
     *                      absent → treat as 0; V25D33-F1 reads HEADER,
     *                      V25D34-F1 reads AERIAL (compounding with HEADER)
     *                      and SHOOTER (LONG_RANGE bonus))
     * @param shooterHeightCm height del shooter en cm (nullable; V25D33 lo ignora,
     *                        reservado para V25D34)
     * @param gkSkills sparse map de PlayerSkill levels del GK (nullable;
     *                  F3 reads WALL; V25D34 will read AERIAL + others)
     * @param gkHeightCm height del GK en cm (nullable; V25D33 lo ignora)
     * @param eventSubType origen del shot (OPEN_PLAY default). HEADER multiplier
     *                      se aplica SOLO cuando es CORNER o CROSS.
     */
    public double calculateXg(V24ShotQuality quality, String formation,
                              String opponentFormation,
                              double possessorAttack, double opponentDefense,
                              Map<PlayerSkill, Integer> shooterSkills, Integer shooterHeightCm,
                              Map<PlayerSkill, Integer> gkSkills, Integer gkHeightCm,
                              V24ShotEventType eventSubType) {
        // V25D34-F2: delega al overload 11-args con defenderSkills vacios.
        // MARKER y TACKLER no aplican (no-op para callers legacy que solo
        // pasan hasta 10-args). Bit-a-bit backward compat con V25D33.
        return calculateXg(quality, formation, opponentFormation,
                possessorAttack, opponentDefense,
                shooterSkills, shooterHeightCm,
                gkSkills, gkHeightCm,
                eventSubType,
                Map.of(), null);
    }

    /**
     * V25D34-F2: overload 11-args que agrega {@code defenderSkills} y
     * {@code defenderHeightCm} para aplicar las defending skills MARKER y
     * TACKLER. El overload 10-args delega a este con {@code Map.of()} y
     * {@code null} (no-op para callers que no pasan defending skills).
     *
     * <p>{@code defenderSkills} representa el AVG de MARKER y TACKLER de los
     * defensores (DEF position) en cancha del equipo oponente. El caller
     * (V24DetailedMatchEngine.attemptShot) agrega via
     * {@code aggregateOpponentDefenderSkills(...)} antes de invocar este
     * overload. Modelo simple (no individual 1v1 duel) — si en el futuro
     * se necesita marcador especifico por atacante, se puede refactor.
     *
     * <p>V25D34-F2 implementation:
     * <ul>
     *   <li>MARKER multiplier ({@code 1 - skill/300}) se aplica SIEMPRE
     *       (en cualquier eventSubType) — los duelos 1v1 ocurren en cualquier
     *       contexto (corner, cross, open play).</li>
     *   <li>TACKLER multiplier ({@code 1 - skill/250}) se aplica SOLO en
     *       {@link V24ShotEventType#OPEN_PLAY} — en corners/crosses no hay
     *       entradas abiertas.</li>
     *   <li>Ambos se aplican DESPUES del HEADER/AERIAL/SHOOTER y ANTES del
     *       WALL divisor (orden: ... * headerMult * shooterLongRangeMult
     *       * markerMult * tacklerMult / wallDivisor).</li>
     *   <li>Con {@code Map.of()} (defender skills vacios) o MARKER=0 y
     *       TACKLER=0 → ambos multipliers = 1.0 → resultado identico al
     *       overload 10-args.</li>
     * </ul>
     *
     * <p>Calibration del MARKER multiplier (spec V25D34-F2):
     * <ul>
     *   <li>MARKER=0 → multiplier = 1.0 (sin cambio)</li>
     *   <li>MARKER=50 → multiplier = 0.833 (-16.7%)</li>
     *   <li>MARKER=90 → multiplier = 0.70 (-30%)</li>
     *   <li>MARKER=99 → multiplier = 0.67 (-33%)</li>
     * </ul>
     *
     * <p>Calibration del TACKLER multiplier (spec V25D34-F2):
     * <ul>
     *   <li>TACKLER=0 → multiplier = 1.0 (sin cambio)</li>
     *   <li>TACKLER=50 → multiplier = 0.80 (-20%)</li>
     *   <li>TACKLER=90 → multiplier = 0.64 (-36%)</li>
     *   <li>TACKLER=99 → multiplier = 0.604 (-39.6%)</li>
     * </ul>
     *
     * @param quality shot context (location, shooter, assist, pressure, GK, style)
     * @param formation the POSSESSOR's formation (e.g. "4-3-3")
     * @param opponentFormation the DEFENDING team's formation (e.g. "5-3-2")
     * @param possessorAttack aggregate attack stat of the possessor's attacking
     *                        players (avg of top-5 attackers, [0-99])
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
     *                          reservado para uso futuro; V25D34-F2 no lo usa).
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

            // V25D34-F1: AERIAL compounds with HEADER when shooter height ≥ 185 cm.
            // Models "jugador alto cabeceador" — el bonus realista cuando un
            // rematador de cabeza tiene ademas la altura para cabecear en el
            // punto penal. Si height < 185 o ausente, AERIAL NO aplica (el
            // jugador cabecea pero no domina el juego aereo).
            // Gated en el mismo branch CORNER/CROSS que HEADER (no aplica en
            // open play).
            int aerialSkill = (shooterSkills != null && shooterSkills.get(PlayerSkill.AERIAL) != null)
                    ? shooterSkills.get(PlayerSkill.AERIAL)
                    : 0;
            if (aerialSkill > 0 && shooterHeightCm != null && shooterHeightCm >= 185) {
                headerMult *= 1.0 + (aerialSkill / 300.0);
            }
        }

        // V25D34-F1: SHOOTER bonus xG en tiros fuera del area (LONG_RANGE only).
        // Models "rematador long-range" (Mbappé style) — solo dispara fuerte
        // desde afuera del box. En SIX_YARD_BOX / PENALTY_AREA_* / OUTSIDE_BOX
        // SHOOTER NO aporta (ahi manda HEADER, técnica, etc.).
        int shooterSkillLevel = (shooterSkills != null && shooterSkills.get(PlayerSkill.SHOOTER) != null)
                ? shooterSkills.get(PlayerSkill.SHOOTER)
                : 0;
        double shooterLongRangeMult = 1.0;
        if (shooterSkillLevel > 0 && quality.location() == V24ShotLocation.LONG_RANGE) {
            shooterLongRangeMult = 1.0 + (shooterSkillLevel / 250.0);
        }

        // V25D34-F2: MARKER reduces xG en duelos 1v1 (modelo "avg defender
        // skill" — no individual duel). Aplica SIEMPRE (en cualquier
        // eventSubType) porque los duelos 1v1 ocurren en todo contexto.
        // Formula: markerMult = 1 - skill/300. MARKER=0 o ausente → 1.0.
        // MARKER=90 → 0.70 (-30%).
        int markerSkill = (defenderSkills != null && defenderSkills.get(PlayerSkill.MARKER) != null)
                ? defenderSkills.get(PlayerSkill.MARKER)
                : 0;
        double markerMult = 1.0 - (markerSkill / 300.0);

        // V25D34-F2: TACKLER reduces xG en juego abierto. Aplica SOLO en
        // OPEN_PLAY (no en corners/crosses — ahi manda WALL). Formula:
        // tacklerMult = 1 - skill/250. TACKLER=0 o ausente → 1.0. TACKLER=90
        // → 0.64 (-36%).
        double tacklerMult = 1.0;
        if (eventSubType == V24ShotEventType.OPEN_PLAY) {
            int tacklerSkill = (defenderSkills != null && defenderSkills.get(PlayerSkill.TACKLER) != null)
                    ? defenderSkills.get(PlayerSkill.TACKLER)
                    : 0;
            tacklerMult = 1.0 - (tacklerSkill / 250.0);
        }

        // V25D33-F3: WALL divisor on xG. Memory lesson "modifier de proteccion
        // /reduccion va como DIVISOR" (V25D27.1 — formationDefensiveModifier).
        // WALL=0 o skill ausente → divisor = 1.0 (sin cambio). WALL=99 →
        // divisor = 1 + 99/150 = 1.66 → xg / 1.66 ≈ xg * 0.602 (≈40% menos
        // xG). Aplicado DESPUES del HEADER multiplier para que HEADER (shooter)
        // y WALL (GK) compongan en cualquier combinacion.
        double wallDivisor = 1.0;
        if (gkSkills != null && gkSkills.get(PlayerSkill.WALL) != null) {
            int wallSkill = gkSkills.get(PlayerSkill.WALL);
            wallDivisor = 1.0 + (wallSkill / 150.0);
        }

        double xg = baseXgVal * shooterMult * assistMult * defMult * gkMult * styleMult
                * offFormMod / defFormMod * headerMult * shooterLongRangeMult
                * markerMult * tacklerMult / wallDivisor;

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