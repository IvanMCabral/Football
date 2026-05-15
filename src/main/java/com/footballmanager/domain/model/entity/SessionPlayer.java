package com.footballmanager.domain.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
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

    public enum SessionPlayerOrigin { CLONED, CUSTOM, RANDOM }

    // ========== Factory Methods ==========

    // Alias for backward compatibility
    public static SessionPlayer cloneFromWorldPlayer(String worldPlayerId, String name,
            String position, Integer age, Integer overall, String currentTeamId) {
        return fromWorldPlayer(worldPlayerId, name, position, age, overall);
    }

    public static SessionPlayer fromWorldPlayer(String worldPlayerId, String name,
            String position, Integer age, Integer overall) {
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
    }

    // ========== Business Logic ==========

    public Integer calculateOverall() {
        if (hasNullAttributes()) return 50;

        double overall = switch (position) {
            case "GK" -> defense * 0.40 + technique * 0.20 + mentality * 0.20 +
                       stamina * 0.10 + speed * 0.05 + attack * 0.05;
            case "DEF" -> defense * 0.35 + technique * 0.15 + mentality * 0.15 +
                       stamina * 0.15 + speed * 0.10 + attack * 0.10;
            case "MID" -> technique * 0.30 + stamina * 0.20 + mentality * 0.15 +
                       defense * 0.15 + speed * 0.10 + attack * 0.10;
            case "WINGER" -> speed * 0.30 + attack * 0.25 + technique * 0.20 +
                       stamina * 0.15 + mentality * 0.05 + defense * 0.05;
            case "ATT" -> attack * 0.40 + technique * 0.20 + speed * 0.15 +
                       mentality * 0.10 + stamina * 0.10 + defense * 0.05;
            default -> getAverageAttributes();
        };
        return (int) Math.round(overall);
    }

    private boolean hasNullAttributes() {
        return attack == null || defense == null || technique == null ||
               speed == null || stamina == null || mentality == null;
    }

    private double getAverageAttributes() {
        return (attack + defense + technique + speed + stamina + mentality) / 6.0;
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
