package com.footballmanager.domain.model.entity;

import java.util.Objects;

/**
 * PlayerAttributes - Atributos numericos basicos de un jugador (las 6 stats originales).
 *
 * <p>V25D32: deprecados los campos duplicados {@code heightCm} y {@code skillLevels}.
 * Esos valores ahora viven directamente en {@link Player} (source of truth unico).
 * Coherente con {@code SessionPlayer} (que tampoco delega a un value object separado
 * para height/skills). La justificacion completa esta en la decision arquitectonica
 * resuelta por Mavis root (ver V25D32 sprint prompt).
 *
 * <p>Esta clase conserva unicamente las 6 stats base:
 * attack, defense, technique, speed, stamina, mentality.
 */
public class PlayerAttributes {

    private int attack;
    private int defense;
    private int technique;
    private int speed;
    private int stamina;
    private int mentality;

    public PlayerAttributes() {
    }

    public PlayerAttributes(int attack, int defense, int technique, int speed, int stamina, int mentality) {
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlayerAttributes that = (PlayerAttributes) o;
        return attack == that.attack
                && defense == that.defense
                && technique == that.technique
                && speed == that.speed
                && stamina == that.stamina
                && mentality == that.mentality;
    }

    @Override
    public int hashCode() {
        return Objects.hash(attack, defense, technique, speed, stamina, mentality);
    }
}
