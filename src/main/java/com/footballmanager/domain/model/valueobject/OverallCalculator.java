package com.footballmanager.domain.model.valueobject;

import java.util.Map;

/**
 * V25D40 (Sprint C5): shared overall calculator for both {@code Player} and
 * {@code SessionPlayer} (V25D39 added the height + skills awareness to
 * {@code Player.getOverall()} but {@code SessionPlayer.calculateOverall()} —
 * the one actually consumed by the UI via {@code SessionPlayerDTO.overall} —
 * was left out of scope, so the change was invisible to users).
 *
 * <p>Formula (Opción A — additive bounded, shared by both paths):
 *
 * <pre>
 *   base          = position-weighted 6-stats formula  (5 valid categories + default average)
 *   skill_bonus   = (Σ skillValue * positionWeight[skill]) / 10   // bounded ~0..6.4
 *   height_factor = position-specific height adjustment          // bounded -2..+2
 *   total         = clamp(0, 99, base + skill_bonus + height_factor)
 * </pre>
 *
 * <p>Position categories (string, case-sensitive — matches {@code SessionPlayer.position}):
 * <ul>
 *   <li>{@code "GK"} — goalkeeper</li>
 *   <li>{@code "DEF"} — defenders (LB/CB/RB/LWB/RWB)</li>
 *   <li>{@code "MID"} — midfielders (CDM/CM/CAM/LM/RM)</li>
 *   <li>{@code "WINGER"} — wingers (LW/RW)</li>
 *   <li>{@code "ATT"} — attackers (CF/ST)</li>
 *   <li>any other value → default: simple arithmetic mean of the 6 stats</li>
 * </ul>
 *
 * <p>For {@code Player.getOverall()} (which uses the 15-value {@code Position} enum),
 * callers must map the enum to one of the 5 categories via
 * {@link #mapPlayerPositionToCategory(com.footballmanager.domain.model.entity.Player.Position)}
 * (or an inline switch in the caller). Keeping the utility string-based lets us
 * share one implementation across both entity layers without coupling the
 * {@code valueobject} package to the {@code entity} package.
 *
 * <h2>Backward compatibility</h2>
 * If {@code heightCm == null} AND {@code skillLevels} is null or empty,
 * returns the pre-V25D39 / pre-V25D40 base formula unchanged. This is the
 * contract that all existing tests, smoke flows, and the V25D39 base
 * (before C5) relied on.
 *
 * <h2>Skill weights table (sum ~0.65 per row → max bonus ≈ 6.4)</h2>
 * <table>
 *   <tr><th>Category</th><th>Weights</th><th>Sum</th></tr>
 *   <tr><td>GK</td><td>WALL 0.30, AERIAL 0.15, TACKLER 0.10, PASSER 0.10</td><td>0.65</td></tr>
 *   <tr><td>DEF</td><td>MARKER 0.25, AERIAL 0.20, TACKLER 0.15, PASSER 0.05</td><td>0.65</td></tr>
 *   <tr><td>MID</td><td>PLAYMAKER 0.30, PASSER 0.20, TACKLER 0.10, MARKER 0.05</td><td>0.65</td></tr>
 *   <tr><td>WINGER</td><td>SPEEDSTER 0.25, DRIBBLER 0.25, PASSER 0.10, SHOOTER 0.05</td><td>0.65</td></tr>
 *   <tr><td>ATT</td><td>SHOOTER 0.25, HEADER 0.20, DRIBBLER 0.10, SPEEDSTER 0.10</td><td>0.65</td></tr>
 * </table>
 *
 * <h2>Height factor table</h2>
 * <table>
 *   <tr><th>Category</th><th>Penalty</th><th>Neutral</th><th>Bonus</th></tr>
 *   <tr><td>GK</td><td>h &lt; 185 → -1</td><td>185 ≤ h &lt; 190</td><td>h ≥ 190 → +1; h ≥ 200 → +2</td></tr>
 *   <tr><td>DEF</td><td>h &lt; 175 → -2</td><td>175 ≤ h &lt; 190</td><td>h ≥ 190 → +2</td></tr>
 *   <tr><td>MID</td><td>h &lt; 170 → -1</td><td>170 ≤ h &lt; 190</td><td>h ≥ 190 → +1</td></tr>
 *   <tr><td>WINGER</td><td>h ≤ 170 → -1</td><td>170 &lt; h &lt; 185</td><td>h ≥ 185 → +1</td></tr>
 *   <tr><td>ATT</td><td>h &lt; 175 → -2</td><td>175 ≤ h &lt; 190</td><td>h ≥ 190 → +2</td></tr>
 * </table>
 *
 * <p>Note on WINGER boundary: closed at h ≤ 170 (not h &lt; 170). The V25D39
 * test {@code winger_skills99_height170} explicitly tests the boundary
 * penalty and asserts {@code base + 4} net — h=170 must trigger -1.
 *
 * <p>Out-of-bounds height (&lt; 160 or &gt; 210) is defensively clamped to the
 * nearest in-range value before lookup, so the formula never escapes [-2, +2].
 */
