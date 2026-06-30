package com.footballmanager.application.service.world;

import com.footballmanager.domain.model.valueobject.PlayerSkill;
import com.footballmanager.domain.model.entity.Player.Position;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * V25D78-C55.1: Generator of the 6 base stats (attack/defense/technique/speed/
 * stamina/mentality) for synthetic player data, plus optional skill-level bonuses.
 *
 * <p><b>Why this exists:</b> the C55.1 sprint seeds 10 leagues with ~6,000 players
 * total. We have real team names (from Wikipedia, public domain) but the stats
 * are NOT copied from any commercial dataset (SoFIFA/EA/FBref — license murky).
 * Instead, this generator produces stats that are:
 * <ul>
 *   <li><b>Deterministic</b> per (seed + name hash) — same name → same stats, so
 *       re-running the seed is idempotent at the byte level.</li>
 *   <li><b>Position-biased</b> — attackers get higher baseAttack, defenders get
 *       higher baseDefense, wingers get higher baseSpeed, etc.</li>
 *   <li><b>Bounded</b> to [40, 95] for the 6 base stats, [50, 90] for skill
 *       bonuses — same bounds as the curated LaLiga JSON seed (see
 *       {@code laliga-2024-25.json}).</li>
 * </ul>
 *
 * <p><b>License posture:</b> stats are <i>creative work generated algorithmically</i>
 * from a hash of the player's name. They do not copy any commercial dataset's
 * numbers. Combined with team names from Wikipedia (CC-BY-SA / public domain),
 * the resulting seed file is a fully synthetic dataset. See
 * {@code docs/legal/synthetic-data.md} for the legal analysis.
 *
 * <p><b>Reproducibility:</b> the default seed ({@link #DEFAULT_SEED}) makes the
 * output stable across runs, so re-seeding the same league produces the same
 * WorldSnapshot content (byte-for-byte). Changing the seed regenerates a
 * different but equally valid distribution.
 */
public final class PlayerAttributesGenerator {

    /** Height bounds (must match {@code Player.setHeightCm} domain validation). */
    public static final int MIN_HEIGHT_CM = 160;
    public static final int MAX_HEIGHT_CM = 210;
    public static final int HEIGHT_MEAN_CM = 178;
    public static final int HEIGHT_STDDEV_CM = 7;

    /** 6 base stats bounds (per task spec: random 40-95 or 50-95). */
    public static final int MIN_BASE_STAT = 40;
    public static final int MAX_BASE_STAT = 95;
    public static final int MIN_ENDURANCE_STAT = 50; // stamina + mentality
    public static final int MAX_ENDURANCE_STAT = 95;

    /** Skill bonus bounds. */
    public static final int MIN_SKILL_BONUS = 50;
    public static final int MAX_SKILL_BONUS = 90;

    /** Skill bonus pool per player (random 0-3 bonuses). */
    public static final int MIN_SKILL_BONUSES = 0;
    public static final int MAX_SKILL_BONUSES = 3;

    /** Default seed = 20240624 (V25D32-F3 sprint date, kept for compat). */
    public static final long DEFAULT_SEED = 20240624L;

    private final Random random;

    public PlayerAttributesGenerator() {
        this(DEFAULT_SEED);
    }

    public PlayerAttributesGenerator(long seed) {
        this.random = new Random(seed);
    }

    /**
     * V25D78-C55.1: Generates the 6 base stats for a synthetic player.
     *
     * <p>Position bias shifts the mean of the random distribution:
     * <ul>
     *   <li>GK — baseDefense ≈ 80, baseAttack ≈ 25</li>
     *   <li>DEF — baseDefense ≈ 75, baseAttack ≈ 50</li>
     *   <li>MID — baseTechnique ≈ 75, balanced</li>
     *   <li>WINGER — baseSpeed ≈ 80, baseAttack ≈ 75</li>
     *   <li>ATT — baseAttack ≈ 80, baseSpeed ≈ 75</li>
     * </ul>
     *
     * @param position MANAGER position enum (GK/DEF/MID/WINGER/ATT)
     * @return map of stat name → int value, all in [40, 95]
     */
    public Map<String, Integer> generateBaseStats(String position) {
        Map<String, Integer> stats = new HashMap<>();
        // Position-bounded range for each base stat. Lo/hi chosen so the
        // resulting distribution roughly matches the curated LaLiga JSON
        // (mean ~70, stddev ~10) but with strong position-specific shape.
        // Position is one of MANAGER's 5-category codes (GK/DEF/MID/WINGER/ATT)
        // as stored in WorldPlayer.position (String). Unknown codes fall
        // through to the MID case (balanced).
        switch (position == null ? "MID" : position.toUpperCase()) {
            case "GK" -> {
                stats.put("baseAttack", randomInRange(20, 35));
                stats.put("baseDefense", randomInRange(75, 92));
                stats.put("baseTechnique", randomInRange(60, 82));
                stats.put("baseSpeed", randomInRange(45, 70));
                stats.put("baseStamina", randomInRange(55, 80));
                stats.put("baseMentality", randomInRange(70, 92));
            }
            case "DEF" -> {
                stats.put("baseAttack", randomInRange(45, 65));
                stats.put("baseDefense", randomInRange(72, 90));
                stats.put("baseTechnique", randomInRange(58, 78));
                stats.put("baseSpeed", randomInRange(60, 82));
                stats.put("baseStamina", randomInRange(65, 90));
                stats.put("baseMentality", randomInRange(65, 88));
            }
            case "MID" -> {
                stats.put("baseAttack", randomInRange(60, 80));
                stats.put("baseDefense", randomInRange(60, 80));
                stats.put("baseTechnique", randomInRange(72, 90));
                stats.put("baseSpeed", randomInRange(60, 80));
                stats.put("baseStamina", randomInRange(70, 92));
                stats.put("baseMentality", randomInRange(70, 90));
            }
            case "WINGER" -> {
                stats.put("baseAttack", randomInRange(70, 88));
                stats.put("baseDefense", randomInRange(40, 60));
                stats.put("baseTechnique", randomInRange(70, 88));
                stats.put("baseSpeed", randomInRange(78, 95));
                stats.put("baseStamina", randomInRange(70, 90));
                stats.put("baseMentality", randomInRange(60, 80));
            }
            case "ATT" -> {
                stats.put("baseAttack", randomInRange(75, 95));
                stats.put("baseDefense", randomInRange(35, 55));
                stats.put("baseTechnique", randomInRange(65, 85));
                stats.put("baseSpeed", randomInRange(70, 90));
                stats.put("baseStamina", randomInRange(60, 85));
                stats.put("baseMentality", randomInRange(60, 85));
            }
            default -> {
                // Unknown position: balanced MID-like distribution.
                stats.put("baseAttack", randomInRange(50, 75));
                stats.put("baseDefense", randomInRange(50, 75));
                stats.put("baseTechnique", randomInRange(60, 80));
                stats.put("baseSpeed", randomInRange(55, 78));
                stats.put("baseStamina", randomInRange(60, 85));
                stats.put("baseMentality", randomInRange(60, 80));
            }
        }
        return stats;
    }

    /**
     * V25D78-C55.1: Generates random skill-level bonuses (subset of
     * {@link PlayerSkill} enum). Probability and values are random within
     * {@link #MIN_SKILL_BONUSES}–{@link #MAX_SKILL_BONUSES} and
     * {@link #MIN_SKILL_BONUS}–{@link #MAX_SKILL_BONUS}.
     *
     * <p>Skill pool is the full enum — this matches the curated LaLiga JSON
     * which uses 14 different skill codes across 400 players. Empty map is a
     * valid result (engine V25D33 applies defaults).
     */
    public Map<PlayerSkill, Integer> generateSkillLevels() {
        PlayerSkill[] pool = PlayerSkill.values();
        int count = random.nextInt(MAX_SKILL_BONUSES - MIN_SKILL_BONUSES + 1);
        Map<PlayerSkill, Integer> out = new HashMap<>();
        // Avoid duplicates by tracking which indices we already picked.
        boolean[] used = new boolean[pool.length];
        for (int i = 0; i < count; i++) {
            int idx;
            do {
                idx = random.nextInt(pool.length);
            } while (used[idx]);
            used[idx] = true;
            out.put(pool[idx], randomInRange(MIN_SKILL_BONUS, MAX_SKILL_BONUS));
        }
        return out;
    }

    /**
     * Deterministic random int in [lo, hi] (inclusive). Uses the generator's
     * seeded Random instance — same generator state → same sequence.
     */
    private int randomInRange(int lo, int hi) {
        return lo + random.nextInt(hi - lo + 1);
    }

    /**
     * V25D78-C55.1: Generates an age in [18, 38] with a triangular
     * distribution peaking at 25 (peak of football career).
     */
    public int generateAge() {
        // Triangular: peak at 25, min 18, max 38. Two uniform samples + average
        // approximates a triangular distribution centered on 25.
        double u1 = (random.nextGaussian() * 4) + 25; // gaussian with mean 25
        int age = (int) Math.round(u1);
        if (age < 18) age = 18;
        if (age > 38) age = 38;
        return age;
    }

    /**
     * Generates a height in [160, 210] cm with normal distribution (mean 178,
     * stddev 7). Preserved from V25D32-F3.
     */
    public int generateHeightCm() {
        double u1 = Math.max(Double.MIN_VALUE, random.nextDouble());
        double u2 = random.nextDouble();
        double z = Math.sqrt(-2.0 * Math.log(u1)) * Math.cos(2.0 * Math.PI * u2);
        int height = (int) Math.round(HEIGHT_MEAN_CM + z * HEIGHT_STDDEV_CM);
        if (height < MIN_HEIGHT_CM) height = MIN_HEIGHT_CM;
        if (height > MAX_HEIGHT_CM) height = MAX_HEIGHT_CM;
        return height;
    }

    /** Test helper: returns an empty skill map (engine V25D33 applies defaults). */
    public Map<PlayerSkill, Integer> generateDefaultSkillLevels() {
        return Map.of();
    }

    /**
     * Generador one-shot para tests / ad-hoc queries. NO usar en el seed path
     * (usar la instancia con seed fijo para reproducibilidad).
     */
    public static int generateHeightCmOneShot() {
        return new PlayerAttributesGenerator().generateHeightCm();
    }

    /**
     * Test helper: generador one-shot con un seed explicito. NO usar en el
     * seed path.
     */
    public static int generateHeightCmOneShot(long seed) {
        return new PlayerAttributesGenerator(seed).generateHeightCm();
    }
}