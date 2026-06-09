package com.footballmanager.application.service.simulation.v24;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/**
 * V24D4A: DTO for V24ShotCoordinate in storage/API layers.
 * Immutable snapshot — not tied to internal V24ShotCoordinate.
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.NONE,
        getterVisibility = JsonAutoDetect.Visibility.PUBLIC_ONLY,
        isGetterVisibility = JsonAutoDetect.Visibility.PUBLIC_ONLY,
        setterVisibility = JsonAutoDetect.Visibility.PUBLIC_ONLY,
        creatorVisibility = JsonAutoDetect.Visibility.PUBLIC_ONLY)
public final class V24ShotCoordinateDto {

    private final double x;
    private final double y;
    private final String location;
    private final double distanceToGoal;
    private final double angleToGoal;
    private final boolean insideBox;

    @JsonCreator
    public V24ShotCoordinateDto(
            @JsonProperty("x") double x,
            @JsonProperty("y") double y,
            @JsonProperty("location") String location,
            @JsonProperty("distanceToGoal") double distanceToGoal,
            @JsonProperty("angleToGoal") double angleToGoal,
            @JsonProperty("insideBox") boolean insideBox) {
        if (!Double.isFinite(x) || x < 0.0 || x > 100.0) {
            throw new IllegalArgumentException("x must be between 0 and 100, got " + x);
        }
        if (!Double.isFinite(y) || y < 0.0 || y > 100.0) {
            throw new IllegalArgumentException("y must be between 0 and 100, got " + y);
        }
        this.x = x;
        this.y = y;
        this.location = Objects.requireNonNull(location, "location must not be null");
        if (!Double.isFinite(distanceToGoal)) {
            throw new IllegalArgumentException("distanceToGoal must be finite");
        }
        if (!Double.isFinite(angleToGoal)) {
            throw new IllegalArgumentException("angleToGoal must be finite");
        }
        this.distanceToGoal = distanceToGoal;
        this.angleToGoal = angleToGoal;
        this.insideBox = insideBox;
    }

    public static V24ShotCoordinateDto fromCoordinate(V24ShotCoordinate coordinate) {
        Objects.requireNonNull(coordinate, "coordinate must not be null");
        return new V24ShotCoordinateDto(
                coordinate.x(),
                coordinate.y(),
                coordinate.location().name(),
                coordinate.distanceToGoal(),
                coordinate.angleToGoal(),
                coordinate.insideBox()
        );
    }

    @JsonProperty("x") public double x() { return x; }
    @JsonProperty("y") public double y() { return y; }
    @JsonProperty("location") public String location() { return location; }
    @JsonProperty("distanceToGoal") public double distanceToGoal() { return distanceToGoal; }
    @JsonProperty("angleToGoal") public double angleToGoal() { return angleToGoal; }
    @JsonProperty("insideBox") public boolean insideBox() { return insideBox; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof V24ShotCoordinateDto that)) return false;
        return Double.compare(that.x, x) == 0
                && Double.compare(that.y, y) == 0
                && Double.compare(that.distanceToGoal, distanceToGoal) == 0
                && Double.compare(that.angleToGoal, angleToGoal) == 0
                && insideBox == that.insideBox
                && Objects.equals(location, that.location);
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, location, distanceToGoal, angleToGoal, insideBox);
    }

    @Override
    public String toString() {
        return "V24ShotCoordinateDto{x=%.2f, y=%.2f, location=%s, dist=%.2f}"
                .formatted(x, y, location, distanceToGoal);
    }
}