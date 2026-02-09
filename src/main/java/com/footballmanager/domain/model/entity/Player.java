package com.footballmanager.domain.model.entity;

import com.footballmanager.domain.model.valueobject.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public class Player {

    public enum Position {
        GK, LB, CB, RB, LWB, RWB, CDM, CM, CAM, LM, RM, LW, RW, CF, ST
    }

    public enum InjuryState {
        HEALTHY, INJURED_LIGHT, INJURED_SERIOUS
    }

    private final PlayerId id;
    private final String name;
    private final int age;
    private final Position position;
    private final BigDecimal marketValue;
    private PlayerAttributes attributes;
    private int energy;
    private InjuryState injuryState;
    private boolean injured;
    private final Instant createdAt;
    private Instant updatedAt;

    private Player(PlayerId id, String name, int age, Position position,
                   PlayerAttributes attributes, BigDecimal marketValue,
                   int energy, InjuryState injuryState, boolean injured,
                   Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.name = Objects.requireNonNull(name, "Player name cannot be null");
        this.position = Objects.requireNonNull(position, "Position cannot be null");
        this.attributes = Objects.requireNonNull(attributes, "Attributes cannot be null");
        this.marketValue = marketValue;
        this.energy = energy;
        this.injuryState = injuryState != null ? injuryState : InjuryState.HEALTHY;
        this.injured = injured;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : Instant.now();
        validateAge(age);
        validateEnergy(energy);
        this.age = age;
    }

    public static Player create(PlayerId id, String name, int age, Position position,
                                PlayerAttributes attributes, BigDecimal marketValue) {
        return new Player(id, name, age, position, attributes, marketValue,
                         100, InjuryState.HEALTHY, false, Instant.now(), Instant.now());
    }

    public static Player reconstruct(PlayerId id, String name, int age, Position position,
                              PlayerAttributes attributes, BigDecimal marketValue,
                              int energy, InjuryState injuryState, boolean injured,
                              Instant createdAt, Instant updatedAt) {
        return new Player(id, name, age, position, attributes, marketValue,
                         energy, injuryState, injured, createdAt, updatedAt);
    }

    private void validateAge(int age) {
        if (age < 16 || age > 45) {
            throw new IllegalArgumentException("Player age must be between 16 and 45");
        }
    }

    private void validateEnergy(int energy) {
        if (energy < 0 || energy > 100) {
            throw new IllegalArgumentException("Energy must be between 0 and 100");
        }
    }

    public void updateEnergy(int delta) {
        this.energy = Math.max(0, Math.min(100, this.energy + delta));
        this.updatedAt = Instant.now();
    }

    public void setEnergyTo(int value) {
        validateEnergy(value);
        this.energy = value;
        this.updatedAt = Instant.now();
    }

    public void sustainSeriousInjury() {
        this.injuryState = InjuryState.INJURED_SERIOUS;
        this.injured = true;
        this.energy = Math.max(0, this.energy - 30);
        this.updatedAt = Instant.now();
    }

    public void sustainLightInjury() {
        this.injuryState = InjuryState.INJURED_LIGHT;
        this.injured = true;
        this.updatedAt = Instant.now();
    }

    public void recoverFromInjury() {
        this.injuryState = InjuryState.HEALTHY;
        this.injured = false;
        this.updatedAt = Instant.now();
    }

    public void updateAttributes(PlayerAttributes newAttributes) {
        this.attributes = Objects.requireNonNull(newAttributes, "Attributes cannot be null");
        this.updatedAt = Instant.now();
    }

    public void updateAttributes(int attackDelta, int defenseDelta, int techniqueDelta,
                                 int speedDelta, int staminaDelta, int mentalityDelta) {
        PlayerAttributes current = this.attributes;
        PlayerAttributes updated = PlayerAttributes.of(
            Math.max(1, current.getAttack() + attackDelta),
            Math.max(1, current.getDefense() + defenseDelta),
            Math.max(1, current.getTechnique() + techniqueDelta),
            Math.max(1, current.getSpeed() + speedDelta),
            Math.max(1, current.getStamina() + staminaDelta),
            Math.max(1, current.getMentality() + mentalityDelta)
        );
        this.attributes = updated;
        this.updatedAt = Instant.now();
    }

    public PlayerId getId() { return id; }
    public String getName() { return name; }
    public int getAge() { return age; }
    public Position getPosition() { return position; }
    public PlayerAttributes getAttributes() { return attributes; }
    public BigDecimal getMarketValue() { return marketValue; }
    public int getEnergy() { return energy; }
    public InjuryState getInjuryState() { return injuryState; }
    public boolean isInjured() { return injured; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public int getOverall() {
        int attack = attributes.getAttack();
        int defense = attributes.getDefense();
        int technique = attributes.getTechnique();
        int speed = attributes.getSpeed();
        int stamina = attributes.getStamina();
        int mentality = attributes.getMentality();

        double overall = switch (position) {
            case GK -> defense * 0.40 + technique * 0.20 + mentality * 0.20 + stamina * 0.10 + speed * 0.05 + attack * 0.05;
            case LB, CB, RB, LWB, RWB -> defense * 0.35 + technique * 0.15 + mentality * 0.15 + stamina * 0.15 + speed * 0.10 + attack * 0.10;
            case CDM, CM, CAM, LM, RM -> technique * 0.30 + stamina * 0.20 + mentality * 0.15 + defense * 0.15 + speed * 0.10 + attack * 0.10;
            case LW, RW -> speed * 0.30 + attack * 0.25 + technique * 0.20 + stamina * 0.15 + mentality * 0.05 + defense * 0.05;
            case CF, ST -> attack * 0.40 + technique * 0.20 + speed * 0.15 + mentality * 0.10 + stamina * 0.10 + defense * 0.05;
            default -> (attack + defense + technique + speed + stamina + mentality) / 6.0;
        };
        return (int) Math.round(overall);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Player player = (Player) o;
        return Objects.equals(id, player.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
