package com.footballmanager.domain.model.entity;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Field-level owner deviations from a canonical real league. */
public final class WorldLeagueDelta {

    public enum Field { NAME, COUNTRY, TIER }

    private UUID realLeagueId;
    private Set<Field> changedFields = EnumSet.noneOf(Field.class);
    private String name;
    private String country;
    private Integer tier;

    public WorldLeagueDelta() {
    }

    public static WorldLeagueDelta between(WorldLeague current, WorldLeague canonical) {
        if (current == null || canonical == null || current.getRealLeagueId() == null
                || !Objects.equals(current.getRealLeagueId(), canonical.getRealLeagueId())) {
            throw new IllegalArgumentException("World league delta requires one canonical identity");
        }
        WorldLeagueDelta delta = new WorldLeagueDelta();
        delta.realLeagueId = current.getRealLeagueId();
        if (!Objects.equals(current.getName(), canonical.getName())) {
            delta.changedFields.add(Field.NAME);
            delta.name = current.getName();
        }
        if (!Objects.equals(current.getCountry(), canonical.getCountry())) {
            delta.changedFields.add(Field.COUNTRY);
            delta.country = current.getCountry();
        }
        if (!Objects.equals(current.getTier(), canonical.getTier())) {
            delta.changedFields.add(Field.TIER);
            delta.tier = current.getTier();
        }
        return delta.changedFields.isEmpty() ? null : delta;
    }

    public void applyTo(WorldLeague target) {
        if (target == null || !Objects.equals(realLeagueId, target.getRealLeagueId())) {
            throw new IllegalStateException("World league delta identity mismatch");
        }
        if (changedFields.contains(Field.NAME)) target.setName(name);
        if (changedFields.contains(Field.COUNTRY)) target.setCountry(country);
        if (changedFields.contains(Field.TIER)) target.setTier(tier);
    }

    public UUID getRealLeagueId() { return realLeagueId; }
    public void setRealLeagueId(UUID realLeagueId) { this.realLeagueId = realLeagueId; }
    public Set<Field> getChangedFields() { return changedFields; }
    public void setChangedFields(Set<Field> changedFields) {
        this.changedFields = changedFields == null || changedFields.isEmpty()
                ? EnumSet.noneOf(Field.class) : EnumSet.copyOf(changedFields);
    }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
    public Integer getTier() { return tier; }
    public void setTier(Integer tier) { this.tier = tier; }
}