public final class OverallCalculator {

    private OverallCalculator() {
        // Pure utility — no instances.
    }

    /**
     * Calculates the overall from base stats + height + skills.
     *
     * @param attack    base attack stat
     * @param defense   base defense stat
     * @param technique base technique stat
     * @param speed     base speed stat
     * @param stamina   base stamina stat
     * @param mentality base mentality stat
     * @param position  category string: "GK" / "DEF" / "MID" / "WINGER" / "ATT" (or any other value → default)
     * @param heightCm  height in cm (nullable; null = no height factor)
     * @param skillLevels sparse map of skills (nullable; null or empty = no skill bonus)
     * @return overall in [0, 99], clamped
     */
    public static int calculate(int attack, int defense, int technique, int speed,
                                int stamina, int mentality,
                                String position, Integer heightCm,
                                Map<PlayerSkill, Integer> skillLevels) {
        int base = computeBaseOverall(attack, defense, technique, speed, stamina, mentality, position);

        // Backward compat: no height and no skills → return base formula unchanged.
        if (heightCm == null && (skillLevels == null || skillLevels.isEmpty())) {
            return base;
        }

        int skillBonus = computeSkillBonus(position, skillLevels);
        int heightFactor = computeHeightFactor(position, heightCm);

        int total = base + skillBonus + heightFactor;
        return Math.max(0, Math.min(99, total));
    }

    private static int computeBaseOverall(int attack, int defense, int technique, int speed,
                                          int stamina, int mentality, String position) {
        double overall;
        if ("GK".equals(position)) {
            overall = defense * 0.40 + technique * 0.20 + mentality * 0.20
                    + stamina * 0.10 + speed * 0.05 + attack * 0.05;
        } else if ("DEF".equals(position)) {
            overall = defense * 0.35 + technique * 0.15 + mentality * 0.15
                    + stamina * 0.15 + speed * 0.10 + attack * 0.10;
        } else if ("MID".equals(position)) {
            overall = technique * 0.30 + stamina * 0.20 + mentality * 0.15
                    + defense * 0.15 + speed * 0.10 + attack * 0.10;
        } else if ("WINGER".equals(position)) {
            overall = speed * 0.30 + attack * 0.25 + technique * 0.20
                    + stamina * 0.15 + mentality * 0.05 + defense * 0.05;
        } else if ("ATT".equals(position)) {
            overall = attack * 0.40 + technique * 0.20 + speed * 0.15
                    + mentality * 0.10 + stamina * 0.10 + defense * 0.05;
        } else {
            // Default (unknown position): simple arithmetic mean.
            overall = (attack + defense + technique + speed + stamina + mentality) / 6.0;
        }
        return (int) Math.round(overall);
    }

    private static int computeSkillBonus(String position, Map<PlayerSkill, Integer> skillLevels) {
        if (skillLevels == null || skillLevels.isEmpty()) {
            return 0;
        }
        Map<PlayerSkill, Double> weights = skillWeightsFor(position);
        if (weights.isEmpty()) {
            return 0;
        }
        double sum = 0.0;
        for (Map.Entry<PlayerSkill, Integer> entry : skillLevels.entrySet()) {
            Double weight = weights.get(entry.getKey());
            if (weight == null || entry.getValue() == null) {
                continue;
            }
            int clampedValue = Math.max(0, Math.min(99, entry.getValue()));
            sum += clampedValue * weight;
        }
        return (int) Math.round(sum / 10.0);
    }

