package com.footballmanager.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public class Player {
    private final PlayerId id;
    private final String name;
    private final int age;
    private final Position position;
    private final PlayerAttributes attributes;
    private final BigDecimal marketValue;
    private int energy;
    private boolean injured;
    private final Instant createdAt;
    private Instant updatedAt;

    public enum Position {
        GK, CB, LB, RB, CDM, CM, CAM, LM, RM, ST, LW, RW
    }

    private Player(PlayerId id, String name, int age, Position position, 
                   PlayerAttributes attributes, BigDecimal marketValue,
                   int energy, boolean injured, Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "PlayerId cannot be null");
        this.name = Objects.requireNonNull(name, "Player name cannot be null");
        this.position = Objects.requireNonNull(position, "Position cannot be null");
        this.attributes = Objects.requireNonNull(attributes, "Attributes cannot be null");
        this.marketValue = Objects.requireNonNull(marketValue, "Market value cannot be null");
        
        validateAge(age);
        validateEnergy(energy);
        
        this.age = age;
        this.energy = energy;
        this.injured = injured;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : Instant.now();
    }

    public static Player create(PlayerId id, String name, int age, Position position,
                                PlayerAttributes attributes, BigDecimal marketValue) {
        return new Player(id, name, age, position, attributes, marketValue, 
                         100, false, Instant.now(), Instant.now());
    }

    public static Player reconstruct(PlayerId id, String name, int age, Position position,
                                     PlayerAttributes attributes, BigDecimal marketValue,
                                     int energy, boolean injured, Instant createdAt, Instant updatedAt) {
        return new Player(id, name, age, position, attributes, marketValue,
                         energy, injured, createdAt, updatedAt);
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

    public void injure() {
        this.injured = true;
        this.energy = Math.max(0, this.energy - 30);
        this.updatedAt = Instant.now();
    }

    public void heal() {
        this.injured = false;
        this.updatedAt = Instant.now();
    }

    public int calculateOverallRating() {
        return attributes.calculateOverall();
    }

    // Getters
    public PlayerId getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getAge() {
        return age;
    }

    public Position getPosition() {
        return position;
    }

    public PlayerAttributes getAttributes() {
        return attributes;
    }

    public BigDecimal getMarketValue() {
        return marketValue;
    }

    public int getEnergy() {
        return energy;
    }

    public boolean isInjured() {
        return injured;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
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

    @Override
    public String toString() {
        return String.format("Player{id=%s, name='%s', age=%d, position=%s, overall=%d, energy=%d, injured=%s}",
                id, name, age, position, calculateOverallRating(), energy, injured);
    }
}
