package com.footballmanager.domain.model.entity;

import com.footballmanager.domain.model.valueobject.PlayerSkill;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * PlayerAttributes - Atributos numericos + metadata fisica/skills de un jugador.
 *
 * V25D31: se agregan heightCm y skillLevels (sparse map).
 */
public class PlayerAttributes {
    // V25D31 - Bounds
    private static final int MIN_SKILL_LEVEL = 0;
    private static final int MAX_SKILL_LEVEL = 99;
    private static final int MIN_HEIGHT_CM = 160;
    private static final int MAX_HEIGHT_CM = 210;

    private int attack;
    private int defense;
    private int technique;
    private int speed;
    private int stamina;
    private int mentality;

    // V25D31 - Physical + skill metadata
    private Integer heightCm;
    private Map<PlayerSkill, Integer> skillLevels;

    public PlayerAttributes() {
        this.skillLevels = new HashMap<>();
    }

    public PlayerAttributes(int attack, int defense, int technique, int speed, int stamina, int mentality) {
        this.attack = attack;
        this.defense = defense;
        this.technique = technique;
        this.speed = speed;
        this.stamina = stamina;
        this.mentality = mentality;
        this.skillLevels = new HashMap<>();
    }

    public static PlayerAttributes of(int attack, int defense, int technique, int speed, int stamina, int mentality) {
        return new PlayerAttributes(attack, defense, technique, speed, stamina, mentality);
    }

    /**
     * Factory completa con height + skills (V25D31).
     */
    public static PlayerAttributes withHeightAndSkills(int attack, int defense, int technique,
                                                       int speed, int stamina, int mentality,
                                                       Integer heightCm,
                                                       Map<PlayerSkill, Integer> skillLevels) {
        PlayerAttributes attrs = new PlayerAttributes(attack, defense, technique, speed, stamina, mentality);
        if (heightCm != null) {
            attrs.setHeightCm(heightCm);
        }
        if (skillLevels != null) {
            for (Map.Entry<PlayerSkill, Integer> e : skillLevels.entrySet()) {
                attrs.setSkillLevel(e.getKey(), e.getValue());
            }
        }
        return attrs;
    }

    public int getAttack() { return attack; }
    public void setAttack(int attack) { this.attack = attack; }
    public int getDefense() { return defense; }
    public void setDefense(int defense) { this.defense = defense; }
    public int getTechnique() { return technique; }
    public void setTechnique(int technique) { this.technique = technique; }
    public int getSpeed() { return speed; }
    public void setSpeed(int speed) { this.speed = speed; }
    public int getStamina() { return stamina; }
    public void setStamina(int stamina) { this.stamina = stamina; }
    public int getMentality() { return mentality; }
    public void setMentality(int mentality) { this.mentality = mentality; }

    public int calculateOverall() {
        return (attack + defense + technique + speed + stamina + mentality) / 6;
    }

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
}
