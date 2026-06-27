package com.footballmanager.domain.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.footballmanager.domain.model.valueobject.OverallCalculator;
import com.footballmanager.domain.model.valueobject.PlayerSkill;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * SessionPlayer - Jugador que existe SOLO en Redis durante una carrera.
 * NO se persiste en base de datos real.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SessionPlayer {

    // Identity
    private String sessionPlayerId;
    private UUID basePlayerId;
    private String worldPlayerId;

    // Core info
    private String name;
    private Integer age;
    private String position;

    // Attributes
    private Integer attack;
    private Integer defense;
    private Integer technique;
    private Integer speed;
    private Integer stamina;
    private Integer mentality;
    private BigDecimal marketValue;

    // Dynamic state
    private Integer energy;
    private Integer form;
    private Boolean injured;
    private String injuryType;
    private Integer injuryRemainingMatches;
    private Integer matchesPlayedInRow;
    private Integer yellowCards;
    private Integer redCards;
    private Boolean suspended;
    private Integer suspensionRemainingMatches;

    // Origin
    private SessionPlayerOrigin origin;

    // V25D31 - Physical + skill metadata
    private Integer heightCm;
    private Map<PlayerSkill, Integer> skillLevels;

    // V25D31 - Bounds for skills and height
    private static final int MIN_SKILL_LEVEL = 0;
    private static final int MAX_SKILL_LEVEL = 99;
    private static final int MIN_HEIGHT_CM = 160;
    private static final int MAX_HEIGHT_CM = 210;

    public enum SessionPlayerOrigin { CLONED, CUSTOM, RANDOM }

    // ========== Factory Methods ==========

    // Alias for backward compatibility (V25D33-F0-mapping: delegates to height+skills-aware overload with nulls)
    public static SessionPlayer cloneFromWorldPlayer(String worldPlayerId, String name,
            String position, Integer age, Integer overall, String currentTeamId) {
        return fromWorldPlayer(worldPlayerId, name, position, age, overall, null, null);
    }

    // V25D33-F0-mapping: overload with height + skill propagation from WorldPlayer
    // (V25D32 SENIOR flag: WorldPlayer -> SessionPlayer mapping was leaking height/skills).
    // Existing 5-arg overload delegates here with null/empty so behavior is bit-a-bit
    // preserved for callers that do not yet propagate the new fields.
    public static SessionPlayer cloneFromWorldPlayer(String worldPlayerId, String name,
            String position, Integer age, Integer overall, String currentTeamId,
            Integer heightCm, Map<PlayerSkill, Integer> skillLevels) {
        return fromWorldPlayer(worldPlayerId, name, position, age, overall, heightCm, skillLevels);
    }

    public static SessionPlayer fromWorldPlayer(String worldPlayerId, String name,
            String position, Integer age, Integer overall) {
        return fromWorldPlayer(worldPlayerId, name, position, age, overall, null, null);
    }

    /**
     * V25D33-F0-mapping: full factory with height + skill propagation.
     *
     * <p>Replaces the pre-V25D33 factory that silently dropped {@code heightCm}
     * and {@code skillLevels} from the {@link WorldPlayer}. The V25D32
     * {@code LaLigaSeedService} sets these on WorldPlayer (top-20 heights
     * hardcoded + curated skills for top-5), but the clone into SessionPlayer
     * was losing them. With V25D33 the clone propagates both, so the engine
     * can read them via {@link V24PlayerMatchState#heightCm()} and
     * {@link V24PlayerMatchState#skillLevels()}.
     *
     * <p>Null/empty inputs are normalized:
     * <ul>
     *   <li>{@code heightCm=null} → SessionPlayer.heightCm remains null (sparse).</li>
     *   <li>{@code skillLevels=null or empty} → SessionPlayer.skillLevels is empty map
     *       (engine treats absent skills as 0).</li>
     *   <li>Defensive copy on {@code skillLevels} so caller mutations cannot
     *       leak into the SessionPlayer's sparse map.</li>
     * </ul>
     */
    public static SessionPlayer fromWorldPlayer(String worldPlayerId, String name,
            String position, Integer age, Integer overall,
            Integer heightCm, Map<PlayerSkill, Integer> skillLevels) {
        SessionPlayer p = new SessionPlayer();
        p.sessionPlayerId = worldPlayerId;
        p.worldPlayerId = worldPlayerId;
        p.basePlayerId = null;
        p.name = name;
        p.age = age;
        p.position = position;
        p.setAttributesFromOverall(overall);
        p.initDefaults();
        p.origin = SessionPlayerOrigin.CLONED;
        // V25D33-F0-mapping: propagate physical + skill metadata via the
        // bounds-checked setters. setHeightCm accepts null; setSkillLevel is
        // per-entry so we iterate the (possibly null/empty) map defensively.
        if (heightCm != null) {
            p.setHeightCm(heightCm);
        }
        if (skillLevels != null && !skillLevels.isEmpty()) {
            for (Map.Entry<PlayerSkill, Integer> e : skillLevels.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    // setSkillLevel bounds-checks [0,99] and removes zero entries
                    // (sparse map). Defensive copy already done by WorldPlayer.getSkillLevels().
                    p.setSkillLevel(e.getKey(), e.getValue());
                }
            }
        }
        return p;
    }

    public static SessionPlayer fromRealPlayer(UUID realPlayerId, String worldPlayerId,
            String name, String position, Integer overall) {
        SessionPlayer p = new SessionPlayer();
        p.sessionPlayerId = UUID.randomUUID().toString();
        p.basePlayerId = realPlayerId;
        p.worldPlayerId = worldPlayerId;
        p.name = name;
        p.age = 25;
        p.position = position;
        p.setAttributesFromOverall(overall);
        p.initDefaults();
        p.origin = SessionPlayerOrigin.CLONED;
        return p;
    }

    public static SessionPlayer custom(String name, Integer age, String position,
            Integer attack, Integer defense, Integer technique,
            Integer speed, Integer stamina, Integer mentality,
            BigDecimal marketValue) {
        SessionPlayer p = new SessionPlayer();
        p.sessionPlayerId = UUID.randomUUID().toString();
        p.basePlayerId = null;
        p.worldPlayerId = null;
        p.name = name;
        p.age = age;
        p.position = position;
        p.attack = attack;
        p.defense = defense;
        p.technique = technique;
        p.speed = speed;
        p.stamina = stamina;
        p.mentality = mentality;
        p.marketValue = marketValue;
        p.initDefaults();
        p.origin = SessionPlayerOrigin.CUSTOM;
        return p;
    }

    private void setAttributesFromOverall(Integer overall) {
        this.attack = overall;
        this.defense = overall;
        this.technique = overall;
        this.speed = overall;
        this.stamina = overall;
        this.mentality = overall;
        this.marketValue = BigDecimal.valueOf(overall * 100000L);
    }

    private void initDefaults() {
        this.energy = 100;
        this.form = 50;
        this.injured = false;
        this.injuryType = null;
        this.injuryRemainingMatches = 0;
        this.matchesPlayedInRow = 0;
        this.yellowCards = 0;
        this.redCards = 0;
        this.suspended = false;
        this.suspensionRemainingMatches = 0;
        // V25D31: skill map default empty (sparse). Height remains null until seeder/clone sets it.
        this.skillLevels = new HashMap<>();
    }

    // ========== Business Logic ==========

    public Integer calculateOverall() {
        if (hasNullAttributes()) return 50;

        // V25D40 (Sprint C5): delegate to the shared {@link OverallCalculator}.
        // Before this refactor, SessionPlayer and Player had duplicated switch
        // statements with the same weights, and only Player was extended to
        // consider height + skills in V25D39 — leaving the engine path
        // (consumed by the UI via SessionPlayerDTO.overall) with stale overalls.
        // Now both paths share one source of truth for the formula.
        int raw = OverallCalculator.calculate(
                attack, defense, technique, speed, stamina, mentality,
                position, heightCm, skillLevels);
        return raw;
    }

    private boolean hasNullAttributes() {
        return attack == null || defense == null || technique == null ||
               speed == null || stamina == null || mentality == null;
    }

    // ========== Getters ==========

    public String getSessionPlayerId() { return sessionPlayerId; }
    public UUID getBasePlayerId() { return basePlayerId; }
    public String getWorldPlayerId() { return worldPlayerId; }
    public String getName() { return name; }
    public Integer getAge() { return age; }
    public String getPosition() { return position; }
    public Integer getAttack() { return attack; }
    public Integer getDefense() { return defense; }
    public Integer getTechnique() { return technique; }
    public Integer getSpeed() { return speed; }
    public Integer getStamina() { return stamina; }
    public Integer getMentality() { return mentality; }
    public BigDecimal getMarketValue() { return marketValue; }
    public Integer getEnergy() { return energy; }
    public Integer getForm() { return form; }
    public Boolean getInjured() { return injured; }
    public String getInjuryType() { return injuryType; }
    public Integer getInjuryRemainingMatches() { return injuryRemainingMatches; }
    public Integer getMatchesPlayedInRow() { return matchesPlayedInRow; }
    public Integer getYellowCards() { return yellowCards != null ? yellowCards : 0; }
    public Integer getRedCards() { return redCards != null ? redCards : 0; }
    public Boolean getSuspended() { return suspended != null ? suspended : false; }
    public Integer getSuspensionRemainingMatches() { return suspensionRemainingMatches != null ? suspensionRemainingMatches : 0; }
    public SessionPlayerOrigin getOrigin() { return origin; }

    // ========== V25D31 - Height + Skills ==========

    public Integer getHeightCm() { return heightCm; }

    public void setHeightCm(Integer heightCm) {
        if (heightCm == null) {
            this.heightCm = null;
            return;
        }
        if (heightCm < MIN_HEIGHT_CM || heightCm > MAX_HEIGHT_CM) {
            throw new IllegalArgumentException(
                    "heightCm must be between " + MIN_HEIGHT_CM + " and " + MAX_HEIGHT_CM);
        }
        this.heightCm = heightCm;
    }

    /**
     * Returns a defensive read-only view of the skill levels map.
     * Sparse map: only contains skills with level > 0.
     */
    public Map<PlayerSkill, Integer> getSkillLevels() {
        return skillLevels == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(skillLevels);
    }

    public int getSkillLevel(PlayerSkill skill) {
        if (skill == null || skillLevels == null) return 0;
        Integer level = skillLevels.get(skill);
        return level == null ? 0 : level;
    }

    /**
     * Sets the level of a single skill. Bounds-checked to [0, 99].
     * Values > MAX throw IAE; negative values throw IAE.
     * Setting to 0 is allowed (and effectively removes the entry from the sparse map).
     */
    public void setSkillLevel(PlayerSkill skill, Integer value) {
        if (skill == null) {
            throw new IllegalArgumentException("skill cannot be null");
        }
        if (value == null) {
            throw new IllegalArgumentException("skill level cannot be null");
        }
        if (value < MIN_SKILL_LEVEL || value > MAX_SKILL_LEVEL) {
            throw new IllegalArgumentException(
                    "Skill level must be between " + MIN_SKILL_LEVEL + " and " + MAX_SKILL_LEVEL);
        }
        if (skillLevels == null) {
            skillLevels = new HashMap<>();
        }
        if (value == 0) {
            skillLevels.remove(skill);
        } else {
            skillLevels.put(skill, value);
        }
    }

    // ========== Setters ==========

    public void setSessionPlayerId(String id) { this.sessionPlayerId = id; }
    public void setBasePlayerId(UUID id) { this.basePlayerId = id; }
    public void setWorldPlayerId(String id) { this.worldPlayerId = id; }
    public void setName(String name) { this.name = name; }
    public void setAge(Integer age) { this.age = age; }
    public void setPosition(String position) { this.position = position; }
    public void setAttack(Integer attack) { this.attack = attack; }
    public void setDefense(Integer defense) { this.defense = defense; }
    public void setTechnique(Integer technique) { this.technique = technique; }
    public void setSpeed(Integer speed) { this.speed = speed; }
    public void setStamina(Integer stamina) { this.stamina = stamina; }
    public void setMentality(Integer mentality) { this.mentality = mentality; }
    public void setMarketValue(BigDecimal value) { this.marketValue = value; }
    public void setEnergy(Integer energy) { this.energy = energy; }
    public void setForm(Integer form) { this.form = form; }
    public void setInjured(Boolean injured) { this.injured = injured; }
    public void setInjuryType(String type) { this.injuryType = type; }
    public void setInjuryRemainingMatches(Integer matches) { this.injuryRemainingMatches = matches; }
    public void setMatchesPlayedInRow(Integer matches) { this.matchesPlayedInRow = matches; }
    public void setYellowCards(Integer yellowCards) { this.yellowCards = yellowCards; }
    public void setRedCards(Integer redCards) { this.redCards = redCards; }
    public void setSuspended(Boolean suspended) { this.suspended = suspended; }
    public void setSuspensionRemainingMatches(Integer matches) { this.suspensionRemainingMatches = matches; }
    public void setOrigin(SessionPlayerOrigin origin) { this.origin = origin; }
}
