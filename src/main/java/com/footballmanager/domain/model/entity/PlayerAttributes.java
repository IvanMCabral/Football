package com.footballmanager.domain.model.entity;

public class PlayerAttributes {
    private int attack;
    private int defense;
    private int technique;
    private int speed;
    private int stamina;
    private int mentality;

    public PlayerAttributes() {}
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
}
