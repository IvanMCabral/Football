package com.footballmanager.domain.model;

import java.util.Objects;

public class PlayerAttributes {
    private final int attack;
    private final int defense;
    private final int technique;
    private final int speed;
    private final int stamina;
    private final int mentality;

    private PlayerAttributes(int attack, int defense, int technique, int speed, int stamina, int mentality) {
        validateAttribute(attack, "attack");
        validateAttribute(defense, "defense");
        validateAttribute(technique, "technique");
        validateAttribute(speed, "speed");
        validateAttribute(stamina, "stamina");
        validateAttribute(mentality, "mentality");

        this.attack = attack;
        this.defense = defense;
        this.technique = technique;
        this.speed = speed;
        this.stamina = stamina;
        this.mentality = mentality;
    }

    public static PlayerAttributes of(int attack, int defense, int technique, int speed, int stamina, int mentality) {
        return new PlayerAttributes(attack, defense, technique, speed, stamina, mentality);
    }

    private void validateAttribute(int value, String name) {
        if (value < 0 || value > 100) {
            throw new IllegalArgumentException(name + " must be between 0 and 100");
        }
    }

    public int getAttack() {
        return attack;
    }

    public int getDefense() {
        return defense;
    }

    public int getTechnique() {
        return technique;
    }

    public int getSpeed() {
        return speed;
    }

    public int getStamina() {
        return stamina;
    }

    public int getMentality() {
        return mentality;
    }

    public int calculateOverall() {
        return (attack + defense + technique + speed + stamina + mentality) / 6;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlayerAttributes that = (PlayerAttributes) o;
        return attack == that.attack &&
                defense == that.defense &&
                technique == that.technique &&
                speed == that.speed &&
                stamina == that.stamina &&
                mentality == that.mentality;
    }

    @Override
    public int hashCode() {
        return Objects.hash(attack, defense, technique, speed, stamina, mentality);
    }

    @Override
    public String toString() {
        return String.format("PlayerAttributes{overall=%d, attack=%d, defense=%d, technique=%d, speed=%d, stamina=%d, mentality=%d}",
                calculateOverall(), attack, defense, technique, speed, stamina, mentality);
    }
}