    private static int computeHeightFactor(String position, Integer heightCm) {
        if (heightCm == null) {
            return 0;
        }
        int h = Math.max(160, Math.min(210, heightCm));

        if ("GK".equals(position)) {
            if (h >= 200) return 2;
            if (h >= 190) return 1;
            if (h < 185) return -1;
            return 0;
        }
        if ("DEF".equals(position)) {
            if (h >= 190) return 2;
            if (h < 175) return -2;
            return 0;
        }
        if ("MID".equals(position)) {
            if (h >= 190) return 1;
            if (h < 170) return -1;
            return 0;
        }
        if ("WINGER".equals(position)) {
            // Closed boundary at 170: matches V25D39 winger_skills99_height170 test expectation.
            if (h >= 185) return 1;
            if (h <= 170) return -1;
            return 0;
        }
        if ("ATT".equals(position)) {
            if (h >= 190) return 2;
            if (h < 175) return -2;
            return 0;
        }
        // Default (unknown position): height factor is 0.
        return 0;
    }

    /**
     * Per-position skill weight table. Each row sums to ~0.65 so the max
     * possible bonus is bounded at {@code 99 * 0.65 / 10 ≈ 6.4}. Skills not
     * listed for a position have weight 0 (ignored).
     */
    private static Map<PlayerSkill, Double> skillWeightsFor(String position) {
        if ("GK".equals(position)) {
            return Map.of(
                    PlayerSkill.WALL,    0.30,
                    PlayerSkill.AERIAL,  0.15,
                    PlayerSkill.TACKLER, 0.10,
                    PlayerSkill.PASSER,  0.10);
        }
        if ("DEF".equals(position)) {
            return Map.of(
                    PlayerSkill.MARKER,  0.25,
                    PlayerSkill.AERIAL,  0.20,
                    PlayerSkill.TACKLER, 0.15,
                    PlayerSkill.PASSER,  0.05);
        }
        if ("MID".equals(position)) {
            return Map.of(
                    PlayerSkill.PLAYMAKER, 0.30,
                    PlayerSkill.PASSER,    0.20,
                    PlayerSkill.TACKLER,   0.10,
                    PlayerSkill.MARKER,    0.05);
        }
        if ("WINGER".equals(position)) {
            return Map.of(
                    PlayerSkill.SPEEDSTER, 0.25,
                    PlayerSkill.DRIBBLER,  0.25,
                    PlayerSkill.PASSER,    0.10,
                    PlayerSkill.SHOOTER,   0.05);
        }
        if ("ATT".equals(position)) {
            return Map.of(
                    PlayerSkill.SHOOTER,   0.25,
                    PlayerSkill.HEADER,    0.20,
                    PlayerSkill.DRIBBLER,  0.10,
                    PlayerSkill.SPEEDSTER, 0.10);
        }
        return Map.of();
    }

    /**
     * Maps {@code Player.Position} enum (15 values) to the 5 category strings
     * consumed by {@link #calculate}. Kept here (not in {@code Player}) so
     * the utility class owns the position vocabulary that the engine layer
     * also uses via {@code SessionPlayer.position}.
     *
     * <p>Mapping (preserves the V25D39 Player.getOverall() internal grouping):
     * <ul>
     *   <li>{@code GK} → "GK"</li>
     *   <li>{@code LB/CB/RB/LWB/RWB} → "DEF"</li>
     *   <li>{@code CDM/CM/CAM/LM/RM} → "MID"</li>
     *   <li>{@code LW/RW} → "WINGER"</li>
     *   <li>{@code CF/ST} → "ATT"</li>
     * </ul>
     *
     * @return category string, or {@code null} for any unknown future enum value
     *         (caller should treat null as "default" — same behavior as the
     *         pre-V25D39 Player.getOverall() default branch)
     */
    public static String mapPlayerPositionToCategory(
            com.footballmanager.domain.model.entity.Player.Position position) {
        if (position == null) {
            return null;
        }
        return switch (position) {
            case GK -> "GK";
            case LB, CB, RB, LWB, RWB -> "DEF";
            case CDM, CM, CAM, LM, RM -> "MID";
            case LW, RW -> "WINGER";
            case CF, ST -> "ATT";
        };
    }
}